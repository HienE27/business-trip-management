package com.hospital.scheduler.whatif.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Scenario response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioResponse {

    Integer id;
    String name;
    String description;
    boolean baseline;
    Integer sourcePeriodId;
    Map<String, Object> configOverrides;
    String status;
    ScenarioResult results;
    Long simulationDurationMs;
    String sessionKey;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Integer createdBy;
    Integer parentScenarioId;
    String tags;

    /**
     * Simulation results.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioResult {
        Double coverage;
        Double fairness;
        Integer violations;
        Integer iterations;
        Long runtimeMs;
        Double score;
        Map<String, Double> metrics;
    }
}
