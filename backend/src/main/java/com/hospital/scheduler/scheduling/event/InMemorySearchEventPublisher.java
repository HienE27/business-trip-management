package com.hospital.scheduler.scheduling.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * In-memory default implementation of {@link SearchEventPublisher}. Thread-safe,
 * uses copy-on-write lists to keep iteration cost cheap even when many
 * subscribers attach/detach concurrently.
 *
 * <p>Backed by a {@link ConcurrentHashMap} keyed by {@code runId}.
 */
@Component
public class InMemorySearchEventPublisher implements SearchEventPublisher {

    private final Map<String, CopyOnWriteArrayList<Subscriber>> subscribersByRun =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Subscriber> wildcardSubscribers =
            new CopyOnWriteArrayList<>();

    @Override
    public Subscription subscribe(String runId, Subscriber subscriber) {
        CopyOnWriteArrayList<Subscriber> list = subscribersByRun.computeIfAbsent(
                runId, k -> new CopyOnWriteArrayList<>());
        list.add(subscriber);
        return () -> {
            CopyOnWriteArrayList<Subscriber> current = subscribersByRun.get(runId);
            if (current != null) {
                current.remove(subscriber);
                if (current.isEmpty()) {
                    subscribersByRun.remove(runId, current);
                }
            }
        };
    }

    /** Subscribe to every event regardless of run id (used by the SSE bridge). */
    public Subscription subscribeAll(Subscriber subscriber) {
        wildcardSubscribers.add(subscriber);
        return () -> wildcardSubscribers.remove(subscriber);
    }

    @Override
    public void publish(SearchEvent event) {
        CopyOnWriteArrayList<Subscriber> list = subscribersByRun.get(event.getRunId());
        if (list != null) {
            for (Subscriber sub : list) {
                try {
                    sub.onEvent(event);
                } catch (RuntimeException ignored) {
                    // Telemetry must never break the search loop
                }
            }
        }
        if (!wildcardSubscribers.isEmpty()) {
            for (Subscriber sub : wildcardSubscribers) {
                try {
                    sub.onEvent(event);
                } catch (RuntimeException ignored) {
                    // Telemetry must never break the search loop
                }
            }
        }
    }

    @Override
    public void clear(String runId) {
        subscribersByRun.remove(runId);
    }

    /** Test helper — generate a fresh run id. */
    public static String newRunId() {
        return UUID.randomUUID().toString();
    }
}
