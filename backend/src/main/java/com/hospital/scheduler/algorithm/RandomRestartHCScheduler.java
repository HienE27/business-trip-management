package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import static com.hospital.scheduler.algorithm.ArrangementModeSupport.*;
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

    /** L04 cross-specialty config — set per solve() call. */
    private L04CrossSpecialtyConfig l04CrossConfig = L04CrossSpecialtyConfig.DISABLED;

    private static final int DEFAULT_NUM_RESTARTS = 12;
    private static final int DEFAULT_MAX_ITER = 500;
    /**
     * Tự thích ứng theo kích thước bài toán: với kỳ lớn (~900+ NS, 1479 lịch),
     * RRHC đã đạt 100% coverage ngay từ phase random-solution — HC chỉ refine
     * fairness + conflicts nhẹ, không cần 12 restarts × 500 iter (benchmark
     * ~17s CPU). Giảm thành 6 × 250 → ~8s, chất lượng lời giải không đổi
     * (đã verify: coverage 100%, conflicts ≤ 2). Period nhỏ giữ defaults.
     */
    private static final int LARGE_PERIOD_RESTARTS = 6;
    private static final int LARGE_PERIOD_MAX_ITER = 250;
    private static final int LARGE_PERIOD_THRESHOLD_SLOTS = 1000;
    private static final double COVERAGE_WEIGHT = 0.7;
    private static final double FAIRNESS_WEIGHT = 0.3;
    private static final double CONFLICT_PENALTY = 2.0;

    // Per-type rebalance objective weights (PA2' — TASK-PHASEC-REBALANCE).
    // At capacity (all staff at maxShifts), any single-staff move creates 29v31 total
    // imbalance. Total fairness guardrail kept low so per-type CV improvement dominates.
    private static final double PER_TYPE_WEIGHT = 0.8;
    private static final double TOTAL_FAIRNESS_WEIGHT = 0.2;

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds,
            L04CrossSpecialtyConfig l04CrossConfig) {

        this.l04CrossConfig = l04CrossConfig != null ? l04CrossConfig : L04CrossSpecialtyConfig.DISABLED;

        long start = System.currentTimeMillis();
        Random rng = new Random();
        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        int totalRequiredSlots = requirements.stream()
                .mapToInt(ShiftRequirement::getRequiredStaffCount).sum();
        int l01Window = runtimeConfig != null ? runtimeConfig.getL01AdjacentDayWindow() : 1;
        // Adaptive: kỳ lớn → giảm restarts/iter (HC chỉ refine nhẹ sau khi randomSolution
        // đã đạt coverage). Benchmark period 5 (~1479 slots): 17s → 8s, cùng chất lượng.
        int numRestarts = totalRequiredSlots >= LARGE_PERIOD_THRESHOLD_SLOTS
                ? LARGE_PERIOD_RESTARTS : DEFAULT_NUM_RESTARTS;
        int maxIter = totalRequiredSlots >= LARGE_PERIOD_THRESHOLD_SLOTS
                ? LARGE_PERIOD_MAX_ITER : DEFAULT_MAX_ITER;

        List<Schedule> bestSchedules = new ArrayList<>();
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int restart = 0; restart < numRestarts; restart++) {
            // Phase 1: random feasible solution
            List<Schedule> current = randomSolution(activeStaff, requirements, period,
                    runtimeConfig, excludedStaffIds, staffMap, rng, l01Window);
            if (current.isEmpty()) continue;

            double currentScore = score(current, requirements, staffMap, l01Window, runtimeConfig);
            if (currentScore > bestScore) {
                bestScore = currentScore;
                bestSchedules = deepCopy(current);
            }

            // Phase 2: first-choice hill climbing — random neighbor, accept first improving
            for (int iter = 0; iter < maxIter; iter++) {
                boolean accepted = false;
                // Try up to maxTries random neighbors before giving up (local optimum)
                int maxTries = Math.min(20, current.size() * activeStaff.size());
                for (int t = 0; t < maxTries; t++) {
                    if (rng.nextBoolean()) {
                        accepted = tryRandomMove(current, activeStaff, excludedStaffIds, staffMap, requirements, runtimeConfig, rng, l01Window);
                    } else {
                        accepted = tryRandomSwap(current, requirements, staffMap, rng, l01Window, runtimeConfig);
                    }
                    if (accepted) break;
                }
                if (!accepted) break; // local optimum — restart

                double newScore = score(current, requirements, staffMap, l01Window, runtimeConfig);
                if (newScore > bestScore) {
                    bestScore = newScore;
                    bestSchedules = deepCopy(current);
                }
            }
        }

        // Phase 3: Fairness rebalance — move shifts from overloaded to underloaded staff
        if (!bestSchedules.isEmpty()) {
            fairnessRebalance(bestSchedules, activeStaff, excludedStaffIds, staffMap, requirements, runtimeConfig, rng, l01Window);
        }

        log.info("RandomRestartHC: {} schedules in {}ms (restarts={}, score={})",
                bestSchedules.size(), System.currentTimeMillis() - start, numRestarts,
                String.format("%.3f", bestScore));
        return bestSchedules;
    }

    /** Deep-copy so later HC mutations don't corrupt the stored best. */
    private List<Schedule> deepCopy(List<Schedule> src) {
        List<Schedule> copy = new ArrayList<>(src.size());
        for (Schedule s : src) {
            Schedule c = new Schedule();
            c.setStaff(s.getStaff());
            c.setPeriod(s.getPeriod());
            c.setWorkDate(s.getWorkDate());
            c.setShiftType(s.getShiftType());
            c.setRequirement(s.getRequirement());
            c.setHasConflict(s.getHasConflict());
            copy.add(c);
        }
        return copy;
    }

    /**
     * Repeatedly move a shift from the most overloaded staff to the most underloaded.
     * Accepts only if it doesn't create conflicts and improves fairness.
     * Includes per-type rebalance phase (TASK-L01-FAIRNESS Phase C).
     */
    private void fairnessRebalance(List<Schedule> schedules, List<Staff> activeStaff,
                                    Set<Integer> excludedIds, Map<Integer, Staff> staffMap,
                                    List<ShiftRequirement> reqs,
                                    AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                    Random rng, int l01Window) {
        int maxShiftsPerDay = runtimeConfig != null && runtimeConfig.getMaxShiftsPerDay() > 0
                ? runtimeConfig.getMaxShiftsPerDay() : Integer.MAX_VALUE;
        // Phase 1: Total-count rebalance (existing logic)
        totalCountRebalanceRRHC(schedules, activeStaff, excludedIds, staffMap, reqs, runtimeConfig, rng, l01Window, maxShiftsPerDay);
        // Phase 2: Per-type rebalance (TASK-L01-FAIRNESS Phase C)
        perTypeRebalanceRRHC(schedules, activeStaff, excludedIds, staffMap, reqs, runtimeConfig, rng, l01Window, maxShiftsPerDay);
    }

    /** Total-count rebalance: move shifts from most overloaded → most underloaded staff. */
    private void totalCountRebalanceRRHC(List<Schedule> schedules, List<Staff> activeStaff,
                                    Set<Integer> excludedIds, Map<Integer, Staff> staffMap,
                                    List<ShiftRequirement> reqs,
                                    AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                    Random rng, int l01Window, int maxShiftsPerDay) {
        int totalRounds = runtimeConfig != null ? runtimeConfig.getRebalanceRoundsTotal() : 80;
        for (int round = 0; round < totalRounds; round++) {
            Map<Integer, Integer> counts = new HashMap<>();
            // Include zero-load staff so rebalance fills empty people
            for (Staff st : activeStaff) {
                if (excludedIds != null && excludedIds.contains(st.getId())) continue;
                counts.put(st.getId(), 0);
            }
            for (Schedule s : schedules) counts.merge(s.getStaff().getId(), 1, Integer::sum);
            if (counts.isEmpty()) break;

            int maxCnt = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int minCnt = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            if (maxCnt - minCnt <= 1) break;

            // Find most overloaded staff
            int overloaded = counts.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue))
                    .get().getKey();
            // Find most underloaded staff (may have 0 assignments)
            int underloaded = counts.entrySet().stream()
                    .min(Comparator.comparingInt(Map.Entry::getValue))
                    .get().getKey();

            // Try moving a random shift from overloaded to underloaded
            List<Schedule> overShifts = schedules.stream()
                    .filter(s -> s.getStaff().getId() == overloaded)
                    .collect(Collectors.toList());
            Collections.shuffle(overShifts, rng);

            boolean moved = false;
            for (Schedule s : overShifts) {
                if (!canTake(schedules, underloaded, s, l01Window)) continue;
                if (hasCompensationDay(schedules, underloaded, s.getWorkDate())) continue;
                if (wouldExceedMaxShiftsPerDay(schedules, underloaded, s.getWorkDate(), maxShiftsPerDay)) continue;
                if (!matchesSpecialtyL04(staffMap.get(underloaded), s.getShiftType().getId(), s.getWorkDate(), reqs))
                    continue;

                double oldScore = score(schedules, reqs, staffMap, l01Window, runtimeConfig);
                Staff origStaff = s.getStaff();
                s.setStaff(staffMap.get(underloaded));
                updateReq(s, reqs);

                double newScore = score(schedules, reqs, staffMap, l01Window, runtimeConfig);
                if (newScore > oldScore) {
                    moved = true;
                    break;
                }
                // Revert
                s.setStaff(origStaff);
                updateReq(s, reqs);
            }
            if (!moved) break;
        }
    }

    /**
     * Per-type rebalance: for L02/L03/L01, move shifts from staff with most of
     * that type to staff with least, if it improves the global score.
     */
    private void perTypeRebalanceRRHC(List<Schedule> schedules, List<Staff> activeStaff,
                                    Set<Integer> excludedIds, Map<Integer, Staff> staffMap,
                                    List<ShiftRequirement> reqs,
                                    AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                    Random rng, int l01Window, int maxShiftsPerDay) {
        String[] typesToBalance = {"L02", "L03", "L01"};
        int totalMoved = 0;
        for (String type : typesToBalance) {
            int perTypeRounds = runtimeConfig != null ? runtimeConfig.getRebalanceRoundsPerType() : 30;
            for (int round = 0; round < perTypeRounds; round++) {
                // Count per-type per-staff
                Map<Integer, Integer> typeCounts = new HashMap<>();
                for (Staff st : activeStaff) {
                    if (excludedIds != null && excludedIds.contains(st.getId())) continue;
                    typeCounts.put(st.getId(), 0);
                }
                for (Schedule s : schedules) {
                    if (type.equals(s.getShiftType().getId())) {
                        typeCounts.merge(s.getStaff().getId(), 1, Integer::sum);
                    }
                }
                if (typeCounts.isEmpty()) break;

                int maxCnt = typeCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                int minCnt = typeCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
                if (maxCnt - minCnt <= 1) break;

                int overloaded = -1, underloaded = -1;
                int overCnt = Integer.MIN_VALUE, underCnt = Integer.MAX_VALUE;
                for (Map.Entry<Integer, Integer> e : typeCounts.entrySet()) {
                    if (e.getValue() > overCnt) { overCnt = e.getValue(); overloaded = e.getKey(); }
                    if (e.getValue() < underCnt) { underCnt = e.getValue(); underloaded = e.getKey(); }
                }
                if (overloaded < 0 || underloaded < 0 || overloaded == underloaded) break;

                // Try to move a shift of this type from overloaded to underloaded
                boolean moved = false;
                for (Schedule s : schedules) {
                    if (s.getStaff().getId() != overloaded) continue;
                    if (!type.equals(s.getShiftType().getId())) continue;
                    if (!canTake(schedules, underloaded, s, l01Window)) continue;
                    if (hasCompensationDay(schedules, underloaded, s.getWorkDate())) continue;
                    if (wouldExceedMaxShiftsPerDay(schedules, underloaded, s.getWorkDate(), maxShiftsPerDay)) continue;
                    if (!matchesSpecialtyL04(staffMap.get(underloaded), s.getShiftType().getId(), s.getWorkDate(), reqs))
                        continue;

                    // Use per-type objective (PA2') instead of global score().
                    double oldObj = perTypeMoveObjective(schedules, type, activeStaff, excludedIds, staffMap, l01Window);
                    Staff origStaff = s.getStaff();
                    s.setStaff(staffMap.get(underloaded));
                    updateReq(s, reqs);

                    double newObj = perTypeMoveObjective(schedules, type, activeStaff, excludedIds, staffMap, l01Window);
                    if (newObj > oldObj) {
                        moved = true;
                        totalMoved++;
                        break;
                    }
                    // Revert
                    s.setStaff(origStaff);
                    updateReq(s, reqs);
                }
                if (!moved) break;
            }
        }
        if (totalMoved > 0) {
            log.info("RandomRestartHC: per-type rebalance moved {} shifts", totalMoved);
        }
    }

    /**
     * Per-type objective for perTypeRebalanceRRHC (PA2' — TASK-PHASEC-REBALANCE).
     *
     * <p>Measures per-type fairness (CV of target-type distribution across staff) +
     * total-count fairness guardrail − conflict penalty. Coverage is omitted because
     * rebalance only reassigns staff on existing schedules — total count unchanged.
     *
     * <p>This objective is independent of the global {@link #score(List, List, Map, int)}
     * function: changes here do NOT affect {@code tryRandomMove}, {@code tryRandomSwap},
     * or {@code totalCountRebalanceRRHC}.
     *
     * <p>At capacity (all staff at maxShifts), any single-staff move creates a short-term
     * total-count imbalance (29 vs 31). The total-fairness guardrail weight is kept low
     * (0.2) so per-type CV improvement dominates. The imbalance is temporary — subsequent
     * moves can rebalance totals, and the final result has the same schedule count.
     *
     * <p>Performance note: builds per-type + total count maps from scratch each call
     * (O(N) over ~600 schedules). Incremental update after each accepted move would
     * avoid the re-scan but adds complexity (track delta for both counts). Given
     * ≤ 180 calls × 600 schedules ≈ 108K hash ops — negligible (< 1ms) — keeping simple.
     */
    private double perTypeMoveObjective(List<Schedule> schedules, String type,
                                         List<Staff> activeStaff, Set<Integer> excludedIds,
                                         Map<Integer, Staff> staffMap, int l01Window) {
        // 1. Per-type fairness: CV of target-type distribution
        Map<Integer, Integer> typeCounts = new HashMap<>();
        for (Staff st : activeStaff) {
            if (excludedIds != null && excludedIds.contains(st.getId())) continue;
            typeCounts.put(st.getId(), 0);
        }
        for (Schedule s : schedules) {
            if (type.equals(s.getShiftType().getId())) {
                typeCounts.merge(s.getStaff().getId(), 1, Integer::sum);
            }
        }
        double tMean = typeCounts.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        double perTypeF = 1.0;
        if (tMean > 0) {
            double tVar = typeCounts.values().stream()
                    .mapToDouble(c -> Math.pow(c - tMean, 2)).average().orElse(0);
            perTypeF = Math.max(0, 1 - Math.sqrt(tVar) / tMean);
        }

        // 2. Total-count fairness guardrail (same CV formula as score())
        Map<Integer, Integer> totalCounts = new HashMap<>();
        for (Integer id : staffMap.keySet()) totalCounts.put(id, 0);
        for (Schedule s : schedules) {
            if (s.getStaff() != null) totalCounts.merge(s.getStaff().getId(), 1, Integer::sum);
        }
        double mean = schedules.isEmpty() ? 0 : (double) schedules.size() / Math.max(1, totalCounts.size());
        double totalF = 1.0;
        if (mean > 0) {
            double var = totalCounts.values().stream()
                    .mapToDouble(c -> Math.pow(c - mean, 2)).average().orElse(0);
            totalF = Math.max(0, 1 - Math.sqrt(var) / mean);
        }

        // 3. Conflicts (reuse countConflicts — existing method)
        int conflicts = countConflicts(schedules, l01Window);

        return PER_TYPE_WEIGHT * perTypeF + TOTAL_FAIRNESS_WEIGHT * totalF - conflicts * CONFLICT_PENALTY;
    }

    /** maxShiftsPerDay guard for move-style operators: returns true if adding one shift
     *  for {@code staffId} on {@code date} would exceed the per-day hard cap. */
    private boolean wouldExceedMaxShiftsPerDay(List<Schedule> schedules, int staffId,
                                               LocalDate date, int maxShiftsPerDay) {
        if (maxShiftsPerDay == Integer.MAX_VALUE) return false;
        long today = 0;
        for (Schedule s : schedules) {
            if (s.getStaff().getId() == staffId && s.getWorkDate().equals(date)) today++;
        }
        return today + 1 > maxShiftsPerDay;
    }

    /** Find eligible staff to move a schedule to (excluding current staff). */
    private List<Integer> findMoveTargets(List<Schedule> current, Schedule s,
                                           List<Staff> activeStaff, Set<Integer> excludedIds,
                                           List<ShiftRequirement> requirements, Random rng) {
        int currentStaffId = s.getStaff().getId();
        return activeStaff.stream()
                .filter(staff -> !staff.getId().equals(currentStaffId))
                .filter(staff -> excludedIds == null || !excludedIds.contains(staff.getId()))
                .filter(staff -> canTake(current, staff.getId(), s, 1)) // dead code path; l01Window=1 (default)
                .filter(staff -> !hasCompensationDay(current, staff.getId(), s.getWorkDate()))
                .filter(staff -> matchesSpecialtyL04(staff, s.getShiftType().getId(), s.getWorkDate(), requirements))
                .map(Staff::getId)
                .collect(Collectors.toList());
    }

	    /** Random feasible solution via shuffled greedy construction. */
	    private List<Schedule> randomSolution(List<Staff> staff, List<ShiftRequirement> reqs,
	                                           SchedulePeriod period, AlgorithmConfigService.AlgorithmRuntimeConfig config,
	                                           Set<Integer> excluded, Map<Integer, Staff> staffMap, Random rng,
	                                           int l01Window) {
	        List<Schedule> result = new ArrayList<>();
	        // Track (staffId|date → set of shiftType ids) — allow non-conflicting same-day combos
	        Map<String, Set<String>> assignedTypesPerDay = new HashMap<>();
	        Map<Integer, Integer> counts = new HashMap<>();
	        Map<Integer, Map<LocalDate, Set<String>>> shiftPerStaff = new HashMap<>();
	        Map<Integer, Set<LocalDate>> staffCompDays = new HashMap<>();
	        // Cross-specialty tracking for L04: key=date|shiftType|specId → count (TASK-02)
	        Map<String, Integer> crossAssignmentCount = new HashMap<>();
	
	        int maxShifts = config != null && config.getMaxShiftsPerStaff() > 0
	                ? config.getMaxShiftsPerStaff() : Integer.MAX_VALUE;
	        int maxShiftsPerDay = config != null && config.getMaxShiftsPerDay() > 0
	                ? config.getMaxShiftsPerDay() : Integer.MAX_VALUE;
	
	        for (ShiftRequirement req : reqs) {
	            int required = req.getRequiredStaffCount();
	            Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
	            String shiftType = req.getShiftType().getId();
	
	            List<Integer> eligible = staff.stream()
	                    .filter(s -> excluded == null || !excluded.contains(s.getId()))
	                    .filter(s -> counts.getOrDefault(s.getId(), 0) < maxShifts)
	                    // Specialty check with cross-specialty support (TASK-02)
	                    .filter(s -> {
	                        if (specId == null) return true;
	                        if (!"L04".equals(shiftType)) {
	                            return s.getSpecialty() != null && s.getSpecialty().getId().equals(specId);
	                        }
	                        // L04 with specialty requirement
	                        boolean matchesSpecialty = s.getSpecialty() != null
	                                && s.getSpecialty().getId().equals(specId);
	                        if (matchesSpecialty) return true;
	                        // Cross-specialty: permitted AND within capacity cap
	                        if (!l04CrossConfig.isPermittedFor(req.getSpecialty() != null
	                                ? req.getSpecialty().getName() : null)) return false;
	                        String crossKey = req.getWorkDate() + "|" + shiftType + "|" + specId;
	                        int crossCap = l04CrossConfig.crossCap(required);
	                        return crossAssignmentCount.getOrDefault(crossKey, 0) < crossCap;
	                    })
                    .filter(s -> !hasConflict(s.getId(), req.getWorkDate(), shiftType, shiftPerStaff, l01Window))
                    .filter(s -> {
                        // maxShiftsPerDay — HARD cap tổng số ca mỗi nhân sự mỗi ngày.
                        if (maxShiftsPerDay != Integer.MAX_VALUE) {
                            Map<LocalDate, Set<String>> dayMap = shiftPerStaff.get(s.getId());
                            Set<String> today = dayMap != null ? dayMap.get(req.getWorkDate()) : null;
                            if (today != null && today.size() >= maxShiftsPerDay) return false;
                        }
                        return true;
                    })
                    .filter(s -> {
                        // Block if same day already has conflicting/duplicate shift type
                        String dayKey = s.getId() + "|" + req.getWorkDate();
                        Set<String> todayTypes = assignedTypesPerDay.getOrDefault(dayKey, Collections.emptySet());
                        if (todayTypes.contains(shiftType)) return false;
                        for (String t : todayTypes) {
                            if (ScheduleConflictUtils.isBusinessConflict(shiftType, t)) return false;
                        }
                        return true;
                    })
                    .filter(s -> {
                        Set<LocalDate> compDays = staffCompDays.get(s.getId());
                        return compDays == null || !compDays.contains(req.getWorkDate());
                    })
                    // null-specialty staff blocked from L01/L02/L03
                    .filter(s -> !(specId == null && !"L04".equals(shiftType) && s.getSpecialty() == null))
                    .map(Staff::getId)
                    .collect(Collectors.toList());

            Collections.shuffle(eligible, rng);
            for (int i = 0; i < Math.min(required, eligible.size()); i++) {
                int sid = eligible.get(i);
                assignedTypesPerDay.computeIfAbsent(sid + "|" + req.getWorkDate(), k -> new HashSet<>())
                        .add(shiftType);
                counts.merge(sid, 1, Integer::sum);
                shiftPerStaff.computeIfAbsent(sid, k -> new HashMap<>())
                        .computeIfAbsent(req.getWorkDate(), k -> new HashSet<>()).add(shiftType);
	                if ("L01".equals(shiftType)) {
	                    LocalDate compDate = compensationDateCalculator.calculate(req.getWorkDate());
	                    if (compDate != null) {
	                        staffCompDays.computeIfAbsent(sid, k -> new HashSet<>()).add(compDate);
	                    }
	                }
	                // Track cross-specialty L04 assignments for ratio cap (TASK-02)
	                if (specId != null && "L04".equals(shiftType)) {
	                    Staff assignedStaff = staffMap.get(sid);
	                    boolean isCross = assignedStaff != null
	                            && assignedStaff.getSpecialty() != null
	                            && !assignedStaff.getSpecialty().getId().equals(specId);
	                    if (isCross) {
	                        String crossKey = req.getWorkDate() + "|" + shiftType + "|" + specId;
	                        crossAssignmentCount.merge(crossKey, 1, Integer::sum);
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

    /** Per-requirement coverage + fairness − conflict penalty − inter_penalty (WITH_INTER_BALANCE).
     *  Inter penalty mirrors ARRANGEMENT_MODE_CONTRACT: interEnabled → 5.0 weight × mean span × 0.02 scale. */
    private double score(List<Schedule> schedules, List<ShiftRequirement> reqs,
                         Map<Integer, Staff> staffMap, int l01Window,
                         AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {
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

        // Fairness over full eligible pool (zero-load staff count as 0)
        Map<Integer, Integer> counts = new HashMap<>();
        if (staffMap != null) {
            for (Integer id : staffMap.keySet()) counts.put(id, 0);
        }
        for (Schedule s : schedules) {
            if (s.getStaff() == null) continue;
            counts.merge(s.getStaff().getId(), 1, Integer::sum);
        }
        double fairness = 1.0;
        if (!counts.isEmpty() && !schedules.isEmpty()) {
            double mean = (double) schedules.size() / counts.size();
            if (mean > 0) {
                double variance = counts.values().stream()
                        .mapToDouble(c -> Math.pow(c - mean, 2))
                        .average().orElse(0);
                fairness = Math.max(0, 1 - Math.sqrt(variance) / mean);
            }
        }
        int conflicts = countConflicts(schedules, l01Window);

        // Soft inter-type penalty: WITH_INTER_BALANCE only (ARRANGEMENT_MODE_CONTRACT)
        double interPenalty = 0;
        if (interEnabled(runtimeConfig)) {
            Map<Integer, Map<String, Integer>> byStaff = typeCountsFromSchedules(schedules);
            double meanSpan = meanInterSpan(byStaff);
            interPenalty = DEFAULT_INTER_WEIGHT * meanSpan * OBJECTIVE_INTER_SCALE;
        }

        return COVERAGE_WEIGHT * coverage + FAIRNESS_WEIGHT * fairness
                - conflicts * CONFLICT_PENALTY - interPenalty;
    }

    private int countConflicts(List<Schedule> schedules, int l01Window) {
        int conflicts = 0;
        Map<Integer, Map<LocalDate, Set<String>>> byStaffDay = new HashMap<>();
        for (Schedule s : schedules) {
            Map<LocalDate, Set<String>> days = byStaffDay.computeIfAbsent(s.getStaff().getId(), k -> new HashMap<>());
            Set<String> existing = days.computeIfAbsent(s.getWorkDate(), k -> new HashSet<>());
            String type = s.getShiftType().getId();
            if (existing.contains(type)) {
                conflicts++;
            } else {
                for (String t : existing) {
                    if (ScheduleConflictUtils.isBusinessConflict(type, t)) { conflicts++; break; }
                }
            }
            existing.add(type);
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
                if (dates.get(i).toEpochDay() - dates.get(i - 1).toEpochDay() <= l01Window) conflicts++;
            }
        }
        return conflicts;
    }

    /** Check if staffId can take this schedule (no conflict, no duplicate type). */
    private boolean canTake(List<Schedule> current, int staffId, Schedule candidate, int l01Window) {
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
                if (diff <= l01Window) return false;
            }
        }
        return true;
    }

    /** L04 must match staff specialty unless cross-specialty is permitted. */
    private boolean matchesSpecialtyL04(Staff staff, String shiftType, LocalDate date,
                                         List<ShiftRequirement> reqs) {
        if (!"L04".equals(shiftType)) return true;
        ShiftRequirement req = ScheduleConflictUtils.findMatchingRequirement(staff, date, shiftType, reqs);
        if (req == null || req.getSpecialty() == null) return true;
        if (staff.getSpecialty() == null) return false;
        boolean matchesSpecialty = staff.getSpecialty().getId().equals(req.getSpecialty().getId());
        if (matchesSpecialty) return true;
        // Cross-specialty: permitted for this requirement specialty?
        return l04CrossConfig.isPermittedFor(req.getSpecialty().getName());
    }

    /** Indexed-lookup variant for randomSolution — same-day conflict + consecutive L01. */
    private boolean hasConflict(int staffId, LocalDate date, String newType,
                                Map<Integer, Map<LocalDate, Set<String>>> shiftPerStaff,
                                int l01Window) {
        Map<LocalDate, Set<String>> days = shiftPerStaff.get(staffId);
        if (days == null) return false;
        Set<String> existing = days.getOrDefault(date, Collections.emptySet());
        if (existing.contains(newType)) return true;
        for (String t : existing) {
            if (ScheduleConflictUtils.isBusinessConflict(newType, t)) return true;
        }
        if ("L01".equals(newType)) {
            for (int dt = 1; dt <= l01Window; dt++) {
                if (days.getOrDefault(date.minusDays(dt), Collections.emptySet()).contains("L01")) return true;
                if (days.getOrDefault(date.plusDays(dt), Collections.emptySet()).contains("L01")) return true;
            }
        }
        return false;
    }

    /** Full-scan variant: checks if staffId would have a conflict with newType on date
     *  considering ALL existing schedules (used in hill climbing moves). */
    private boolean hasConflict(List<Schedule> schedules, int staffId, LocalDate date, String newType, int l01Window) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            if (s.getWorkDate().equals(date)) {
                String existingType = s.getShiftType().getId();
                if (ScheduleConflictUtils.isBusinessConflict(newType, existingType)) return true;
            }
            if ("L01".equals(newType) && "L01".equals(s.getShiftType().getId())) {
                if (Math.abs(s.getWorkDate().toEpochDay() - date.toEpochDay()) <= l01Window) return true;
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
                                   List<ShiftRequirement> reqs,
                                   AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                   Random rng, int l01Window) {
        Schedule s = current.get(rng.nextInt(current.size()));
        Staff originalStaff = s.getStaff();
        int maxShiftsPerDay = runtimeConfig != null && runtimeConfig.getMaxShiftsPerDay() > 0
                ? runtimeConfig.getMaxShiftsPerDay() : Integer.MAX_VALUE;

        // Pick random eligible target staff
        Staff target = null;
        List<Staff> shuffled = new ArrayList<>(activeStaff);
        Collections.shuffle(shuffled, rng);
        for (Staff st : shuffled) {
            if (st.getId().equals(originalStaff.getId())) continue;
            if (excludedIds != null && excludedIds.contains(st.getId())) continue;
            if (!canTake(current, st.getId(), s, l01Window)) continue;
            if (hasCompensationDay(current, st.getId(), s.getWorkDate())) continue;
            // maxShiftsPerDay — move làm target nhận thêm 1 lịch trong ngày → check không vượt cap.
            if (wouldExceedMaxShiftsPerDay(current, st.getId(), s.getWorkDate(), maxShiftsPerDay)) continue;
            if (!matchesSpecialtyL04(st, s.getShiftType().getId(), s.getWorkDate(), reqs)) continue;
            target = st;
            break;
        }
        if (target == null) return false;

        double oldScore = score(current, reqs, staffMap, l01Window, runtimeConfig);
        s.setStaff(target);
        updateReq(s, reqs);

        if (score(current, reqs, staffMap, l01Window, runtimeConfig) > oldScore) return true;

        // Revert
        s.setStaff(originalStaff);
        updateReq(s, reqs);
        return false;
    }

    /**
     * First-choice swap: pick two random schedules same-date different-staff different-type,
     * accept if strictly improves score. O(N) per call.
     */
    private boolean tryRandomSwap(List<Schedule> current, List<ShiftRequirement> reqs,
                                  Map<Integer, Staff> staffMap, Random rng, int l01Window,
                                  AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {
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
                    if (hasConflict(current, a.getStaff().getId(), a.getWorkDate(), b.getShiftType().getId(), l01Window))
                        continue;
                    if (hasConflict(current, b.getStaff().getId(), b.getWorkDate(), a.getShiftType().getId(), l01Window))
                        continue;
                    if (!matchesSpecialtyL04(a.getStaff(), b.getShiftType().getId(), b.getWorkDate(), reqs))
                        continue;
                    if (!matchesSpecialtyL04(b.getStaff(), a.getShiftType().getId(), a.getWorkDate(), reqs))
                        continue;

                    double oldScore = score(current, reqs, staffMap, l01Window, runtimeConfig);
                    var typeA = a.getShiftType();
                    var typeB = b.getShiftType();
                    a.setShiftType(typeB);
                    b.setShiftType(typeA);
                    updateReq(a, reqs);
                    updateReq(b, reqs);

                    if (score(current, reqs, staffMap, l01Window, runtimeConfig) > oldScore) return true;

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
