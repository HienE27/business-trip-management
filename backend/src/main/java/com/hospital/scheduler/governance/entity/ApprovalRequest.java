package com.hospital.scheduler.governance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Approval request entity for multi-level workflow.
 *
 * <p>Supports:
 * <ul>
 *   <li>Draft → Submit → Review → Approved → Applied → Archived</li>
 *   <li>Multiple approval levels</li>
 *   <li>Comments and rejection reasons</li>
 * </ul>
 */
@Entity
@Table(name = "approval_request", indexes = {
    @Index(name = "idx_approval_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_approval_status", columnList = "status"),
    @Index(name = "idx_approval_submitter", columnList = "submitted_by")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Request title/subject.
     */
    @Column(nullable = false)
    private String title;

    /**
     * Request description.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Entity type being approved (Config, Profile, etc.).
     */
    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    /**
     * Entity ID being approved.
     */
    @Column(name = "entity_id")
    private String entityId;

    /**
     * Config version ID (if approving config change).
     */
    @Column(name = "config_version_id")
    private Integer configVersionId;

    /**
     * Current status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.DRAFT;

    /**
     * Approval level (1 = first level, 2 = second level, etc.).
     */
    @Column(name = "approval_level")
    @Builder.Default
    private Integer approvalLevel = 1;

    /**
     * Required approval levels.
     */
    @Column(name = "required_levels")
    @Builder.Default
    private Integer requiredLevels = 1;

    /**
     * User who submitted the request.
     */
    @Column(name = "submitted_by")
    private Integer submittedBy;

    /**
     * Username who submitted.
     */
    @Column(name = "submitted_by_name")
    private String submittedByName;

    /**
     * Submission timestamp.
     */
    @CreationTimestamp
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /**
     * User who reviewed (current level).
     */
    @Column(name = "reviewed_by")
    private Integer reviewedBy;

    /**
     * Username who reviewed.
     */
    @Column(name = "reviewed_by_name")
    private String reviewedByName;

    /**
     * Review timestamp (current level).
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * Review comment.
     */
    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    /**
     * Approval history as JSON.
     */
    @Column(name = "approval_history", columnDefinition = "JSON")
    private String approvalHistory;

    /**
     * Priority (1 = highest).
     */
    @Column
    @Builder.Default
    private Integer priority = 3;

    /**
     * Due date for approval.
     */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    /**
     * Whether approved and applied.
     */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    /**
     * User who applied.
     */
    @Column(name = "applied_by")
    private Integer appliedBy;

    /**
     * Creation timestamp.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Update timestamp.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Approval statuses.
     */
    public enum ApprovalStatus {
        DRAFT,
        SUBMITTED,
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        CHANGES_REQUESTED,
        CANCELLED,
        EXPIRED,
        APPLIED
    }
}
