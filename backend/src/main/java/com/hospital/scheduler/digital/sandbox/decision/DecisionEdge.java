package com.hospital.scheduler.digital.sandbox.decision;

import lombok.Value;

import java.util.UUID;

/**
 * Represents a single decision edge in the decision graph.
 *
 * <p>Edges connect nodes to show the flow of decisions.
 */
@Value
public class DecisionEdge {

    /**
     * Source node ID.
     */
    UUID fromId;

    /**
     * Target node ID.
     */
    UUID toId;

    /**
     * Edge type.
     */
    EdgeType type;

    /**
     * Edge label for display.
     */
    String label;

    /**
     * Edge weight (e.g., penalty value).
     */
    Double weight;

    /**
     * Edge types.
     */
    public enum EdgeType {
        /**
         * Evaluating a candidate.
         */
        TRY,

        /**
         * Candidate rejected.
         */
        REJECT,

        /**
         * Candidate accepted.
         */
        ACCEPT,

        /**
         * Rolling back to try another candidate.
         */
        ROLLBACK,

        /**
         * Backtracking to parent.
         */
        BACKTRACK,

        /**
         * Child alternative.
         */
        ALTERNATIVE
    }
}
