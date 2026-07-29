package com.hospital.scheduler.service.scheduling;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LoadScoreCalculator}.
 *
 * <p>Phase 5D: lock in the weighting contract so that the rebalance algorithm
 * can rely on it. The weights here are used by
 * {@link PostAssignmentOptimizer} to compare overloaded vs. underloaded staff.
 */
class LoadScoreCalculatorTest {

    @Test
    void weightOf_knownShiftTypes() {
        assertEquals(2.0, LoadScoreCalculator.weightOf("L01"));
        assertEquals(1.0, LoadScoreCalculator.weightOf("L02"));
        assertEquals(1.0, LoadScoreCalculator.weightOf("L03"));
        assertEquals(1.5, LoadScoreCalculator.weightOf("L04"));
    }

    @Test
    void weightOf_unknownShiftTypeDefaultsToOne() {
        assertEquals(1.0, LoadScoreCalculator.weightOf("L99"));
        assertEquals(1.0, LoadScoreCalculator.weightOf(""));
        assertEquals(1.0, LoadScoreCalculator.weightOf(null));
    }

    @Test
    void loadFromCounts_emptyOrNullReturnsZero() {
        assertEquals(0.0, LoadScoreCalculator.loadFromCounts(null));
        assertEquals(0.0, LoadScoreCalculator.loadFromCounts(Map.of()));
    }

    @Test
    void loadFromCounts_sumsWeightedCounts() {
        // 2 L01 + 3 L02 + 1 L04 = 2*2.0 + 3*1.0 + 1*1.5 = 4.0 + 3.0 + 1.5 = 8.5
        Map<String, Long> counts = new HashMap<>();
        counts.put("L01", 2L);
        counts.put("L02", 3L);
        counts.put("L04", 1L);

        assertEquals(8.5, LoadScoreCalculator.loadFromCounts(counts), 0.0001);
    }

    @Test
    void loadFromCounts_ignoresUnknownKeys() {
        // L01=2, L99=5 (ignored default 1.0), L02=1 = 2*2.0 + 5*1.0 + 1*1.0 = 10.0
        Map<String, Long> counts = new HashMap<>();
        counts.put("L01", 2L);
        counts.put("L99", 5L);
        counts.put("L02", 1L);

        assertEquals(10.0, LoadScoreCalculator.loadFromCounts(counts), 0.0001);
    }

    @Test
    void varianceOfLoads_balancedReturnsZero() {
        Map<Integer, Double> loads = Map.of(1, 5.0, 2, 5.0, 3, 5.0);
        assertEquals(0.0, LoadScoreCalculator.varianceOfLoads(loads), 0.0001);
    }

    @Test
    void varianceOfLoads_imbalancedHasPositiveVariance() {
        Map<Integer, Double> loads = Map.of(1, 10.0, 2, 2.0, 3, 2.0);
        // mean = (10+2+2)/3 = 14/3
        // variance = ((10-14/3)^2 + (2-14/3)^2 + (2-14/3)^2) / 3
        //         = ((16/3)^2 + 2*(8/3)^2) / 3
        //         = (256/9 + 128/9) / 3 = 384/9/3 = 128/9 ≈ 14.222
        double variance = LoadScoreCalculator.varianceOfLoads(loads);
        assertTrue(variance > 14.0, "expected variance > 14 but got " + variance);
        assertTrue(variance < 15.0, "expected variance < 15 but got " + variance);
    }

    @Test
    void varianceOfLoads_emptyOrNullReturnsZero() {
        assertEquals(0.0, LoadScoreCalculator.varianceOfLoads(null));
        assertEquals(0.0, LoadScoreCalculator.varianceOfLoads(Map.of()));
    }
}
