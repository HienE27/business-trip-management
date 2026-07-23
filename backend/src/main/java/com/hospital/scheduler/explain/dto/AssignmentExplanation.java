package com.hospital.scheduler.explain.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Explanation for a schedule assignment.
 *
 * <p>Provides detailed reasoning for why a staff member was assigned to a slot.
 */
@Value
@Builder
public class AssignmentExplanation {

    /**
     * Assignment ID.
     */
    Integer assignmentId;

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
     * Assigned staff ID.
     */
    Integer staffId;

    /**
     * Assigned staff name.
     */
    String staffName;

    /**
     * Staff code.
     */
    String staffCode;

    /**
     * Explanation timestamp.
     */
    LocalDateTime explainedAt;

    /**
     * Total score contribution.
     */
    double totalScore;

    /**
     * Whether the assignment satisfies all hard constraints.
     */
    boolean allHardConstraintsSatisfied;

    /**
     * Score breakdown by category.
     */
    ScoreBreakdown scoreBreakdown;

    /**
     * Hard constraints evaluation.
     */
    List<ConstraintResult> hardConstraints;

    /**
     * Soft constraints evaluation.
     */
    List<ConstraintResult> softConstraints;

    /**
     * Reasons for selection.
     */
    List<SelectionReason> selectionReasons;

    /**
     * Natural language explanation.
     */
    String naturalLanguageExplanation;

    @Value
    @Builder
    public static class ScoreBreakdown {
        double coverageScore;
        double fairnessScore;
        double preferenceScore;
        double recoveryScore;
        double weekendScore;
        double otherScore;
        double totalPenalty;
        double netScore;
    }

    @Value
    @Builder
    public static class ConstraintResult {
        String constraintId;
        String constraintName;
        boolean satisfied;
        String detail;
        double contribution;
        String reason;
    }

    @Value
    @Builder
    public static class SelectionReason {
        String reason;
        String detail;
        double weight;
        boolean positive;
    }
}
