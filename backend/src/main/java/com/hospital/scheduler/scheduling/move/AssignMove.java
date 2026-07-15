package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Assigns {@code staffId} to {@code slotId}, replacing any prior assignment.
 *
 * <p>If the slot was already assigned, the previous staff is implicitly
 * unassigned (counts as a {@code ChangeStaff} for stats purposes — but we
 * keep {@link #type()} as {@code ASSIGN} so logging is consistent).
 */
public class AssignMove implements Move {

    private final int slotId;
    private final int staffId;
    private int previousStaffId;

    public AssignMove(int slotId, int staffId) {
        if (slotId <= 0) throw new IllegalArgumentException("slotId must be positive");
        if (staffId <= 0) throw new IllegalArgumentException("staffId must be positive");
        this.slotId = slotId;
        this.staffId = staffId;
    }

    @Override
    public void doMove(WorkingSolution solution) {
        previousStaffId = solution.getAssignedStaff(slotId);
        solution.assign(slotId, staffId);
    }

    @Override
    public void undo(WorkingSolution solution) {
        if (previousStaffId > 0) {
            solution.assign(slotId, previousStaffId);
        } else {
            solution.unassign(slotId);
        }
    }

    @Override
    public MoveType type() {
        return MoveType.ASSIGN;
    }

    @Override
    public int[] affectedStaffIndices() {
        return new int[]{staffId, previousStaffId};
    }

    @Override
    public int[] affectedSlotIndices() {
        return new int[]{slotId};
    }

    public int slotId() {
        return slotId;
    }

    public int staffId() {
        return staffId;
    }

    public int previousStaffId() {
        return previousStaffId;
    }
}