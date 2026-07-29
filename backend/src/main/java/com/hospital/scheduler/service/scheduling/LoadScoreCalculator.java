package com.hospital.scheduler.service.scheduling;

import java.util.Map;

/**
 * Phase 5D: Load-aware scoring for fairness rebalance.
 *
 * <p>Replaces raw shift counts with a weighted score so that the rebalance
 * algorithm accounts for shift "heaviness". L01 carries the highest weight
 * because it spans 24h and creates a compensation-day side effect; L04 is
 * weighted higher than L02/L03 because it requires specialty expertise.
 *
 * <p>The weights are intentionally tunable so the algorithm can be tuned
 * later without touching the optimizer code.
 */
public final class LoadScoreCalculator {

    /** L01 — 24h on-call, creates compensation day. */
    public static final double W_L01 = 2.0;

    /** L02 — daytime ward rounds, no side effects. */
    public static final double W_L02 = 1.0;

    /** L03 — service clinic, no side effects. */
    public static final double W_L03 = 1.0;

    /** L04 — specialist clinic, requires specialty match. */
    public static final double W_L04 = 1.5;

    private LoadScoreCalculator() {}

    /**
     * Returns the load weight for a shift type id.
     * Unknown shift types default to 1.0 so the algorithm stays forgiving.
     */
    public static double weightOf(String shiftTypeId) {
        if (shiftTypeId == null) return 1.0;
        return switch (shiftTypeId) {
            case "L01" -> W_L01;
            case "L02" -> W_L02;
            case "L03" -> W_L03;
            case "L04" -> W_L04;
            default -> 1.0;
        };
    }

    /**
     * Compute load score from a per-type count map.
     * load = Σ(count[type] × weight[type])
     */
    public static double loadFromCounts(Map<String, Long> countsByType) {
        if (countsByType == null || countsByType.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Map.Entry<String, Long> e : countsByType.entrySet()) {
            sum += e.getValue() * weightOf(e.getKey());
        }
        return sum;
    }

    /**
     * Variance of load scores across a set of staff. Useful for asserting
     * that a rebalance pass actually moved the distribution closer to flat.
     */
    public static double varianceOfLoads(Map<Integer, Double> loadByStaff) {
        if (loadByStaff == null || loadByStaff.isEmpty()) return 0.0;
        double mean = loadByStaff.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sqSum = 0.0;
        for (double v : loadByStaff.values()) {
            double d = v - mean;
            sqSum += d * d;
        }
        return sqSum / loadByStaff.size();
    }
}
