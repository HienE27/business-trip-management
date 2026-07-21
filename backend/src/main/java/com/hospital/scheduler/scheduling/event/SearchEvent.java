package com.hospital.scheduler.scheduling.event;

import com.hospital.scheduler.scheduling.score.ScoreSnapshot;

/**
 * Immutable payload for a single search-lifecycle event. Carries just enough
 * state for downstream consumers to render a live chart or filter alerts.
 */
public final class SearchEvent {

    private final String runId;
    private final int iteration;
    private final long elapsedMillis;
    private final SearchEventType type;
    private final ScoreSnapshot currentScore;
    private final ScoreSnapshot bestScore;
    private final String moveType;
    private final int hardDelta;
    private final double coverageDelta;

    public SearchEvent(String runId,
                       int iteration,
                       long elapsedMillis,
                       SearchEventType type,
                       ScoreSnapshot currentScore,
                       ScoreSnapshot bestScore,
                       String moveType,
                       int hardDelta,
                       double coverageDelta) {
        this.runId = runId;
        this.iteration = iteration;
        this.elapsedMillis = elapsedMillis;
        this.type = type;
        this.currentScore = currentScore;
        this.bestScore = bestScore;
        this.moveType = moveType;
        this.hardDelta = hardDelta;
        this.coverageDelta = coverageDelta;
    }

    public String getRunId() { return runId; }
    public int getIteration() { return iteration; }
    public long getElapsedMillis() { return elapsedMillis; }
    public SearchEventType getType() { return type; }
    public ScoreSnapshot getCurrentScore() { return currentScore; }
    public ScoreSnapshot getBestScore() { return bestScore; }
    public String getMoveType() { return moveType; }
    public int getHardDelta() { return hardDelta; }
    public double getCoverageDelta() { return coverageDelta; }
}
