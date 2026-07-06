package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

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

    ProblemData build(
            List<Staff> staffList,
            List<LocalDate> dates,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests) {

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

        BitSet[] domains = buildInitialDomains(varCount, varDay, varShift, slotCount, numDays, numStaff, leaveMatrix, holidayDays);
        List<Integer>[] constraintGraph = buildConstraintGraph(varDay, varShift, varCount, slotCount, dates);

        applySymmetryBreaking(domains, varCount);

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
                .build();

        // Initial AC-3 prunes domains using BR-01, BR-02 and BR-03 arcs
        ac3Engine.runInitialAC3(domains, constraintGraph, varDay, data);
        return data;
    }

    int findStaffIdx(List<Staff> staffList, int staffId) {
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
        boolean[][] leaveMatrix = new boolean[numStaff][numDays];
        if (leaveRequests == null) return leaveMatrix;

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

    private BitSet[] buildInitialDomains(int varCount, int[] varDay, int[] varShift, int[][] slotCount,
                                         int numDays, int numStaff, boolean[][] leaveMatrix, boolean[] holidayDays) {
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
