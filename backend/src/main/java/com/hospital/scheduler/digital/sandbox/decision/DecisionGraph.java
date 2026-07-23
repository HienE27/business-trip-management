package com.hospital.scheduler.digital.sandbox.decision;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a decision graph for a sandbox session.
 *
 * <p>The graph shows the decision-making process for each slot:
 * - Which candidates were tried
 * - Why they were rejected
 * - Which candidate was ultimately accepted
 */
@Value
@Builder
public class DecisionGraph {

    /**
     * Session key.
     */
    String sessionKey;

    /**
     * Root node ID.
     */
    UUID rootId;

    /**
     * All nodes in the graph.
     */
    List<DecisionNode> nodes;

    /**
     * All edges in the graph.
     */
    List<DecisionEdge> edges;

    /**
     * Graph statistics.
     */
    GraphStatistics statistics;

    /**
     * Graph metadata.
     */
    GraphMetadata metadata;

    /**
     * Graph statistics.
     */
    @Value
    @Builder
    public static class GraphStatistics {
        int totalNodes;
        int totalEdges;
        int totalIterations;
        int totalCandidates;
        int totalAccepted;
        int totalRejected;
        double averageBranchingFactor;
        int maxDepth;
        int maxCandidatesPerIteration;
        Map<String, Integer> rejectionReasons; // reason -> count
    }

    /**
     * Graph metadata.
     */
    @Value
    @Builder
    public static class GraphMetadata {
        String sessionName;
        int sourcePeriodId;
        int totalSlots;
        long buildTimeMs;
        long graphSizeBytes;
    }
}
