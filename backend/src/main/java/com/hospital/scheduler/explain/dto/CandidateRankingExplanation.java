package com.hospital.scheduler.explain.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ranking of candidates for a slot.
 *
 * <p>Shows all candidates tried and why they were ranked.
 */
@Value
@Builder
public class CandidateRankingExplanation {

    /**
     * Slot ID.
     */
    Integer slotId;

    /**
     * Work date.
     */
    String workDate;

    /**
     * Shift type.
     */
    String shiftType;

    /**
     * Explanation timestamp.
     */
    LocalDateTime explainedAt;

    /**
     * Total candidates evaluated.
     */
    int totalCandidates;

    /**
     * Accepted candidates.
     */
    int acceptedCount;

    /**
     * Rejected candidates.
     */
    int rejectedCount;

    /**
     * Candidate rankings.
     */
    List<CandidateRank> rankings;

    /**
     * Summary statistics.
     */
    RankingSummary summary;

    @Value
    @Builder
    public static class CandidateRank {
        int rank;
        Integer staffId;
        String staffName;
        String staffCode;
        double score;
        boolean selected;
        boolean rejected;
        String rejectionReason;
        String primaryConstraint;
        List<String> strengths;
        List<String> weaknesses;
    }

    @Value
    @Builder
    public static class RankingSummary {
        double highestScore;
        double lowestScore;
        double averageScore;
        double averageBranchingFactor;
        String mostCommonRejectionConstraint;
    }
}
