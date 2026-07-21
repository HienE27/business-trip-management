package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;

/**
 * Stops the search after {@code search.timeLimitSeconds} wall-clock seconds.
 */
public class TimeLimitTermination implements Termination {

    private final SchedulingConfig config;

    public TimeLimitTermination(SchedulingConfig config) {
        this.config = config;
    }

    @Override
    public boolean isTerminated(SearchState state) {
        return state.getElapsedMillis() >= config.getSearch().getTimeLimitSeconds() * 1000L;
    }
}