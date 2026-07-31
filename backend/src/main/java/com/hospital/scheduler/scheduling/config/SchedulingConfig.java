package com.hospital.scheduler.scheduling.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration cho v10 local-search scheduler.
 *
 * <p>Grouped into 3 sub-configs:
 * <ul>
 *   <li>{@link SearchConfig} — tabu tenure, neighborhood size, candidate list size</li>
 *   <li>{@link FairnessConfig} — CV/Gini targets for objective function</li>
 *   <li>{@link TerminationConfig} — when to stop search</li>
 * </ul>
 *
 * <p>Bind via {@code @ConfigurationProperties(prefix = "scheduling")} in {@code application.properties}.
 */
@Component
@ConfigurationProperties(prefix = "scheduling")
@Getter
@Setter
public class SchedulingConfig {

    private SearchConfig search = new SearchConfig();
    private FairnessConfig fairness = new FairnessConfig();
    private TerminationConfig termination = new TerminationConfig();

    /**
     * Build a SchedulingConfig from the DB-backed {@link ConfigDomain}
     * (algorithm_config table, {@code scheduling_*} keys). Zero/unset fields
     * fall back to this class's Java defaults.
     */
    public static SchedulingConfig from(ConfigDomain cfg) {
        SchedulingConfig sc = new SchedulingConfig();
        SearchConfig search = sc.getSearch();
        if (cfg.candidateListSize() > 0) search.setCandidateListSize(cfg.candidateListSize());
        if (cfg.neighborhoodSize() > 0) search.setNeighborhoodSize(cfg.neighborhoodSize());
        if (cfg.tabuTenureMin() > 0) search.setTabuTenureMin(cfg.tabuTenureMin());
        if (cfg.tabuTenureMax() > 0) search.setTabuTenureMax(cfg.tabuTenureMax());
        if (cfg.maxIterations() > 0) search.setMaxIterations(cfg.maxIterations());
        if (cfg.maxNoImproveIterations() > 0) search.setMaxNoImprove(cfg.maxNoImproveIterations());
        if (cfg.timeLimitSeconds() > 0) search.setTimeLimitSeconds(cfg.timeLimitSeconds());
        FairnessConfig fairness = sc.getFairness();
        if (cfg.cvTarget() > 0) fairness.setCvTarget(cfg.cvTarget());
        if (cfg.cvWorst() > 0) fairness.setCvWorst(cfg.cvWorst());
        TerminationConfig termination = sc.getTermination();
        if (cfg.relativeImprovementThreshold() > 0)
            termination.setRelativeImprovementThreshold(cfg.relativeImprovementThreshold());
        if (cfg.diversifyAfterIterations() > 0) termination.setDiversifyAfter(cfg.diversifyAfterIterations());
        return sc;
    }

    @Getter
    @Setter
    public static class SearchConfig {
        /** Number of candidates sampled per iteration. */
        private int candidateListSize = 50;
        /** Hard cap on the size of the move neighborhood explored per iteration. */
        private int neighborhoodSize = 10;
        /** Minimum tabu tenure (iterations a move stays tabu). */
        private int tabuTenureMin = 5;
        /** Maximum tabu tenure (sampled uniformly in [min,max]). */
        private int tabuTenureMax = 10;
        /** Hard iteration cap (defensive). */
        private int maxIterations = 500;
        /** Stop if no improvement after this many iterations. */
        private int maxNoImprove = 50;
        /** Wall-clock cap for the search loop. */
        private long timeLimitSeconds = 60;
    }

    @Getter
    @Setter
    public static class FairnessConfig {
        /** Coefficient-of-variation target (≤ = balanced). 0.10 = excellent. */
        private double cvTarget = 0.10;
        /** CV above this is treated as severely unfair (large penalty). */
        private double cvWorst = 0.50;
    }

    @Getter
    @Setter
    public static class TerminationConfig {
        /** Stop if relative improvement falls below this fraction. */
        private double relativeImprovementThreshold = 0.001;
        /** After N iterations without improvement, force diversification. */
        private int diversifyAfter = 20;
    }
}