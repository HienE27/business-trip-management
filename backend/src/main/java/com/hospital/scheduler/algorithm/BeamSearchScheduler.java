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
 * Beam Search scheduler with fairness + shift-type rotation.
 *
 * Keeps K best partial schedules, scoring by coverage, workload fairness,
 * AND shift-type variety (each staff should rotate through L01-L04).
 * Beam width configurable via runtimeConfig.getBeamWidth() (default 5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeamSearchScheduler {

    private final CompensationDateCalculator compensationDateCalculator;

    /** L04 cross-specialty config — set per solve() call. Used by rebalance methods. */
    private L04CrossSpecialtyConfig l04CrossConfig = L04CrossSpecialtyConfig.DISABLED;

    private static final int DEFAULT_BEAM_WIDTH = 5;
    private static final double COVERAGE_WEIGHT = 0.20;
    private static final double FAIRNESS_WEIGHT = 0.55;
    private static final double VARIETY_WEIGHT = 0.10;
    private static final double BALANCE_WEIGHT = 0.15;
    private static final String[] SHIFT_TYPES = {"L01", "L02", "L03", "L04"};
    /** Only expand the K lightest eligible staff per slot (speed + fairness). */
    private static final int ELIGIBLE_EXPAND_CAP = 8;

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds,
            L04CrossSpecialtyConfig l04CrossConfig) {

        long start = System.currentTimeMillis();
        this.l04CrossConfig = l04CrossConfig != null ? l04CrossConfig : L04CrossSpecialtyConfig.DISABLED;
        int beamWidth = runtimeConfig != null && runtimeConfig.getBeamWidth() > 0
                ? runtimeConfig.getBeamWidth() : DEFAULT_BEAM_WIDTH;
        int maxShifts = runtimeConfig != null && runtimeConfig.getMaxShiftsPerStaff() > 0
                ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE;
        int maxShiftsPerDay = runtimeConfig != null && runtimeConfig.getMaxShiftsPerDay() > 0
                ? runtimeConfig.getMaxShiftsPerDay() : Integer.MAX_VALUE;
        int l01Window = runtimeConfig != null ? runtimeConfig.getL01AdjacentDayWindow() : 1;

        // Group requirements by date
        Map<LocalDate, List<ShiftRequirement>> byDate = requirements.stream()
                .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate,
                        TreeMap::new, Collectors.toList()));

        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        // Initialize beam with empty state
        List<PartialState> beam = new ArrayList<>();
        beam.add(new PartialState(new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>()));

        int totalSlots = requirements.stream()
                .mapToInt(ShiftRequirement::getRequiredStaffCount).sum();

        for (Map.Entry<LocalDate, List<ShiftRequirement>> dateEntry : byDate.entrySet()) {
            LocalDate date = dateEntry.getKey();
            List<ShiftRequirement> dayReqs = dateEntry.getValue();
            dayReqs.sort(Comparator.comparingInt(r -> {
                String id = r.getShiftType().getId();
                return "L01".equals(id) ? 0 : "L02".equals(id) ? 1
                     : "L03".equals(id) ? 2 : "L04".equals(id) ? 3 : 4;
            }));

            for (ShiftRequirement req : dayReqs) {
                String shiftTypeId = req.getShiftType().getId();
                int required = req.getRequiredStaffCount();
	                Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
	                String reqSpecName = req.getSpecialty() != null ? req.getSpecialty().getName() : null;

		                // Expand beam by 1 slot at a time (required times)
		                int crossCapacity = specId != null && "L04".equals(shiftTypeId)
		                        ? this.l04CrossConfig.crossCap(required) : 0;
		                for (int slot = 0; slot < required; slot++) {
		                    List<ScoredEntry> candidates = new ArrayList<>();

		                    for (PartialState state : beam) {
		                        List<Integer> eligible = findEligible(activeStaff, state,
		                                date, shiftTypeId, specId, excludedStaffIds, maxShifts, maxShiftsPerDay, l01Window,
		                                this.l04CrossConfig, reqSpecName, crossCapacity);
                        if (eligible.size() > ELIGIBLE_EXPAND_CAP) {
                            eligible = eligible.subList(0, ELIGIBLE_EXPAND_CAP);
                        }

                        for (int sid : eligible) {
                            Map<String, String> newAssign = new HashMap<>(state.assignments);
                            Map<Integer, Integer> newCount = new HashMap<>(state.count);
                            Map<Integer, Set<String>> newTypes = new HashMap<>();
                            for (var e : state.typeMap.entrySet()) {
                                newTypes.put(e.getKey(), new HashSet<>(e.getValue()));
                            }
                            Map<String, Set<String>> newDayTypes = new HashMap<>();
                            for (var e : state.dayTypes.entrySet()) {
                                newDayTypes.put(e.getKey(), new HashSet<>(e.getValue()));
                            }
                            Map<Integer, Set<LocalDate>> newComp = new HashMap<>();
                            for (var e : state.compDays.entrySet()) {
                                newComp.put(e.getKey(), new HashSet<>(e.getValue()));
                            }

                            newAssign.put(sid + "|" + date + "|" + shiftTypeId, shiftTypeId);
                            newTypes.computeIfAbsent(sid, k -> new HashSet<>()).add(shiftTypeId);
                            newCount.merge(sid, 1, Integer::sum);
                            newDayTypes.computeIfAbsent(sid + "|" + date, k -> new HashSet<>()).add(shiftTypeId);
                            if ("L01".equals(shiftTypeId)) {
                                LocalDate comp = compensationDateCalculator.calculate(date);
                                if (comp != null) {
                                    newComp.computeIfAbsent(sid, k -> new HashSet<>()).add(comp);
                                }
                            }

                            double score = scoreStateFast(newAssign, newTypes, newCount,
                                    totalSlots, activeStaff.size(), runtimeConfig);
                            candidates.add(new ScoredEntry(newAssign, newTypes, newCount,
                                    newDayTypes, newComp, score));
                        }
                    }

                    if (!candidates.isEmpty()) {
                        candidates.sort((a, b) -> Double.compare(b.score, a.score));
                        beam = candidates.subList(0, Math.min(beamWidth, candidates.size()))
                                .stream().map(e -> new PartialState(e.assignments, e.count, e.types,
                                        e.dayTypes, e.compDays))
                                .collect(Collectors.toList());
                    }
                }
            }
        }

        PartialState best = beam.isEmpty()
                ? new PartialState(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>())
                : beam.get(0);

        // Convert to Schedule entities (key = staffId|date|type)
        List<Schedule> result = new ArrayList<>();
        for (Map.Entry<String, String> e : best.assignments.entrySet()) {
            String[] p = e.getKey().split("\\|");
            if (p.length < 2) continue;
            Staff s = staffMap.get(Integer.parseInt(p[0]));
            if (s == null) continue;
            LocalDate workDate = LocalDate.parse(p[1]);
            String shiftTypeId = e.getValue();
            ShiftRequirement matchedReq = ScheduleConflictUtils.findMatchingRequirement(s, workDate, shiftTypeId, requirements);
            Schedule sch = new Schedule();
            sch.setStaff(s);
            sch.setPeriod(period);
            sch.setWorkDate(workDate);
            sch.setShiftType(ScheduleConflictUtils.findShiftType(shiftTypeId, requirements));
            sch.setRequirement(matchedReq);
            sch.setHasConflict(false);
            result.add(sch);
        }

        // Light fairness rebalance: move shifts max-load → min-load staff
        fairnessRebalance(result, activeStaff, excludedStaffIds, staffMap, requirements, l01Window, runtimeConfig);

        log.info("BeamSearch: {} schedules in {}ms (beam={})",
                result.size(), System.currentTimeMillis() - start, beamWidth);
        return result;
    }

    /** Move a few shifts from overloaded to underloaded staff. Keeps coverage. */
    private void fairnessRebalance(List<Schedule> schedules, List<Staff> activeStaff,
                                   Set<Integer> excludedIds, Map<Integer, Staff> staffMap,
                                   List<ShiftRequirement> reqs, int l01Window,
                                   AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {
        // Phase 1: Total-count rebalance (existing logic)
        totalCountRebalance(schedules, activeStaff, excludedIds, staffMap, reqs, l01Window, runtimeConfig);
        // Phase 2: Per-type rebalance (TASK-L01-FAIRNESS Phase C)
        perTypeRebalance(schedules, activeStaff, excludedIds, staffMap, reqs, l01Window, runtimeConfig);
    }

    /** Total-count rebalance: move shifts from most overloaded → most underloaded staff. */
    private void totalCountRebalance(List<Schedule> schedules, List<Staff> activeStaff,
                                   Set<Integer> excludedIds, Map<Integer, Staff> staffMap,
                                   List<ShiftRequirement> reqs, int l01Window,
                                   AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {
        int totalRounds = runtimeConfig != null ? runtimeConfig.getRebalanceRoundsTotal() : 80;
        for (int round = 0; round < totalRounds; round++) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (Schedule s : schedules) counts.merge(s.getStaff().getId(), 1, Integer::sum);
            if (counts.isEmpty()) break;
            int maxCnt = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int minCnt = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            // include zero-load staff in min
            for (Staff st : activeStaff) {
                if (excludedIds != null && excludedIds.contains(st.getId())) continue;
                minCnt = Math.min(minCnt, counts.getOrDefault(st.getId(), 0));
            }
            if (maxCnt - minCnt <= 1) break;

            int overloaded = counts.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
            int underloaded = -1;
            int underCnt = Integer.MAX_VALUE;
            for (Staff st : activeStaff) {
                if (excludedIds != null && excludedIds.contains(st.getId())) continue;
                int c = counts.getOrDefault(st.getId(), 0);
                if (c < underCnt) { underCnt = c; underloaded = st.getId(); }
            }
            if (underloaded < 0 || underloaded == overloaded) break;

            boolean moved = false;
            for (Schedule s : schedules) {
                if (s.getStaff().getId() != overloaded) continue;
                if (!canTakeBeam(schedules, underloaded, s, l01Window)) continue;
                if (isCompensationDayList(schedules, underloaded, s.getWorkDate())) continue;
                if ("L04".equals(s.getShiftType().getId())) {
                    ShiftRequirement r = ScheduleConflictUtils.findMatchingRequirement(
                            staffMap.get(underloaded), s.getWorkDate(), "L04", reqs);
                    if (r != null && r.getSpecialty() != null) {
                        Staff u = staffMap.get(underloaded);
                        boolean matchesSpecialty = u.getSpecialty() != null
                                && u.getSpecialty().getId().equals(r.getSpecialty().getId());
                        if (!matchesSpecialty) {
                            if (!l04CrossConfig.isPermittedFor(r.getSpecialty().getName()))
                                continue;
                        }
                    }
                }
                s.setStaff(staffMap.get(underloaded));
                ShiftRequirement matched = ScheduleConflictUtils.findMatchingRequirement(
                        s.getStaff(), s.getWorkDate(), s.getShiftType().getId(), reqs);
                if (matched != null) s.setRequirement(matched);
                moved = true;
                break;
            }
            if (!moved) break;
        }
    }

    /**
     * Per-type rebalance: for each shift type (L01/L02/L03), move shifts from
     * staff with highest count of that type to staff with lowest count.
     * This complements the total-count rebalance by fixing per-type disparities
     * that total rebalance cannot address (e.g., staff A has 13 L01 + 2 L02,
     * staff B has 11 L01 + 6 L02 — total counts are similar but L02 is imbalanced).
     *
     * <p>ponytail: add L04 per-specialty rebalance when cross-specialty is enabled.
     */
    private void perTypeRebalance(List<Schedule> schedules, List<Staff> activeStaff,
                                   Set<Integer> excludedIds, Map<Integer, Staff> staffMap,
                                   List<ShiftRequirement> reqs, int l01Window,
                                   AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {
        String[] typesToBalance = {"L02", "L03", "L01"}; // L02/L03 first (most imbalanced), then L01
        int perTypeRounds = runtimeConfig != null ? runtimeConfig.getRebalanceRoundsPerType() : 30;
        for (String type : typesToBalance) {
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
                if (maxCnt - minCnt <= 1) break; // balanced for this type

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
                    if (!canTakeBeam(schedules, underloaded, s, l01Window)) continue;
                    if (isCompensationDayList(schedules, underloaded, s.getWorkDate())) continue;
                    if ("L04".equals(s.getShiftType().getId())) {
                        ShiftRequirement r = ScheduleConflictUtils.findMatchingRequirement(
                                staffMap.get(underloaded), s.getWorkDate(), "L04", reqs);
                        if (r != null && r.getSpecialty() != null) {
                            Staff u = staffMap.get(underloaded);
                            boolean matchesSpecialty = u.getSpecialty() != null
                                    && u.getSpecialty().getId().equals(r.getSpecialty().getId());
                            if (!matchesSpecialty) {
                                if (!l04CrossConfig.isPermittedFor(r.getSpecialty().getName()))
                                    continue;
                            }
                        }
                    }
                    s.setStaff(staffMap.get(underloaded));
                    ShiftRequirement matched = ScheduleConflictUtils.findMatchingRequirement(
                            s.getStaff(), s.getWorkDate(), s.getShiftType().getId(), reqs);
                    if (matched != null) s.setRequirement(matched);
                    moved = true;
                    break;
                }
                if (!moved) break;
            }
        }
    }

    private boolean canTakeBeam(List<Schedule> schedules, int staffId, Schedule candidate, int l01Window) {
        LocalDate date = candidate.getWorkDate();
        String type = candidate.getShiftType().getId();
        for (Schedule ex : schedules) {
            if (ex.getStaff().getId() != staffId || ex == candidate) continue;
            if (ex.getWorkDate().equals(date)) {
                String exType = ex.getShiftType().getId();
                if (exType.equals(type) || ScheduleConflictUtils.isBusinessConflict(type, exType)) return false;
            }
            if ("L01".equals(type) && "L01".equals(ex.getShiftType().getId())
                    && Math.abs(ex.getWorkDate().toEpochDay() - date.toEpochDay()) <= l01Window) return false;
        }
        return true;
    }

    private boolean isCompensationDayList(List<Schedule> schedules, int staffId, LocalDate date) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId || !"L01".equals(s.getShiftType().getId())) continue;
            LocalDate comp = compensationDateCalculator.calculate(s.getWorkDate());
            if (comp != null && comp.equals(date)) return true;
        }
        return false;
    }

    /**
     * Fast scoring using pre-computed count and typeMap — no scan over assignments.
     * Skips expensive per-type CV computation (dropped during expansion; fairness+coverage sufficient for pruning).
     * Includes soft inter-type penalty when arrangementMode= WITH_INTER_BALANCE (ARRANGEMENT_MODE_CONTRACT).
     */
    private double scoreStateFast(Map<String, String> assignments,
                                   Map<Integer, Set<String>> staffTypes,
                                   Map<Integer, Integer> staffCount,
                                   int totalRequired, int numStaff,
                                   AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {
        double coverage = totalRequired > 0 ? (double) assignments.size() / totalRequired : 0;

        // Fairness over full staff pool (zero-load staff count as 0)
        double fairness = 0;
        if (numStaff > 0) {
            int assignedSum = staffCount.values().stream().mapToInt(Integer::intValue).sum();
            double mean = (double) assignedSum / numStaff;
            if (mean > 0) {
                double sumSq = 0;
                int withLoad = 0;
                for (int c : staffCount.values()) {
                    sumSq += (c - mean) * (c - mean);
                    withLoad++;
                }
                // zeros for unassigned staff
                sumSq += (numStaff - withLoad) * mean * mean;
                fairness = Math.max(0, 1 - Math.sqrt(sumSq / numStaff) / mean);
                int maxCount = staffCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                if (maxCount > mean * 1.5) {
                    fairness *= Math.max(0, 1 - (maxCount - mean * 1.5) / mean);
                }
            } else { fairness = 1; }
        }

        // Variety (cheap, uses typeMap)
        int totalVariety = 0;
        for (Set<String> types : staffTypes.values()) totalVariety += types.size();
        double variety = staffTypes.isEmpty() ? 0
                : Math.min(1, (double) totalVariety / staffTypes.size() / SHIFT_TYPES.length);

        // Soft inter-type penalty: WITH_INTER_BALANCE only (ARRANGEMENT_MODE_CONTRACT)
        // Build per-staff L01/L02/L03 counts from typeMap (L04 excluded)
        double interPenalty = 0;
        if (interEnabled(runtimeConfig)) {
            Map<Integer, Map<String, Integer>> byStaff = new HashMap<>();
            for (Map.Entry<Integer, Set<String>> e : staffTypes.entrySet()) {
                Map<String, Integer> m = byStaff.computeIfAbsent(e.getKey(), k -> new HashMap<>());
                for (String t : e.getValue()) {
                    if ("L01".equals(t) || "L02".equals(t) || "L03".equals(t)) {
                        m.merge(t, 1, Integer::sum);
                    }
                }
            }
            double meanSpan = meanInterSpan(byStaff);
            interPenalty = DEFAULT_INTER_WEIGHT * meanSpan * OBJECTIVE_INTER_SCALE;
        }

        // Eligibility already enforces conflicts — skip O(N) scan in hot path
        return COVERAGE_WEIGHT * coverage
                + (FAIRNESS_WEIGHT + BALANCE_WEIGHT) * fairness
                + VARIETY_WEIGHT * variety
                - interPenalty;
    }

	    private List<Integer> findEligible(
	            List<Staff> staffList, PartialState state,
	            LocalDate date, String shiftTypeId, Integer specId,
	            Set<Integer> excludedIds, int maxShifts, int maxShiftsPerDay,
	            int l01Window,
	            L04CrossSpecialtyConfig l04CrossConfig,
	            String reqSpecName,
	            int crossCapacity) {

	        // Build staff specialty lookup for cross-cap computation
	        Map<Integer, Staff> staffLookup = staffList.stream()
	                .collect(Collectors.toMap(Staff::getId, s -> s));

	        return staffList.stream()
	                .filter(s -> excludedIds == null || !excludedIds.contains(s.getId()))
	                .filter(s -> !state.assignments.containsKey(s.getId() + "|" + date + "|" + shiftTypeId))
	                .filter(s -> state.count.getOrDefault(s.getId(), 0) < maxShifts)
	                // Specialty check: L04 with cross-specialty support (TASK-02)
	                .filter(s -> {
	                    if (specId == null) return true; // no specialty req → all eligible
	                    if (!"L04".equals(shiftTypeId)) {
	                        // Non-L04: staff must match requirement specialty
	                        return s.getSpecialty() != null && s.getSpecialty().getId().equals(specId);
	                    }
	                    // L04 with specialty requirement
	                    boolean matchesSpecialty = s.getSpecialty() != null
	                            && s.getSpecialty().getId().equals(specId);
	                    if (matchesSpecialty) return true;
	                    // Cross-specialty: only if permitted AND within capacity cap
	                    if (!l04CrossConfig.isPermittedFor(reqSpecName)) return false;
	                    if (crossCapacity <= 0) return false;
	                    // Count current cross-specialty assignments in state for this requirement
	                    int crossCount = 0;
	                    String prefix = "|" + date + "|" + shiftTypeId;
	                    for (String key : state.assignments.keySet()) {
	                        if (key.endsWith(prefix)) {
	                            int asid = Integer.parseInt(key.split("\\|")[0]);
	                            Staff as = staffLookup.get(asid);
	                            if (as != null && as.getSpecialty() != null
	                                    && !as.getSpecialty().getId().equals(specId)) {
	                                crossCount++;
	                            }
	                        }
	                    }
	                    return crossCount < crossCapacity;
	                })
                .filter(s -> {
                    if (specId == null && !"L04".equals(shiftTypeId) && s.getSpecialty() == null) return false;
                    return true;
                })
                .filter(s -> {
                    Set<String> today = state.dayTypes.getOrDefault(s.getId() + "|" + date, Collections.emptySet());
                    if (today.contains(shiftTypeId)) return false;
                    for (String t : today) {
                        if (ScheduleConflictUtils.isBusinessConflict(shiftTypeId, t)) return false;
                    }
                    // maxShiftsPerDay — HARD cap tổng số ca mỗi nhân sự mỗi ngày.
                    if (maxShiftsPerDay != Integer.MAX_VALUE && today.size() >= maxShiftsPerDay) {
                        return false;
                    }
                    return true;
                })
                .filter(s -> {
                    Set<LocalDate> comps = state.compDays.get(s.getId());
                    return comps == null || !comps.contains(date);
                })
                .filter(s -> {
                    if (!"L01".equals(shiftTypeId)) return true;
                    // Consecutive L01 check: window = ceil(overnightRecoveryHours/24)
                    for (int dt = 1; dt <= l01Window; dt++) {
                        Set<String> prev = state.dayTypes.getOrDefault(
                                s.getId() + "|" + date.minusDays(dt), Collections.emptySet());
                        if (prev.contains("L01")) return false;
                        Set<String> next = state.dayTypes.getOrDefault(
                                s.getId() + "|" + date.plusDays(dt), Collections.emptySet());
                        if (next.contains("L01")) return false;
                    }
                    return true;
                })
                .sorted(Comparator
                        .comparingInt((Staff s) -> state.count.getOrDefault(s.getId(), 0))
                        .thenComparingInt(s -> state.typeMap.getOrDefault(s.getId(), Collections.emptySet())
                                .contains(shiftTypeId) ? 1 : 0))
                .map(Staff::getId)
                .collect(Collectors.toList());
    }

    private record PartialState(Map<String, String> assignments,
                                 Map<Integer, Integer> count,
                                 Map<Integer, Set<String>> typeMap,
                                 Map<String, Set<String>> dayTypes,
                                 Map<Integer, Set<LocalDate>> compDays) {}
    private record ScoredEntry(Map<String, String> assignments,
                                Map<Integer, Set<String>> types,
                                Map<Integer, Integer> count,
                                Map<String, Set<String>> dayTypes,
                                Map<Integer, Set<LocalDate>> compDays,
                                double score) {}
}
