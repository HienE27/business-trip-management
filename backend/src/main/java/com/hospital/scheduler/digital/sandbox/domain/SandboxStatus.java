package com.hospital.scheduler.digital.sandbox.domain;

/**
 * Lifecycle states for a sandbox session.
 *
 * <pre>
 * CREATED → CLONING → READY → RUNNING ⇄ PAUSED → COMPLETED
 *                 ↓                           ↓
 *              FAILED                      EXPIRED
 *                                             ↓
 *                                         PROMOTED
 *                                             ↓
 *                                         DELETED
 * </pre>
 */
public enum SandboxStatus {
    /** Session created but clone not started. */
    CREATED,

    /** Clone in progress. */
    CLONING,

    /** Clone completed, ready to run. */
    READY,

    /** Simulation running. */
    RUNNING,

    /** Simulation paused. */
    PAUSED,

    /** Simulation completed (success or max iterations). */
    COMPLETED,

    /** Clone or simulation failed. */
    FAILED,

    /** Session expired (TTL). */
    EXPIRED,

    /** Results promoted to production. */
    PROMOTED,

    /** Session deleted. */
    DELETED
}
