package com.hospital.scheduler.algorithm;

import lombok.Builder;

import java.time.LocalDate;
import java.util.BitSet;
import java.util.List;

/**
 * Immutable snapshot of the CSP input built by {@link CspDataBuilder}.
 * Shared between the AC-3 engine and the search engine.
 */
@Builder
class ProblemData {
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
}
