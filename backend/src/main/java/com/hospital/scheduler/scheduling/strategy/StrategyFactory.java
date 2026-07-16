package com.hospital.scheduler.scheduling.strategy;

import java.util.List;
import java.util.Objects;

/**
 * Factory that builds a {@link MoveAcceptanceStrategy} from a {@link StrategyConfig}.
 *
 * <p>Single entry-point for "config → strategy" wiring. New strategies only
 * require a new {@code case} here — no other code changes.
 */
public final class StrategyFactory {

    private StrategyFactory() {}

    public static MoveAcceptanceStrategy build(StrategyConfig config) {
        Objects.requireNonNull(config, "StrategyConfig");
        return switch (config.kind()) {
            case HILL_CLIMBING -> new HillClimbingAcceptance();
            case TABU -> new TabuAcceptance(config.tabuTenureMin(), config.tabuTenureMax());
            case LATE_ACCEPTANCE -> new LateAcceptanceAcceptance(config.laMemorySize());
            case SIMULATED_ANNEALING -> new SimulatedAnnealingAcceptance(
                    config.saT0(), config.saCooling(), config.saTmin());
            case GREAT_DELUGE -> new GreatDelugeAcceptance(
                    config.gdInitialLevel(), config.gdDecay(), config.gdMinLevel());
            case VARIABLE_NEIGHBORHOOD_SEARCH -> {
                List<MoveAcceptanceStrategy> inner = config.vnsNeighborhoods().stream()
                        .map(StrategyFactory::build)
                        .toList();
                yield new VariableNeighborhoodAcceptance(inner);
            }
        };
    }
}
