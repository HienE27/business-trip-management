package com.hospital.scheduler.whatif.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * Comparison between scenarios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioComparison {

    /**
     * Baseline scenario ID.
     */
    Integer baselineId;

    /**
     * Compared scenario ID.
     */
    Integer comparedId;

    /**
     * Comparison metrics.
     */
    ComparisonMetrics metrics;

    /**
     * Per-metric comparisons.
     */
    Map<String, MetricChange> changes;

    /**
     * Summary recommendation.
     */
    String recommendation;

    /**
     * Comparison metrics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonMetrics {
        Double baselineCoverage;
        Double comparedCoverage;
        Double coverageDelta;

        Double baselineFairness;
        Double comparedFairness;
        Double fairnessDelta;

        Integer baselineViolations;
        Integer comparedViolations;
        Integer violationsDelta;

        Long baselineRuntime;
        Long comparedRuntime;
        Double runtimeDelta;

        Double baselineScore;
        Double comparedScore;
        Double scoreDelta;
    }

    /**
     * Change for a single metric.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricChange {
        String metricName;
        String changeType; // IMPROVED, DEGRADED, NEUTRAL
        Double absoluteChange;
        Double percentChange;
        String impact; // HIGH, MEDIUM, LOW
        String description;
    }
}
