package com.hospital.scheduler.digital.sandbox.decision;

import com.hospital.scheduler.digital.sandbox.dto.ReplayFrame;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Builds a decision graph from replay frames and snapshots.
 *
 * <p>This component transforms the linear replay into a graph showing:
 * - All candidates evaluated for each slot
 * - Rejection reasons
 * - The accepted candidate
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DecisionGraphBuilder {

    /** Cache for built graphs. */
    private final ConcurrentHashMap<String, DecisionGraph> graphCache = new ConcurrentHashMap<>();

    /** Cache TTL: 30 minutes. */
    private static final long CACHE_TTL_MS = 30 * 60 * 1000;

    /**
     * Build decision graph for a session from replay frames.
     *
     * @param sessionKey Session key
     * @param frames     Replay frames
     * @return Decision graph
     */
    public DecisionGraph buildFromFrames(String sessionKey, List<ReplayFrame> frames) {
        // Check cache
        DecisionGraph cached = graphCache.get(sessionKey);
        if (cached != null) {
            return cached;
        }

        // Build graph
        DecisionGraph graph = buildGraph(sessionKey, frames);

        // Cache
        graphCache.put(sessionKey, graph);

        return graph;
    }

    /**
     * Build decision graph from snapshots.
     */
    public DecisionGraph buildFromSnapshots(String sessionKey, List<SandboxSnapshot> snapshots) {
        List<ReplayFrame> frames = snapshots.stream()
                .map(this::snapshotToFrame)
                .collect(Collectors.toList());
        return buildFromFrames(sessionKey, frames);
    }

    private DecisionGraph buildGraph(String sessionKey, List<ReplayFrame> frames) {
        Map<UUID, DecisionNode> nodeMap = new LinkedHashMap<>();
        List<DecisionEdge> edges = new ArrayList<>();
        Map<Integer, List<DecisionNode>> nodesBySlot = new HashMap<>();

        UUID rootId = UUID.randomUUID();
        UUID currentParentId = rootId;

        // Group frames by iteration/slot to identify candidates
        Map<String, List<ReplayFrame>> framesByContext = new LinkedHashMap<>();

        for (ReplayFrame frame : frames) {
            String contextKey = frame.getIteration() + "_" +
                    (frame.getSlot() != null ? frame.getSlot().getId() : "root");

            framesByContext.computeIfAbsent(contextKey, k -> new ArrayList<>()).add(frame);
        }

        // Build nodes
        for (Map.Entry<String, List<ReplayFrame>> entry : framesByContext.entrySet()) {
            List<ReplayFrame> slotFrames = entry.getValue();

            if (slotFrames.isEmpty()) continue;

            ReplayFrame firstFrame = slotFrames.get(0);
            Integer slotId = firstFrame.getSlot() != null ? firstFrame.getSlot().getId() : null;

            // Create parent node for this slot evaluation
            UUID slotNodeId = UUID.randomUUID();
            DecisionNode slotNode = DecisionNode.builder()
                    .id(slotNodeId)
                    .iteration(firstFrame.getIteration())
                    .slotId(slotId != null ? slotId : 0)
                    .candidateStaffId(null)
                    .candidateStaffName("Slot #" + (slotId != null ? slotId : "root"))
                    .status(DecisionStatus.TRYING)
                    .rejectionReason(null)
                    .violatedConstraint(null)
                    .scoreDelta(0)
                    .coverageDelta(0)
                    .fairnessDelta(0)
                    .children(new ArrayList<>())
                    .parentId(null)
                    .depth(0)
                    .evaluationTimeMs(0)
                    .metadata(Map.of("type", "slot"))
                    .build();

            nodeMap.put(slotNodeId, slotNode);
            if (slotId != null) {
                nodesBySlot.computeIfAbsent(slotId, k -> new ArrayList<>()).add(slotNode);
            }

            // Create candidate nodes
            UUID previousNodeId = slotNodeId;

            for (ReplayFrame frame : slotFrames) {
                UUID nodeId = UUID.randomUUID();

                // Determine status
                DecisionStatus status = frame.isAccepted()
                        ? DecisionStatus.ACCEPTED
                        : DecisionStatus.REJECTED;

                // Parse rejection reason from frame.reason
                String rejectionReason = null;
                String violatedConstraint = null;

                if (!frame.isAccepted() && frame.getReason() != null) {
                    rejectionReason = frame.getReason();
                    violatedConstraint = extractConstraint(frame.getReason());
                }

                // Build children list for parent
                DecisionNode parentNode = nodeMap.get(previousNodeId);
                if (parentNode != null) {
                    List<UUID> children = new ArrayList<>(parentNode.getChildren());
                    children.add(nodeId);
                    nodeMap.put(previousNodeId, DecisionNode.builder()
                            .id(parentNode.getId())
                            .iteration(parentNode.getIteration())
                            .slotId(parentNode.getSlotId())
                            .candidateStaffId(parentNode.getCandidateStaffId())
                            .candidateStaffName(parentNode.getCandidateStaffName())
                            .status(parentNode.getStatus())
                            .rejectionReason(parentNode.getRejectionReason())
                            .violatedConstraint(parentNode.getViolatedConstraint())
                            .scoreDelta(parentNode.getScoreDelta())
                            .coverageDelta(parentNode.getCoverageDelta())
                            .fairnessDelta(parentNode.getFairnessDelta())
                            .children(children)
                            .parentId(parentNode.getParentId())
                            .depth(parentNode.getDepth())
                            .evaluationTimeMs(parentNode.getEvaluationTimeMs())
                            .metadata(parentNode.getMetadata())
                            .build());
                }

                // Create this node
                DecisionNode node = DecisionNode.builder()
                        .id(nodeId)
                        .iteration(frame.getIteration())
                        .slotId(slotId != null ? slotId : 0)
                        .candidateStaffId(frame.getStaff() != null ? frame.getStaff().getId() : null)
                        .candidateStaffName(frame.getStaff() != null ? frame.getStaff().getName() : "Unknown")
                        .status(status)
                        .rejectionReason(rejectionReason)
                        .violatedConstraint(violatedConstraint)
                        .scoreDelta(frame.getScoreDelta())
                        .coverageDelta(frame.getCoverageDelta())
                        .fairnessDelta(frame.getFairnessCv())
                        .children(new ArrayList<>())
                        .parentId(previousNodeId)
                        .depth(parentNode != null ? parentNode.getDepth() + 1 : 1)
                        .evaluationTimeMs(frame.getDurationMs())
                        .metadata(Map.of(
                                "accepted", frame.isAccepted(),
                                "reason", frame.getReason() != null ? frame.getReason() : ""
                        ))
                        .build();

                nodeMap.put(nodeId, node);

                // Create edge
                DecisionEdge edge = new DecisionEdge(
                        previousNodeId,
                        nodeId,
                        frame.isAccepted() ? DecisionEdge.EdgeType.ACCEPT : DecisionEdge.EdgeType.REJECT,
                        frame.isAccepted() ? "Accepted" : "Rejected",
                        Math.abs(frame.getScoreDelta())
                );
                edges.add(edge);

                previousNodeId = nodeId;
            }
        }

        // Calculate statistics
        DecisionGraph.GraphStatistics stats = calculateStatistics(nodeMap, edges);

        return DecisionGraph.builder()
                .sessionKey(sessionKey)
                .rootId(rootId)
                .nodes(new ArrayList<>(nodeMap.values()))
                .edges(edges)
                .statistics(stats)
                .metadata(DecisionGraph.GraphMetadata.builder()
                        .sessionName(sessionKey)
                        .sourcePeriodId(0)
                        .totalSlots(nodesBySlot.size())
                        .buildTimeMs(System.currentTimeMillis())
                        .graphSizeBytes(estimateGraphSize(nodeMap, edges))
                        .build())
                .build();
    }

    private String extractConstraint(String reason) {
        if (reason == null) return null;

        // Try to extract constraint ID like "BR03", "BR05"
        if (reason.contains("BR03")) return "BR03";
        if (reason.contains("BR05")) return "BR05";
        if (reason.contains("BR01")) return "BR01";
        if (reason.contains("BR02")) return "BR02";
        if (reason.contains("BR04")) return "BR04";

        return null;
    }

    private DecisionGraph.GraphStatistics calculateStatistics(Map<UUID, DecisionNode> nodes, List<DecisionEdge> edges) {
        int totalAccepted = 0;
        int totalRejected = 0;
        Map<String, Integer> rejectionReasons = new HashMap<>();
        int maxDepth = 0;
        int totalCandidates = 0;

        for (DecisionNode node : nodes.values()) {
            if (node.getCandidateStaffId() != null) {
                totalCandidates++;

                switch (node.getStatus()) {
                    case ACCEPTED -> totalAccepted++;
                    case REJECTED, REJECTED_HARD, REJECTED_SOFT, REJECTED_TABU, REJECTED_NO_IMPROVEMENT -> {
                        totalRejected++;
                        if (node.getViolatedConstraint() != null) {
                            rejectionReasons.merge(node.getViolatedConstraint(), 1, Integer::sum);
                        }
                    }
                }
            }

            maxDepth = Math.max(maxDepth, node.getDepth());
        }

        double avgBranching = nodes.isEmpty() ? 0 : (double) totalCandidates / Math.max(1, totalAccepted);

        return DecisionGraph.GraphStatistics.builder()
                .totalNodes(nodes.size())
                .totalEdges(edges.size())
                .totalIterations(nodes.values().stream()
                        .mapToInt(DecisionNode::getIteration)
                        .max().orElse(0))
                .totalCandidates(totalCandidates)
                .totalAccepted(totalAccepted)
                .totalRejected(totalRejected)
                .averageBranchingFactor(avgBranching)
                .maxDepth(maxDepth)
                .maxCandidatesPerIteration(nodes.values().stream()
                        .collect(Collectors.groupingBy(DecisionNode::getIteration))
                        .values().stream()
                        .mapToInt(List::size)
                        .max().orElse(0))
                .rejectionReasons(rejectionReasons)
                .build();
    }

    private long estimateGraphSize(Map<UUID, DecisionNode> nodes, List<DecisionEdge> edges) {
        // Rough estimate: ~500 bytes per node, ~50 bytes per edge
        return nodes.size() * 500L + edges.size() * 50L;
    }

    private ReplayFrame snapshotToFrame(SandboxSnapshot snapshot) {
        return ReplayFrame.builder()
                .iteration(snapshot.getIteration())
                .timestamp(snapshot.getCreatedAt())
                .score(snapshot.getScore())
                .coverage(snapshot.getCoverageRate() != null ? snapshot.getCoverageRate() : 0)
                .fairnessCv(snapshot.getFairnessCv() != null ? snapshot.getFairnessCv() : 0)
                .hardViolations(snapshot.getViolations() != null ? snapshot.getViolations() : 0)
                .softViolations(0)
                .moveType(snapshot.getMoveType())
                .accepted(snapshot.getAccepted() != null ? snapshot.getAccepted() : false)
                .reason("")
                .scoreDelta(snapshot.getScoreDelta() != null ? snapshot.getScoreDelta() : 0)
                .coverageDelta(0)
                .durationMs(0)
                .isCheckpoint(snapshot.getIsCheckpoint() != null && snapshot.getIsCheckpoint())
                .build();
    }

    /**
     * Get graph for a specific iteration.
     */
    public DecisionGraph getGraphForIteration(String sessionKey, List<ReplayFrame> frames, int iteration) {
        List<ReplayFrame> filteredFrames = frames.stream()
                .filter(f -> f.getIteration() <= iteration)
                .collect(Collectors.toList());
        return buildFromFrames(sessionKey, filteredFrames);
    }

    /**
     * Get subgraph for a specific slot.
     */
    public DecisionGraph getGraphForSlot(String sessionKey, List<ReplayFrame> frames, int slotId) {
        List<ReplayFrame> slotFrames = frames.stream()
                .filter(f -> f.getSlot() != null && f.getSlot().getId() == slotId)
                .collect(Collectors.toList());

        if (slotFrames.isEmpty()) {
            return DecisionGraph.builder()
                    .sessionKey(sessionKey)
                    .rootId(null)
                    .nodes(Collections.emptyList())
                    .edges(Collections.emptyList())
                    .statistics(DecisionGraph.GraphStatistics.builder().build())
                    .build();
        }

        return buildFromFrames(sessionKey + "_slot_" + slotId, slotFrames);
    }

    /**
     * Clear cache.
     */
    public void clearCache(String sessionKey) {
        graphCache.remove(sessionKey);
    }

    /**
     * Clear all caches.
     */
    public void clearAllCaches() {
        graphCache.clear();
    }
}
