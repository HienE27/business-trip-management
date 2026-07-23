package com.hospital.scheduler.digital.sandbox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.digital.sandbox.dto.TimelineEvent;
import com.hospital.scheduler.digital.sandbox.dto.TimelineEventType;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSessionRepository;
import com.hospital.scheduler.digital.sandbox.service.SandboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE Controller for live sandbox timeline streaming.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /sandbox/{sessionKey}/timeline/live - Subscribe to live events</li>
 *   <li>POST /sandbox/{sessionKey}/timeline/publish - Internal: publish event</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/sandbox")
@RequiredArgsConstructor
@Slf4j
public class SandboxTimelineController {

    private final SandboxService sandboxService;
    private final SandboxSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    /** Active SSE emitters per session. */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emittersBySession =
            new ConcurrentHashMap<>();

    /** SSE timeout: 30 minutes. */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    // ─── SSE Stream ────────────────────────────────────────────────────────────

    /**
     * Open SSE stream for live timeline updates.
     *
     * <p>Client connects here to receive real-time events during simulation.
     * Events are serialized as JSON and sent via SSE.
     *
     * @param sessionKey Session key
     * @return SSE emitter
     */
    @GetMapping(value = "/{sessionKey}/timeline/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionKey) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) {
            log.warn("Session not found for SSE stream: {}", sessionKey);
            return new SseEmitter(0L);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        CopyOnWriteArrayList<SseEmitter> emitters =
                emittersBySession.computeIfAbsent(sessionKey, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE completed for session: {}", sessionKey);
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE timeout for session: {}", sessionKey);
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("SSE error for session: {}", sessionKey);
        });

        // Send initial connection event
        try {
            TimelineEvent connectEvent = TimelineEvent.builder()
                    .eventType(TimelineEventType.STARTED)
                    .timestamp(LocalDateTime.now())
                    .iteration(0)
                    .metadata(Map.of(
                            "sessionKey", sessionKey,
                            "status", session.getStatus().name(),
                            "type", "connection"
                    ))
                    .build();

            emitter.send(SseEmitter.event()
                    .name("timeline")
                    .data(serialize(connectEvent)));
        } catch (IOException e) {
            log.warn("Failed to send connect event for session: {}", sessionKey);
            emitters.remove(emitter);
        }

        // If simulation already completed, send final state
        if (session.getStatus() == com.hospital.scheduler.digital.sandbox.domain.SandboxStatus.COMPLETED) {
            try {
                TimelineEvent completeEvent = TimelineEvent.builder()
                        .eventType(TimelineEventType.COMPLETED)
                        .timestamp(LocalDateTime.now())
                        .iteration(session.getIterations())
                        .score(session.getBestScore() != null ? session.getBestScore() : 0)
                        .coverage(session.getCoverageRate() != null ? session.getCoverageRate() : 0)
                        .fairnessCv(session.getFairnessCv() != null ? session.getFairnessCv() : 0)
                        .hardViolations(session.getViolations() != null ? session.getViolations() : 0)
                        .metadata(Map.of("type", "final_state"))
                        .build();

                emitter.send(SseEmitter.event()
                        .name("timeline")
                        .data(serialize(completeEvent)));
            } catch (IOException e) {
                log.warn("Failed to send final state for session: {}", sessionKey);
            }
        }

        log.info("SSE stream opened for session: {}", sessionKey);
        return emitter;
    }

    // ─── Internal Publish (for sandbox scheduler) ───────────────────────────────

    /**
     * Publish a timeline event to all connected clients.
     *
     * <p>This is called by the sandbox scheduler during simulation.
     * Internal use only - should be called from SandboxExecutionService.
     *
     * @param sessionKey Session key
     * @param event Timeline event
     */
    @Async
    public void publishEvent(String sessionKey, TimelineEvent event) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersBySession.get(sessionKey);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String payload = serialize(event);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("timeline")
                        .data(payload));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    /**
     * Notify all clients that simulation completed.
     */
    @Async
    public void completeSession(String sessionKey, SandboxSession session) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersBySession.remove(sessionKey);
        if (emitters == null) {
            return;
        }

        TimelineEvent completeEvent = TimelineEvent.builder()
                .eventType(TimelineEventType.COMPLETED)
                .timestamp(LocalDateTime.now())
                .iteration(session.getIterations())
                .score(session.getBestScore() != null ? session.getBestScore() : 0)
                .coverage(session.getCoverageRate() != null ? session.getCoverageRate() : 0)
                .fairnessCv(session.getFairnessCv() != null ? session.getFairnessCv() : 0)
                .hardViolations(session.getViolations() != null ? session.getViolations() : 0)
                .metadata(Map.of(
                        "type", "completion",
                        "sessionStatus", session.getStatus().name()
                ))
                .build();

        String payload = serialize(completeEvent);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("timeline")
                        .data(payload));
                emitter.complete();
            } catch (IOException e) {
                // Client already gone
            }
        }

        log.info("SSE stream completed for session: {}", sessionKey);
    }

    /**
     * Notify all clients that simulation failed.
     */
    @Async
    public void failSession(String sessionKey, String errorMessage) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersBySession.remove(sessionKey);
        if (emitters == null) {
            return;
        }

        TimelineEvent failEvent = TimelineEvent.builder()
                .eventType(TimelineEventType.FAILED)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                        "type", "error",
                        "error", errorMessage
                ))
                .build();

        String payload = serialize(failEvent);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("timeline")
                        .data(payload));
                emitter.complete();
            } catch (IOException e) {
                // Client already gone
            }
        }
    }

    /**
     * Send heartbeat to keep connection alive.
     */
    @Async
    public void sendHeartbeat(String sessionKey) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersBySession.get(sessionKey);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        TimelineEvent heartbeat = TimelineEvent.builder()
                .eventType(TimelineEventType.PROGRESS)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of("type", "heartbeat"))
                .build();

        String payload = serialize(heartbeat);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("timeline")
                        .data(payload));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String serialize(TimelineEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("Failed to serialize timeline event", e);
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    /**
     * Get active connection count for a session.
     */
    public int getActiveConnectionCount(String sessionKey) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersBySession.get(sessionKey);
        return emitters != null ? emitters.size() : 0;
    }
}
