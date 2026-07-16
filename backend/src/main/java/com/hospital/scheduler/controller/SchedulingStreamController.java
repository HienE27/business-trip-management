package com.hospital.scheduler.controller;

import com.hospital.scheduler.scheduling.event.InMemorySearchEventPublisher;
import com.hospital.scheduler.scheduling.event.SearchEvent;
import com.hospital.scheduler.scheduling.event.SearchEventPublisher;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events endpoint for live search telemetry. Streams events for a
 * specific run id to any number of attached clients. WebSocket equivalent can
 * be added later without changing the {@link SearchEventPublisher} contract.
 */
@RestController
@RequestMapping("/api/v1/scheduling/stream")
public class SchedulingStreamController {

    private final SearchEventPublisher publisher;
    private final ConcurrentHashMap<String, List<SseEmitter>> emittersByRun =
            new ConcurrentHashMap<>();

    public SchedulingStreamController(SearchEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Open a live SSE stream for a run id. The first call also assigns the run
     * id if it doesn't exist yet — clients can connect before the search begins
     * and start receiving events from the first iteration.
     */
    @GetMapping(value = "/{runId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        List<SseEmitter> emitters = emittersByRun.computeIfAbsent(
                runId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(t -> emitters.remove(emitter));

        // Send initial heartbeat so the client knows it's connected.
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"runId\":\"" + runId + "\"}"));
        } catch (IOException ignored) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * REST hook for the search driver to notify attached SSE clients when a
     * search run ends. Emits a final "complete" event and detaches everyone.
     */
    @Async
    public void completeRun(String runId) {
        List<SseEmitter> emitters = emittersByRun.remove(runId);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("complete").data("{\"runId\":\"" + runId + "\"}"));
                emitter.complete();
            } catch (IOException ignored) {
                // client gone
            }
        }
    }

    /** For a search driver to push an event out to SSE subscribers. */
    public void emit(String runId, SearchEvent event) {
        List<SseEmitter> emitters = emittersByRun.get(runId);
        if (emitters == null || emitters.isEmpty()) return;
        String payload = serialize(event);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("event").data(payload));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    private String serialize(SearchEvent event) {
        // Hand-rolled JSON keeps this endpoint free of Jackson dependency noise.
        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
                .append("\"type\":\"").append(event.getType()).append("\",")
                .append("\"iteration\":").append(event.getIteration()).append(',')
                .append("\"elapsed\":").append(event.getElapsedMillis()).append(',')
                .append("\"hardDelta\":").append(event.getHardDelta()).append(',')
                .append("\"coverageDelta\":").append(event.getCoverageDelta());
        if (event.getCurrentScore() != null) {
            sb.append(",\"currentHard\":").append(event.getCurrentScore().getHardViolations())
              .append(",\"currentCoverage\":").append(event.getCurrentScore().getCoverage());
        }
        if (event.getBestScore() != null) {
            sb.append(",\"bestHard\":").append(event.getBestScore().getHardViolations())
              .append(",\"bestCoverage\":").append(event.getBestScore().getCoverage());
        }
        sb.append('}');
        return sb.toString();
    }

    /** Test seam — substitute the publisher from outside. */
    public void setPublisher(SearchEventPublisher publisher) {
        // No-op; constructor-bound.
    }

    /** Helper to mint a fresh run id (mirrors {@link InMemorySearchEventPublisher#newRunId()}). */
    public String newRunId() {
        return InMemorySearchEventPublisher.newRunId();
    }
}
