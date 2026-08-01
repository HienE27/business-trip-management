package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

import static com.hospital.scheduler.algorithm.CspConstants.DIRECT_24H;
import static com.hospital.scheduler.algorithm.CspConstants.SHIFT_ORDER;
import static com.hospital.scheduler.algorithm.CspConstants.conflicts;
import static com.hospital.scheduler.algorithm.CspConstants.getShiftIdx;

/**
 * Constraint helpers for the CSP solver: builds adjacency data, week buckets,
 * and additional per-type work-week counts. Extracted from {@link CspDataBuilder}
 * so that the data-construction pipeline stays focused on snapshot assembly
 * while constraint metadata (BR-04 adjacent-L01 pairs, week buckets, dynamic
 * shift types) lives here.
 *
 * <p><b>Gap fixes living in this class</b>
 * <ul>
 *   <li><b>Gap 1</b> — min-staff per-week tracking: per-(staff, shift-type, week) counts
 *       via {@link MinWeekTracker}, queried by the search engine when picking
 *       candidates to prioritise staff who haven't yet met their weekly minimum.</li>
 *   <li><b>Gap 2</b> — BR-04 adjacent-L01 pair table: a flat int[] of length
 *       2*K listing variable pairs (v1, v2) whose L01 assignments would sit on
 *       consecutive days. Consumed by {@code isConsistent()} to reject the
 *       second L01 in a pair once the first is already assigned.</li>
 *   <li><b>Gap 3</b> — dynamic shift types: {@link #resolveShiftOrder(Set)} lets
 *       the caller (e.g. {@code AutoSchedulingService}) override the hardcoded
 *       {@code SHIFT_ORDER} from active {@code ShiftType} rows so that adding a
 *       new shift type requires no algorithm-side change.</li>
 * </ul>
 */
@Component
public class CspConstraints {

    private final CompensationDateCalculator compensationDateCalculator;

    public CspConstraints(CompensationDateCalculator compensationDateCalculator) {
        this.compensationDateCalculator = compensationDateCalculator;
    }

    // ==================== Gap 3: dynamic shift types ====================

    /**
     * Build the active shift-order array from a set of {@code ShiftType} ids
     * available in the DB. Returns the canonical {@link CspConstants#SHIFT_ORDER}
     * ordering intersected with the supplied set, plus any extra ids appended
     * in input order. The result is guaranteed to be a permutation of the
     * union — never null, never empty (falls back to {@code SHIFT_ORDER}).
     */
    public String[] resolveShiftOrder(Set<String> activeShiftTypeIds) {
        if (activeShiftTypeIds == null || activeShiftTypeIds.isEmpty()) {
            return SHIFT_ORDER.clone();
        }
        List<String> ordered = new ArrayList<>();
        for (String canonical : SHIFT_ORDER) {
            if (activeShiftTypeIds.contains(canonical)) ordered.add(canonical);
        }
        for (String id : activeShiftTypeIds) {
            String upper = id == null ? null : id.toUpperCase();
            if (upper == null) continue;
            if (ordered.contains(upper)) continue;
            ordered.add(upper);
        }
        return ordered.isEmpty() ? SHIFT_ORDER.clone() : ordered.toArray(new String[0]);
    }

    // ==================== Gap 2: BR-04 adjacent-L01 pairs ====================

    /**
     * For every pair of variables (v1, v2) that both reference L01 and whose
     * dates are exactly 1 day apart, append (v1, v2) to the returned buffer.
     * The result is a flat int[] of length 2*K where K is the number of such
     * pairs. Pairs are stored once (v1 < v2) to keep the table small.
     */
    public int[] buildAdjacentL01Pairs(int[] varDay, int[] varShift, int varCount) {
        // Bucket L01 variables by day to make the O(D + L01) scan cheap.
        java.util.Map<Integer, List<Integer>> l01ByDay = new java.util.HashMap<>();
        for (int v = 0; v < varCount; v++) {
            if (!SHIFT_ORDER[varShift[v]].equals(DIRECT_24H)) continue;
            l01ByDay.computeIfAbsent(varDay[v], k -> new ArrayList<>()).add(v);
        }
        List<Integer> flat = new ArrayList<>();
        for (var entry : l01ByDay.entrySet()) {
            int d = entry.getKey();
            List<Integer> here = entry.getValue();
            List<Integer> prev = l01ByDay.get(d - 1);
            if (prev == null) continue;
            for (int v1 : prev) {
                for (int v2 : here) {
                    int a = Math.min(v1, v2);
                    int b = Math.max(v1, v2);
                    flat.add(a);
                    flat.add(b);
                }
            }
        }
        int[] out = new int[flat.size()];
        for (int i = 0; i < flat.size(); i++) out[i] = flat.get(i);
        return out;
    }

    // ==================== Gap 1: week bucket ====================

    /**
     * Map each day index to a 0-based week bucket of the active period
     * (Mon-Sun). Day 0 starts week 0; the period always starts on a Monday
     * for periodised hospital rosters, but we don't enforce that here —
     * any 7-day window is acceptable for fairness purposes.
     */
    public int[] buildWeekBuckets(int numDays) {
        int[] w = new int[numDays];
        for (int d = 0; d < numDays; d++) w[d] = d / 7;
        return w;
    }

    // ==================== Eligibility matrix helper (shared) ====================

    /**
     * Pre-compute a per-(shift, staff) eligibility matrix aligned with the
     * legacy {@link CspConstants#SHIFT_ORDER}. Used by both
     * {@link CspDataBuilder#buildInitialDomains} and any dynamic-shift
     * re-routing.
     */
    public boolean[][] buildEligibilityMatrix(
            int numStaff, int numShifts, List<Staff> staffList) {
        boolean[][] eligibilityMatrix = new boolean[numShifts][numStaff];
        for (int s = 0; s < numShifts; s++) {
            String shiftType = SHIFT_ORDER[s];
            for (int staffIdx = 0; staffIdx < numStaff; staffIdx++) {
                Staff st = staffList.get(staffIdx);
                if (st == null || !Boolean.TRUE.equals(st.getIsActive())) continue;
                Integer requiredSpec = null; // populated for L04 by caller if needed
                eligibilityMatrix[s][staffIdx] = StaffShiftTypeEligibility.isEligible(
                        st, shiftType, requiredSpec);
            }
        }
        return eligibilityMatrix;
    }

    // ==================== MinWeekTracker: Gap 1 runtime state ====================

    /**
     * Tracks per-(staff, shift-type, week) assignment counts at search time.
     * Owned by the search engine and queried by candidate sort to prioritise
     * under-min staff.
     *
     * <p>The 3D count is sized {@code numStaff × numShifts × numWeeks} where
     * {@code numWeeks} is supplied by the caller. Counts are incremented on
     * assignment and decremented on rollback.
     */
    public static final class MinWeekTracker {
        private final int[][][] counts; // [staff][shift][week]
        private final int[] minPerWeekByShift; // [shiftIdx] → required minimum
        private final int numWeeks;

        public MinWeekTracker(int numStaff, int numShifts, int numWeeks, int[] minPerWeekByShift) {
            this.numWeeks = numWeeks;
            this.minPerWeekByShift = minPerWeekByShift == null ? new int[numShifts] : minPerWeekByShift;
            this.counts = new int[numStaff][numShifts][];
            for (int s = 0; s < numStaff; s++) {
                for (int t = 0; t < numShifts; t++) {
                    this.counts[s][t] = new int[numWeeks];
                }
            }
        }

        public void increment(int staffIdx, int shiftIdx, int week) {
            if (staffIdx < 0 || shiftIdx < 0 || week < 0 || week >= numWeeks) return;
            counts[staffIdx][shiftIdx][week]++;
        }

        public void decrement(int staffIdx, int shiftIdx, int week) {
            if (staffIdx < 0 || shiftIdx < 0 || week < 0 || week >= numWeeks) return;
            counts[staffIdx][shiftIdx][week]--;
        }

        /**
         * 0 if staff has met the minimum for this (shift, week), 1 if one
         * short, 2 if two short, etc. Returns 0 when the shift has no minimum.
         */
        public int underMinScore(int staffIdx, int shiftIdx, int week) {
            if (shiftIdx < 0 || shiftIdx >= minPerWeekByShift.length) return 0;
            int min = minPerWeekByShift[shiftIdx];
            if (min <= 0) return 0;
            if (week < 0 || week >= numWeeks) return 0;
            int have = counts[staffIdx][shiftIdx][week];
            return Math.max(0, min - have);
        }
    }
}
