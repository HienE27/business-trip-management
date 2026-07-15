package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for incremental statistics modules.
 * 
 * <p>Provides modular statistics tracking with O(1) updates.
 * Each module tracks a specific aspect of the solution (load, weekend, consecutive, etc.)
 * and can be queried independently.</p>
 */
@Getter
public class IncrementalStatisticsHub {

    private final Map<Class<? extends StatisticsModule>, StatisticsModule> modules = new HashMap<>();
    private final SolutionDescriptor descriptor;

    // Running statistics
    private int totalShifts = 0;
    private double mean = 0;
    private double variance = 0;
    private int maxCount = 0;
    private int minCount = Integer.MAX_VALUE;

    private IncrementalStatisticsHub(SolutionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Create a new IncrementalStatisticsHub with default modules.
     */
    public static IncrementalStatisticsHub create(SolutionDescriptor descriptor) {
        IncrementalStatisticsHub hub = new IncrementalStatisticsHub(descriptor);

        hub.register(LoadStatistics.class, new LoadStatistics(descriptor, hub));
        hub.register(WeekendStatistics.class, new WeekendStatistics(descriptor, hub));
        hub.register(ConsecutiveStatistics.class, new ConsecutiveStatistics(descriptor, hub));
        hub.register(FairnessStatistics.class, new FairnessStatistics(descriptor, hub));

        return hub;
    }

    /**
     * Register a statistics module.
     */
    public <T extends StatisticsModule> void register(Class<T> type, T module) {
        modules.put(type, module);
    }

    /**
     * Get a statistics module by type.
     */
    @SuppressWarnings("unchecked")
    public <T extends StatisticsModule> T get(Class<T> type) {
        return (T) modules.get(type);
    }

    /**
     * Apply a move to all statistics modules.
     */
    public void apply(Move move, WorkingSolution solution) {
        for (StatisticsModule module : modules.values()) {
            module.apply(move, solution);
        }
        updateRunningStatistics();
    }

    /**
     * Undo a move from all statistics modules.
     */
    public void undo(Move move, WorkingSolution solution) {
        for (StatisticsModule module : modules.values()) {
            module.undo(move, solution);
        }
        updateRunningStatistics();
    }

    /**
     * Reset all statistics from current solution state.
     */
    public void reset(WorkingSolution solution) {
        // Reset all modules
        for (StatisticsModule module : modules.values()) {
            module.reset(solution);
        }
        // Recalculate running statistics
        totalShifts = 0;
        int staffCount = descriptor.getStaffCount();
        int[] counts = new int[staffCount];
        
        LoadStatistics loadStats = get(LoadStatistics.class);
        if (loadStats != null) {
            for (int i = 0; i < staffCount; i++) {
                counts[i] = loadStats.getCount(i);
                totalShifts += counts[i];
            }
        }
        
        computeRunningStatistics(counts);
    }

    /**
     * Update running statistics from current load counts.
     */
    private void updateRunningStatistics() {
        LoadStatistics loadStats = get(LoadStatistics.class);
        if (loadStats == null) return;

        int staffCount = descriptor.getStaffCount();
        int[] counts = new int[staffCount];
        totalShifts = 0;
        maxCount = 0;
        minCount = Integer.MAX_VALUE;

        for (int i = 0; i < staffCount; i++) {
            counts[i] = loadStats.getCount(i);
            totalShifts += counts[i];
            maxCount = Math.max(maxCount, counts[i]);
            minCount = Math.min(minCount, counts[i]);
        }

        computeRunningStatistics(counts);
    }

    private void computeRunningStatistics(int[] counts) {
        int n = counts.length;
        if (n == 0) {
            mean = 0;
            variance = 0;
            return;
        }

        // Calculate mean
        mean = (double) totalShifts / n;

        // Calculate variance
        double sumSq = 0;
        for (int c : counts) {
            double diff = c - mean;
            sumSq += diff * diff;
        }
        variance = n > 1 ? sumSq / (n - 1) : 0;
    }

    /**
     * Get CV (Coefficient of Variation).
     */
    public double getCV() {
        return mean > 0 ? Math.sqrt(variance) / mean : 0;
    }

    /**
     * Get gap (max - min shift count).
     */
    public int getGap() {
        return maxCount - minCount;
    }

    /**
     * Get mean shift count.
     */
    public double getMean() {
        return mean;
    }

    /**
     * Get total shifts assigned.
     */
    public int getTotalShifts() {
        return totalShifts;
    }

    /**
     * Get standard deviation.
     */
    public double getStdDev() {
        return Math.sqrt(variance);
    }

    /**
     * Check if solution is perfectly balanced.
     */
    public boolean isPerfectlyBalanced() {
        return maxCount == minCount && variance < 0.001;
    }
}
