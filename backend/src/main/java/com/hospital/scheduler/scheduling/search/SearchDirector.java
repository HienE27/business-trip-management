package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ObjectiveScore;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the search process and manages state.
 * 
 * <p>SearchDirector manages:
 * <ul>
 *   <li>Current and best solution states</li>
 *   <li>Move history for undo operations</li>
 *   <li>Event publishing</li>
 *   <li>Snapshot management</li>
 * </ul>
 */
@Slf4j
@Getter
public class SearchDirector {

    private final ScoreDirector scoreDirector;
    private final IncrementalStatisticsHub statisticsHub;
    private final EventBus eventBus;

    // State
    private int iteration = 0;
    private int noImproveCount = 0;
    private long startTimeMs;
    private boolean started = false;

    // Memory
    private final List<Move> moveHistory = new ArrayList<>();
    private final List<ScoreSnapshot> scoreHistory = new ArrayList<>();
    private WorkingSolution bestSolution;
    private ScoreSnapshot bestSnapshot;

    // Snapshot strategy
    private int snapshotInterval = 10;
    private int improvementCount = 0;
    private WorkingSolution lastSnapshot;
    private final List<Move> movesSinceSnapshot = new ArrayList<>();

    public SearchDirector(ScoreDirector scoreDirector, 
                        IncrementalStatisticsHub statisticsHub,
                        EventBus eventBus) {
        this.scoreDirector = scoreDirector;
        this.statisticsHub = statisticsHub;
        this.eventBus = eventBus != null ? eventBus : EventBus.noop();
    }

    /**
     * Start the search process.
     */
    public void startSearch() {
        startTimeMs = System.currentTimeMillis();
        iteration = 0;
        noImproveCount = 0;
        improvementCount = 0;
        moveHistory.clear();
        scoreHistory.clear();
        movesSinceSnapshot.clear();
        started = true;

        eventBus.publish(new SearchStartedEvent(startTimeMs));
    }

    /**
     * End the search process.
     */
    public void endSearch() {
        if (!started) return;

        eventBus.publish(new SearchEndedEvent(
                iteration,
                System.currentTimeMillis() - startTimeMs,
                scoreDirector.getBestSnapshot()
        ));
        started = false;
    }

    /**
     * Execute a move and update state.
     */
    public MoveResult executeMove(Move move, WorkingSolution solution) {
        if (!started) {
            throw new IllegalStateException("Search not started");
        }

        // Calculate delta
        ScoreDelta delta = scoreDirector.calculateDelta(move, solution);

        // Apply move
        move.doMove(solution);
        statisticsHub.apply(move, solution);
        scoreDirector.applyDelta(delta);

        // Update state
        iteration++;
        moveHistory.add(move);
        movesSinceSnapshot.add(move);
        if (moveHistory.size() > 1000) {
            moveHistory.remove(0);
        }

        // Get new score
        ObjectiveScore newScore = scoreDirector.getCurrentAsObjective();

        // Check improvement
        boolean improved = scoreDirector.updateBestIfImproved(iteration);
        if (improved) {
            noImproveCount = 0;
            improvementCount++;

            // Take snapshot periodically
            if (improvementCount % snapshotInterval == 0) {
                takeSnapshot(solution);
            }

            eventBus.publish(new ImprovementEvent(iteration, newScore));
        } else {
            noImproveCount++;
        }

        // Publish event
        eventBus.publish(new MoveExecutedEvent(move, newScore, improved));

        return new MoveResult(move, delta, newScore, improved);
    }

    /**
     * Undo last move.
     */
    public void undoLastMove(WorkingSolution solution) {
        if (moveHistory.isEmpty()) return;

        Move move = moveHistory.remove(moveHistory.size() - 1);
        ScoreDelta delta = scoreDirector.calculateDelta(move, solution);

        move.undo(solution);
        statisticsHub.undo(move, solution);
        scoreDirector.undoDelta(delta);
    }

    /**
     * Get current search state.
     */
    public SearchState getState() {
        ObjectiveScore current = scoreDirector.getCurrentAsObjective();
        ObjectiveScore best = scoreDirector.getBestAsObjective();

        return new SearchState(
                iteration,
                System.currentTimeMillis() - startTimeMs,
                current.totalScore(),
                best != null ? best.totalScore() : 0,
                noImproveCount,
                statisticsHub.getCV(),
                current.hardViolations() == 0
        );
    }

    /**
     * Get best solution.
     */
    public WorkingSolution getBestSolution() {
        if (lastSnapshot == null) return null;

        // Reconstruct best by replaying moves from snapshot
        WorkingSolution best = new WorkingSolution(
                lastSnapshot, 
                null, // descriptor
                null, // statistics
                null  // config
        );

        for (Move m : movesSinceSnapshot) {
            m.doMove(best);
        }

        return best;
    }

    /**
     * Get best score snapshot.
     */
    public ScoreSnapshot getBestSnapshot() {
        return scoreDirector.getBestSnapshot();
    }

    /**
     * Get best score as objective.
     */
    public ObjectiveScore getBestAsObjective() {
        return scoreDirector.getBestAsObjective();
    }

    private void takeSnapshot(WorkingSolution solution) {
        lastSnapshot = solution.copy();
        movesSinceSnapshot.clear();
    }

    // Result record
    public record MoveResult(
            Move move,
            ScoreDelta delta,
            ObjectiveScore newScore,
            boolean improved
    ) {}

    // Events
    public record SearchStartedEvent(long startTimeMs) {}
    public record SearchEndedEvent(int iterations, long elapsedMs, ScoreSnapshot bestScore) {}
    public record MoveExecutedEvent(Move move, ObjectiveScore score, boolean improved) {}
    public record ImprovementEvent(int iteration, ObjectiveScore score) {}

    // Simple event bus
    public interface EventBus {
        void publish(Object event);
        static EventBus noop() {
            return e -> {};
        }
    }
}
