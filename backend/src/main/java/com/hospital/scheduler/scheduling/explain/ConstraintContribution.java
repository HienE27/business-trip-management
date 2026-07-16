package com.hospital.scheduler.scheduling.explain;

import lombok.Builder;
import lombok.Getter;

/**
 * One row in an {@link AssignmentExplanation}'s constraint breakdown.
 *
 * <p>Captures a single constraint's contribution to the score for the
 * assignment in question, plus whether it's hard or soft and how often it
 * fired across the solution.
 */
@Getter
@Builder
public class ConstraintContribution {

    /** Constraint id (e.g. "BR-01:ShiftConflict"). */
    private final String constraintId;

    /** True for hard constraints (infinite weight). */
    private final boolean hard;

    /** Number of violations contributed by this constraint for this assignment. */
    private final int violations;

    /** Weight (always POSITIVE_INFINITY for hard). */
    private final double weight;
}