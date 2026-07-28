package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.algorithm.scoring.ShiftTypeWeights;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes balance score from a generated schedule list using CV
 * (coefficient of variation) of total weighted volume per staff.
 *
 * L01 weight=2 (24h shift + compensation day), L02/L03/L04 weight=1.
 * This ensures the score reflects actual calendar-day workload, not
 * raw shift count — preventing unfair concentration of L01 shifts
 * on a subset of staff.
 */
@Slf4j
@Component
public class BalanceScoreCalculator {

    public BigDecimal calculateBalanceScore(List<Schedule> schedules, int totalStaff) {
        if (schedules.isEmpty()) return BigDecimal.ZERO;

        // Per-staff weighted volume
        Map<Integer, Double> weightedVolume = new HashMap<>();
        for (Schedule s : schedules) {
            double w = ShiftTypeWeights.of(s.getShiftType().getId());
            weightedVolume.merge(s.getStaff().getId(), w, Double::sum);
        }

        if (weightedVolume.size() <= 1) {
            log.debug("Balance score 0: only {} staff assigned", weightedVolume.size());
            return BigDecimal.valueOf(0);
        }

        // Pool size = total active staff (including zero-load staff)
        int poolSize = Math.max(totalStaff, weightedVolume.size());
        double totalVolume = weightedVolume.values().stream().mapToDouble(Double::doubleValue).sum();
        double mean = totalVolume / poolSize;

        // Variance: include zero-load staff
        double sumSq = 0.0;
        for (Map.Entry<Integer, Double> e : weightedVolume.entrySet()) {
            double diff = e.getValue() - mean;
            sumSq += diff * diff;
        }
        int zeroCount = poolSize - weightedVolume.size();
        if (zeroCount > 0) {
            sumSq += zeroCount * mean * mean;
        }

        double variance = sumSq / poolSize;
        double stdDev = Math.sqrt(variance);
        double cv = mean > 0 ? (stdDev / mean) * 100 : 0.0;

        double score = Math.max(0, 100 - cv);

        if (cv > 30) {
            log.warn("Balance WARNING: weighted-volume CV={}% > 30%", String.format("%.2f", cv));
        }

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }
}
