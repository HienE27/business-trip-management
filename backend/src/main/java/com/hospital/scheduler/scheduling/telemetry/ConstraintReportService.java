package com.hospital.scheduler.scheduling.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.entity.AlgorithmConstraintReport;
import com.hospital.scheduler.repository.AlgorithmConstraintReportRepository;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists constraint reports after a search run. Called from
 * {@code LocalSearchScheduler} or {@code AutoSchedulingService} once the
 * final {@link WorkingSolution} is available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConstraintReportService {

    private final ConstraintReportBuilder builder;
    private final AlgorithmConstraintReportRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AlgorithmConstraintReport persist(Integer periodId,
                                              String runId,
                                              String algorithmType,
                                              ConstraintRegistry registry,
                                              WorkingSolution solution) {
        try {
            ConstraintReportBuilder.Report report = builder.build(registry.all(), solution);
            String json = objectMapper.writeValueAsString(report);
            AlgorithmConstraintReport entity = AlgorithmConstraintReport.builder()
                    .periodId(periodId)
                    .runId(runId)
                    .algorithmType(algorithmType)
                    .reportJson(json)
                    .build();
            return repository.save(entity);
        } catch (Exception ex) {
            log.error("Failed to persist constraint report: {}", ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    @Transactional(readOnly = true)
    public List<AlgorithmConstraintReport> findByPeriod(Integer periodId) {
        return repository.findByPeriodIdOrderByCreatedAtDesc(periodId);
    }
}
