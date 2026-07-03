package com.hospital.scheduler.algorithm;

/**
 * Configuration for Genetic Algorithm scheduling.
 */
public record GeneticAlgorithmConfig(
    int populationSize,      // Number of chromosomes in population (default: 100)
    int maxGenerations,     // Maximum generations to run (default: 500)
    double crossoverRate,    // Probability of crossover (default: 0.8)
    double mutationRate,     // Probability of mutation (default: 0.1)
    double eliteRate,       // Percentage of best solutions to keep (default: 0.1)
    int tournamentSize,     // Tournament selection size (default: 5)
    long timeLimitMs,       // Time limit in milliseconds (default: 30000)
    double conflictWeight,  // Weight for conflict penalty in fitness (default: 100.0)
    double balanceWeight,   // Weight for balance bonus in fitness (default: 10.0)
    double coverageWeight   // Weight for coverage bonus in fitness (default: 50.0)
) {
    public static GeneticAlgorithmConfig DEFAULT = new GeneticAlgorithmConfig(
        100,    // populationSize
        500,    // maxGenerations
        0.8,    // crossoverRate
        0.1,    // mutationRate
        0.1,    // eliteRate
        5,      // tournamentSize
        60000,  // timeLimitMs - increased to 60 seconds
        100.0,  // conflictWeight
        10.0,   // balanceWeight
        50.0    // coverageWeight
    );

    public int eliteCount() {
        return (int) Math.max(1, populationSize * eliteRate);
    }
}
