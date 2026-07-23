package com.hospital.scheduler.digital.sandbox.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full replay data for a sandbox session.
 */
@Value
@Builder
public class ReplayResponse {

    /**
     * Session key.
     */
    String sessionKey;

    /**
     * Total iterations in replay.
     */
    int totalIterations;

    /**
     * Total frames (may be less than iterations due to sampling).
     */
    int totalFrames;

    /**
     * Whether replay is fully loaded.
     */
    boolean fullyLoaded;

    /**
     * Frames in this response.
     */
    List<ReplayFrame> frames;

    /**
     * Replay metadata.
     */
    ReplayMetadata metadata;

    /**
     * Score summary for chart.
     */
    ScoreSummary scoreSummary;

    @Value
    @Builder
    public static class ReplayMetadata {
        String sessionName;
        String sourcePeriodId;
        LocalDateTime createdAt;
        LocalDateTime completedAt;
        long totalDurationMs;
        String bestScore;
        String finalCoverage;
        String finalFairness;
        int acceptedMoves;
        int rejectedMoves;
    }

    @Value
    @Builder
    public static class ScoreSummary {
        List<Integer> iterations;
        List<Double> scores;
        List<Double> coverages;
        List<Double> fairnessCvs;
        List<Integer> violations;
        int maxScore;
        int minScore;
        double maxCoverage;
        double minCoverage;
    }
}
