package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Composite termination — combines multiple criteria with OR semantics.
 *
 * <p>Stops when any single criterion is satisfied:
 * <ul>
 *   <li>Max iterations reached</li>
 *   <li>Time limit exceeded</li>
 *   <li>No improvement for {@code maxNoImprove} iterations</li>
 *   <li>Best score reaches the target (hard violations = 0 AND coverage = 1.0)</li>
 * </ul>
 */
public class CompositeTermination implements Termination {

    /** Returns a reason string if the state triggers termination, else null. */
    @FunctionalInterface
    interface TerminationCheck extends Function<SearchState, String> {}

    private final SchedulingConfig config;
    private final List<TerminationCheck> children = new ArrayList<>();

    public CompositeTermination(SchedulingConfig config) {
        this.config = config;
        children.add(state -> state.getIteration() >= config.getSearch().getMaxIterations()
                ? "max-iterations" : null);
        children.add(state -> state.getElapsedMillis() > config.getSearch().getTimeLimitSeconds() * 1000
                ? "time-limit" : null);
        children.add(state -> state.getNoImproveIterations() >= config.getSearch().getMaxNoImprove()
                ? "no-improve" : null);
        children.add(state -> {
            var best = state.getBestScore();
            if (best == null) return null;
            if (best.getHardViolations() == 0 && best.getCoverage() >= 0.999) {
                return "target-reached";
            }
            return null;
        });
    }

    @Override
    public boolean isTerminated(SearchState state) {
        for (TerminationCheck t : children) {
            String reason = t.apply(state);
            if (reason != null) {
                state.terminate(reason);
                return true;
            }
        }
        return false;
    }
}