package com.hospital.scheduler.digital.sandbox.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Timeline event for sandbox simulation.
 * Used for both live streaming (SSE) and historical replay.
 */
@Value
@Builder
public class TimelineEvent {

    /**
     * Unique event ID.
     */
    Long id;

    /**
     * Event type.
     */
    TimelineEventType eventType;

    /**
     * Iteration number (0 = initial state).
     */
    int iteration;

    /**
     * Event timestamp.
     */
    LocalDateTime timestamp;

    /**
     * Elapsed time since simulation start (ms).
     */
    long elapsedMs;

    /**
     * Current score.
     */
    double score;

    /**
     * Coverage rate (0-100).
     */
    double coverage;

    /**
     * Fairness CV.
     */
    double fairnessCv;

    /**
     * Hard constraint violations.
     */
    int hardViolations;

    /**
     * Soft constraint violations.
     */
    int softViolations;

    /**
     * Move type: ASSIGN, SWAP, CHANGE, UNASSIGN, REPAIR.
     */
    String moveType;

    /**
     * Staff ID involved.
     */
    Integer staffId;

    /**
     * Staff name (denormalized).
     */
    String staffName;

    /**
     * Slot ID involved.
     */
    Integer slotId;

    /**
     * Target staff ID (for SWAP/CHANGE).
     */
    Integer targetStaffId;

    /**
     * Whether the move was accepted.
     */
    Boolean accepted;

    /**
     * Score delta from this move.
     */
    Double scoreDelta;

    /**
     * Constraint deltas: {"hardAdded": [], "hardRemoved": [], "softAdded": [], "softRemoved": []}
     */
    Map<String, Object> constraintDeltas;

    /**
     * Rejection reason (if rejected).
     */
    String rejectionReason;

    /**
     * Additional metadata.
     */
    Map<String, Object> metadata;
}
