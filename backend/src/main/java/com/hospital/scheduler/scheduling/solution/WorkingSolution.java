package com.hospital.scheduler.scheduling.solution;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.AssignmentNode;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.Getter;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mutable working solution for the local search algorithm.
 * 
 * <p>Uses the apply/undo pattern to modify assignments in-place
 * without creating new object copies for each move.</p>
 */
@Getter
public class WorkingSolution {

    private final SchedulingProblem problem;
    private final SolutionDescriptor descriptor;
    private final IncrementalStatisticsHub statistics;
    private final SchedulingConfig config;

    // Mutable data structures
    private final Map<Integer, MutableAssignment> assignmentsBySlot = new HashMap<>();
    private final List<MutableAssignment> assignments = new ArrayList<>();

    // Index structures for fast lookup
    private final Map<Integer, List<Integer>> assignmentsByStaff = new HashMap<>();
    private final Map<LocalDate, List<Integer>> assignmentsByDate = new HashMap<>();

    public WorkingSolution(SchedulingProblem problem, SolutionDescriptor descriptor, 
                         IncrementalStatisticsHub statistics, SchedulingConfig config) {
        this.problem = problem;
        this.descriptor = descriptor;
        this.statistics = statistics;
        this.config = config;
    }

    /**
     * Create working solution from problem.
     */
    public static WorkingSolution fromProblem(SchedulingProblem problem, SchedulingConfig config) {
        SolutionDescriptor descriptor = new SolutionDescriptor(problem);
        IncrementalStatisticsHub statistics = IncrementalStatisticsHub.create(descriptor);
        WorkingSolution solution = new WorkingSolution(problem, descriptor, statistics, config);
        return solution;
    }

    /**
     * Assign a staff to a slot.
     */
    public void assign(int slotId, int staffId) {
        ShiftRequirementInfo req = problem.getRequirement(slotId);
        if (req == null) return;

        MutableAssignment existing = assignmentsBySlot.get(slotId);

        if (existing != null) {
            if (existing.staffId == staffId) {
                return; // No change
            }
            // Unassign old
            unassignInternal(slotId, existing);
        }

        // Create new assignment
        MutableAssignment a = MutableAssignment.create(
                slotId, staffId, req.getDate(), req.getShiftTypeId(),
                req.getHours(), problem.isHoliday(req.getDate()));

        // Check if staff has leave or compensation on this date
        if (problem.hasLeave(staffId, req.getDate()) || 
            problem.isCompensationDay(staffId, req.getDate())) {
            // This is a constraint violation - but we still allow it for search
            // The constraint check will penalize it
        }

        // Add to structures
        assignmentsBySlot.put(slotId, a);
        assignments.add(a);

        // Update indices
        int staffIdx = descriptor.getStaffIndex(staffId);
        assignmentsByStaff.computeIfAbsent(staffIdx, k -> new ArrayList<>()).add(slotId);
        assignmentsByDate.computeIfAbsent(req.getDate(), k -> new ArrayList<>()).add(slotId);

        // Update statistics
        if (staffIdx >= 0) {
            statistics.apply(null, this); // Move will be handled separately
        }
    }

    /**
     * Unassign a slot.
     */
    public void unassign(int slotId) {
        MutableAssignment existing = assignmentsBySlot.remove(slotId);
        if (existing != null) {
            unassignInternal(slotId, existing);
        }
    }

    private void unassignInternal(int slotId, MutableAssignment a) {
        assignments.remove(a);

        int staffIdx = descriptor.getStaffIndex(a.staffId);
        if (staffIdx >= 0) {
            List<Integer> staffAssignments = assignmentsByStaff.get(staffIdx);
            if (staffAssignments != null) {
                staffAssignments.remove(Integer.valueOf(slotId));
            }
        }

        assignmentsByDate.getOrDefault(a.date, Collections.emptyList())
                .remove(Integer.valueOf(slotId));
    }

    /**
     * Swap assignments between two slots.
     */
    public void swap(int slotA, int slotB) {
        MutableAssignment a = assignmentsBySlot.get(slotA);
        MutableAssignment b = assignmentsBySlot.get(slotB);

        if (a == null && b == null) return;

        if (a != null && b != null) {
            // Swap staff IDs
            int tempStaff = a.staffId;
            a.staffId = b.staffId;
            b.staffId = tempStaff;

            // Update indices
            int idxA = descriptor.getStaffIndex(a.staffId);
            int idxB = descriptor.getStaffIndex(b.staffId);

            // This is a simplified swap - for production, we'd need proper index updates
        } else if (a != null) {
            // Move a to b
            assignmentsBySlot.remove(slotA);
            assignmentsBySlot.put(slotB, a);
            a.slotId = slotB;
        } else {
            // Move b to a
            assignmentsBySlot.remove(slotB);
            assignmentsBySlot.put(slotA, b);
            b.slotId = slotA;
        }
    }

    /**
     * Get assignment for a slot.
     */
    public MutableAssignment getAssignment(int slotId) {
        return assignmentsBySlot.get(slotId);
    }

    /**
     * Get all assignments.
     */
    public List<MutableAssignment> getAllAssignments() {
        return assignments;
    }

    /**
     * Get assignments for a staff.
     */
    public List<MutableAssignment> getAssignmentsByStaff(int staffId) {
        int staffIdx = descriptor.getStaffIndex(staffId);
        if (staffIdx < 0) return Collections.emptyList();

        List<Integer> slotIds = assignmentsByStaff.get(staffIdx);
        if (slotIds == null) return Collections.emptyList();

        return slotIds.stream()
                .map(assignmentsBySlot::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get assignments for a date.
     */
    public List<MutableAssignment> getAssignmentsByDate(LocalDate date) {
        List<Integer> slotIds = assignmentsByDate.get(date);
        if (slotIds == null) return Collections.emptyList();

        return slotIds.stream()
                .map(assignmentsBySlot::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Check if a slot is assigned.
     */
    public boolean isAssigned(int slotId) {
        return assignmentsBySlot.containsKey(slotId);
    }

    /**
     * Get coverage percentage.
     */
    public double getCoverage() {
        int totalRequired = problem.getTotalRequiredStaff();
        int totalAssigned = assignments.size();
        return totalRequired > 0 ? (double) totalAssigned / totalRequired * 100 : 0;
    }

    /**
     * Get number of unassigned slots.
     */
    public int getUnassignedCount() {
        return problem.getRequirementCount() - assignments.size();
    }

    /**
     * Check if all slots are assigned.
     */
    public boolean isComplete() {
        return getUnassignedCount() == 0;
    }

    /**
     * Get list of unassigned slot IDs.
     */
    public List<Integer> getUnassignedSlots() {
        return problem.getRequirements().stream()
                .filter(r -> !assignmentsBySlot.containsKey(r.getSlotId()))
                .map(ShiftRequirementInfo::getSlotId)
                .collect(Collectors.toList());
    }

    /**
     * Generate initial solution using round-robin.
     */
    public void generateInitialSolution() {
        List<StaffNode> eligibleStaff = new ArrayList<>(problem.getStaffList());

        if (eligibleStaff.isEmpty()) return;

        int idx = 0;
        for (ShiftRequirementInfo req : problem.getRequirements()) {
            List<Integer> eligible = problem.getEligibleStaff(req.getSlotId());
            if (!eligible.isEmpty()) {
                int staffIdx = idx % eligible.size();
                assign(req.getSlotId(), eligible.get(staffIdx));
                idx++;
            }
        }
    }

    /**
     * Convert to immutable result.
     */
    public List<AssignmentNode> toImmutableAssignments() {
        return assignments.stream()
                .map(MutableAssignment::toImmutable)
                .collect(Collectors.toList());
    }

    /**
     * Create a deep copy of this solution.
     */
    public WorkingSolution copy() {
        WorkingSolution copy = new WorkingSolution(problem, descriptor, statistics, config);

        for (MutableAssignment a : assignments) {
            MutableAssignment aCopy = a.copy();
            copy.assignments.add(aCopy);
            copy.assignmentsBySlot.put(aCopy.slotId, aCopy);
        }

        // Rebuild indices
        for (MutableAssignment a : copy.assignments) {
            int staffIdx = descriptor.getStaffIndex(a.staffId);
            copy.assignmentsByStaff.computeIfAbsent(staffIdx, k -> new ArrayList<>()).add(a.slotId);
            copy.assignmentsByDate.computeIfAbsent(a.date, k -> new ArrayList<>()).add(a.slotId);
        }

        return copy;
    }
}
