package com.hospital.scheduler.scheduling.score;

import com.hospital.scheduler.scheduling.domain.Schedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * STUB — NOT USED IN PRODUCTION SCHEDULER.
 *
 * <p>Only consumed by {@code BenchmarkService}. The returned score is hardcoded
 * and does not reflect actual schedule quality. Benchmark scores are informational
 * only in v1.0.
 *
 * <p>Scheduled for proper implementation or removal in v1.1.
 *
 * @deprecated Use {@code ScheduleQualityScorer} for production quality scoring.
 */
@Deprecated
@Component
@Slf4j
public class ScheduleScorer {

    /**
     * Calculate score for a schedule.
     */
    public ScoreResult calculateScore(Schedule schedule) {
        // Stub implementation - returns default values
        return ScoreResult.builder()
                .totalScore(1000.0)
                .hardViolations(0)
                .softViolations(0)
                .coverageScore(400.0)
                .fairnessScore(350.0)
                .constraintScore(250.0)
                .build();
    }

    /**
     * Score result.
     */
    @lombok.Value
    @lombok.Builder
    public static class ScoreResult {
        private double totalScore;
        private int hardViolations;
        private int softViolations;
        private double coverageScore;
        private double fairnessScore;
        private double constraintScore;
    }
}
