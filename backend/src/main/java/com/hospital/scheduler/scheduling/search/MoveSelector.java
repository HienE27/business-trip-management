package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.List;

/**
 * Generates candidate moves for the search loop.
 *
 * <p>Implementations may sample, prioritize, or filter moves based on the
 * current state. The search loop calls {@link #select(WorkingSolution, int)}
 * once per iteration to get a batch of candidates.
 */
public interface MoveSelector {

    /**
     * Select up to {@code batchSize} candidate moves.
     */
    List<Move> select(WorkingSolution solution, int batchSize);
}