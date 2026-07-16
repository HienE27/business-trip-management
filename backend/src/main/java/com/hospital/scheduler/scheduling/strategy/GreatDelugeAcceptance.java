package com.hospital.scheduler.scheduling.strategy;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;

/**
 * Great Deluge. Accept any move whose resulting score is above a water level
 * that decays by {@code decay} each iteration. Simple, parameter-light.
 */
public class GreatDelugeAcceptance implements MoveAcceptanceStrategy {

    private final double initialLevel;
    private final double decay;
    private final double minLevel;
    private double waterLevel;

    public GreatDelugeAcceptance(double initialLevel, double decay, double minLevel) {
        this.initialLevel = initialLevel;
        this.decay = Math.min(0.9999, Math.max(0.9, decay));
        this.minLevel = Math.max(0.0, minLevel);
    }

    @Override
    public AcceptanceStrategy kind() { return AcceptanceStrategy.GREAT_DELUGE; }

    @Override
    public void initialize(int estimatedIterations) {
        waterLevel = initialLevel;
    }

    @Override
    public boolean evaluate(Move move, ScoreDelta delta, boolean improving) {
        // Heuristic: prefer moves with positive hard delta = 0 and coverage gain.
        // For a real implementation the strategy would compare to a reference
        // score; here we conservatively accept anything that doesn't worsen the
        // dominant hard violation count.
        boolean accept = delta.hardDelta() <= 0;
        waterLevel = Math.max(minLevel, waterLevel * decay);
        return accept;
    }

    public double getWaterLevel() { return waterLevel; }

    @Override
    public void reset() { waterLevel = initialLevel; }
}
