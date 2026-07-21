package com.hospital.scheduler.scheduling.replay;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory registry of {@link MoveLog}s keyed by run id. Cleared when a run
 * ends or after a TTL. Replace with DB persistence once we wire Phase 2.5
 * end-to-end with {@code mutation_history}.
 */
@Component
public class MoveLogRegistry {

    private final Map<String, MoveLog> logs = new ConcurrentHashMap<>();

    public MoveLog create(String runId) {
        MoveLog log = new MoveLog();
        logs.put(runId, log);
        return log;
    }

    public MoveLog get(String runId) {
        return logs.get(runId);
    }

    public void remove(String runId) {
        logs.remove(runId);
    }

    public Map<String, MoveLog> all() {
        return Map.copyOf(logs);
    }

    public List<String> runIds() {
        return List.copyOf(logs.keySet());
    }

    public static String newRunId() {
        return UUID.randomUUID().toString();
    }
}
