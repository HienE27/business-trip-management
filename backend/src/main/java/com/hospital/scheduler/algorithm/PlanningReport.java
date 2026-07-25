package com.hospital.scheduler.algorithm;

import java.util.List;
import java.util.Map;

/**
 * Planning Report — Auto Configuration Planner output.
 *
 * <p>Produced by {@code PlannerService} before scheduling runs.
 * Contains theoretical ceilings, fairness feasibility analysis,
 * algorithm recommendation, and expected result estimates.
 */
public record PlanningReport(
        CapacityAnalysis capacity,
        ConstraintAnalysis constraint,
        List<FairnessAnalysis> fairnessOptions,
        AlgorithmRecommendation algorithm,
        ParameterRecommendation parameters,
        ExpectedResult expected,
        List<String> warnings
) {
    public record CapacityAnalysis(
            int totalStaff,
            int periodDays,
            int totalDemand,
            int maxCapacity,
            double coverageCeiling  // 0–100%
    ) {}

    public record ConstraintAnalysis(
            double leaveDensity,          // 0–1
            double l01AdjacencyImpact,    // 0–1
            double weeklyCapTightness,    // 0–1+ ( >1 = overshoot)
            double overallFeasibility,    // 0–100%
            String riskLevel              // "LOW" | "MEDIUM" | "HIGH"
    ) {}

    public record FairnessAnalysis(
            String type,               // "INTRA_TYPE" | "INTER_TYPE" | "CROSS_SPECIALTY"
            String label,              // "Intra-type fairness" | "Inter-type balance" | "Cross-specialty balance"
            double feasibility,        // 0–100%: khả thi không
            double expectedFairness,   // 0–100%: fairness kỳ vọng
            double coverageImpact,     // % coverage bị ảnh hưởng (âm = giảm)
            String constraintRisk,     // "LOW" | "MEDIUM" | "HIGH"
            String description,        // Mô tả ngắn
            int starRating             // 1–5 sao
    ) {}

    public record AlgorithmRecommendation(
            String algorithm,          // "BEAM_SEARCH" | "ENHANCED_GREEDY" | ...
            String rationale,
            List<String> alternatives
    ) {}

    public record ParameterRecommendation(
            int beamWidth,
            int rebalanceRounds,
            double weekendWeight,
            double coverageWeight,
            double fairnessWeight,
            double constraintWeight,
            int maxShiftsPerStaff,
            String arrangementMode,
            /** Global config param key → relevant (true) or ignored (false) for the recommended algorithm. */
            Map<String, Boolean> paramRelevance
    ) {}

    public record ExpectedResult(
            double coverage,
            double constraintScore,
            double fairnessScore,
            double qualityScore
    ) {}
}
