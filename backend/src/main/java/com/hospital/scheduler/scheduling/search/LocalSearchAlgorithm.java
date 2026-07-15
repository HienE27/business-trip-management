package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.constraint.Constraint;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Main search loop — coordinates move selection, evaluation, acceptance,
 * application, and termination.
 *
 * <p>Algorithm:
 * <pre>
 *   1. Initialize: build the working solution, recompute full score
 *   2. Repeat until terminated:
 *      a. Select candidates
 *      b. For each candidate, doMove, evaluate delta, decide accept
 *      c. If accepted: update stats, score; else: undo
 *   3. Return best solution
 * </pre>
 */
@Slf4j
@Getter
public class LocalSearchAlgorithm {

    private final SchedulingConfig config;
    private final MoveSelector moveSelector;
    private final MoveAcceptor moveAcceptor;
    private final Termination termination;
    private final SearchDirector director;
    private final ScoreDirector scoreDirector;
    private final ConstraintRegistry constraintRegistry;
    private final IncrementalStatisticsHub statisticsHub;

    public LocalSearchAlgorithm(SchedulingConfig config,
                                 MoveSelector moveSelector,
                                 MoveAcceptor moveAcceptor,
                                 Termination termination,
                                 SearchDirector director,
                                 ScoreDirector scoreDirector,
                                 ConstraintRegistry constraintRegistry,
                                 IncrementalStatisticsHub statisticsHub) {
        this.config = config;
        this.moveSelector = moveSelector;
        this.moveAcceptor = moveAcceptor;
        this.termination = termination;
        this.director = director;
        this.scoreDirector = scoreDirector;
        this.constraintRegistry = constraintRegistry;
        this.statisticsHub = statisticsHub;
    }

    /**
     * Run the search starting from {@code initial}.
     */
    public SearchResult search(WorkingSolution initial) {
        WorkingSolution current = initial;
        scoreDirector.recomputeFull(current);
        director.onNewBest(current);
        director.onIteration(current);
        log.info("v10 search starting: {} slots, {} staff, score={}",
                current.getAssignments().size(),
                current.getDescriptor().staffCount(),
                scoreDirector.getCurrent().toImmutable());

        while (!director.getState().isTerminated()
                && !termination.isTerminated(director.getState())) {
            director.getState().incrementIteration();
            int batchSize = config.getSearch().getCandidateListSize();
            List<Move> candidates = moveSelector.select(current, batchSize);

            int acceptedThisIteration = 0;
            for (Move move : candidates) {
                if (processMove(current, move)) {
                    acceptedThisIteration++;
                }
            }
            director.onIteration(current);

            if (acceptedThisIteration == 0) {
                director.onNoImprove();
            }
        }

        SearchResult result = new SearchResult();
        result.solution = director.getBestSolution();
        result.score = director.getBestScore();
        result.iterations = director.getState().getIteration();
        result.elapsedMillis = director.getState().getElapsedMillis();
        result.terminationReason = director.getState().getTerminationReason();
        result.acceptedMoves = director.getState().getAcceptedMoves();
        result.rejectedMoves = director.getState().getRejectedMoves();
        log.info("v10 search finished: reason={}, iters={}, elapsed={}ms",
                result.terminationReason, result.iterations, result.elapsedMillis);
        return result;
    }

    /**
     * Process a single move: try it, evaluate delta, decide.
     * Returns true if accepted.
     */
    private boolean processMove(WorkingSolution solution, Move move) {
        // Snapshot pre-move score for "improving" check
        int preHard = scoreDirector.getCurrent().toImmutable().getHardViolations();
        double preCoverage = scoreDirector.getCurrent().toImmutable().getCoverage();

        // Apply move + statistics
        move.doMove(solution);
        statisticsHub.apply(move, solution);

        // Evaluate all constraints to compute new delta
        ScoreDelta delta = ScoreDelta.zero();
        for (Constraint c : constraintRegistry.all()) {
            ScoreDelta d = c.evaluate(solution);
            delta = delta.plus(d);
        }
        scoreDirector.applyDelta(delta);

        // Decide
        int postHard = scoreDirector.getCurrent().toImmutable().getHardViolations();
        double postCoverage = scoreDirector.getCurrent().toImmutable().getCoverage();
        boolean improving = (postHard < preHard)
                || (postHard == preHard && postCoverage > preCoverage);

        boolean accept = improving;
        if (!improving && moveAcceptor instanceof TabuAcceptor tabu) {
            // Tabu logic: non-improving moves are accepted unless they're tabu
            accept = !tabu.isTabu(move, director.getState().getIteration());
        }

        if (accept) {
            if (moveAcceptor instanceof TabuAcceptor tabu) {
                tabu.rememberApplied(move, director.getState().getIteration());
            }
            director.onAccepted();
            // Check if this is the new best
            if (improving) {
                director.onNewBest(solution);
            } else {
                director.onNoImprove();
            }
            return true;
        } else {
            // Undo
            move.undo(solution);
            statisticsHub.undo(move, solution);
            scoreDirector.undoDelta(delta);
            director.onRejected();
            return false;
        }
    }

    @Getter
    public static class SearchResult {
        private WorkingSolution solution;
        private com.hospital.scheduler.scheduling.score.ScoreSnapshot score;
        private int iterations;
        private long elapsedMillis;
        private String terminationReason;
        private int acceptedMoves;
        private int rejectedMoves;
    }
}