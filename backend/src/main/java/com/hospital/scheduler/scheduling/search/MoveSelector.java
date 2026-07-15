package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;

import java.util.List;

/**
 * Interface for move selectors.
 * 
 * <p>A move selector generates candidate moves for evaluation
 * based on the current search state.</p>
 */
public interface MoveSelector {

    /**
     * Select candidate moves based on search state.
     */
    List<Move> select(SearchState state, SelectionContext context);

    /**
     * Search state at a point in time.
     */
    record SearchState(
            int iteration,
            long elapsedMs,
            double currentScore,
            double bestScore,
            int noImproveCount
    ) {}

    /**
     * Context for move selection.
     */
    record SelectionContext(
            WorkingSolution solution,
            SchedulingProblem problem,
            SolutionDescriptor descriptor,
            IncrementalStatisticsHub statistics,
            ScoreDirector scoreDirector
    ) {
        public static SelectionContext from(WorkingSolution solution) {
            return new SelectionContext(
                    solution,
                    solution.getProblem(),
                    solution.getDescriptor(),
                    solution.getStatistics(),
                    null // Score director not available during selection
            );
        }
    }
}
