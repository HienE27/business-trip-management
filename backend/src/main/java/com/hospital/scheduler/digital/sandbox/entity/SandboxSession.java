package com.hospital.scheduler.digital.sandbox.entity;

import com.hospital.scheduler.digital.sandbox.domain.SandboxStatus;
import com.hospital.scheduler.digital.sandbox.domain.SimulationMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * SandboxSession represents an isolated execution context for scheduling simulations.
 *
 * <p>Key design principles:
 * <ul>
 *   <li>Sandbox is an EXECUTION CONTEXT, not just a data copy</li>
 *   <li>Only schedules are cloned; reference data (Staff, ShiftType, etc.) is shared read-only</li>
 *   <li>Snapshots are taken at each iteration for replay/rollback</li>
 *   <li>Session can be promoted to production or discarded</li>
 * </ul>
 *
 * <p>Lifecycle:
 * <pre>
 * CREATED → CLONING → READY → RUNNING ⇄ PAUSED → COMPLETED
 *                 ↓                           ↓
 *              FAILED                      EXPIRED
 *                                             ↓
 *                                         PROMOTED
 * </pre>
 */
@Entity
@Table(name = "sandbox_session",
    indexes = {
        @Index(name = "idx_sandbox_status", columnList = "status"),
        @Index(name = "idx_sandbox_user", columnList = "created_by"),
        @Index(name = "idx_sandbox_expires", columnList = "expires_at"),
        @Index(name = "idx_sandbox_period", columnList = "source_period_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SandboxSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique session identifier for API access.
     */
    @Column(name = "session_key", unique = true, nullable = false, length = 64)
    private String sessionKey;

    /**
     * Human-readable name for the session.
     */
    @Column(name = "name", length = 128)
    private String name;

    /**
     * Current lifecycle status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SandboxStatus status;

    /**
     * Simulation mode determining scheduler behavior.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "simulation_mode", length = 32)
    private SimulationMode simulationMode;

    /**
     * User who created this session.
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * When this session expires and can be auto-cleaned.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Source period that was cloned for this sandbox.
     */
    @Column(name = "source_period_id")
    private Integer sourcePeriodId;

    /**
     * Profile ID used for this simulation.
     */
    @Column(name = "profile_id")
    private Long profileId;

    /**
     * Current snapshot ID (points to latest snapshot for replay).
     */
    @Column(name = "current_snapshot_id")
    private Long currentSnapshotId;

    /**
     * Total iterations executed in this session.
     */
    @Column(name = "iterations")
    @Builder.Default
    private Integer iterations = 0;

    /**
     * Current best score achieved.
     */
    @Column(name = "best_score")
    private Double bestScore;

    /**
     * Score at the start (before simulation).
     */
    @Column(name = "initial_score")
    private Double initialScore;

    /**
     * Coverage rate at the end (0-100).
     */
    @Column(name = "coverage_rate")
    private Double coverageRate;

    /**
     * Fairness (CV) at the end.
     */
    @Column(name = "fairness_cv")
    private Double fairnessCv;

    /**
     * Number of constraint violations.
     */
    @Column(name = "violations")
    @Builder.Default
    private Integer violations = 0;

    /**
     * Total runtime in seconds.
     */
    @Column(name = "runtime_seconds")
    @Builder.Default
    private Integer runtimeSeconds = 0;

    /**
     * Session error message if failed.
     */
    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    /**
     * TTL in hours (default 24h).
     */
    @Column(name = "ttl_hours")
    @Builder.Default
    private Integer ttlHours = 24;

    /**
     * Whether this session is pinned (won't auto-cleanup).
     */
    @Column(name = "is_pinned")
    @Builder.Default
    private Boolean isPinned = false;

    /**
     * Additional notes or description.
     */
    @Column(name = "description", length = 512)
    private String description;

    // ─── Status transition helpers ──────────────────────────────────────────

    public boolean canStart() {
        return status == SandboxStatus.READY || status == SandboxStatus.PAUSED;
    }

    public boolean canPause() {
        return status == SandboxStatus.RUNNING;
    }

    public boolean canResume() {
        return status == SandboxStatus.PAUSED;
    }

    public boolean canPromote() {
        return status == SandboxStatus.COMPLETED || status == SandboxStatus.PAUSED;
    }

    public boolean isTerminal() {
        return status == SandboxStatus.PROMOTED
            || status == SandboxStatus.DELETED
            || status == SandboxStatus.EXPIRED;
    }

    public boolean isActive() {
        return status == SandboxStatus.RUNNING
            || status == SandboxStatus.PAUSED
            || status == SandboxStatus.READY;
    }
}
