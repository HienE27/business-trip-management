package com.hospital.scheduler.scheduling.config;

/**
 * Sensible default values for ConfigDomain.
 * All defaults are defined in ONE place — no magic numbers in services.
 */
public final class ConfigDefaults {

    private ConfigDefaults() {}

    // GENERAL
    public static final boolean  ENABLED                      = true;
    public static final String   HOLIDAY_MODE                 = "SKIP";
    public static final String[] REMOVED_SHIFT_TYPES          = {};

    // ALGORITHM
    public static final int     MAX_ITERATIONS               = 500;
    public static final int     NEIGHBORHOOD_SIZE             = 10;
    public static final int     TABU_TENURE_MIN               = 5;
    public static final int     TABU_TENURE_MAX               = 10;
    public static final int     MAX_NO_IMPROVE_ITERATIONS     = 50;
    public static final double   RELATIVE_IMPROVEMENT_THRESHOLD = 0.001;
    public static final int     DIVERSIFY_AFTER_ITERATIONS    = 20;

    // ACCEPTANCE STRATEGY
    public static final String   ACCEPTANCE_STRATEGY          = "TABU";
    public static final double   SA_INITIAL_TEMPERATURE        = 100.0;
    public static final double   SA_COOLING_RATE              = 0.9995;
    public static final double   SA_TEMPERATURE_MIN           = 0.01;
    public static final int     LA_MEMORY_SIZE                = 10;
    public static final double   GD_INITIAL_LEVEL              = 1000.0;
    public static final double   GD_DECAY_RATE                 = 0.999;
    public static final double   GD_MIN_LEVEL                  = 0.0;

    // FAIRNESS
    public static final double   CV_TARGET                    = 0.10;
    public static final double   CV_WORST                     = 0.50;
    public static final double   WEEKEND_WEIGHT               = 2.0;

    // COVERAGE - L01
    public static final int     L01_MIN_PER_DAY               = 1;
    public static final int     L01_MAX_PER_DAY               = 10;
    public static final int     L01_MIN_PER_WEEK              = 1;
    public static final int     L01_MAX_PER_WEEK              = 3;

    // COVERAGE - L02
    public static final int     L02_MIN_PER_DAY               = 1;
    public static final int     L02_MAX_PER_DAY               = 10;
    public static final int     L02_MIN_PER_WEEK              = 1;
    public static final int     L02_MAX_PER_WEEK              = 3;

    // COVERAGE - L03
    public static final int     L03_MIN_PER_DAY               = 1;
    public static final int     L03_MAX_PER_DAY               = 10;
    public static final int     L03_MIN_PER_WEEK              = 1;
    public static final int     L03_MAX_PER_WEEK              = 3;

    // COVERAGE - L04
    public static final int     L04_MIN_PER_DAY               = 1;
    public static final int     L04_MAX_PER_DAY               = 10;
    public static final int     L04_MIN_PER_WEEK              = 1;
    public static final int     L04_MAX_PER_WEEK              = 3;

    // L04 CROSS-SPECIALTY
    public static final boolean L04_CROSS_SPECIALTY_ENABLED   = false;
    public static final double  L04_CROSS_SPECIALTY_RATIO    = 0.30;
    public static final String[] L04_ALLOWED_SPECIALTIES      = {};
    public static final String   L04_BALANCE_STRATEGY          = "FAIR_DISTRIBUTE";

    // CONSTRAINTS
    public static final int     OVERNIGHT_RECOVERY_HOURS      = 24;
    public static final double GREEDY_COVERAGE_THRESHOLD      = 0.85;
    public static final int     MIN_STAFF_PER_SHIFT           = 0;
    public static final int     MAX_STAFF_PER_SHIFT           = 0;
    public static final int     MIN_SHIFTS_PER_STAFF          = 0;
    public static final int     MAX_SHIFTS_PER_STAFF          = 0;

    // PERFORMANCE
    public static final int     TIME_LIMIT_SECONDS            = 60;
    public static final int     CANDIDATE_LIST_SIZE           = 50;

    /**
     * Creates ConfigDomain with all defaults.
     */
    public static ConfigDomain withDefaults() {
        return ConfigDomain.builder()
                .enabled(ENABLED)
                .holidayMode(HOLIDAY_MODE)
                .removedShiftTypes(REMOVED_SHIFT_TYPES)
                .maxIterations(MAX_ITERATIONS)
                .neighborhoodSize(NEIGHBORHOOD_SIZE)
                .tabuTenureMin(TABU_TENURE_MIN)
                .tabuTenureMax(TABU_TENURE_MAX)
                .maxNoImproveIterations(MAX_NO_IMPROVE_ITERATIONS)
                .relativeImprovementThreshold(RELATIVE_IMPROVEMENT_THRESHOLD)
                .diversifyAfterIterations(DIVERSIFY_AFTER_ITERATIONS)
                .acceptanceStrategy(ACCEPTANCE_STRATEGY)
                .saInitialTemperature(SA_INITIAL_TEMPERATURE)
                .saCoolingRate(SA_COOLING_RATE)
                .saTemperatureMin(SA_TEMPERATURE_MIN)
                .laMemorySize(LA_MEMORY_SIZE)
                .gdInitialLevel(GD_INITIAL_LEVEL)
                .gdDecayRate(GD_DECAY_RATE)
                .gdMinLevel(GD_MIN_LEVEL)
                .cvTarget(CV_TARGET)
                .cvWorst(CV_WORST)
                .weekendWeight(WEEKEND_WEIGHT)
                .l01MinPerDay(L01_MIN_PER_DAY).l01MaxPerDay(L01_MAX_PER_DAY)
                .l01MinPerWeek(L01_MIN_PER_WEEK).l01MaxPerWeek(L01_MAX_PER_WEEK)
                .l02MinPerDay(L02_MIN_PER_DAY).l02MaxPerDay(L02_MAX_PER_DAY)
                .l02MinPerWeek(L02_MIN_PER_WEEK).l02MaxPerWeek(L02_MAX_PER_WEEK)
                .l03MinPerDay(L03_MIN_PER_DAY).l03MaxPerDay(L03_MAX_PER_DAY)
                .l03MinPerWeek(L03_MIN_PER_WEEK).l03MaxPerWeek(L03_MAX_PER_WEEK)
                .l04MinPerDay(L04_MIN_PER_DAY).l04MaxPerDay(L04_MAX_PER_DAY)
                .l04MinPerWeek(L04_MIN_PER_WEEK).l04MaxPerWeek(L04_MAX_PER_WEEK)
                .l04CrossSpecialtyEnabled(L04_CROSS_SPECIALTY_ENABLED)
                .l04CrossSpecialtyRatio(L04_CROSS_SPECIALTY_RATIO)
                .l04AllowedSpecialties(L04_ALLOWED_SPECIALTIES)
                .l04BalanceStrategy(L04_BALANCE_STRATEGY)
                .overnightRecoveryHours(OVERNIGHT_RECOVERY_HOURS)
                .greedyCoverageThreshold(GREEDY_COVERAGE_THRESHOLD)
                .minStaffPerShift(MIN_STAFF_PER_SHIFT)
                .maxStaffPerShift(MAX_STAFF_PER_SHIFT)
                .minShiftsPerStaff(MIN_SHIFTS_PER_STAFF)
                .maxShiftsPerStaff(MAX_SHIFTS_PER_STAFF)
                .timeLimitSeconds(TIME_LIMIT_SECONDS)
                .candidateListSize(CANDIDATE_LIST_SIZE)
                .build();
    }
}
