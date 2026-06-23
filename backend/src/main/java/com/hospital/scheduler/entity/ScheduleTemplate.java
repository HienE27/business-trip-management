package com.hospital.scheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "shift_type_id", length = 10)
    private String shiftTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id")
    private Specialty specialty;

    @Column(name = "required_staff_count", nullable = false)
    @Builder.Default
    private Integer requiredStaffCount = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "source_period_id")
    private Integer sourcePeriodId;

    @Column(name = "algorithm_type", length = 50)
    private String algorithmType;

    @Column(name = "algorithm_config", columnDefinition = "TEXT")
    private String algorithmConfig;

    @Column(name = "template_type", nullable = false, length = 20)
    @Builder.Default
    private String templateType = "PATTERN";

    @Column(name = "generated_schedule_ids", columnDefinition = "TEXT")
    private String generatedScheduleIds;

    @Column(name = "pattern_config", columnDefinition = "TEXT")
    private String patternConfig;

    @Column(name = "source_period_name", length = 100)
    private String sourcePeriodName;
}
