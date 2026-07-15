package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.Arrays;

/**
 * Tracks shift counts per staff for fairness calculations.
 */
public class LoadStatistics implements StatisticsModule {

    private final SolutionDescriptor descriptor;
    private final IncrementalStatisticsHub hub;
    private final int[] shiftCount;

    public LoadStatistics(SolutionDescriptor descriptor, IncrementalStatisticsHub hub) {
        this.descriptor = descriptor;
        this.hub = hub;
        this.shiftCount = new int[descriptor.getStaffCount()];
    }

    @Override
    public void apply(Move move, WorkingSolution solution) {
        int[] indices = move.affectedStaffIndices();
        for (int idx : indices) {
            if (idx >= 0 && idx < shiftCount.length) {
                shiftCount[idx]++;
            }
        }
    }

    @Override
    public void undo(Move move, WorkingSolution solution) {
        int[] indices = move.affectedStaffIndices();
        for (int idx : indices) {
            if (idx >= 0 && idx < shiftCount.length) {
                shiftCount[idx]--;
            }
        }
    }

    @Override
    public void reset(WorkingSolution solution) {
        Arrays.fill(shiftCount, 0);
        for (MutableAssignment a : solution.getAllAssignments()) {
            int idx = descriptor.getStaffIndex(a.staffId);
            if (idx >= 0) {
                shiftCount[idx]++;
            }
        }
    }

    /**
     * Get shift count for a staff index.
     */
    public int getCount(int staffIndex) {
        if (staffIndex < 0 || staffIndex >= shiftCount.length) {
            return 0;
        }
        return shiftCount[staffIndex];
    }

    /**
     * Get shift count for a staff ID.
     */
    public int getCountById(int staffId) {
        return getCount(descriptor.getStaffIndex(staffId));
    }

    /**
     * Get all shift counts as array.
     */
    public int[] getCounts() {
        return shiftCount.clone();
    }

    /**
     * Find staff indices sorted by shift count (ascending).
     */
    public int[] getStaffIndicesSortedByCount() {
        Integer[] indices = new Integer[shiftCount.length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> Integer.compare(shiftCount[a], shiftCount[b]));
        int[] result = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            result[i] = indices[i];
        }
        return result;
    }

    /**
     * Find top K overloaded staff indices (highest counts).
     */
    public int[] getTopKOverloaded(int k) {
        int[] sorted = getStaffIndicesSortedByCount();
        int len = Math.min(k, sorted.length);
        return Arrays.copyOfRange(sorted, sorted.length - len, sorted.length);
    }

    /**
     * Find bottom K underloaded staff indices (lowest counts).
     */
    public int[] getBottomKUnderloaded(int k) {
        int[] sorted = getStaffIndicesSortedByCount();
        return Arrays.copyOf(sorted, Math.min(k, sorted.length));
    }

    /**
     * Get total shifts across all staff.
     */
    public int getTotal() {
        return Arrays.stream(shiftCount).sum();
    }

    /**
     * Get max shift count.
     */
    public int getMax() {
        return Arrays.stream(shiftCount).max().orElse(0);
    }

    /**
     * Get min shift count.
     */
    public int getMin() {
        return Arrays.stream(shiftCount).min().orElse(0);
    }
}
