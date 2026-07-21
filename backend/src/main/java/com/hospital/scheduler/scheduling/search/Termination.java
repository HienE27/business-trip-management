package com.hospital.scheduler.scheduling.search;

/**
 * Termination criteria for the search loop. The search loop calls
 * {@link #isTerminated(SearchState)} after each iteration.
 */
public interface Termination {

    boolean isTerminated(SearchState state);
}