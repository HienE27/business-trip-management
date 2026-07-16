package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.search.SearchState;

/**
 * Stops the search once {@code iteration >= search.maxIterations}.
 */
public class IterationLimitTermination implements Termination {

    private final SchedulingConfig config;

    public IterationLimitTermination(SchedulingConfig config) {
        this.config = config;
    }

    @Override
    public boolean isTerminated(SearchState state) {
        return state.getIteration() >= config.getSearch().getMaxIterations();
    }
}