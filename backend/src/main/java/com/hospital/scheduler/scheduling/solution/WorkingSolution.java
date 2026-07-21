package com.hospital.scheduler.scheduling.solution;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable solution being optimized.
 *
 * <p>Holds:
 * <ul>
 *   <li>{@link #assignmentsBySlot} — slotId → {@link MutableAssignment}</li>
 *   <li>{@link #assignments} — insertion-ordered list (used to enumerate assignments)</li>
 *   <li>Inverse indices for fast lookup (staff → slots assigned, etc.)</li>
 * </ul>
 *
 * <p>Mutation methods ({@link #assign}, {@link #unassign}, {@link #swap}) are
 * the canonical entry points. The statistics hub is updated by the search
 * loop AFTER each mutation, NOT from inside this class — keeping the model
 * passive avoids double-bookkeeping.
 */
@Getter
public class WorkingSolution {

    private final SchedulingConfig config;
    private final SolutionDescriptor descriptor;
    private final SchedulingProblem problem;

    /** slotId → MutableAssignment (assigned OR sentinel for empty slot). */
    private final Map<Integer, MutableAssignment> assignmentsBySlot = new HashMap<>();

    /** Insertion-ordered list of all slot assignments (mirrors assignmentsBySlot). */
    private final List<MutableAssignment> assignments = new ArrayList<>();

    /** staffId → list of slots assigned to that staff. */
    private final Map<Integer, List<Integer>> slotsByStaff = new HashMap<>();

    public WorkingSolution(SchedulingConfig config, SolutionDescriptor descriptor) {
        this.config = config;
        this.descriptor = descriptor;
        this.problem = descriptor.getProblem();
    }

    // ── Mutation operations ─────────────────────────────────────────────────

    /**
     * Assign {@code staffId} to {@code slotId}. Replaces any prior assignment.
     * Caller MUST call {@code IncrementalStatisticsHub.apply(move, this)} after.
     */
    public void assign(int slotId, int staffId) {
        if (staffId <= 0) {
            throw new IllegalArgumentException("staffId must be positive, got " + staffId);
        }
        MutableAssignment existing = assignmentsBySlot.get(slotId);
        if (existing == null) {
            // New slot — load requirement data and create assignment
            ShiftRequirementInfo req = problem.getRequirementsById().get(slotId);
            if (req == null) {
                throw new IllegalArgumentException("Unknown slotId: " + slotId);
            }
            MutableAssignment ma = new MutableAssignment();
            ma.slotId = slotId;
            ma.staffId = staffId;
            ma.date = req.date();
            ma.shiftTypeId = req.shiftTypeId();
            ma.hours = hoursFor(req.shiftTypeId());
            ma.isWeekend = ma.date != null
                    && (ma.date.getDayOfWeek().getValue() >= 6);
            ma.isHoliday = problem.isHoliday(ma.date);
            assignmentsBySlot.put(slotId, ma);
            assignments.add(ma);
        } else {
            // Reassignment — update staffId and inverse index
            int oldStaffId = existing.staffId;
            if (oldStaffId == staffId) return;
            if (oldStaffId > 0) {
                removeFromStaffIndex(oldStaffId, slotId);
            }
            existing.staffId = staffId;
        }
        slotsByStaff.computeIfAbsent(staffId, k -> new ArrayList<>()).add(slotId);
    }

    /** Remove any staff from {@code slotId}. */
    public void unassign(int slotId) {
        MutableAssignment existing = assignmentsBySlot.get(slotId);
        if (existing == null || existing.staffId <= 0) {
            return; // already unassigned
        }
        removeFromStaffIndex(existing.staffId, slotId);
        existing.staffId = -1;
    }

    /**
     * Swap the staff between two slots. If either slot is unassigned, the
     * unassigned side becomes -1 (no swap partner).
     */
    public void swap(int slotA, int slotB) {
        if (slotA == slotB) return;
        int staffA = getAssignedStaff(slotA);
        int staffB = getAssignedStaff(slotB);
        if (staffA > 0) {
            unassign(slotA);
            assign(slotA, staffB);
        }
        if (staffB > 0 && staffA > 0) {
            assign(slotB, staffA);
        } else if (staffB > 0) {
            assign(slotB, staffA);
        }
    }

    // ── Read accessors ──────────────────────────────────────────────────────

    /** Staff id assigned to {@code slotId}, or -1 if unassigned. */
    public int getAssignedStaff(int slotId) {
        MutableAssignment a = assignmentsBySlot.get(slotId);
        return a != null ? a.staffId : -1;
    }

    /** Returns the {@link MutableAssignment} for {@code slotId}, or null. */
    public MutableAssignment getAssignment(int slotId) {
        return assignmentsBySlot.get(slotId);
    }

    /** Number of slots currently assigned to {@code staffId}. */
    public int getShiftCount(int staffId) {
        List<Integer> slots = slotsByStaff.get(staffId);
        return slots != null ? slots.size() : 0;
    }

    /** All slot ids assigned to {@code staffId}. */
    public List<Integer> getSlotsAssignedTo(int staffId) {
        return slotsByStaff.getOrDefault(staffId, List.of());
    }

    /**
     * Coverage as a fraction in [0, 1]. Equals
     * {@code (assigned slot count) / (total slot count)}.
     */
    public double getCoverage() {
        if (assignments.isEmpty()) return 0.0;
        int assigned = 0;
        for (MutableAssignment a : assignments) {
            if (a.staffId > 0) assigned++;
        }
        return (double) assigned / assignments.size();
    }

    // ── Factory ─────────────────────────────────────────────────────────────

    /**
     * Build an empty {@code WorkingSolution} from a problem descriptor.
     * All slots start unassigned.
     */
    public static WorkingSolution fromProblem(SchedulingConfig config,
                                              SolutionDescriptor descriptor) {
        WorkingSolution sol = new WorkingSolution(config, descriptor);
        for (ShiftRequirementInfo req : descriptor.getProblem().getRequirements()) {
            MutableAssignment ma = new MutableAssignment();
            ma.slotId = req.id();
            ma.staffId = -1;
            ma.date = req.date();
            ma.shiftTypeId = req.shiftTypeId();
            ma.hours = hoursFor(req.shiftTypeId());
            ma.isWeekend = ma.date != null
                    && ma.date.getDayOfWeek().getValue() >= 6;
            ma.isHoliday = descriptor.getProblem().isHoliday(ma.date);
            sol.assignmentsBySlot.put(req.id(), ma);
            sol.assignments.add(ma);
        }
        return sol;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void removeFromStaffIndex(int staffId, int slotId) {
        List<Integer> slots = slotsByStaff.get(staffId);
        if (slots != null) {
            slots.remove(Integer.valueOf(slotId));
            if (slots.isEmpty()) {
                slotsByStaff.remove(staffId);
            }
        }
    }

    private static int hoursFor(String shiftTypeId) {
        if (shiftTypeId == null) return 0;
        return switch (shiftTypeId) {
            case "L01" -> 24;
            case "L02" -> 4;
            case "L03" -> 4;
            case "L04" -> 4;
            default -> 0;
        };
    }
}