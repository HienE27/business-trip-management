package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.score.ScoreDelta;

/**
 * Decides whether a {@link com.hospital.scheduler.scheduling.move.Move} with
 * the given {@link ScoreDelta} should be accepted at the current iteration.
 */
public interface MoveAcceptor {

    /**
     * @param delta       score change the move would produce
     * @param iteration   current iteration (for tabu tenure bookkeeping)
     * @param improving   true if the move improves on best-so-far
     * @return true to accept, false to reject
     */
    boolean accept(ScoreDelta delta, int iteration, boolean improving);
}