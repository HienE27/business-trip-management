package com.hospital.scheduler.scheduling.event;

import com.hospital.scheduler.controller.SchedulingStreamController;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Wires the in-memory event publisher to the SSE controller so every event is
 * automatically forwarded to connected browser clients without the search loop
 * knowing about HTTP plumbing.
 */
@Component
public class SseEventBridge {

    private final InMemorySearchEventPublisher publisher;
    private final SchedulingStreamController streamController;

    public SseEventBridge(InMemorySearchEventPublisher publisher,
                          SchedulingStreamController streamController) {
        this.publisher = publisher;
        this.streamController = streamController;
    }

    @PostConstruct
    public void wire() {
        publisher.subscribeAll(event -> streamController.emit(event.getRunId(), event));
    }
}
