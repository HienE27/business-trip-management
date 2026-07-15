package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the search process.
 *
 * <p>Owns the live {@link SearchState}, the {@link ScoreDirector}, and the
 * {@link IncrementalStatisticsHub}. The {@link LocalSearchAlgorithm} reads
 * state from here and writes back results.
 */
@Slf4j
@Getter
public class SearchDirector {

    private final SearchState state = new SearchState();
    private WorkingSolution bestSolution;
    private ScoreSnapshot bestScore;

    private final ScoreDirector scoreDirector;
    private final IncrementalStatisticsHub statisticsHub;

    public SearchDirector(ScoreDirector scoreDirector,
                          IncrementalStatisticsHub statisticsHub) {
        this.scoreDirector = scoreDirector;
        this.statisticsHub = statisticsHub;
    }

    /** Called by the algorithm when a new best score is found. */
    public void onNewBest(WorkingSolution solution) {
        // Deep-copy via toImmutable on each assignment
        this.bestSolution = copySolution(solution);
        this.bestScore = scoreDirector.getCurrent().toImmutable();
        state.setBestScore(bestScore);
        state.resetNoImprove();
    }

    /** Update the current score (called every iteration). */
    public void onIteration(WorkingSolution solution) {
        state.setCurrentScore(scoreDirector.getCurrent().toImmutable());
    }

    /** Track a no-improvement iteration. */
    public void onNoImprove() {
        state.incrementNoImprove();
    }

    /** Track an accepted move. */
    public void onAccepted() {
        state.incrementAccepted();
    }

    /** Track a rejected move. */
    public void onRejected() {
        state.incrementRejected();
    }

    /** Deep-copy a {@link WorkingSolution} so we can keep the best one. */
    private WorkingSolution copySolution(WorkingSolution source) {
        WorkingSolution copy = WorkingSolution.fromProblem(
                source.getConfig(), source.getDescriptor());
        for (var a : source.getAssignments()) {
            if (a.staffId > 0) {
                copy.assign(a.slotId, a.staffId);
            }
        }
        return copy;
    }
}