package com.hospital.scheduler.scheduling.event;

/**
 * Sink for {@link SearchEvent}s. Subscribers register themselves per-run
 * (typically backed by a WebSocket session or an SSE emitter).
 *
 * <p>Default no-op implementation is {@link NullSearchEventPublisher}.
 */
public interface SearchEventPublisher {

    /** Subscribe to events for a specific run; returns an opaque handle. */
    Subscription subscribe(String runId, Subscriber subscriber);

    /** Publish a single event. Subscribers receive it synchronously. */
    void publish(SearchEvent event);

    /** Tear down all subscribers for a run (typically after search ends). */
    void clear(String runId);

    /** Subscriber callback. */
    @FunctionalInterface
    interface Subscriber {
        void onEvent(SearchEvent event);
    }

    /** Handle returned by {@link #subscribe(String, Subscriber)}. */
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
