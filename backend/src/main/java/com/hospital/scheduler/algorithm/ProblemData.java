package com.hospital.scheduler.algorithm;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.BitSet;
import java.util.List;

/**
 * Immutable snapshot of the CSP input built by {@link CspDataBuilder}.
 * Shared between the AC-3 engine and the search engine.
 */
@Builder
@Getter
public class ProblemData {
    int numDays;
    int numShifts;
    int numStaff;
    int numVars;

    int[] varDay;
    int[] varShift;
    int[] varSlot;
    // Specialty ID for each variable (null/0 for non-L04 shifts)
    int[] varSpecialty;

    int[][] slotCount;
    boolean[][] leaveMatrix;
    boolean[] holidayDays;
    int[] staffMaxShifts;

    BitSet[] domains;
    List<Integer>[] constraintGraph;

    LocalDate baseDate;

    // ───── Gap 1: min-staff enforcement ─────
    /** Min shifts per staff per shift-type per week (size = numShifts, 0 = no minimum). */
    int[] minShiftsPerWeekByShift;
    /** Day index → ISO week-of-period (0-based), for week-bucketing. */
    int[] dayToWeek;

    // ───── Gap 2: BR-04 adjacent-L01 guard ─────
    /** Adjacent-day var pairs (v1, v2) where both are L01 on consecutive days (size = 2*K). */
    int[] adjacentL01Pairs;
    int adjacentL01PairCount;

    // ───── Gap 3: dynamic shift types ─────
    /** Shift type id per index (parallel to SHIFT_ORDER-like lookup, but data-driven). */
    String[] shiftTypeIds;

    // ───── Performance: per-day variable index ─────
    /**
     * For each day, list of variable indices that fall on that day. Built once by
     * CspDataBuilder so propagate() and isConsistent() can avoid O(numVars) scans
     * for BR-03 / BR-06 same-day checks.
     */
    List<Integer>[] varsByDay;

    // ───── BR-03: flexible compensation day mapping ─────
    /**
     * Pre-computed compensation day index for each day.
     * {@code compDayIdx[dayIdx]} = the compensation day index for an L01 on that day,
     * or -1 if the compensation day falls outside the period.
     * For Fri/Sat duty, the best option (least-loaded) among Tue/Wed/Thu is selected
     * at problem-build time based on slotCount.
     */
    int[] compDayIdx;
}
