package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.Arrays;

/**
 * Move that assigns a staff to a slot.
 */
public class AssignMove implements Move {

    private static final long serialVersionUID = 1L;

    private final int slotId;
    private final int oldStaffId;
    private final int newStaffId;
    private final SolutionDescriptor descriptor;

    public AssignMove(int slotId, int oldStaffId, int newStaffId, SolutionDescriptor descriptor) {
        this.slotId = slotId;
        this.oldStaffId = oldStaffId;
        this.newStaffId = newStaffId;
        this.descriptor = descriptor;
    }

    public AssignMove(int slotId, int newStaffId, SolutionDescriptor descriptor) {
        this(slotId, -1, newStaffId, descriptor);
    }

    @Override
    public void doMove(WorkingSolution solution) {
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
    public String moveKey() {
        return String.format("A:%d:%d>%d", slotId, oldStaffId, newStaffId);
    }

    @Override
    public MoveType type() {
        return MoveType.ASSIGN;
    }

    @Override
    public int[] affectedStaffIndices() {
        int[] indices = new int[2];
        int count = 0;
        if (oldStaffId > 0) {
            indices[count++] = descriptor.getStaffIndex(oldStaffId);
        }
        if (newStaffId > 0) {
            indices[count++] = descriptor.getStaffIndex(newStaffId);
        }
        return Arrays.copyOf(indices, count);
    }

    @Override
    public int[] affectedSlotIndices() {
        return new int[] { descriptor.getSlotIndex(slotId) };
    }

    @Override
    public double estimatedImprovement() {
        // Simple heuristic: assigning is generally positive
        return oldStaffId > 0 ? 0.1 : 0.5;
    }

    public int getSlotId() {
        return slotId;
    }

    public int getOldStaffId() {
        return oldStaffId;
    }

    public int getNewStaffId() {
        return newStaffId;
    }

    @Override
    public String toString() {
        return String.format("AssignMove[slot=%d, %d->%d]", slotId, oldStaffId, newStaffId);
    }
}
