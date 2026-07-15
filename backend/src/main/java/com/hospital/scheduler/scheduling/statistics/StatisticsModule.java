package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Interface for incremental statistics modules.
 * 
 * <p>Each module tracks a specific aspect of the solution and can be
 * updated incrementally when a move is applied or undone.</p>
 */
public interface StatisticsModule {

    /**
     * Apply a move to update statistics.
     */
    void apply(Move move, WorkingSolution solution);

    /**
     * Undo a move to revert statistics.
     */
    void undo(Move move, WorkingSolution solution);

    /**
     * Reset statistics from current solution state.
     */
    void reset(WorkingSolution solution);
}
