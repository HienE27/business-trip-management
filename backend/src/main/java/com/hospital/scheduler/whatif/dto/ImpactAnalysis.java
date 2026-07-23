package com.hospital.scheduler.whatif.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * Impact analysis for configuration changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactAnalysis {

    /**
     * Scenario ID.
     */
    Integer scenarioId;

    /**
     * Config changes analyzed.
     */
    List<ConfigImpact> configImpacts;

    /**
     * Overall impact summary.
     */
    ImpactSummary summary;

    /**
     * Predicted metrics changes.
     */
    PredictedMetrics predictedMetrics;

    /**
     * Risks and warnings.
     */
    List<ImpactWarning> warnings;

    /**
     * Config change impact.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigImpact {
        String configKey;
        Object previousValue;
        Object newValue;
        String category; // TABU, COVERAGE, FAIRNESS, etc.
        ImpactLevel impactLevel; // HIGH, MEDIUM, LOW
        Double impactScore;
        List<String> affectedMetrics;
    }

    /**
     * Overall impact summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactSummary {
        String overallImpact; // POSITIVE, NEGATIVE, NEUTRAL, MIXED
        Double confidenceScore; // 0-1
        String summary;
        List<String> keyFindings;
    }

    /**
     * Predicted metrics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictedMetrics {
        Double coverage;
        Double coverageDelta;
        Double coverageConfidence;

        Double fairness;
        Double fairnessDelta;
        Double fairnessConfidence;

        Integer violations;
        Integer violationsDelta;
        Double violationsConfidence;

        Double score;
        Double scoreDelta;
        Double scoreConfidence;

        Long estimatedRuntime;
        Double runtimeDelta;
    }

    /**
     * Impact warning.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactWarning {
        String warningType; // RISK, CONSTRAINT, PERFORMANCE
        String message;
        String severity; // HIGH, MEDIUM, LOW
        String affectedMetric;
    }

    /**
     * Impact level enum.
     */
    public enum ImpactLevel {
        HIGH,
        MEDIUM,
        LOW
    }
}
