package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.*;

/**
 * Tracks fairness metrics: Gini coefficient, Theil index, MAD.
 */
public class FairnessStatistics implements StatisticsModule {

    private final SolutionDescriptor descriptor;
    private final IncrementalStatisticsHub hub;
    private final LoadStatistics loadStats;

    // Cached values (recalculated when dirty)
    private boolean dirty = true;
    private double gini = 0;
    private double theil = 0;
    private double mad = 0;

    public FairnessStatistics(SolutionDescriptor descriptor, IncrementalStatisticsHub hub) {
        this.descriptor = descriptor;
        this.hub = hub;
        this.loadStats = hub.get(LoadStatistics.class);
    }

    @Override
    public void apply(Move move, WorkingSolution solution) {
        dirty = true;
    }

    @Override
    public void undo(Move move, WorkingSolution solution) {
        dirty = true;
    }

    @Override
    public void reset(WorkingSolution solution) {
        dirty = true;
    }

    /**
     * Get Gini coefficient (0 = perfect equality, 1 = perfect inequality).
     */
    public double getGini() {
        computeIfDirty();
        return gini;
    }

    /**
     * Get Theil index.
     */
    public double getTheil() {
        computeIfDirty();
        return theil;
    }

    /**
     * Get Mean Absolute Deviation.
     */
    public double getMAD() {
        computeIfDirty();
        return mad;
    }

    private void computeIfDirty() {
        if (!dirty) return;
        compute();
        dirty = false;
    }

    private void compute() {
        if (loadStats == null) return;

        int[] counts = loadStats.getCounts();
        double mean = hub.getMean();
        int n = counts.length;

        if (n == 0 || mean <= 0) {
            gini = 0;
            theil = 0;
            mad = 0;
            return;
        }

        // Sort for Gini
        int[] sorted = counts.clone();
        Arrays.sort(sorted);

        // Gini coefficient
        double cumsum = 0;
        double sumAbsDiff = 0;
        for (int i = 0; i < n; i++) {
            cumsum += sorted[i];
            sumAbsDiff += Math.abs((2 * (i + 1) - n - 1) * sorted[i]);
        }
        gini = sumAbsDiff / (2.0 * n * cumsum);

        // Theil index
        theil = 0;
        for (int c : counts) {
            if (c > 0) {
                double ratio = c / mean;
                theil += ratio * Math.log(ratio);
            }
        }
        theil /= n;

        // MAD
        double sumAbsDev = 0;
        for (int c : counts) {
            sumAbsDev += Math.abs(c - mean);
        }
        mad = sumAbsDev / n;
    }

    /**
     * Force recalculation.
     */
    public void invalidate() {
        dirty = true;
    }
}
