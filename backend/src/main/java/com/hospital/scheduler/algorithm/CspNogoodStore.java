package com.hospital.scheduler.algorithm;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Conflict-clause learning store.
 *
 * A "nogood" is a set of (varIdx, staffIdx) pairs that the search has already
 * proven cannot be simultaneously satisfied. Before exploring a new branch
 * the search asks {@link #isNogood(int[], int)} whether the current partial
 * assignment subsumes any stored nogood; if so it skips the branch.
 */
@Component
class CspNogoodStore {

    private static final int MAX_NOGOODS = 1000;

    private final Map<String, Set<int[]>> nogoods = new HashMap<>();
    private int nogoodsLearned = 0;

    /**
     * Learn a new conflict clause. The store is bounded by {@link #MAX_NOGOODS};
     * the oldest entry is evicted when full.
     */
    void addNogood(Set<int[]> conflict, String reason) {
        if (nogoods.size() >= MAX_NOGOODS) {
            String oldest = nogoods.keySet().iterator().next();
            nogoods.remove(oldest);
        }
        String key = conflict.stream()
                .sorted(Comparator.comparingInt(a -> a[0]))
                .map(a -> a[0] + "|" + a[1])
                .collect(Collectors.joining(","));
        nogoods.put(key, conflict);
        nogoodsLearned++;
    }

    /**
     * Check whether the current partial assignment subsumes any stored
     * nogood. A subsumption means: for every (var, staff) in the nogood,
     * either var is unassigned or var is assigned to staff. In that case
     * we know the branch will fail, so the search can prune it.
     */
    boolean isNogood(int[] currentAssignment, int numVars) {
        for (Map.Entry<String, Set<int[]>> entry : nogoods.entrySet()) {
            String[] parts = entry.getKey().split(",");
            boolean subsumes = true;
            for (String part : parts) {
                String[] varStaff = part.split("\\|");
                int var = Integer.parseInt(varStaff[0]);
                int staff = Integer.parseInt(varStaff[1]);
                if (var < numVars && currentAssignment[var] >= 0 && currentAssignment[var] != staff) {
                    subsumes = false;
                    break;
                }
            }
            if (subsumes) return true;
        }
        return false;
    }

    int getNogoodCount() {
        return nogoodsLearned;
    }

    /**
     * Build a nogood from the current failure: the failed variable plus
     * every assignment recorded on the propagation trail up to trailPtr.
     * Then simplify it (currently: keep all, reserved for future pruning).
     */
    Set<int[]> extractNogood(int[] assignment, int var, int[] trailVar, int trailPtr) {
        Set<int[]> nogood = new HashSet<>();
        if (var >= 0 && var < assignment.length && assignment[var] >= 0) {
            nogood.add(new int[]{var, assignment[var]});
        }
        for (int i = 0; i < trailPtr; i++) {
            int trailedVar = trailVar[i];
            if (trailedVar >= 0 && trailedVar < assignment.length && assignment[trailedVar] >= 0) {
                nogood.add(new int[]{trailedVar, assignment[trailedVar]});
            }
        }
        return simplifyNogood(nogood, var);
    }

    /**
     * Simplify a nogood by removing assignments that don't contribute to
     * the failure. The current implementation is the identity transform
     * (kept as a hook for implication-graph-based reduction later).
     */
    private Set<int[]> simplifyNogood(Set<int[]> nogood, int failedVar) {
        return new HashSet<>(nogood);
    }

    /**
     * Convenience for the search loop: copy the assignment, slot in the
     * candidate value, and ask the store if the resulting partial is
     * known to fail.
     */
    boolean violatesNogood(int[] assignment, int var, int staffIdx, int numVars) {
        int[] temp = Arrays.copyOf(assignment, numVars);
        temp[var] = staffIdx;
        return isNogood(temp, numVars);
    }
}
