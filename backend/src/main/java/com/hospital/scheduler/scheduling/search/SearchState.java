package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.score.ScoreSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable state of the search — passed to {@link Termination#isTerminated(SearchState)}
 * after each iteration. Also used to publish events for telemetry.
 */
public class SearchState {

    private int iteration = 0;
    private long startMillis = System.currentTimeMillis();
    private ScoreSnapshot currentScore;
    private ScoreSnapshot bestScore;
    private int noImproveIterations = 0;
    private int acceptedMoves = 0;
    private int rejectedMoves = 0;
    private boolean terminated = false;
    private String terminationReason = null;

    private final List<ScoreSnapshot> history = new ArrayList<>();

    public int getIteration() { return iteration; }
    public long getElapsedMillis() { return System.currentTimeMillis() - startMillis; }
    public ScoreSnapshot getCurrentScore() { return currentScore; }
    public ScoreSnapshot getBestScore() { return bestScore; }
    public int getNoImproveIterations() { return noImproveIterations; }
    public int getAcceptedMoves() { return acceptedMoves; }
    public int getRejectedMoves() { return rejectedMoves; }
    public boolean isTerminated() { return terminated; }
    public String getTerminationReason() { return terminationReason; }
    public List<ScoreSnapshot> getHistory() { return history; }

    public void incrementIteration() { iteration++; }
    public void setCurrentScore(ScoreSnapshot s) { this.currentScore = s; history.add(s); }
    public void setBestScore(ScoreSnapshot s) { this.bestScore = s; }
    public void incrementNoImprove() { noImproveIterations++; }
    public void resetNoImprove() { noImproveIterations = 0; }
    public void incrementAccepted() { acceptedMoves++; }
    public void incrementRejected() { rejectedMoves++; }
    public void terminate(String reason) {
        this.terminated = true;
        this.terminationReason = reason;
    }
}