package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.dto.response.AlgorithmMetricsDTO;
import com.hospital.scheduler.repository.AlgorithmMetricsRepository;
import com.hospital.scheduler.util.DateUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds and persists algorithm execution metrics for a scheduling run.
 */
@Service
public class SchedulingMetricsService {

    private final AlgorithmMetricsRepository metricsRepository;

    public SchedulingMetricsService(AlgorithmMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    public void saveMetrics(SchedulePeriod period, String algorithmType, int executionTime,
                            BigDecimal coverageRate, BigDecimal balanceScore, int conflictCount, int totalSchedulesCreated) {
        AlgorithmMetrics metrics = AlgorithmMetrics.builder()
                .period(period)
                .algorithmType(algorithmType)
                .executionTimeMs(executionTime)
                .coverageRate(coverageRate)
                .balanceScore(balanceScore)
                .conflictCount(conflictCount)
                .totalSchedulesCreated(totalSchedulesCreated)
                .build();

        metricsRepository.save(metrics);
    }

    public List<AlgorithmMetricsDTO> getMetricsByPeriod(Integer periodId) {
        return metricsRepository.findByPeriodId(periodId).stream()
                .map(this::metricsToDTO)
                .toList();
    }

    public List<AlgorithmMetricsDTO> getAllMetrics() {
        return metricsRepository.findAll().stream()
                .map(this::metricsToDTO)
                .toList();
    }

    public Page<AlgorithmMetricsDTO> getMetricsPage(Integer periodId, Pageable pageable) {
        Page<AlgorithmMetrics> page = (periodId == null)
                ? metricsRepository.findAll(pageable)
                : metricsRepository.findByPeriodId(periodId, pageable);
        return page.map(this::metricsToDTO);
    }

    /**
     * Paginated query with optional keyword + algoType + coverage filters.
     * BUGFIX #6: previously the frontend fetched ONE page and then filtered
     * client-side, losing matches from other pages.
     */
    public Page<AlgorithmMetricsDTO> getMetricsPage(
            Integer periodId,
            String algoType,
            String keyword,
            java.math.BigDecimal coverageMin,
            java.math.BigDecimal coverageMax,
            Pageable pageable) {
        boolean noFilters = (algoType == null || algoType.isBlank())
                && (keyword == null || keyword.isBlank())
                && coverageMin == null && coverageMax == null;
        if (noFilters) {
            return getMetricsPage(periodId, pageable);
        }
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword;
        return metricsRepository.findPageWithFilters(periodId, algoType, kw, coverageMin, coverageMax, pageable)
                .map(this::metricsToDTO);
    }

    private AlgorithmMetricsDTO metricsToDTO(AlgorithmMetrics m) {
        return AlgorithmMetricsDTO.builder()
                .id(m.getId())
                .algorithmType(m.getAlgorithmType())
                .executionTimeMs(m.getExecutionTimeMs())
                .coverageRate(m.getCoverageRate())
                .balanceScore(m.getBalanceScore())
                .conflictCount(m.getConflictCount())
                .totalSchedulesCreated(m.getTotalSchedulesCreated())
                .periodId(m.getPeriod() != null ? m.getPeriod().getId() : null)
                .periodName(m.getPeriod() != null ? m.getPeriod().getPeriodName() : null)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
