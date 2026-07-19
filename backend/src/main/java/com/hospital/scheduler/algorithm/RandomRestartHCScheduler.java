package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Random Restart Hill Climbing — multiple random greedy starts + hill climbing.
 * Each restart runs {@code NUM_RESTARTS × MAX_ITER} neighbour moves.
 * Move = swap a schedule from overloaded staff → underloaded staff, respecting
 * L01+L02, L03+L04, consecutive L01, and L04 specialty constraints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RandomRestartHCScheduler {

    private final CompensationDateCalculator compensationDateCalculator;

    private static final int DEFAULT_NUM_RESTARTS = 12;
    private static final int DEFAULT_MAX_ITER = 500;
    private static final String[] SHIFT_TYPES = {"L01", "L02", "L03", "L04"};
    private static final double COVERAGE_WEIGHT = 0.7;
    private static final double FAIRNESS_WEIGHT = 0.3;
    private static final double CONFLICT_PENALTY = 0.2;

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds) {

        long start = System.currentTimeMillis();
        Random rng = new Random();
        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        int numRestarts = DEFAULT_NUM_RESTARTS;
        int maxIter = DEFAULT_MAX_ITER;
        int totalRequired = requirements.stream()
                .mapToInt(ShiftRequirement::getRequiredStaffCount).sum();

        List<Schedule> bestSchedules = new ArrayList<>();
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int restart = 0; restart < numRestarts; restart++) {
            List<Schedule> current = randomGreedy(activeStaff, requirements, period,
                    runtimeConfig, excludedStaffIds, staffMap, rng);
            if (current.isEmpty()) continue;

            double currentScore = score(current, requirements, totalRequired);
            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestSchedules = new ArrayList<>(current);
            }

            for (int iter = 0; iter < maxIter && !current.isEmpty(); iter++) {
                Map<Integer, Integer> cnt = new HashMap<>();
                for (Schedule s : current) cnt.merge(s.getStaff().getId(), 1, Integer::sum);

                int minCnt = cnt.values().stream().mapToInt(Integer::intValue).min().orElse(0);
                int maxCnt = cnt.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                if (maxCnt - minCnt <= 1) break; // Already balanced

                List<Integer> overloaded = cnt.entrySet().stream()
                        .filter(e -> e.getValue() > minCnt + 1)
                        .map(Map.Entry::getKey).collect(Collectors.toList());
                List<Integer> underloaded = cnt.entrySet().stream()
                        .filter(e -> e.getValue() <= minCnt)
                        .map(Map.Entry::getKey).collect(Collectors.toList());
                if (overloaded.isEmpty() || underloaded.isEmpty()) break;

                Collections.shuffle(overloaded, rng);
                Collections.shuffle(underloaded, rng);

                boolean swapped = trySwap(current, staffMap, requirements, overloaded, underloaded, rng);
                if (!swapped) break;

                double newScore = score(current, requirements, totalRequired);
                if (newScore > bestScore) {
                    bestScore = newScore;
                    bestSchedules = new ArrayList<>(current);
                }
            }
        }

        log.info("RandomRestartHC: {} schedules in {}ms (restarts={}, score={})",
                bestSchedules.size(), System.currentTimeMillis() - start, numRestarts,
                String.format("%.3f", bestScore));
        return bestSchedules;
    }

    /**
     * Try moving one schedule from an overloaded staff to an underloaded staff.
     * Returns true iff any swap was applied.
     */
    private boolean trySwap(List<Schedule> current, Map<Integer, Staff> staffMap,
                            List<ShiftRequirement> requirements,
                            List<Integer> overloaded, List<Integer> underloaded, Random rng) {
        for (int from : overloaded) {
            for (int to : underloaded) {
                List<Schedule> fromSchedules = current.stream()
                        .filter(s -> s.getStaff().getId() == from)
                        .collect(Collectors.toList());
                Collections.shuffle(fromSchedules, rng);

                for (Schedule s : fromSchedules) {
                    if (!canTake(current, to, s)) continue;
                    if (hasCompensationDay(current, to, s.getWorkDate())) continue;
                    if (!matchesSpecialty(staffMap.get(to), s, requirements)) continue;

                    s.setStaff(staffMap.get(to));
s.setRequirement(ScheduleConflictUtils.findMatchingRequirement(
                        staffMap.get(to), s.getWorkDate(), s.getShiftType().getId(), requirements));
                    return true;
                }
            }
        }
        return false;
    }

    /** Same-day L01+L02 / L03+L04 + adjacent-day consecutive L01. */
    private boolean canTake(List<Schedule> current, int staffId, Schedule candidate) {
        LocalDate date = candidate.getWorkDate();
        String type = candidate.getShiftType().getId();
        for (Schedule ex : current) {
            if (ex.getStaff().getId() != staffId) continue;
            if (ex == candidate) continue;
            if (ex.getWorkDate().equals(date)) {
                String exType = ex.getShiftType().getId();
                if (exType.equals(type)) return false; // already has this type that day
                if (ScheduleConflictUtils.isBusinessConflict(type, exType)) return false;
            }
            // Consecutive L01
            if ("L01".equals(type) && "L01".equals(ex.getShiftType().getId())) {
                long diff = Math.abs(ex.getWorkDate().toEpochDay() - date.toEpochDay());
                if (diff == 1) return false;
            }
        }
        return true;
    }

    /** L04 must match staff specialty of the requirement. */
    private boolean matchesSpecialty(Staff staff, Schedule s, List<ShiftRequirement> reqs) {
        if (!"L04".equals(s.getShiftType().getId())) return true;
        ShiftRequirement req = ScheduleConflictUtils.findMatchingRequirement(staff, s.getWorkDate(),
                s.getShiftType().getId(), reqs);
        if (req == null || req.getSpecialty() == null) return true; // no spec required
        if (staff.getSpecialty() == null) return false;
        return staff.getSpecialty().getId().equals(req.getSpecialty().getId());
    }

    private List<Schedule> randomGreedy(List<Staff> staff, List<ShiftRequirement> reqs,
                                          SchedulePeriod period, AlgorithmConfigService.AlgorithmRuntimeConfig config,
                                          Set<Integer> excluded, Map<Integer, Staff> staffMap, Random rng) {
        List<Schedule> result = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        Map<Integer, Integer> counts = new HashMap<>();
        // Indexed: staffId -> date -> shiftTypeId (O(1) lookup vs O(N) startsWith scan)
        Map<Integer, Map<LocalDate, String>> shiftPerStaff = new HashMap<>();
        Map<Integer, Set<LocalDate>> staffCompDays = new HashMap<>();

        int maxShifts = config != null && config.getMaxShiftsPerStaff() > 0
                ? config.getMaxShiftsPerStaff() : Integer.MAX_VALUE;

        for (ShiftRequirement req : reqs) {
            int required = req.getRequiredStaffCount();
            Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
            String shiftType = req.getShiftType().getId();

            List<Integer> eligible = staff.stream()
                    .filter(s -> excluded == null || !excluded.contains(s.getId()))
                    .filter(s -> !assigned.contains(s.getId() + "|" + req.getWorkDate()))
                    .filter(s -> counts.getOrDefault(s.getId(), 0) < maxShifts)
                    .filter(s -> specId == null || (s.getSpecialty() != null && s.getSpecialty().getId().equals(specId)))
                    .filter(s -> !hasConflict(s.getId(), req.getWorkDate(), shiftType, shiftPerStaff))
                    .filter(s -> {
                        Set<LocalDate> compDays = staffCompDays.get(s.getId());
                        return compDays == null || !compDays.contains(req.getWorkDate());
                    })
                    .map(Staff::getId)
                    .collect(Collectors.toList());

            Collections.shuffle(eligible, rng);
            for (int i = 0; i < Math.min(required, eligible.size()); i++) {
                int sid = eligible.get(i);
                String key = sid + "|" + req.getWorkDate();
                if (!assigned.contains(key)) {
                    assigned.add(key);
                    counts.merge(sid, 1, Integer::sum);
                    shiftPerStaff.computeIfAbsent(sid, k -> new HashMap<>())
                            .put(req.getWorkDate(), shiftType);
                    if ("L01".equals(shiftType)) {
                        LocalDate compDate = compensationDateCalculator.calculate(req.getWorkDate());
                        if (compDate != null) {
                            staffCompDays.computeIfAbsent(sid, k -> new HashSet<>()).add(compDate);
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

    /** Score = coverage × 0.7 + fairness × 0.3 − conflict_penalty. */
    private double score(List<Schedule> schedules, List<ShiftRequirement> reqs, int totalRequired) {
        double coverage = totalRequired > 0 ? (double) schedules.size() / totalRequired : 0;

        Set<Integer> assignedIds = new HashSet<>();
        Map<Integer, Integer> counts = new HashMap<>();
        for (Schedule s : schedules) {
            assignedIds.add(s.getStaff().getId());
            counts.merge(s.getStaff().getId(), 1, Integer::sum);
        }
        double fairness = 1.0;
        if (!assignedIds.isEmpty()) {
            double mean = (double) schedules.size() / assignedIds.size();
            if (mean > 0) {
                double variance = assignedIds.stream()
                        .mapToDouble(id -> Math.pow(counts.getOrDefault(id, 0) - mean, 2))
                        .average().orElse(0);
                fairness = Math.max(0, 1 - Math.sqrt(variance) / mean);
            }
        }
        int conflicts = countConflicts(schedules);
        return COVERAGE_WEIGHT * coverage + FAIRNESS_WEIGHT * fairness - conflicts * CONFLICT_PENALTY;
    }

    private int countConflicts(List<Schedule> schedules) {
        int conflicts = 0;
        Map<Integer, Map<LocalDate, String>> byStaffDay = new HashMap<>();
        for (Schedule s : schedules) {
            Map<LocalDate, String> days = byStaffDay.computeIfAbsent(s.getStaff().getId(), k -> new HashMap<>());
            String existing = days.get(s.getWorkDate());
            if (existing != null && ScheduleConflictUtils.isBusinessConflict(s.getShiftType().getId(), existing)) conflicts++;
            days.put(s.getWorkDate(), s.getShiftType().getId());
        }
        Map<Integer, List<LocalDate>> l01ByStaff = new HashMap<>();
        for (Schedule s : schedules) {
            if ("L01".equals(s.getShiftType().getId())) {
                l01ByStaff.computeIfAbsent(s.getStaff().getId(), k -> new ArrayList<>())
                        .add(s.getWorkDate());
            }
        }
        for (List<LocalDate> dates : l01ByStaff.values()) {
            Collections.sort(dates);
            for (int i = 1; i < dates.size(); i++) {
                if (dates.get(i).toEpochDay() - dates.get(i - 1).toEpochDay() == 1) conflicts++;
            }
        }
        return conflicts;
    }

    private boolean hasConflict(int staffId, LocalDate date, String newType,
                                Map<Integer, Map<LocalDate, String>> shiftPerStaff) {
        Map<LocalDate, String> days = shiftPerStaff.get(staffId);
        if (days == null) return false;
        String existing = days.get(date);
        return existing != null && ScheduleConflictUtils.isBusinessConflict(newType, existing);
    }

    private boolean hasCompensationDay(List<Schedule> schedules, int staffId, LocalDate date) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            if (!"L01".equals(s.getShiftType().getId())) continue;
            LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
            if (compDate != null && compDate.equals(date)) return true;
        }
        return false;
    }

    private boolean isBusinessConflict(String a, String b) {
        return ("L01".equals(a) && "L02".equals(b))
            || ("L02".equals(a) && "L01".equals(b))
            || ("L03".equals(a) && "L04".equals(b))
            || ("L04".equals(a) && "L03".equals(b));
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