package com.hospital.scheduler.whatif.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * Sensitivity analysis results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitivityAnalysis {

    /**
     * Parameter analyzed.
     */
    String parameterName;

    /**
     * Parameter range.
     */
    List<Double> parameterValues;

    /**
     * Results per value.
     */
    List<SensitivityResult> results;

    /**
     * Optimal value.
     */
    Double optimalValue;

    /**
     * Sensitivity score (how much output changes with input).
     */
    Double sensitivityScore;

    /**
     * Correlation coefficient.
     */
    Double correlation;

    /**
     * Analysis summary.
     */
    String summary;

    /**
     * Result for a single parameter value.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensitivityResult {
        Double parameterValue;
        Double coverage;
        Double fairness;
        Integer violations;
        Double score;
        Long runtimeMs;
    }
}
