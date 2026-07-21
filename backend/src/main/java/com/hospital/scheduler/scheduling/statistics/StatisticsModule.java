package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * SPI for incremental statistics modules.
 *
 * <p>Each module maintains a derived view of the working solution (load
 * counts, weekend counts, fairness metrics, etc.) and updates incrementally
 * via {@link #apply(Move, WorkingSolution)} / {@link #undo(Move, WorkingSolution)}
 * so the search loop can score moves in O(1) instead of O(n) rescans.
 */
public interface StatisticsModule {

    /** Called after a move is applied. Update internal counters. */
    void apply(Move move, WorkingSolution solution);

    /** Called after a move is undone. Roll internal counters back. */
    void undo(Move move, WorkingSolution solution);

    /** Called once after the initial solution is built. */
    void reset(WorkingSolution solution);
}