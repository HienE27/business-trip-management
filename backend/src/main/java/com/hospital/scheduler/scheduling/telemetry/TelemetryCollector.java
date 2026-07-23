package com.hospital.scheduler.scheduling.telemetry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telemetry collector for debugging and analysis.
 */
@Component
@Slf4j
public class TelemetryCollector {

    private final Map<String, List<TelemetryEvent>> events = new ConcurrentHashMap<>();

    /**
     * Record a telemetry event.
     */
    public void record(String sessionId, TelemetryEvent event) {
        events.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(event);
    }

    /**
     * Get all events for a session.
     */
    public List<TelemetryEvent> getEvents(String sessionId) {
        return events.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * Clear events for a session.
     */
    public void clear(String sessionId) {
        events.remove(sessionId);
    }

    /**
     * Telemetry event.
     */
    @lombok.Value
    @lombok.Builder
    public static class TelemetryEvent {
        private long timestamp;
        private String type;
        private String description;
        private Map<String, Object> data;
    }
}
