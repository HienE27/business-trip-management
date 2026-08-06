package com.hospital.scheduler.scheduling.diversifier;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.move.Move.MoveType;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Path Relinking between two elite solutions.
 *
 * <p>Given a {@code source} solution (typically the current working solution) and
 * a {@code target} solution (typically the best elite solution), generates a
 * trajectory of intermediate solutions by applying moves that bring the source
 * closer to the target. Returns the best intermediate found along the trajectory.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Identify all slots where source ≠ target (the "gap").</li>
 *   <li>Sort gaps by contribution to score difference.</li>
 *   <li>Apply the top-{@code maxSteps} moves and track the best intermediate.</li>
 *   <li>Return the best intermediate found, or source if no improvement.</li>
 * </ol>
 *
 * <p>For scheduling, each gap corresponds to a single-staff reassignment (the
 * slot is filled by one staff in source and another in target). A Move that
 * changes one staff assignment on one slot is modelled as a SwapMove with one
 * side null (unassign + assign), but here we apply them as direct mutations
 * on a copy of the source so we don't need to implement a new Move type.
 */
public class PathRelinking {

    private final int maxSteps;
    private final Random random;

    public PathRelinking(int maxSteps) {
        this(maxSteps, new Random());
    }

    public PathRelinking(int maxSteps, Random random) {
        if (maxSteps < 0) throw new IllegalArgumentException("maxSteps must be >= 0");
        this.maxSteps = maxSteps;
        this.random = random;
    }

    /**
     * Generate a trajectory from {@code source} toward {@code target} and
     * return the best intermediate solution found.
     *
     * @param source the starting solution (will not be mutated)
     * @param target the target solution to move toward (will not be mutated)
     * @param scoreExtractor used to score intermediates for comparison
     * @return the best intermediate solution found along the trajectory
     */
    public WorkingSolution relink(WorkingSolution source,
                                WorkingSolution target,
                                SolutionScoreExtractor scoreExtractor) {
        if (source == null) return target;
        if (target == null) return source;
        if (source == target) return source;

        WorkingSolution current = copyOf(source);
        WorkingSolution best = copyOf(source);
        double bestScore = scoreExtractor.score(current);

        // Build the gap list: (slotId, sourceStaff, targetStaff) where they differ
        List<GapEntry> gaps = buildGaps(source, target);
        if (gaps.isEmpty()) return source;

        int steps = Math.min(maxSteps, gaps.size());
        for (int i = 0; i < steps; i++) {
            GapEntry gap = gaps.get(i);
            // Apply the reassignment toward target
            if (gap.targetStaff > 0) {
                current.unassign(gap.slotId);
                current.assign(gap.slotId, gap.targetStaff);
            }

            double score = scoreExtractor.score(current);
            if (score > bestScore) {
                best = copyOf(current);
                bestScore = score;
            }
        }

        return best;
    }

    /**
     * Build the list of gaps between source and target, sorted by the
     * contribution to the score difference (highest first).
     */
    private List<GapEntry> buildGaps(WorkingSolution source, WorkingSolution target) {
        List<GapEntry> gaps = new ArrayList<>();
        for (var a : source.getAssignments()) {
            if (a.staffId <= 0) continue;
            int targetStaff = target.getAssignedStaff(a.slotId);
            if (targetStaff != a.staffId) {
                gaps.add(new GapEntry(a.slotId, a.staffId, targetStaff));
            }
        }
        // Sort: prefer slots that differ most in contribution (higher target staff coverage)
        gaps.sort((g1, g2) -> {
            // Higher target staff count in target → more desirable
            int t2Count = target.getShiftCount(g2.targetStaff);
            int t1Count = target.getShiftCount(g1.targetStaff);
            return Integer.compare(t2Count, t1Count);
        });
        return gaps;
    }

    private WorkingSolution copyOf(WorkingSolution s) {
        WorkingSolution copy = WorkingSolution.fromProblem(s.getConfig(), s.getDescriptor());
        for (var a : s.getAssignments()) {
            if (a.staffId > 0) {
                copy.assign(a.slotId, a.staffId);
            }
        }
        return copy;
    }

    private record GapEntry(int slotId, int sourceStaff, int targetStaff) {}

    /** Simple score extraction for path relinking intermediates. */
    public interface SolutionScoreExtractor {
        double score(WorkingSolution solution);
    }
}