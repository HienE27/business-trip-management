package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static com.hospital.scheduler.algorithm.CspConstants.DIRECT_24H;
import static com.hospital.scheduler.algorithm.CspConstants.SHIFT_ORDER;
import static com.hospital.scheduler.algorithm.CspConstants.conflicts;

/**
 * AC-3 arc-consistency engine and supporting constraint checks.
 *
 * The calculator is injected because the pairwise BR-03 check needs to
 * translate a "work day index" into its actual compensation day index.
 */
@Component
@RequiredArgsConstructor
class CspAc3Engine {

    private final CompensationDateCalculator compensationDateCalculator;

    /**
     * Run a full AC-3 pass over the constraint graph to prune any domain
     * value that has no supporting value in a neighbor's domain.
     *
     * Returns the same array passed in for fluent use. If a domain is wiped
     * out, the array is returned as-is — the caller decides how to react.
     */
    BitSet[] runInitialAC3(
            BitSet[] domains,
            List<Integer>[] constraintGraph,
            int[] varDay,
            ProblemData data) {

        Queue<int[]> queue = new LinkedList<>();
        for (int v = 0; v < data.numVars; v++) {
            for (int u : constraintGraph[v]) {
                if (u > v) queue.add(new int[]{v, u});
            }
        }

        while (!queue.isEmpty()) {
            int[] arc = queue.poll();
            int Xi = arc[0];
            int Xj = arc[1];

            if (revise(domains, Xi, Xj, varDay, data)) {
                if (domains[Xi].isEmpty()) return domains;
                for (int Xk : constraintGraph[Xi]) {
                    if (Xk != Xj) queue.add(new int[]{Xi, Xk});
                }
            }
        }
        return domains;
    }

    /**
     * For each value a ∈ D(Xi), check if there exists b ∈ D(Xj) that
     * satisfies the pairwise constraint. Drop a if not.
     */
    boolean revise(BitSet[] domains, int Xi, int Xj, int[] varDay, ProblemData data) {
        boolean revised = false;
        int di = varDay[Xi];
        int dj = varDay[Xj];

        for (int a = domains[Xi].nextSetBit(0); a >= 0; a = domains[Xi].nextSetBit(a + 1)) {
            boolean hasSupport = false;
            for (int b = domains[Xj].nextSetBit(0); b >= 0 && !hasSupport; b = domains[Xj].nextSetBit(b + 1)) {
                if (isValidPair(a, Xi, b, Xj, di, dj, data)) hasSupport = true;
            }
            if (!hasSupport) {
                domains[Xi].clear(a);
                revised = true;
            }
        }
        return revised;
    }

    /**
     * Check whether assigning staffA to varA and staffB to varB is allowed
     * under the pairwise business rules. Two constraints matter:
     * - BR-01/02: same staff on same day with conflicting shift types
     * - BR-03: same staff on an L01 day vs. its compensation day
     */
    boolean isValidPair(
            int staffA, int varA,
            int staffB, int varB,
            int dayA, int dayB,
            ProblemData data) {

        if (staffA == staffB && dayA == dayB) {
            String shiftA = SHIFT_ORDER[data.varShift[varA]];
            String shiftB = SHIFT_ORDER[data.varShift[varB]];
            if (conflicts(shiftA, shiftB)) return false;
        }

        String shiftA = SHIFT_ORDER[data.varShift[varA]];
        String shiftB = SHIFT_ORDER[data.varShift[varB]];

        if (shiftA.equals(DIRECT_24H) && staffA == staffB
                && compensationOverlap(data, dayA, dayB)) return false;
        if (shiftB.equals(DIRECT_24H) && staffA == staffB
                && compensationOverlap(data, dayB, dayA)) return false;
        return true;
    }

    /**
     * True if {@code otherDay} is the BR-03 compensation day of
     * {@code l01Day} AND both days fall inside the active period.
     */
    private boolean compensationOverlap(ProblemData data, int l01Day, int otherDay) {
        LocalDate workDate = data.baseDate.plusDays(l01Day);
        LocalDate compDate = compensationDateCalculator.calculate(workDate);
        long offset = ChronoUnit.DAYS.between(data.baseDate, compDate);
        return offset >= 0 && offset < data.numDays && otherDay == offset;
    }
}
