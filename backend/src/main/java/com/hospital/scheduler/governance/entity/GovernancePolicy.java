package com.hospital.scheduler.governance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Policy entity for rule-based policies.
 *
 * <p>Allows administrators to define custom rules without code changes:
 * <pre>
 * IF staff = "A" AND day = Saturday AND month = 8
 * THEN Forbidden
 * </pre>
 */
@Entity
@Table(name = "governance_policy", indexes = {
    @Index(name = "idx_policy_type", columnList = "policy_type"),
    @Index(name = "idx_policy_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GovernancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Policy name.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Policy description.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Policy type.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false)
    private PolicyType policyType;

    /**
     * Policy condition (JSON).
     */
    @Column(name = "condition_json", columnDefinition = "JSON")
    private String conditionJson;

    /**
     * Policy action when triggered.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private PolicyAction action;

    /**
     * Action value (e.g., penalty value).
     */
    @Column(name = "action_value")
    private String actionValue;

    /**
     * Priority (lower = higher priority).
     */
    @Column
    @Builder.Default
    private Integer priority = 100;

    /**
     * Whether policy is active.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Whether to apply to all staff or specific.
     */
    @Column(name = "is_global", nullable = false)
    @Builder.Default
    private boolean global = false;

    /**
     * Target staff IDs (JSON array).
     */
    @Column(name = "target_staff_ids", columnDefinition = "JSON")
    private String targetStaffIds;

    /**
     * Target shift types (JSON array).
     */
    @Column(name = "target_shift_types", columnDefinition = "JSON")
    private String targetShiftTypes;

    /**
     * Effective from date.
     */
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    /**
     * Effective to date.
     */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    /**
     * Created by user.
     */
    @Column(name = "created_by")
    private Integer createdBy;

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
     * Last evaluation timestamp.
     */
    @Column(name = "last_evaluated_at")
    private LocalDateTime lastEvaluatedAt;

    /**
     * Times triggered.
     */
    @Column(name = "trigger_count")
    @Builder.Default
    private Integer triggerCount = 0;

    /**
     * Policy types.
     */
    public enum PolicyType {
        // Staff restrictions
        STAFF_FORBIDDEN_SHIFT,
        STAFF_REQUIRED_SHIFT,
        STAFF_MAX_SHIFTS,

        // Time-based rules
        TIME_NO_WORK,
        TIME_MIN_GAP,
        TIME_MAX_CONSECUTIVE,

        // Fairness rules
        FAIRNESS_MAX_BALANCE,
        FAIRNESS_WEEKEND_LIMIT,

        // Compliance rules
        COMPLIANCE_LABOR_LAW,
        COMPLIANCE_OVERTIME,
        COMPLIANCE_REST_PERIOD,

        // Custom
        CUSTOM
    }

    /**
     * Policy actions.
     */
    public enum PolicyAction {
        // Hard constraints
        FORBIDDEN,
        WARN,
        REQUIRE_APPROVAL,

        // Soft constraints
        PENALTY,
        PREFERENCE_BOOST,
        PREFERENCE_REDUCE,

        // Notifications
        NOTIFY_SUPERVISOR,
        NOTIFY_ADMIN,

        // Logging
        LOG_ONLY
    }
}
