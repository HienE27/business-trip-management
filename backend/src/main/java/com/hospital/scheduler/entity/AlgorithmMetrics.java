package com.hospital.scheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "algorithm_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgorithmMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    private SchedulePeriod period;

    @Column(name = "algorithm_type", nullable = false, length = 50)
    private String algorithmType;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "coverage_rate", precision = 5, scale = 2)
    private BigDecimal coverageRate;

    @Column(name = "balance_score", precision = 5, scale = 2)
    private BigDecimal balanceScore;

    @Column(name = "conflict_count", nullable = false)
    @Builder.Default
    private Integer conflictCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
