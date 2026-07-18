package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Random Restart Hill Climbing — multiple random greedy starts + hill climbing.
 * Ported from solve_random_restart_hc.
 */
@Slf4j
@Component
public class RandomRestartHCScheduler {

    private static final int NUM_RESTARTS = 12;   // Tăng từ 8→12
    private static final int MAX_ITER = 500;       // Tăng từ 300→500
    private static final String[] SHIFT_TYPES = {"L01", "L02", "L03", "L04"};
    private static final Random GLOBAL_RNG = new Random();

    @Autowired
    private com.hospital.scheduler.util.CompensationDateCalculator compensationDateCalculator;

    @Autowired
    private com.hospital.scheduler.repository.ScheduleRepository scheduleRepository;

    @Autowired
    private com.hospital.scheduler.repository.CompensationDayRepository compensationDayRepository;

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds) {

        long start = System.currentTimeMillis();
        Random rng = GLOBAL_RNG;
        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        List<Schedule> bestSchedules = new ArrayList<>();
        int bestCount = 0;

        // Load database pre-existing L01 schedules and compensation days
        LocalDate periodStart = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        
        List<CompensationDay> compDays = compensationDayRepository.findInRange(periodStart, periodEnd);
        List<Schedule> l01Schedules = scheduleRepository.findL01SchedulesInRange(periodStart.minusDays(7), periodStart.minusDays(1));
        
        Map<Integer, Set<LocalDate>> blockedDates = new HashMap<>();
        for (CompensationDay cd : compDays) {
            blockedDates.computeIfAbsent(cd.getStaff().getId(), k -> new HashSet<>())
                    .add(cd.getCompensationDate());
        }
        for (Schedule s : l01Schedules) {
            int staffId = s.getStaff().getId();
            LocalDate workDate = s.getWorkDate();
            blockedDates.computeIfAbsent(staffId, k -> new HashSet<>()).add(workDate.plusDays(1));
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            if (compDate != null) {
                blockedDates.computeIfAbsent(staffId, k -> new HashSet<>()).add(compDate);
            }
        }

        for (int restart = 0; restart < NUM_RESTARTS; restart++) {
            // Create a deep copy of blockedDates for this restart path
            Map<Integer, Set<LocalDate>> runBlockedDates = new HashMap<>();
            for (var entry : blockedDates.entrySet()) {
                runBlockedDates.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }

            List<Schedule> current = randomGreedy(activeStaff, requirements, period,
                    runtimeConfig, excludedStaffIds, staffMap, rng, runBlockedDates);
            if (current.isEmpty()) continue;

            // Hill climbing: try to improve by finding better feasible swaps
            for (int iter = 0; iter < MAX_ITER && !current.isEmpty(); iter++) {
                // Try swapping a schedule between overloaded and underloaded staff
                Map<Integer, Integer> cnt = new HashMap<>();
                for (Schedule s : current) cnt.merge(s.getStaff().getId(), 1, Integer::sum);
                
                int minCnt = cnt.values().stream().mapToInt(Integer::intValue).min().orElse(0);
                int maxCnt = cnt.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                
                if (maxCnt - minCnt <= 1) break; // Already balanced
                
                // Find overloaded and underloaded staff
                List<Integer> overloaded = cnt.entrySet().stream()
                        .filter(e -> e.getValue() > minCnt + 1)
                        .map(Map.Entry::getKey).collect(Collectors.toList());
                List<Integer> underloaded = cnt.entrySet().stream()
                        .filter(e -> e.getValue() <= minCnt)
                        .map(Map.Entry::getKey).collect(Collectors.toList());
                
                if (overloaded.isEmpty() || underloaded.isEmpty()) break;
                
                Collections.shuffle(overloaded, rng);
                Collections.shuffle(underloaded, rng);
                
                boolean swapped = false;
                for (int from : overloaded) {
                    for (int to : underloaded) {
                        // Find a schedule from 'from' that can be moved to 'to'
                        List<Schedule> fromSchedules = current.stream()
                                .filter(s -> s.getStaff().getId() == from)
                                .collect(Collectors.toList());
                        Collections.shuffle(fromSchedules, rng);
                        
                        for (Schedule s : fromSchedules) {
                            if (canSwapShift(current, to, s, runBlockedDates)) {
                                String shiftTypeId = s.getShiftType().getId();
                                LocalDate date = s.getWorkDate();

                                // Update blocked dates for swap
                                if ("L01".equals(shiftTypeId)) {
                                    Set<LocalDate> fromBlocked = runBlockedDates.computeIfAbsent(from, k -> new HashSet<>());
                                    fromBlocked.remove(date.plusDays(1));
                                    LocalDate compDate = compensationDateCalculator.calculate(date);
                                    if (compDate != null) fromBlocked.remove(compDate);

                                    Set<LocalDate> toBlocked = runBlockedDates.computeIfAbsent(to, k -> new HashSet<>());
                                    toBlocked.add(date.plusDays(1));
                                    if (compDate != null) toBlocked.add(compDate);
                                }

                                s.setStaff(staffMap.get(to));
                                s.setRequirement(findMatchingRequirement(
                                        staffMap.get(to), s.getWorkDate(), s.getShiftType().getId(), requirements));
                                swapped = true;
                                break;
                            }
                        }
                        if (swapped) break;
                    }
                    if (swapped) break;
                }
                
                if (!swapped) break;
            }

            int score = current.size();
            if (score > bestCount) {
                bestCount = score;
                bestSchedules = new ArrayList<>(current);
            }
        }

        log.info("RandomRestartHC: {} schedules in {}ms", bestSchedules.size(),
                System.currentTimeMillis() - start);
        return bestSchedules;
    }

    private List<Schedule> randomGreedy(List<Staff> staff, List<ShiftRequirement> reqs,
                                          SchedulePeriod period, AlgorithmConfigService.AlgorithmRuntimeConfig config,
                                          Set<Integer> excluded, Map<Integer, Staff> staffMap, Random rng,
                                          Map<Integer, Set<LocalDate>> blockedDates) {
        List<Schedule> result = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        Map<Integer, Integer> counts = new HashMap<>();
        Map<String, String> shiftPerStaffDay = new HashMap<>(); // "staffId|date" -> shiftTypeId

        int maxShifts = config != null && config.getMaxShiftsPerStaff() > 0
                ? config.getMaxShiftsPerStaff() : Integer.MAX_VALUE;

        // Process L01 → L02 → L03 → L04 (respecting constraint order)
        for (ShiftRequirement req : reqs) {
            int required = req.getRequiredStaffCount();
            Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
            String shiftType = req.getShiftType().getId();

            List<Integer> eligible = staff.stream()
                    .filter(s -> excluded == null || !excluded.contains(s.getId()))
                    .filter(s -> !assigned.contains(s.getId() + "|" + req.getWorkDate()))
                    .filter(s -> counts.getOrDefault(s.getId(), 0) < maxShifts)
                    .filter(s -> {
                        Set<LocalDate> blocked = blockedDates.get(s.getId());
                        return blocked == null || !blocked.contains(req.getWorkDate());
                    })
                    .filter(s -> specId == null || (s.getSpecialty() != null && s.getSpecialty().getId().equals(specId)))
                    .filter(s -> !hasConflict(s.getId(), req.getWorkDate(), shiftType, shiftPerStaffDay))
                    .map(Staff::getId)
                    .collect(Collectors.toList());

            Collections.shuffle(eligible, rng);
            for (int i = 0; i < Math.min(required, eligible.size()); i++) {
                int sid = eligible.get(i);
                String key = sid + "|" + req.getWorkDate();
                if (!assigned.contains(key)) {
                    assigned.add(key);
                    counts.merge(sid, 1, Integer::sum);
                    shiftPerStaffDay.put(key, shiftType);
                    
                    // Dynamically propagate blocked dates for L01
                    if ("L01".equals(shiftType)) {
                        blockedDates.computeIfAbsent(sid, k -> new HashSet<>()).add(req.getWorkDate().plusDays(1));
                        LocalDate compDate = compensationDateCalculator.calculate(req.getWorkDate());
                        if (compDate != null) {
                            blockedDates.computeIfAbsent(sid, k -> new HashSet<>()).add(compDate);
                        }
                    }

                    Schedule s = new Schedule();
                    s.setStaff(staffMap.get(sid));
                    s.setPeriod(period);
                    s.setWorkDate(req.getWorkDate());
                    s.setShiftType(req.getShiftType());
                    s.setRequirement(req);
                    s.setHasConflict(false);
                    result.add(s);
                }
            }
        }
        return result;
    }

    private boolean isConflictPair(String t1, String t2) {
        if ("L01".equals(t1) && !"L01".equals(t2)) return true;
        if ("L01".equals(t2) && !"L01".equals(t1)) return true;
        if (("L03".equals(t1) && "L04".equals(t2)) || ("L04".equals(t1) && "L03".equals(t2))) return true;
        return false;
    }

    private boolean canSwapShift(List<Schedule> schedules, int to, Schedule s, Map<Integer, Set<LocalDate>> blockedDates) {
        LocalDate date = s.getWorkDate();
        String shiftTypeId = s.getShiftType().getId();

        // 1. Check database-loaded blocked dates for 'to'
        Set<LocalDate> blocked = blockedDates.get(to);
        if (blocked != null && blocked.contains(date)) return false;

        // 2. Check same-day conflict & adjacent L01 conflict for 'to'
        for (Schedule ex : schedules) {
            if (ex.getStaff().getId() != to) continue;

            // Same day same type or conflict
            if (ex.getWorkDate().equals(date)) {
                if (ex.getShiftType().getId().equals(shiftTypeId) || isConflictPair(shiftTypeId, ex.getShiftType().getId())) {
                    return false;
                }
            }

            // If we are moving L01 to 'to': they cannot have L01 yesterday or tomorrow
            if ("L01".equals(shiftTypeId)) {
                long diff = Math.abs(ex.getWorkDate().toEpochDay() - date.toEpochDay());
                if (diff == 1) return false;
            }

            // If 'to' already has L01 on some day: date cannot be day N+1 or compensation day
            if ("L01".equals(ex.getShiftType().getId())) {
                long diff = date.toEpochDay() - ex.getWorkDate().toEpochDay();
                if (diff == 1) return false;
                LocalDate compDate = compensationDateCalculator.calculate(ex.getWorkDate());
                if (date.equals(compDate)) return false;
            }
        }
        return true;
    }

    /** Kiểm tra ràng buộc: L01 vs all và L03+L04 cùng ngày = conflict */
    private boolean hasConflict(int staffId, LocalDate date, String newType,
                                Map<String, String> shiftPerStaffDay) {
        String existingType = shiftPerStaffDay.get(staffId + "|" + date);
        if (existingType != null) {
            if (isConflictPair(newType, existingType)) {
                return true;
            }
        }
        return false;
    }

    private com.hospital.scheduler.entity.ShiftType findShiftType(
            String id, List<ShiftRequirement> reqs) {
        return reqs.stream().filter(r -> r.getShiftType().getId().equals(id))
                .findFirst().map(ShiftRequirement::getShiftType).orElse(null);
    }

    private ShiftRequirement findMatchingRequirement(Staff staff, LocalDate workDate,
            String shiftTypeId, List<ShiftRequirement> reqs) {
        List<ShiftRequirement> candidates = reqs.stream()
                .filter(r -> r.getShiftType().getId().equals(shiftTypeId)
                        && r.getWorkDate().equals(workDate))
                .toList();
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        if (staff.getSpecialty() != null) {
            for (ShiftRequirement r : candidates) {
                if (r.getSpecialty() != null && r.getSpecialty().getId().equals(staff.getSpecialty().getId())) {
                    return r;
                }
            }
        }
        return candidates.get(0);
    }
}
