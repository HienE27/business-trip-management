package com.hospital.scheduler.scheduling.diversifier;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * Restart strategy based on stagnation: restart when the search fails to improve
 * for more than {@code noImproveThreshold} consecutive iterations.
 *
 * <p>Supports both <em>hard restart</em> (reload a saved solution and continue)
 * and <em>shaking</em> (apply k random moves before continuing).
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code noImproveThreshold}: iterations with no improvement before triggering.</li>
 *   <li>{@code maxRestarts}: cap on total restarts. When exhausted the strategy
 *       always returns {@code CONTINUE} even if threshold is exceeded.</li>
 *   <li>{@code shakingStrength}: number of random moves to apply after each
 *       restart. Zero means pure restart (no shaking).</li>
 * </ul>
 */
public class NoImproveRestartStrategy implements SchedulingDiversifier {

    private final int noImproveThreshold;
    private final int maxRestarts;
    private final int shakingStrength;

    private int restartCount = 0;

    public NoImproveRestartStrategy(int noImproveThreshold, int maxRestarts, int shakingStrength) {
        if (noImproveThreshold < 1) throw new IllegalArgumentException("noImproveThreshold must be >= 1");
        if (maxRestarts < 0) throw new IllegalArgumentException("maxRestarts must be >= 0");
        if (shakingStrength < 0) throw new IllegalArgumentException("shakingStrength must be >= 0");
        this.noImproveThreshold = noImproveThreshold;
        this.maxRestarts = maxRestarts;
        this.shakingStrength = shakingStrength;
    }

    public NoImproveRestartStrategy(int noImproveThreshold, int maxRestarts) {
        this(noImproveThreshold, maxRestarts, 0);
    }

    public NoImproveRestartStrategy(int noImproveThreshold) {
        this(noImproveThreshold, Integer.MAX_VALUE, 0);
    }

    @Override
    public DiversifierSignal decide(WorkingSolution current,
                                   WorkingSolution best,
                                   int noImproveSinceRestart,
                                   int iterationsSinceRestart) {
        if (noImproveSinceRestart > noImproveThreshold) {
            if (restartCount >= maxRestarts) {
                return DiversifierSignal.CONTINUE;
            }
            restartCount++;
            return shakingStrength > 0
                    ? DiversifierSignal.SHAKE
                    : DiversifierSignal.RESTART;
        }
        return DiversifierSignal.CONTINUE;
    }

    @Override
    public void beforeRestart() {
        // Nothing to prepare
    }

    public int getRestartCount() {
        return restartCount;
    }

    public void reset() {
        restartCount = 0;
    }

    public int getNoImproveThreshold() {
        return noImproveThreshold;
    }

    public int getMaxRestarts() {
        return maxRestarts;
    }

    public int getShakingStrength() {
        return shakingStrength;
    }
}