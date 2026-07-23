package com.hospital.scheduler.digital.sandbox.dto;

/**
 * Timeline event types for sandbox simulation.
 *
 * <p>These events are emitted during simulation and streamed to clients via SSE.
 */
public enum TimelineEventType {

    /**
     * Simulation started.
     */
    STARTED,

    /**
     * Snapshot created (periodic checkpoint).
     */
    SNAPSHOT,

    /**
     * Move evaluation started.
     */
    MOVE_EVALUATING,

    /**
     * Move accepted.
     */
    MOVE_ACCEPTED,

    /**
     * Move rejected.
     */
    MOVE_REJECTED,

    /**
     * Score improved.
     */
    SCORE_IMPROVED,

    /**
     * Tabu hit (move in tabu list).
     */
    TABU_HIT,

    /**
     * Diversification triggered.
     */
    DIVERSIFIED,

    /**
     * Best score updated.
     */
    BEST_UPDATED,

    /**
     * Simulation paused.
     */
    PAUSED,

    /**
     * Simulation resumed.
     */
    RESUMED,

    /**
     * Simulation completed (success).
     */
    COMPLETED,

    /**
     * Simulation failed.
     */
    FAILED,

    /**
     * Simulation cancelled.
     */
    CANCELLED,

    /**
     * Progress update (periodic heartbeat).
     */
    PROGRESS
}
