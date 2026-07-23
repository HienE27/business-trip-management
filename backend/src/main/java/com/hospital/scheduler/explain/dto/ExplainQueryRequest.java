package com.hospital.scheduler.explain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Query request for natural language explanations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplainQueryRequest {

    /**
     * Query type.
     */
    private QueryType queryType;

    /**
     * Target slot ID.
     */
    private Integer slotId;

    /**
     * Target assignment ID.
     */
    private Integer assignmentId;

    /**
     * Target staff ID.
     */
    private Integer staffId;

    /**
     * Target session key.
     */
    private String sessionKey;

    /**
     * Target iteration.
     */
    private Integer iteration;

    /**
     * Natural language query (optional).
     */
    private String naturalQuery;

    /**
     * Query types.
     */
    public enum QueryType {
        ASSIGNMENT,
        WHY_NOT,
        CANDIDATE_RANKING,
        REPLAY_ITERATION,
        RULE_CONTRIBUTION,
        CONSTRAINT_CHAIN
    }
}
