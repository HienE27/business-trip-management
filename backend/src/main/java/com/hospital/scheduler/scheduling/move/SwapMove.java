package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Swap the staff between two slots. If a slot is unassigned, the swap
 * effectively unassigns the other slot too (no-op when both are unassigned).
 */
public class SwapMove implements Move {

    private final int slotA;
    private final int slotB;
    private int staffA;
    private int staffB;

    public SwapMove(int slotA, int slotB) {
        if (slotA <= 0 || slotB <= 0) throw new IllegalArgumentException("slot ids must be positive");
        if (slotA == slotB) throw new IllegalArgumentException("cannot swap a slot with itself");
        this.slotA = slotA;
        this.slotB = slotB;
    }

    @Override
    public void doMove(WorkingSolution solution) {
        staffA = solution.getAssignedStaff(slotA);
        staffB = solution.getAssignedStaff(slotB);
        if (staffA > 0 && staffB > 0) {
            // Both assigned — pure swap
            solution.assign(slotA, staffB);
            solution.assign(slotB, staffA);
        } else if (staffA > 0) {
            // Only A is assigned — move A to B
            solution.unassign(slotA);
            if (staffB > 0) solution.unassign(slotB);
            solution.assign(slotB, staffA);
        } else if (staffB > 0) {
            solution.unassign(slotB);
            if (staffA > 0) solution.unassign(slotA);
            solution.assign(slotA, staffB);
        }
        // else: both unassigned — no-op
    }

    @Override
    public void undo(WorkingSolution solution) {
        // Re-run the swap in reverse — assign(slotA, staffA), assign(slotB, staffB)
        if (staffA > 0) solution.assign(slotA, staffA);
        else solution.unassign(slotA);
        if (staffB > 0) solution.assign(slotB, staffB);
        else solution.unassign(slotB);
    }

    @Override
    public MoveType type() {
        return MoveType.SWAP;
    }

    @Override
    public int[] affectedStaffIndices() {
        return new int[]{staffA, staffB};
    }

    @Override
    public int[] affectedSlotIndices() {
        return new int[]{slotA, slotB};
    }

    public int slotA() { return slotA; }
    public int slotB() { return slotB; }
}