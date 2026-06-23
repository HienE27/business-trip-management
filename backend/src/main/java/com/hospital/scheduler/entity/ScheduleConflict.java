package com.hospital.scheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_conflict", uniqueConstraints = {
    @UniqueConstraint(name = "uk_schedule_unresolved", columnNames = {"schedule_id", "is_resolved"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_type", nullable = false)
    @Builder.Default
    private ConflictType conflictType = ConflictType.OTHER;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private Boolean isResolved = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private Staff resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ConflictType {
        LEAVE_CONFLICT,
        MAX_SHIFT_EXCEEDED,
        SPECIALTY_MISMATCH,
        REQUIREMENT_NOT_MET,
        DUPLICATE_ASSIGNMENT,
        COMPENSATION_CONFLICT,
        OTHER
    }
}
