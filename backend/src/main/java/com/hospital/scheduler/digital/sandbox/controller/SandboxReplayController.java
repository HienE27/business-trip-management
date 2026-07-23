package com.hospital.scheduler.digital.sandbox.controller;

import com.hospital.scheduler.digital.sandbox.dto.ReplayFrame;
import com.hospital.scheduler.digital.sandbox.dto.ReplayResponse;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSessionRepository;
import com.hospital.scheduler.digital.sandbox.service.SandboxReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for replay functionality.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /sandbox/{sessionKey}/replay          - Load full replay</li>
 *   <li>GET  /sandbox/{sessionKey}/replay/{iter} - Get specific frame</li>
 *   <li>GET  /sandbox/{sessionKey}/replay/range  - Get frames in range</li>
 *   <li>GET  /sandbox/{sessionKey}/replay/summary - Get score summary</li>
 *   <li>GET  /sandbox/{sessionKey}/replay/export/json - Export as JSON</li>
 *   <li>GET  /sandbox/{sessionKey}/replay/export/csv - Export as CSV</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/sandbox")
@RequiredArgsConstructor
@Slf4j
public class SandboxReplayController {

    private final SandboxReplayService replayService;
    private final SandboxSessionRepository sessionRepository;

    /**
     * Load full replay for a session.
     */
    @GetMapping("/{sessionKey}/replay")
    public ResponseEntity<?> getReplay(@PathVariable String sessionKey) {
        try {
            ReplayResponse response = replayService.loadReplay(sessionKey);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get a specific frame by iteration.
     */
    @GetMapping("/{sessionKey}/replay/{iteration}")
    public ResponseEntity<?> getFrame(
            @PathVariable String sessionKey,
            @PathVariable int iteration
    ) {
        try {
            ReplayFrame frame = replayService.getFrame(sessionKey, iteration);
            return ResponseEntity.ok(frame);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get frames in a range (for pagination).
     */
    @GetMapping("/{sessionKey}/replay/range")
    public ResponseEntity<?> getFramesInRange(
            @PathVariable String sessionKey,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "100") int end
    ) {
        try {
            List<ReplayFrame> frames = replayService.getFramesInRange(sessionKey, start, end);
            return ResponseEntity.ok(frames);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get score summary for chart.
     */
    @GetMapping("/{sessionKey}/replay/summary")
    public ResponseEntity<?> getScoreSummary(@PathVariable String sessionKey) {
        try {
            ReplayResponse.ScoreSummary summary = replayService.getScoreSummary(sessionKey);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get replay metadata (session info without frames).
     */
    @GetMapping("/{sessionKey}/replay/metadata")
    public ResponseEntity<?> getMetadata(@PathVariable String sessionKey) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        ReplayMetadata metadata = new ReplayMetadata(
                session.getSessionKey(),
                session.getName(),
                session.getSourcePeriodId(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.getIterations(),
                session.getBestScore(),
                session.getCoverageRate(),
                session.getFairnessCv(),
                session.getViolations(),
                session.getStatus().name()
        );

        return ResponseEntity.ok(metadata);
    }

    /**
     * Export replay as JSON.
     */
    @GetMapping(value = "/{sessionKey}/replay/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportJson(@PathVariable String sessionKey) {
        try {
            String json = replayService.exportAsJson(sessionKey);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"replay-" + sessionKey + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Export replay as CSV.
     */
    @GetMapping(value = "/{sessionKey}/replay/export/csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(@PathVariable String sessionKey) {
        try {
            String csv = replayService.exportAsCsv(sessionKey);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"replay-" + sessionKey + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Clear replay cache for a session.
     */
    @DeleteMapping("/{sessionKey}/replay/cache")
    public ResponseEntity<Void> clearCache(@PathVariable String sessionKey) {
        replayService.clearCache(sessionKey);
        return ResponseEntity.noContent().build();
    }

    // ─── Metadata Record ───────────────────────────────────────────────────

    public record ReplayMetadata(
            String sessionKey,
            String sessionName,
            Integer sourcePeriodId,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime expiresAt,
            Integer iterations,
            Double bestScore,
            Double coverageRate,
            Double fairnessCv,
            Integer violations,
            String status
    ) {}
}
