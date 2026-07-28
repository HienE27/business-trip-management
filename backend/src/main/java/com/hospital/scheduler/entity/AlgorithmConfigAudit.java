package com.hospital.scheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit log cho mọi thay đổi AlgorithmConfig.
 * Ghi lại: paramKey, oldValue, newValue, action, user, timestamp.
 * Bảng này chỉ INSERT, không UPDATE/DELETE — dùng để truy vết lịch sử.
 */
@Entity
@Table(name = "algorithm_config_audit", indexes = {
        @Index(name = "idx_audit_param_key", columnList = "param_key"),
        @Index(name = "idx_algcfg_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_user", columnList = "changed_by")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgorithmConfigAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "param_key", nullable = false, length = 50)
    private String paramKey;

    @Column(name = "old_value", length = 2000)
    private String oldValue;

    @Column(name = "new_value", nullable = false, length = 2000)
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private Action action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private Staff changedBy;

    @Column(name = "changed_by_username", length = 100)
    private String changedByUsername;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Action {
        CREATE, UPDATE, DELETE, BULK_SYNC, BULK_UPDATE
    }
}