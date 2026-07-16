package com.hospital.scheduler.scheduling.config;

import java.util.*;

/**
 * Bidirectional mapper between {@link ConfigDomain} and the {@code algorithm_config} table.
 *
 * <p>Design: This mapper is the ONLY place that defines the mapping between ConfigDomain
 * field paths and algorithm_config param_key values. No duplication in services, DTOs, or UI.
 *
 * <p>Direction 1: ConfigDomain → algorithm_config rows (save)
 * <p>Direction 2: algorithm_config rows → ConfigDomain (load)
 *
 * <p>The param_key naming convention groups keys by category:
 * <pre>
 * auto_gen.*          → AutoGenConfig (holidayMode, removedShiftTypes, L01-L04 bounds)
 * scheduling.*       → SchedulingConfig (algorithm params)
 * fairness.*         → Fairness targets (CV, weekend weight)
 * l04.*              → L04 cross-specialty config
 * constraint.*       → Constraint params (recovery, limits)
 * performance.*       → Performance params (time limit, candidate size)
 * runtime.*          → Runtime overrides
 * </pre>
 */
public final class ConfigMapper {

    private ConfigMapper() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // Field Path → ParamKey mapping
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Map ConfigDomain field path → algorithm_config param_key.
     */
    public static String toParamKey(String fieldPath) {
        return SWITCH.getOrDefault(fieldPath, fieldPath);
    }

    /**
     * Map algorithm_config param_key → ConfigDomain field path.
     */
    public static String toFieldPath(String paramKey) {
        return REVERSE.getOrDefault(paramKey, paramKey);
    }

    // ─── The canonical mapping ────────────────────────────────────────────────

    private static final Map<String, String> SWITCH = Map.ofEntries(
            // GENERAL
            Map.entry("enabled",              "auto_gen_enabled"),
            Map.entry("holidayMode",          "auto_gen_holiday_mode"),
            Map.entry("removedShiftTypes",    "auto_gen_removed_shift_types"),

            // ALGORITHM
            Map.entry("algorithm.maxIterations",              "scheduling_max_iterations"),
            Map.entry("algorithm.neighborhoodSize",          "scheduling_neighborhood_size"),
            Map.entry("algorithm.tabuTenureMin",            "scheduling_tabu_tenure_min"),
            Map.entry("algorithm.tabuTenureMax",            "scheduling_tabu_tenure_max"),
            Map.entry("algorithm.maxNoImproveIterations",    "scheduling_max_no_improve"),
            Map.entry("algorithm.relativeImprovementThreshold", "scheduling_rel_improvement_threshold"),
            Map.entry("algorithm.diversifyAfterIterations",  "scheduling_diversify_after"),

            // ACCEPTANCE STRATEGY
            Map.entry("acceptanceStrategy.kind",               "scheduling_acceptance_strategy"),
            Map.entry("acceptanceStrategy.saInitialTemperature", "scheduling_sa_initial_temp"),
            Map.entry("acceptanceStrategy.saCoolingRate",       "scheduling_sa_cooling_rate"),
            Map.entry("acceptanceStrategy.saTemperatureMin",    "scheduling_sa_temp_min"),
            Map.entry("acceptanceStrategy.laMemorySize",       "scheduling_la_memory_size"),
            Map.entry("acceptanceStrategy.gdInitialLevel",     "scheduling_gd_initial_level"),
            Map.entry("acceptanceStrategy.gdDecayRate",        "scheduling_gd_decay_rate"),
            Map.entry("acceptanceStrategy.gdMinLevel",         "scheduling_gd_min_level"),

            // FAIRNESS
            Map.entry("fairness.cvTarget",    "balance_score_target"),
            Map.entry("fairness.cvWorst",      "balance_score_worst"),
            Map.entry("fairness.weekendWeight","weekend_weight"),

            // COVERAGE - L01
            Map.entry("coverage.l01.minPerDay",  "auto_gen_l01_min_per_day"),
            Map.entry("coverage.l01.maxPerDay",  "auto_gen_l01_max_per_day"),
            Map.entry("coverage.l01.minPerWeek", "auto_gen_l01_min_per_week"),
            Map.entry("coverage.l01.maxPerWeek", "auto_gen_l01_max_per_week"),

            // COVERAGE - L02
            Map.entry("coverage.l02.minPerDay",  "auto_gen_l02_min_per_day"),
            Map.entry("coverage.l02.maxPerDay",  "auto_gen_l02_max_per_day"),
            Map.entry("coverage.l02.minPerWeek", "auto_gen_l02_min_per_week"),
            Map.entry("coverage.l02.maxPerWeek", "auto_gen_l02_max_per_week"),

            // COVERAGE - L03
            Map.entry("coverage.l03.minPerDay",  "auto_gen_l03_min_per_day"),
            Map.entry("coverage.l03.maxPerDay",  "auto_gen_l03_max_per_day"),
            Map.entry("coverage.l03.minPerWeek", "auto_gen_l03_min_per_week"),
            Map.entry("coverage.l03.maxPerWeek", "auto_gen_l03_max_per_week"),

            // COVERAGE - L04
            Map.entry("coverage.l04.minPerDay",  "auto_gen_l04_min_per_day"),
            Map.entry("coverage.l04.maxPerDay",  "auto_gen_l04_max_per_day"),
            Map.entry("coverage.l04.minPerWeek", "auto_gen_l04_min_per_week"),
            Map.entry("coverage.l04.maxPerWeek", "auto_gen_l04_max_per_week"),

            // L04 CROSS-SPECIALTY
            Map.entry("l04.crossSpecialtyEnabled",   "auto_gen_l04_cross_specialty"),
            Map.entry("l04.crossSpecialtyRatio",     "auto_gen_l04_cross_specialty_ratio"),
            Map.entry("l04.allowedSpecialties",      "auto_gen_l04_allowed_specialties"),
            Map.entry("l04.balanceStrategy",         "auto_gen_l04_balance_strategy"),

            // CONSTRAINTS
            Map.entry("constraints.overnightRecoveryHours",  "overnight_recovery_hours"),
            Map.entry("constraints.autoCompensationEnabled", "auto_compensation_enabled"),
            Map.entry("constraints.greedyCoverageThreshold","greedy_coverage_threshold"),
            Map.entry("constraints.minStaffPerShift",       "min_staff_per_shift"),
            Map.entry("constraints.maxStaffPerShift",       "max_staff_per_shift"),
            Map.entry("constraints.minShiftsPerStaff",       "min_shifts_per_staff"),
            Map.entry("constraints.maxShiftsPerStaff",       "max_shifts_per_staff"),

            // PERFORMANCE
            Map.entry("performance.timeLimitSeconds",   "scheduling_time_limit_seconds"),
            Map.entry("performance.candidateListSize", "scheduling_candidate_list_size")
    );

    private static final Map<String, String> REVERSE = buildReverse(SWITCH);

    private static Map<String, String> buildReverse(Map<String, String> forward) {
        Map<String, String> rev = new HashMap<>();
        for (Map.Entry<String, String> e : forward.entrySet()) {
            rev.put(e.getValue(), e.getKey());
        }
        return rev;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ConfigDomain → Map<paramKey, String>
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Serialize ConfigDomain to a map of paramKey → value (as String).
     * Used for persisting to algorithm_config table.
     */
    public static Map<String, String> toParamMap(ConfigDomain config) {
        Map<String, String> result = new LinkedHashMap<>();
        ConfigDomain d = config;

        // GENERAL
        put(result, "enabled",                 String.valueOf(d.enabled()));
        put(result, "holidayMode",             d.holidayMode());
        put(result, "removedShiftTypes",        join(d.removedShiftTypes()));

        // ALGORITHM
        put(result, "algorithm.maxIterations",              String.valueOf(d.maxIterations()));
        put(result, "algorithm.neighborhoodSize",           String.valueOf(d.neighborhoodSize()));
        put(result, "algorithm.tabuTenureMin",             String.valueOf(d.tabuTenureMin()));
        put(result, "algorithm.tabuTenureMax",             String.valueOf(d.tabuTenureMax()));
        put(result, "algorithm.maxNoImproveIterations",    String.valueOf(d.maxNoImproveIterations()));
        put(result, "algorithm.relativeImprovementThreshold", String.valueOf(d.relativeImprovementThreshold()));
        put(result, "algorithm.diversifyAfterIterations",  String.valueOf(d.diversifyAfterIterations()));

        // ACCEPTANCE STRATEGY
        put(result, "acceptanceStrategy.kind",               d.acceptanceStrategy());
        put(result, "acceptanceStrategy.saInitialTemperature", String.valueOf(d.saInitialTemperature()));
        put(result, "acceptanceStrategy.saCoolingRate",       String.valueOf(d.saCoolingRate()));
        put(result, "acceptanceStrategy.saTemperatureMin",    String.valueOf(d.saTemperatureMin()));
        put(result, "acceptanceStrategy.laMemorySize",       String.valueOf(d.laMemorySize()));
        put(result, "acceptanceStrategy.gdInitialLevel",     String.valueOf(d.gdInitialLevel()));
        put(result, "acceptanceStrategy.gdDecayRate",        String.valueOf(d.gdDecayRate()));
        put(result, "acceptanceStrategy.gdMinLevel",         String.valueOf(d.gdMinLevel()));

        // FAIRNESS
        put(result, "fairness.cvTarget",    String.valueOf(d.cvTarget()));
        put(result, "fairness.cvWorst",     String.valueOf(d.cvWorst()));
        put(result, "fairness.weekendWeight", String.valueOf(d.weekendWeight()));

        // COVERAGE - L01
        put(result, "coverage.l01.minPerDay",  String.valueOf(d.l01MinPerDay()));
        put(result, "coverage.l01.maxPerDay",  String.valueOf(d.l01MaxPerDay()));
        put(result, "coverage.l01.minPerWeek", String.valueOf(d.l01MinPerWeek()));
        put(result, "coverage.l01.maxPerWeek", String.valueOf(d.l01MaxPerWeek()));

        // COVERAGE - L02
        put(result, "coverage.l02.minPerDay",  String.valueOf(d.l02MinPerDay()));
        put(result, "coverage.l02.maxPerDay",  String.valueOf(d.l02MaxPerDay()));
        put(result, "coverage.l02.minPerWeek", String.valueOf(d.l02MinPerWeek()));
        put(result, "coverage.l02.maxPerWeek", String.valueOf(d.l02MaxPerWeek()));

        // COVERAGE - L03
        put(result, "coverage.l03.minPerDay",  String.valueOf(d.l03MinPerDay()));
        put(result, "coverage.l03.maxPerDay",  String.valueOf(d.l03MaxPerDay()));
        put(result, "coverage.l03.minPerWeek", String.valueOf(d.l03MinPerWeek()));
        put(result, "coverage.l03.maxPerWeek", String.valueOf(d.l03MaxPerWeek()));

        // COVERAGE - L04
        put(result, "coverage.l04.minPerDay",  String.valueOf(d.l04MinPerDay()));
        put(result, "coverage.l04.maxPerDay",  String.valueOf(d.l04MaxPerDay()));
        put(result, "coverage.l04.minPerWeek", String.valueOf(d.l04MinPerWeek()));
        put(result, "coverage.l04.maxPerWeek", String.valueOf(d.l04MaxPerWeek()));

        // L04 CROSS-SPECIALTY
        put(result, "l04.crossSpecialtyEnabled",   String.valueOf(d.l04CrossSpecialtyEnabled()));
        put(result, "l04.crossSpecialtyRatio",     String.valueOf(d.l04CrossSpecialtyRatio()));
        put(result, "l04.allowedSpecialties",      join(d.l04AllowedSpecialties()));
        put(result, "l04.balanceStrategy",         d.l04BalanceStrategy());

        // CONSTRAINTS
        put(result, "constraints.overnightRecoveryHours",  String.valueOf(d.overnightRecoveryHours()));
        put(result, "constraints.autoCompensationEnabled", String.valueOf(d.autoCompensationEnabled()));
        put(result, "constraints.greedyCoverageThreshold", String.valueOf(d.greedyCoverageThreshold()));
        put(result, "constraints.minStaffPerShift",       String.valueOf(d.minStaffPerShift()));
        put(result, "constraints.maxStaffPerShift",       String.valueOf(d.maxStaffPerShift()));
        put(result, "constraints.minShiftsPerStaff",       String.valueOf(d.minShiftsPerStaff()));
        put(result, "constraints.maxShiftsPerStaff",       String.valueOf(d.maxShiftsPerStaff()));

        // PERFORMANCE
        put(result, "performance.timeLimitSeconds",   String.valueOf(d.timeLimitSeconds()));
        put(result, "performance.candidateListSize", String.valueOf(d.candidateListSize()));

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Map<paramKey, String> → ConfigDomain
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Deserialize a map of paramKey → value (as String) to ConfigDomain.
     * Unknown keys are ignored. Missing keys default to 0/false/empty.
     */
    public static ConfigDomain fromParamMap(Map<String, String> paramMap) {
        ConfigDomain.Builder b = ConfigDomain.builder();

        // GENERAL
        b.enabled(boolOf(paramMap, "enabled", true));
        b.holidayMode(strOf(paramMap, "holidayMode", "SKIP"));
        b.removedShiftTypes(splitOf(paramMap, "removedShiftTypes"));

        // ALGORITHM
        b.maxIterations(intOf(paramMap, "algorithm.maxIterations", 500));
        b.neighborhoodSize(intOf(paramMap, "algorithm.neighborhoodSize", 10));
        b.tabuTenureMin(intOf(paramMap, "algorithm.tabuTenureMin", 5));
        b.tabuTenureMax(intOf(paramMap, "algorithm.tabuTenureMax", 10));
        b.maxNoImproveIterations(intOf(paramMap, "algorithm.maxNoImproveIterations", 50));
        b.relativeImprovementThreshold(doubleOf(paramMap, "algorithm.relativeImprovementThreshold", 0.001));
        b.diversifyAfterIterations(intOf(paramMap, "algorithm.diversifyAfterIterations", 20));

        // ACCEPTANCE STRATEGY
        b.acceptanceStrategy(strOf(paramMap, "acceptanceStrategy.kind", "TABU"));
        b.saInitialTemperature(doubleOf(paramMap, "acceptanceStrategy.saInitialTemperature", 100.0));
        b.saCoolingRate(doubleOf(paramMap, "acceptanceStrategy.saCoolingRate", 0.9995));
        b.saTemperatureMin(doubleOf(paramMap, "acceptanceStrategy.saTemperatureMin", 0.01));
        b.laMemorySize(intOf(paramMap, "acceptanceStrategy.laMemorySize", 10));
        b.gdInitialLevel(doubleOf(paramMap, "acceptanceStrategy.gdInitialLevel", 1000.0));
        b.gdDecayRate(doubleOf(paramMap, "acceptanceStrategy.gdDecayRate", 0.999));
        b.gdMinLevel(doubleOf(paramMap, "acceptanceStrategy.gdMinLevel", 0.0));

        // FAIRNESS
        b.cvTarget(doubleOf(paramMap, "fairness.cvTarget", 0.10));
        b.cvWorst(doubleOf(paramMap, "fairness.cvWorst", 0.50));
        b.weekendWeight(doubleOf(paramMap, "fairness.weekendWeight", 2.0));

        // COVERAGE - L01
        b.l01MinPerDay(intOf(paramMap, "coverage.l01.minPerDay", 1));
        b.l01MaxPerDay(intOf(paramMap, "coverage.l01.maxPerDay", 10));
        b.l01MinPerWeek(intOf(paramMap, "coverage.l01.minPerWeek", 1));
        b.l01MaxPerWeek(intOf(paramMap, "coverage.l01.maxPerWeek", 3));

        // COVERAGE - L02
        b.l02MinPerDay(intOf(paramMap, "coverage.l02.minPerDay", 1));
        b.l02MaxPerDay(intOf(paramMap, "coverage.l02.maxPerDay", 10));
        b.l02MinPerWeek(intOf(paramMap, "coverage.l02.minPerWeek", 1));
        b.l02MaxPerWeek(intOf(paramMap, "coverage.l02.maxPerWeek", 3));

        // COVERAGE - L03
        b.l03MinPerDay(intOf(paramMap, "coverage.l03.minPerDay", 1));
        b.l03MaxPerDay(intOf(paramMap, "coverage.l03.maxPerDay", 10));
        b.l03MinPerWeek(intOf(paramMap, "coverage.l03.minPerWeek", 1));
        b.l03MaxPerWeek(intOf(paramMap, "coverage.l03.maxPerWeek", 3));

        // COVERAGE - L04
        b.l04MinPerDay(intOf(paramMap, "coverage.l04.minPerDay", 1));
        b.l04MaxPerDay(intOf(paramMap, "coverage.l04.maxPerDay", 10));
        b.l04MinPerWeek(intOf(paramMap, "coverage.l04.minPerWeek", 1));
        b.l04MaxPerWeek(intOf(paramMap, "coverage.l04.maxPerWeek", 3));

        // L04 CROSS-SPECIALTY
        b.l04CrossSpecialtyEnabled(boolOf(paramMap, "l04.crossSpecialtyEnabled", false));
        b.l04CrossSpecialtyRatio(doubleOf(paramMap, "l04.crossSpecialtyRatio", 0.30));
        b.l04AllowedSpecialties(splitOf(paramMap, "l04.allowedSpecialties"));
        b.l04BalanceStrategy(strOf(paramMap, "l04.balanceStrategy", "FAIR_DISTRIBUTE"));

        // CONSTRAINTS
        b.overnightRecoveryHours(intOf(paramMap, "constraints.overnightRecoveryHours", 24));
        b.autoCompensationEnabled(boolOf(paramMap, "constraints.autoCompensationEnabled", true));
        b.greedyCoverageThreshold(doubleOf(paramMap, "constraints.greedyCoverageThreshold", 0.85));
        b.minStaffPerShift(intOf(paramMap, "constraints.minStaffPerShift", 0));
        b.maxStaffPerShift(intOf(paramMap, "constraints.maxStaffPerShift", 0));
        b.minShiftsPerStaff(intOf(paramMap, "constraints.minShiftsPerStaff", 0));
        b.maxShiftsPerStaff(intOf(paramMap, "constraints.maxShiftsPerStaff", 0));

        // PERFORMANCE
        b.timeLimitSeconds(intOf(paramMap, "performance.timeLimitSeconds", 60));
        b.candidateListSize(intOf(paramMap, "performance.candidateListSize", 50));

        return b.build();
    }

    /**
     * Create a diff between two ConfigDomain instances.
     * Returns a map of fieldPath → {oldValue, newValue} for fields that differ.
     */
    public static Map<String, DiffEntry> diff(ConfigDomain oldConfig, ConfigDomain newConfig) {
        Map<String, DiffEntry> result = new LinkedHashMap<>();
        Map<String, String> oldMap = toParamMap(oldConfig);
        Map<String, String> newMap = toParamMap(newConfig);

        for (String fieldPath : SWITCH.keySet()) {
            String pk = SWITCH.get(fieldPath);
            String oldVal = oldMap.get(pk);
            String newVal = newMap.get(pk);
            if (!Objects.equals(oldVal, newVal)) {
                result.put(fieldPath, new DiffEntry(oldVal, newVal));
            }
        }
        return result;
    }

    public record DiffEntry(String oldValue, String newValue) {}

    // ─── Primitive accessors with defaults ───────────────────────────────────

    private static void put(Map<String, String> m, String fieldPath, String value) {
        String pk = toParamKey(fieldPath);
        m.put(pk, value != null ? value : "");
    }

    private static boolean boolOf(Map<String, String> m, String fieldPath, boolean defaultVal) {
        String pk = toParamKey(fieldPath);
        String v = m.get(pk);
        if (v == null || v.isBlank()) return defaultVal;
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    private static int intOf(Map<String, String> m, String fieldPath, int defaultVal) {
        String pk = toParamKey(fieldPath);
        String v = m.get(pk);
        if (v == null || v.isBlank()) return defaultVal;
        try { return (int) Double.parseDouble(v); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private static double doubleOf(Map<String, String> m, String fieldPath, double defaultVal) {
        String pk = toParamKey(fieldPath);
        String v = m.get(pk);
        if (v == null || v.isBlank()) return defaultVal;
        try { return Double.parseDouble(v); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private static String strOf(Map<String, String> m, String fieldPath, String defaultVal) {
        String pk = toParamKey(fieldPath);
        String v = m.get(pk);
        return (v != null && !v.isBlank()) ? v : defaultVal;
    }

    private static String[] splitOf(Map<String, String> m, String fieldPath) {
        String pk = toParamKey(fieldPath);
        String v = m.get(pk);
        if (v == null || v.isBlank()) return new String[0];
        return Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private static String join(String[] arr) {
        return arr != null ? String.join(",", arr) : "";
    }
}
