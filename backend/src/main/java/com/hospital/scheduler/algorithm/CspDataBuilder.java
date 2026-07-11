package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hospital.scheduler.algorithm.CspConstants.DIRECT_24H;
import static com.hospital.scheduler.algorithm.CspConstants.SHIFT_ORDER;
import static com.hospital.scheduler.algorithm.CspConstants.conflicts;
import static com.hospital.scheduler.algorithm.CspConstants.getShiftIdx;

/**
 * Builds the {@link ProblemData} snapshot from raw inputs: requirements,
 * leaves, staff and the supplied date range.
 *
 * Also runs an initial AC-3 pass and symmetry breaking before handing the
 * data to the search engine.
 */
@Component
@RequiredArgsConstructor
class CspDataBuilder {

    private final CompensationDateCalculator compensationDateCalculator;
    private final CspAc3Engine ac3Engine;
    private final CspConstraints constraints;

    ProblemData build(
            List<Staff> staffList,
            List<LocalDate> dates,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests) {
        return build(staffList, dates, requirements, leaveRequests, null, null, null);
    }

    /**
     * Overload that lets the caller pass the configured L04 allowed specialties.
     * Passing {@code null}/empty means "all eligible specialties" (matches
     * {@link StaffShiftTypeEligibility#ALL_ELIGIBLE_SPECIALTIES}).
     *
     * <p>Note: for L01/L02/L03 the eligibility set is always
     * {@link StaffShiftTypeEligibility#CORE_ELIGIBLE_SPECIALTIES} (or the
     * per-type override supplied via {@code algorithmConfigService} — the CSP
     * solver only needs a baseline because the per-type override is applied
     * in the heuristic layer; see {@code AutoSchedulingService} for the
     * detailed gating). The two sources MUST agree on what "core" means so
     * the domain pruning and the scoring use the same definition.
     *
     * <p>Additional Gap-fix params:
     * <ul>
     *   <li>{@code minShiftsPerWeekByShift} — Gap 1: per-(shift) minimum
     *       shifts a staff should accumulate per week. Pass {@code null} to
     *       disable enforcement (the search engine treats it as all-zero).</li>
     *   <li>{@code activeShiftTypeIds} — Gap 3: when non-null, re-orders
     *       the shift-type table to match the set of {@code ShiftType}
     *       rows active in the DB. When {@code null}, the canonical
     *       {@link CspConstants#SHIFT_ORDER} is used.</li>
     * </ul>
     */
    ProblemData build(
            List<Staff> staffList,
            List<LocalDate> dates,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests,
            List<String> l04AllowedSpecialties) {
        return build(staffList, dates, requirements, leaveRequests,
                l04AllowedSpecialties, null, null);
    }

    ProblemData build(
            List<Staff> staffList,
            List<LocalDate> dates,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests,
            List<String> l04AllowedSpecialties,
            int[] minShiftsPerWeekByShift,
            Set<String> activeShiftTypeIds) {

        int numDays = dates.size();
        int numShifts = SHIFT_ORDER.length;
        int numStaff = staffList.size();

        int[][] slotCount = countSlots(dates, numDays, requirements);
        int varCount = totalVars(slotCount, numDays, numShifts);

        int[] varDay = new int[varCount];
        int[] varShift = new int[varCount];
        int[] varSlot = new int[varCount];
        int[] varSpecialty = new int[varCount];
        fillVarArrays(slotCount, varDay, varShift, varSlot, varSpecialty, numDays, numShifts, requirements, dates);

        boolean[][] leaveMatrix = buildLeaveMatrix(staffList, dates, numDays, numStaff, leaveRequests);
        boolean[] holidayDays = detectHolidayDays(slotCount, numDays, numShifts);
        int[] staffMaxShifts = maxShiftsPerStaff(staffList, numStaff);

        Set<String> l04Allowed = (l04AllowedSpecialties != null && !l04AllowedSpecialties.isEmpty())
                ? new HashSet<>(l04AllowedSpecialties)
                : StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES;

        BitSet[] domains = buildInitialDomains(varCount, varDay, varShift, varSpecialty, slotCount, numDays, numStaff,
                leaveMatrix, holidayDays, staffList, l04Allowed);
        List<Integer>[] constraintGraph = buildConstraintGraph(varDay, varShift, varCount, slotCount, dates);

        applySymmetryBreaking(domains, varCount);

        // Gap 1: week bucket for per-week minimum tracking
        int[] dayToWeek = constraints.buildWeekBuckets(numDays);

        // Gap 2: BR-04 adjacent-L01 pair table
        int[] adjacentPairs = constraints.buildAdjacentL01Pairs(varDay, varShift, varCount);

        // Gap 3: shift type ids (mirror SHIFT_ORDER by default, override when DB-driven)
        String[] shiftTypeIds = constraints.resolveShiftOrder(activeShiftTypeIds);

        // Performance: pre-index vars by day so propagate() and isConsistent() can do
        // O(varsOnDay) work instead of O(numVars) — important for 29-day periods where
        // numVars is in the hundreds of thousands and most slots are off-day.
        List<Integer>[] varsByDay = buildVarsByDay(varDay, varCount, numDays);

        ProblemData data = ProblemData.builder()
                .numDays(numDays)
                .numShifts(numShifts)
                .numStaff(numStaff)
                .numVars(varCount)
                .varDay(varDay)
                .varShift(varShift)
                .varSlot(varSlot)
                .varSpecialty(varSpecialty)
                .slotCount(slotCount)
                .leaveMatrix(leaveMatrix)
                .holidayDays(holidayDays)
                .staffMaxShifts(staffMaxShifts)
                .domains(domains)
                .constraintGraph(constraintGraph)
                .baseDate(dates.get(0))
                .minShiftsPerWeekByShift(
                        minShiftsPerWeekByShift != null ? minShiftsPerWeekByShift : new int[numShifts])
                .dayToWeek(dayToWeek)
                .adjacentL01Pairs(adjacentPairs)
                .adjacentL01PairCount(adjacentPairs.length / 2)
                .shiftTypeIds(shiftTypeIds)
                .varsByDay(varsByDay)
                .build();

        // Initial AC-3 prunes domains using BR-01, BR-02 and BR-03 arcs
        ac3Engine.runInitialAC3(domains, constraintGraph, varDay, data);
        return data;
    }

    // ==================== private helpers ====================

    private int findStaffIdx(List<Staff> staffList, int staffId) {
        for (int i = 0; i < staffList.size(); i++) {
            if (staffList.get(i).getId() == staffId) return i;
        }
        return -1;
    }

    // ==================== private helpers ====================

    private int[][] countSlots(List<LocalDate> dates, int numDays, List<ShiftRequirementInfo> requirements) {
        int numShifts = SHIFT_ORDER.length;
        int[][] slotCount = new int[numDays][numShifts];
        for (ShiftRequirementInfo req : requirements) {
            int dayIdx = (int) ChronoUnit.DAYS.between(dates.get(0), req.workDate());
            int shiftIdx = getShiftIdx(req.shiftTypeId());
            if (dayIdx >= 0 && dayIdx < numDays && shiftIdx >= 0) {
                slotCount[dayIdx][shiftIdx] += req.requiredCount();
            }
        }
        return slotCount;
    }

    private int totalVars(int[][] slotCount, int numDays, int numShifts) {
        int varCount = 0;
        for (int d = 0; d < numDays; d++) {
            for (int s = 0; s < numShifts; s++) {
                varCount += slotCount[d][s];
            }
        }
        return varCount;
    }

    private void fillVarArrays(int[][] slotCount, int[] varDay, int[] varShift, int[] varSlot, int[] varSpecialty,
                               int numDays, int numShifts, List<ShiftRequirementInfo> requirements, List<LocalDate> dates) {
        // Build a map of (dayIdx, shiftIdx) -> first requirement's specialty
        java.util.Map<String, Integer> specialtyMap = new java.util.HashMap<>();
        for (ShiftRequirementInfo req : requirements) {
            int dayIdx = (int) ChronoUnit.DAYS.between(dates.get(0), req.workDate());
            String key = dayIdx + "_" + getShiftIdx(req.shiftTypeId());
            if (req.specialtyId() != null) {
                specialtyMap.put(key, req.specialtyId());
            }
        }
        
        int vid = 0;
        for (int d = 0; d < numDays; d++) {
            for (int s = 0; s < numShifts; s++) {
                for (int slot = 0; slot < slotCount[d][s]; slot++) {
                    varDay[vid] = d;
                    varShift[vid] = s;
                    varSlot[vid] = slot;
                    // Store specialty for L04 shifts (null/0 for other types)
                    if (s == 3) { // L04 is index 3 in SHIFT_ORDER
                        Integer specId = specialtyMap.get(d + "_" + s);
                        varSpecialty[vid] = specId != null ? specId : 0;
                    } else {
                        varSpecialty[vid] = 0;
                    }
                    vid++;
                }
            }
        }
    }

    private boolean[][] buildLeaveMatrix(List<Staff> staffList, List<LocalDate> dates, int numDays, int numStaff,
                                         List<LeaveRequest> leaveRequests) {
        // Default = all-true (every staff is available on every day). Individual
        // APPROVED leave requests then set the matching cell to false.
        // The previous implementation left the matrix all-false when leaveRequests
        // was null, which made the domain pruner wipe every variable.
        boolean[][] leaveMatrix = new boolean[numStaff][numDays];
        for (int i = 0; i < numStaff; i++) {
            java.util.Arrays.fill(leaveMatrix[i], true);
        }
        if (leaveRequests == null || leaveRequests.isEmpty()) return leaveMatrix;

        // Cache epoch days outside inner loop for O(1) lookup
        long[] dayEpochs = new long[numDays];
        for (int d = 0; d < numDays; d++) {
            dayEpochs[d] = dates.get(d).toEpochDay();
        }

        for (LeaveRequest lr : leaveRequests) {
            if (lr.getStatus() != LeaveRequest.LeaveStatus.APPROVED || lr.getStaff() == null) continue;
            int staffIdx = findStaffIdx(staffList, lr.getStaff().getId());
            if (staffIdx < 0) continue;
            long startEpoch = lr.getStartDate().toEpochDay();
            long endEpoch = lr.getEndDate().toEpochDay();
            for (int d = 0; d < numDays; d++) {
                long dayEpoch = dayEpochs[d];
                if (dayEpoch >= startEpoch && dayEpoch <= endEpoch) {
                    leaveMatrix[staffIdx][d] = true;
                }
            }
        }
        return leaveMatrix;
    }

    private boolean[] detectHolidayDays(int[][] slotCount, int numDays, int numShifts) {
        boolean[] holidayDays = new boolean[numDays];
        for (int d = 0; d < numDays; d++) {
            // A day with no requirements on any shift is treated as a holiday
            holidayDays[d] = (slotCount[d][0] == 0 && slotCount[d][1] == 0 &&
                              slotCount[d][2] == 0 && slotCount[d][3] == 0);
        }
        return holidayDays;
    }

    private int[] maxShiftsPerStaff(List<Staff> staffList, int numStaff) {
        int[] staffMaxShifts = new int[numStaff];
        for (int i = 0; i < numStaff; i++) {
            staffMaxShifts[i] = staffList.get(i).getMaxShiftsPerMonth() != null
                    ? staffList.get(i).getMaxShiftsPerMonth() : 5;
        }
        return staffMaxShifts;
    }

    private BitSet[] buildInitialDomains(int varCount, int[] varDay, int[] varShift, int[] varSpecialty, int[][] slotCount,
                                         int numDays, int numStaff, boolean[][] leaveMatrix, boolean[] holidayDays,
                                         List<Staff> staffList, Set<String> l04AllowedSpecialties) {
        // Pre-compute eligibility flags per staff per shift type.
        //
        // IMPORTANT: keep this in sync with {@link StaffShiftTypeEligibility}:
        //   L01/L02/L03 → CORE_ELIGIBLE_SPECIALTIES (default = {Ngoại, Nội})
        //   L04         → l04AllowedSpecialties (default = ALL_ELIGIBLE_SPECIALTIES)
        //                  AND staff.specialty.id == varSpecialty[v] for L04 vars.
        //
        // Specialty.name values come from the {@code Specialty} entity (seeded
        // as "Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng" in DataSeeder), NOT
        // from any role-like string such as "Bác sĩ" / "Điều dưỡng". Hardcoded
        // role strings here would silently make EVERY staff ineligible.
        java.util.Map<Integer, boolean[]> eligibilityMatrix = new java.util.HashMap<>();
        for (int staffIdx = 0; staffIdx < numStaff; staffIdx++) {
            Staff st = staffList.get(staffIdx);
            String spName = st.getSpecialty() != null ? st.getSpecialty().getName() : null;
            boolean active = Boolean.TRUE.equals(st.getIsActive());
            boolean inCore = spName != null && StaffShiftTypeEligibility.CORE_ELIGIBLE_SPECIALTIES.contains(spName);
            boolean inL04  = spName != null && l04AllowedSpecialties.contains(spName);
            for (int s = 0; s < SHIFT_ORDER.length; s++) {
                String shiftTypeId = SHIFT_ORDER[s];
                boolean eligible;
                if ("L04".equals(shiftTypeId)) {
                    // L04: any active staff whose specialty is in the L04-allowed
                    // set is eligible (specialty filter still applied in AC-3).
                    eligible = active && inL04;
                } else {
                    // L01/L02/L03: only staff whose specialty is in CORE.
                    eligible = active && inCore;
                }
                eligibilityMatrix.computeIfAbsent(s, k -> new boolean[numStaff])[staffIdx] = eligible;
            }
        }

        BitSet[] domains = new BitSet[varCount];
        for (int v = 0; v < varCount; v++) {
            domains[v] = new BitSet(numStaff);
            int d = varDay[v];
            int s = varShift[v];
            if (slotCount[d][s] > 0 && !holidayDays[d]) {
                boolean[] shiftEligibility = eligibilityMatrix.get(s);
                // Per-variable specialty filter for L04 vars — the shift-level
                // eligibility matrix only knows whether staff.specialty.name is
                // in the L04-allowed set; without this extra check, an L04 Mắt
                // variable would admit staff from Ngoại / Nội / Sản / Nhi / Răng
                // too, and the search engine would explore thousands of wrong
                // assignments before backtracking out (often exceeding the
                // preview timeout).
                int requiredSpecialtyId = (varSpecialty != null && s == 3) ? varSpecialty[v] : 0;
                for (int staffIdx = 0; staffIdx < numStaff; staffIdx++) {
                    if (!leaveMatrix[staffIdx][d]) continue;
                    if (!shiftEligibility[staffIdx]) continue;
                    if (requiredSpecialtyId != 0) {
                        Specialty sp = staffList.get(staffIdx).getSpecialty();
                        if (sp == null || sp.getId() == null || sp.getId() != requiredSpecialtyId) continue;
                    }
                    domains[v].set(staffIdx);
                }
            }
        }
        return domains;
    }

    /**
     * Build a symmetric constraint graph where edges mean "the two variables
     * must not be assigned the same staff on the same day" (BR-01/02) or
     * "L01(day) blocks any shift on its compensation day" (BR-03).
     */
    private List<Integer>[] buildConstraintGraph(int[] varDay, int[] varShift, int varCount,
                                                 int[][] slotCount, List<LocalDate> dates) {
        @SuppressWarnings("unchecked")
        List<Integer>[] graph = new ArrayList[varCount];
        for (int i = 0; i < varCount; i++) {
            graph[i] = new ArrayList<>();
        }

        // 1. Same-day conflicts: L01↔L02, L03↔L04
        for (int v1 = 0; v1 < varCount; v1++) {
            int d1 = varDay[v1];
            String t1 = SHIFT_ORDER[varShift[v1]];

            for (int v2 = v1 + 1; v2 < varCount; v2++) {
                if (varDay[v2] != d1) continue;
                String t2 = SHIFT_ORDER[varShift[v2]];
                if (conflicts(t1, t2)) {
                    graph[v1].add(v2);
                    graph[v2].add(v1);
                }
            }
        }

        // 2. BR-03: L01(day) ↔ all shifts on its compensation day
        for (int v = 0; v < varCount; v++) {
            if (!SHIFT_ORDER[varShift[v]].equals(DIRECT_24H)) continue;

            int dayIdx = varDay[v];
            LocalDate workDate = dates.get(dayIdx);
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            long offset = ChronoUnit.DAYS.between(dates.get(0), compDate);

            if (offset < 0 || offset >= dates.size()) continue;
            int compDayIdx = (int) offset;

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
     * Build a {@code [numDays]} array of {@code List<Integer>} of variable indices
     * that fall on each day. Used by propagate() and isConsistent() to skip
     * irrelevant vars instead of iterating every var on the hot path.
     */
    @SuppressWarnings("unchecked")
    private List<Integer>[] buildVarsByDay(int[] varDay, int varCount, int numDays) {
        List<Integer>[] out = new List[numDays];
        for (int d = 0; d < numDays; d++) out[d] = new ArrayList<>();
        for (int v = 0; v < varCount; v++) {
            int d = varDay[v];
            if (d >= 0 && d < numDays) out[d].add(v);
        }
        return out;
    }

    /**
     * Symmetry breaking: fix the first variable (the one with the smallest
     * domain) to its first eligible staff, so the search tree doesn't
     * enumerate permutations of equivalent solutions.
     */
    private void applySymmetryBreaking(BitSet[] domains, int varCount) {
        int firstVar = -1;
        int minDomainSize = Integer.MAX_VALUE;
        for (int v = 0; v < varCount; v++) {
            if (!domains[v].isEmpty() && domains[v].cardinality() < minDomainSize) {
                minDomainSize = domains[v].cardinality();
                firstVar = v;
            }
        }
        if (firstVar < 0 || domains[firstVar].isEmpty()) return;

        int firstStaff = domains[firstVar].nextSetBit(0);
        for (int s = domains[firstVar].nextSetBit(0); s >= 0; s = domains[firstVar].nextSetBit(s + 1)) {
            if (s != firstStaff) domains[firstVar].clear(s);
        }
    }
}
