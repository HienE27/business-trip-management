package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.Arrays;

/**
 * Tracks weekend shift counts per staff.
 */
public class WeekendStatistics implements StatisticsModule {

    private final SolutionDescriptor descriptor;
    private final IncrementalStatisticsHub hub;
    private final int[] weekendCount;

    public WeekendStatistics(SolutionDescriptor descriptor, IncrementalStatisticsHub hub) {
        this.descriptor = descriptor;
        this.hub = hub;
        this.weekendCount = new int[descriptor.getStaffCount()];
    }

    @Override
    public void apply(Move move, WorkingSolution solution) {
        for (int slotId : move.affectedSlotIdsAsList()) {
            MutableAssignment a = solution.getAssignment(slotId);
            if (a != null && a.isWeekend) {
                int idx = descriptor.getStaffIndex(a.staffId);
                if (idx >= 0) {
                    weekendCount[idx]++;
                }
            }
        }
    }

    @Override
    public void undo(Move move, WorkingSolution solution) {
        for (int slotId : move.affectedSlotIdsAsList()) {
            MutableAssignment a = solution.getAssignment(slotId);
            if (a != null && a.isWeekend) {
                int idx = descriptor.getStaffIndex(a.staffId);
                if (idx >= 0) {
                    weekendCount[idx]--;
                }
            }
        }
    }

    @Override
    public void reset(WorkingSolution solution) {
        Arrays.fill(weekendCount, 0);
        for (MutableAssignment a : solution.getAllAssignments()) {
            if (a.isWeekend) {
                int idx = descriptor.getStaffIndex(a.staffId);
                if (idx >= 0) {
                    weekendCount[idx]++;
                }
            }
        }
    }

    /**
     * Get weekend count for a staff index.
     */
    public int getCount(int staffIndex) {
        if (staffIndex < 0 || staffIndex >= weekendCount.length) {
            return 0;
        }
        return weekendCount[staffIndex];
    }

    /**
     * Get weekend count for a staff ID.
     */
    public int getCountById(int staffId) {
        return getCount(descriptor.getStaffIndex(staffId));
    }

    /**
     * Get all weekend counts as array.
     */
    public int[] getCounts() {
        return weekendCount.clone();
    }
}
