package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.Arrays;

/**
 * Move that swaps assignments between two slots.
 */
public class SwapMove implements Move {

    private static final long serialVersionUID = 1L;

    private final int slotA;
    private final int slotB;
    private final SolutionDescriptor descriptor;

    // Cached for undo
    private int staffA = -1;
    private int staffB = -1;
    private boolean initialized = false;

    public SwapMove(int slotA, int slotB, SolutionDescriptor descriptor) {
        this.slotA = slotA;
        this.slotB = slotB;
        this.descriptor = descriptor;
    }

    @Override
    public void doMove(WorkingSolution solution) {
        // Cache staff IDs for undo
        var a = solution.getAssignment(slotA);
        var b = solution.getAssignment(slotB);
        
        if (a != null) staffA = a.staffId;
        if (b != null) staffB = b.staffId;
        initialized = true;

        solution.swap(slotA, slotB);
    }

    @Override
    public void undo(WorkingSolution solution) {
        // Swap back
        solution.swap(slotA, slotB);
    }

    @Override
    public String moveKey() {
        int min = Math.min(slotA, slotB);
        int max = Math.max(slotA, slotB);
        return String.format("S:%d:%d", min, max);
    }

    @Override
    public MoveType type() {
        return MoveType.SWAP;
    }

    @Override
    public int[] affectedStaffIndices() {
        int[] indices = new int[2];
        int count = 0;
        
        if (staffA > 0) {
            indices[count++] = descriptor.getStaffIndex(staffA);
        }
        if (staffB > 0) {
            indices[count++] = descriptor.getStaffIndex(staffB);
        }
        
        return Arrays.copyOf(indices, count);
    }

    @Override
    public int[] affectedSlotIndices() {
        return new int[] { 
            descriptor.getSlotIndex(slotA),
            descriptor.getSlotIndex(slotB)
        };
    }

    public int getSlotA() {
        return slotA;
    }

    public int getSlotB() {
        return slotB;
    }

    @Override
    public String toString() {
        return String.format("SwapMove[slot=%d<->slot=%d]", slotA, slotB);
    }
}
