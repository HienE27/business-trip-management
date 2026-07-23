package com.hospital.scheduler.explain.controller;

import com.hospital.scheduler.explain.dto.*;
import com.hospital.scheduler.explain.service.ExplainService;
import com.hospital.scheduler.digital.sandbox.decision.DecisionGraph;
import com.hospital.scheduler.digital.sandbox.decision.DecisionGraphBuilder;
import com.hospital.scheduler.digital.sandbox.dto.ReplayResponse;
import com.hospital.scheduler.digital.sandbox.service.SandboxReplayService;
import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for explain functionality.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /explain/assignment/{id}                    - Explain assignment</li>
 *   <li>GET /explain/why-not?slot={id}&staff={id}     - Why not selected</li>
 *   <li>GET /explain/ranking/{slotId}                 - Candidate ranking</li>
 *   <li>GET /explain/replay/{session}/{iteration}     - Replay explanation</li>
 *   <li>POST /explain/query                            - Natural language query</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/explain")
@RequiredArgsConstructor
@Slf4j
public class ExplainController {

    private final ExplainService explainService;
    private final SandboxReplayService replayService;
    private final DecisionGraphBuilder graphBuilder;

    /**
     * Explain why a staff member was assigned to a slot.
     *
     * @param assignmentId Assignment ID
     * @param slotId       Slot ID
     * @param staffId      Staff ID
     * @param staffName    Staff name
     * @param score        Score snapshot
     * @return Assignment explanation
     */
    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<AssignmentExplanation> explainAssignment(
            @PathVariable Integer assignmentId,
            @RequestParam(required = false) Integer slotId,
            @RequestParam(required = false) Integer staffId,
            @RequestParam(required = false) String staffName,
            @RequestParam(required = false) Double score
    ) {
        try {
            ScoreSnapshot scoreSnapshot = new ScoreSnapshot(
                    0, // hardViolations
                    score != null ? score * 0.8 : 0, // coverage
                    score != null ? score * 0.2 : 0, // cvTotal
                    0, // cvWeekend
                    0, // weekendGap
                    0, // consecutiveGap
                    0, // gap
                    0.0 // gini
            );

            AssignmentExplanation explanation = explainService.explainAssignment(
                    assignmentId,
                    slotId,
                    staffId,
                    staffName != null ? staffName : "Unknown",
                    scoreSnapshot
            );

            return ResponseEntity.ok(explanation);
        } catch (Exception e) {
            log.error("Failed to explain assignment", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Explain why a candidate was not selected.
     *
     * @param slotId   Slot ID
     * @param staffId  Staff ID
     * @param sessionKey Session key (optional, for decision graph lookup)
     * @return Why not explanation
     */
    @GetMapping("/why-not")
    public ResponseEntity<WhyNotExplanation> explainWhyNot(
            @RequestParam Integer slotId,
            @RequestParam Integer staffId,
            @RequestParam(required = false) String staffName,
            @RequestParam(required = false) String sessionKey
    ) {
        try {
            DecisionGraph graph = null;

            if (sessionKey != null) {
                ReplayResponse replay = replayService.loadReplay(sessionKey);
                graph = graphBuilder.buildFromFrames(sessionKey, replay.getFrames());
            }

            WhyNotExplanation explanation = explainService.explainWhyNot(
                    slotId,
                    staffId,
                    staffName != null ? staffName : "Staff #" + staffId,
                    graph
            );

            return ResponseEntity.ok(explanation);
        } catch (Exception e) {
            log.error("Failed to explain why not", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get candidate ranking for a slot.
     *
     * @param slotId     Slot ID
     * @param sessionKey Session key (for decision graph lookup)
     * @return Candidate ranking explanation
     */
    @GetMapping("/ranking/{slotId}")
    public ResponseEntity<CandidateRankingExplanation> getCandidateRanking(
            @PathVariable Integer slotId,
            @RequestParam String sessionKey
    ) {
        try {
            ReplayResponse replay = replayService.loadReplay(sessionKey);
            DecisionGraph graph = graphBuilder.buildFromFrames(sessionKey, replay.getFrames());

            CandidateRankingExplanation ranking = explainService.explainCandidateRanking(slotId, graph);

            return ResponseEntity.ok(ranking);
        } catch (Exception e) {
            log.error("Failed to get candidate ranking", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Explain a replay iteration.
     *
     * @param sessionKey Session key
     * @param iteration  Iteration number
     * @return Replay explanation
     */
    @GetMapping("/replay/{sessionKey}/{iteration}")
    public ResponseEntity<ReplayExplanation> explainReplayIteration(
            @PathVariable String sessionKey,
            @PathVariable int iteration
    ) {
        try {
            ReplayExplanation explanation = explainService.explainReplayIteration(sessionKey, iteration);
            return ResponseEntity.ok(explanation);
        } catch (Exception e) {
            log.error("Failed to explain replay iteration", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Natural language query endpoint.
     *
     * @param request Query request
     * @return Query response
     */
    @PostMapping("/query")
    public ResponseEntity<ExplainQueryResponse> query(@RequestBody ExplainQueryRequest request) {
        try {
            ExplainQueryResponse response;

            switch (request.getQueryType()) {
                case ASSIGNMENT:
                    response = ExplainQueryResponse.builder()
                            .queryType(request.getQueryType())
                            .timestamp(java.time.LocalDateTime.now())
                            .assignmentExplanation(explainService.explainAssignment(
                                    request.getAssignmentId(),
                                    request.getSlotId(),
                                    request.getStaffId(),
                                    request.getStaffId() != null ? "Staff #" + request.getStaffId() : "Unknown",
                                    new ScoreSnapshot(0, 0, 0, 0, 0, 0, 0, 0.0)
                            ))
                            .build();
                    break;

                case WHY_NOT:
                    response = ExplainQueryResponse.builder()
                            .queryType(request.getQueryType())
                            .timestamp(java.time.LocalDateTime.now())
                            .whyNotExplanation(explainService.explainWhyNot(
                                    request.getSlotId(),
                                    request.getStaffId(),
                                    request.getStaffId() != null ? "Staff #" + request.getStaffId() : "Unknown",
                                    null
                            ))
                            .build();
                    break;

                case REPLAY_ITERATION:
                    response = ExplainQueryResponse.builder()
                            .queryType(request.getQueryType())
                            .timestamp(java.time.LocalDateTime.now())
                            .replayExplanation(explainService.explainReplayIteration(
                                    request.getSessionKey(),
                                    request.getIteration() != null ? request.getIteration() : 0
                            ))
                            .build();
                    break;

                default:
                    response = ExplainQueryResponse.builder()
                            .queryType(request.getQueryType())
                            .timestamp(java.time.LocalDateTime.now())
                            .naturalLanguageResponse("Query type not supported yet.")
                            .build();
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to process query", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
