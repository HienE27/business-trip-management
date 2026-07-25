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

    /** L04 cross-specialty config — set per solve() call. */
    private L04CrossSpecialtyConfig l04CrossConfig = L04CrossSpecialtyConfig.DISABLED;

    private static final int DEFAULT_MAX_ITER = 2500;
    private static final double INITIAL_TEMP = 0.08;
    private static final double COOLING_RATE = 0.985;
    private static final double COVERAGE_WEIGHT = 0.55;
    private static final double FAIRNESS_WEIGHT = 0.45;
    private static final double CONFLICT_PENALTY = 2.0;

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
        int maxIter = runtimeConfig != null && runtimeConfig.getBeamWidth() > 0
                ? runtimeConfig.getBeamWidth() * 100 : DEFAULT_MAX_ITER;
        int totalRequired = requirements.stream()
                .mapToInt(ShiftRequirement::getRequiredStaffCount).sum();
        int l01Window = runtimeConfig != null ? runtimeConfig.getL01AdjacentDayWindow() : 1;

        // Phase 1: Initial greedy solution
        List<Schedule> current = greedyInitial(activeStaff, requirements, period,
                runtimeConfig, excludedStaffIds, staffMap, rng, l01Window);
        if (current.isEmpty()) return current;

        // Index: staffId -> their schedules (for O(1) conflict checks)
        Map<Integer, List<Schedule>> byStaff = indexByStaff(current);

        double currentScore = score(current, requirements, staffMap, totalRequired, l01Window, runtimeConfig);
        List<Schedule> bestSolution = deepCopy(current);
        double bestScore = currentScore;

        int maxShiftsPerDay = runtimeConfig != null && runtimeConfig.getMaxShiftsPerDay() > 0
                ? runtimeConfig.getMaxShiftsPerDay() : Integer.MAX_VALUE;

        double T = INITIAL_TEMP;

        // Phase 2: Simulated Annealing — move or swap to improve fairness
        for (int iter = 0; iter < maxIter && current.size() >= 2; iter++) {
            if (rng.nextBoolean()) {
                // Move mutation: move a schedule to a different eligible staff
                Schedule s = current.get(rng.nextInt(current.size()));
                Staff origStaff = s.getStaff();
                for (int attempt = 0; attempt < 10; attempt++) {
                    Staff target = activeStaff.get(rng.nextInt(activeStaff.size()));
                    if (target.getId().equals(origStaff.getId())) continue;
                    if (excludedStaffIds != null && excludedStaffIds.contains(target.getId())) continue;
                    if (!canTake(byStaff, target.getId(), s, l01Window)) continue;
                    if (hasCompensationDay(byStaff, target.getId(), s.getWorkDate())) continue;
                    // maxShiftsPerDay — HARD cap tổng số ca mỗi nhân sự mỗi ngày.
                    // Move làm target nhận thêm 1 lịch trong ngày → check không vượt cap.
                    if (wouldExceedMaxShiftsPerDay(byStaff, target.getId(), s.getWorkDate(), maxShiftsPerDay)) continue;
                    if (!matchesSpecialtyL04(target, s.getShiftType().getId(), s.getWorkDate(), requirements))
                        continue;

                    double oldScore = currentScore;
                    s.setStaff(target);
                    updateReq(s, requirements);
                    byStaff.get(origStaff.getId()).remove(s);
                    byStaff.computeIfAbsent(target.getId(), k -> new ArrayList<>()).add(s);

                    double newScore = score(current, requirements, staffMap, totalRequired, l01Window, runtimeConfig);
                    double delta = newScore - oldScore;
                    if (delta > 0 || rng.nextDouble() < Math.exp(delta / Math.max(T, 1e-9))) {
                        currentScore = newScore;
                        if (newScore > bestScore) {
                            bestScore = newScore;
                            bestSolution = deepCopy(current);
                        }
                        break;
                    }
                    // Revert
                    s.setStaff(origStaff);
                    updateReq(s, requirements);
                    byStaff.get(target.getId()).remove(s);
                    byStaff.get(origStaff.getId()).add(s);
                    break;
                }
            } else {
                // Swap mutation: swap staff between two schedules same date+type
                Schedule a = current.get(rng.nextInt(current.size()));
                List<Schedule> candidates = current.stream()
                        .filter(b -> b != a && !b.getStaff().getId().equals(a.getStaff().getId())
                                && b.getWorkDate().equals(a.getWorkDate())
                                && b.getShiftType().getId().equals(a.getShiftType().getId()))
                        .collect(Collectors.toList());
                if (!candidates.isEmpty()) {
                    Schedule b = candidates.get(rng.nextInt(candidates.size()));
                    Staff staffA = a.getStaff();
                    Staff staffB = b.getStaff();

                    // canTake on each other's assignment (excludes self schedules a/b)
                    if (canTake(byStaff, staffA.getId(), b, l01Window) && canTake(byStaff, staffB.getId(), a, l01Window)
                            && !hasCompensationDay(byStaff, staffA.getId(), b.getWorkDate())
                            && !hasCompensationDay(byStaff, staffB.getId(), a.getWorkDate())
                            && matchesSpecialtyL04(staffA, b.getShiftType().getId(), b.getWorkDate(), requirements)
                            && matchesSpecialtyL04(staffB, a.getShiftType().getId(), a.getWorkDate(), requirements)) {
                        double oldScore = currentScore;
                        a.setStaff(staffB);
                        b.setStaff(staffA);
                        // Keep byStaff index in sync
                        byStaff.get(staffA.getId()).remove(a);
                        byStaff.get(staffB.getId()).remove(b);
                        byStaff.get(staffA.getId()).add(b);
                        byStaff.get(staffB.getId()).add(a);

                        double newScore = score(current, requirements, staffMap, totalRequired, l01Window, runtimeConfig);
                        double delta = newScore - oldScore;
                        if (delta > 0 || rng.nextDouble() < Math.exp(delta / Math.max(T, 1e-9))) {
                            currentScore = newScore;
                            if (newScore > bestScore) {
                                bestScore = newScore;
                                bestSolution = deepCopy(current);
                            }
                        } else {
                            a.setStaff(staffA);
                            b.setStaff(staffB);
                            byStaff.get(staffA.getId()).remove(b);
                            byStaff.get(staffB.getId()).remove(a);
                            byStaff.get(staffA.getId()).add(a);
                            byStaff.get(staffB.getId()).add(b);
                        }
                    }
                }
            }

            T *= COOLING_RATE;
        }

        // Directed fairness pass: move load max → min (incl. zero-load staff)
        fairnessRebalance(bestSolution, activeStaff, excludedStaffIds, staffMap, requirements, runtimeConfig, rng, l01Window);

        log.info("SimulatedAnnealing: {} schedules (best={}) in {}ms (maxIter={})",
                bestSolution.size(), String.format("%.3f", score(bestSolution, requirements, staffMap, totalRequired, l01Window, runtimeConfig)),
                System.currentTimeMillis() - start, maxIter);
        return bestSolution;
    }

    /** Move shifts from overloaded → underloaded staff (incl. staff with 0 load). */
    private void fairnessRebalance(List<Schedule> schedules, List<Staff> activeStaff,
                                   Set<Integer> excludedIds, Map<Integer, Staff> staffMap,
                                   List<ShiftRequirement> reqs,
                                   AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                   Random rng, int l01Window) {
        int maxShiftsPerDay = runtimeConfig != null && runtimeConfig.getMaxShiftsPerDay() > 0
                ? runtimeConfig.getMaxShiftsPerDay() : Integer.MAX_VALUE;
        int totalRounds = runtimeConfig != null ? runtimeConfig.getRebalanceRoundsTotal() : 80;
        for (int round = 0; round < totalRounds; round++) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (Staff st : activeStaff) {
                if (excludedIds != null && excludedIds.contains(st.getId())) continue;
                counts.put(st.getId(), 0);
            }
            for (Schedule s : schedules) counts.merge(s.getStaff().getId(), 1, Integer::sum);
            if (counts.isEmpty()) break;

            int maxCnt = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int minCnt = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            if (maxCnt - minCnt <= 1) break;

            int overloaded = counts.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
            int underloaded = counts.entrySet().stream()
                    .min(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
            if (overloaded == underloaded) break;

            List<Schedule> overShifts = schedules.stream()
                    .filter(s -> s.getStaff().getId() == overloaded)
                    .collect(Collectors.toList());
            Collections.shuffle(overShifts, rng);

            boolean moved = false;
            for (Schedule s : overShifts) {
                if (!canTake(schedules, underloaded, s, l01Window)) continue;
                if (hasCompensationDay(schedules, underloaded, s.getWorkDate())) continue;
                // maxShiftsPerDay — move làm underloaded nhận thêm 1 lịch trong ngày → check không vượt cap.
                if (wouldExceedMaxShiftsPerDay(schedules, underloaded, s.getWorkDate(), maxShiftsPerDay)) continue;
                if (!matchesSpecialtyL04(staffMap.get(underloaded), s.getShiftType().getId(),
                        s.getWorkDate(), reqs)) continue;
                s.setStaff(staffMap.get(underloaded));
                updateReq(s, reqs);
                moved = true;
                break;
            }
            if (!moved) break;
        }
    }

    /** maxShiftsPerDay guard for move-style operators: returns true if adding one shift
     *  for {@code staffId} on {@code date} would exceed the per-day hard cap.
     *  Indexed variant uses the byStaff map (built once per SA outer loop) for O(staff's schedules). */
    private boolean wouldExceedMaxShiftsPerDay(Map<Integer, List<Schedule>> byStaff, int staffId,
                                               LocalDate date, int maxShiftsPerDay) {
        if (maxShiftsPerDay == Integer.MAX_VALUE) return false;
        List<Schedule> staffSchedules = byStaff.get(staffId);
        if (staffSchedules == null) return 1 > maxShiftsPerDay;
        long today = 0;
        for (Schedule s : staffSchedules) {
            if (s.getWorkDate().equals(date)) today++;
        }
        return today + 1 > maxShiftsPerDay;
    }

    /** Full-scan variant used by fairnessRebalance (operates on the full list, no index). */
    private boolean wouldExceedMaxShiftsPerDay(List<Schedule> schedules, int staffId,
                                               LocalDate date, int maxShiftsPerDay) {
        if (maxShiftsPerDay == Integer.MAX_VALUE) return false;
        long today = 0;
        for (Schedule s : schedules) {
            if (s.getStaff().getId() == staffId && s.getWorkDate().equals(date)) today++;
        }
        return today + 1 > maxShiftsPerDay;
    }

    /** Deep-copy schedules so later mutations on `current` don't corrupt best. */
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

    private List<Schedule> greedyInitial(List<Staff> staff, List<ShiftRequirement> reqs,
                                          SchedulePeriod period, AlgorithmConfigService.AlgorithmRuntimeConfig config,
                                          Set<Integer> excluded, Map<Integer, Staff> staffMap, Random rng,
                                          int l01Window) {
        List<Schedule> result = new ArrayList<>();
	        // Track (staffId|date → set of shiftType ids) — allow non-conflicting same-day combos
	        Map<String, Set<String>> assignedTypesPerDay = new HashMap<>();
	        Map<Integer, Integer> counts = new HashMap<>();
	        Map<Integer, Map<LocalDate, Set<String>>> shiftPerStaff = new HashMap<>();
	        Map<Integer, Set<LocalDate>> staffCompDays = new HashMap<>();
	        // Cross-specialty tracking for L04 ratio cap (TASK-02)
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
	                    // Specialty check: L04 with cross-specialty support (TASK-02)
	                    .filter(s -> {
	                        if (specId == null) return true;
	                        if (!"L04".equals(shiftType)) {
	                            return s.getSpecialty() != null && s.getSpecialty().getId().equals(specId);
	                        }
	                        // L04 with specialty requirement
	                        boolean matches = s.getSpecialty() != null
	                                && s.getSpecialty().getId().equals(specId);
	                        if (matches) return true;
	                        String specName = req.getSpecialty() != null ? req.getSpecialty().getName() : null;
	                        // Cross-specialty: permitted AND within capacity cap
	                        if (!l04CrossConfig.isPermittedFor(specName)) return false;
	                        String crossKey = req.getWorkDate() + "|" + shiftType + "|" + specId;
	                        int crossCap = l04CrossConfig.crossCap(required);
	                        return crossAssignmentCount.getOrDefault(crossKey, 0) < crossCap;
	                    })
                    .filter(s -> {
                        if (specId == null && !"L04".equals(shiftType) && s.getSpecialty() == null) return false;
                        return true;
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
	                // Track cross-specialty L04 for ratio cap (TASK-02)
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

    /** Score = coverage × 0.7 + fairness × 0.3 − conflict_penalty − inter_penalty (WITH_INTER_BALANCE).
     *  Inter penalty mirrors ARRANGEMENT_MODE_CONTRACT: interEnabled → 5.0 weight × mean span × 0.02 scale. */
    private double score(List<Schedule> schedules, List<ShiftRequirement> reqs,
                         Map<Integer, Staff> staffMap, int totalRequired, int l01Window,
                         AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {
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

        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer id : staffMap.keySet()) counts.put(id, 0);
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
                int maxC = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                int minC = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
                // Max-min gap penalty — drives rebalance toward even load
                if (maxC - minC > 1) {
                    fairness *= Math.max(0.1, 1.0 - (maxC - minC) / (mean * 4.0));
                }
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

    /** Same-day L01↔L02 / L03↔L04, consecutive L01 (within l01Window), and L01 → compensation-day guards. */
    private boolean hasConflict(List<Schedule> schedules, int staffId,
                                LocalDate workDate, String newType, int l01Window) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            if (s.getWorkDate().equals(workDate)) {
                String existingType = s.getShiftType().getId();
                if (ScheduleConflictUtils.isBusinessConflict(newType, existingType)) return true;
            }
            if ("L01".equals(newType) && "L01".equals(s.getShiftType().getId())) {
                if (Math.abs(s.getWorkDate().toEpochDay() - workDate.toEpochDay()) <= l01Window) return true;
            }
            if ("L01".equals(s.getShiftType().getId())) {
                LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                if (compDate != null && compDate.equals(workDate)) return true;
            }
        }
        return false;
    }

    /** Indexed-lookup variant for greedyInitial — same-day conflict + consecutive L01. */
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

    /** Indexed-lookup variant of canTake — O(staff's schedules) instead of O(N). */
    private boolean canTake(Map<Integer, List<Schedule>> byStaff, int staffId, Schedule candidate, int l01Window) {
        LocalDate date = candidate.getWorkDate();
        String type = candidate.getShiftType().getId();
        List<Schedule> staffSchedules = byStaff.get(staffId);
        if (staffSchedules == null) return true;
        for (Schedule ex : staffSchedules) {
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

    private boolean hasCompensationDay(List<Schedule> schedules, int staffId, LocalDate date) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            if (!"L01".equals(s.getShiftType().getId())) continue;
            LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
            if (compDate != null && compDate.equals(date)) return true;
        }
        return false;
    }

    /** Indexed-lookup variant of hasCompensationDay — O(staff's L01 shifts) instead of O(N). */
    private boolean hasCompensationDay(Map<Integer, List<Schedule>> byStaff, int staffId, LocalDate date) {
        List<Schedule> staffSchedules = byStaff.get(staffId);
        if (staffSchedules == null) return false;
        for (Schedule s : staffSchedules) {
            if (!"L01".equals(s.getShiftType().getId())) continue;
            LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
            if (compDate != null && compDate.equals(date)) return true;
        }
        return false;
    }

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

    private void updateReq(Schedule s, List<ShiftRequirement> reqs) {
        if (s.getStaff() == null) return;
        ShiftRequirement r = ScheduleConflictUtils.findMatchingRequirement(
                s.getStaff(), s.getWorkDate(), s.getShiftType().getId(), reqs);
        if (r != null) s.setRequirement(r);
    }

    /** Build staffId -> list of their schedules index for O(1) conflict lookups. */
    private Map<Integer, List<Schedule>> indexByStaff(List<Schedule> schedules) {
        Map<Integer, List<Schedule>> idx = new HashMap<>();
        for (Schedule s : schedules) {
            idx.computeIfAbsent(s.getStaff().getId(), k -> new ArrayList<>()).add(s);
        }
        return idx;
    }
}
