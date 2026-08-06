package com.hospital.scheduler.scheduling.diversifier;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Diversification strategy for local search.
 *
 * <p>A {@code SchedulingDiversifier} is consulted after each iteration by the
 * search loop. It may return:
 * <ul>
 *   <li>{@link DiversifierSignal#CONTINUE} — keep searching normally.</li>
 *   <li>{@link DiversifierSignal#SHAKE} — apply {@code k} random moves to
 *       escape the current basin.</li>
 *   <li>{@link DiversifierSignal#RESTART} — reload a known-good solution
 *       (elite pool or path-relinking result).</li>
 *   <li>{@link DiversifierSignal#FORCE_ACCEPT} — force-accept the current
 *       solution even if the acceptance policy would reject it.</li>
 * </ul>
 *
 * <p>Implementations are free to use any combination of:
 * <ul>
 *   <li>Tabu (assignment frequency) — discourage over-used (slot, staff) pairs</li>
 *   <li>Elite solution pool — keep top-N solutions seen so far</li>
 *   <li>No-improve restart — restart when stagnation exceeds a threshold</li>
 *   <li>Path relinking — blend two elite solutions to find new regions</li>
 * </ul>
 */
public interface SchedulingDiversifier {

    /**
     * Called after each search iteration. Returns a signal directing the search
     * loop on how to proceed.
     *
     * @param current   the current working solution
     * @param best     the best solution found so far (may equal {@code current})
     * @param noImproveSinceRestart number of iterations since the last improvement
     * @param iterationsSinceRestart number of iterations since the last restart
     * @return a signal instructing the search loop, or {@link DiversifierSignal#CONTINUE}
     */
    DiversifierSignal decide(WorkingSolution current,
                             WorkingSolution best,
                             int noImproveSinceRestart,
                             int iterationsSinceRestart);

    /**
     * Called when the search finds a new best solution. Implementations may
     * add it to the elite pool and update internal statistics.
     *
     * @param solution the new best solution
     */
    default void onNewBest(WorkingSolution solution) {}

    /**
     * Called before a restart begins so the diversifier can prepare state.
     */
    default void beforeRestart() {}

    /**
     * Called after a restart completes so the diversifier can update internal state.
     *
     * @param restartedFrom the solution the restart was seeded from
     * @param current       the current working solution after the restart
     */
    default void afterRestart(WorkingSolution restartedFrom, WorkingSolution current) {}
}