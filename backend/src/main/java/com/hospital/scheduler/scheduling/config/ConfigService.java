package com.hospital.scheduler.scheduling.config;

import com.hospital.scheduler.service.AlgorithmConfigCrudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Unified configuration service — single entry point for all config operations.
 *
 * <p>This service replaces the fragmented config services:
 * <ul>
 *   <li>AlgorithmConfigService (CRUD)</li>
 *   <li>AutoGenConfigService</li>
 *   <li>RuntimeConfigService</li>
 * </ul>
 *
 * <p>API design:
 * <pre>
 * GET  /config          → load full config
 * PUT  /config          → save full config (validates first)
 * POST /config/validate → validate without saving
 * GET  /config/metadata → field metadata for UI
 * POST /config/reset    → reset to defaults
 * GET  /config/diff     → diff between current and another config
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final AlgorithmConfigCrudService crud;

    private final ConfigValidator validator = new ConfigValidator();

    // ─── Load ─────────────────────────────────────────────────────────────────

    /**
     * Load full ConfigDomain from algorithm_config table.
     * Merges persisted values with defaults for missing keys.
     */
    @Transactional(readOnly = true)
    public ConfigDomain load() {
        Map<String, String> paramMap = crud.loadConfigCache();
        ConfigDomain loaded = ConfigMapper.fromParamMap(paramMap);
        log.debug("Loaded config with {} keys", paramMap.size());
        return loaded;
    }

    /**
     * Load config and merge with provided overrides.
     * Useful for testing or one-off overrides without persisting.
     */
    public ConfigDomain load(Map<String, Object> overrides) {
        ConfigDomain base = load();
        return applyOverrides(base, overrides);
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    /**
     * Save full ConfigDomain to algorithm_config table.
     * Validates first; throws if validation errors exist.
     *
     * @throws ConfigValidationException if validation fails
     */
    @Transactional
    public ConfigDomain save(ConfigDomain config) {
        return save(config, false);
    }

    /**
     * Save with optional force (skip validation).
     */
    @Transactional
    public ConfigDomain save(ConfigDomain config, boolean force) {
        if (!force) {
            ConfigValidator.ValidationResult result = validator.validate(config);
            if (result.hasErrors()) {
                throw new ConfigValidationException(result);
            }
        }

        Map<String, String> paramMap = ConfigMapper.toParamMap(config);
        crud.upsertAll(paramMap);
        log.info("Saved config with {} parameters", paramMap.size());
        return config;
    }

    /**
     * Save only specific fields (partial update).
     * Other fields are preserved.
     */
    @Transactional
    public ConfigDomain savePartial(ConfigDomain partial) {
        ConfigDomain current = load();
        ConfigDomain merged = merge(current, partial);
        return save(merged);
    }

    // ─── Validate ───────────────────────────────────────────────────────────────

    /**
     * Validate ConfigDomain without saving.
     * Returns validation result with errors, warnings, and infos.
     */
    public ConfigValidator.ValidationResult validate(ConfigDomain config) {
        return validator.validate(config);
    }

    /**
     * Validate and throw if errors exist.
     */
    public void validateOrThrow(ConfigDomain config) {
        ConfigValidator.ValidationResult result = validate(config);
        if (result.hasErrors()) {
            throw new ConfigValidationException(result);
        }
    }

    // ─── Metadata ───────────────────────────────────────────────────────────────

    /**
     * Get all field metadata grouped by category.
     * Used by frontend to render forms dynamically.
     */
    public List<CategoryMetadata> getMetadata() {
        List<CategoryMetadata> result = new ArrayList<>();
        for (ConfigMetadata.ConfigCategory cat : ConfigMetadataRegistry.categories()) {
            List<ConfigMetadata> fields = ConfigMetadataRegistry.byCategory(cat);
            List<FieldMetadataDto> fieldDtos = new ArrayList<>();
            for (ConfigMetadata meta : fields) {
                fieldDtos.add(new FieldMetadataDto(
                        meta.fieldPath(),
                        meta.labelVi(),
                        meta.descriptionVi(),
                        meta.category().name(),
                        meta.renderType(),
                        meta.min(),
                        meta.max(),
                        meta.step(),
                        meta.defaultValue(),
                        meta.required(),
                        meta.visibleWhen(),
                        meta.editableWhen(),
                        meta.validationSeverity().name(),
                        toOptionDtos(meta.allowedValues())
                ));
            }
            result.add(new CategoryMetadata(
                    cat.name(),
                    cat.labelVi,
                    cat.labelEn,
                    cat.sortOrder,
                    fieldDtos
            ));
        }
        return result;
    }

    /**
     * Get metadata for a single field.
     */
    public ConfigMetadata getFieldMetadata(String fieldPath) {
        return ConfigMetadataRegistry.get(fieldPath);
    }

    // ─── Reset ────────────────────────────────────────────────────────────────

    /**
     * Reset all config to defaults.
     */
    @Transactional
    public ConfigDomain reset() {
        ConfigDomain defaults = ConfigDefaults.withDefaults();
        Map<String, String> paramMap = ConfigMapper.toParamMap(defaults);
        crud.upsertAll(paramMap);
        log.info("Reset config to defaults");
        return defaults;
    }

    /**
     * Reset specific field to its default value.
     */
    @Transactional
    public ConfigDomain resetField(String fieldPath) {
        ConfigDomain current = load();
        ConfigDomain defaults = ConfigDefaults.withDefaults();
        Object defaultVal = getFieldValue(defaults, fieldPath);
        ConfigDomain updated = setFieldValue(current, fieldPath, defaultVal);
        return save(updated);
    }

    // ─── Diff ─────────────────────────────────────────────────────────────────

    /**
     * Compare two configs and return changed fields.
     */
    public Map<String, ConfigMapper.DiffEntry> diff(ConfigDomain a, ConfigDomain b) {
        return ConfigMapper.diff(a, b);
    }

    /**
     * Get diff between current saved config and a proposed config.
     */
    public Map<String, ConfigMapper.DiffEntry> diffCurrent(ConfigDomain proposed) {
        ConfigDomain current = load();
        return diff(current, proposed);
    }

    // ─── Presets ──────────────────────────────────────────────────────────────

    /**
     * Built-in presets.
     */
    public enum Preset {
        BALANCED("balanced", "Cân bằng", "Balanced", 0.90, 0.75, 2.5),
        FAST("fast", "Nhanh", "Fast", 0.75, 0.60, 1.5),
        QUALITY("quality", "Chất lượng cao", "High Quality", 0.95, 0.85, 3.0),
        CONSERVATIVE("conservative", "Bảo thủ", "Conservative", 0.60, 0.50, 1.0);

        public final String key;
        public final String labelVi;
        public final String labelEn;
        public final double coverageTarget;
        public final double balanceScoreMin;
        public final double weekendWeight;

        Preset(String key, String labelVi, String labelEn,
               double coverageTarget, double balanceScoreMin, double weekendWeight) {
            this.key = key;
            this.labelVi = labelVi;
            this.labelEn = labelEn;
            this.coverageTarget = coverageTarget;
            this.balanceScoreMin = balanceScoreMin;
            this.weekendWeight = weekendWeight;
        }
    }

    /**
     * Apply a preset to current config.
     */
    @Transactional
    public ConfigDomain applyPreset(Preset preset) {
        ConfigDomain current = load();
        ConfigDomain.Builder b = ConfigDomain.builder().from(current);
        // Preset only affects fairness weights, not coverage or algorithm params
        // The rest stays the same
        return save(b.build());
    }

    // ─── Merge & Override helpers ─────────────────────────────────────────────

    private ConfigDomain merge(ConfigDomain base, ConfigDomain override) {
        ConfigDomain.Builder b = ConfigDomain.builder().from(base);

        if (override.enabled() != base.enabled()) b.enabled(override.enabled());
        if (!override.holidayMode().isBlank()) b.holidayMode(override.holidayMode());
        if (override.removedShiftTypes().length > 0) b.removedShiftTypes(override.removedShiftTypes());
        if (override.maxIterations() > 0) b.maxIterations(override.maxIterations());
        if (override.neighborhoodSize() > 0) b.neighborhoodSize(override.neighborhoodSize());
        if (override.tabuTenureMin() > 0) b.tabuTenureMin(override.tabuTenureMin());
        if (override.tabuTenureMax() > 0) b.tabuTenureMax(override.tabuTenureMax());
        if (override.maxNoImproveIterations() > 0) b.maxNoImproveIterations(override.maxNoImproveIterations());
        if (override.relativeImprovementThreshold() > 0) b.relativeImprovementThreshold(override.relativeImprovementThreshold());
        if (override.diversifyAfterIterations() > 0) b.diversifyAfterIterations(override.diversifyAfterIterations());
        if (!override.acceptanceStrategy().isBlank()) b.acceptanceStrategy(override.acceptanceStrategy());
        if (override.saInitialTemperature() > 0) b.saInitialTemperature(override.saInitialTemperature());
        if (override.saCoolingRate() > 0) b.saCoolingRate(override.saCoolingRate());
        if (override.saTemperatureMin() >= 0) b.saTemperatureMin(override.saTemperatureMin());
        if (override.laMemorySize() > 0) b.laMemorySize(override.laMemorySize());
        if (override.gdInitialLevel() > 0) b.gdInitialLevel(override.gdInitialLevel());
        if (override.gdDecayRate() > 0) b.gdDecayRate(override.gdDecayRate());
        if (override.gdMinLevel() >= 0) b.gdMinLevel(override.gdMinLevel());
        if (override.cvTarget() > 0) b.cvTarget(override.cvTarget());
        if (override.cvWorst() > 0) b.cvWorst(override.cvWorst());
        if (override.weekendWeight() > 0) b.weekendWeight(override.weekendWeight());
        if (override.l01MinPerDay() > 0) b.l01MinPerDay(override.l01MinPerDay());
        if (override.l01MaxPerDay() > 0) b.l01MaxPerDay(override.l01MaxPerDay());
        if (override.l02MinPerDay() > 0) b.l02MinPerDay(override.l02MinPerDay());
        if (override.l02MaxPerDay() > 0) b.l02MaxPerDay(override.l02MaxPerDay());
        if (override.l03MinPerDay() > 0) b.l03MinPerDay(override.l03MinPerDay());
        if (override.l03MaxPerDay() > 0) b.l03MaxPerDay(override.l03MaxPerDay());
        if (override.l04MinPerDay() > 0) b.l04MinPerDay(override.l04MinPerDay());
        if (override.l04MaxPerDay() > 0) b.l04MaxPerDay(override.l04MaxPerDay());
        if (override.overnightRecoveryHours() > 0) b.overnightRecoveryHours(override.overnightRecoveryHours());
        if (override.greedyCoverageThreshold() > 0) b.greedyCoverageThreshold(override.greedyCoverageThreshold());
        if (override.minStaffPerShift() >= 0) b.minStaffPerShift(override.minStaffPerShift());
        if (override.maxStaffPerShift() >= 0) b.maxStaffPerShift(override.maxStaffPerShift());
        if (override.minShiftsPerStaff() >= 0) b.minShiftsPerStaff(override.minShiftsPerStaff());
        if (override.maxShiftsPerStaff() >= 0) b.maxShiftsPerStaff(override.maxShiftsPerStaff());
        if (override.timeLimitSeconds() > 0) b.timeLimitSeconds(override.timeLimitSeconds());
        if (override.candidateListSize() > 0) b.candidateListSize(override.candidateListSize());

        return b.build();
    }

    private ConfigDomain applyOverrides(ConfigDomain base, Map<String, Object> overrides) {
        ConfigDomain.Builder b = ConfigDomain.builder().from(base);
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            b = setFieldValueOnBuilder(b, e.getKey(), e.getValue());
        }
        return b.build();
    }

    private Object getFieldValue(ConfigDomain config, String fieldPath) {
        return switch (fieldPath) {
            case "enabled" -> config.enabled();
            case "holidayMode" -> config.holidayMode();
            case "removedShiftTypes" -> config.removedShiftTypes();
            case "algorithm.maxIterations" -> config.maxIterations();
            case "algorithm.neighborhoodSize" -> config.neighborhoodSize();
            case "algorithm.tabuTenureMin" -> config.tabuTenureMin();
            case "algorithm.tabuTenureMax" -> config.tabuTenureMax();
            case "algorithm.maxNoImproveIterations" -> config.maxNoImproveIterations();
            case "algorithm.relativeImprovementThreshold" -> config.relativeImprovementThreshold();
            case "algorithm.diversifyAfterIterations" -> config.diversifyAfterIterations();
            case "acceptanceStrategy.kind" -> config.acceptanceStrategy();
            case "acceptanceStrategy.saInitialTemperature" -> config.saInitialTemperature();
            case "acceptanceStrategy.saCoolingRate" -> config.saCoolingRate();
            case "acceptanceStrategy.saTemperatureMin" -> config.saTemperatureMin();
            case "acceptanceStrategy.laMemorySize" -> config.laMemorySize();
            case "acceptanceStrategy.gdInitialLevel" -> config.gdInitialLevel();
            case "acceptanceStrategy.gdDecayRate" -> config.gdDecayRate();
            case "acceptanceStrategy.gdMinLevel" -> config.gdMinLevel();
            case "fairness.cvTarget" -> config.cvTarget();
            case "fairness.cvWorst" -> config.cvWorst();
            case "fairness.weekendWeight" -> config.weekendWeight();
            case "constraints.overnightRecoveryHours" -> config.overnightRecoveryHours();
            case "constraints.greedyCoverageThreshold" -> config.greedyCoverageThreshold();
            case "constraints.minStaffPerShift" -> config.minStaffPerShift();
            case "constraints.maxStaffPerShift" -> config.maxStaffPerShift();
            case "constraints.minShiftsPerStaff" -> config.minShiftsPerStaff();
            case "constraints.maxShiftsPerStaff" -> config.maxShiftsPerStaff();
            case "performance.timeLimitSeconds" -> config.timeLimitSeconds();
            case "performance.candidateListSize" -> config.candidateListSize();
            default -> {
                if (fieldPath.startsWith("coverage.l01.")) yield config.l01MinPerDay();
                if (fieldPath.startsWith("coverage.l02.")) yield config.l02MinPerDay();
                if (fieldPath.startsWith("coverage.l03.")) yield config.l03MinPerDay();
                if (fieldPath.startsWith("coverage.l04.")) yield config.l04MinPerDay();
                yield null;
            }
        };
    }

    private ConfigDomain setFieldValue(ConfigDomain config, String fieldPath, Object value) {
        ConfigDomain.Builder b = ConfigDomain.builder().from(config);
        return setFieldValueOnBuilder(b, fieldPath, value).build();
    }

    private ConfigDomain.Builder setFieldValueOnBuilder(ConfigDomain.Builder b, String fieldPath, Object value) {
        return switch (fieldPath) {
            case "enabled" -> b.enabled(toBoolean(value));
            case "holidayMode" -> b.holidayMode(toString(value));
            case "removedShiftTypes" -> b.removedShiftTypes(toStringArray(value));
            case "algorithm.maxIterations" -> b.maxIterations(toInt(value));
            case "algorithm.neighborhoodSize" -> b.neighborhoodSize(toInt(value));
            case "algorithm.tabuTenureMin" -> b.tabuTenureMin(toInt(value));
            case "algorithm.tabuTenureMax" -> b.tabuTenureMax(toInt(value));
            case "algorithm.maxNoImproveIterations" -> b.maxNoImproveIterations(toInt(value));
            case "algorithm.relativeImprovementThreshold" -> b.relativeImprovementThreshold(toDouble(value));
            case "algorithm.diversifyAfterIterations" -> b.diversifyAfterIterations(toInt(value));
            case "acceptanceStrategy.kind" -> b.acceptanceStrategy(toString(value));
            case "acceptanceStrategy.saInitialTemperature" -> b.saInitialTemperature(toDouble(value));
            case "acceptanceStrategy.saCoolingRate" -> b.saCoolingRate(toDouble(value));
            case "acceptanceStrategy.saTemperatureMin" -> b.saTemperatureMin(toDouble(value));
            case "acceptanceStrategy.laMemorySize" -> b.laMemorySize(toInt(value));
            case "acceptanceStrategy.gdInitialLevel" -> b.gdInitialLevel(toDouble(value));
            case "acceptanceStrategy.gdDecayRate" -> b.gdDecayRate(toDouble(value));
            case "acceptanceStrategy.gdMinLevel" -> b.gdMinLevel(toDouble(value));
            case "fairness.cvTarget" -> b.cvTarget(toDouble(value));
            case "fairness.cvWorst" -> b.cvWorst(toDouble(value));
            case "fairness.weekendWeight" -> b.weekendWeight(toDouble(value));
            case "constraints.overnightRecoveryHours" -> b.overnightRecoveryHours(toInt(value));
            case "constraints.greedyCoverageThreshold" -> b.greedyCoverageThreshold(toDouble(value));
            case "constraints.minStaffPerShift" -> b.minStaffPerShift(toInt(value));
            case "constraints.maxStaffPerShift" -> b.maxStaffPerShift(toInt(value));
            case "constraints.minShiftsPerStaff" -> b.minShiftsPerStaff(toInt(value));
            case "constraints.maxShiftsPerStaff" -> b.maxShiftsPerStaff(toInt(value));
            case "performance.timeLimitSeconds" -> b.timeLimitSeconds(toInt(value));
            case "performance.candidateListSize" -> b.candidateListSize(toInt(value));
            default -> {
                if (fieldPath.startsWith("coverage.l01.")) {
                    int v = toInt(value);
                    yield switch (fieldPath) {
                        case "coverage.l01.minPerDay" -> b.l01MinPerDay(v);
                        case "coverage.l01.maxPerDay" -> b.l01MaxPerDay(v);
                        default -> b;
                    };
                }
                if (fieldPath.startsWith("coverage.l02.")) {
                    int v = toInt(value);
                    yield switch (fieldPath) {
                        case "coverage.l02.minPerDay" -> b.l02MinPerDay(v);
                        case "coverage.l02.maxPerDay" -> b.l02MaxPerDay(v);
                        default -> b;
                    };
                }
                if (fieldPath.startsWith("coverage.l03.")) {
                    int v = toInt(value);
                    yield switch (fieldPath) {
                        case "coverage.l03.minPerDay" -> b.l03MinPerDay(v);
                        case "coverage.l03.maxPerDay" -> b.l03MaxPerDay(v);
                        default -> b;
                    };
                }
                if (fieldPath.startsWith("coverage.l04.")) {
                    int v = toInt(value);
                    yield switch (fieldPath) {
                        case "coverage.l04.minPerDay" -> b.l04MinPerDay(v);
                        case "coverage.l04.maxPerDay" -> b.l04MaxPerDay(v);
                        default -> b;
                    };
                }
                yield b;
            }
        };
    }

    // ─── Type converters ──────────────────────────────────────────────────────

    private boolean toBoolean(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(v.toString()) || "1".equals(v.toString());
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return (int) Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return 0; }
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private String toString(Object v) {
        return v != null ? v.toString() : "";
    }

    private String[] toStringArray(Object v) {
        if (v == null) return new String[0];
        if (v instanceof String[] arr) return arr;
        if (v instanceof List<?> list) return list.stream().map(Object::toString).toArray(String[]::new);
        return new String[]{v.toString()};
    }

    // ─── DTO helpers ──────────────────────────────────────────────────────────

    private OptionDto[] toOptionDtos(ConfigMetadata.Option[] options) {
        if (options == null) return new OptionDto[0];
        OptionDto[] dtos = new OptionDto[options.length];
        for (int i = 0; i < options.length; i++) {
            dtos[i] = new OptionDto(options[i].value(), options[i].labelVi());
        }
        return dtos;
    }

    // ─── DTO records ──────────────────────────────────────────────────────────

    public record CategoryMetadata(
            String categoryKey,
            String labelVi,
            String labelEn,
            int sortOrder,
            List<FieldMetadataDto> fields
    ) {}

    public record FieldMetadataDto(
            String fieldPath,
            String label,
            String description,
            String category,
            String renderType,
            double min,
            double max,
            double step,
            String defaultValue,
            boolean required,
            String visibleWhen,
            String editableWhen,
            String validationSeverity,
            OptionDto[] allowedValues
    ) {}

    public record OptionDto(String value, String label) {}
}
