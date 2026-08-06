package com.hospital.scheduler.scheduling.diversifier;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps the top-{@code poolSize} solutions seen so far, ordered by score.
 *
 * <p>The pool only stores <em>copies</em> of solutions to prevent external
 * mutation from invalidating the stored elite.
 *
 * <p>Scoring: primary key is coverage (higher = better), secondary is
 * hard-violations (lower = better), tertiary is mix-deviation (lower = better).
 */
public class EliteSolutionPool {

    private final int poolSize;
    /** Candidates in ascending score order (worst first, best last). */
    private final ArrayList<SolutionEntry> candidates = new ArrayList<>();

    public EliteSolutionPool(int poolSize) {
        if (poolSize < 1) throw new IllegalArgumentException("poolSize must be >= 1");
        this.poolSize = poolSize;
    }

    /**
     * Attempt to add a solution to the pool.
     *
     * @param solution non-null working solution (will be deep-copied)
     * @param coverage coverage score of the solution
     * @param hardViolations hard-constraint violations
     * @param mixDeviation mix deviation of the solution
     * @return true if the solution was accepted into the pool
     */
    public boolean offer(WorkingSolution solution,
                         double coverage,
                         int hardViolations,
                         double mixDeviation) {
        if (solution == null) return false;

        SolutionEntry entry = new SolutionEntry(copyOf(solution), coverage, hardViolations, mixDeviation);

        // If pool is not full, always accept
        if (candidates.size() < poolSize) {
            candidates.add(entry);
            trimToPool();
            return true;
        }

        // Check if this solution beats the worst entry in the pool
        SolutionEntry worst = candidates.get(0);
        if (betterThan(entry, worst) > 0) {
            candidates.add(entry);
            trimToPool();
            return true;
        }
        return false;
    }

    /**
     * Returns the elite solutions in score order (best first).
     */
    public List<WorkingSolution> getElite() {
        List<SolutionEntry> sorted = new ArrayList<>(candidates);
        sorted.sort(this::reverseBetterThan);
        List<WorkingSolution> result = new ArrayList<>();
        for (SolutionEntry e : sorted) {
            result.add(copyOf(e.solution));
        }
        return result;
    }

    /** Returns the single best elite solution, or null if pool is empty. */
    public WorkingSolution getBest() {
        if (candidates.isEmpty()) return null;
        // candidates is sorted ascending (worst first), so last is best
        return copyOf(candidates.get(candidates.size() - 1).solution);
    }

    /** Number of solutions currently in the pool. */
    public int size() {
        return candidates.size();
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    /** Clears all entries. */
    public void clear() {
        candidates.clear();
    }

    private void trimToPool() {
        while (candidates.size() > poolSize) {
            // Remove worst (first element, since sorted ascending)
            candidates.remove(0);
        }
        // Re-sort after trim
        candidates.sort(this::betterThan);
    }

    /** True if a is better than b. */
    private int betterThan(SolutionEntry a, SolutionEntry b) {
        // Higher coverage is better
        if (a.coverage != b.coverage) return Double.compare(a.coverage, b.coverage);
        // Fewer hard violations is better
        if (a.hardViolations != b.hardViolations) return Integer.compare(a.hardViolations, b.hardViolations);
        // Lower mix deviation is better
        return Double.compare(a.mixDeviation, b.mixDeviation);
    }

    private int reverseBetterThan(SolutionEntry a, SolutionEntry b) {
        return -betterThan(a, b);
    }

    /** Deep-copy a solution. */
    private WorkingSolution copyOf(WorkingSolution s) {
        WorkingSolution copy = WorkingSolution.fromProblem(s.getConfig(), s.getDescriptor());
        for (var a : s.getAssignments()) {
            if (a.staffId > 0) {
                copy.assign(a.slotId, a.staffId);
            }
        }
        return copy;
    }

    private static class SolutionEntry {
        final WorkingSolution solution;
        final double coverage;
        final int hardViolations;
        final double mixDeviation;

        SolutionEntry(WorkingSolution solution, double coverage, int hardViolations, double mixDeviation) {
            this.solution = solution;
            this.coverage = coverage;
            this.hardViolations = hardViolations;
            this.mixDeviation = mixDeviation;
        }
    }
}