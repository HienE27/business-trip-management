package com.hospital.scheduler.scheduling.strategy;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;

/**
 * v11 strategy API. Each strategy decides whether a move with the given
 * score delta should be accepted. All strategies share the same lifecycle:
 * <ul>
 *   <li>{@link #initialize(int)} — called once before the search loop starts</li>
 *   <li>{@link #evaluate(Move, ScoreDelta, boolean)} — per-move decision</li>
 *   <li>{@link #reset()} — clear internal state between search runs</li>
 * </ul>
 *
 * <p>The interface intentionally hides the concrete score type so each
 * strategy can use its own internal model (e.g. late-acceptance ring buffer,
 * temperature schedule, water level) without leaking details to the search
 * loop.
 */
public interface MoveAcceptanceStrategy {

    AcceptanceStrategy kind();

    void initialize(int estimatedIterations);

    /**
     * @param move        the proposed move
     * @param delta       the score change the move would produce
     * @param improving   true if the move improves on best-so-far
     * @return true to accept, false to reject
     */
    boolean evaluate(Move move, ScoreDelta delta, boolean improving);

    void reset();
}
