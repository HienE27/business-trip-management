package com.hospital.scheduler.explain.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for explain queries.
 */
@Value
@Builder
public class ExplainQueryResponse {

    /**
     * Query type.
     */
    ExplainQueryRequest.QueryType queryType;

    /**
     * Response timestamp.
     */
    LocalDateTime timestamp;

    /**
     * Assignment explanation (if query type is ASSIGNMENT).
     */
    AssignmentExplanation assignmentExplanation;

    /**
     * Why not explanation (if query type is WHY_NOT).
     */
    WhyNotExplanation whyNotExplanation;

    /**
     * Candidate ranking (if query type is CANDIDATE_RANKING).
     */
    CandidateRankingExplanation candidateRankingExplanation;

    /**
     * Replay explanation (if query type is REPLAY_ITERATION).
     */
    ReplayExplanation replayExplanation;

    /**
     * Natural language response.
     */
    String naturalLanguageResponse;

    /**
     * Additional context.
     */
    List<ContextItem> context;

    @Value
    @Builder
    public static class ContextItem {
        String label;
        String value;
        String detail;
    }
}
