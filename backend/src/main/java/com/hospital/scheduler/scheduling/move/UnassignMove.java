package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.Arrays;

/**
 * Move that unassigns a staff from a slot.
 */
public class UnassignMove implements Move {

    private static final long serialVersionUID = 1L;

    private final int slotId;
    private final int staffId;
    private final SolutionDescriptor descriptor;

    public UnassignMove(int slotId, int staffId, SolutionDescriptor descriptor) {
        this.slotId = slotId;
        this.staffId = staffId;
        this.descriptor = descriptor;
    }

    @Override
    public void doMove(WorkingSolution solution) {
        solution.unassign(slotId);
    }

    @Override
    public void undo(WorkingSolution solution) {
        solution.assign(slotId, staffId);
    }

    @Override
    public String moveKey() {
        return String.format("U:%d:%d", slotId, staffId);
    }

    @Override
    public MoveType type() {
        return MoveType.UNASSIGN;
    }

    @Override
    public int[] affectedStaffIndices() {
        return new int[] { descriptor.getStaffIndex(staffId) };
    }

    @Override
    public int[] affectedSlotIndices() {
        return new int[] { descriptor.getSlotIndex(slotId) };
    }

    public int getSlotId() {
        return slotId;
    }

    public int getStaffId() {
        return staffId;
    }

    @Override
    public String toString() {
        return String.format("UnassignMove[slot=%d, staff=%d]", slotId, staffId);
    }
}
