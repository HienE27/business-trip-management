package com.hospital.scheduler.digital.sandbox.decision;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single decision node in the decision graph.
 *
 * <p>A decision node captures:
 * <ul>
 *   <li>Which staff candidate is being evaluated</li>
 *   <li>The iteration and slot context</li>
 *   <li>The decision outcome (tried, accepted, rejected)</li>
 *   <li>The reason for rejection if applicable</li>
 *   <li>Score delta from this candidate</li>
 * </ul>
 */
@Value
@Builder
public class DecisionNode {

    /**
     * Unique node ID.
     */
    UUID id;

    /**
     * Iteration number.
     */
    int iteration;

    /**
     * Slot being filled.
     */
    int slotId;

    /**
     * Staff candidate being evaluated.
     */
    Integer candidateStaffId;

    /**
     * Staff name (denormalized for display).
     */
    String candidateStaffName;

    /**
     * Decision status.
     */
    DecisionStatus status;

    /**
     * Reason for rejection (if rejected).
     */
    String rejectionReason;

    /**
     * Constraint that caused rejection (e.g., "BR03", "BR05").
     */
    String violatedConstraint;

    /**
     * Score delta from evaluating this candidate.
     */
    double scoreDelta;

    /**
     * Coverage delta.
     */
    double coverageDelta;

    /**
     * Fairness delta.
     */
    double fairnessDelta;

    /**
     * IDs of child nodes (alternatives tried).
     */
    List<UUID> children;

    /**
     * Parent node ID.
     */
    UUID parentId;

    /**
     * Node depth in the graph.
     */
    int depth;

    /**
     * Time spent evaluating this node (ms).
     */
    long evaluationTimeMs;

    /**
     * Additional metadata.
     */
    Map<String, Object> metadata;
}
