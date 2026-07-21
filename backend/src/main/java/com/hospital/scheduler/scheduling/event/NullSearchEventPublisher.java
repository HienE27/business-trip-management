package com.hospital.scheduler.scheduling.event;

/**
 * No-op publisher used in unit tests and code paths where telemetry is
 * explicitly disabled. Silently drops every event.
 */
public final class NullSearchEventPublisher implements SearchEventPublisher {

    public static final NullSearchEventPublisher INSTANCE = new NullSearchEventPublisher();

    private NullSearchEventPublisher() {}

    @Override
    public Subscription subscribe(String runId, Subscriber subscriber) {
        return () -> {};
    }

    @Override
    public void publish(SearchEvent event) {
        // no-op
    }

    @Override
    public void clear(String runId) {
        // no-op
    }
}
