package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * Interface for moves in the local search algorithm.
 * 
 * <p>A move represents a change to the working solution.
 * Each move supports the apply/undo pattern for efficient local search.</p>
 */
public interface Move extends Serializable {

    /**
     * Apply the move to the solution.
     */
    void doMove(WorkingSolution solution);

    /**
     * Undo the move.
     */
    void undo(WorkingSolution solution);

    /**
     * Get unique key for this move (for tabu tracking).
     */
    String moveKey();

    /**
     * Get move type.
     */
    MoveType type();

    /**
     * Get affected staff indices.
     */
    int[] affectedStaffIndices();

    /**
     * Get affected slot indices.
     */
    int[] affectedSlotIndices();

    /**
     * Get affected slot IDs as list.
     */
    default List<Integer> affectedSlotIdsAsList() {
        int[] ids = affectedSlotIndices();
        Integer[] boxed = new Integer[ids.length];
        for (int i = 0; i < ids.length; i++) {
            boxed[i] = ids[i];
        }
        return Arrays.asList(boxed);
    }

    /**
     * Estimate improvement potential (for prioritization).
     */
    default double estimatedImprovement() {
        return 0.0;
    }

    enum MoveType {
        ASSIGN,     // Assign a staff to a slot
        UNASSIGN,   // Unassign a staff from a slot
        MOVE,       // Move assignment from one slot to another
        SWAP        // Swap two assignments
    }
}
