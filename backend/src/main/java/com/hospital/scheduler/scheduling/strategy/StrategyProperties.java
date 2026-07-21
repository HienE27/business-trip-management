package com.hospital.scheduler.scheduling.strategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring binding for {@code scheduling.strategy.*}. Loaded by
 * {@code StrategyPropertiesBinding} and forwarded into
 * {@link StrategyConfig}.
 */
@ConfigurationProperties(prefix = "scheduling.strategy")
public class StrategyProperties {

    /** One of the {@link AcceptanceStrategy} enum values. */
    private String strategy = "TABU";

    private int tabuTenureMin = 5;
    private int tabuTenureMax = 15;

    private int laMemory = 400;

    private double saT0 = 1000.0;
    private double saCooling = 0.99;
    private double saTmin = 1.0;

    private double gdDecay = 0.999;
    private double gdMinLevel = 0.0;
    private double gdInitialLevel = 1.0;

    /** Comma-separated names used when strategy=VARIABLE_NEIGHBORHOOD_SEARCH. */
    private String vnsNeighborhoods = "HILL_CLIMBING,TABU";

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public int getTabuTenureMin() { return tabuTenureMin; }
    public void setTabuTenureMin(int tabuTenureMin) { this.tabuTenureMin = tabuTenureMin; }
    public int getTabuTenureMax() { return tabuTenureMax; }
    public void setTabuTenureMax(int tabuTenureMax) { this.tabuTenureMax = tabuTenureMax; }
    public int getLaMemory() { return laMemory; }
    public void setLaMemory(int laMemory) { this.laMemory = laMemory; }
    public double getSaT0() { return saT0; }
    public void setSaT0(double saT0) { this.saT0 = saT0; }
    public double getSaCooling() { return saCooling; }
    public void setSaCooling(double saCooling) { this.saCooling = saCooling; }
    public double getSaTmin() { return saTmin; }
    public void setSaTmin(double saTmin) { this.saTmin = saTmin; }
    public double getGdDecay() { return gdDecay; }
    public void setGdDecay(double gdDecay) { this.gdDecay = gdDecay; }
    public double getGdMinLevel() { return gdMinLevel; }
    public void setGdMinLevel(double gdMinLevel) { this.gdMinLevel = gdMinLevel; }
    public double getGdInitialLevel() { return gdInitialLevel; }
    public void setGdInitialLevel(double gdInitialLevel) { this.gdInitialLevel = gdInitialLevel; }
    public String getVnsNeighborhoods() { return vnsNeighborhoods; }
    public void setVnsNeighborhoods(String vnsNeighborhoods) { this.vnsNeighborhoods = vnsNeighborhoods; }

    /**
     * Translate the bound properties into a {@link StrategyConfig} that the
     * {@link StrategyFactory} can consume.
     */
    public StrategyConfig toStrategyConfig() {
        AcceptanceStrategy kind = parseKind(strategy);
        switch (kind) {
            case HILL_CLIMBING -> {
                return StrategyConfig.hillClimbing();
            }
            case TABU -> {
                return new StrategyConfig(AcceptanceStrategy.TABU,
                        tabuTenureMin, tabuTenureMax, laMemory,
                        saT0, saCooling, saTmin,
                        gdInitialLevel, gdDecay, gdMinLevel,
                        List.of());
            }
            case LATE_ACCEPTANCE -> {
                return new StrategyConfig(AcceptanceStrategy.LATE_ACCEPTANCE,
                        tabuTenureMin, tabuTenureMax, laMemory,
                        saT0, saCooling, saTmin,
                        gdInitialLevel, gdDecay, gdMinLevel,
                        List.of());
            }
            case SIMULATED_ANNEALING -> {
                return new StrategyConfig(AcceptanceStrategy.SIMULATED_ANNEALING,
                        tabuTenureMin, tabuTenureMax, laMemory,
                        saT0, saCooling, saTmin,
                        gdInitialLevel, gdDecay, gdMinLevel,
                        List.of());
            }
            case GREAT_DELUGE -> {
                return new StrategyConfig(AcceptanceStrategy.GREAT_DELUGE,
                        tabuTenureMin, tabuTenureMax, laMemory,
                        saT0, saCooling, saTmin,
                        gdInitialLevel, gdDecay, gdMinLevel,
                        List.of());
            }
            case VARIABLE_NEIGHBORHOOD_SEARCH -> {
                List<StrategyConfig> inner = new ArrayList<>();
                for (String name : Arrays.asList(vnsNeighborhoods.split(","))) {
                    String trimmed = name.trim();
                    if (trimmed.isEmpty()) continue;
                    AcceptanceStrategy child = parseKind(trimmed);
                    inner.add(toChild(child));
                }
                return new StrategyConfig(AcceptanceStrategy.VARIABLE_NEIGHBORHOOD_SEARCH,
                        tabuTenureMin, tabuTenureMax, laMemory,
                        saT0, saCooling, saTmin,
                        gdInitialLevel, gdDecay, gdMinLevel,
                        inner);
            }
            default -> throw new IllegalStateException("Unknown strategy: " + strategy);
        }
    }

    private StrategyConfig toChild(AcceptanceStrategy child) {
        return switch (child) {
            case HILL_CLIMBING -> StrategyConfig.hillClimbing();
            case TABU -> new StrategyConfig(AcceptanceStrategy.TABU,
                    tabuTenureMin, tabuTenureMax, laMemory,
                    saT0, saCooling, saTmin,
                    gdInitialLevel, gdDecay, gdMinLevel,
                    List.of());
            case LATE_ACCEPTANCE -> new StrategyConfig(AcceptanceStrategy.LATE_ACCEPTANCE,
                    tabuTenureMin, tabuTenureMax, laMemory,
                    saT0, saCooling, saTmin,
                    gdInitialLevel, gdDecay, gdMinLevel,
                    List.of());
            case SIMULATED_ANNEALING -> new StrategyConfig(AcceptanceStrategy.SIMULATED_ANNEALING,
                    tabuTenureMin, tabuTenureMax, laMemory,
                    saT0, saCooling, saTmin,
                    gdInitialLevel, gdDecay, gdMinLevel,
                    List.of());
            case GREAT_DELUGE -> new StrategyConfig(AcceptanceStrategy.GREAT_DELUGE,
                    tabuTenureMin, tabuTenureMax, laMemory,
                    saT0, saCooling, saTmin,
                    gdInitialLevel, gdDecay, gdMinLevel,
                    List.of());
            default -> throw new IllegalStateException("Unsupported VNS child: " + child);
        };
    }

    private static AcceptanceStrategy parseKind(String name) {
        try {
            return AcceptanceStrategy.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown scheduling.strategy: " + name, ex);
        }
    }
}
