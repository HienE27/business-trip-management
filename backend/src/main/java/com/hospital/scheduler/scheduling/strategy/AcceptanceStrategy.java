package com.hospital.scheduler.scheduling.strategy;

/**
 * Identifier for the v11 pluggable acceptance strategy.
 */
public enum AcceptanceStrategy {
    HILL_CLIMBING,
    TABU,
    LATE_ACCEPTANCE,
    SIMULATED_ANNEALING,
    GREAT_DELUGE,
    VARIABLE_NEIGHBORHOOD_SEARCH
}
