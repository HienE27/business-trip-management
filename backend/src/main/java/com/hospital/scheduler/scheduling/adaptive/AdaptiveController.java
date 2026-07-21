package com.hospital.scheduler.scheduling.adaptive;

import java.util.ArrayList;
import java.util.List;

/**
 * Watches a {@link com.hospital.scheduler.scheduling.search.SearchDirector}'s
 * {@code noImproveIterations} counter and fires perturbations when stagnation
 * is detected.
 *
 * <p>The controller tracks a sequence of "epochs" — each epoch ends either
 * when a new best score appears or when {@code stagnationThreshold}
 * iterations have passed without improvement. At the end of an epoch the
 * registered {@link AdaptivePerturbation}s are applied.
 *
 * <p>Perturbations cover the strategies documented in the roadmap:
 * <ul>
 *   <li>Bump tabu tenure (TabuAcceptance)</li>
 *   <li>Reheat SA temperature</li>
 *   <li>Shrink / expand candidate list size</li>
 * </ul>
 */
public class AdaptiveController {

    public interface AdaptivePerturbation {
        /** Human-readable name shown in logs. */
        String name();

        /** Apply the perturbation. */
        void apply();

        /** Reset back to original value (called when search ends). */
        void reset();
    }

    private final int stagnationThreshold;
    private final List<AdaptivePerturbation> perturbations = new ArrayList<>();
    private int lastObservedNoImprove = 0;
    private int epochCount = 0;

    public AdaptiveController(int stagnationThreshold) {
        this.stagnationThreshold = Math.max(1, stagnationThreshold);
    }

    public void register(AdaptivePerturbation perturbation) {
        perturbations.add(perturbation);
    }

    /**
     * Called by the search loop every iteration. Returns true if a perturbation
     * was triggered this call.
     */
    public boolean tick(int noImproveIterations) {
        if (noImproveIterations - lastObservedNoImprove >= stagnationThreshold) {
            epochCount++;
            for (AdaptivePerturbation p : perturbations) {
                try {
                    p.apply();
                } catch (RuntimeException ignored) {
                    // never break the search loop
                }
            }
            lastObservedNoImprove = noImproveIterations;
            return true;
        }
        return false;
    }

    /** Reset all perturbations to their original values. */
    public void shutdown() {
        perturbations.forEach(AdaptivePerturbation::reset);
    }

    public int getEpochCount() { return epochCount; }
    public int getStagnationThreshold() { return stagnationThreshold; }
    public List<AdaptivePerturbation> getPerturbations() { return List.copyOf(perturbations); }
}
