package com.hospital.scheduler.scheduling.domain;

import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Unifies access to scheduling problem components and indices.
 * 
 * <p>Provides O(1) lookup for staff and slot indices, and delegates to
 * the statistics hub for statistics access.</p>
 */
@Getter
public final class SolutionDescriptor {

    private final SchedulingProblem problem;
    private final IncrementalStatisticsHub statisticsHub;
    private final Map<Integer, Integer> staffIdToIndex;
    private final Map<Integer, Integer> slotIdToIndex;
    private final int staffCount;
    private final int slotCount;

    /**
     * Create SolutionDescriptor with a new statistics hub.
     */
    public SolutionDescriptor(SchedulingProblem problem) {
        this.problem = problem;
        this.staffIdToIndex = buildStaffIndex(problem);
        this.slotIdToIndex = buildSlotIndex(problem);
        this.staffCount = problem.getStaffCount();
        this.slotCount = problem.getRequirementCount();
        this.statisticsHub = IncrementalStatisticsHub.create(this);
    }

    /**
     * Create SolutionDescriptor with existing statistics hub.
     */
    public SolutionDescriptor(SchedulingProblem problem, IncrementalStatisticsHub statisticsHub) {
        this.problem = problem;
        this.statisticsHub = statisticsHub;
        this.staffIdToIndex = buildStaffIndex(problem);
        this.slotIdToIndex = buildSlotIndex(problem);
        this.staffCount = problem.getStaffCount();
        this.slotCount = problem.getRequirementCount();
    }

    private static Map<Integer, Integer> buildStaffIndex(SchedulingProblem problem) {
        Map<Integer, Integer> index = new HashMap<>();
        int idx = 0;
        for (var staff : problem.getStaffList()) {
            index.put(staff.getId(), idx++);
        }
        return index;
    }

    private static Map<Integer, Integer> buildSlotIndex(SchedulingProblem problem) {
        Map<Integer, Integer> index = new HashMap<>();
        int idx = 0;
        for (var req : problem.getRequirements()) {
            index.put(req.getSlotId(), idx++);
        }
        return index;
    }

    /**
     * Get staff index from staff ID.
     */
    public int getStaffIndex(int staffId) {
        return staffIdToIndex.getOrDefault(staffId, -1);
    }

    /**
     * Get slot index from slot ID.
     */
    public int getSlotIndex(int slotId) {
        return slotIdToIndex.getOrDefault(slotId, -1);
    }

    /**
     * Get staff ID from index.
     */
    public int getStaffId(int staffIndex) {
        if (staffIndex < 0 || staffIndex >= staffCount) {
            return -1;
        }
        return problem.getStaffList().get(staffIndex).getId();
    }

    /**
     * Get slot ID from index.
     */
    public int getSlotId(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotCount) {
            return -1;
        }
        return problem.getRequirements().get(slotIndex).getSlotId();
    }

    /**
     * Get statistics module by type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getStatistics(Class<T> type) {
        if (statisticsHub == null) {
            throw new IllegalStateException("Statistics hub not initialized");
        }
        return (T) statisticsHub.get(type);
    }

    /**
     * Check if indices are valid.
     */
    public boolean isValidStaffIndex(int index) {
        return index >= 0 && index < staffCount;
    }

    public boolean isValidSlotIndex(int index) {
        return index >= 0 && index < slotCount;
    }
}
