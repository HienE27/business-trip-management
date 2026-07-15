package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.MutableScore;
import com.hospital.scheduler.scheduling.score.ObjectiveScore;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Local search algorithm with tabu search.
 * 
 * <p>This is the main search loop that coordinates:
 * <ul>
 *   <li>Move selection</li>
 *   <li>Move evaluation</li>
 *   <li>Move acceptance</li>
 *   <li>Termination checking</li>
 * </ul>
 */
@Slf4j
@Component
public class LocalSearchAlgorithm {

    private final SchedulingConfig config;
    private final MoveSelector moveSelector;
    private final MoveAcceptor moveAcceptor;
    private final CompositeTermination termination;
    private final SearchDirector director;

    public LocalSearchAlgorithm(
            SchedulingConfig config,
            MoveSelector moveSelector,
            MoveAcceptor moveAcceptor,
            CompositeTermination termination,
            SearchDirector director) {
        this.config = config;
        this.moveSelector = moveSelector;
        this.moveAcceptor = moveAcceptor;
        this.termination = termination;
        this.director = director;
    }

    /**
     * Run local search on an initial solution.
     */
    public SearchResult search(WorkingSolution initialSolution) {
        director.startSearch();

        log.info("Starting local search...");
        long startTime = System.currentTimeMillis();

        while (!termination.isTerminated(director.getState())) {
            // 1. Select candidate moves
            List<Move> moves = moveSelector.select(
                    director.getState(),
                    MoveSelector.SelectionContext.from(initialSolution)
            );

            if (moves.isEmpty()) {
                log.warn("No moves available - terminating");
                break;
            }

            // 2. Evaluate and select best move
            Move bestMove = null;
            ScoreDelta bestDelta = null;
            ObjectiveScore bestScoreAfter = null;
            MoveAcceptor.AcceptResult bestAcceptResult = null;

            for (Move move : moves) {
                // Calculate delta
                ScoreDelta delta = director.getScoreDirector().calculateDelta(move, initialSolution);

                // Calculate proposed score
                MutableScore proposedScore = new MutableScore(config);
                proposedScore.reset(director.getScoreDirector().getCurrentScore());
                proposedScore.applyDelta(delta);

                // Check acceptance
                ObjectiveScore current = director.getScoreDirector().getCurrentAsObjective();
                ObjectiveScore best = director.getScoreDirector().getBestAsObjective();

                MoveAcceptor.AcceptResult acceptResult = moveAcceptor.shouldAccept(
                        move, current, proposedScore.toImmutable(), best);

                if (acceptResult.accept()) {
                    if (bestMove == null || 
                            proposedScore.compareTo(bestScoreAfter) < 0) {
                        bestMove = move;
                        bestDelta = delta;
                        bestScoreAfter = proposedScore.toImmutable();
                        bestAcceptResult = acceptResult;
                    }
                }
            }

            // 3. Apply best move
            if (bestMove != null) {
                director.executeMove(bestMove, initialSolution);
                
                if (log.isDebugEnabled()) {
                    log.debug("Iteration {}: accepted {} ({})", 
                            director.getIteration(), bestMove, bestAcceptResult.reason());
                }
            } else {
                // Diversification or stuck
                if (log.isDebugEnabled() && director.getNoImproveCount() > 5) {
                    log.debug("No accepted moves in iteration {}, noImprove={}",
                            director.getIteration(), director.getNoImproveCount());
                }
            }

            // 4. Periodic logging
            if (director.getIteration() % 10 == 0) {
                logProgress();
            }
        }

        director.endSearch();
        long elapsed = System.currentTimeMillis() - startTime;

        log.info("Search completed: {} iterations, {}ms elapsed", 
                director.getIteration(), elapsed);

        return new SearchResult(
                initialSolution,
                director.getBestSnapshot(),
                elapsed
        );
    }

    private void logProgress() {
        var state = director.getState();
        log.info("Progress: iter={}, CV={:.2f}%, noImprove={}, bestScore={:.2f}",
                state.iteration(),
                state.currentCV() * 100,
                state.noImproveCount(),
                state.bestScore());
    }

    /**
     * Search result.
     */
    public record SearchResult(
            WorkingSolution solution,
            com.hospital.scheduler.scheduling.score.ScoreSnapshot bestSnapshot,
            long elapsedMs
    ) {}
}
