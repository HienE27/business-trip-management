package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Removes any staff from {@code slotId}. Result is an unassigned slot.
 */
public class UnassignMove implements Move {

    private final int slotId;
    private int previousStaffId;

    public UnassignMove(int slotId) {
        if (slotId <= 0) throw new IllegalArgumentException("slotId must be positive");
        this.slotId = slotId;
    }

    @Override
    public void doMove(WorkingSolution solution) {
        previousStaffId = solution.getAssignedStaff(slotId);
        solution.unassign(slotId);
    }

    @Override
    public void undo(WorkingSolution solution) {
        if (previousStaffId > 0) {
            solution.assign(slotId, previousStaffId);
        }
    }

    @Override
    public MoveType type() {
        return MoveType.UNASSIGN;
    }

    @Override
    public int[] affectedStaffIndices() {
        return new int[]{previousStaffId};
    }

    @Override
    public int[] affectedSlotIndices() {
        return new int[]{slotId};
    }

    public int slotId() {
        return slotId;
    }

    public int previousStaffId() {
        return previousStaffId;
    }
}