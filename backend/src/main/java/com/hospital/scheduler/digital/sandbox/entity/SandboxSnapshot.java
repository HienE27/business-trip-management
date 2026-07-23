package com.hospital.scheduler.digital.sandbox.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * SandboxSnapshot captures the state of a simulation at a specific iteration.
 *
 * <p>Key design:
 * <ul>
 *   <li>Snapshots are immutable once created</li>
 *   <li>Only schedule assignments are stored (not full WorkingSolution)</li>
 *   <li>Used for replay, rollback, and decision graph</li>
 *   <li>Stored as JSON blob for efficient retrieval</li>
 * </ul>
 *
 * <p>Storage optimization:
 * <ul>
 *   <li>First snapshot (iteration 0) stores full state</li>
 *   <li>Subsequent snapshots store only deltas</li>
 *   <li>Compression applied for large snapshots</li>
 * </ul>
 */
@Entity
@Table(name = "sandbox_snapshot",
    indexes = {
        @Index(name = "idx_snapshot_session", columnList = "session_id"),
        @Index(name = "idx_snapshot_iteration", columnList = "session_id, iteration")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SandboxSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent sandbox session.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SandboxSession session;

    /**
     * Iteration number (0 = initial state).
     */
    @Column(name = "iteration", nullable = false)
    private Integer iteration;

    /**
     * Score at this iteration.
     */
    @Column(name = "score", nullable = false)
    private Double score;

    /**
     * Coverage rate at this iteration.
     */
    @Column(name = "coverage_rate")
    private Double coverageRate;

    /**
     * Fairness CV at this iteration.
     */
    @Column(name = "fairness_cv")
    private Double fairnessCv;

    /**
     * Number of violations at this iteration.
     */
    @Column(name = "violations")
    private Integer violations;

    /**
     * Move type: ASSIGN, UNASSIGN, SWAP, CHANGE
     */
    @Column(name = "move_type", length = 32)
    private String moveType;

    /**
     * Staff ID involved in the move (nullable for UNASSIGN).
     */
    @Column(name = "staff_id")
    private Integer staffId;

    /**
     * Slot ID involved in the move.
     */
    @Column(name = "slot_id")
    private Integer slotId;

    /**
     * Target staff ID (for SWAP/CHANGE moves).
     */
    @Column(name = "target_staff_id")
    private Integer targetStaffId;

    /**
     * Score delta from this move.
     */
    @Column(name = "score_delta")
    private Double scoreDelta;

    /**
     * Whether this move was accepted by the acceptance strategy.
     */
    @Column(name = "accepted")
    private Boolean accepted;

    /**
     * Acceptance probability (for probabilistic strategies).
     */
    @Column(name = "acceptance_probability")
    private Double acceptanceProbability;

    /**
     * Current temperature (for simulated annealing).
     */
    @Column(name = "temperature")
    private Double temperature;

    /**
     * Tabu tenure remaining (for tabu search).
     */
    @Column(name = "tabu_remaining")
    private Integer tabuRemaining;

    /**
     * Constraint violations added/removed by this move.
     * Stored as JSON: {"hardAdded": [], "hardRemoved": [], "softAdded": [], "softRemoved": []}
     */
    @Column(name = "constraint_deltas", columnDefinition = "TEXT")
    private String constraintDeltas;

    /**
     * Full state as JSON (only for iteration 0 or key checkpoints).
     */
    @Column(name = "state_json", columnDefinition = "LONGTEXT")
    private String stateJson;

    /**
     * Delta from previous snapshot (JSON).
     */
    @Column(name = "delta_json", columnDefinition = "LONGTEXT")
    private String deltaJson;

    /**
     * Timestamp when this snapshot was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Memory usage in bytes (for storage monitoring).
     */
    @Column(name = "memory_bytes")
    private Long memoryBytes;

    /**
     * Whether this is a key checkpoint (e.g., every 100 iterations).
     */
    @Column(name = "is_checkpoint")
    @Builder.Default
    private Boolean isCheckpoint = false;

    /**
     * Additional metadata (JSON).
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    // ─── Helper methods ─────────────────────────────────────────────────────

    /**
     * Check if this snapshot represents an accepted move.
     */
    public boolean isAcceptedMove() {
        return Boolean.TRUE.equals(accepted);
    }

    /**
     * Check if this is the initial state snapshot.
     */
    public boolean isInitialState() {
        return iteration == 0;
    }

    /**
     * Get move description for display.
     */
    public String getMoveDescription() {
        if (isInitialState()) {
            return "Initial state";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Move #").append(iteration);

        if (moveType != null) {
            sb.append(": ").append(moveType);
        }

        if (slotId != null) {
            sb.append(" Slot[").append(slotId).append("]");
        }

        if (staffId != null) {
            sb.append(" Staff[").append(staffId).append("]");
        }

        if (accepted != null) {
            sb.append(" (").append(accepted ? "✓" : "✗").append(")");
        }

        return sb.toString();
    }
}
