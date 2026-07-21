package com.hospital.scheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persisted snapshot of a search run's per-constraint match counts. Stores
 * the report as JSON in {@link #reportJson} so the schema stays flexible as
 * new constraint types are added.
 */
@Entity
@Table(name = "algorithm_constraint_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgorithmConstraintReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "period_id", nullable = false)
    private Integer periodId;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "algorithm_type", length = 50, nullable = false)
    private String algorithmType;

    @Column(name = "report_json", columnDefinition = "TEXT", nullable = false)
    private String reportJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
