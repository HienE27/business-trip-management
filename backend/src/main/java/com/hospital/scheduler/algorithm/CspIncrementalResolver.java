package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            List<ShiftRequirement> requirements,
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

        if (!revalidateAndPropagate(state)) {
            return fullReSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
        }
        if (!localSearch(state, startTime)) {
            return fullReSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
        }
        return buildResultFromIncrementalState(state, staffList, startTime);
    }

    SchedulingResult fullReSolve(
            SchedulingResult previousResult,
            ScheduleChange deltaChanges,
            List<Staff> staffList,
            List<ShiftRequirement> requirements,
            List<LeaveRequest> leaveRequests) {

        long startTime = System.currentTimeMillis();

        LocalDate startDate = null;
        LocalDate endDate = null;
        if (previousResult != null && !previousResult.getAssignments().isEmpty()) {
            for (String key : previousResult.getAssignments().keySet()) {
                String[] parts = key.split("_");
                if (parts.length == 2) {
                    LocalDate date = LocalDate.parse(parts[1]);
                    if (startDate == null || date.isBefore(startDate)) startDate = date;
                    if (endDate == null || date.isAfter(endDate)) endDate = date;
                }
            }
        }
        if (startDate == null || endDate == null) {
            if (requirements != null && !requirements.isEmpty()) {
                startDate = requirements.stream().map(ShiftRequirement::getWorkDate)
                        .min(LocalDate::compareTo).orElse(LocalDate.now());
                endDate = requirements.stream().map(ShiftRequirement::getWorkDate)
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
        ProblemData data = dataBuilder.build(staffList, dates, requirements != null ? requirements : List.of(), leaveRequests);
        CspSearchEngine.Result solution = searchEngine.solve(data, startTime);
        return resultBuilder.build(solution, data, staffList, dates, startTime);
    }

    // ==================== State helpers ====================

    private IncrementalState buildIncrementalState(
            SchedulingResult previousResult, List<Staff> staffList, List<ShiftRequirement> requirements) {

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
            for (ShiftRequirement req : requirements) {
                String varKey = req.getWorkDate() + "|" + req.getShiftType().getId();
                if (!state.varIndexMap.containsKey(varKey)) {
                    state.varIndexMap.put(varKey, varIdx++);
                }
            }
        }
        return state;
    }

    private void applyDeltaChanges(IncrementalState state, ScheduleChange deltaChanges) {
        for (ScheduleChange.AssignmentDelta rem : deltaChanges.getRemoved()) {
            String key = rem.getStaffId() + "_" + rem.getDate();
            state.assignments.remove(key);
            state.affectedVars.add(rem.getDate() + "|" + rem.getShiftType());
        }
        for (ScheduleChange.AssignmentDelta add : deltaChanges.getAdded()) {
            String key = add.getStaffId() + "_" + add.getDate();
            state.assignments.put(key, add.getShiftType());
            state.affectedVars.add(add.getDate() + "|" + add.getShiftType());
        }
        for (ScheduleChange.AssignmentDelta mod : deltaChanges.getModified()) {
            if (mod.getOldStaffId() != null) {
                state.assignments.remove(mod.getOldStaffId() + "_" + mod.getDate());
            }
            state.assignments.put(mod.getStaffId() + "_" + mod.getDate(), mod.getShiftType());
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

    private boolean revalidateAndPropagate(IncrementalState state) {
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

        int maxIterations = 10;
        int iter = 0;
        while (!state.conflicts.isEmpty() && iter < maxIterations) {
            ConflictInfo conflict = state.conflicts.remove(0);
            String[] parts = conflict.varKey.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];

            Integer alternativeStaffId = findAlternativeStaff(state, date, shiftType, conflict.staffId);
            if (alternativeStaffId != null) {
                removeAssignment(state, conflict.staffId, date);
                addAssignment(state, alternativeStaffId, date, shiftType);
            } else if (conflict.staffId != null) {
                removeAssignment(state, conflict.staffId, date);
            }
            iter++;
        }
        return state.conflicts.isEmpty();
    }

    private String checkAssignmentConflict(
            IncrementalState state, LocalDate date, String shiftType, Integer staffId, Integer staffIdx) {

        // BR-01/02: same-day shift conflicts
        for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            if (keyParts.length != 2) continue;
            Integer otherStaffId = Integer.parseInt(keyParts[0]);
            LocalDate otherDate = LocalDate.parse(keyParts[1]);
            if (otherStaffId.equals(staffId) && otherDate.equals(date)) {
                if (CspConstants.conflicts(shiftType, entry.getValue())) {
                    return "Same day conflict with " + entry.getValue();
                }
            }
        }

        // BR-03: L01 comp-day conflict
        if (shiftType.equals(DIRECT_24H)) {
            LocalDate compDate = compensationDateCalculator.calculate(date);
            String compKey = staffId + "_" + compDate;
            for (String key : state.assignments.keySet()) {
                if (key.equals(compKey) || key.startsWith(staffId + "_" + compDate)) {
                    return "Compensation day conflict";
                }
            }
        }

        // BR-04: leave conflict
        if (state.newLeaves.containsKey(staffIdx) && state.newLeaves.get(staffIdx).contains(date)) {
            return "Staff on leave";
        }
        return null;
    }

    private Integer findAlternativeStaff(IncrementalState state, LocalDate date, String shiftType, Integer excludeStaffId) {
        for (Integer staffId : state.staffIndexMap.keySet()) {
            if (staffId.equals(excludeStaffId)) continue;
            Integer staffIdx = state.staffIndexMap.get(staffId);
            if (staffIdx == null) continue;
            if (checkAssignmentConflict(state, date, shiftType, staffId, staffIdx) == null) {
                return staffId;
            }
        }
        return null;
    }

    private boolean localSearch(IncrementalState state, long startTime) {
        int maxIterations = 50;
        int iter = 0;
        while (iter < maxIterations) {
            if (System.currentTimeMillis() - startTime > 5_000) return false;
            String conflictVar = findUnassignedOrConflict(state);
            if (conflictVar == null) return true;
            if (!fixSlotLocally(state, conflictVar)) return false;
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

    private boolean fixSlotLocally(IncrementalState state, String varKey) {
        String[] parts = varKey.split("\\|");
        LocalDate date = LocalDate.parse(parts[0]);
        String shiftType = parts[1];
        Integer currentStaffId = findStaffForSlot(state, date, shiftType);

        for (Integer staffId : state.staffIndexMap.keySet()) {
            Integer staffIdx = state.staffIndexMap.get(staffId);
            if (checkAssignmentConflict(state, date, shiftType, staffId, staffIdx) == null) {
                if (currentStaffId != null && !currentStaffId.equals(staffId)) {
                    removeAssignment(state, currentStaffId, date);
                }
                addAssignment(state, staffId, date, shiftType);
                return true;
            }
        }
        return false;
    }

    private Integer findStaffForSlot(IncrementalState state, LocalDate date, String shiftType) {
        for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            if (keyParts.length == 2 && keyParts[1].equals(date.toString()) && entry.getValue().equals(shiftType)) {
                return Integer.parseInt(keyParts[0]);
            }
        }
        return null;
    }

    private void removeAssignment(IncrementalState state, Integer staffId, LocalDate date) {
        state.assignments.remove(staffId + "_" + date);
    }

    private void addAssignment(IncrementalState state, Integer staffId, LocalDate date, String shiftType) {
        state.assignments.put(staffId + "_" + date, shiftType);
    }

    private SchedulingResult buildResultFromIncrementalState(
            IncrementalState state, List<Staff> staffList, long startTime) {

        Set<String> compensationDays = new HashSet<>();
        for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            if (keyParts.length == 2 && entry.getValue().equals(DIRECT_24H)) {
                Integer staffId = Integer.parseInt(keyParts[0]);
                LocalDate workDate = LocalDate.parse(keyParts[1]);
                LocalDate compDate = compensationDateCalculator.calculate(workDate);
                compensationDays.add(staffId + "_" + compDate);
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
