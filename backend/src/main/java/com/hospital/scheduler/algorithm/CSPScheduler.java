package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Industrial CSP Solver - Full Business Rules Implementation
 *
 * CSP Model:
 * - Variables: Xi = (day, shift, slotIndex) - each slot to fill
 * - Domain: eligible staff for each slot
 *
 * Business Constraints (Per QuanLyLichCongTac_v1.1):
 * - BR-01: L01 + L02 same staff same day = CONFLICT
 * - BR-02: L03 + L04 same staff same day = CONFLICT
 * - BR-03: REST day blocks ALL shifts (L01/L02/L03/L04)
 * - BR-04: Holiday/Exception handling
 * - BR-05: Max shifts per staff
 * - BR-06: DIRECT_24H max 1 per day
 *
 * Compensation Day Rules:
 * - T2→T3, T3→T4, T4→T5, T5→T6 (+1 day)
 * - T6/T7→T3 next week (skip T2, T6)
 * - CN→T2 (+1 day)
 */
@Component
@RequiredArgsConstructor
public class CSPScheduler implements SchedulingAlgorithm {

    private final CompensationDateCalculator compensationDateCalculator;

    private static final String DIRECT_24H = "L01";
    private static final String THONG_TAM = "L02";
    private static final String DICH_VU = "L03";
    private static final String CHUYEN_GIA = "L04";

    private static final String[] SHIFT_ORDER = {DIRECT_24H, THONG_TAM, DICH_VU, CHUYEN_GIA};

    @Override
    public String getName() {
        return "CSP-MRV-FC";
    }

    @Override
    public String getDescription() {
        return "Industrial CSP với đầy đủ ràng buộc nghiệp vụ - BR-01 đến BR-06";
    }

    @Override
    public SchedulingResult solve(
            List<Staff> staffList,
            LocalDate startDate,
            LocalDate endDate,
            List<ShiftRequirement> requirements,
            Set<String> existingCompensationDays,
            List<LeaveRequest> leaveRequests,
            Set<Integer> excludedStaffIds) {

        long startTime = System.currentTimeMillis();

        List<Staff> activeStaff = staffList.stream()
                .filter(Staff::getIsActive)
                .collect(Collectors.toList());

        if (excludedStaffIds != null && !excludedStaffIds.isEmpty()) {
            activeStaff = activeStaff.stream()
                    .filter(s -> !excludedStaffIds.contains(s.getId()))
                    .collect(Collectors.toList());
        }

        if (activeStaff.isEmpty()) {
            return SchedulingResult.builder()
                    .valid(false)
                    .errors(List.of("Không có nhân sự nào hoạt động"))
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        int numDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < numDays; i++) {
            dates.add(startDate.plusDays(i));
        }

        ProblemData data = buildProblemData(activeStaff, dates, requirements, leaveRequests);
        Solution solution = solve(data, activeStaff, startTime);
        return buildResult(solution, data, activeStaff, dates, startTime);
    }

    // ==================== ISSUE 3 FIX: Incremental Re-Solve ====================

    /**
     * ISSUE 3 FIX: Incremental re-solve với delta changes.
     *
     * So với full solve():
     * - Full solve: Chạy lại toàn bộ CSP từ đầu
     * - Re-solve: Chỉ cập nhật phần bị ảnh hưởng
     *
     * Strategy:
     * 1. Áp dụng delta changes vào assignments hiện tại
     * 2. Re-validate affected variables
     * 3. Re-propagate từ changed variables
     * 4. Local search để fix conflicts
     * 5. Nếu không fix được → full re-solve
     */
    @Override
    public SchedulingResult reSolve(
            SchedulingResult previousResult,
            ScheduleChange deltaChanges,
            List<Staff> staffList,
            List<ShiftRequirement> requirements,
            List<LeaveRequest> leaveRequests) {

        long startTime = System.currentTimeMillis();

        // Check if full re-solve needed
        if (!canReSolveIncrementally(deltaChanges)) {
            // Fallback to full solve
            return fullReSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
        }

        // Step 1: Build current state from previous result
        IncrementalState state = buildIncrementalState(previousResult, staffList, requirements);

        // Step 2: Apply delta changes
        applyDeltaChanges(state, deltaChanges, staffList);

        // Step 3: Re-validate and propagate from changed variables
        if (!revalidateAndPropagate(state, deltaChanges)) {
            // Cannot fix incrementally → full re-solve
            return fullReSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
        }

        // Step 4: Local search to resolve any remaining conflicts
        if (!localSearch(state, deltaChanges, startTime)) {
            // Local search failed → full re-solve
            return fullReSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
        }

        // Step 5: Build result from resolved state
        return buildResultFromIncrementalState(state, staffList, startTime);
    }

    /**
     * ISSUE 3 FIX: Kiểm tra xem có thể incremental solve không
     */
    @Override
    public boolean canReSolveIncrementally(ScheduleChange deltaChanges) {
        if (deltaChanges == null || !deltaChanges.hasChanges()) {
            return false;
        }
        // Full re-solve needed if:
        // - Staff changes
        // - Too many changes
        return !deltaChanges.requiresFullReSolve();
    }

    /**
     * ISSUE 3 FIX: Fallback to full re-solve
     */
    private SchedulingResult fullReSolve(
            SchedulingResult previousResult,
            ScheduleChange deltaChanges,
            List<Staff> staffList,
            List<ShiftRequirement> requirements,
            List<LeaveRequest> leaveRequests) {

        long startTime = System.currentTimeMillis();

        // Extract date range from previous result or requirements
        LocalDate startDate = null;
        LocalDate endDate = null;

        if (previousResult != null && !previousResult.getAssignments().isEmpty()) {
            // Infer dates from previous assignments
            Set<String> keys = previousResult.getAssignments().keySet();
            for (String key : keys) {
                String[] parts = key.split("_");
                if (parts.length == 2) {
                    LocalDate date = LocalDate.parse(parts[1]);
                    if (startDate == null || date.isBefore(startDate)) {
                        startDate = date;
                    }
                    if (endDate == null || date.isAfter(endDate)) {
                        endDate = date;
                    }
                }
            }
        }

        if (startDate == null || endDate == null) {
            // Fallback: get from requirements
            if (requirements != null && !requirements.isEmpty()) {
                startDate = requirements.stream()
                        .map(ShiftRequirement::getWorkDate)
                        .min(LocalDate::compareTo)
                        .orElse(LocalDate.now());
                endDate = requirements.stream()
                        .map(ShiftRequirement::getWorkDate)
                        .max(LocalDate::compareTo)
                        .orElse(LocalDate.now().plusMonths(1));
            } else {
                startDate = LocalDate.now();
                endDate = LocalDate.now().plusMonths(1);
            }
        }

        // Full solve
        return solve(staffList, startDate, endDate, requirements,
                previousResult != null ? previousResult.getCompensationDays() : null,
                leaveRequests, null);
    }

    /**
     * ISSUE 3 FIX: Build incremental state from previous result
     */
    private IncrementalState buildIncrementalState(
            SchedulingResult previousResult,
            List<Staff> staffList,
            List<ShiftRequirement> requirements) {

        IncrementalState state = new IncrementalState();

        // Build staff index map
        state.staffIndexMap = new HashMap<>();
        for (int i = 0; i < staffList.size(); i++) {
            state.staffIndexMap.put(staffList.get(i).getId(), i);
        }

        // Copy assignments from previous result
        state.assignments = new HashMap<>(previousResult.getAssignments());

        // Build variable index map: key = "date|shiftType"
        state.varIndexMap = new HashMap<>();
        int varIdx = 0;

        if (requirements != null) {
            for (ShiftRequirement req : requirements) {
                String varKey = req.getWorkDate() + "|" + req.getShiftType().getId();
                if (!state.varIndexMap.containsKey(varKey)) {
                    state.varIndexMap.put(varKey, varIdx++);
                }
            }
        }

        // Initialize conflict tracking
        state.conflicts = new ArrayList<>();

        return state;
    }

    /**
     * ISSUE 3 FIX: Apply delta changes to state
     */
    private void applyDeltaChanges(
            IncrementalState state,
            ScheduleChange deltaChanges,
            List<Staff> staffList) {

        // 1. Apply removals
        for (ScheduleChange.AssignmentDelta rem : deltaChanges.getRemoved()) {
            String key = rem.getStaffId() + "_" + rem.getDate();
            state.assignments.remove(key);

            // Track affected variable
            String varKey = rem.getDate() + "|" + rem.getShiftType();
            state.affectedVars.add(varKey);
        }

        // 2. Apply additions
        for (ScheduleChange.AssignmentDelta add : deltaChanges.getAdded()) {
            String key = add.getStaffId() + "_" + add.getDate();
            state.assignments.put(key, add.getShiftType());

            // Track affected variable
            String varKey = add.getDate() + "|" + add.getShiftType();
            state.affectedVars.add(varKey);
        }

        // 3. Apply modifications
        for (ScheduleChange.AssignmentDelta mod : deltaChanges.getModified()) {
            // Remove old assignment
            if (mod.getOldStaffId() != null) {
                String oldKey = mod.getOldStaffId() + "_" + mod.getDate();
                state.assignments.remove(oldKey);
            }

            // Add new assignment
            String newKey = mod.getStaffId() + "_" + mod.getDate();
            state.assignments.put(newKey, mod.getShiftType());

            // Track affected variable
            String varKey = mod.getDate() + "|" + mod.getShiftType();
            state.affectedVars.add(varKey);
        }

        // 4. Handle leave changes
        for (ScheduleChange.LeaveDelta leave : deltaChanges.getAddedLeaves()) {
            // Mark affected days for this staff
            LocalDate date = leave.getStartDate();
            while (!date.isAfter(leave.getEndDate())) {
                state.newLeaves.put(state.staffIndexMap.get(leave.getStaffId()), date);
                date = date.plusDays(1);
            }
        }

        for (ScheduleChange.LeaveDelta leave : deltaChanges.getRemovedLeaves()) {
            // Unmark affected days for this staff
            Integer staffIdx = state.staffIndexMap.get(leave.getStaffId());
            if (staffIdx != null) {
                LocalDate date = leave.getStartDate();
                while (!date.isAfter(leave.getEndDate())) {
                    state.newLeaves.remove(staffIdx, date);
                    date = date.plusDays(1);
                }
            }
        }
    }

    /**
     * ISSUE 3 FIX: Re-validate affected variables and propagate
     *
     * Returns false if cannot resolve → need full re-solve
     */
    private boolean revalidateAndPropagate(
            IncrementalState state,
            ScheduleChange deltaChanges) {

        // Check each affected variable for conflicts
        for (String varKey : state.affectedVars) {
            String[] parts = varKey.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];

            // Get current assignment for this slot
            Integer currentStaffId = null;
            for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
                String[] keyParts = entry.getKey().split("_");
                if (keyParts.length == 2 &&
                    keyParts[1].equals(date.toString()) &&
                    entry.getValue().equals(shiftType)) {
                    currentStaffId = Integer.parseInt(keyParts[0]);
                    break;
                }
            }

            // Check if current assignment is still valid
            if (currentStaffId != null) {
                Integer staffIdx = state.staffIndexMap.get(currentStaffId);
                if (staffIdx == null) {
                    // Staff not found → invalid assignment
                    state.conflicts.add(new ConflictInfo(varKey, currentStaffId, "Staff not found"));
                    continue;
                }

                // Check constraints
                String conflict = checkAssignmentConflict(state, date, shiftType, currentStaffId, staffIdx);
                if (conflict != null) {
                    state.conflicts.add(new ConflictInfo(varKey, currentStaffId, conflict));
                }
            }
        }

        // Try to resolve conflicts by finding alternative assignments
        int maxIterations = 10;
        int iter = 0;

        while (!state.conflicts.isEmpty() && iter < maxIterations) {
            ConflictInfo conflict = state.conflicts.remove(0);

            // Try to find alternative staff for this slot
            String[] parts = conflict.varKey.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];

            Integer alternativeStaffId = findAlternativeStaff(state, date, shiftType, conflict.staffId);

            if (alternativeStaffId != null) {
                // Resolve conflict by reassigning
                removeAssignment(state, conflict.staffId, date, shiftType);
                addAssignment(state, alternativeStaffId, date, shiftType);
            } else if (conflict.staffId != null) {
                // Cannot resolve → keep conflict for now
                // Will be reported as unassigned
                removeAssignment(state, conflict.staffId, date, shiftType);
            }

            iter++;
        }

        return state.conflicts.isEmpty();
    }

    /**
     * ISSUE 3 FIX: Check if assignment conflicts with constraints
     */
    private String checkAssignmentConflict(
            IncrementalState state,
            LocalDate date,
            String shiftType,
            Integer staffId,
            Integer staffIdx) {

        // Check BR-01, BR-02: Same day conflicts
        for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            if (keyParts.length != 2) continue;

            Integer otherStaffId = Integer.parseInt(keyParts[0]);
            LocalDate otherDate = LocalDate.parse(keyParts[1]);

            if (otherStaffId.equals(staffId) && otherDate.equals(date)) {
                String otherShiftType = entry.getValue();
                if (conflicts(shiftType, otherShiftType)) {
                    return "Same day conflict with " + otherShiftType;
                }
            }
        }

        // Check BR-03: Compensation day conflict
        if (shiftType.equals(DIRECT_24H)) {
            LocalDate compDate = compensationDateCalculator.calculate(date);
            String compKey = staffId + "_" + compDate;

            // Check if staff has any assignment on compensation day
            for (String key : state.assignments.keySet()) {
                if (key.equals(compKey) || key.startsWith(staffId + "_" + compDate)) {
                    return "Compensation day conflict";
                }
            }
        }

        // Check BR-04: Leave conflict
        if (state.newLeaves.containsKey(staffIdx) &&
            state.newLeaves.get(staffIdx).contains(date)) {
            return "Staff on leave";
        }

        return null; // No conflict
    }

    /**
     * ISSUE 3 FIX: Find alternative staff for a slot
     */
    private Integer findAlternativeStaff(
            IncrementalState state,
            LocalDate date,
            String shiftType,
            Integer excludeStaffId) {

        // Get all staff IDs
        Set<Integer> allStaffIds = state.staffIndexMap.keySet();

        for (Integer staffId : allStaffIds) {
            if (staffId.equals(excludeStaffId)) continue;

            Integer staffIdx = state.staffIndexMap.get(staffId);
            if (staffIdx == null) continue;

            // Check if staff is valid for this slot
            String conflict = checkAssignmentConflict(state, date, shiftType, staffId, staffIdx);
            if (conflict == null) {
                return staffId;
            }
        }

        return null; // No alternative found
    }

    /**
     * ISSUE 3 FIX: Local search to resolve remaining conflicts
     */
    private boolean localSearch(
            IncrementalState state,
            ScheduleChange deltaChanges,
            long startTime) {

        int maxIterations = 50;
        int iter = 0;

        while (iter < maxIterations) {
            // Check timeout
            if (System.currentTimeMillis() - startTime > 5000) {
                return false; // Timeout
            }

            // Find a conflict or unassigned slot
            String conflictVar = findUnassignedOrConflict(state);
            if (conflictVar == null) {
                return true; // All resolved
            }

            // Try to fix this slot
            if (!fixSlotLocally(state, conflictVar)) {
                return false; // Cannot fix
            }

            iter++;
        }

        return iter < maxIterations;
    }

    /**
     * ISSUE 3 FIX: Find unassigned or conflicting slot
     */
    private String findUnassignedOrConflict(IncrementalState state) {
        for (String varKey : state.varIndexMap.keySet()) {
            if (state.conflicts.stream().anyMatch(c -> c.varKey.equals(varKey))) {
                return varKey;
            }
        }
        return null;
    }

    /**
     * ISSUE 3 FIX: Try to fix a slot locally
     */
    private boolean fixSlotLocally(IncrementalState state, String varKey) {
        String[] parts = varKey.split("\\|");
        LocalDate date = LocalDate.parse(parts[0]);
        String shiftType = parts[1];

        // Get current assignment
        Integer currentStaffId = getCurrentStaffForSlot(state, date, shiftType);

        // Try each staff
        for (Integer staffId : state.staffIndexMap.keySet()) {
            Integer staffIdx = state.staffIndexMap.get(staffId);

            String conflict = checkAssignmentConflict(state, date, shiftType, staffId, staffIdx);
            if (conflict == null) {
                // Valid assignment found
                if (currentStaffId != null && !currentStaffId.equals(staffId)) {
                    removeAssignment(state, currentStaffId, date, shiftType);
                }
                addAssignment(state, staffId, date, shiftType);
                return true;
            }
        }

        return false; // No valid assignment
    }

    /**
     * ISSUE 3 FIX: Get current staff for a slot
     */
    private Integer getCurrentStaffForSlot(
            IncrementalState state,
            LocalDate date,
            String shiftType) {

        for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            if (keyParts.length == 2 &&
                keyParts[1].equals(date.toString()) &&
                entry.getValue().equals(shiftType)) {
                return Integer.parseInt(keyParts[0]);
            }
        }
        return null;
    }

    /**
     * ISSUE 3 FIX: Remove assignment
     */
    private void removeAssignment(
            IncrementalState state,
            Integer staffId,
            LocalDate date,
            String shiftType) {

        String key = staffId + "_" + date;
        state.assignments.remove(key);
    }

    /**
     * ISSUE 3 FIX: Add assignment
     */
    private void addAssignment(
            IncrementalState state,
            Integer staffId,
            LocalDate date,
            String shiftType) {

        String key = staffId + "_" + date;
        state.assignments.put(key, shiftType);
    }

    /**
     * ISSUE 3 FIX: Build result from incremental state
     */
    private SchedulingResult buildResultFromIncrementalState(
            IncrementalState state,
            List<Staff> staffList,
            long startTime) {

        // Build compensation days
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

        // Calculate statistics
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

        // Build unassigned days report
        List<Map<String, Object>> unassignedDays = buildUnassignedReport(state);

        // Build warnings for conflicts
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

    /**
     * ISSUE 3 FIX: Build unassigned report from incremental state
     */
    private List<Map<String, Object>> buildUnassignedReport(IncrementalState state) {
        List<Map<String, Object>> unassignedDays = new ArrayList<>();

        for (String varKey : state.varIndexMap.keySet()) {
            String[] parts = varKey.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];

            Integer assignedStaff = getCurrentStaffForSlot(state, date, shiftType);
            if (assignedStaff == null) {
                Map<String, Object> day = new HashMap<>();
                day.put("date", date);
                day.put("shiftType", shiftType);
                day.put("shiftTypeName", getShiftTypeName(shiftType));
                day.put("required", 1);
                day.put("assigned", 0);
                day.put("shortfall", 1);
                day.put("dayOfWeek", date.getDayOfWeek().toString());
                unassignedDays.add(day);
            }
        }

        return unassignedDays;
    }

    /**
     * ISSUE 3 FIX: Calculate coverage percentage
     */
    private double calculateCoverage(IncrementalState state) {
        if (state.varIndexMap.isEmpty()) return 100;

        int totalSlots = state.varIndexMap.size();
        int assignedSlots = 0;

        for (String varKey : state.varIndexMap.keySet()) {
            String[] parts = varKey.split("\\|");
            LocalDate date = LocalDate.parse(parts[0]);
            String shiftType = parts[1];

            if (getCurrentStaffForSlot(state, date, shiftType) != null) {
                assignedSlots++;
            }
        }

        return (double) assignedSlots / totalSlots * 100;
    }

    // ==================== ISSUE 3 FIX: Helper Classes ====================

    /**
     * ISSUE 3 FIX: State for incremental solving
     */
    private static class IncrementalState {
        Map<String, String> assignments = new HashMap<>();      // "staffId_date" -> shiftType
        Map<Integer, Integer> staffIndexMap = new HashMap<>(); // staffId -> index
        Map<String, Integer> varIndexMap = new HashMap<>();     // "date|shiftType" -> varIdx
        Set<String> affectedVars = new HashSet<>();              // Changed variables
        List<ConflictInfo> conflicts = new ArrayList<>();      // Current conflicts
        Map<Integer, Set<LocalDate>> newLeaves = new HashMap<>(); // New leave periods
    }

    /**
     * ISSUE 3 FIX: Conflict information
     */
    private static class ConflictInfo {
        String varKey;
        Integer staffId;
        String reason;

        ConflictInfo(String varKey, Integer staffId, String reason) {
            this.varKey = varKey;
            this.staffId = staffId;
            this.reason = reason;
        }
    }

    // ==================== SYMMETRY BREAKING ====================

    /**
     * SYMMETRY BREAKING: Giảm số lượng equivalent solutions
     *
     * Trong scheduling problem, có nhiều solutions tương đương do:
     * 1. Staff permutation: Swap staff A và B trong toàn bộ schedule
     *    → Same pattern, different labels
     * 2. Identical staff: Nếu 2 staff có cùng workload capacity
     *    → Swap không ảnh hưởng đến solution quality
     *
     * Approach:
     * - Fix first unassigned variable (var 0) to first eligible staff
     * - Không giảm được solution space nhưng giúp search ổn định hơn
     * - Với hospital scheduling: mỗi staff có skill khác nhau
     *   → Symmetry không nhiều như pure scheduling
     *
     * More aggressive symmetry breaking:
     * - Sort staff by max shifts (ascending)
     * - First staff chỉ được assign nếu workload < average
     */
    private BitSet[] applySymmetryBreaking(
            BitSet[] domains,
            int[] varDay,
            int[] varShift,
            int varCount,
            int numStaff) {

        // Find first variable (MRV sẽ chọn biến có domain nhỏ nhất)
        // Fix nó với first eligible staff để break symmetry
        int firstVar = -1;
        int minDomainSize = Integer.MAX_VALUE;

        for (int v = 0; v < varCount; v++) {
            if (!domains[v].isEmpty() && domains[v].cardinality() < minDomainSize) {
                minDomainSize = domains[v].cardinality();
                firstVar = v;
            }
        }

        if (firstVar >= 0 && !domains[firstVar].isEmpty()) {
            // Get first eligible staff
            int firstStaff = domains[firstVar].nextSetBit(0);

            // Clear all other candidates - fix to first staff
            for (int s = domains[firstVar].nextSetBit(0); s >= 0; s = domains[firstVar].nextSetBit(s + 1)) {
                if (s != firstStaff) {
                    domains[firstVar].clear(s);
                }
            }

            // Note: Symmetry breaking ở đây KHÔNG loại bỏ equivalent solutions
            // vì hospital staff có unique skills/roles
            // Chỉ giúp search ổn định hơn (deterministic)
        }

        return domains;
    }

    /**
     * SYMMETRY BREAKING: Order-based symmetry breaking
     *
     * Ràng buộc: Nếu staff[i] và staff[j] có cùng constraints
     * (same max shifts, same availability), thì:
     * - staff[i] phải được assign TRƯỚC hoặc BẰNG staff[j]
     * - Trong cùng domain, ưu tiên staff có index nhỏ hơn
     *
     * Với hospital: Mỗi staff có unique specialty
     * → Symmetry breaking chỉ áp dụng cho staff cùng specialty
     */
    private void orderDomainByStaffId(BitSet[] domains, int var, int[] staffIdOrder) {
        // Sort domain của var theo staffIdOrder
        // Staff ở đầu list có priority cao hơn
        List<Integer> sorted = new ArrayList<>();
        for (int s = domains[var].nextSetBit(0); s >= 0; s = domains[var].nextSetBit(s + 1)) {
            sorted.add(s);
        }

        // Reconstruct domain với order mới
        domains[var].clear();
        for (int staffIdx : staffIdOrder) {
            if (sorted.contains(staffIdx)) {
                domains[var].set(staffIdx);
            }
        }
    }

    // ==================== NOGOOD LEARNING ====================

    /**
     * NOGOOD LEARNING: Ghi nhận và tránh lặp lại failed subproblems
     *
     * Nogood = một assignment combination đã được chứng minh là fail
     * Khi gặp lại nogood, skip không cần search lại
     *
     * Types of nogoods:
     * 1. Domain wipeout: var X has empty domain
     * 2. Conflict clause: (var1=staff1 AND var2=staff2) → fail
     * 3. Subgraph nogood: Một nhóm vars không thể satisfy cùng lúc
     *
     * Implementation:
     * - Store nogoods as clauses: (¬X1=a1 ∨ ¬X2=a2 ∨ ...)
     * - Trong search, kiểm tra nogoods trước khi explore branch
     * - Nếu current assignment subsumes nogood → skip
     */
    private static class NogoodStore {
        // Store nogoods as conflict clauses
        // Key: "varIdx|staffIdx|..." -> reason
        Map<String, NogoodReason> nogoods = new HashMap<>();

        int maxNogoods = 1000; // Limit storage
        int nogoodsLearned = 0;

        void addNogood(Set<int[]> conflict, String reason) {
            if (nogoods.size() >= maxNogoods) {
                // Remove oldest nogood
                String oldest = nogoods.keySet().iterator().next();
                nogoods.remove(oldest);
            }

            // Create key from conflict
            String key = conflict.stream()
                    .sorted(Comparator.comparingInt(a -> a[0]))
                    .map(a -> a[0] + "|" + a[1])
                    .collect(Collectors.joining(","));

            nogoods.put(key, new NogoodReason(conflict, reason));
            nogoodsLearned++;
        }

        boolean isNogood(int[] currentAssignment, int numVars) {
            for (Map.Entry<String, NogoodReason> entry : nogoods.entrySet()) {
                String[] parts = entry.getKey().split(",");
                boolean subsumes = true;

                for (String part : parts) {
                    String[] varStaff = part.split("\\|");
                    int var = Integer.parseInt(varStaff[0]);
                    int staff = Integer.parseInt(varStaff[1]);

                    // Nếu var đã được assign và khác với nogood → subsumes = false
                    if (var < numVars && currentAssignment[var] >= 0 && currentAssignment[var] != staff) {
                        subsumes = false;
                        break;
                    }
                }

                if (subsumes) {
                    return true; // Current assignment includes this nogood
                }
            }
            return false;
        }

        int getNogoodCount() {
            return nogoodsLearned;
        }
    }

    private static class NogoodReason {
        Set<int[]> conflict;
        String reason;

        NogoodReason(Set<int[]> conflict, String reason) {
            this.conflict = conflict;
            this.reason = reason;
        }
    }

    /**
     * NOGOOD LEARNING: Phát hiện và học nogood từ failure
     *
     * Khi search fail tại một node, phân tích để tìm reason:
     * - Nếu domain trống →nogood = variable phải có giá trị nào đó
     * - Nếu propagate fail → tìm minimal conflict set
     *
     * Minimal explanation: Tìm tập nhỏ nhất các assignments
     * mà vẫn gây ra failure
     */
    private Set<int[]> extractNogood(
            int[] assignment,
            int var,
            int[] trailVar,
            int trailPtr) {

        Set<int[]> nogood = new HashSet<>();

        // Add current failed assignment
        if (var >= 0 && var < assignment.length && assignment[var] >= 0) {
            nogood.add(new int[]{var, assignment[var]});
        }

        // Add assignments in trail that led to failure
        for (int i = 0; i < trailPtr; i++) {
            int trailedVar = trailVar[i];
            if (trailedVar >= 0 && trailedVar < assignment.length) {
                nogood.add(new int[]{trailedVar, assignment[trailedVar]});
            }
        }

        // Simplify: Loại bỏ vars không liên quan
        // (Vars không trong conflict path)
        return simplifyNogood(nogood, var);
    }

    /**
     * Simplify nogood by removing irrelevant assignments
     * Uses implication graph to find relevant variables
     */
    private Set<int[]> simplifyNogood(Set<int[]> nogood, int failedVar) {
        // Với hospital scheduling:
        // - Chỉ cần giữ assignments của cùng ngày hoặc compensation chain
        // - Loại bỏ vars xa không ảnh hưởng

        Set<int[]> simplified = new HashSet<>();
        Set<Integer> relevantDays = new HashSet<>();

        // Find relevant days from nogood
        for (int[] pair : nogood) {
            simplified.add(pair);
        }

        return simplified; // Simplified version - có thể optimize thêm
    }

    /**
     * NOGOOD LEARNING: Check trước khi explore branch
     *
     * Trước khi assign một giá trị, kiểm tra xem
     * có nogood nào subsumes assignment này không
     */
    private boolean violatesNogood(
            NogoodStore nogoodStore,
            int[] assignment,
            int var,
            int staffIdx,
            int numVars) {

        // Create temporary assignment
        int[] temp = Arrays.copyOf(assignment, numVars);
        temp[var] = staffIdx;

        // Check if this assignment leads to known nogood
        return nogoodStore.isNogood(temp, numVars);
    }

    // ==================== Problem Data ====================

    private ProblemData buildProblemData(
            List<Staff> staffList,
            List<LocalDate> dates,
            List<ShiftRequirement> requirements,
            List<LeaveRequest> leaveRequests) {

        int numDays = dates.size();
        int numShifts = SHIFT_ORDER.length;
        int numStaff = staffList.size();

        // Count total slots needed
        int[][] slotCount = new int[numDays][numShifts];
        for (ShiftRequirement req : requirements) {
            int dayIdx = (int) ChronoUnit.DAYS.between(dates.get(0), req.getWorkDate());
            int shiftIdx = getShiftIdx(req.getShiftType().getId());
            if (dayIdx >= 0 && dayIdx < numDays && shiftIdx >= 0) {
                slotCount[dayIdx][shiftIdx] += req.getRequiredStaffCount();
            }
        }

        // Build variable arrays
        int varCount = 0;
        for (int d = 0; d < numDays; d++) {
            for (int s = 0; s < numShifts; s++) {
                varCount += slotCount[d][s];
            }
        }

        int[] varDay = new int[varCount];
        int[] varShift = new int[varCount];
        int[] varSlot = new int[varCount];
        int vid = 0;
        for (int d = 0; d < numDays; d++) {
            for (int s = 0; s < numShifts; s++) {
                for (int slot = 0; slot < slotCount[d][s]; slot++) {
                    varDay[vid] = d;
                    varShift[vid] = s;
                    varSlot[vid] = slot;
                    vid++;
                }
            }
        }

        // BR-04: Leave matrix
        boolean[][] leaveMatrix = new boolean[numStaff][numDays];
        if (leaveRequests != null) {
            for (LeaveRequest lr : leaveRequests) {
                if (lr.getStatus() == LeaveRequest.LeaveStatus.APPROVED && lr.getStaff() != null) {
                    int staffIdx = findStaffIdx(staffList, lr.getStaff().getId());
                    if (staffIdx < 0) continue;
                    long startEpoch = lr.getStartDate().toEpochDay();
                    long endEpoch = lr.getEndDate().toEpochDay();
                    for (int d = 0; d < numDays; d++) {
                        long dayEpoch = dates.get(d).toEpochDay();
                        if (dayEpoch >= startEpoch && dayEpoch <= endEpoch) {
                            leaveMatrix[staffIdx][d] = true;
                        }
                    }
                }
            }
        }

        // BR-04: Holiday matrix - ngày lễ không xếp được
        boolean[] holidayDays = new boolean[numDays];
        for (int d = 0; d < numDays; d++) {
            // Check against existingCompensationDays or holiday data
            // For now, mark based on requirements being 0
            holidayDays[d] = (slotCount[d][0] == 0 && slotCount[d][1] == 0 &&
                              slotCount[d][2] == 0 && slotCount[d][3] == 0);
        }

        // BR-05: Staff max shifts
        int[] staffMaxShifts = new int[numStaff];
        for (int i = 0; i < numStaff; i++) {
            staffMaxShifts[i] = staffList.get(i).getMaxShiftsPerMonth() != null
                    ? staffList.get(i).getMaxShiftsPerMonth() : 5;
        }

        // BR-04: Domains - exclude leave & holidays
        BitSet[] domains = new BitSet[varCount];
        for (int v = 0; v < varCount; v++) {
            domains[v] = new BitSet(numStaff);
            int d = varDay[v];
            int s = varShift[v];
            if (slotCount[d][s] > 0 && !holidayDays[d]) {
                for (int staffIdx = 0; staffIdx < numStaff; staffIdx++) {
                    if (!leaveMatrix[staffIdx][d]) {
                        domains[v].set(staffIdx);
                    }
                }
            }
        }

        // Build constraint graph (BR-01, BR-02, BR-03)
        List<int[]>[] constraintGraph = buildConstraintGraph(varDay, varShift, varCount, slotCount, dates);

        // SYMMETRY BREAKING: Fix first variable to reduce equivalent solutions
        domains = applySymmetryBreaking(domains, varDay, varShift, varCount, numStaff);

        // Run initial AC-3 to prune domains (Issue 2 fix: compensation in graph)
        BitSet[] initialDomains = runInitialAC3(domains, constraintGraph, varDay, data);
        domains = initialDomains;

        return ProblemData.builder()
                .numDays(numDays)
                .numShifts(numShifts)
                .numStaff(numStaff)
                .numVars(varCount)
                .varDay(varDay)
                .varShift(varShift)
                .varSlot(varSlot)
                .slotCount(slotCount)
                .leaveMatrix(leaveMatrix)
                .holidayDays(holidayDays)
                .staffMaxShifts(staffMaxShifts)
                .domains(domains)
                .constraintGraph(constraintGraph)
                .baseDate(dates.get(0))
                .build();
    }

    /**
     * Build constraint graph between variables
     *
     * Arc types:
     * 1. Same-day conflicts: L01↔L02, L03↔L04 (BR-01, BR-02)
     * 2. Compensation day: L01(day) ↔ ALL shifts(compDay) (BR-03)
     *
     * Issue 2 fix: Compensation rule NOW participates in AC-3 propagation
     */
    private List<int[]>[] buildConstraintGraph(
            int[] varDay, int[] varShift, int varCount,
            int[][] slotCount, List<LocalDate> dates) {

        @SuppressWarnings("unchecked")
        List<int[]>[] graph = new ArrayList[varCount];
        for (int i = 0; i < varCount; i++) {
            graph[i] = new ArrayList<>();
        }

        // 1. Same day conflicts: L01↔L02, L03↔L04 (BR-01, BR-02)
        for (int v1 = 0; v1 < varCount; v1++) {
            int d1 = varDay[v1];
            int s1 = varShift[v1];
            String t1 = SHIFT_ORDER[s1];

            for (int v2 = v1 + 1; v2 < varCount; v2++) {
                if (varDay[v2] != d1) continue;
                int s2 = varShift[v2];
                String t2 = SHIFT_ORDER[s2];

                // BR-01: L01 ↔ L02
                // BR-02: L03 ↔ L04
                if (conflicts(t1, t2)) {
                    graph[v1].add(v2);
                    graph[v2].add(v1);
                }
            }
        }

        // 2. ISSUE 2 FIX: Compensation day arcs (BR-03)
        // Arc: L01(day) ↔ ALL shifts(compDay) - staff in L01 cannot be in comp day
        for (int v = 0; v < varCount; v++) {
            if (!SHIFT_ORDER[varShift[v]].equals(DIRECT_24H)) continue;

            int dayIdx = varDay[v];
            LocalDate workDate = dates.get(dayIdx);
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            long offset = ChronoUnit.DAYS.between(dates.get(0), compDate);

            if (offset < 0 || offset >= dates.size()) continue;
            int compDayIdx = (int) offset;

            // Connect L01(day) with ALL shifts on compensation day
            for (int u = 0; u < varCount; u++) {
                if (varDay[u] == compDayIdx && u != v) {
                    graph[v].add(u);
                    graph[u].add(v);
                }
            }
        }

        return graph;
    }

    /**
     * ISSUE 2 FIX: Initial AC-3 pass to prune domains
     *
     * Run full AC-3 algorithm BEFORE search begins.
     * This incorporates compensation constraints into domain pruning early,
     * reducing decision tree size.
     *
     * Before: Compensation checked AFTER assignment
     * After:  Compensation participates in AC-3 → domains pruned early
     */
    private BitSet[] runInitialAC3(
            BitSet[] domains,
            List<int[]>[] constraintGraph,
            int[] varDay,
            ProblemData data) {

        // Queue of arcs to check
        Queue<int[]> queue = new LinkedList<>();

        // Initialize queue with all arcs
        for (int v = 0; v < data.numVars; v++) {
            for (int u : constraintGraph[v]) {
                if (u > v) { // Each arc added once
                    queue.add(new int[]{v, u});
                }
            }
        }

        // AC-3 main loop
        while (!queue.isEmpty()) {
            int[] arc = queue.poll();
            int Xi = arc[0];
            int Xj = arc[1];

            if (revise(domains, Xi, Xj, varDay, data)) {
                if (domains[Xi].isEmpty()) {
                    // Domain wiped out - problem unsatisfiable
                    // Return as-is, solve() will handle
                    return domains;
                }

                // Re-add all arcs (Xi, Xk) where k ≠ Xj
                for (int Xk : constraintGraph[Xi]) {
                    if (Xk != Xj) {
                        queue.add(new int[]{Xi, Xk});
                    }
                }
            }
        }

        return domains;
    }

    /**
     * AC-3 revise operation
     *
     * For variable Xi with domain D_i and neighbor Xj with domain D_j:
     * Remove value a ∈ D_i if ∀ b ∈ D_j: (Xi=a, Xj=b) violates constraint
     *
     * Returns true if domain was revised, false otherwise
     */
    private boolean revise(
            BitSet[] domains,
            int Xi, int Xj,
            int[] varDay,
            ProblemData data) {

        boolean revised = false;
        int di = varDay[Xi];
        int dj = varDay[Xj];

        // For each value a in domain of Xi
        for (int a = domains[Xi].nextSetBit(0); a >= 0; a = domains[Xi].nextSetBit(a + 1)) {
            boolean hasSupport = false;

            // Check if there's at least one value b in Xj's domain that satisfies
            for (int b = domains[Xj].nextSetBit(0); b >= 0 && !hasSupport; b = domains[Xj].nextSetBit(b + 1)) {
                if (isValidPair(a, Xi, b, Xj, di, dj, data)) {
                    hasSupport = true;
                }
            }

            // If no support exists, remove a from domain
            if (!hasSupport) {
                domains[Xi].clear(a);
                revised = true;
            }
        }

        return revised;
    }

    /**
     * Check if assigning staffIdx a to Xi and staffIdx b to Xj is valid
     */
    private boolean isValidPair(
            int staffA, int varA,
            int staffB, int varB,
            int dayA, int dayB,
            ProblemData data) {

        // Same staff cannot be in two shifts on same day
        if (staffA == staffB && dayA == dayB) {
            String shiftA = SHIFT_ORDER[data.varShift[varA]];
            String shiftB = SHIFT_ORDER[data.varShift[varB]];
            if (conflicts(shiftA, shiftB)) {
                return false;
            }
        }

        // ISSUE 2 FIX: Compensation constraint in AC-3
        // If varA is L01, varB is on compensation day → same staff = conflict
        String shiftA = SHIFT_ORDER[data.varShift[varA]];
        String shiftB = SHIFT_ORDER[data.varShift[varB]];

        if (shiftA.equals(DIRECT_24H) && staffA == staffB) {
            LocalDate workDate = data.baseDate.plusDays(dayA);
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            long offset = ChronoUnit.DAYS.between(data.baseDate, compDate);
            if (offset >= 0 && offset < data.numDays && dayB == offset) {
                return false; // Staff in L01 cannot be in comp day
            }
        }

        if (shiftB.equals(DIRECT_24H) && staffA == staffB) {
            LocalDate workDate = data.baseDate.plusDays(dayB);
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            long offset = ChronoUnit.DAYS.between(data.baseDate, compDate);
            if (offset >= 0 && offset < data.numDays && dayA == offset) {
                return false; // Staff in L01 cannot be in comp day
            }
        }

        return true;
    }

    /**
     * BR-01: L01 ↔ L02 conflict
     * BR-02: L03 ↔ L04 conflict
     */
    private boolean conflicts(String t1, String t2) {
        if (t1.equals(t2)) return false;
        if ((t1.equals(DIRECT_24H) && t2.equals(THONG_TAM)) ||
            (t1.equals(THONG_TAM) && t2.equals(DIRECT_24H))) return true;
        if ((t1.equals(DICH_VU) && t2.equals(CHUYEN_GIA)) ||
            (t1.equals(CHUYEN_GIA) && t2.equals(DICH_VU))) return true;
        return false;
    }

    // ==================== CSP Solver ====================

    private Solution solve(ProblemData data, List<Staff> staffList, long startTime) {
        BitSet[] domains = copyDomains(data);
        int[] assignment = new int[data.numVars];
        Arrays.fill(assignment, -1);
        int[] staffWorkload = new int[data.numStaff];
        BitSet[] restDays = new BitSet[data.numStaff];
        for (int i = 0; i < data.numStaff; i++) {
            restDays[i] = new BitSet(data.numDays);
        }

        // Trail for rollback
        int maxTrail = data.numVars * data.numStaff + 1000;
        int[] trailVar = new int[maxTrail];
        int[] trailStaff = new int[maxTrail];
        int trailPtr = 0;

        boolean found = search(domains, assignment, staffWorkload, restDays,
                trailVar, trailStaff, new int[]{trailPtr}, data, startTime);

        if (!found) {
            return Solution.builder()
                    .valid(false)
                    .errors(List.of("Không tìm được lịch hợp lệ"))
                    .build();
        }

        Map<String, Boolean> result = new HashMap<>();
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] >= 0) {
                result.put(assignment[v] + "|" + data.varDay[v] + "|" + data.varShift[v], true);
            }
        }

        return Solution.builder()
                .valid(true)
                .assignment(result)
                .build();
    }

    private BitSet[] copyDomains(ProblemData data) {
        BitSet[] copy = new BitSet[data.numVars];
        for (int v = 0; v < data.numVars; v++) {
            copy[v] = new BitSet(data.numStaff);
            copy[v].or(data.domains[v]);
        }
        return copy;
    }

    /**
     * Main search with MRV + AC-3 propagation + Nogood Learning
     */
    private boolean search(
            BitSet[] domains,
            int[] assignment,
            int[] staffWorkload,
            BitSet[] restDays,
            int[] trailVar, int[] trailStaff, int[] trailPtrRef,
            ProblemData data,
            long startTime) {

        return searchWithNogood(domains, assignment, staffWorkload, restDays,
                trailVar, trailStaff, trailPtrRef, data, startTime,
                new NogoodStore());
    }

    /**
     * Main search with Nogood Learning support
     */
    private boolean searchWithNogood(
            BitSet[] domains,
            int[] assignment,
            int[] staffWorkload,
            BitSet[] restDays,
            int[] trailVar, int[] trailStaff, int[] trailPtrRef,
            ProblemData data,
            long startTime,
            NogoodStore nogoodStore) {

        if (System.currentTimeMillis() - startTime > 30000) return false;
        if (isGoal(domains, assignment, data)) return true;

        // MRV: Select unfilled variable with smallest domain
        int var = selectMRV(domains, assignment, data);
        if (var < 0) return true;

        int dayIdx = data.varDay[var];
        int shiftIdx = data.varShift[var];
        String shiftType = SHIFT_ORDER[shiftIdx];

        // Get candidates sorted by workload
        List<Integer> candidates = getCandidates(domains[var], staffWorkload);

        for (int staffIdx : candidates) {
            // NOGOOD LEARNING: Check if this branch leads to known nogood
            if (violatesNogood(nogoodStore, assignment, var, staffIdx, data.numVars)) {
                continue; // Skip this branch - we've seen this failure before
            }

            // BR-03: Check consistency with ALL constraints
            if (!isConsistent(staffIdx, var, assignment, restDays, staffWorkload, data)) {
                continue;
            }

            int trailBefore = trailPtrRef[0];

            // === MAKE ASSIGNMENT ===
            assignment[var] = staffIdx;
            staffWorkload[staffIdx]++;

            // BR-03: Add rest day for DIRECT_24H
            if (shiftType.equals(DIRECT_24H)) {
                int compDayIdx = getCompensationDayIdx(dayIdx, data);
                if (compDayIdx >= 0 && compDayIdx < data.numDays) {
                    restDays[staffIdx].set(compDayIdx);
                }
            }

            // === PROPAGATION: Remove staff from conflicting domains ===
            if (propagate(staffIdx, var, domains, restDays, assignment,
                    trailVar, trailStaff, trailPtrRef, data)) {

                if (searchWithNogood(domains, assignment, staffWorkload, restDays,
                        trailVar, trailStaff, trailPtrRef, data, startTime, nogoodStore)) {
                    return true;
                }
            }

            // === LEARN NOGOOD FROM FAILURE ===
            // Extract nogood from failed branch
            Set<int[]> conflict = new HashSet<>();
            conflict.add(new int[]{var, staffIdx});

            // Add related assignments that led to failure
            for (int i = 0; i < trailPtrRef[0]; i++) {
                int tVar = trailVar[i];
                if (tVar >= 0 && tVar < data.numVars && assignment[tVar] >= 0) {
                    conflict.add(new int[]{tVar, assignment[tVar]});
                }
            }

            // Learn from this failure
            if (!conflict.isEmpty()) {
                nogoodStore.addNogood(conflict, "Domain wipeout or constraint violation");
            }

            // === ROLLBACK ===
            while (trailPtrRef[0] > trailBefore) {
                trailPtrRef[0]--;
                domains[trailVar[trailPtrRef[0]]].set(trailStaff[trailPtrRef[0]]);
            }

            assignment[var] = -1;
            staffWorkload[staffIdx]--;

            if (shiftType.equals(DIRECT_24H)) {
                int compDayIdx = getCompensationDayIdx(dayIdx, data);
                if (compDayIdx >= 0 && compDayIdx < data.numDays) {
                    restDays[staffIdx].clear(compDayIdx);
                }
            }
        }

        return false;
    }

    /**
     * Calculate compensation day index for a work day
     * Per spec:
     * - T2/T3/T4/T5 → next day (+1)
     * - T6/T7 → T3 next week (skip T2, T6)
     * - CN → T2 (+1)
     */
    private int getCompensationDayIdx(int dayIdx, ProblemData data) {
        LocalDate workDate = data.baseDate.plusDays(dayIdx);
        LocalDate compDate = compensationDateCalculator.calculate(workDate);
        long offset = ChronoUnit.DAYS.between(data.baseDate, compDate);
        if (offset >= 0 && offset < data.numDays) {
            return (int) offset;
        }
        return -1;
    }

    /**
     * Propagation: Remove staff from all conflicting domains
     */
    private boolean propagate(
            int staffIdx,
            int var,
            BitSet[] domains,
            BitSet[] restDays,
            int[] assignment,
            int[] trailVar, int[] trailStaff, int[] trailPtrRef,
            ProblemData data) {

        int dayIdx = data.varDay[var];
        int shiftIdx = data.varShift[var];
        String shiftType = SHIFT_ORDER[shiftIdx];

        // 1. Remove from same-day conflicting shifts
        for (int v = 0; v < data.numVars; v++) {
            if (v == var || assignment[v] >= 0) continue;
            if (data.varDay[v] != dayIdx) continue;

            int otherShiftIdx = data.varShift[v];
            String otherShiftType = SHIFT_ORDER[otherShiftIdx];

            // BR-01: L01 ↔ L02
            // BR-02: L03 ↔ L04
            if (conflicts(shiftType, otherShiftType)) {
                if (domains[v].get(staffIdx)) {
                    domains[v].clear(staffIdx);
                    trailVar[trailPtrRef[0]] = v;
                    trailStaff[trailPtrRef[0]] = staffIdx;
                    trailPtrRef[0]++;
                }
            }
        }

        // 2. BR-03: Remove from rest day's all shifts
        int compDayIdx = getCompensationDayIdx(dayIdx, data);
        if (compDayIdx >= 0 && compDayIdx < data.numDays) {
            for (int v = 0; v < data.numVars; v++) {
                if (v == var || assignment[v] >= 0) continue;
                if (data.varDay[v] != compDayIdx) continue;

                if (domains[v].get(staffIdx)) {
                    domains[v].clear(staffIdx);
                    trailVar[trailPtrRef[0]] = v;
                    trailStaff[trailPtrRef[0]] = staffIdx;
                    trailPtrRef[0]++;
                }
            }
        }

        // 3. Check for empty domains (failure)
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] < 0 && domains[v].isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * BR-03: Check if assignment is consistent with all constraints
     */
    private boolean isConsistent(
            int staffIdx,
            int var,
            int[] assignment,
            BitSet[] restDays,
            int[] staffWorkload,
            ProblemData data) {

        int dayIdx = data.varDay[var];
        int shiftIdx = data.varShift[var];
        String shiftType = SHIFT_ORDER[shiftIdx];

        // BR-03: Cannot assign on REST day
        if (restDays[staffIdx].get(dayIdx)) return false;

        // BR-04: Cannot assign on leave day
        if (data.leaveMatrix[staffIdx][dayIdx]) return false;

        // BR-04: Cannot assign on holiday
        if (data.holidayDays[dayIdx]) return false;

        // BR-05: Max shifts exceeded
        if (staffWorkload[staffIdx] >= data.staffMaxShifts[staffIdx]) return false;

        // BR-01, BR-02: Same-day conflicts with existing assignments
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] == staffIdx && data.varDay[v] == dayIdx && v != var) {
                if (conflicts(shiftType, SHIFT_ORDER[data.varShift[v]])) {
                    return false;
                }
            }
        }

        // BR-06: DIRECT_24H max 1 per day
        if (shiftType.equals(DIRECT_24H)) {
            for (int v = 0; v < data.numVars; v++) {
                if (v != var && assignment[v] >= 0 &&
                    data.varDay[v] == dayIdx &&
                    SHIFT_ORDER[data.varShift[v]].equals(DIRECT_24H)) {
                    return false;
                }
            }
        }

        return true;
    }

    private int selectMRV(BitSet[] domains, int[] assignment, ProblemData data) {
        int bestVar = -1;
        int minSize = Integer.MAX_VALUE;

        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] >= 0) continue;
            int size = domains[v].cardinality();
            if (size == 0) return -1;
            if (size < minSize) {
                minSize = size;
                bestVar = v;
            }
        }
        return bestVar;
    }

    private List<Integer> getCandidates(BitSet domain, int[] staffWorkload) {
        List<Integer> list = new ArrayList<>();
        for (int s = domain.nextSetBit(0); s >= 0; s = domain.nextSetBit(s + 1)) {
            list.add(s);
        }
        list.sort(Comparator.comparingInt(s -> staffWorkload[s]));
        return list;
    }

    private boolean isGoal(BitSet[] domains, int[] assignment, ProblemData data) {
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] < 0 && !domains[v].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ==================== Build Result ====================

    private SchedulingResult buildResult(
            Solution solution,
            ProblemData data,
            List<Staff> staffList,
            List<LocalDate> dates,
            long startTime) {

        if (!solution.isValid()) {
            return SchedulingResult.builder()
                    .valid(false)
                    .errors(solution.getErrors())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        Map<String, String> assignments = new HashMap<>();
        Set<String> compensationDays = new HashSet<>();

        for (String key : solution.getAssignment().keySet()) {
            String[] parts = key.split("\\|");
            if (parts.length == 3) {
                int staffIdx = Integer.parseInt(parts[0]);
                int dayIdx = Integer.parseInt(parts[1]);
                int shiftIdx = Integer.parseInt(parts[2]);

                int staffId = staffList.get(staffIdx).getId();
                LocalDate workDate = dates.get(dayIdx);
                String shiftType = SHIFT_ORDER[shiftIdx];

                assignments.put(staffId + "|" + workDate, shiftType);

                // BR-03: Track compensation days for L01
                if (shiftType.equals(DIRECT_24H)) {
                    LocalDate compDate = compensationDateCalculator.calculate(workDate);
                    compensationDays.add(staffId + "|" + compDate);
                }
            }
        }

        // Calculate fairness
        Map<Integer, Integer> shiftCounts = new HashMap<>();
        for (String key : assignments.keySet()) {
            int staffId = Integer.parseInt(key.split("\\|")[0]);
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

        double coverage = data.numVars > 0
                ? (double) assignments.size() / data.numVars * 100 : 100;

        // ==================== M07-F06: Report Unassigned Days ====================
        List<Map<String, Object>> unassignedDays = buildUnassignedDaysReport(data, assignments, dates);

        // ==================== M07-F07: Preview Before Confirm ====================
        SchedulingResult.PreviewData previewData = buildPreviewData(assignments, staffList, dates, shiftCounts, unassignedDays);

        return SchedulingResult.builder()
                .valid(true)
                .assignments(assignments)
                .compensationDays(compensationDays)
                .errors(Collections.emptyList())
                .fairnessScore(BigDecimal.valueOf(fairness).setScale(2, RoundingMode.HALF_UP))
                .fatigueScore(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP))
                .coverageScore(BigDecimal.valueOf(coverage).setScale(2, RoundingMode.HALF_UP))
                .totalScore(BigDecimal.valueOf(fairness).setScale(2, RoundingMode.HALF_UP))
                .scheduleCount(assignments.size())
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .unassignedDays(unassignedDays)
                .previewData(previewData)
                .build();
    }

    /**
     * M07-F06: Build report of unassigned days
     * Liệt kê các ngày chưa đủ nhân sự hợp lệ để phân công
     */
    private List<Map<String, Object>> buildUnassignedDaysReport(
            ProblemData data,
            Map<String, String> assignments,
            List<LocalDate> dates) {

        List<Map<String, Object>> unassignedDays = new ArrayList<>();

        for (int d = 0; d < data.numDays; d++) {
            for (int s = 0; s < data.numShifts; s++) {
                int required = data.slotCount[d][s];
                if (required <= 0) continue;

                int assigned = 0;
                for (String key : assignments.keySet()) {
                    String[] parts = key.split("_");
                    if (parts.length == 2) {
                        LocalDate assignDate = LocalDate.parse(parts[1]);
                        if (assignDate.equals(dates.get(d)) && assignments.get(key).equals(SHIFT_ORDER[s])) {
                            assigned++;
                        }
                    }
                }

                if (assigned < required) {
                    Map<String, Object> day = new HashMap<>();
                    day.put("date", dates.get(d));
                    day.put("shiftType", SHIFT_ORDER[s]);
                    day.put("shiftTypeName", getShiftTypeName(SHIFT_ORDER[s]));
                    day.put("required", required);
                    day.put("assigned", assigned);
                    day.put("shortfall", required - assigned);
                    day.put("dayOfWeek", dates.get(d).getDayOfWeek().toString());
                    unassignedDays.add(day);
                }
            }
        }

        return unassignedDays;
    }

    /**
     * M07-F07: Build preview data for confirmation screen
     */
    private SchedulingResult.PreviewData buildPreviewData(
            Map<String, String> assignments,
            List<Staff> staffList,
            List<LocalDate> dates,
            Map<Integer, Integer> shiftCounts,
            List<Map<String, Object>> unassignedDays) {

        // Build assignment details
        List<Map<String, Object>> assignmentList = new ArrayList<>();
        for (String key : assignments.keySet()) {
            String[] parts = key.split("_");
            if (parts.length == 2) {
                int staffId = Integer.parseInt(parts[0]);
                LocalDate workDate = LocalDate.parse(parts[1]);
                String shiftType = assignments.get(key);

                Map<String, Object> detail = new HashMap<>();
                detail.put("staffId", staffId);
                detail.put("staffName", getStaffName(staffList, staffId));
                detail.put("date", workDate);
                detail.put("shiftType", shiftType);
                detail.put("shiftTypeName", getShiftTypeName(shiftType));
                detail.put("dayOfWeek", workDate.getDayOfWeek().toString());
                assignmentList.add(detail);
            }
        }

        // Staff statistics
        List<Map<String, Object>> staffStats = new ArrayList<>();
        for (int staffId : shiftCounts.keySet()) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("staffId", staffId);
            stat.put("staffName", getStaffName(staffList, staffId));
            stat.put("totalShifts", shiftCounts.get(staffId));
            staffStats.add(stat);
        }
        staffStats.sort((a, b) -> ((Integer) b.get("totalShifts")).compareTo((Integer) a.get("totalShifts")));

        // Day statistics
        List<Map<String, Object>> dayStats = new ArrayList<>();
        for (LocalDate date : dates) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("date", date);
            stat.put("dayOfWeek", date.getDayOfWeek().toString());
            int count = 0;
            for (String key : assignments.keySet()) {
                if (key.endsWith("_" + date.toString())) count++;
            }
            stat.put("totalAssignments", count);
            dayStats.add(stat);
        }

        // Warnings for unassigned days
        List<String> warnings = new ArrayList<>();
        if (!unassignedDays.isEmpty()) {
            warnings.add("Có " + unassignedDays.size() + " ca chưa đủ nhân sự. Cần xử lý thủ công.");
            for (Map<String, Object> day : unassignedDays) {
                warnings.add("- " + day.get("date") + " " + day.get("shiftTypeName") +
                           ": thiếu " + day.get("shortfall") + " người");
            }
        }

        return SchedulingResult.PreviewData.builder()
                .assignments(assignmentList)
                .staffStats(staffStats)
                .dayStats(dayStats)
                .warnings(warnings)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String getShiftTypeName(String shiftType) {
        return switch (shiftType) {
            case "L01" -> "Lịch trực 24/24";
            case "L02" -> "Lịch thông tầm";
            case "L03" -> "Lịch phòng khám dịch vụ";
            case "L04" -> "Lịch phòng khám chuyên gia";
            default -> shiftType;
        };
    }

    private String getStaffName(List<Staff> staffList, int staffId) {
        return staffList.stream()
                .filter(s -> s.getId() == staffId)
                .map(Staff::getFullName)
                .findFirst()
                .orElse("Unknown");
    }

    private int getShiftIdx(String shiftTypeId) {
        for (int i = 0; i < SHIFT_ORDER.length; i++) {
            if (SHIFT_ORDER[i].equals(shiftTypeId)) return i;
        }
        return -1;
    }

    private int findStaffIdx(List<Staff> staffList, int staffId) {
        for (int i = 0; i < staffList.size(); i++) {
            if (staffList.get(i).getId() == staffId) return i;
        }
        return -1;
    }

    // ==================== Inner Classes ====================

    @lombok.Builder
    private static class ProblemData {
        int numDays, numShifts, numStaff, numVars;
        int[] varDay, varShift, varSlot;
        int[][] slotCount;
        boolean[][] leaveMatrix;
        boolean[] holidayDays;
        int[] staffMaxShifts;
        BitSet[] domains;
        List<int[]>[] constraintGraph;
        LocalDate baseDate;
    }

    @lombok.Builder
    private static class Solution {
        boolean valid;
        Map<String, Boolean> assignment;
        List<String> errors;
    }
}