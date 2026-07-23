package com.hospital.scheduler.digital.sandbox.decision;

/**
 * Decision status for a node.
 */
public enum DecisionStatus {
    /**
     * Candidate is being evaluated.
     */
    TRYING,

    /**
     * Candidate was accepted.
     */
    ACCEPTED,

    /**
     * Candidate was rejected.
     */
    REJECTED,

    /**
     * Candidate was rejected due to hard constraint.
     */
    REJECTED_HARD,

    /**
     * Candidate was rejected due to soft constraint.
     */
    REJECTED_SOFT,

    /**
     * Candidate rejected due to tabu.
     */
    REJECTED_TABU,

    /**
     * Candidate rejected due to no improvement.
     */
    REJECTED_NO_IMPROVEMENT,

    /**
     * Skipped (e.g., not evaluated).
     */
    SKIPPED
}
