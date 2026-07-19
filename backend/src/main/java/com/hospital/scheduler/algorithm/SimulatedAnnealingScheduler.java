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
 * Simulated Annealing scheduler — ported from algorithm_comparison.ipynb.
 * Uses greedy initial solution then SA refinement.
 *
 * <p>Score = 0.7 × coverage + 0.3 × fairness − 0.2 × conflicts.
 * Coverage is per-requirement (date+shiftType+specialty), not total count.
 * SA mutation swaps a shift between two staff on the same date+type to
 * preserve coverage while improving fairness.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatedAnnealingScheduler {

    private final CompensationDateCalculator compensationDateCalculator;
    private static final int DEFAULT_MAX_ITER = 2000;
    private static final double INITIAL_TEMP = 0.05;
    private static final double COOLING_RATE = 0.97;
    private static final double COVERAGE_WEIGHT = 0.7;
    private static final double FAIRNESS_WEIGHT = 0.3;
    private static final double CONFLICT_PENALTY = 2.0;

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
        int maxIter = runtimeConfig != null && runtimeConfig.getBeamWidth() > 0
                ? runtimeConfig.getBeamWidth() * 100 : DEFAULT_MAX_ITER;
        int totalRequired = requirements.stream()
                .mapToInt(ShiftRequirement::getRequiredStaffCount).sum();

        // Phase 1: Initial greedy solution
        List<Schedule> current = greedyInitial(activeStaff, requirements, period,
                runtimeConfig, excludedStaffIds, staffMap, rng);
        if (current.isEmpty()) return current;

        double currentScore = score(current, requirements, staffMap, totalRequired);
        List<Schedule> bestSolution = new ArrayList<>(current);
        double bestScore = currentScore;

        double T = INITIAL_TEMP;

        // Phase 2: Simulated Annealing — swap shifts between staff to improve fairness
        for (int iter = 0; iter < maxIter && current.size() >= 2; iter++) {
            // Pick two random schedule entries with different staff on same date+type
            Schedule a = current.get(rng.nextInt(current.size()));
            // Find candidates with same date+shiftType but different staff
            List<Schedule> candidates = current.stream()
                    .filter(b -> b != a && !b.getStaff().getId().equals(a.getStaff().getId())
                            && b.getWorkDate().equals(a.getWorkDate())
                            && b.getShiftType().getId().equals(a.getShiftType().getId()))
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) continue;

            Schedule b = candidates.get(rng.nextInt(candidates.size()));

            // Swap staff assignments: a gets b's staff, b gets a's staff
            Staff staffA = a.getStaff();
            Staff staffB = b.getStaff();

            // Check conflicts after swap
            if (hasConflict(current, staffA.getId(), b.getWorkDate(), b.getShiftType().getId()))
                continue;
            if (hasConflict(current, staffB.getId(), a.getWorkDate(), a.getShiftType().getId()))
                continue;

            // Also check: same staff might end up with duplicate on same day if
            // swapping different dates — but we already filter same date, so swaps
            // are same-date and duplicate shiftType would be caught by conflict check.

            a.setStaff(staffB);
            b.setStaff(staffA);

            double newScore = score(current, requirements, staffMap, totalRequired);
            double delta = newScore - currentScore;

            if (delta > 0 || rng.nextDouble() < Math.exp(delta / Math.max(T, 1e-9))) {
                currentScore = newScore;
                if (newScore > bestScore) {
                    bestScore = newScore;
                    bestSolution = new ArrayList<>(current);
                }
            } else {
                // Revert
                a.setStaff(staffA);
                b.setStaff(staffB);
            }

            T *= COOLING_RATE;
        }

        log.info("SimulatedAnnealing: {} schedules (best={}) in {}ms (maxIter={})",
                bestSolution.size(), String.format("%.3f", bestScore),
                System.currentTimeMillis() - start, maxIter);
        return bestSolution;
    }

    private List<Schedule> greedyInitial(List<Staff> staff, List<ShiftRequirement> reqs,
                                          SchedulePeriod period, AlgorithmConfigService.AlgorithmRuntimeConfig config,
                                          Set<Integer> excluded, Map<Integer, Staff> staffMap, Random rng) {
        List<Schedule> result = new ArrayList<>();
        // Track (staffId|date → set of shiftType ids) — allow non-conflicting same-day combos
        Map<String, Set<String>> assignedTypesPerDay = new HashMap<>();
        Map<Integer, Integer> counts = new HashMap<>();
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
                    .filter(s -> counts.getOrDefault(s.getId(), 0) < maxShifts)
                    .filter(s -> specId == null || (s.getSpecialty() != null && s.getSpecialty().getId().equals(specId)))
                    .filter(s -> {
                        if (specId == null && !"L04".equals(shiftType) && s.getSpecialty() == null) return false;
                        return true;
                    })
                    .filter(s -> !hasConflict(s.getId(), req.getWorkDate(), shiftType, shiftPerStaff))
                    .filter(s -> {
                        // Block if same day already has conflicting shift type
                        String dayKey = s.getId() + "|" + req.getWorkDate();
                        Set<String> todayTypes = assignedTypesPerDay.getOrDefault(dayKey, Collections.emptySet());
                        if (ScheduleConflictUtils.isBusinessConflict(shiftType,
                                todayTypes.stream().findFirst().orElse(null))) return false;
                        // Block duplicate shift type same day
                        return !todayTypes.contains(shiftType);
                    })
                    .filter(s -> {
                        Set<LocalDate> compDays = staffCompDays.get(s.getId());
                        return compDays == null || !compDays.contains(req.getWorkDate());
                    })
                    .map(Staff::getId)
                    .collect(Collectors.toList());

            Collections.shuffle(eligible, rng);
            for (int i = 0; i < Math.min(required, eligible.size()); i++) {
                int sid = eligible.get(i);
                assignedTypesPerDay.computeIfAbsent(sid + "|" + req.getWorkDate(), k -> new HashSet<>())
                        .add(shiftType);
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
        return result;
    }

    /** Score = coverage × 0.7 + fairness × 0.3 − conflict_penalty. */
    private double score(List<Schedule> schedules, List<ShiftRequirement> reqs,
                         Map<Integer, Staff> staffMap, int totalRequired) {
        // Per-requirement coverage
        Map<String, Integer> requiredCount = new HashMap<>();
        for (ShiftRequirement r : reqs) {
            String key = r.getWorkDate() + "|" + r.getShiftType().getId()
                       + "|" + (r.getSpecialty() != null ? r.getSpecialty().getId() : 0);
            requiredCount.merge(key, r.getRequiredStaffCount(), Integer::sum);
        }
        Map<String, Integer> assignedCount = new HashMap<>();
        for (Schedule s : schedules) {
            if (s.getStaff() == null) continue;
            String key = s.getWorkDate() + "|" + s.getShiftType().getId()
                       + "|" + (s.getRequirement() != null && s.getRequirement().getSpecialty() != null
                                 ? s.getRequirement().getSpecialty().getId() : 0);
            assignedCount.merge(key, 1, Integer::sum);
        }
        int fulfilled = 0, totalReqSlots = 0;
        for (var e : requiredCount.entrySet()) {
            int req = e.getValue();
            int asn = assignedCount.getOrDefault(e.getKey(), 0);
            fulfilled += Math.min(req, asn);
            totalReqSlots += req;
        }
        double coverage = totalReqSlots > 0 ? (double) fulfilled / totalReqSlots : 0;

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
            if (existing != null && ScheduleConflictUtils.isBusinessConflict(s.getShiftType().getId(), existing)) {
                conflicts++;
            }
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

    /** Same-day L01↔L02 / L03↔L04, consecutive L01, and L01 → compensation-day guards. */
    private boolean hasConflict(List<Schedule> schedules, int staffId,
                                LocalDate workDate, String newType) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            if (s.getWorkDate().equals(workDate)) {
                String existingType = s.getShiftType().getId();
                if (ScheduleConflictUtils.isBusinessConflict(newType, existingType)) return true;
            }
            if ("L01".equals(newType) && "L01".equals(s.getShiftType().getId())) {
                if (Math.abs(s.getWorkDate().toEpochDay() - workDate.toEpochDay()) == 1) return true;
            }
            if ("L01".equals(s.getShiftType().getId())) {
                LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                if (compDate != null && compDate.equals(workDate)) return true;
            }
        }
        return false;
    }

    /** Indexed-lookup variant for greedyInitial — checks same-day conflict + consecutive L01. */
    private boolean hasConflict(int staffId, LocalDate date, String newType,
                                Map<Integer, Map<LocalDate, String>> shiftPerStaff) {
        Map<LocalDate, String> days = shiftPerStaff.get(staffId);
        if (days == null) return false;
        // Same-day business conflict
        String existing = days.get(date);
        if (existing != null && ScheduleConflictUtils.isBusinessConflict(newType, existing)) return true;
        // Consecutive L01
        if ("L01".equals(newType)) {
            String prev = days.get(date.minusDays(1));
            String next = days.get(date.plusDays(1));
            if ("L01".equals(prev) || "L01".equals(next)) return true;
        }
        return false;
    }
}
