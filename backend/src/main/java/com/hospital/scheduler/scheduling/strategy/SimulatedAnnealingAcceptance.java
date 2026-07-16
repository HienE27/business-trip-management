package com.hospital.scheduler.scheduling.strategy;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import java.util.Random;

/**
 * Simulated Annealing. Uphill moves are accepted with probability
 * {@code exp(-delta / T)} where T cools each iteration via a geometric
 * schedule {@code T *= cooling} down to {@code Tmin}.
 */
public class SimulatedAnnealingAcceptance implements MoveAcceptanceStrategy {

    private final double t0;
    private final double cooling;
    private final double tmin;
    private final Random random = new Random();
    private double temperature;

    public SimulatedAnnealingAcceptance(double t0, double cooling, double tmin) {
        this.t0 = Math.max(1e-3, t0);
        this.cooling = Math.min(0.9999, Math.max(0.5, cooling));
        this.tmin = Math.max(1e-6, tmin);
    }

    @Override
    public AcceptanceStrategy kind() { return AcceptanceStrategy.SIMULATED_ANNEALING; }

    @Override
    public void initialize(int estimatedIterations) {
        this.temperature = t0;
    }

    @Override
    public boolean evaluate(Move move, ScoreDelta delta, boolean improving) {
        if (improving) {
            cool();
            return true;
        }
        double h = -heuristic(delta);
        double p = Math.exp(h / Math.max(1e-6, temperature));
        boolean accept = random.nextDouble() < p;
        cool();
        return accept;
    }

    private void cool() {
        temperature = Math.max(tmin, temperature * cooling);
    }

    /**
     * Convert a delta into a positive magnitude suitable for the
     * Boltzmann distribution. Larger magnitude uphill moves are accepted
     * less often.
     */
    private double heuristic(ScoreDelta delta) {
        // Use hard violations first (dominant), then negative coverage delta
        int hard = Math.max(0, delta.hardDelta());
        double cov = Math.max(0.0, -delta.coverageDelta());
        return hard * 100.0 + cov;
    }

    public double getTemperature() { return temperature; }

    @Override
    public void reset() { temperature = t0; }
}
