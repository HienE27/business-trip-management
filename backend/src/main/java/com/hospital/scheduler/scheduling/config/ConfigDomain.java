package com.hospital.scheduler.scheduling.config;

/**
 * Unified configuration domain for the auto-scheduling engine.
 * Single source of truth for ALL configuration parameters.
 *
 * <p>This record replaces scattered config across:
 * <ul>
 *   <li>RuntimeConfig (AlgorithmConfigService)</li>
 *   <li>AutoGenConfig (AutoGenConfigService)</li>
 *   <li>SchedulingConfig (application.properties)</li>
 *   <li>StrategyConfig (strategy package)</li>
 * </ul>
 *
 * <p>Each field corresponds to one or more keys in {@code algorithm_config} table.
 * The mapping is handled by {@link ConfigMapper}.
 *
 * <p>Categories:
 * <ul>
 *   <li>GENERAL - global enable/holiday settings</li>
 *   <li>ALGORITHM - iteration, neighborhood, tabu, termination</li>
 *   <li>FAIRNESS - coefficient of variation targets</li>
 *   <li>COVERAGE - per-shift-type min/max bounds</li>
 *   <li>L04 - cross-specialty, ratio, balance strategy</li>
 *   <li>CONSTRAINTS - overnight recovery, staff/shift limits</li>
 *   <li>PERFORMANCE - time limit, candidate size</li>
 * </ul>
 */
public record ConfigDomain(

        // ═══════════════════════════════════════════════════════════════════
        // GENERAL
        // ═══════════════════════════════════════════════════════════════════

        /** Enable auto-scheduling globally. */
        boolean enabled,

        /** Holiday handling: SKIP (skip holidays) or PARTIAL (partial coverage). */
        String holidayMode,

        /** Shift types to completely skip during auto-generation. */
        String[] removedShiftTypes,

        // ═══════════════════════════════════════════════════════════════════
        // ALGORITHM
        // ═══════════════════════════════════════════════════════════════════

        /** Maximum iterations before termination. Default: 500. */
        int maxIterations,

        /** Number of neighborhood moves to generate per iteration. Default: 10. */
        int neighborhoodSize,

        /** Minimum tabu tenure (lower bound of random range). Default: 5. */
        int tabuTenureMin,

        /** Maximum tabu tenure (upper bound of random range). Default: 10. */
        int tabuTenureMax,

        /** Stop if no improvement for this many iterations. Default: 50. */
        int maxNoImproveIterations,

        /** Relative improvement threshold to continue search. Default: 0.001. */
        double relativeImprovementThreshold,

        /** Number of iterations without improvement before diversification. Default: 20. */
        int diversifyAfterIterations,

        /** Acceptance strategy: TABU, HILL_CLIMBING, SIMULATED_ANNEALING, LATE_ACCEPTANCE, GREAT_DELUGE. */
        String acceptanceStrategy,

        // Simulated Annealing params
        /** SA: Initial temperature. Default: 100.0. */
        double saInitialTemperature,

        /** SA: Cooling rate per iteration. Default: 0.9995. */
        double saCoolingRate,

        /** SA: Minimum temperature to stop. Default: 0.01. */
        double saTemperatureMin,

        // Late Acceptance params
        /** LA: Memory size (number of previous solutions to compare). Default: 10. */
        int laMemorySize,

        // Great Deluge params
        /** GD: Initial water level (starting score ceiling). Default: 1000.0. */
        double gdInitialLevel,

        /** GD: Decay rate per iteration. Default: 0.999. */
        double gdDecayRate,

        /** GD: Minimum water level before stopping. Default: 0.0. */
        double gdMinLevel,

        // ═══════════════════════════════════════════════════════════════════
        // FAIRNESS
        // ═══════════════════════════════════════════════════════════════════

        /** Target coefficient of variation for shift distribution fairness. Default: 0.10. */
        double cvTarget,

        /** Worst acceptable CV. If current CV exceeds this, fairness is violated. Default: 0.50. */
        double cvWorst,

        /** Weekend penalty weight multiplier. Higher = weekend shifts more costly. Default: 2.0. */
        double weekendWeight,

        // ═══════════════════════════════════════════════════════════════════
        // COVERAGE - per shift type bounds
        // Each shift type (L01-L04) has minPerDay / maxPerDay — total shifts
        // assigned per day. Per-staff weekly cap is enforced via
        // maxShiftsPerStaff (CONSTRAINTS block), not per-shift-type.
        // ═══════════════════════════════════════════════════════════════════

        /** L01: Minimum total shifts assigned per day. */
        int l01MinPerDay,
        /** L01: Maximum total shifts assigned per day. */
        int l01MaxPerDay,
        /** L01: Maximum shifts per staff per week for this shift type (0 = no limit). */
        int l01MaxPerWeek,

        /** L02: Minimum total shifts assigned per day. */
        int l02MinPerDay,
        /** L02: Maximum total shifts assigned per day. */
        int l02MaxPerDay,
        /** L02: Maximum shifts per staff per week for this shift type (0 = no limit). */
        int l02MaxPerWeek,

        /** L03: Minimum total shifts assigned per day. */
        int l03MinPerDay,
        /** L03: Maximum total shifts assigned per day. */
        int l03MaxPerDay,
        /** L03: Maximum shifts per staff per week for this shift type (0 = no limit). */
        int l03MaxPerWeek,

        /** L04: Minimum total shifts assigned per day. */
        int l04MinPerDay,
        /** L04: Maximum total shifts assigned per day. */
        int l04MaxPerDay,
        /** L04: Maximum shifts per staff per week for this shift type (0 = no limit). */
        int l04MaxPerWeek,

        // ═══════════════════════════════════════════════════════════════════
        // L04 - Expert Clinic (cross-specialty specific)
        // ═══════════════════════════════════════════════════════════════════

        /** Enable cross-specialty assignment for L04. Default: false. */
        boolean l04CrossSpecialtyEnabled,

        /** Max ratio of L04 shifts that can be cross-specialty (0.0-1.0). Default: 0.3. */
        double l04CrossSpecialtyRatio,

        /** Allowed specialty IDs for cross-specialty L04 assignment. */
        String[] l04AllowedSpecialties,

        /** Balance strategy for L04: STRICT_MATCH_ONLY, FAIR_DISTRIBUTE, WEIGHTED_FAIR. */
        String l04BalanceStrategy,

        // ═══════════════════════════════════════════════════════════════════
        // CONSTRAINTS
        // ═══════════════════════════════════════════════════════════════════

        /** Hours between consecutive L01 shifts. Default: 24. */
        int overnightRecoveryHours,

        /** Greedy algorithm early-stop coverage threshold. Default: 0.85. */
        double greedyCoverageThreshold,

        /** Min staff per shift (monitoring only, 0 = unlimited). Default: 0. */
        int minStaffPerShift,

        /** Max staff per shift (0 = unlimited). Default: 0. */
        int maxStaffPerShift,

        /** Min shifts per staff per month (monitoring only, 0 = unlimited). Default: 0. */
        int minShiftsPerStaff,

        /** Max shifts per staff per month (0 = unlimited). Default: 0. */
        int maxShiftsPerStaff,

        // ═══════════════════════════════════════════════════════════════════
        // PERFORMANCE
        // ═══════════════════════════════════════════════════════════════════

        /** Max execution time in seconds. Default: 60. */
        int timeLimitSeconds,

        /** Candidate list size for neighborhood generation. Default: 50. */
        int candidateListSize
) {

    /** Number of shift types supported. */
    public static final int SHIFT_TYPE_COUNT = 4;

    /** Shift type IDs in order. */
    public static final String[] SHIFT_TYPE_IDS = {"L01", "L02", "L03", "L04"};

    /**
     * Creates ConfigDomain with safe defaults.
     * All numeric fields default to 0; booleans default to false.
     * Callers should use {@link ConfigDefaults} to fill in sensible defaults.
     */
    public ConfigDomain {
        // Defensive: null arrays → empty arrays
        removedShiftTypes = removedShiftTypes != null ? removedShiftTypes.clone() : new String[0];
        l04AllowedSpecialties = l04AllowedSpecialties != null ? l04AllowedSpecialties.clone() : new String[0];

        // Defensive: null string → empty string
        holidayMode = holidayMode != null ? holidayMode : "";
        acceptanceStrategy = acceptanceStrategy != null ? acceptanceStrategy : "";
        l04BalanceStrategy = l04BalanceStrategy != null ? l04BalanceStrategy : "";
    }

    /**
     * Returns the minPerDay for a given shift type.
     * @param shiftTypeId L01, L02, L03, or L04
     * @return minPerDay value
     * @throws IllegalArgumentException if shiftTypeId is invalid
     */
    public int getMinPerDay(String shiftTypeId) {
        return switch (shiftTypeId) {
            case "L01" -> l01MinPerDay;
            case "L02" -> l02MinPerDay;
            case "L03" -> l03MinPerDay;
            case "L04" -> l04MinPerDay;
            default -> throw new IllegalArgumentException("Unknown shift type: " + shiftTypeId);
        };
    }

    /**
     * Returns the maxPerDay for a given shift type.
     */
    public int getMaxPerDay(String shiftTypeId) {
        return switch (shiftTypeId) {
            case "L01" -> l01MaxPerDay;
            case "L02" -> l02MaxPerDay;
            case "L03" -> l03MaxPerDay;
            case "L04" -> l04MaxPerDay;
            default -> throw new IllegalArgumentException("Unknown shift type: " + shiftTypeId);
        };
    }

    /**
     * Check if a shift type is removed from auto-generation.
     */
    public boolean isShiftTypeRemoved(String shiftTypeId) {
        for (String removed : removedShiftTypes) {
            if (removed.equalsIgnoreCase(shiftTypeId)) return true;
        }
        return false;
    }

    /**
     * Builder pattern for ConfigDomain.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enabled = false;
        private String holidayMode = "";
        private String[] removedShiftTypes = new String[0];

        private int maxIterations = 0;
        private int neighborhoodSize = 0;
        private int tabuTenureMin = 0;
        private int tabuTenureMax = 0;
        private int maxNoImproveIterations = 0;
        private double relativeImprovementThreshold = 0;
        private int diversifyAfterIterations = 0;
        private String acceptanceStrategy = "";
        private double saInitialTemperature = 0;
        private double saCoolingRate = 0;
        private double saTemperatureMin = 0;
        private int laMemorySize = 0;
        private double gdInitialLevel = 0;
        private double gdDecayRate = 0;
        private double gdMinLevel = 0;

        private double cvTarget = 0;
        private double cvWorst = 0;
        private double weekendWeight = 0;

        private int l01MinPerDay = 0, l01MaxPerDay = 0, l01MaxPerWeek = 0;
        private int l02MinPerDay = 0, l02MaxPerDay = 0, l02MaxPerWeek = 0;
        private int l03MinPerDay = 0, l03MaxPerDay = 0, l03MaxPerWeek = 0;
        private int l04MinPerDay = 0, l04MaxPerDay = 0, l04MaxPerWeek = 0;

        private boolean l04CrossSpecialtyEnabled = false;
        private double l04CrossSpecialtyRatio = 0;
        private String[] l04AllowedSpecialties = new String[0];
        private String l04BalanceStrategy = "";

        private int overnightRecoveryHours = 0;
        private double greedyCoverageThreshold = 0;
        private int minStaffPerShift = 0, maxStaffPerShift = 0;
        private int minShiftsPerStaff = 0, maxShiftsPerStaff = 0;

        private int timeLimitSeconds = 0;
        private int candidateListSize = 0;

        public Builder from(ConfigDomain other) {
            this.enabled = other.enabled;
            this.holidayMode = other.holidayMode;
            this.removedShiftTypes = other.removedShiftTypes.clone();
            this.maxIterations = other.maxIterations;
            this.neighborhoodSize = other.neighborhoodSize;
            this.tabuTenureMin = other.tabuTenureMin;
            this.tabuTenureMax = other.tabuTenureMax;
            this.maxNoImproveIterations = other.maxNoImproveIterations;
            this.relativeImprovementThreshold = other.relativeImprovementThreshold;
            this.diversifyAfterIterations = other.diversifyAfterIterations;
            this.acceptanceStrategy = other.acceptanceStrategy;
            this.saInitialTemperature = other.saInitialTemperature;
            this.saCoolingRate = other.saCoolingRate;
            this.saTemperatureMin = other.saTemperatureMin;
            this.laMemorySize = other.laMemorySize;
            this.gdInitialLevel = other.gdInitialLevel;
            this.gdDecayRate = other.gdDecayRate;
            this.gdMinLevel = other.gdMinLevel;
            this.cvTarget = other.cvTarget;
            this.cvWorst = other.cvWorst;
            this.weekendWeight = other.weekendWeight;
            this.l01MinPerDay = other.l01MinPerDay;
            this.l01MaxPerDay = other.l01MaxPerDay;
            this.l01MaxPerWeek = other.l01MaxPerWeek;
            this.l02MinPerDay = other.l02MinPerDay;
            this.l02MaxPerDay = other.l02MaxPerDay;
            this.l02MaxPerWeek = other.l02MaxPerWeek;
            this.l03MinPerDay = other.l03MinPerDay;
            this.l03MaxPerDay = other.l03MaxPerDay;
            this.l03MaxPerWeek = other.l03MaxPerWeek;
            this.l04MinPerDay = other.l04MinPerDay;
            this.l04MaxPerDay = other.l04MaxPerDay;
            this.l04MaxPerWeek = other.l04MaxPerWeek;
            this.l04CrossSpecialtyEnabled = other.l04CrossSpecialtyEnabled;
            this.l04CrossSpecialtyRatio = other.l04CrossSpecialtyRatio;
            this.l04AllowedSpecialties = other.l04AllowedSpecialties.clone();
            this.l04BalanceStrategy = other.l04BalanceStrategy;
            this.overnightRecoveryHours = other.overnightRecoveryHours;
            this.greedyCoverageThreshold = other.greedyCoverageThreshold;
            this.minStaffPerShift = other.minStaffPerShift;
            this.maxStaffPerShift = other.maxStaffPerShift;
            this.minShiftsPerStaff = other.minShiftsPerStaff;
            this.maxShiftsPerStaff = other.maxShiftsPerStaff;
            this.timeLimitSeconds = other.timeLimitSeconds;
            this.candidateListSize = other.candidateListSize;
            return this;
        }

        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder holidayMode(String v) { this.holidayMode = v; return this; }
        public Builder removedShiftTypes(String[] v) { this.removedShiftTypes = v; return this; }

        public Builder maxIterations(int v) { this.maxIterations = v; return this; }
        public Builder neighborhoodSize(int v) { this.neighborhoodSize = v; return this; }
        public Builder tabuTenureMin(int v) { this.tabuTenureMin = v; return this; }
        public Builder tabuTenureMax(int v) { this.tabuTenureMax = v; return this; }
        public Builder maxNoImproveIterations(int v) { this.maxNoImproveIterations = v; return this; }
        public Builder relativeImprovementThreshold(double v) { this.relativeImprovementThreshold = v; return this; }
        public Builder diversifyAfterIterations(int v) { this.diversifyAfterIterations = v; return this; }
        public Builder acceptanceStrategy(String v) { this.acceptanceStrategy = v; return this; }
        public Builder saInitialTemperature(double v) { this.saInitialTemperature = v; return this; }
        public Builder saCoolingRate(double v) { this.saCoolingRate = v; return this; }
        public Builder saTemperatureMin(double v) { this.saTemperatureMin = v; return this; }
        public Builder laMemorySize(int v) { this.laMemorySize = v; return this; }
        public Builder gdInitialLevel(double v) { this.gdInitialLevel = v; return this; }
        public Builder gdDecayRate(double v) { this.gdDecayRate = v; return this; }
        public Builder gdMinLevel(double v) { this.gdMinLevel = v; return this; }

        public Builder cvTarget(double v) { this.cvTarget = v; return this; }
        public Builder cvWorst(double v) { this.cvWorst = v; return this; }
        public Builder weekendWeight(double v) { this.weekendWeight = v; return this; }

        public Builder l01MinPerDay(int v) { this.l01MinPerDay = v; return this; }
        public Builder l01MaxPerDay(int v) { this.l01MaxPerDay = v; return this; }
        public Builder l01MaxPerWeek(int v) { this.l01MaxPerWeek = v; return this; }
        public Builder l02MinPerDay(int v) { this.l02MinPerDay = v; return this; }
        public Builder l02MaxPerDay(int v) { this.l02MaxPerDay = v; return this; }
        public Builder l02MaxPerWeek(int v) { this.l02MaxPerWeek = v; return this; }
        public Builder l03MinPerDay(int v) { this.l03MinPerDay = v; return this; }
        public Builder l03MaxPerDay(int v) { this.l03MaxPerDay = v; return this; }
        public Builder l03MaxPerWeek(int v) { this.l03MaxPerWeek = v; return this; }
        public Builder l04MinPerDay(int v) { this.l04MinPerDay = v; return this; }
        public Builder l04MaxPerDay(int v) { this.l04MaxPerDay = v; return this; }
        public Builder l04MaxPerWeek(int v) { this.l04MaxPerWeek = v; return this; }

        public Builder l04CrossSpecialtyEnabled(boolean v) { this.l04CrossSpecialtyEnabled = v; return this; }
        public Builder l04CrossSpecialtyRatio(double v) { this.l04CrossSpecialtyRatio = v; return this; }
        public Builder l04AllowedSpecialties(String[] v) { this.l04AllowedSpecialties = v; return this; }
        public Builder l04BalanceStrategy(String v) { this.l04BalanceStrategy = v; return this; }

        public Builder overnightRecoveryHours(int v) { this.overnightRecoveryHours = v; return this; }
        public Builder greedyCoverageThreshold(double v) { this.greedyCoverageThreshold = v; return this; }
        public Builder minStaffPerShift(int v) { this.minStaffPerShift = v; return this; }
        public Builder maxStaffPerShift(int v) { this.maxStaffPerShift = v; return this; }
        public Builder minShiftsPerStaff(int v) { this.minShiftsPerStaff = v; return this; }
        public Builder maxShiftsPerStaff(int v) { this.maxShiftsPerStaff = v; return this; }

        public Builder timeLimitSeconds(int v) { this.timeLimitSeconds = v; return this; }
        public Builder candidateListSize(int v) { this.candidateListSize = v; return this; }

        public ConfigDomain build() {
            return new ConfigDomain(
                    enabled, holidayMode, removedShiftTypes,
                    maxIterations, neighborhoodSize, tabuTenureMin, tabuTenureMax,
                    maxNoImproveIterations, relativeImprovementThreshold, diversifyAfterIterations,
                    acceptanceStrategy,
                    saInitialTemperature, saCoolingRate, saTemperatureMin,
                    laMemorySize, gdInitialLevel, gdDecayRate, gdMinLevel,
                    cvTarget, cvWorst, weekendWeight,
                    l01MinPerDay, l01MaxPerDay, l01MaxPerWeek,
                    l02MinPerDay, l02MaxPerDay, l02MaxPerWeek,
                    l03MinPerDay, l03MaxPerDay, l03MaxPerWeek,
                    l04MinPerDay, l04MaxPerDay, l04MaxPerWeek,
                    l04CrossSpecialtyEnabled, l04CrossSpecialtyRatio,
                    l04AllowedSpecialties, l04BalanceStrategy,
                    overnightRecoveryHours, greedyCoverageThreshold,
                    minStaffPerShift, maxStaffPerShift, minShiftsPerStaff, maxShiftsPerStaff,
                    timeLimitSeconds, candidateListSize
            );
        }
    }
}
