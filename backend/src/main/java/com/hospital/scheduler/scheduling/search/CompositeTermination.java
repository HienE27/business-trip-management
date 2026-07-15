package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Composite termination with multiple criteria.
 */
@Slf4j
public class CompositeTermination implements Termination {

    private final List<Termination> terminations;
    private final SchedulingConfig config;

    public CompositeTermination(List<Termination> terminations, SchedulingConfig config) {
        this.terminations = terminations;
        this.config = config;
    }

    @Override
    public boolean isTerminated(SearchState state) {
        for (Termination t : terminations) {
            if (t.isTerminated(state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create standard termination criteria.
     */
    public static CompositeTermination standard(SchedulingConfig config) {
        return new CompositeTermination(List.of(
                new TimeLimitTermination(config.getSearch().getTimeLimitSeconds()),
                new IterationTermination(config.getSearch().getMaxIterations()),
                new NoImprovementTermination(config.getSearch().getMaxNoImprove()),
                new ScoreTargetTermination(
                        config.getFairness().getCvTarget(),
                        config.getTermination().getMinIterationsBeforeTarget()
                ),
                new RelativeImprovementTermination(
                        config.getTermination().getRelativeImprovementThreshold(),
                        config.getTermination().getMinIterationsBeforeRelativeImprovement()
                )
        ), config);
    }

    // Individual termination criteria

    public static class TimeLimitTermination implements Termination {
        private final long timeLimitMs;

        public TimeLimitTermination(long seconds) {
            this.timeLimitMs = seconds * 1000;
        }

        @Override
        public boolean isTerminated(SearchState state) {
            return state.elapsedMs() >= timeLimitMs;
        }
    }

    public static class IterationTermination implements Termination {
        private final int maxIterations;

        public IterationTermination(int maxIterations) {
            this.maxIterations = maxIterations;
        }

        @Override
        public boolean isTerminated(SearchState state) {
            return state.iteration() >= maxIterations;
        }
    }

    public static class NoImprovementTermination implements Termination {
        private final int maxNoImprove;

        public NoImprovementTermination(int maxNoImprove) {
            this.maxNoImprove = maxNoImprove;
        }

        @Override
        public boolean isTerminated(SearchState state) {
            return state.noImproveCount() >= maxNoImprove;
        }
    }

    public static class ScoreTargetTermination implements Termination {
        private final double targetCV;
        private final int minIterations;

        public ScoreTargetTermination(double targetCV, int minIterations) {
            this.targetCV = targetCV;
            this.minIterations = minIterations;
        }

        @Override
        public boolean isTerminated(SearchState state) {
            if (state.iteration() < minIterations) return false;
            return state.isFeasible() && state.currentCV() <= targetCV;
        }
    }

    public static class RelativeImprovementTermination implements Termination {
        private final double threshold;
        private final int minIterations;
        private double lastBestScore = Double.MAX_VALUE;

        public RelativeImprovementTermination(double threshold, int minIterations) {
            this.threshold = threshold;
            this.minIterations = minIterations;
        }

        @Override
        public boolean isTerminated(SearchState state) {
            if (state.iteration() < minIterations) return false;

            if (state.bestScore() <= 0) return false;

            double relativeImprove = Math.abs(state.bestScore() - lastBestScore) / lastBestScore;
            lastBestScore = state.bestScore();

            return relativeImprove < threshold;
        }
    }
}
