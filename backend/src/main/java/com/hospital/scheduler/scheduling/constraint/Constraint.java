package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.io.Serializable;

/**
 * Interface for constraint plugins.
 * 
 * <p>Each constraint implements the calculateDelta method to return
 * the score impact of a move. Constraints are registered in the
 * ConstraintRegistry and evaluated incrementally.</p>
 */
public interface Constraint extends Serializable {

    /**
     * Constraint type (hard must be satisfied, soft is penalized).
     */
    enum Type {
        HARD,
        SOFT
    }

    /**
     * Get constraint name.
     */
    String name();

    /**
     * Get constraint type.
     */
    Type type();

    /**
     * Check if this constraint is applicable to the given move.
     */
    boolean isApplicable(Move move);

    /**
     * Calculate the delta in violations when this move is applied.
     * 
     * @param move     The move being evaluated
     * @param solution The current solution state
     * @return ScoreDelta representing the change in violations
     */
    ScoreDelta calculateDelta(Move move, WorkingSolution solution);

    /**
     * Get the penalty weight for this constraint (default 1.0).
     */
    default double weight() {
        return 1.0;
    }

    /**
     * Get a repair hint for this constraint (for repair heuristics).
     */
    default RepairHint repairHint() {
        return RepairHint.NONE;
    }

    /**
     * Repair hints for automated repair.
     */
    enum RepairHint {
        NONE,
        REASSIGN,
        MOVE_STAFF,
        ADD_STAFF,
        REMOVE_ASSIGNMENT,
        SWAP
    }
}
