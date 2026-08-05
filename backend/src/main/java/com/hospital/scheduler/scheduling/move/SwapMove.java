package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Swap the staff between two slots. If a slot is unassigned, the swap
 * effectively unassigns the other slot too (no-op when both are unassigned).
 *
 * <p>BR04-safe variant: {@link #buildValidated(WorkingSolution, int, int)} validates
 * that swapping does NOT create an adjacent-L01 violation (BR-04) for either
 * staff. When a validated move cannot be built, null is returned so the
 * selector can skip to the next candidate.
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

    /**
     * Build a BR-04-safe swap move between two slots.
     *
     * <p>Validates that after the swap, neither staff would have two L01 slots
     * within 1 day of each other. If invalid, returns null.
     *
     * <p>L04-specialty constraint is NOT validated here — callers must check
     * {@code wouldBreakL04Specialty} separately (done in {@link
     * com.hospital.scheduler.scheduling.search.SampledMoveSelector}).
     *
     * @return a validated SwapMove, or null if swap would violate BR-04
     */
    public static SwapMove buildValidated(WorkingSolution solution, int slotA, int slotB) {
        MutableAssignment a = solution.getAssignment(slotA);
        MutableAssignment b = solution.getAssignment(slotB);
        if (a == null || b == null) return null;

        int staffA = a.staffId;
        int staffB = b.staffId;

        // Check if swap would create adjacent L01 for staff A
        if ("L01".equals(b.shiftTypeId) && staffA > 0) {
            if (hasAdjacentL01On(solution, staffA, b.date)) return null;
        }
        // Check if swap would create adjacent L01 for staff B
        if ("L01".equals(a.shiftTypeId) && staffB > 0) {
            if (hasAdjacentL01On(solution, staffB, a.date)) return null;
        }

        return new SwapMove(slotA, slotB);
    }

    private static boolean hasAdjacentL01On(WorkingSolution solution, int staffId, LocalDate date) {
        if (date == null) return false;
        for (int slotId : solution.getSlotsAssignedTo(staffId)) {
            MutableAssignment other = solution.getAssignment(slotId);
            if (other == null || other.staffId <= 0) continue;
            if (!"L01".equals(other.shiftTypeId)) continue;
            if (other.date == null) continue;
            if (other.date.plusDays(1).equals(date) || other.date.minusDays(1).equals(date)) {
                return true;
            }
        }
        return false;
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