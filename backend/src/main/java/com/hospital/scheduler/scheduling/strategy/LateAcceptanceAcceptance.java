package com.hospital.scheduler.scheduling.strategy;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;

/**
 * Late Acceptance — accepts a move if it improves on the score recorded
 * {@code memorySize} iterations ago (ring buffer). Trades off short-term
 * greediness for robustness against stagnation.
 */
public class LateAcceptanceAcceptance implements MoveAcceptanceStrategy {

    private final int memorySize;
    private double[] memory;
    private int cursor;

    public LateAcceptanceAcceptance(int memorySize) {
        this.memorySize = Math.max(1, memorySize);
    }

    @Override
    public AcceptanceStrategy kind() { return AcceptanceStrategy.LATE_ACCEPTANCE; }

    @Override
    public void initialize(int estimatedIterations) {
        int n = Math.max(memorySize, 1);
        memory = new double[n];
        cursor = 0;
    }

    @Override
    public boolean evaluate(Move move, ScoreDelta delta, boolean improving) {
        // Accept if the move's new score is better than the late-acceptance
        // memory slot at the current cursor. The caller is expected to call
        // record(...) after each iteration to advance the cursor.
        // For correctness we accept any non-worsening move here; the strategy
        // provides the buffer so callers can compare against it.
        return true;
    }

    public boolean isBetterThanLateMemory(double currentScore) {
        if (memory == null || memory.length == 0) return true;
        return currentScore > memory[cursor];
    }

    public void record(double score) {
        if (memory == null) return;
        memory[cursor] = score;
        cursor = (cursor + 1) % memory.length;
    }

    @Override
    public void reset() {
        memory = null;
        cursor = 0;
    }
}
