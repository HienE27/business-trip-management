package com.hospital.scheduler.scheduling.score;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.FairnessStatistics;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import com.hospital.scheduler.scheduling.statistics.LoadStatistics;
import com.hospital.scheduler.scheduling.statistics.WeekendStatistics;
import lombok.Getter;

/**
 * Manages the {@link MutableScore} for a {@link WorkingSolution}.
 *
 * <p>Two modes:
 * <ul>
 *   <li>{@link #recomputeFull(WorkingSolution)} — full recompute from scratch (used at
 *       initial solution construction and after diversification).</li>
 *   <li>{@link #applyDelta(ScoreDelta)} / {@link #undoDelta(ScoreDelta)} — incremental
 *       updates after a move is accepted.</li>
 * </ul>
 *
 * <p>Also exposes {@link #getCurrent()} so the search loop can compare
 * candidate deltas without mutating the live score.
 */
@Getter
public class ScoreDirector {

    private final SolutionDescriptor descriptor;
    private final MutableScore current = new MutableScore();

    public ScoreDirector(SolutionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    // ── Full recompute ─────────────────────────────────────────────────────

    /**
     * Reset and recompute the score from scratch. O(n) over staff.
     */
    public void recomputeFull(WorkingSolution solution) {
        current.reset();
        IncrementalStatisticsHub hub = descriptor.getStatisticsHub();
        if (hub != null) {
            hub.reset(solution);
        }

        // Coverage
        double coverage = solution.getCoverage();
        current.applyDelta(new ScoreDelta(0, coverage, 0, 0, 0, 0, 0));

        // Fairness
        LoadStatistics load = hub != null ? hub.get(LoadStatistics.class) : null;
        WeekendStatistics weekend = hub != null ? hub.get(WeekendStatistics.class) : null;
        FairnessStatistics fairness = hub != null ? hub.get(FairnessStatistics.class) : null;

        if (load != null) {
            int n = descriptor.staffCount();
            if (n > 0) {
                double mean = (double) load.totalShifts() / n;
                double sqSum = 0;
                for (int i = 0; i < n; i++) {
                    double d = load.getShiftCount(i) - mean;
                    sqSum += d * d;
                }
                double cv = mean > 0 ? Math.sqrt(sqSum / n) / mean : 0;
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                for (int i = 0; i < n; i++) {
                    int v = load.getShiftCount(i);
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
                int gap = min == Integer.MAX_VALUE ? 0 : (max - min);
                current.applyDelta(new ScoreDelta(0, 0, cv, 0, 0, gap, 0));
            }
        }
        if (weekend != null && descriptor.staffCount() > 0) {
            int n = descriptor.staffCount();
            double mean = (double) weekend.totalWeekendShifts() / n;
            double sqSum = 0;
            for (int i = 0; i < n; i++) {
                double d = weekend.getWeekendCount(i) - mean;
                sqSum += d * d;
            }
            double cvW = mean > 0 ? Math.sqrt(sqSum / n) / mean : 0;
            current.applyDelta(new ScoreDelta(0, 0, 0, 0, 0, 0, 0));
            // Reuse cvDelta slot for cvWeekend? No — keep separate. Add via direct setter:
            // (no public setter on MutableScore, so we encode weekend CV as part of cvDelta)
            // For simplicity, fold into cvDelta via a small accessor:
            setCvWeekend(cvW);
        }
        if (fairness != null) {
            double gini = fairness.getGini();
            current.applyDelta(new ScoreDelta(0, 0, 0, 0, 0, 0, gini));
        }
    }

    // ── Incremental ────────────────────────────────────────────────────────

    public void applyDelta(ScoreDelta delta) {
        current.applyDelta(delta);
    }

    public void undoDelta(ScoreDelta delta) {
        current.undoDelta(delta);
    }

    public MutableScore getCurrent() {
        return current;
    }

    // ── Internal ───────────────────────────────────────────────────────────

    /** Direct setter for cvWeekend (used during full recompute). */
    private void setCvWeekend(double v) {
        current.setCvWeekend(v);
    }
}