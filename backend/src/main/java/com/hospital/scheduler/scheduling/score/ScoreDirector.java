package com.hospital.scheduler.scheduling.score;

import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.FairnessStatistics;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import com.hospital.scheduler.scheduling.statistics.LoadStatistics;
import lombok.Getter;

/**
 * Manages score calculation with incremental updates.
 * 
 * <p>Coordinates between constraint registry and statistics hub to calculate
 * score deltas for moves without full recalculation.</p>
 */
@Getter
public class ScoreDirector {

    private final ConstraintRegistry constraintRegistry;
    private final IncrementalStatisticsHub statisticsHub;
    private final SolutionDescriptor descriptor;
    private final MutableScore currentScore;

    private ScoreSnapshot bestSnapshot;
    private int bestIteration = 0;

    public ScoreDirector(ConstraintRegistry constraintRegistry, 
                        IncrementalStatisticsHub statisticsHub,
                        SolutionDescriptor descriptor) {
        this.constraintRegistry = constraintRegistry;
        this.statisticsHub = statisticsHub;
        this.descriptor = descriptor;
        this.currentScore = new MutableScore();
    }

    /**
     * Initialize score from current solution state.
     */
    public void initialize(WorkingSolution solution) {
        // Calculate initial score
        int hard = 0;
        int soft = 0;
        
        // Count constraint violations
        var violations = constraintRegistry.validate(solution);
        hard = violations.getHardCount();
        soft = violations.getSoftCount();
        
        // Get statistics
        double cv = statisticsHub.getCV();
        int gap = statisticsHub.getGap();
        double gini = 0;
        
        FairnessStatistics fairStats = statisticsHub.get(FairnessStatistics.class);
        if (fairStats != null) {
            gini = fairStats.getGini();
        }
        
        // Set scores
        currentScore.setHardViolations(hard);
        currentScore.setCoverage(solution.getCoverage());
        currentScore.setCvTotal(cv);
        currentScore.setSoftViolations(soft);
        currentScore.setGap(gap);
        currentScore.setGini(gini);
        
        // Set as best
        bestSnapshot = currentScore.toSnapshot();
        bestIteration = 0;
    }

    /**
     * Calculate delta score for a move.
     */
    public ScoreDelta calculateDelta(Move move, WorkingSolution solution) {
        ScoreDelta.Builder builder = ScoreDelta.builder();

        // 1. Calculate constraint delta
        ScoreDelta constraintDelta = constraintRegistry.calculateDelta(move, solution);
        if (constraintDelta != null) {
            builder.add(constraintDelta);
        }

        // 2. Calculate coverage delta
        double coverageDelta = calculateCoverageDelta(move, solution);
        builder.coverageDelta(coverageDelta);

        // 3. Calculate CV delta (approximate)
        double cvDelta = calculateCVDelta(move, solution);
        builder.cvDelta(cvDelta);

        // 4. Calculate gap delta
        int gapDelta = calculateGapDelta(move, solution);
        builder.gapDelta(gapDelta);

        // 5. Calculate Gini delta
        double giniDelta = calculateGiniDelta(move, solution);
        builder.giniDelta(giniDelta);

        return builder.build();
    }

    /**
     * Apply a delta to the current score.
     */
    public void applyDelta(ScoreDelta delta) {
        currentScore.applyDelta(delta);
    }

    /**
     * Undo a delta from the current score.
     */
    public void undoDelta(ScoreDelta delta) {
        currentScore.undoDelta(delta);
    }

    /**
     * Update best score if improved.
     */
    public boolean updateBestIfImproved(int iteration) {
        int cmp = currentScore.compareTo(ObjectiveScoreAdapter.adapt(bestSnapshot));
        if (cmp < 0) { // Lower is better in lexicographic order
            bestSnapshot = currentScore.toSnapshot();
            bestIteration = iteration;
            return true;
        }
        return false;
    }

    /**
     * Snapshot current score.
     */
    public ScoreSnapshot snapshot() {
        return currentScore.toSnapshot();
    }

    /**
     * Restore from snapshot.
     */
    public void restore(ScoreSnapshot snapshot) {
        currentScore.reset(snapshot);
    }

    /**
     * Get current score.
     */
    public MutableScore getCurrentScore() {
        return currentScore;
    }

    /**
     * Get current score as immutable.
     */
    public ObjectiveScore getCurrentAsObjective() {
        return currentScore.toImmutable();
    }

    /**
     * Get best score.
     */
    public ScoreSnapshot getBestSnapshot() {
        return bestSnapshot;
    }

    /**
     * Get best score as objective.
     */
    public ObjectiveScore getBestAsObjective() {
        if (bestSnapshot == null) {
            return currentScore.toImmutable();
        }
        return ObjectiveScoreAdapter.adapt(bestSnapshot);
    }

    // Delta calculation helpers

    private double calculateCoverageDelta(Move move, WorkingSolution solution) {
        // Coverage changes when assigning/unassigning slots
        switch (move.type()) {
            case ASSIGN:
                return solution.isAssigned(move.affectedSlotIds()[0]) ? 0 : 
                    100.0 / solution.getProblem().getTotalRequiredStaff();
            case UNASSIGN:
                return solution.isAssigned(move.affectedSlotIds()[0]) ? 
                    -100.0 / solution.getProblem().getTotalRequiredStaff() : 0;
            default:
                return 0; // MOVE and SWAP don't change coverage
        }
    }

    private double calculateCVDelta(Move move, WorkingSolution solution) {
        // Calculate CV change by simulating the move
        LoadStatistics loadStats = statisticsHub.get(LoadStatistics.class);
        if (loadStats == null) return 0;

        // Get old CV
        double oldCV = statisticsHub.getCV();

        // Simulate apply
        int[] affectedStaff = move.affectedStaffIndices();
        for (int idx : affectedStaff) {
            // This is a simplification - full implementation would track
            // the exact change in counts
        }

        // Get new CV (would need to actually apply to calculate)
        double newCV = oldCV; // Placeholder

        return newCV - oldCV;
    }

    private int calculateGapDelta(Move move, WorkingSolution solution) {
        // Gap changes when shift counts change
        int[] affectedStaff = move.affectedStaffIndices();
        if (affectedStaff.length == 0) return 0;

        LoadStatistics loadStats = statisticsHub.get(LoadStatistics.class);
        if (loadStats == null) return 0;

        // Simplified: just recalculate gap
        // Full implementation would track max/min changes
        int oldGap = loadStats.getMax() - loadStats.getMin();
        
        // This is a simplification - full implementation would be incremental
        return 0;
    }

    private double calculateGiniDelta(Move move, WorkingSolution solution) {
        FairnessStatistics fairStats = statisticsHub.get(FairnessStatistics.class);
        if (fairStats == null) return 0;

        double oldGini = fairStats.getGini();
        // Would need to simulate to get new value
        return 0; // Simplified
    }

    /**
     * Adapter for comparing ScoreSnapshot with ObjectiveScore.
     */
    static class ObjectiveScoreAdapter {
        static ObjectiveScore adapt(ScoreSnapshot snapshot) {
            if (snapshot == null) return null;
            return new ObjectiveScore(
                    snapshot.hardViolations(),
                    snapshot.coverage(),
                    snapshot.cvTotal(),
                    snapshot.cvWeekend(),
                    snapshot.softViolations(),
                    snapshot.gap(),
                    snapshot.gini(),
                    snapshot.mad()
            );
        }
    }
}
