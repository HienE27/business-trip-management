package com.hospital.scheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "algorithm_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgorithmConfig {

    @Id
    @Column(name = "param_key", length = 50)
    private String paramKey;

    @Column(name = "param_value", nullable = false, length = 2000)
    private String paramValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    @Builder.Default
    private ValueType valueType = ValueType.STRING;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private Staff updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ValueType {
        STRING, NUMBER, BOOLEAN, JSON
    }
}
