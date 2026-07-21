package com.hospital.scheduler.scheduling.strategy;

import java.util.List;

/**
 * Configuration for the v11 pluggable acceptance strategy. Mirrors the keys
 * documented in the roadmap and feeds {@link StrategyFactory#build}.
 */
public record StrategyConfig(
        AcceptanceStrategy kind,
        int tabuTenureMin,
        int tabuTenureMax,
        int laMemorySize,
        double saT0,
        double saCooling,
        double saTmin,
        double gdInitialLevel,
        double gdDecay,
        double gdMinLevel,
        List<StrategyConfig> vnsNeighborhoods
) {

    public static StrategyConfig tabu() {
        return new StrategyConfig(
                AcceptanceStrategy.TABU, 5, 15, 400,
                1000.0, 0.99, 1.0,
                0.0, 0.999, 0.0,
                List.of());
    }

    public static StrategyConfig hillClimbing() {
        return new StrategyConfig(
                AcceptanceStrategy.HILL_CLIMBING, 0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                List.of());
    }

    public static StrategyConfig lateAcceptance() {
        return new StrategyConfig(
                AcceptanceStrategy.LATE_ACCEPTANCE, 0, 0, 400,
                0, 0, 0,
                0, 0, 0,
                List.of());
    }

    public static StrategyConfig simulatedAnnealing() {
        return new StrategyConfig(
                AcceptanceStrategy.SIMULATED_ANNEALING, 0, 0, 0,
                1000.0, 0.99, 1.0,
                0, 0, 0,
                List.of());
    }

    public static StrategyConfig greatDeluge() {
        return new StrategyConfig(
                AcceptanceStrategy.GREAT_DELUGE, 0, 0, 0,
                0, 0, 0,
                1.0, 0.999, 0.0,
                List.of());
    }

    public static StrategyConfig vns(List<StrategyConfig> neighborhoods) {
        return new StrategyConfig(
                AcceptanceStrategy.VARIABLE_NEIGHBORHOOD_SEARCH,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                neighborhoods);
    }
}
