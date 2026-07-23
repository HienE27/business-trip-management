package com.hospital.scheduler.digital.sandbox.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ReplayFrame represents a single step in the replay.
 *
 * <p>Contains all information needed to visualize a move:
 * <ul>
 *   <li>Current metrics (score, coverage, fairness, violations)</li>
 *   <li>Move details (type, staff, slot)</li>
 *   <li>Acceptance reason</li>
 *   <li>Changed assignments</li>
 * </ul>
 */
@Value
@Builder
public class ReplayFrame {

    /**
     * Frame ID (matches iteration).
     */
    int iteration;

    /**
     * Frame timestamp.
     */
    LocalDateTime timestamp;

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
     * Whether the move was accepted.
     */
    boolean accepted;

    /**
     * Acceptance/rejection reason.
     */
    String reason;

    /**
     * Score delta from this move.
     */
    double scoreDelta;

    /**
     * Coverage delta from this move.
     */
    double coverageDelta;

    /**
     * Staff involved in the move.
     */
    MoveStaffInfo staff;

    /**
     * Target staff (for SWAP/CHANGE).
     */
    MoveStaffInfo targetStaff;

    /**
     * Slot information.
     */
    SlotInfo slot;

    /**
     * Assignments changed by this move.
     */
    List<AssignmentChange> changedAssignments;

    /**
     * Constraint deltas from this move.
     */
    Map<String, ConstraintDelta> constraintDeltas;

    /**
     * Time taken for this iteration (ms).
     */
    long durationMs;

    /**
     * Whether this is a checkpoint frame.
     */
    boolean isCheckpoint;

    @Value
    @Builder
    public static class MoveStaffInfo {
        Integer id;
        String name;
        String staffCode;
    }

    @Value
    @Builder
    public static class SlotInfo {
        Integer id;
        String date;
        String shiftType;
        String requirementName;
    }

    @Value
    @Builder
    public static class AssignmentChange {
        Integer slotId;
        Integer previousStaffId;
        String previousStaffName;
        Integer newStaffId;
        String newStaffName;
        String changeType; // ADD, REMOVE, REPLACE
    }

    @Value
    @Builder
    public static class ConstraintDelta {
        String constraintId;
        String constraintName;
        int previousViolations;
        int newViolations;
        int delta;
    }
}
