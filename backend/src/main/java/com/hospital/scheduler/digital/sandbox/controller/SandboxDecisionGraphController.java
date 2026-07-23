package com.hospital.scheduler.digital.sandbox.controller;

import com.hospital.scheduler.digital.sandbox.decision.DecisionGraph;
import com.hospital.scheduler.digital.sandbox.decision.DecisionGraphBuilder;
import com.hospital.scheduler.digital.sandbox.dto.ReplayFrame;
import com.hospital.scheduler.digital.sandbox.dto.ReplayResponse;
import com.hospital.scheduler.digital.sandbox.service.SandboxReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for decision graph functionality.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /sandbox/{sessionKey}/decision-graph        - Full graph</li>
 *   <li>GET /sandbox/{sessionKey}/decision-graph/iter - Graph up to iteration</li>
 *   <li>GET /sandbox/{sessionKey}/decision-graph/slot/{slotId} - Graph for slot</li>
 *   <li>GET /sandbox/{sessionKey}/decision-graph/stats - Graph statistics</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/sandbox")
@RequiredArgsConstructor
@Slf4j
public class SandboxDecisionGraphController {

    private final SandboxReplayService replayService;
    private final DecisionGraphBuilder graphBuilder;

    /**
     * Get full decision graph for a session.
     */
    @GetMapping("/{sessionKey}/decision-graph")
    public ResponseEntity<DecisionGraph> getDecisionGraph(@PathVariable String sessionKey) {
        try {
            // Get replay frames
            ReplayResponse replay = replayService.loadReplay(sessionKey);

            // Build graph
            DecisionGraph graph = graphBuilder.buildFromFrames(sessionKey, replay.getFrames());

            return ResponseEntity.ok(graph);
        } catch (Exception e) {
            log.error("Failed to build decision graph for session: {}", sessionKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get decision graph up to a specific iteration.
     */
    @GetMapping("/{sessionKey}/decision-graph/iter/{iteration}")
    public ResponseEntity<DecisionGraph> getDecisionGraphUpToIteration(
            @PathVariable String sessionKey,
            @PathVariable int iteration
    ) {
        try {
            ReplayResponse replay = replayService.loadReplay(sessionKey);

            List<ReplayFrame> frames = replay.getFrames().stream()
                    .filter(f -> f.getIteration() <= iteration)
                    .toList();

            DecisionGraph graph = graphBuilder.buildFromFrames(sessionKey + "_iter_" + iteration, frames);

            return ResponseEntity.ok(graph);
        } catch (Exception e) {
            log.error("Failed to build decision graph for iteration {}: {}", iteration, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get decision graph for a specific slot.
     */
    @GetMapping("/{sessionKey}/decision-graph/slot/{slotId}")
    public ResponseEntity<DecisionGraph> getDecisionGraphForSlot(
            @PathVariable String sessionKey,
            @PathVariable int slotId
    ) {
        try {
            ReplayResponse replay = replayService.loadReplay(sessionKey);

            DecisionGraph graph = graphBuilder.getGraphForSlot(sessionKey, replay.getFrames(), slotId);

            return ResponseEntity.ok(graph);
        } catch (Exception e) {
            log.error("Failed to build decision graph for slot {}: {}", slotId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get graph statistics only.
     */
    @GetMapping("/{sessionKey}/decision-graph/stats")
    public ResponseEntity<DecisionGraph.GraphStatistics> getGraphStatistics(@PathVariable String sessionKey) {
        try {
            ReplayResponse replay = replayService.loadReplay(sessionKey);
            DecisionGraph graph = graphBuilder.buildFromFrames(sessionKey, replay.getFrames());

            return ResponseEntity.ok(graph.getStatistics());
        } catch (Exception e) {
            log.error("Failed to get graph statistics for session: {}", sessionKey, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Clear decision graph cache.
     */
    @DeleteMapping("/{sessionKey}/decision-graph/cache")
    public ResponseEntity<Void> clearCache(@PathVariable String sessionKey) {
        graphBuilder.clearCache(sessionKey);
        return ResponseEntity.noContent().build();
    }
}
