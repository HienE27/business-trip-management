package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.AlgorithmMetricsDTO;
import com.hospital.scheduler.repository.AlgorithmMetricsRepository;
import com.hospital.scheduler.entity.AlgorithmMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for algorithm metrics and performance monitoring.
 * Tracks execution times, coverage rates, and balance scores.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlgorithmMetricsService {

    private final AlgorithmMetricsRepository metricsRepository;

    /**
     * Get summary statistics for all algorithm runs.
     */
    public Map<String, Object> getAlgorithmStatsSummary() {
        List<AlgorithmMetrics> allMetrics = metricsRepository.findAll();
        
        if (allMetrics.isEmpty()) {
            return Map.of(
                "totalRuns", 0,
                "avgExecutionTimeMs", 0,
                "avgCoverageRate", BigDecimal.ZERO,
                "avgBalanceScore", BigDecimal.ZERO
            );
        }

        // Group by algorithm type
        Map<String, List<AlgorithmMetrics>> byAlgorithm = allMetrics.stream()
                .collect(Collectors.groupingBy(AlgorithmMetrics::getAlgorithmType));

        // Calculate stats per algorithm
        Map<String, Map<String, Object>> statsPerAlgorithm = new LinkedHashMap<>();
        byAlgorithm.forEach((algo, metrics) -> {
            int count = metrics.size();
            double avgTime = metrics.stream()
                    .mapToLong(m -> m.getExecutionTimeMs() != null ? m.getExecutionTimeMs() : 0L)
                    .average()
                    .orElse(0);
            BigDecimal avgCoverage = metrics.stream()
                    .map(m -> m.getCoverageRate() != null ? m.getCoverageRate() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            BigDecimal avgBalance = metrics.stream()
                    .map(m -> m.getBalanceScore() != null ? m.getBalanceScore() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            
            statsPerAlgorithm.put(algo, Map.of(
                "runCount", count,
                "avgExecutionTimeMs", (long) avgTime,
                "avgCoverageRate", avgCoverage,
                "avgBalanceScore", avgBalance
            ));
        });

        // Overall stats
        double totalAvgTime = allMetrics.stream()
                .mapToLong(m -> m.getExecutionTimeMs() != null ? m.getExecutionTimeMs() : 0L)
                .average()
                .orElse(0);
        BigDecimal overallAvgCoverage = allMetrics.stream()
                .map(m -> m.getCoverageRate() != null ? m.getCoverageRate() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(allMetrics.size()), 2, RoundingMode.HALF_UP);
        BigDecimal overallAvgBalance = allMetrics.stream()
                .map(m -> m.getBalanceScore() != null ? m.getBalanceScore() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(allMetrics.size()), 2, RoundingMode.HALF_UP);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", allMetrics.size());
        summary.put("avgExecutionTimeMs", (long) totalAvgTime);
        summary.put("avgCoverageRate", overallAvgCoverage);
        summary.put("avgBalanceScore", overallAvgBalance);
        summary.put("byAlgorithm", statsPerAlgorithm);
        
        return summary;
    }

    /**
     * Get the latest metrics for each algorithm type.
     */
    public Map<String, AlgorithmMetricsDTO> getLatestMetricsByType() {
        List<AlgorithmMetrics> allMetrics = metricsRepository.findAll();
        
        Map<String, AlgorithmMetricsDTO> latestByType = new LinkedHashMap<>();
        for (AlgorithmMetrics metric : allMetrics) {
            String algoType = metric.getAlgorithmType();
            if (!latestByType.containsKey(algoType) ||
                    metric.getCreatedAt().isAfter(latestByType.get(algoType).getCreatedAt())) {
                latestByType.put(algoType, toDTO(metric));
            }
        }
        
        return latestByType;
    }

    /**
     * Get algorithm performance trends (last N runs).
     */
    public List<AlgorithmMetricsDTO> getRecentMetrics(int limit) {
        return metricsRepository.findAll().stream()
                .sorted(Comparator.comparing(AlgorithmMetrics::getCreatedAt).reversed())
                .limit(limit)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Calculate performance score for an algorithm type.
     * Higher is better. Score is based on coverage rate and balance score.
     */
    public double calculatePerformanceScore(String algorithmType) {
        List<AlgorithmMetrics> metrics = metricsRepository.findAll().stream()
                .filter(m -> m.getAlgorithmType().equals(algorithmType))
                .toList();
        
        if (metrics.isEmpty()) {
            return 0.0;
        }

        // Calculate weighted average (coverage 60%, balance 40%)
        double avgCoverage = metrics.stream()
                .mapToDouble(m -> m.getCoverageRate() != null ? m.getCoverageRate().doubleValue() : 0)
                .average()
                .orElse(0);
        double avgBalance = metrics.stream()
                .mapToDouble(m -> m.getBalanceScore() != null ? m.getBalanceScore().doubleValue() : 0)
                .average()
                .orElse(0);

        return (avgCoverage * 0.6 + avgBalance * 0.4);
    }

    /**
     * Get the best performing algorithm based on coverage and balance.
     */
    public String getBestAlgorithm() {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("GREEDY", calculatePerformanceScore("GREEDY"));
        // FAIR_GREEDY is the renamed Round-Robin alias (lazy greedy with fair-share rotation).
        // Both keys are scored so historical metrics under the old name keep showing up.
        scores.put("FAIR_GREEDY", calculatePerformanceScore("FAIR_GREEDY"));
        scores.put("ROUND_ROBIN", calculatePerformanceScore("ROUND_ROBIN"));
        scores.put("CSP_MRV_FC", calculatePerformanceScore("CSP_MRV_FC"));
        scores.put("CSP_MRV_FC", calculatePerformanceScore("CSP_MRV_FC"));

        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("GREEDY");
    }

    private AlgorithmMetricsDTO toDTO(AlgorithmMetrics metric) {
        return AlgorithmMetricsDTO.builder()
                .id(metric.getId())
                .algorithmType(metric.getAlgorithmType())
                .periodId(metric.getPeriod() != null ? metric.getPeriod().getId() : null)
                .periodName(metric.getPeriod() != null ? metric.getPeriod().getPeriodName() : null)
                .totalSchedulesCreated(metric.getTotalSchedulesCreated())
                .executionTimeMs(metric.getExecutionTimeMs())
                .coverageRate(metric.getCoverageRate())
                .balanceScore(metric.getBalanceScore())
                .conflictCount(metric.getConflictCount())
                .createdAt(metric.getCreatedAt())
                .build();
    }

    /**
     * Bulk delete by id list. Mirrors the audit-history "Xóa nhiều" flow.
     * Skips unknown ids silently so the call stays idempotent.
     */
    @org.springframework.transaction.annotation.Transactional
    public int deleteMetricsByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        return metricsRepository.deleteByIdInBatch(ids);
    }

    /**
     * Delete every metric row whose {@code createdAt} falls inside
     * {@code [start, end)}. The frontend passes ISO {@code yyyy-MM-dd}
     * dates; the controller widens them to a half-open range so the end
     * day is inclusive.
     */
    @org.springframework.transaction.annotation.Transactional
    public int deleteMetricsByDateRange(LocalDateTime start, LocalDateTime end) {
        return metricsRepository.deleteByCreatedAtRange(start, end);
    }

    /**
     * Wipe the entire {@code algorithm_metrics} table. Requires the
     * typed-confirm UI gate on the frontend.
     */
    @org.springframework.transaction.annotation.Transactional
    public int deleteAllMetrics() {
        int total = (int) metricsRepository.count();
        metricsRepository.deleteAllInBatch();
        return total;
    }
}
