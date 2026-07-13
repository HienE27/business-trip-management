package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.service.ConflictDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Post-assignment optimization: local-search fairness rebalance + hard guarantee
 * that every active staff has at least one shift.
 */
@Slf4j
@Component
public class PostAssignmentOptimizer {

    private final ScheduleRepository scheduleRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final StaffEligibilityFilter eligibilityFilter;

    public PostAssignmentOptimizer(ScheduleRepository scheduleRepository,
                                  LeaveRequestRepository leaveRequestRepository,
                                  StaffEligibilityFilter eligibilityFilter) {
        this.scheduleRepository = scheduleRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.eligibilityFilter = eligibilityFilter;
    }

    public record RebalanceMove(Schedule schedule, Staff toStaff) {}

    /**
     * Local search fairness optimizer (reassigns loaded → underloaded staff,
     * keep L01 fixed because compensation-day side effects).
     */
    public int optimizeFairnessBySafeReassignment(List<Schedule> schedules,
                                                 List<Staff> activeStaff,
                                                 List<ShiftRequirement> requirements,
                                                 int maxRounds,
                                                 SchedulingStateAccessor stateAccessor) {
        if (schedules == null || schedules.isEmpty() || activeStaff == null || activeStaff.isEmpty()) {
            return 0;
        }
        Map<Integer, Staff> staffById = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s, (a, b) -> a));
        int moves = 0;

        for (int round = 0; round < maxRounds; round++) {
            Map<String, Map<Integer, Long>> counts = buildSafeRebalanceCounts(schedules, activeStaff);
            RebalanceMove move = findBestSafeRebalanceMove(schedules, activeStaff, staffById, counts, stateAccessor);
            if (move == null) break;

            move.schedule().setStaff(move.toStaff());
            if (move.schedule().getId() != null) {
                scheduleRepository.save(move.schedule());
            }
            moves++;
        }
        return moves;
    }

    /**
     * HARD GUARANTEE: ensure every active staff has at least 1 shift.
     */
    public int guaranteeMinimumShifts(List<Schedule> schedules,
                                      List<Staff> staffWithoutShifts,
                                      List<ShiftRequirement> requirements,
                                      List<Staff> activeStaff,
                                      SchedulingStateAccessor stateAccessor) {
        if (staffWithoutShifts == null || staffWithoutShifts.isEmpty()) {
            return 0;
        }
        int fixed = 0;
        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s, (a, b) -> a));

        Map<LocalDate, Set<Integer>> assignedByDate = new HashMap<>();
        Map<LocalDate, Map<String, Set<Integer>>> assignedByDateAndType = new HashMap<>();
        for (Schedule s : schedules) {
            LocalDate date = s.getWorkDate();
            assignedByDate.computeIfAbsent(date, k -> new HashSet<>()).add(s.getStaff().getId());
            assignedByDateAndType.computeIfAbsent(date, k -> new HashMap<>())
                    .computeIfAbsent(s.getShiftType().getId(), k -> new HashSet<>())
                    .add(s.getStaff().getId());
        }

        Map<LocalDate, Map<String, Integer>> unfilledByDateAndType = new HashMap<>();
        for (ShiftRequirement req : requirements) {
            LocalDate date = req.getWorkDate();
            String typeId = req.getShiftType().getId();
            Set<Integer> assigned = assignedByDateAndType.getOrDefault(date, Map.of())
                    .getOrDefault(typeId, Set.of());
            int needed = Math.max(0, req.getRequiredStaffCount() - assigned.size());
            if (needed > 0) {
                unfilledByDateAndType.computeIfAbsent(date, k -> new HashMap<>()).put(typeId, needed);
            }
        }

        for (Staff staff : staffWithoutShifts) {
            boolean assigned = false;
            for (Map.Entry<LocalDate, Map<String, Integer>> dateEntry : unfilledByDateAndType.entrySet()) {
                if (assigned) break;
                LocalDate date = dateEntry.getKey();
                for (Map.Entry<String, Integer> typeEntry : dateEntry.getValue().entrySet()) {
                    if (assigned) break;
                    if (typeEntry.getValue() <= 0) continue;
                    String typeId = typeEntry.getKey();

                    Integer specId = requirements.stream()
                            .filter(r -> r.getWorkDate().equals(date) && r.getShiftType().getId().equals(typeId))
                            .findFirst()
                            .map(r -> r.getSpecialty() != null ? r.getSpecialty().getId() : null)
                            .orElse(null);

                    if (!StaffShiftTypeEligibility.isEligible(staff, typeId, specId, eligibilityFilter.getNonL04AllowedSpecialties(typeId))) {
                        continue;
                    }
                    if (assignedByDate.getOrDefault(date, Set.of()).contains(staff.getId())) continue;

                    boolean hasConflict = false;
                    Set<String> existingTypes = assignedByDateAndType.getOrDefault(date, Map.of()).keySet();
                    for (String existingType : existingTypes) {
                        if (eligibilityFilter.isBusinessShiftConflict(typeId, existingType)) {
                            hasConflict = true; break;
                        }
                    }
                    if (hasConflict) continue;

                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeId)) {
                        LocalDate prevDate = date.minusDays(1);
                        LocalDate nextDate = date.plusDays(1);
                        Set<Integer> prevAssigned = assignedByDate.getOrDefault(prevDate, Set.of());
                        Set<Integer> nextAssigned = assignedByDate.getOrDefault(nextDate, Set.of());
                        if (prevAssigned.contains(staff.getId()) || nextAssigned.contains(staff.getId())) {
                            boolean hasAdjL01 = false;
                            for (Schedule s : schedules) {
                                if (s.getStaff().getId().equals(staff.getId())) {
                                    if ((s.getWorkDate().equals(prevDate) || s.getWorkDate().equals(nextDate))
                                            && ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId())) {
                                        hasAdjL01 = true; break;
                                    }
                                }
                            }
                            if (hasAdjL01) continue;
                        }
                    }

                    ShiftRequirement req = requirements.stream()
                            .filter(r -> r.getWorkDate().equals(date) && r.getShiftType().getId().equals(typeId))
                            .findFirst()
                            .orElse(null);

                    if (req != null) {
                        Schedule newSchedule = buildNewSchedule(staff, req, date);
                        schedules.add(newSchedule);
                        assignedByDate.computeIfAbsent(date, k -> new HashSet<>()).add(staff.getId());
                        assignedByDateAndType.computeIfAbsent(date, k -> new HashMap<>())
                                .computeIfAbsent(typeId, k -> new HashSet<>())
                                .add(staff.getId());
                        typeEntry.setValue(typeEntry.getValue() - 1);
                        log.info("HARD GUARANTEE: Assigned staff {} to {} on {}", staff.getId(), typeId, date);
                        fixed++;
                        assigned = true;
                    }
                }
            }
        }
        return fixed;
    }

    private Schedule buildNewSchedule(Staff staff, ShiftRequirement req, LocalDate workDate) {
        return Schedule.builder()
                .staff(staff)
                .shiftType(req.getShiftType())
                .workDate(workDate)
                .period(req.getPeriod())
                .requirement(req)
                .hasConflict(false)
                .isPreview(false)
                .build();
    }

    private Map<String, Map<Integer, Long>> buildSafeRebalanceCounts(List<Schedule> schedules, List<Staff> activeStaff) {
        Map<String, Map<Integer, Long>> counts = new LinkedHashMap<>();
        for (Schedule schedule : schedules) {
            String typeId = schedule.getShiftType().getId();
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeId)) continue;
            String key = rebalanceKey(schedule);
            counts.computeIfAbsent(key, k -> new HashMap<>())
                    .merge(schedule.getStaff().getId(), 1L, Long::sum);
        }
        for (String key : new ArrayList<>(counts.keySet())) {
            Set<Integer> pool = eligiblePoolForRebalanceKey(key, activeStaff);
            for (Integer staffId : pool) counts.get(key).putIfAbsent(staffId, 0L);
        }
        return counts;
    }

    private RebalanceMove findBestSafeRebalanceMove(List<Schedule> schedules,
                                                    List<Staff> activeStaff,
                                                    Map<Integer, Staff> staffById,
                                                    Map<String, Map<Integer, Long>> counts,
                                                    SchedulingStateAccessor stateAccessor) {
        RebalanceMove best = null;
        long bestGap = 1;

        for (Map.Entry<String, Map<Integer, Long>> entry : counts.entrySet()) {
            String key = entry.getKey();
            Map<Integer, Long> perStaff = entry.getValue();
            if (perStaff.isEmpty()) continue;

            Integer overloadedStaffId = perStaff.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            Integer underloadedStaffId = perStaff.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            if (overloadedStaffId == null || underloadedStaffId == null || overloadedStaffId.equals(underloadedStaffId))
                continue;

            long gap = perStaff.getOrDefault(overloadedStaffId, 0L) - perStaff.getOrDefault(underloadedStaffId, 0L);
            if (gap <= bestGap) continue;

            Staff toStaff = staffById.get(underloadedStaffId);
            if (toStaff == null) continue;

            Optional<Schedule> movable = schedules.stream()
                    .filter(s -> overloadedStaffId.equals(s.getStaff().getId()))
                    .filter(s -> key.equals(rebalanceKey(s)))
                    .filter(s -> isSafeLocalSearchReassignment(s, toStaff, schedules, stateAccessor))
                    .findFirst();

            if (movable.isPresent()) {
                best = new RebalanceMove(movable.get(), toStaff);
                bestGap = gap;
            }
        }
        return best;
    }

    private boolean isSafeLocalSearchReassignment(Schedule schedule, Staff candidate, List<Schedule> schedules,
                                                  SchedulingStateAccessor stateAccessor) {
        String typeId = schedule.getShiftType().getId();
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeId)) return false;
        if (candidate == null || candidate.getId().equals(schedule.getStaff().getId())) return false;
        if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)
                && schedule.getRequirement() != null
                && schedule.getRequirement().getSpecialty() != null
                && !eligibilityFilter.isStrictMatchForStaff(candidate, schedule.getRequirement())) {
            return false;
        }

        LocalDate workDate = schedule.getWorkDate();
        if (stateAccessor.isCompensationDate(candidate.getId(), workDate)) return false;

        boolean hasApprovedLeave = leaveRequestRepository
                .findByStaffIdAndDateRange(candidate.getId(), workDate, workDate)
                .stream()
                .anyMatch(lr -> lr.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (hasApprovedLeave) return false;

        for (Schedule existing : schedules) {
            if (existing == schedule) continue;
            if (!candidate.getId().equals(existing.getStaff().getId())) continue;

            String existingType = existing.getShiftType().getId();
            if (workDate.equals(existing.getWorkDate())) {
                if (existingType.equals(typeId)) return false;
                if (eligibilityFilter.isBusinessShiftConflict(typeId, existingType)) return false;
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existingType)) return false;
            }
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existingType)
                    && existing.getWorkDate().equals(workDate.minusDays(1))) {
                return false;
            }
        }
        return true;
    }

    private Set<Integer> eligiblePoolForRebalanceKey(String key, List<Staff> activeStaff) {
        if (key.startsWith(ConflictDetectionService.SHIFT_TYPE_L04 + ":")) {
            Integer specialtyId = Integer.parseInt(key.substring(key.indexOf(':') + 1));
            return activeStaff.stream()
                    .filter(s -> s.getSpecialty() != null && specialtyId.equals(s.getSpecialty().getId()))
                    .map(Staff::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        String shiftTypeId = key.startsWith("L0") ? key.substring(0, 3) : key;
        Integer requiredSpecId = null;
        return activeStaff.stream()
                .filter(s -> StaffShiftTypeEligibility.isEligible(s, shiftTypeId, requiredSpecId))
                .map(Staff::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String rebalanceKey(Schedule schedule) {
        String typeId = schedule.getShiftType().getId();
        if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)
                && schedule.getRequirement() != null
                && schedule.getRequirement().getSpecialty() != null) {
            return typeId + ":" + schedule.getRequirement().getSpecialty().getId();
        }
        return typeId;
    }

    /**
     * Compatibility bridge for AutoSchedulingService.buildNewSchedule().
     */
    public Schedule buildNewScheduleCompat(Staff staff, ShiftRequirement req, LocalDate workDate) {
        return buildNewSchedule(staff, req, workDate);
    }

    /**
     * Compatibility bridge for AutoSchedulingService.buildSafeRebalanceCounts().
     */
    public Map<String, Map<Integer, Long>> buildSafeRebalanceCountsCompat(List<Schedule> schedules,
                                                                          List<Staff> activeStaff) {
        return buildSafeRebalanceCounts(schedules, activeStaff);
    }

    /**
     * Compatibility bridge for AutoSchedulingService.findBestSafeRebalanceMove().
     */
    public RebalanceMove findBestSafeRebalanceMoveCompat(List<Schedule> schedules,
                                                         List<Staff> activeStaff,
                                                         Map<Integer, Staff> staffById,
                                                         Map<String, Map<Integer, Long>> counts,
                                                         SchedulingStateAccessor stateAccessor) {
        return findBestSafeRebalanceMove(schedules, activeStaff, staffById, counts, stateAccessor);
    }
}
