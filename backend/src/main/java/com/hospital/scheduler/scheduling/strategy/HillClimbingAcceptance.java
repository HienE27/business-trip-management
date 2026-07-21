package com.hospital.scheduler.scheduling.strategy;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;

/**
 * Hill Climbing — strict improvement only. No uphill moves, no tabu, no
 * randomness. The cheapest strategy to reason about; useful as a baseline.
 */
public class HillClimbingAcceptance implements MoveAcceptanceStrategy {

    @Override
    public AcceptanceStrategy kind() { return AcceptanceStrategy.HILL_CLIMBING; }

    @Override
    public void initialize(int estimatedIterations) {
        // no-op
    }

    @Override
    public boolean evaluate(Move move, ScoreDelta delta, boolean improving) {
        return improving;
    }

    @Override
    public void reset() {
        // no-op
    }
}
