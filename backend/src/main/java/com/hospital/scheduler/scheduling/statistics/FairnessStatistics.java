package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import lombok.Getter;

/**
 * Fairness metrics derived from {@link LoadStatistics}.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link #getCV()} — coefficient of variation (lower = fairer)</li>
 *   <li>{@link #getGap()} — max-min spread (zero = perfectly fair)</li>
 *   <li>{@link #getGini()} — Gini coefficient (0 = perfect equality)</li>
 * </ul>
 *
 * <p>Reuses {@link LoadStatistics} so it doesn't double-track counters —
 * cheaper than maintaining its own shift counts.
 */
@Getter
public class FairnessStatistics implements StatisticsModule {

    private final SolutionDescriptor descriptor;

    public FairnessStatistics(SolutionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Coefficient of variation (stddev / mean) of shift counts across staff.
     * Returns 0 when no shifts are assigned or only one staff is loaded.
     */
    public double getCV() {
        LoadStatistics load = getLoadStats();
        if (load == null) return 0;
        int n = descriptor.staffCount();
        if (n == 0) return 0;
        double sum = 0;
        int nonZero = 0;
        for (int idx = 0; idx < n; idx++) {
            int v = load.getShiftCount(idx);
            sum += v;
            if (v > 0) nonZero++;
        }
        double mean = sum / n;
        if (mean == 0) return 0;
        double sqSum = 0;
        for (int idx = 0; idx < n; idx++) {
            double d = load.getShiftCount(idx) - mean;
            sqSum += d * d;
        }
        double variance = sqSum / n;
        return Math.sqrt(variance) / mean;
    }

    /** Gap between most-loaded and least-loaded staff (shift count). */
    public int getGap() {
        LoadStatistics load = getLoadStats();
        if (load == null) return 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int idx = 0; idx < descriptor.staffCount(); idx++) {
            int v = load.getShiftCount(idx);
            if (v < min) min = v;
            if (v > max) max = v;
        }
        return min == Integer.MAX_VALUE ? 0 : (max - min);
    }

    /** Gini coefficient in [0, 1]. 0 = perfect equality. */
    public double getGini() {
        LoadStatistics load = getLoadStats();
        if (load == null) return 0;
        int n = descriptor.staffCount();
        if (n == 0) return 0;
        int[] counts = new int[n];
        for (int idx = 0; idx < n; idx++) counts[idx] = load.getShiftCount(idx);
        java.util.Arrays.sort(counts);
        double sum = 0;
        for (int i = 0; i < n; i++) sum += counts[i];
        if (sum == 0) return 0;
        double cumulativeDiff = 0;
        for (int i = 0; i < n; i++) {
            cumulativeDiff += (2.0 * (i + 1) - n - 1) * counts[i];
        }
        return cumulativeDiff / (n * sum);
    }

    // ── StatisticsModule hooks (no-op; we delegate to LoadStatistics) ───────

    @Override
    public void apply(Move move, WorkingSolution solution) {
        // FairnessStatistics derives from LoadStatistics; nothing to do here.
    }

    @Override
    public void undo(Move move, WorkingSolution solution) {
        // Same — derived view.
    }

    @Override
    public void reset(WorkingSolution solution) {
        // Same — derived view.
    }

    /** Lazy accessor for the hub's LoadStatistics instance. */
    private LoadStatistics getLoadStats() {
        if (descriptor == null) return null;
        IncrementalStatisticsHub hub = descriptor.getStatisticsHub();
        if (hub == null) return null;
        return hub.get(LoadStatistics.class);
    }
}