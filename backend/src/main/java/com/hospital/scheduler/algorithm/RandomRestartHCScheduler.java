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
 * Random Restart Hill Climbing — true RRHC.
 *
 * <p>Each restart:
 * <ol>
 *   <li>Generate a random feasible solution via shuffled greedy construction</li>
 *   <li>Hill climb: repeatedly find an improving neighbor (move or swap)
 *       and accept it. Stop at local optimum (no improving neighbor).</li>
 *   <li>If score improved, update best solution.</li>
 * </ol>
 *
 * <p>Neighbors:
 * <ul>
 *   <li><b>Move</b>: reassign a schedule to a different staff (same date+type)</li>
 *   <li><b>Swap</b>: swap shift types between two schedules on the same date
 *       (different staff, different types)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RandomRestartHCScheduler {

    private final CompensationDateCalculator compensationDateCalculator;

    private static final int DEFAULT_NUM_RESTARTS = 12;
    private static final int DEFAULT_MAX_ITER = 500;
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

        List<Schedule> bestSchedules = new ArrayList<>();
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int restart = 0; restart < numRestarts; restart++) {
            // Phase 1: random feasible solution
            List<Schedule> current = randomSolution(activeStaff, requirements, period,
                    runtimeConfig, excludedStaffIds, staffMap, rng);
            if (current.isEmpty()) continue;

            double currentScore = score(current, requirements);
            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestSchedules = new ArrayList<>(current);
            }

            // Phase 2: first-choice hill climbing — random neighbor, accept first improving
            for (int iter = 0; iter < maxIter; iter++) {
                boolean accepted = false;
                // Try up to maxTries random neighbors before giving up (local optimum)
                int maxTries = Math.min(10, current.size() * activeStaff.size());
                for (int t = 0; t < maxTries; t++) {
                    if (rng.nextBoolean()) {
                        accepted = tryRandomMove(current, activeStaff, excludedStaffIds, staffMap, requirements, rng);
                    } else {
                        accepted = tryRandomSwap(current, requirements, rng);
                    }
                    if (accepted) break;
                }
                if (!accepted) break; // local optimum — restart

                double newScore = score(current, requirements);
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

    /** Find eligible staff to move a schedule to (excluding current staff). */
    private List<Integer> findMoveTargets(List<Schedule> current, Schedule s,
                                           List<Staff> activeStaff, Set<Integer> excludedIds,
                                           List<ShiftRequirement> requirements, Random rng) {
        int currentStaffId = s.getStaff().getId();
        return activeStaff.stream()
                .filter(staff -> !staff.getId().equals(currentStaffId))
                .filter(staff -> excludedIds == null || !excludedIds.contains(staff.getId()))
                .filter(staff -> canTake(current, staff.getId(), s))
                .filter(staff -> !hasCompensationDay(current, staff.getId(), s.getWorkDate()))
                .filter(staff -> matchesSpecialtyL04(staff, s.getShiftType().getId(), s.getWorkDate(), requirements))
                .map(Staff::getId)
                .collect(Collectors.toList());
    }

    /** Random feasible solution via shuffled greedy construction. */
    private List<Schedule> randomSolution(List<Staff> staff, List<ShiftRequirement> reqs,
                                           SchedulePeriod period, AlgorithmConfigService.AlgorithmRuntimeConfig config,
                                           Set<Integer> excluded, Map<Integer, Staff> staffMap, Random rng) {
        List<Schedule> result = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
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

    /** Per-requirement coverage + fairness − conflict penalty. */
    private double score(List<Schedule> schedules, List<ShiftRequirement> reqs) {
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

    /** Check if staffId can take this schedule (no conflict, no duplicate type). */
    private boolean canTake(List<Schedule> current, int staffId, Schedule candidate) {
        LocalDate date = candidate.getWorkDate();
        String type = candidate.getShiftType().getId();
        for (Schedule ex : current) {
            if (ex.getStaff().getId() != staffId) continue;
            if (ex == candidate) continue;
            if (ex.getWorkDate().equals(date)) {
                String exType = ex.getShiftType().getId();
                if (exType.equals(type)) return false;
                if (ScheduleConflictUtils.isBusinessConflict(type, exType)) return false;
            }
            if ("L01".equals(type) && "L01".equals(ex.getShiftType().getId())) {
                long diff = Math.abs(ex.getWorkDate().toEpochDay() - date.toEpochDay());
                if (diff == 1) return false;
            }
        }
        return true;
    }

    /** L04 must match staff specialty. */
    private boolean matchesSpecialtyL04(Staff staff, String shiftType, LocalDate date,
                                         List<ShiftRequirement> reqs) {
        if (!"L04".equals(shiftType)) return true;
        ShiftRequirement req = ScheduleConflictUtils.findMatchingRequirement(staff, date, shiftType, reqs);
        if (req == null || req.getSpecialty() == null) return true;
        if (staff.getSpecialty() == null) return false;
        return staff.getSpecialty().getId().equals(req.getSpecialty().getId());
    }

    /** Indexed-lookup variant for randomSolution (O(1) vs O(N) scan). */
    private boolean hasConflict(int staffId, LocalDate date, String newType,
                                Map<Integer, Map<LocalDate, String>> shiftPerStaff) {
        Map<LocalDate, String> days = shiftPerStaff.get(staffId);
        if (days == null) return false;
        String existing = days.get(date);
        return existing != null && ScheduleConflictUtils.isBusinessConflict(newType, existing);
    }

    /** Full-scan variant: checks if staffId would have a conflict with newType on date
     *  considering ALL existing schedules (used in hill climbing moves). */
    private boolean hasConflict(List<Schedule> schedules, int staffId, LocalDate date, String newType) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            if (s.getWorkDate().equals(date)) {
                String existingType = s.getShiftType().getId();
                if (ScheduleConflictUtils.isBusinessConflict(newType, existingType)) return true;
            }
            if ("L01".equals(newType) && "L01".equals(s.getShiftType().getId())) {
                if (Math.abs(s.getWorkDate().toEpochDay() - date.toEpochDay()) == 1) return true;
            }
            if ("L01".equals(s.getShiftType().getId())) {
                LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                if (compDate != null && compDate.equals(date)) return true;
            }
        }
        return false;
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

    private void updateReq(Schedule s, List<ShiftRequirement> reqs) {
        if (s.getStaff() == null) return;
        ShiftRequirement r = ScheduleConflictUtils.findMatchingRequirement(
                s.getStaff(), s.getWorkDate(), s.getShiftType().getId(), reqs);
        if (r != null) s.setRequirement(r);
    }

    /**
     * First-choice hill climbing: pick random schedule + random target staff.
     * Accept move if it strictly improves score. O(S) per call.
     */
    private boolean tryRandomMove(List<Schedule> current, List<Staff> activeStaff,
                                   Set<Integer> excludedIds, Map<Integer, Staff> staffMap,
                                   List<ShiftRequirement> reqs, Random rng) {
        Schedule s = current.get(rng.nextInt(current.size()));
        Staff originalStaff = s.getStaff();

        // Pick random eligible target staff
        Staff target = null;
        List<Staff> shuffled = new ArrayList<>(activeStaff);
        Collections.shuffle(shuffled, rng);
        for (Staff st : shuffled) {
            if (st.getId().equals(originalStaff.getId())) continue;
            if (excludedIds != null && excludedIds.contains(st.getId())) continue;
            if (!canTake(current, st.getId(), s)) continue;
            if (hasCompensationDay(current, st.getId(), s.getWorkDate())) continue;
            if (!matchesSpecialtyL04(st, s.getShiftType().getId(), s.getWorkDate(), reqs)) continue;
            target = st;
            break;
        }
        if (target == null) return false;

        double oldScore = score(current, reqs);
        s.setStaff(target);
        updateReq(s, reqs);

        if (score(current, reqs) > oldScore) return true;

        // Revert
        s.setStaff(originalStaff);
        updateReq(s, reqs);
        return false;
    }

    /**
     * First-choice swap: pick two random schedules same-date different-staff different-type,
     * accept if strictly improves score. O(N) per call.
     */
    private boolean tryRandomSwap(List<Schedule> current, List<ShiftRequirement> reqs, Random rng) {
        if (current.size() < 2) return false;

        // Build index: date → list of schedules
        Map<LocalDate, List<Schedule>> byDate = new HashMap<>();
        for (Schedule s : current) {
            byDate.computeIfAbsent(s.getWorkDate(), k -> new ArrayList<>()).add(s);
        }

        // Pick a random date that has at least 2 schedules with different types
        List<LocalDate> dates = new ArrayList<>(byDate.keySet());
        Collections.shuffle(dates, rng);
        for (LocalDate d : dates) {
            List<Schedule> daySchedules = byDate.get(d);
            if (daySchedules == null || daySchedules.size() < 2) continue;

            // Find a pair with different staff AND different shift type
            for (int i = 0; i < daySchedules.size(); i++) {
                Schedule a = daySchedules.get(i);
                for (int j = i + 1; j < daySchedules.size(); j++) {
                    Schedule b = daySchedules.get(j);
                    if (a.getStaff().getId().equals(b.getStaff().getId())) continue;
                    if (a.getShiftType().getId().equals(b.getShiftType().getId())) continue;

                    // Check conflict after swap
                    if (hasConflict(current, a.getStaff().getId(), a.getWorkDate(), b.getShiftType().getId()))
                        continue;
                    if (hasConflict(current, b.getStaff().getId(), b.getWorkDate(), a.getShiftType().getId()))
                        continue;
                    if (!matchesSpecialtyL04(a.getStaff(), b.getShiftType().getId(), b.getWorkDate(), reqs))
                        continue;
                    if (!matchesSpecialtyL04(b.getStaff(), a.getShiftType().getId(), a.getWorkDate(), reqs))
                        continue;

                    double oldScore = score(current, reqs);
                    var typeA = a.getShiftType();
                    var typeB = b.getShiftType();
                    a.setShiftType(typeB);
                    b.setShiftType(typeA);
                    updateReq(a, reqs);
                    updateReq(b, reqs);

                    if (score(current, reqs) > oldScore) return true;

                    // Revert
                    a.setShiftType(typeA);
                    b.setShiftType(typeB);
                    updateReq(a, reqs);
                    updateReq(b, reqs);
                    return false; // first pair found → stop
                }
            }
        }
        return false;
    }
}
