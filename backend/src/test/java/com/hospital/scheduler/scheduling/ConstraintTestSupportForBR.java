package com.hospital.scheduler.scheduling;

import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Test-only helpers for v10 integration tests.
 */
public final class ConstraintTestSupportForBR {

    private ConstraintTestSupportForBR() {}

    public static WorkingSolution wrapSolution(List<Staff> staff, List<ShiftRequirement> reqs) {
        SchedulingProblem problem = SchedulingProblem.from(
                staff,
                reqs,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashSet<>(),
                new SchedulingConfig());
        return wrapSolution(problem);
    }

    public static WorkingSolution wrapSolution(SchedulingProblem problem) {
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);
        SolutionDescriptor wired = new SolutionDescriptor(problem, hub);
        return WorkingSolution.fromProblem(new SchedulingConfig(), wired);
    }
}