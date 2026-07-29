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

    /** Phase 5D: 2-way swap between two staff on the same rebalance key. */
    public record SwapPair(Schedule scheduleA, Schedule scheduleB) {}

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

            // Phase 5D: try 2-way swap first (reduces 2 moves in 1 round, higher benefit).
            SwapPair swap = findSafeSwapPair(schedules, staffById, counts, stateAccessor);
            if (swap != null) {
                Staff staffForA = swap.scheduleB().getStaff();
                Staff staffForB = swap.scheduleA().getStaff();
                swap.scheduleA().setStaff(staffForA);
                swap.scheduleB().setStaff(staffForB);
                if (swap.scheduleA().getId() != null) scheduleRepository.save(swap.scheduleA());
                if (swap.scheduleB().getId() != null) scheduleRepository.save(swap.scheduleB());
                moves += 2;
                continue;
            }

            // Fallback: 1-way reassign
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

                    if (!StaffShiftTypeEligibility.isEligible(staff, typeId, specId, List.of())) {
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
        double bestGap = 0.5;

        for (Map.Entry<String, Map<Integer, Long>> entry : counts.entrySet()) {
            String key = entry.getKey();
            Map<Integer, Long> perStaff = entry.getValue();
            if (perStaff.isEmpty()) continue;

            // Phase 5D: compute load score per staff, then pick max/min by load.
            // The map key (e.g. "L02" or "L04:7") is the same shift type, so
            // weightOf for the lead shift type is constant within this entry.
            String leadTypeId = key.startsWith("L04:") ? "L04"
                    : (key.startsWith("L0") ? key.substring(0, 3) : key);
            double weight = LoadScoreCalculator.weightOf(leadTypeId);

            Map<Integer, Double> loadByStaff = new HashMap<>();
            for (Map.Entry<Integer, Long> kv : perStaff.entrySet()) {
                loadByStaff.put(kv.getKey(), kv.getValue() * weight);
            }

            Integer overloadedStaffId = loadByStaff.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            Integer underloadedStaffId = loadByStaff.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            if (overloadedStaffId == null || underloadedStaffId == null || overloadedStaffId.equals(underloadedStaffId))
                continue;

            double gap = loadByStaff.getOrDefault(overloadedStaffId, 0.0)
                    - loadByStaff.getOrDefault(underloadedStaffId, 0.0);
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

    /**
     * Phase 5D: 2-way swap. Find a pair of schedules (both under same rebalance key L02/L03/L04)
     * where swapping their staff reduces load variance for BOTH staff simultaneously.
     * That's strictly better than a 1-way move because it counts as 2 rebalance units per round.
     * <p>Candidate pattern: schedule A (overloaded staff X, type T1) and schedule B (underloaded staff Y,
     * same key) such that after swap: X has -1 count of T1, Y has +1 count of T1.
     * Symmetric swap (A↔B) needs to keep both safe — checked via isSafeLocalSearchReassignment
     * with the hypothetical owner swap.
     */
    private SwapPair findSafeSwapPair(List<Schedule> schedules,
                                       Map<Integer, Staff> staffById,
                                       Map<String, Map<Integer, Long>> counts,
                                       SchedulingStateAccessor stateAccessor) {
        SwapPair best = null;
        double bestLoadImprovement = 0.5;

        for (Map.Entry<String, Map<Integer, Long>> entry : counts.entrySet()) {
            String key = entry.getKey();
            Map<Integer, Long> perStaff = entry.getValue();
            if (perStaff.size() < 2) continue;

            String leadTypeId = key.startsWith("L04:") ? "L04"
                    : (key.startsWith("L0") ? key.substring(0, 3) : key);
            double weight = LoadScoreCalculator.weightOf(leadTypeId);

            // Find overloaded + underloaded staff (same key)
            Integer overloadedId = null, underloadedId = null;
            long maxCount = Long.MIN_VALUE, minCount = Long.MAX_VALUE;
            for (Map.Entry<Integer, Long> kv : perStaff.entrySet()) {
                if (kv.getValue() > maxCount) { maxCount = kv.getValue(); overloadedId = kv.getKey(); }
                if (kv.getValue() < minCount) { minCount = kv.getValue(); underloadedId = kv.getKey(); }
            }
            if (overloadedId == null || underloadedId == null || overloadedId.equals(underloadedId)) continue;
            long countGap = maxCount - minCount;
            if (countGap < 2) continue;  // need at least 2-count gap to make swap worthwhile

            // Find a schedule for each staff matching the same key
            Schedule overloadedSchedule = null, underloadedSchedule = null;
            for (Schedule s : schedules) {
                if (!key.equals(rebalanceKey(s))) continue;
                if (s.getStaff().getId() == overloadedId && overloadedSchedule == null) {
                    overloadedSchedule = s;
                } else if (s.getStaff().getId() == underloadedId && underloadedSchedule == null) {
                    underloadedSchedule = s;
                }
            }
            if (overloadedSchedule == null || underloadedSchedule == null) continue;

            // Safety check: both moves must be safe after the swap.
            // Simulate: assign overloadedSchedule's pivot to underloaded staff, and vice versa.
            Staff overloadedStaff = staffById.get(underloadedId);
            Staff underloadedStaff = staffById.get(overloadedId);
            if (overloadedStaff == null || underloadedStaff == null) continue;

            // Hypothetical swap: overloadedSchedule's slot is taken by underloadedId staff,
            // and underloadedSchedule's slot is taken by overloadedId staff.
            // Use isSafeLocalSearchReassignment on both directions.
            // Note: we run the safety check twice but with each candidate applied to the other's slot.
            boolean safeMoveA = isSafeLocalSearchReassignment(overloadedSchedule, overloadedStaff, schedules, stateAccessor);
            boolean safeMoveB = isSafeLocalSearchReassignment(underloadedSchedule, underloadedStaff, schedules, stateAccessor);
            if (!safeMoveA || !safeMoveB) continue;

            // Load improvement: each side moves ±1 count → gap reduces by 2*weight.
            double loadImprovement = 2.0 * weight;
            if (loadImprovement > bestLoadImprovement) {
                best = new SwapPair(overloadedSchedule, underloadedSchedule);
                bestLoadImprovement = loadImprovement;
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
