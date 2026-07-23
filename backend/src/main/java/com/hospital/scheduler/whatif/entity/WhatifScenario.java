package com.hospital.scheduler.whatif.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a what-if scenario for simulation.
 *
 * <p>A scenario contains:
 * <ul>
 *   <li>Name and description</li>
 *   <li>Source period ID (the schedule to modify)</li>
 *   <li>Configuration overrides</li>
 *   <li>Results after running</li>
 * </ul>
 */
@Entity
@Table(name = "whatif_scenario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatifScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Scenario name.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Scenario description.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Whether this is the baseline scenario.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean baseline = false;

    /**
     * Source period ID to simulate.
     */
    @Column(name = "source_period_id")
    private Integer sourcePeriodId;

    /**
     * Configuration overrides as JSON.
     */
    @Column(columnDefinition = "JSON")
    private String configOverrides;

    /**
     * Scenario status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScenarioStatus status = ScenarioStatus.DRAFT;

    /**
     * Result metrics after simulation.
     */
    @Column(columnDefinition = "JSON")
    private String results;

    /**
     * Simulation duration in ms.
     */
    @Column(name = "simulation_duration_ms")
    private Long simulationDurationMs;

    /**
     * Session key from sandbox (if simulated).
     */
    @Column(name = "session_key")
    private String sessionKey;

    /**
     * Created timestamp.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Updated timestamp.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Created by user ID.
     */
    @Column(name = "created_by")
    private Integer createdBy;

    /**
     * Parent scenario ID (for comparison).
     */
    @Column(name = "parent_scenario_id")
    private Integer parentScenarioId;

    /**
     * Tags for organization.
     */
    @Column
    private String tags;

    /**
     * Scenario status enum.
     */
    public enum ScenarioStatus {
        DRAFT,
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
