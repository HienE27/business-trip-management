package com.hospital.scheduler.dto.response;

import com.hospital.scheduler.algorithm.AutoGenConfig;

import java.util.List;
import java.util.Map;

/**
 * Commit B (Workflow M07): recommendation response enriched with demand analysis,
 * fairness type, cross-specialty policy, expected metrics, and trade-off warnings.
 */
public record AutoGenConfigRecommendResponse(
    AutoGenConfig recommendedConfig,
    RecommendedRuntimeConfig recommendedRuntimeConfig,
    int totalShiftsExpected,
    int totalStaffTargeted,
    String rationale,
    /** Per-shift-type minPerDay values used to derive the recommendation — keys: L01/L02/L03/L04 */
    Map<String, Integer> demandRatio,
    /**
     * Fairness type applied:
     * - INTRA_TYPE: L01/L02/L03 intra-type fairness via CV; L04 by specialty eligibility.
     * - INTRA_TYPE_WITH_INTER_BALANCE: same as INTRA_TYPE + soft inter-type balance attempt
     *   when demand ratios are roughly similar (not guaranteed equal).
     */
    String fairnessType,
    /** Human-readable cross-specialty policy description. */
    String crossSpecialtyPolicy,
    /** Estimated metrics before running preview. null if cannot estimate. */
    ExpectedMetrics expectedMetrics,
    /** Trade-off and limit warnings. Empty if demand is well-balanced. */
    List<String> warnings
) {
    public record RecommendedRuntimeConfig(int maxShiftsPerStaff) {}

    /** Estimated quality metrics derived from recommendation parameters. */
    public record ExpectedMetrics(
        /** Estimated minimum achievable coverage (0–100). Derived from minPerDay vs total eligible capacity. */
        Double estimatedCoverageMin,
        /** Estimated fairness score (0–100) based on target CV vs worst CV. */
        Double estimatedFairnessScore,
        /** Estimated overall quality score (0–100) weighted average. */
        Double estimatedQualityScore,
        /** Target CV used in the fairness computation. */
        double targetCv,
        /** Worst acceptable CV used in the fairness computation. */
        double worstCv
    ) {}
}
