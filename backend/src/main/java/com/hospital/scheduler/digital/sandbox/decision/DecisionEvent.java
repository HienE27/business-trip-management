package com.hospital.scheduler.digital.sandbox.decision;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event emitted during simulation to capture decision-making process.
 *
 * <p>These events are used to build the decision graph.
 */
@Value
@Builder
public class DecisionEvent {

    /**
     * Event ID.
     */
    UUID id;

    /**
     * Session key.
     */
    String sessionKey;

    /**
     * Iteration number.
     */
    int iteration;

    /**
     * Event timestamp.
     */
    LocalDateTime timestamp;

    /**
     * Event type.
     */
    DecisionEventType eventType;

    /**
     * Slot ID being filled.
     */
    Integer slotId;

    /**
     * Candidate staff ID.
     */
    Integer candidateStaffId;

    /**
     * Candidate staff name.
     */
    String candidateStaffName;

    /**
     * Decision status.
     */
    DecisionStatus status;

    /**
     * Rejection reason if rejected.
     */
    String rejectionReason;

    /**
     * Constraint that caused rejection.
     */
    String violatedConstraint;

    /**
     * Score delta.
     */
    Double scoreDelta;

    /**
     * Coverage delta.
     */
    Double coverageDelta;

    /**
     * Fairness delta.
     */
    Double fairnessDelta;

    /**
     * Parent node ID.
     */
    UUID parentId;

    /**
     * Node ID (if this event creates a node).
     */
    UUID nodeId;

    /**
     * Event types.
     */
    public enum DecisionEventType {
        /**
         * Started evaluating candidates for a slot.
         */
        SLOT_EVALUATION_START,

        /**
         * Trying a candidate.
         */
        CANDIDATE_TRY,

        /**
         * Candidate accepted.
         */
        CANDIDATE_ACCEPTED,

        /**
         * Candidate rejected.
         */
        CANDIDATE_REJECTED,

        /**
         * All candidates exhausted.
         */
        SLOT_EVALUATION_END,

        /**
         * Backtracking.
         */
        BACKTRACK,

        /**
         * Diversification.
         */
        DIVERSIFY
    }
}
