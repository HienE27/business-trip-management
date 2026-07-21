package com.hospital.scheduler.scheduling.domain;

import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides O(1) index lookups into the {@link SchedulingProblem} plus a
 * reference to the {@link IncrementalStatisticsHub} that the search loop
 * updates as it tries moves.
 *
 * <p>Indexes are built once at construction. Hot-path code (moves, statistics
 * updates) MUST go through this descriptor instead of scanning the lists.
 */
@Getter
public final class SolutionDescriptor {

    private final SchedulingProblem problem;
    private final IncrementalStatisticsHub statisticsHub;

    private final Map<Integer, Integer> staffIdToIndex;
    private final Map<Integer, Integer> slotIdToIndex;

    public SolutionDescriptor(SchedulingProblem problem,
                              IncrementalStatisticsHub statisticsHub) {
        this.problem = problem;
        this.statisticsHub = statisticsHub;

        this.staffIdToIndex = new HashMap<>();
        int i = 0;
        for (StaffNode s : problem.getStaffList()) {
            staffIdToIndex.put(s.getId(), i++);
        }

        this.slotIdToIndex = new HashMap<>();
        i = 0;
        for (ShiftRequirementInfo r : problem.getRequirements()) {
            slotIdToIndex.put(r.id(), i++);
        }
    }

    /** Index of a staff in {@code problem.getStaffList()}, or -1 if missing. */
    public int staffIndex(int staffId) {
        Integer idx = staffIdToIndex.get(staffId);
        return idx != null ? idx : -1;
    }

    /** Index of a slot in {@code problem.getRequirements()}, or -1 if missing. */
    public int slotIndex(int slotId) {
        Integer idx = slotIdToIndex.get(slotId);
        return idx != null ? idx : -1;
    }

    public int staffCount() {
        return problem.getStaffList().size();
    }

    public int slotCount() {
        return problem.getRequirements().size();
    }
}