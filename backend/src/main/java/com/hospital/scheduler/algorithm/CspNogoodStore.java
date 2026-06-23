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
 *
 * Optimization: varIndex maps varIdx → Set of nogood keys containing that variable.
 * This lets isNogood() retrieve only relevant nogoods in O(1) per variable
 * instead of scanning all nogoods on every call.
 */
@Component
class CspNogoodStore {

    private static final int MAX_NOGOODS = 1000;

    /** varIdx → Set of nogood keys (canonical strings) that contain this variable */
    private final Map<Integer, Set<String>> varIndex = new HashMap<>();
    /** Canonical nogood key → Set of (varIdx, staffIdx) pairs in that clause */
    private final Map<String, Set<int[]>> nogoods = new HashMap<>();
    private int nogoodsLearned = 0;

    /**
     * Learn a new conflict clause. The store is bounded by {@link #MAX_NOGOODS};
     * the oldest entry is evicted when full.
     *
     * Also updates the varIndex for O(1) retrieval of relevant nogoods.
     */
    void addNogood(Set<int[]> conflict, String reason) {
        if (nogoods.size() >= MAX_NOGOODS) {
            String oldest = nogoods.keySet().iterator().next();
            Set<int[]> evicted = nogoods.remove(oldest);
            if (evicted != null) {
                for (int[] pair : evicted) {
                    Set<String> varSet = varIndex.get(pair[0]);
                    if (varSet != null) varSet.remove(pair[0] + "|" + pair[1]);
                }
            }
        }
        String key = conflict.stream()
                .sorted(Comparator.comparingInt(a -> a[0]))
                .map(a -> a[0] + "|" + a[1])
                .collect(Collectors.joining(","));
        nogoods.put(key, conflict);

        for (int[] pair : conflict) {
            varIndex.computeIfAbsent(pair[0], k -> new HashSet<>()).add(key);
        }
        nogoodsLearned++;
    }

    /**
     * Fast nogood subsumption check: retrieve only nogoods containing at least
     * one assigned variable via varIndex, then verify full subsumption.
     *
     * A subsumption means: for every (var, staff) in the nogood,
     * either var is unassigned or var is assigned to staff. In that case
     * we know the branch will fail, so the search can prune it.
     */
    boolean isNogood(int[] currentAssignment, int numVars) {
        Set<String> checked = new HashSet<>();

        for (int v = 0; v < numVars; v++) {
            if (currentAssignment[v] < 0) continue;
            Set<String> relevantKeys = varIndex.get(v);
            if (relevantKeys == null) continue;

            for (String key : relevantKeys) {
                if (!checked.add(key)) continue;

                Set<int[]> clause = nogoods.get(key);
                if (clause == null) continue;

                boolean subsumes = true;
                for (int[] pair : clause) {
                    int var = pair[0];
                    int staff = pair[1];
                    if (var < numVars && currentAssignment[var] >= 0 && currentAssignment[var] != staff) {
                        subsumes = false;
                        break;
                    }
                }
                if (subsumes) return true;
            }
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
