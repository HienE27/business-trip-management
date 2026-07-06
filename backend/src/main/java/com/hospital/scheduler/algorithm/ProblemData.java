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
}
