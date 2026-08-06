package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.strategy.MoveAcceptanceStrategy;
import com.hospital.scheduler.scheduling.strategy.StrategyConfig;
import com.hospital.scheduler.scheduling.strategy.StrategyFactory;
import com.hospital.scheduler.scheduling.strategy.StrategyProperties;

/**
 * Single entry-point for turning a {@link StrategyProperties} binding into a
 * ready-to-use {@link MoveAcceptor}.
 *
 * <p>This is the only place where {@link StrategyFactory} (v11 strategy API)
 * meets {@link MoveAcceptor} (v10 search-loop API). Callers receive a
 * {@link MoveAcceptor} without having to know which underlying
 * {@link MoveAcceptanceStrategy} was selected.
 *
 * <p>The factory seeds the wrapped strategy with a coarse estimate of the
 * number of iterations so strategies with lifecycle state (Tabu tenure,
 * Late-Acceptance ring buffer, Simulated Annealing temperature schedule,
 * Great Deluge water level) can pre-allocate without guessing.
 */
public final class StrategyAcceptorFactory {

    private StrategyAcceptorFactory() {}

    /**
     * Build a {@link MoveAcceptor} from a Spring-bound
     * {@link StrategyProperties}. The returned acceptor is NOT yet
     * initialized; callers are expected to call {@link MoveAcceptor#initialize}
     * once before the search loop starts.
     *
     * @param properties     Spring-bound properties (must not be null)
     * @param estimatedIterations  upper bound for the upcoming search
     * @return a configured {@link MoveAcceptor}
     */
    public static MoveAcceptor build(StrategyProperties properties, int estimatedIterations) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        StrategyConfig config = properties.toStrategyConfig();
        MoveAcceptanceStrategy strategy = StrategyFactory.build(config);
        MoveAcceptor acceptor = new StrategyAcceptorAdapter(strategy);
        acceptor.initialize(estimatedIterations);
        return acceptor;
    }

    /**
     * Build a {@link MoveAcceptor} from an explicit {@link StrategyConfig}.
     * Useful for tests and for callers that need to compose strategies
     * outside the {@code application.yml} binding (e.g. VNS neighborhoods
     * constructed in code).
     *
     * @param config             strategy configuration (must not be null)
     * @param estimatedIterations upper bound for the upcoming search
     * @return a configured {@link MoveAcceptor}
     */
    public static MoveAcceptor build(StrategyConfig config, int estimatedIterations) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        MoveAcceptanceStrategy strategy = StrategyFactory.build(config);
        MoveAcceptor acceptor = new StrategyAcceptorAdapter(strategy);
        acceptor.initialize(estimatedIterations);
        return acceptor;
    }
}