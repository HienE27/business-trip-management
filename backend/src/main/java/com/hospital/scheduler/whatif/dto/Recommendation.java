package com.hospital.scheduler.whatif.dto;

import lombok.*;
import java.util.List;

/**
 * Recommendations for configuration optimization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {

    /**
     * Recommendation ID.
     */
    Integer id;

    /**
     * Priority (1 = highest).
     */
    Integer priority;

    /**
     * Category.
     */
    String category;

    /**
     * Recommendation title.
     */
    String title;

    /**
     * Description.
     */
    String description;

    /**
     * Current value.
     */
    Object currentValue;

    /**
     * Recommended value.
     */
    Object recommendedValue;

    /**
     * Expected impact.
     */
    ExpectedImpact expectedImpact;

    /**
     * Confidence level.
     */
    Double confidence;

    /**
     * Reason/rationale.
     */
    String reason;

    /**
     * Risk assessment.
     */
    RiskAssessment riskAssessment;

    /**
     * Expected impact metrics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpectedImpact {
        Double coverageDelta;
        Double fairnessDelta;
        Integer violationsDelta;
        Double scoreDelta;
        Long runtimeDelta;
    }

    /**
     * Risk assessment.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        String level; // HIGH, MEDIUM, LOW
        List<String> risks;
        List<String> mitigations;
    }
}
