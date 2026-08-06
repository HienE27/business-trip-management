package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static com.hospital.scheduler.algorithm.CspConstants.DIRECT_24H;

/**
 * Incremental re-solver: applies a {@link ScheduleChange} delta to a
 * previous result, re-validates affected variables, runs a local search,
 * and falls back to a full solve if local fixes don't converge.
 *
 * Kept separate from the batch solver because the data model is fundamentally
 * different (Map-based assignments, no BitSet domains, no AC-3 graph).
 */
@Component
@RequiredArgsConstructor
class CspIncrementalResolver {

    private final CspDataBuilder dataBuilder;
    private final CspSearchEngine searchEngine;
    private final CspResultBuilder resultBuilder;
    private final CompensationDateCalculator compensationDateCalculator;

    SchedulingResult reSolve(
            SchedulingResult previousResult,
            ScheduleChange deltaChanges,
            List<Staff> staffList,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests) {

        long startTime = System.currentTimeMillis();
        if (deltaChanges == null || !deltaChanges.hasChanges()) {
            return fullReSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
        }
        if (deltaChanges.requiresFullReSolve()) {
            return fullReSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
        }

        IncrementalState state = buildIncrementalState(previousResult, staffList, requirements);
        applyDeltaChanges(state, deltaChanges);

        if (!revalidateAndPropagate(state, staffList)) {
            return fullReSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
        }
        if (!localSearch(state, staffList, startTime)) {
            return fullReSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
        }
        return buildResultFromIncrementalState(state, staffList, startTime);
    }

    SchedulingResult fullReSolve(
            SchedulingResult previousResult,
            ScheduleChange deltaChanges,
            List<Staff> staffList,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests) {

        long startTime = System.currentTimeMillis();

        LocalDate startDate = null;
        LocalDate endDate = null;
        if (previousResult != null && !previousResult.getAssignments().isEmpty()) {
            for (String key : previousResult.getAssignments().keySet()) {
                String[] parts = key.split("_");
                if (parts.length >= 2) {
                    LocalDate date = LocalDate.parse(parts[1]);
                    if (startDate == null || date.isBefore(startDate)) startDate = date;
                    if (endDate == null || date.isAfter(endDate)) endDate = date;
                }
            }
        }
        if (startDate == null || endDate == null) {
            if (requirements != null && !requirements.isEmpty()) {
                startDate = requirements.stream().map(ShiftRequirementInfo::workDate)
                        .min(LocalDate::compareTo).orElse(LocalDate.now());
                endDate = requirements.stream().map(ShiftRequirementInfo::workDate)
                        .max(LocalDate::compareTo).orElse(LocalDate.now().plusMonths(1));
            } else {
                startDate = LocalDate.now();
                endDate = LocalDate.now().plusMonths(1);
            }
        }

        // Build ProblemData and run a fresh batch solve (reuses the same
        // builder / search engine / result builder modules)
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) dates.add(d);
        ProblemData data = dataBuilder.build(staffList, dates,
                requirements != null ? requirements : List.of(), leaveRequests);
        CspSearchEngine.Result solution = searchEngine.solve(data, startTime);
        return resultBuilder.build(solution, data, staffList, dates, startTime);
    }

    // ==================== State helpers ====================

    private IncrementalState buildIncrementalState(
            SchedulingResult previousResult, List<Staff> staffList, List<ShiftRequirementInfo> requirements) {

        IncrementalState state = new IncrementalState();
        state.staffIndexMap = new HashMap<>();
        for (int i = 0; i < staffList.size(); i++) {
            state.staffIndexMap.put(staffList.get(i).getId(), i);
        }
        state.assignments = new HashMap<>(previousResult.getAssignments());
        state.varIndexMap = new HashMap<>();
        state.affectedVars = new HashSet<>();
        state.conflicts = new ArrayList<>();
        state.newLeaves = new HashMap<>();

        if (requirements != null) {
            int varIdx = 0;
            for (ShiftRequirementInfo req : requirements) {
                String varKey = req.workDate() + "|" + req.shiftTypeId();
                if (!state.varIndexMap.containsKey(varKey)) {
                    state.varIndexMap.put(varKey, varIdx++);
                }
            }
        }
        return state;
    }

    private void applyDeltaChanges(IncrementalState state, ScheduleChange deltaChanges) {
        for (ScheduleChange.AssignmentDelta rem : deltaChanges.getRemoved()) {
            String key = rem.getStaffId() + "_" + rem.getDate() + "_" + rem.getShiftType();
            state.assignments.remove(key);
            state.affectedVars.add(rem.getDate() + "|" + rem.getShiftType());
        }
        for (ScheduleChange.AssignmentDelta add : deltaChanges.getAdded()) {
            String key = add.getStaffId() + "_" + add.getDate() + "_" + add.getShiftType();
            state.assignments.put(key, add.getShiftType());
            state.affectedVars.add(add.getDate() + "|" + add.getShiftType());
        }
        for (ScheduleChange.AssignmentDelta mod : deltaChanges.getModified()) {
            if (mod.getOldStaffId() != null) {
                state.assignments.remove(mod.getOldStaffId() + "_" + mod.getDate() + "_" + mod.getShiftType());
            }
            state.assignments.put(mod.getStaffId() + "_" + mod.getDate() + "_" + mod.getShiftType(), mod.getShiftType());
            state.affectedVars.add(mod.getDate() + "|" + mod.getShiftType());
        }
        for (ScheduleChange.LeaveDelta leave : deltaChanges.getAddedLeaves()) {
            Integer staffIdx = state.staffIndexMap.get(leave.getStaffId());
            if (staffIdx == null) continue;
            for (LocalDate d = leave.getStartDate(); !d.isAfter(leave.getEndDate()); d = d.plusDays(1)) {
                state.newLeaves.computeIfAbsent(staffIdx, k -> new HashSet<>()).add(d);
            }
        }
        for (ScheduleChange.LeaveDelta leave : deltaChanges.getRemovedLeaves()) {
            Integer staffIdx = state.staffIndexMap.get(leave.getStaffId());
            if (staffIdx == null) continue;
            for (LocalDate d = leave.getStartDate(); !d.isAfter(leave.getEndDate()); d = d.plusDays(1)) {
                Set<LocalDate> set = state.newLeaves.get(staffIdx);
                if (set != null) set.remove(d);
            }
        }
    }

    /**
     * OPT-002: queue-driven repair pass.  Replaces the original
     * arbitrary-order {@code state.conflicts.remove(0)} loop with a
     * priority queue ({@link CspRepairQueueManager}) that orders slots by
     * (scarcity, streak, slack, seq).  Each entry is fed to
     * {@link CspRepairHeuristics#attemptRepair}, which adds soft-fairness
     * relaxation and rotation retries on top of the hard-constraint check.
     */
    private boolean revalidateAndPropagate(IncrementalState state, List<Staff> staffList) {
        for (String varKey : state.affectedVars) {
            String[] parts = varKey.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];

            Integer currentStaffId = findStaffForSlot(state, date, shiftType);
            if (currentStaffId == null) continue;

            Integer staffIdx = state.staffIndexMap.get(currentStaffId);
            if (staffIdx == null) {
                state.conflicts.add(new ConflictInfo(varKey, currentStaffId, "Staff not found"));
                continue;
            }
            String conflict = checkAssignmentConflict(state, date, shiftType, currentStaffId, staffIdx);
            if (conflict != null) state.conflicts.add(new ConflictInfo(varKey, currentStaffId, conflict));
        }

        if (state.conflicts.isEmpty()) return true;

        // ── OPT-002: queue-driven repair ─────────────────────────────────────
        return repairWithQueue(state, staffList, 10);
    }

    /**
     * Process {@link IncrementalState#conflicts} through the
     * {@link CspRepairQueueManager} + {@link CspRepairHeuristics} pipeline.
     * Returns {@code true} when every conflict has been resolved.
     *
     * <p>Failed repairs are re-enqueued with an incremented streak so the
     * next iteration gets higher priority (and a fresh fairness context).
     */
    private boolean repairWithQueue(IncrementalState state, List<Staff> staffList, int maxIterations) {
        Map<String, Integer> eligibleCounts = buildEligibleCounts(staffList, state);
        Set<String> unmetKeys = new LinkedHashSet<>();
        for (ConflictInfo c : state.conflicts) unmetKeys.add(c.varKey);

        CspRepairQueueManager queue = CspRepairQueueManager.build(
                unmetKeys, state.varIndexMap, state.staffIndexMap, staffList,
                state.assignments, eligibleCounts, true /* relaxFairness */);

        int iter = 0;
        while (queue.remainingRepairs() > 0 && iter < maxIterations) {
            CspRepairQueueManager.RepairEntry entry = queue.pollNextRepair();
            if (entry == null) break;

            String[] parts = entry.varKey().split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];
            Integer currentStaffId = findStaffForSlot(state, date, shiftType);

            CspRepairHeuristics.RepairResult result = attemptRepairFor(
                    state,
                    entry.varKey(), date, shiftType, currentStaffId, staffList, queue);

            if (result.isRepaired()) {
                Integer newStaff = result.getReplacementStaffId();
                if (currentStaffId != null && !currentStaffId.equals(newStaff)) {
                    removeAssignment(state, currentStaffId, date);
                }
                addAssignment(state, newStaff, date, shiftType);
                queue.recordAssignment(newStaff);
                queue.markRepaired(entry.varKey());
                state.conflicts.removeIf(c -> c.varKey.equals(entry.varKey()));
            } else {
                int nextStreak = entry.priority().streak() + 1;
                queue.reenqueueWithStreak(entry.varKey(), nextStreak);
            }
            iter++;
        }
        return state.conflicts.isEmpty();
    }

    /**
     * Single-slot repair using {@link CspRepairHeuristics#attemptRepair}.
     * Bridges the resolver's hard-constraint checker and a workload-aware
     * fairness predicate into the 3-phase (normal → relaxed → rotation)
     * heuristic.
     */
    private CspRepairHeuristics.RepairResult attemptRepairFor(
            IncrementalState state,
            String varKey, LocalDate date, String shiftType, Integer excludeStaffId,
            List<Staff> staffList, CspRepairQueueManager queue) {

        java.util.function.BiFunction<Integer, Integer, String> hardCheck = (candidateId, w) -> {
            Integer staffIdx = state.staffIndexMap.get(candidateId);
            if (staffIdx == null) return "Staff not in roster";
            String conflict = checkAssignmentConflict(state, date, shiftType, candidateId, staffIdx);
            return conflict;
        };

        // Fairness (BR05) predicate: when relaxed, every staff passes; otherwise
        // only staff within +2 of the average workload pass.
        java.util.function.Predicate<Integer> fairnessCheck;
        Map<Integer, Integer> wl = queue.getStaffWorkload();
        if (queue.shouldRelaxFairness() || wl.isEmpty()) {
            fairnessCheck = sid -> true;
        } else {
            double avg = wl.values().stream().mapToInt(Integer::intValue).average().orElse(0);
            int threshold = (int) Math.ceil(avg + 2);
            fairnessCheck = sid -> wl.getOrDefault(sid, 0) <= threshold;
        }

        return CspRepairHeuristics.attemptRepair(
                varKey, date, shiftType, excludeStaffId,
                staffList, wl, fairnessCheck, hardCheck);
    }

    /**
     * Build eligible-staff counts per shift type for the priority queue's
     * scarcity tier computation.  We use the total roster size as an upper
     * bound — the precise per-shift specialty filtering lives in the
     * hard-constraint lambda passed to {@link CspRepairHeuristics}.
     */
    private Map<String, Integer> buildEligibleCounts(List<Staff> staffList, IncrementalState state) {
        Map<String, Integer> counts = new HashMap<>();
        if (staffList == null || staffList.isEmpty()) return counts;
        int total = staffList.size();
        for (String shiftType : new String[]{"L01", "L02", "L03", "L04"}) {
            counts.put(shiftType, total);
        }
        // Derive from affected vars when present so we don't overestimate
        for (String varKey : state.affectedVars) {
            String[] parts = varKey.split("\\|");
            if (parts.length < 2) continue;
            counts.putIfAbsent(parts[1], total);
        }
        return counts;
    }

    /**
     * Inner-class helper exposing the current {@link IncrementalState} to
     * nested lambdas without capturing it through every helper signature.
     * The current repair loop rebinds this each iteration via
     * {@link #setStateRef}.
     */
    private IncrementalState stateRef() {
        return currentState;
    }

    private IncrementalState currentState;

    private void setStateRef(IncrementalState state) {
        this.currentState = state;
    }

    private String checkAssignmentConflict(
            IncrementalState state, LocalDate date, String shiftType, Integer staffId, Integer staffIdx) {

        // BR-01/02: same-day shift conflicts
        for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            if (keyParts.length < 2) continue;
            Integer otherStaffId = Integer.parseInt(keyParts[0]);
            LocalDate otherDate = LocalDate.parse(keyParts[1]);
            if (otherStaffId.equals(staffId) && otherDate.equals(date)) {
                if (CspConstants.conflicts(shiftType, entry.getValue())) {
                    return "Same day conflict with " + entry.getValue();
                }
            }
        }

        // BR-03: L01 comp-day conflict — check ALL possible compensation days
        if (shiftType.equals(DIRECT_24H)) {
            Set<LocalDate> compOptions = compensationDateCalculator.calculateAll(date);
            boolean anyFree = false;
            for (LocalDate compDate : compOptions) {
                String compKey = staffId + "_" + compDate;
                boolean hasConflict = false;
                for (String key : state.assignments.keySet()) {
                    if (key.equals(compKey) || key.startsWith(staffId + "_" + compDate)) {
                        hasConflict = true;
                        break;
                    }
                }
                if (!hasConflict) {
                    anyFree = true;
                    break;
                }
            }
            if (!anyFree && !compOptions.isEmpty()) {
                return "No free compensation day among options";
            }
        }

        // BR-04: leave conflict
        if (state.newLeaves.containsKey(staffIdx) && state.newLeaves.get(staffIdx).contains(date)) {
            return "Staff on leave";
        }
        return null;
    }

    /**
     * OPT-002: queue-driven local search.  Unassigned / conflicted vars are
     * pulled from {@link CspRepairQueueManager} in priority order and each
     * is repaired via {@link CspRepairHeuristics#attemptRepair}.
     */
    private boolean localSearch(IncrementalState state, List<Staff> staffList, long startTime) {
        int maxIterations = 50;
        int iter = 0;
        setStateRef(state);
        while (iter < maxIterations) {
            if (System.currentTimeMillis() - startTime > 5_000) return false;
            String conflictVar = findUnassignedOrConflict(state);
            if (conflictVar == null) return true;

            String[] parts = conflictVar.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];
            Integer currentStaffId = findStaffForSlot(state, date, shiftType);

            // Build a one-entry queue so we reuse the same repair code path
            CspRepairQueueManager oneShot = CspRepairQueueManager.build(
                    java.util.Set.of(conflictVar), state.varIndexMap, state.staffIndexMap,
                    staffList, state.assignments,
                    buildEligibleCounts(staffList, state), true);

            CspRepairHeuristics.RepairResult result = attemptRepairFor(
                    state,
                    conflictVar, date, shiftType, currentStaffId, staffList, oneShot);

            if (!result.isRepaired()) {
                // Stuck — escalate to full re-solve so the orchestrator picks
                // a different algorithm instead of thrashing on this slot.
                return false;
            }
            Integer newStaff = result.getReplacementStaffId();
            if (currentStaffId != null && !currentStaffId.equals(newStaff)) {
                removeAssignment(state, currentStaffId, date);
            }
            addAssignment(state, newStaff, date, shiftType);
            iter++;
        }
        return iter < maxIterations;
    }

    private String findUnassignedOrConflict(IncrementalState state) {
        for (String varKey : state.varIndexMap.keySet()) {
            if (state.conflicts.stream().anyMatch(c -> c.varKey.equals(varKey))) return varKey;
        }
        return null;
    }

    private Integer findStaffForSlot(IncrementalState state, LocalDate date, String shiftType) {
        for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            if (keyParts.length >= 2 && keyParts[1].equals(date.toString()) && entry.getValue().equals(shiftType)) {
                return Integer.parseInt(keyParts[0]);
            }
        }
        return null;
    }

    private void removeAssignment(IncrementalState state, Integer staffId, LocalDate date) {
        // Remove ALL assignments for this staff+date (any shiftType).
        // With multi-shift support, iterate to find and remove matching entries.
        state.assignments.keySet().removeIf(k -> k.startsWith(staffId + "_" + date + "_"));
    }

    private void removeAssignment(IncrementalState state, Integer staffId, LocalDate date, String shiftType) {
        state.assignments.remove(staffId + "_" + date + "_" + shiftType);
    }

    private void addAssignment(IncrementalState state, Integer staffId, LocalDate date, String shiftType) {
        state.assignments.put(staffId + "_" + date + "_" + shiftType, shiftType);
    }

    private SchedulingResult buildResultFromIncrementalState(
            IncrementalState state, List<Staff> staffList, long startTime) {

        Set<String> compensationDays = new HashSet<>();
        for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            if (keyParts.length >= 2 && entry.getValue().equals(DIRECT_24H)) {
                Integer staffId = Integer.parseInt(keyParts[0]);
                LocalDate workDate = LocalDate.parse(keyParts[1]);
                // Pick first valid option from calculateAll
                Set<LocalDate> compOptions = compensationDateCalculator.calculateAll(workDate);
                LocalDate compDate = compOptions.isEmpty() ? null : compOptions.iterator().next();
                if (compDate != null) {
                    compensationDays.add(staffId + "_" + compDate);
                }
            }
        }

        Map<Integer, Integer> shiftCounts = new HashMap<>();
        for (String key : state.assignments.keySet()) {
            Integer staffId = Integer.parseInt(key.split("_")[0]);
            shiftCounts.merge(staffId, 1, Integer::sum);
        }

        double fairness = 100;
        if (!shiftCounts.isEmpty()) {
            double avg = shiftCounts.values().stream().mapToInt(Integer::intValue).average().orElse(0);
            if (avg > 0) {
                double variance = shiftCounts.values().stream()
                        .mapToDouble(c -> (c - avg) * (c - avg))
                        .average().orElse(0);
                fairness = Math.max(0, 100 - variance * 10);
            }
        }

        List<Map<String, Object>> unassignedDays = buildUnassignedReport(state);
        List<String> warnings = new ArrayList<>();
        if (!state.conflicts.isEmpty()) {
            warnings.add("Có " + state.conflicts.size() + " xung đột chưa được giải quyết.");
            for (ConflictInfo conflict : state.conflicts) {
                warnings.add("- " + conflict.varKey + ": " + conflict.reason);
            }
        }

        return SchedulingResult.builder()
                .valid(state.conflicts.isEmpty())
                .assignments(state.assignments)
                .compensationDays(compensationDays)
                .errors(state.conflicts.isEmpty() ? Collections.emptyList() : warnings)
                .fairnessScore(BigDecimal.valueOf(fairness).setScale(2, RoundingMode.HALF_UP))
                .fatigueScore(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP))
                .coverageScore(BigDecimal.valueOf(calculateCoverage(state)).setScale(2, RoundingMode.HALF_UP))
                .totalScore(BigDecimal.valueOf(fairness).setScale(2, RoundingMode.HALF_UP))
                .scheduleCount(state.assignments.size())
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .unassignedDays(unassignedDays)
                .build();
    }

    private List<Map<String, Object>> buildUnassignedReport(IncrementalState state) {
        List<Map<String, Object>> unassignedDays = new ArrayList<>();
        for (String varKey : state.varIndexMap.keySet()) {
            String[] parts = varKey.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];

            if (findStaffForSlot(state, date, shiftType) == null) {
                Map<String, Object> day = new HashMap<>();
                day.put("date", date);
                day.put("shiftType", shiftType);
                day.put("shiftTypeName", CspConstants.getShiftTypeName(shiftType));
                day.put("required", 1);
                day.put("assigned", 0);
                day.put("shortfall", 1);
                day.put("dayOfWeek", date.getDayOfWeek().toString());
                unassignedDays.add(day);
            }
        }
        return unassignedDays;
    }

    private double calculateCoverage(IncrementalState state) {
        if (state.varIndexMap.isEmpty()) return 100;
        int totalSlots = state.varIndexMap.size();
        int assignedSlots = 0;
        for (String varKey : state.varIndexMap.keySet()) {
            String[] parts = varKey.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];
            if (findStaffForSlot(state, date, shiftType) != null) assignedSlots++;
        }
        return (double) assignedSlots / totalSlots * 100;
    }

    // ==================== Inner state holders ====================

    static class IncrementalState {
        Map<String, String> assignments = new HashMap<>();
        Map<Integer, Integer> staffIndexMap = new HashMap<>();
        Map<String, Integer> varIndexMap = new HashMap<>();
        Set<String> affectedVars = new HashSet<>();
        List<ConflictInfo> conflicts = new ArrayList<>();
        Map<Integer, Set<LocalDate>> newLeaves = new HashMap<>();
    }

    static class ConflictInfo {
        String varKey;
        Integer staffId;
        String reason;

        ConflictInfo(String varKey, Integer staffId, String reason) {
            this.varKey = varKey;
            this.staffId = staffId;
            this.reason = reason;
        }
    }
}
