package com.hospital.scheduler.scheduling.score;

import lombok.Getter;

/**
 * Immutable snapshot of a {@link MutableScore}. Used as the value type
 * returned by {@link MutableScore#toImmutable()} for logging, telemetry,
 * and the final committed solution.
 */
@Getter
public final class ScoreSnapshot {

    private final int hardViolations;
    private final double coverage;
    private final double cvTotal;
    private final double cvWeekend;
    private final int weekendGap;
    private final int consecutiveGap;
    private final int gap;
    private final double gini;

    public ScoreSnapshot(int hardViolations,
                          double coverage,
                          double cvTotal,
                          double cvWeekend,
                          int weekendGap,
                          int consecutiveGap,
                          int gap,
                          double gini) {
        this.hardViolations = hardViolations;
        this.coverage = coverage;
        this.cvTotal = cvTotal;
        this.cvWeekend = cvWeekend;
        this.weekendGap = weekendGap;
        this.consecutiveGap = consecutiveGap;
        this.gap = gap;
        this.gini = gini;
    }

    @Override
    public String toString() {
        return "ScoreSnapshot{hard=" + hardViolations
                + ", coverage=" + String.format("%.3f", coverage)
                + ", cv=" + String.format("%.3f", cvTotal)
                + ", cvWknd=" + String.format("%.3f", cvWeekend)
                + ", gap=" + gap
                + ", gini=" + String.format("%.3f", gini)
                + "}";
    }
}