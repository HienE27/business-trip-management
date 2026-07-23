package com.hospital.scheduler.explain.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Explanation for why a candidate was NOT selected.
 *
 * <p>Provides detailed reasoning for candidate rejection.
 */
@Value
@Builder
public class WhyNotExplanation {

    /**
     * Slot ID.
     */
    Integer slotId;

    /**
     * Work date.
     */
    String workDate;

    /**
     * Shift type.
     */
    String shiftType;

    /**
     * Candidate staff ID.
     */
    Integer staffId;

    /**
     * Candidate staff name.
     */
    String staffName;

    /**
     * Candidate staff code.
     */
    String staffCode;

    /**
     * Why explanation timestamp.
     */
    LocalDateTime explainedAt;

    /**
     * Whether the candidate was rejected.
     */
    boolean rejected;

    /**
     * Rejection reasons (sorted by severity).
     */
    List<RejectionReason> rejectionReasons;

    /**
     * The constraint that caused rejection (most severe).
     */
    String primaryRejectionConstraint;

    /**
     * Constraint chain leading to rejection.
     */
    List<ConstraintChainNode> constraintChain;

    /**
     * Score impact of this candidate.
     */
    double scoreImpact;

    /**
     * Candidate's rank among all candidates.
     */
    int rank;

    /**
     * Alternative candidate that was selected.
     */
    SelectedAlternative selectedAlternative;

    /**
     * Natural language explanation.
     */
    String naturalLanguageExplanation;

    @Value
    @Builder
    public static class RejectionReason {
        String constraintId;
        String constraintName;
        String reasonType; // HARD, SOFT, PREFERENCE
        String detail;
        double penalty;
        boolean isBlocking; // If true, this alone caused rejection
    }

    @Value
    @Builder
    public static class ConstraintChainNode {
        String description;
        String detail;
        boolean satisfied;
    }

    @Value
    @Builder
    public static class SelectedAlternative {
        Integer staffId;
        String staffName;
        double score;
        String selectionReason;
    }
}
