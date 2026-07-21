package com.hospital.scheduler.scheduling.event;

import com.hospital.scheduler.scheduling.score.ScoreSnapshot;

/**
 * Lifecycle events emitted by {@link com.hospital.scheduler.scheduling.search.LocalSearchAlgorithm}.
 *
 * <p>Subscribers (WS, SSE, telemetry collectors) can react to these without
 * knowing the search internals.
 */
public enum SearchEventType {
    ITERATION,
    MOVE_ACCEPTED,
    MOVE_REJECTED,
    TABU_HIT,
    SCORE_IMPROVED,
    DIVERSIFIED
}
