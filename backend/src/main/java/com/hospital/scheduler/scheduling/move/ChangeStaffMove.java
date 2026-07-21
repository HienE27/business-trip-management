package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Same as {@link AssignMove} but tagged with {@link MoveType#CHANGE_STAFF}
 * for telemetry. Useful when the search loop explicitly wants to distinguish
 * "first-time assignment" from "re-assignment".
 */
public class ChangeStaffMove implements Move {

    private final int slotId;
    private final int newStaffId;
    private int oldStaffId;

    public ChangeStaffMove(int slotId, int newStaffId) {
        if (slotId <= 0) throw new IllegalArgumentException("slotId must be positive");
        if (newStaffId <= 0) throw new IllegalArgumentException("newStaffId must be positive");
        this.slotId = slotId;
        this.newStaffId = newStaffId;
    }

    @Override
    public void doMove(WorkingSolution solution) {
        oldStaffId = solution.getAssignedStaff(slotId);
        solution.assign(slotId, newStaffId);
    }

    @Override
    public void undo(WorkingSolution solution) {
        if (oldStaffId > 0) {
            solution.assign(slotId, oldStaffId);
        } else {
            solution.unassign(slotId);
        }
    }

    @Override
    public MoveType type() {
        return MoveType.CHANGE_STAFF;
    }

    @Override
    public int[] affectedStaffIndices() {
        return new int[]{newStaffId, oldStaffId};
    }

    @Override
    public int[] affectedSlotIndices() {
        return new int[]{slotId};
    }

    public int slotId() { return slotId; }
    public int newStaffId() { return newStaffId; }
    public int oldStaffId() { return oldStaffId; }
}