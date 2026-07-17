package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    private static final int NUM_RESTARTS = 8;   // Tăng từ 4→8
    private static final int MAX_ITER = 300;       // Tăng từ 150→300
    private static final String[] SHIFT_TYPES = {"L01", "L02", "L03", "L04"};
    private static final Random GLOBAL_RNG = new Random();

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

        for (int restart = 0; restart < NUM_RESTARTS; restart++) {
            List<Schedule> current = randomGreedy(activeStaff, requirements, period,
                    runtimeConfig, excludedStaffIds, staffMap, rng);
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
                            // Check if 'to' can take this shift
                            boolean canTake = current.stream()
                                    .noneMatch(ex -> ex.getStaff().getId() == to 
                                            && ex.getWorkDate().equals(s.getWorkDate())
                                            && (ex.getShiftType().getId().equals(s.getShiftType().getId())
                                                || ("L01".equals(s.getShiftType().getId()) && "L02".equals(ex.getShiftType().getId()))
                                                || ("L02".equals(s.getShiftType().getId()) && "L01".equals(ex.getShiftType().getId()))
                                                || ("L03".equals(s.getShiftType().getId()) && "L04".equals(ex.getShiftType().getId()))
                                                || ("L04".equals(s.getShiftType().getId()) && "L03".equals(ex.getShiftType().getId()))));
                            if (canTake) {
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
                                          Set<Integer> excluded, Map<Integer, Staff> staffMap, Random rng) {
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

    /** Kiểm tra ràng buộc: L01+L02 cùng ngày = conflict, L03+L04 cùng ngày = conflict */
    private boolean hasConflict(int staffId, LocalDate date, String newType,
                                Map<String, String> shiftPerStaffDay) {
        // Check if any existing assignment for this staff on this day has a conflicting type
        for (var e : shiftPerStaffDay.entrySet()) {
            if (!e.getKey().startsWith(staffId + "|")) continue;
            String existingType = e.getValue();
            if (("L01".equals(newType) && "L02".equals(existingType)) ||
                ("L02".equals(newType) && "L01".equals(existingType)) ||
                ("L03".equals(newType) && "L04".equals(existingType)) ||
                ("L04".equals(newType) && "L03".equals(existingType))) {
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
