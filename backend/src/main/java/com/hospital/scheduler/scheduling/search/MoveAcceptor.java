package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;

/**
 * Decides whether a {@link com.hospital.scheduler.scheduling.move.Move} with
 * the given {@link ScoreDelta} should be accepted at the current iteration.
 *
 * <p>The interface intentionally exposes a small set of hooks so a single
 * acceptor implementation can carry both the soft-acceptance policy
 * (HillClimbing / SA / LAP / GreatDeluge / VNS) and the tabu bookkeeping
 * (Tabu). Strategies that do not use tabu return {@code false} from
 * {@link #isTabu(Move, int)} and leave {@link #rememberApplied(Move, int)}
 * as a no-op.
 *
 * <h2>Decision contract</h2>
 *
 * <p>The search loop asks two questions for each candidate move:
 * <ol>
 *   <li>{@link #isTabu(Move, int)} — hard-constraint tabu gate (only
 *       relevant for {@code TabuAcceptor}). If the move is tabu and does
 *       <em>not</em> improve on best-so-far, it is rejected outright.</li>
 *   <li>{@link #accept(ScoreDelta, int, boolean)} — soft acceptance
 *       policy for the remaining moves.</li>
 * </ol>
 *
 * <p>Implementations must be stateless across calls apart from the tabu
 * store; {@link #initialize(int)} and {@link #reset()} provide lifecycle
 * hooks so the loop can prepare and tear down the store between runs.
 */
public interface MoveAcceptor {

    /**
     * Decide whether a move with the given delta should be accepted at the
     * current iteration.
     *
     * @param delta       score change the move would produce
     * @param iteration   current iteration (for tabu tenure bookkeeping)
     * @param improving   true if the move improves on best-so-far
     * @return true to accept, false to reject
     */
    boolean accept(ScoreDelta delta, int iteration, boolean improving);

    /**
     * Whether {@code move} is currently tabu at {@code iteration}. Default
     * returns {@code false} for strategies that do not maintain a tabu list.
     *
     * @param move        the candidate move
     * @param iteration   current iteration
     * @return true if the move is forbidden at this iteration
     */
    default boolean isTabu(Move move, int iteration) {
        return false;
    }

    /**
     * Register a move as applied so it becomes tabu for the next tenured
     * iterations. Default is a no-op for strategies that do not maintain a
     * tabu list.
     *
     * @param move        the move that was just accepted
     * @param iteration   the iteration at which the move was applied
     */
    default void rememberApplied(Move move, int iteration) {
        // no-op by default
    }

    /**
     * Lifecycle hook called once before the search loop starts. Default is
     * a no-op.
     */
    default void initialize(int estimatedIterations) {
        // no-op by default
    }

    /**
     * Lifecycle hook called after the search loop ends so the acceptor can
     * release internal state. Default is a no-op.
     */
    default void reset() {
        // no-op by default
    }
}