package com.hospital.scheduler.scheduling.domain;

import lombok.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Core Schedule domain object.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    private Integer id;
    private Integer periodId;
    private Map<String, Object> metadata;
    private List<ScheduleAssignment> assignments;
    private List<ConstraintResult> constraintResults;
    private double coverageRate;
    private double fairnessMetric;
    private double weekendFairnessMetric;
    private double bestScore;

    public ScheduleMetadata getMetadata() {
        return ScheduleMetadata.builder()
                .iterations(metadata != null ? (Integer) metadata.getOrDefault("iterations", 0) : 0)
                .acceptedMoves(metadata != null ? (Integer) metadata.getOrDefault("acceptedMoves", 0) : 0)
                .rejectedMoves(metadata != null ? (Integer) metadata.getOrDefault("rejectedMoves", 0) : 0)
                .timeToFirstSolutionMs(metadata != null ? ((Number) metadata.getOrDefault("timeToFirstSolutionMs", 0)).longValue() : 0)
                .build();
    }

    public List<ConstraintResult> getConstraintResults() {
        return constraintResults != null ? constraintResults : new ArrayList<>();
    }

    @lombok.Value
    @lombok.Builder
    public static class ScheduleMetadata {
        private int iterations;
        private int acceptedMoves;
        private int rejectedMoves;
        private long timeToFirstSolutionMs;
    }
}
