package com.hospital.scheduler.scheduling.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the v10 Local Search Scheduler.
 * 
 * <p>Controls search parameters, fairness targets, and termination criteria.</p>
 */
@Component
@ConfigurationProperties(prefix = "scheduling")
@Getter
@Setter
public class SchedulingConfig {

    private SearchConfig search = new SearchConfig();
    private FairnessConfig fairness = new FairnessConfig();
    private TerminationConfig termination = new TerminationConfig();

    @Getter
    @Setter
    public static class SearchConfig {
        /** Maximum number of candidate moves to evaluate per iteration. */
        private int candidateListSize = 50;

        /** Number of top/bottom staff to consider in neighborhood selection. */
        private int neighborhoodSize = 10;

        /** Minimum tabu tenure (iterations). */
        private int tabuTenureMin = 5;

        /** Maximum tabu tenure (iterations). */
        private int tabuTenureMax = 10;

        /** Maximum iterations before forced termination. */
        private int maxIterations = 500;

        /** Stop if no improvement after this many iterations. */
        private int maxNoImprove = 50;

        /** Time limit in seconds. */
        private long timeLimitSeconds = 60;

        /** Probability of random diversification (0.0 - 1.0). */
        private double diversificationProbability = 0.05;
    }

    @Getter
    @Setter
    public static class FairnessConfig {
        /** Target CV (Coefficient of Variation) - 0.10 = 10%. */
        private double cvTarget = 0.10;

        /** Worst acceptable CV - 0.50 = 50%. */
        private double cvWorst = 0.50;

        /** Weight for coverage in total score (0.0 - 1.0). */
        private double coverageWeight = 0.40;

        /** Weight for fairness in total score (0.0 - 1.0). */
        private double fairnessWeight = 0.35;

        /** Weight for constraint compliance in total score (0.0 - 1.0). */
        private double constraintWeight = 0.25;
    }

    @Getter
    @Setter
    public static class TerminationConfig {
        /** Stop if relative improvement is below this threshold. */
        private double relativeImprovementThreshold = 0.001;

        /** Trigger diversification after this many no-improvement iterations. */
        private int diversifyAfter = 20;

        /** Minimum iterations before checking score target. */
        private int minIterationsBeforeTarget = 10;

        /** Minimum iterations before relative improvement termination. */
        private int minIterationsBeforeRelativeImprovement = 20;
    }
}
