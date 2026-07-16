package com.hospital.scheduler.scheduling.config;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 3-tier configuration validator.
 *
 * <h2>Tier 1: Field Validation</h2>
 * Per-field: type check, min/max bounds, required check.
 * These are enforced by reading metadata bounds.
 *
 * <h2>Tier 2: Cross-Field Validation</h2>
 * Relationships between fields: min ≤ max, tabuMin ≤ tabuMax, etc.
 * Hard errors that must be fixed before saving.
 *
 * <h2>Tier 3: Business Validation</h2>
 * Domain-aware rules: coverage vs staff availability, L04 cross vs ratio, etc.
 * Soft warnings that alert Admin but don't block saving.
 *
 * <p>Usage:
 * <pre>
 * ConfigDomain config = ...;
 * ValidationResult result = validator.validate(config);
 * if (result.hasErrors()) throw new ConfigValidationException(result.errors());
 * if (result.hasWarnings()) showWarnings(result.warnings());
 * </pre>
 */
@Component
public class ConfigValidator {

    /**
     * Result of validation — separate error and warning lists.
     * Errors block saving; warnings are advisory.
     */
    public record ValidationResult(
            List<Violation> errors,
            List<Violation> warnings,
            List<Violation> infos
    ) {
        public boolean hasErrors()  { return !errors.isEmpty(); }
        public boolean hasWarnings(){ return !warnings.isEmpty(); }
        public boolean hasInfos()   { return !infos.isEmpty(); }
        public boolean isValid()    { return !hasErrors(); }

        public static ValidationResult valid() {
            return new ValidationResult(Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());
        }

        public ValidationResult merge(ValidationResult other) {
            List<Violation> e = new ArrayList<>(this.errors);
            List<Violation> w = new ArrayList<>(this.warnings);
            List<Violation> i = new ArrayList<>(this.infos);
            e.addAll(other.errors);
            w.addAll(other.warnings);
            i.addAll(other.infos);
            return new ValidationResult(e, w, i);
        }
    }

    /**
     * A single validation violation.
     */
    public record Violation(
            String fieldPath,
            String messageVi,
            String messageEn,
            ConfigMetadata.ValidationSeverity severity
    ) {
        public Violation(String fieldPath, String messageVi,
                        ConfigMetadata.ValidationSeverity severity) {
            this(fieldPath, messageVi, messageVi, severity);
        }
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    /**
     * Full 3-tier validation of a ConfigDomain.
     */
    public ValidationResult validate(ConfigDomain config) {
        ValidationResult r1 = validateFields(config);
        ValidationResult r2 = validateCrossFields(config);
        ValidationResult r3 = validateBusiness(config);
        return r1.merge(r2).merge(r3);
    }

    /**
     * Quick field-only validation (Tier 1).
     */
    public ValidationResult validateFields(ConfigDomain config) {
        List<Violation> errors = new ArrayList<>();
        List<Violation> warnings = new ArrayList<>();

        for (String fieldPath : ConfigMetadataRegistry.fieldPaths()) {
            ConfigMetadata meta = ConfigMetadataRegistry.get(fieldPath);
            if (meta == null) continue;

            Object value = getFieldValue(config, fieldPath);
            Violation v = validateSingleField(meta, value);
            if (v != null) {
                if (v.severity() == ConfigMetadata.ValidationSeverity.ERROR) {
                    errors.add(v);
                } else if (v.severity() == ConfigMetadata.ValidationSeverity.WARNING) {
                    warnings.add(v);
                }
            }
        }

        return new ValidationResult(errors, warnings, Collections.emptyList());
    }

    /**
     * Cross-field validation (Tier 2).
     * These are always ERROR severity.
     */
    public ValidationResult validateCrossFields(ConfigDomain config) {
        List<Violation> errors = new ArrayList<>();
        List<Violation> warnings = new ArrayList<>();

        // Tabu tenure range
        if (config.tabuTenureMin() > config.tabuTenureMax()) {
            errors.add(new Violation(
                    "algorithm.tabuTenureMax",
                    "Tabu tối đa phải ≥ Tabu tối thiểu",
                    ConfigMetadata.ValidationSeverity.ERROR
            ));
        }

        // Neighborhood vs candidate list size
        if (config.candidateListSize() > 0 && config.neighborhoodSize() > config.candidateListSize()) {
            warnings.add(new Violation(
                    "algorithm.neighborhoodSize",
                    "Kích thước vùng lân cận lớn hơn danh sách ứng viên — có thể bỏ sót ứng viên tốt",
                    ConfigMetadata.ValidationSeverity.WARNING
            ));
        }

        // CV target vs worst
        if (config.cvTarget() > config.cvWorst()) {
            errors.add(new Violation(
                    "fairness.cvTarget",
                    "CV mục tiêu phải ≤ CV tồi tệ nhất",
                    ConfigMetadata.ValidationSeverity.ERROR
            ));
        }

        // Coverage: min ≤ max for each shift type
        checkCoverageBounds(errors, config);

        // SA cooling rate bounds
        if (config.saCoolingRate() < 0.9 || config.saCoolingRate() > 0.99999) {
            warnings.add(new Violation(
                    "acceptanceStrategy.saCoolingRate",
                    "Tốc độ làm nguội nên nằm trong khoảng 0.9–0.99999",
                    ConfigMetadata.ValidationSeverity.WARNING
            ));
        }

        // GD decay rate bounds
        if (config.gdDecayRate() < 0.9 || config.gdDecayRate() > 0.99999) {
            warnings.add(new Violation(
                    "acceptanceStrategy.gdDecayRate",
                    "Tốc độ giảm GD nên nằm trong khoảng 0.9–0.99999",
                    ConfigMetadata.ValidationSeverity.WARNING
            ));
        }

        // LA memory size
        if (config.laMemorySize() > config.maxIterations()) {
            warnings.add(new Violation(
                    "acceptanceStrategy.laMemorySize",
                    "LA memory size lớn hơn số lần lặp — không đủ lịch sử để so sánh",
                    ConfigMetadata.ValidationSeverity.WARNING
            ));
        }

        return new ValidationResult(errors, warnings, Collections.emptyList());
    }

    /**
     * Business-level validation (Tier 3).
     * These are typically WARNING severity — informing Admin of potential issues.
     */
    public ValidationResult validateBusiness(ConfigDomain config) {
        List<Violation> errors = new ArrayList<>();
        List<Violation> warnings = new ArrayList<>();
        List<Violation> infos = new ArrayList<>();

        // L04 cross-specialty enabled but ratio is 0
        if (config.l04CrossSpecialtyEnabled() && config.l04CrossSpecialtyRatio() <= 0) {
            warnings.add(new Violation(
                    "l04.crossSpecialtyRatio",
                    "Cross-specialty đã bật nhưng tỷ lệ = 0 — không có tác dụng",
                    ConfigMetadata.ValidationSeverity.WARNING
            ));
        }

        // L04 cross-specialty disabled but ratio > 0
        if (!config.l04CrossSpecialtyEnabled() && config.l04CrossSpecialtyRatio() > 0) {
            infos.add(new Violation(
                    "l04.crossSpecialtyRatio",
                    "Tỷ lệ cross-specialty đang được thiết lập nhưng tính năng chưa bật",
                    ConfigMetadata.ValidationSeverity.INFO
            ));
        }

        // L04 disabled in removed types but cross-specialty enabled
        if (config.isShiftTypeRemoved("L04") && config.l04CrossSpecialtyEnabled()) {
            warnings.add(new Violation(
                    "l04.crossSpecialtyEnabled",
                    "L04 đã bị loại trừ nhưng cross-specialty còn bật",
                    ConfigMetadata.ValidationSeverity.WARNING
            ));
        }

        // Iteration 0 means no iterations
        if (config.maxIterations() == 0) {
            errors.add(new Violation(
                    "algorithm.maxIterations",
                    "Số lần lặp phải ≥ 1",
                    ConfigMetadata.ValidationSeverity.ERROR
            ));
        }

        // Time limit 0 means no time limit (may be intentional)
        if (config.timeLimitSeconds() == 0) {
            infos.add(new Violation(
                    "performance.timeLimitSeconds",
                    "Không giới hạn thời gian — thuật toán sẽ chạy đến khi hội tụ",
                    ConfigMetadata.ValidationSeverity.INFO
            ));
        }

        // Very low CV target may be impossible
        if (config.cvTarget() < 0.05) {
            warnings.add(new Violation(
                    "fairness.cvTarget",
                    "CV mục tiêu rất thấp (< 5%) — có thể không đạt được với dữ liệu thực tế",
                    ConfigMetadata.ValidationSeverity.WARNING
            ));
        }

        // Very high weekend weight
        if (config.weekendWeight() > 5.0) {
            warnings.add(new Violation(
                    "fairness.weekendWeight",
                    "Trọng số cuối tuần rất cao (> 5×) — có thể ưu tiên quá mức và ảnh hưởng coverage",
                    ConfigMetadata.ValidationSeverity.WARNING
            ));
        }

        // Candidate list size too small for neighborhood
        if (config.candidateListSize() < config.neighborhoodSize() * 2) {
            warnings.add(new Violation(
                    "performance.candidateListSize",
                    "Danh sách ứng viên nhỏ hơn 2× kích thước vùng lân cận — có thể thiếu ứng viên chất lượng",
                    ConfigMetadata.ValidationSeverity.WARNING
            ));
        }

        // Overnight recovery hours too low
        if (config.overnightRecoveryHours() > 0 && config.overnightRecoveryHours() < 12) {
            errors.add(new Violation(
                    "constraints.overnightRecoveryHours",
                    "Giờ hồi phục < 12h vi phạm quy định an toàn lao động",
                    ConfigMetadata.ValidationSeverity.ERROR
            ));
        }

        return new ValidationResult(errors, warnings, infos);
    }

    // ─── Tier 1: Single field validation ─────────────────────────────────

    private Violation validateSingleField(ConfigMetadata meta, Object value) {
        // Required check
        if (meta.required() && isEmpty(value)) {
            return new Violation(meta.fieldPath(),
                    "Trường '" + meta.labelVi() + "' không được để trống",
                    ConfigMetadata.ValidationSeverity.ERROR);
        }

        // Null check for required
        if (value == null) return null;

        String strVal = value.toString();

        // Numeric bounds check
        if (meta.renderType().equals("number") || meta.renderType().equals("slider")) {
            if (!strVal.isBlank()) {
                try {
                    double numVal = Double.parseDouble(strVal);
                    if (numVal < meta.min()) {
                        return new Violation(meta.fieldPath(),
                                meta.labelVi() + " phải ≥ " + formatBound(meta.min()),
                                ConfigMetadata.ValidationSeverity.ERROR);
                    }
                    if (numVal > meta.max()) {
                        return new Violation(meta.fieldPath(),
                                meta.labelVi() + " phải ≤ " + formatBound(meta.max()),
                                ConfigMetadata.ValidationSeverity.ERROR);
                    }
                } catch (NumberFormatException e) {
                    return new Violation(meta.fieldPath(),
                            meta.labelVi() + " phải là số",
                            ConfigMetadata.ValidationSeverity.ERROR);
                }
            }
        }

        // Enum validation
        if (meta.hasAllowedValues() && !strVal.isBlank()) {
            boolean valid = false;
            for (ConfigMetadata.Option opt : meta.allowedValues()) {
                if (opt.value().equals(strVal)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                return new Violation(meta.fieldPath(),
                        meta.labelVi() + " có giá trị không hợp lệ",
                        ConfigMetadata.ValidationSeverity.ERROR);
            }
        }

        return null;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void checkCoverageBounds(List<Violation> errors, ConfigDomain config) {
        checkMinMax(errors, "coverage.l01.minPerDay",  "L01 min/ngày",  config.l01MinPerDay(),  config.l01MaxPerDay());
        checkMinMax(errors, "coverage.l01.minPerWeek", "L01 min/tuần",  config.l01MinPerWeek(), config.l01MaxPerWeek());
        checkMinMax(errors, "coverage.l02.minPerDay",  "L02 min/ngày",  config.l02MinPerDay(),  config.l02MaxPerDay());
        checkMinMax(errors, "coverage.l02.minPerWeek", "L02 min/tuần",  config.l02MinPerWeek(), config.l02MaxPerWeek());
        checkMinMax(errors, "coverage.l03.minPerDay",  "L03 min/ngày",  config.l03MinPerDay(),  config.l03MaxPerDay());
        checkMinMax(errors, "coverage.l03.minPerWeek", "L03 min/tuần",  config.l03MinPerWeek(), config.l03MaxPerWeek());
        checkMinMax(errors, "coverage.l04.minPerDay",  "L04 min/ngày",  config.l04MinPerDay(),  config.l04MaxPerDay());
        checkMinMax(errors, "coverage.l04.minPerWeek", "L04 min/tuần",  config.l04MinPerWeek(), config.l04MaxPerWeek());
    }

    private void checkMinMax(List<Violation> errors, String fieldPath, String label,
                             int min, int max) {
        if (min > 0 && max > 0 && min > max) {
            errors.add(new Violation(fieldPath,
                    label + " tối thiểu (" + min + ") phải ≤ tối đa (" + max + ")",
                    ConfigMetadata.ValidationSeverity.ERROR));
        }
    }

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        if (value instanceof String[] arr) return arr.length == 0;
        return false;
    }

    private String formatBound(double val) {
        if (Double.isInfinite(val)) return "∞";
        if (val == (int) val) return String.valueOf((int) val);
        return String.valueOf(val);
    }

    /**
     * Reflectively get field value from ConfigDomain by fieldPath.
     * Field paths use dot notation: "algorithm.maxIterations"
     */
    private Object getFieldValue(ConfigDomain config, String fieldPath) {
        try {
            String[] parts = fieldPath.split("\\.", 2);
            var field = ConfigDomain.class.getDeclaredField(parts[0]);
            field.setAccessible(true);
            Object value = field.get(config);
            if (parts.length == 2 && value != null) {
                // Handle nested records (e.g., acceptanceStrategy.kind)
                var nested = value.getClass();
                var nestedField = nested.getDeclaredField(parts[1]);
                nestedField.setAccessible(true);
                return nestedField.get(value);
            }
            return value;
        } catch (Exception e) {
            // Fallback: use ConfigMapper to convert
            Map<String, String> paramMap = ConfigMapper.toParamMap(config);
            String pk = ConfigMapper.toParamKey(fieldPath);
            return paramMap.get(pk);
        }
    }
}
