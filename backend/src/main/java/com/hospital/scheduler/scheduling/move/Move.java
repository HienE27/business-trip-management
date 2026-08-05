package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Represents one change to a {@link WorkingSolution}.
 *
 * <p>Every move must be reversible — {@link #doMove(WorkingSolution)} applies
 * the change, {@link #undo(WorkingSolution)} reverts it exactly. The search
 * loop relies on this to evaluate moves without committing them permanently.
 *
 * <p>Implementations also report the affected slot/staff indices so the
 * statistics hub and constraints can update incrementally.
 */
public interface Move {

    /** Apply the change to {@code solution}. Must be reversible by {@link #undo}. */
    void doMove(WorkingSolution solution);

    /** Revert the change. State must equal pre-{@link #doMove} state. */
    void undo(WorkingSolution solution);

    /** A short tag describing the move type — used in logs and telemetry. */
    MoveType type();

    /** Return staff indices affected by this move (for incremental stats). */
    int[] affectedStaffIndices();

    /** Return slot indices affected by this move (for constraint validation). */
    int[] affectedSlotIndices();

    /** Discriminator for {@link Move} implementations. */
    enum MoveType {
        ASSIGN,
        UNASSIGN,
        SWAP,
        CHANGE_STAFF,
        /** 2-opt: reverse a subsequence of L01 assignments for the same staff. */
        TWO_OPT,
        /** Or-opt: relocate a chain of 1-3 consecutive L01 slots within the same staff. */
        OR_OPT
    }
}