package com.hospital.scheduler.scheduling.explain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * JSON-serializable explanation for a single assignment.
 *
 * <p>For a {@code (slotId, staffId)} pair, explains:
 * <ul>
 *   <li>Why this staff was chosen ({@link #chosenReason})</li>
 *   <li>Which candidates were considered and why each was rejected
 *       ({@link #rejectedCandidates})</li>
 *   <li>Per-constraint score contributions ({@link #constraintBreakdown})</li>
 * </ul>
 */
@Getter
@Builder
public class AssignmentExplanation {

    private final int slotId;
    private final Integer staffId;
    private final LocalDate workDate;
    private final String shiftTypeId;

    /** High-level reason for the chosen assignment (e.g. "only eligible staff",
     *  "best fairness delta", "tie-break by load"). */
    private final String chosenReason;

    /** Constraints that fired during evaluation (hard + soft). */
    private final List<ConstraintContribution> constraintBreakdown;

    /** Up to N considered candidates that were rejected, with reasons. */
    private final List<RejectedCandidate> rejectedCandidates;
}