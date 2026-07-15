package com.hospital.scheduler.scheduling.search;

/**
 * Interface for termination criteria.
 * 
 * <p>Multiple termination criteria can be combined using CompositeTermination.</p>
 */
public interface Termination {

    /**
     * Check if search should terminate.
     */
    boolean isTerminated(SearchState state);

    /**
     * Search state.
     */
    record SearchState(
            int iteration,
            long elapsedMs,
            double currentScore,
            double bestScore,
            int noImproveCount,
            double currentCV,
            boolean isFeasible
    ) {}
}
