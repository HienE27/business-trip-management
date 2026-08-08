package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.scheduling.config.*;
import com.hospital.scheduler.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Unified Configuration REST API.
 *
 * <p>Replaces fragmented endpoints:
 * <ul>
 *   <li>GET /auto-schedule/config → GET /config</li>
 *   <li>PUT /auto-schedule/runtime-config → PUT /config</li>
 *   <li>PUT /auto-schedule/auto-gen-config → PUT /config</li>
 *   <li>GET /auto-schedule/config/{key} → GET /config/{fieldPath}</li>
 * </ul>
 *
 * <p>New endpoints:
 * <ul>
 *   <li>POST /config/validate — validate without saving</li>
 *   <li>GET /config/metadata — field metadata for dynamic UI</li>
 *   <li>POST /config/reset — reset to defaults</li>
 *   <li>GET /config/diff — diff proposed vs current</li>
 *   <li>GET /config/presets — list available presets</li>
 *   <li>POST /config/presets/{key}/apply — apply preset</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/config
     * Get full configuration as a flat map.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_VIEW + "')")
    public ResponseEntity<ApiResponse<ConfigDto>> getConfig() {
        ConfigDomain config = configService.load();
        return ResponseEntity.ok(ApiResponse.success(toDto(config)));
    }

    /**
     * GET /api/v1/config/{fieldPath}
     * Get a single field value.
     */
    @GetMapping("/{fieldPath:.+}")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_VIEW + "')")
    public ResponseEntity<ApiResponse<FieldValueDto>> getField(@PathVariable String fieldPath) {
        ConfigMetadata meta = ConfigMetadataRegistry.get(fieldPath);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        ConfigDomain config = configService.load();
        Object value = getFieldValue(config, fieldPath);
        return ResponseEntity.ok(ApiResponse.success(new FieldValueDto(fieldPath, value, meta.labelVi())));
    }

    /**
     * GET /api/v1/config/metadata
     * Get all field metadata grouped by category.
     * Used by frontend to render dynamic forms.
     */
    @GetMapping("/metadata")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ConfigService.CategoryMetadata>>> getMetadata() {
        List<ConfigService.CategoryMetadata> metadata = configService.getMetadata();
        return ResponseEntity.ok(ApiResponse.success(metadata));
    }

    /**
     * GET /api/v1/config/presets
     * List all available presets.
     */
    @GetMapping("/presets")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_VIEW + "')")
    public ResponseEntity<ApiResponse<List<PresetDto>>> getPresets() {
        ConfigService.Preset[] presets = ConfigService.Preset.values();
        List<PresetDto> dtos = java.util.Arrays.stream(presets)
                .map(p -> new PresetDto(p.key, p.labelVi, p.labelEn, p.coverageTarget,
                        p.balanceScoreMin, p.weekendWeight))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    // ─── Write ─────────────────────────────────────────────────────────────────

    /**
     * PUT /api/v1/config
     * Save full configuration. Validates first; rejects if errors exist.
     */
    @PutMapping
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_EDIT + "')")
    public ResponseEntity<ApiResponse<ConfigDto>> saveConfig(@RequestBody ConfigDto dto) {
        ConfigDomain config = fromDto(dto);
        ConfigValidator.ValidationResult result = configService.validate(config);
        if (result.hasErrors()) {
            throw new ConfigValidationException(result);
        }
        ConfigDomain saved = configService.save(config);
        return ResponseEntity.ok(ApiResponse.success(toDto(saved)));
    }

    /**
     * PUT /api/v1/config/{fieldPath}
     * Save a single field value.
     */
    @PutMapping("/{fieldPath:.+}")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_EDIT + "')")
    public ResponseEntity<ApiResponse<FieldValueDto>> saveField(
            @PathVariable String fieldPath,
            @RequestBody Map<String, Object> payload) {
        ConfigMetadata meta = ConfigMetadataRegistry.get(fieldPath);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        ConfigDomain current = configService.load();
        ConfigDomain updated = configService.savePartial(
                ConfigDomain.builder().from(current).build());
        // Apply partial update via service
        ConfigDomain result = configService.savePartial(applyFieldValue(current, fieldPath, payload.get("value")));
        return ResponseEntity.ok(ApiResponse.success(new FieldValueDto(fieldPath, getFieldValue(result, fieldPath), meta.labelVi())));
    }

    // ─── Validation ───────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/config/validate
     * Validate configuration without saving.
     * Returns errors, warnings, and infos.
     */
    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_VIEW + "')")
    public ResponseEntity<ApiResponse<ConfigValidationException.ValidationResponse>> validate(
            @RequestBody ConfigDto dto) {
        ConfigDomain config = fromDto(dto);
        ConfigValidator.ValidationResult result = configService.validate(config);
        ConfigValidationException ex = new ConfigValidationException(result);
        return ResponseEntity.ok(ApiResponse.success(ex.toResponse()));
    }

    // ─── Reset ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/config/reset
     * Reset all configuration to defaults.
     */
    @PostMapping("/reset")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_EDIT + "')")
    public ResponseEntity<ApiResponse<ConfigDto>> reset() {
        ConfigDomain defaults = configService.reset();
        return ResponseEntity.ok(ApiResponse.success(toDto(defaults)));
    }

    /**
     * POST /api/v1/config/reset/{fieldPath}
     * Reset a single field to its default value.
     */
    @PostMapping("/reset/{fieldPath:.+}")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_EDIT + "')")
    public ResponseEntity<ApiResponse<FieldValueDto>> resetField(@PathVariable String fieldPath) {
        ConfigMetadata meta = ConfigMetadataRegistry.get(fieldPath);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        ConfigDomain result = configService.resetField(fieldPath);
        return ResponseEntity.ok(ApiResponse.success(new FieldValueDto(fieldPath, getFieldValue(result, fieldPath), meta.labelVi())));
    }

    // ─── Presets ────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/config/presets/{presetKey}/apply
     * Apply a preset configuration.
     */
    @PostMapping("/presets/{presetKey}/apply")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_EDIT + "')")
    public ResponseEntity<ApiResponse<ConfigDto>> applyPreset(@PathVariable String presetKey) {
        ConfigService.Preset preset = java.util.Arrays.stream(ConfigService.Preset.values())
                .filter(p -> p.key.equals(presetKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown preset: " + presetKey));
        ConfigDomain current = configService.load();
        ConfigDomain.Builder b = ConfigDomain.builder().from(current);
        b.cvTarget(preset.coverageTarget);
        b.cvWorst(preset.coverageTarget * 2);
        b.weekendWeight(preset.weekendWeight);
        ConfigDomain result = configService.save(b.build());
        return ResponseEntity.ok(ApiResponse.success(toDto(result)));
    }

    // ─── Diff ───────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/config/diff
     * Get diff between current saved config and proposed config.
     */
    @PostMapping("/diff")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_CONFIG_VIEW + "')")
    public ResponseEntity<ApiResponse<List<DiffDto>>> diff(@RequestBody ConfigDto dto) {
        ConfigDomain proposed = fromDto(dto);
        Map<String, ConfigMapper.DiffEntry> changes = configService.diffCurrent(proposed);
        List<DiffDto> diffs = changes.entrySet().stream()
                .map(e -> new DiffDto(e.getKey(), e.getValue().oldValue(), e.getValue().newValue()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(diffs));
    }

    // ─── DTO conversion ─────────────────────────────────────────────────────────

    private ConfigDto toDto(ConfigDomain config) {
        return new ConfigDto(
                config.enabled(),
                config.holidayMode(),
                config.removedShiftTypes(),
                config.maxIterations(),
                config.neighborhoodSize(),
                config.tabuTenureMin(),
                config.tabuTenureMax(),
                config.maxNoImproveIterations(),
                config.relativeImprovementThreshold(),
                config.diversifyAfterIterations(),
                config.acceptanceStrategy(),
                config.saInitialTemperature(),
                config.saCoolingRate(),
                config.saTemperatureMin(),
                config.laMemorySize(),
                config.gdInitialLevel(),
                config.gdDecayRate(),
                config.gdMinLevel(),
                config.cvTarget(),
                config.cvWorst(),
                config.weekendWeight(),
                config.l01MinPerDay(), config.l01MaxPerDay(), config.l01MaxPerWeek(),
                config.l02MinPerDay(), config.l02MaxPerDay(), config.l02MaxPerWeek(),
                config.l03MinPerDay(), config.l03MaxPerDay(), config.l03MaxPerWeek(),
                config.l04MinPerDay(), config.l04MaxPerDay(), config.l04MaxPerWeek(),
                config.overnightRecoveryHours(),
                config.greedyCoverageThreshold(),
                config.minStaffPerShift(),
                config.maxStaffPerShift(),
                config.minShiftsPerStaff(),
                config.maxShiftsPerStaff(),
                config.timeLimitSeconds(),
                config.candidateListSize()
        );
    }

    private ConfigDomain fromDto(ConfigDto dto) {
        return new ConfigDomain(
                dto.enabled,
                dto.holidayMode,
                dto.removedShiftTypes,
                dto.maxIterations,
                dto.neighborhoodSize,
                dto.tabuTenureMin,
                dto.tabuTenureMax,
                dto.maxNoImproveIterations,
                dto.relativeImprovementThreshold,
                dto.diversifyAfterIterations,
                dto.acceptanceStrategy,
                dto.saInitialTemperature,
                dto.saCoolingRate,
                dto.saTemperatureMin,
                dto.laMemorySize,
                dto.gdInitialLevel,
                dto.gdDecayRate,
                dto.gdMinLevel,
                dto.cvTarget,
                dto.cvWorst,
                dto.weekendWeight,
                dto.l01MinPerDay, dto.l01MaxPerDay, dto.l01MaxPerWeek,
                dto.l02MinPerDay, dto.l02MaxPerDay, dto.l02MaxPerWeek,
                dto.l03MinPerDay, dto.l03MaxPerDay, dto.l03MaxPerWeek,
                dto.l04MinPerDay, dto.l04MaxPerDay, dto.l04MaxPerWeek,
                dto.overnightRecoveryHours,
                dto.greedyCoverageThreshold,
                dto.minStaffPerShift,
                dto.maxStaffPerShift,
                dto.minShiftsPerStaff,
                dto.maxShiftsPerStaff,
                dto.timeLimitSeconds,
                dto.candidateListSize
        );
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
                if (fieldPath.startsWith("coverage.l01.")) {
                    yield switch (fieldPath) {
                        case "coverage.l01.minPerDay" -> config.l01MinPerDay();
                        case "coverage.l01.maxPerDay" -> config.l01MaxPerDay();
                        case "coverage.l01.maxPerWeek" -> config.l01MaxPerWeek();
                        default -> null;
                    };
                }
                if (fieldPath.startsWith("coverage.l02.")) {
                    yield switch (fieldPath) {
                        case "coverage.l02.minPerDay" -> config.l02MinPerDay();
                        case "coverage.l02.maxPerDay" -> config.l02MaxPerDay();
                        case "coverage.l02.maxPerWeek" -> config.l02MaxPerWeek();
                        default -> null;
                    };
                }
                if (fieldPath.startsWith("coverage.l03.")) {
                    yield switch (fieldPath) {
                        case "coverage.l03.minPerDay" -> config.l03MinPerDay();
                        case "coverage.l03.maxPerDay" -> config.l03MaxPerDay();
                        case "coverage.l03.maxPerWeek" -> config.l03MaxPerWeek();
                        default -> null;
                    };
                }
                if (fieldPath.startsWith("coverage.l04.")) {
                    yield switch (fieldPath) {
                        case "coverage.l04.minPerDay" -> config.l04MinPerDay();
                        case "coverage.l04.maxPerDay" -> config.l04MaxPerDay();
                        case "coverage.l04.maxPerWeek" -> config.l04MaxPerWeek();
                        default -> null;
                    };
                }
                yield null;
            }
        };
    }

    private ConfigDomain applyFieldValue(ConfigDomain base, String fieldPath, Object value) {
        ConfigDomain.Builder b = ConfigDomain.builder().from(base);
        if (value == null) return base;
        return switch (fieldPath) {
            case "enabled" -> { b.enabled(toBoolean(value)); yield b.build(); }
            case "holidayMode" -> { b.holidayMode(value.toString()); yield b.build(); }
            case "removedShiftTypes" -> { b.removedShiftTypes(toStringArray(value)); yield b.build(); }
            case "algorithm.maxIterations" -> { b.maxIterations(toInt(value)); yield b.build(); }
            case "algorithm.neighborhoodSize" -> { b.neighborhoodSize(toInt(value)); yield b.build(); }
            case "algorithm.tabuTenureMin" -> { b.tabuTenureMin(toInt(value)); yield b.build(); }
            case "algorithm.tabuTenureMax" -> { b.tabuTenureMax(toInt(value)); yield b.build(); }
            case "algorithm.maxNoImproveIterations" -> { b.maxNoImproveIterations(toInt(value)); yield b.build(); }
            case "algorithm.relativeImprovementThreshold" -> { b.relativeImprovementThreshold(toDouble(value)); yield b.build(); }
            case "algorithm.diversifyAfterIterations" -> { b.diversifyAfterIterations(toInt(value)); yield b.build(); }
            case "acceptanceStrategy.kind" -> { b.acceptanceStrategy(value.toString()); yield b.build(); }
            case "acceptanceStrategy.saInitialTemperature" -> { b.saInitialTemperature(toDouble(value)); yield b.build(); }
            case "acceptanceStrategy.saCoolingRate" -> { b.saCoolingRate(toDouble(value)); yield b.build(); }
            case "acceptanceStrategy.saTemperatureMin" -> { b.saTemperatureMin(toDouble(value)); yield b.build(); }
            case "acceptanceStrategy.laMemorySize" -> { b.laMemorySize(toInt(value)); yield b.build(); }
            case "acceptanceStrategy.gdInitialLevel" -> { b.gdInitialLevel(toDouble(value)); yield b.build(); }
            case "acceptanceStrategy.gdDecayRate" -> { b.gdDecayRate(toDouble(value)); yield b.build(); }
            case "acceptanceStrategy.gdMinLevel" -> { b.gdMinLevel(toDouble(value)); yield b.build(); }
            case "fairness.cvTarget" -> { b.cvTarget(toDouble(value)); yield b.build(); }
            case "fairness.cvWorst" -> { b.cvWorst(toDouble(value)); yield b.build(); }
            case "fairness.weekendWeight" -> { b.weekendWeight(toDouble(value)); yield b.build(); }
            case "constraints.overnightRecoveryHours" -> { b.overnightRecoveryHours(toInt(value)); yield b.build(); }
            case "constraints.greedyCoverageThreshold" -> { b.greedyCoverageThreshold(toDouble(value)); yield b.build(); }
            case "constraints.minStaffPerShift" -> { b.minStaffPerShift(toInt(value)); yield b.build(); }
            case "constraints.maxStaffPerShift" -> { b.maxStaffPerShift(toInt(value)); yield b.build(); }
            case "constraints.minShiftsPerStaff" -> { b.minShiftsPerStaff(toInt(value)); yield b.build(); }
            case "constraints.maxShiftsPerStaff" -> { b.maxShiftsPerStaff(toInt(value)); yield b.build(); }
            case "performance.timeLimitSeconds" -> { b.timeLimitSeconds(toInt(value)); yield b.build(); }
            case "performance.candidateListSize" -> { b.candidateListSize(toInt(value)); yield b.build(); }
            default -> {
                if (fieldPath.startsWith("coverage.l01.")) {
                    int v = toInt(value);
                    ConfigDomain.Builder bb = b;
                    switch (fieldPath) {
                        case "coverage.l01.minPerDay" -> bb.l01MinPerDay(v);
                        case "coverage.l01.maxPerDay" -> bb.l01MaxPerDay(v);
                        case "coverage.l01.maxPerWeek" -> bb.l01MaxPerWeek(v);
                    }
                    yield bb.build();
                }
                if (fieldPath.startsWith("coverage.l02.")) {
                    int v = toInt(value);
                    ConfigDomain.Builder bb = b;
                    switch (fieldPath) {
                        case "coverage.l02.minPerDay" -> bb.l02MinPerDay(v);
                        case "coverage.l02.maxPerDay" -> bb.l02MaxPerDay(v);
                        case "coverage.l02.maxPerWeek" -> bb.l02MaxPerWeek(v);
                    }
                    yield bb.build();
                }
                if (fieldPath.startsWith("coverage.l03.")) {
                    int v = toInt(value);
                    ConfigDomain.Builder bb = b;
                    switch (fieldPath) {
                        case "coverage.l03.minPerDay" -> bb.l03MinPerDay(v);
                        case "coverage.l03.maxPerDay" -> bb.l03MaxPerDay(v);
                        case "coverage.l03.maxPerWeek" -> bb.l03MaxPerWeek(v);
                    }
                    yield bb.build();
                }
                if (fieldPath.startsWith("coverage.l04.")) {
                    int v = toInt(value);
                    ConfigDomain.Builder bb = b;
                    switch (fieldPath) {
                        case "coverage.l04.minPerDay" -> bb.l04MinPerDay(v);
                        case "coverage.l04.maxPerDay" -> bb.l04MaxPerDay(v);
                        case "coverage.l04.maxPerWeek" -> bb.l04MaxPerWeek(v);
                    }
                    yield bb.build();
                }
                yield base;
            }
        };
    }

    private boolean toBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(v != null ? v.toString() : "");
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return (int) Double.parseDouble(v.toString()); }
        catch (Exception e) { return 0; }
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); }
        catch (Exception e) { return 0.0; }
    }

    private String[] toStringArray(Object v) {
        if (v instanceof String[] arr) return arr;
        if (v instanceof java.util.List<?> list) return list.stream().map(Object::toString).toArray(String[]::new);
        if (v != null) return new String[]{v.toString()};
        return new String[0];
    }

    // ─── DTOs ──────────────────────────────────────────────────────────────────

    public record ConfigDto(
            boolean enabled,
            String holidayMode,
            String[] removedShiftTypes,
            int maxIterations,
            int neighborhoodSize,
            int tabuTenureMin,
            int tabuTenureMax,
            int maxNoImproveIterations,
            double relativeImprovementThreshold,
            int diversifyAfterIterations,
            String acceptanceStrategy,
            double saInitialTemperature,
            double saCoolingRate,
            double saTemperatureMin,
            int laMemorySize,
            double gdInitialLevel,
            double gdDecayRate,
            double gdMinLevel,
            double cvTarget,
            double cvWorst,
            double weekendWeight,
            int l01MinPerDay, int l01MaxPerDay, int l01MaxPerWeek,
            int l02MinPerDay, int l02MaxPerDay, int l02MaxPerWeek,
            int l03MinPerDay, int l03MaxPerDay, int l03MaxPerWeek,
            int l04MinPerDay, int l04MaxPerDay, int l04MaxPerWeek,
            int overnightRecoveryHours,
            double greedyCoverageThreshold,
            int minStaffPerShift,
            int maxStaffPerShift,
            int minShiftsPerStaff,
            int maxShiftsPerStaff,
            int timeLimitSeconds,
            int candidateListSize
    ) {}

    public record FieldValueDto(String fieldPath, Object value, String label) {}
    public record DiffDto(String fieldPath, String oldValue, String newValue) {}
    public record PresetDto(String key, String labelVi, String labelEn,
                           double coverageTarget, double balanceScoreMin, double weekendWeight) {}
}
