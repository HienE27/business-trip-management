package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared test fixture builder for v10 constraint tests.
 *
 * <p>Provides fluent helpers to construct a {@link WorkingSolution} with
 * synthetic staff/requirements/leaves without spinning up Spring or JPA.
 */
public final class ConstraintTestSupport {

    private ConstraintTestSupport() {}

    /**
     * Build a minimal {@link SchedulingProblem} with N active staff and zero
     * requirements. Constraints under test will iterate {@code solution.getAssignments()}
     * so most tests add their own assignments directly.
     */
    public static SchedulingProblem emptyProblem(int staffCount) {
        List<Staff> staff = new ArrayList<>();
        for (int i = 0; i < staffCount; i++) {
            Staff s = new Staff();
            s.setId(i + 1);
            s.setFullName("Test Staff " + (i + 1));
            s.setIsActive(true);
            s.setMaxShiftsPerMonth(5);
            staff.add(s);
        }
        return SchedulingProblem.from(
                staff,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashSet<>(),
                new SchedulingConfig());
    }

    /** Build a problem with explicit leaves (one per staff entry). */
    public static SchedulingProblem problemWithLeaves(Map<Integer, Set<LocalDate>> leaves) {
        List<Staff> staff = new ArrayList<>();
        List<com.hospital.scheduler.entity.LeaveRequest> leavesList = new ArrayList<>();
        for (Map.Entry<Integer, Set<LocalDate>> e : leaves.entrySet()) {
            Staff s = new Staff();
            s.setId(e.getKey());
            s.setFullName("Staff " + e.getKey());
            s.setIsActive(true);
            s.setMaxShiftsPerMonth(5);
            staff.add(s);
            for (LocalDate d : e.getValue()) {
                com.hospital.scheduler.entity.LeaveRequest lr =
                        new com.hospital.scheduler.entity.LeaveRequest();
                lr.setStaff(s);
                lr.setStartDate(d);
                lr.setEndDate(d);
                leavesList.add(lr);
            }
        }
        return SchedulingProblem.from(
                staff,
                new ArrayList<>(),
                leavesList,
                new ArrayList<>(),
                new HashSet<>(),
                new SchedulingConfig());
    }

    /** Wrap a {@link SchedulingProblem} in a {@link WorkingSolution} ready for constraint evaluation. */
    public static WorkingSolution wrapSolution(SchedulingProblem problem) {
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);
        // Replace descriptor with one that holds the hub
        SolutionDescriptor wired = new SolutionDescriptor(problem, hub);
        return WorkingSolution.fromProblem(new SchedulingConfig(), wired);
    }

    /** Convenience helper. */
    public static List<ShiftRequirementInfo> requirementsFor(int[] slotIds, LocalDate baseDate, String[] shiftTypes) {
        List<ShiftRequirementInfo> out = new ArrayList<>();
        for (int i = 0; i < slotIds.length; i++) {
            out.add(new ShiftRequirementInfo(
                    slotIds[i],
                    baseDate.plusDays(i),
                    shiftTypes[i % shiftTypes.length],
                    null,
                    1));
        }
        return out;
    }

    /** Convenience: empty compensation map. */
    public static Map<Integer, Set<LocalDate>> emptyCompByStaff() {
        return new HashMap<>();
    }
}