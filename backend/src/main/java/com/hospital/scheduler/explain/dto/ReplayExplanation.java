package com.hospital.scheduler.explain.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Explanation for a replay iteration.
 */
@Value
@Builder
public class ReplayExplanation {

    /**
     * Session key.
     */
    String sessionKey;

    /**
     * Iteration number.
     */
    int iteration;

    /**
     * Explanation timestamp.
     */
    LocalDateTime explainedAt;

    /**
     * Move type.
     */
    String moveType;

    /**
     * Whether the move was accepted.
     */
    boolean accepted;

    /**
     * Primary staff involved.
     */
    Integer staffId;

    /**
     * Staff name.
     */
    String staffName;

    /**
     * Target staff (for swap/change).
     */
    Integer targetStaffId;

    /**
     * Target staff name.
     */
    String targetStaffName;

    /**
     * Score breakdown for this move.
     */
    ScoreBreakdown scoreBreakdown;

    /**
     * Constraint changes from this move.
     */
    List<ConstraintChange> constraintChanges;

    /**
     * Acceptance reason.
     */
    String acceptanceReason;

    /**
     * Rejection reason (if rejected).
     */
    String rejectionReason;

    /**
     * Natural language explanation.
     */
    String naturalLanguageExplanation;

    @Value
    @Builder
    public static class ScoreBreakdown {
        double coverageDelta;
        double fairnessDelta;
        double preferenceDelta;
        double recoveryDelta;
        double weekendDelta;
        double otherDelta;
        double totalDelta;
    }

    @Value
    @Builder
    public static class ConstraintChange {
        String constraintId;
        String constraintName;
        int previousViolations;
        int newViolations;
        int delta;
        boolean improved;
    }
}
