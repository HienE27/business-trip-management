package com.hospital.scheduler.scheduling.config;

import java.util.List;
import java.util.function.Function;

/**
 * Metadata descriptor for a single configuration field.
 * Drives both backend validation AND frontend form rendering.
 *
 * <p>This is the single source of truth for:
 * <ul>
 *   <li>Human-readable label and description</li>
 *   <li>Min/max/step bounds for numeric fields</li>
 *   <li>Allowed values for enum fields</li>
 *   <li>Field-level validation rules</li>
 *   <li>Category grouping for UI organization</li>
 *   <li>Conditional visibility/editing</li>
 * </ul>
 *
 * <p>Example usage in frontend:
 * <pre>
 * field.renderType === 'slider' → &lt;Slider min={meta.min} max={meta.max} step={meta.step} /&gt;
 * field.renderType === 'select' → &lt;Select options={meta.allowedValues} /&gt;
 * field.validation === 'cv'    → backend validates 0 ≤ value ≤ 1
 * </pre>
 *
 * <p>Example usage in backend:
 * <pre>
 * ConfigValidator.validateField(meta, value) → throws ConfigValidationException
 * </pre>
 */
public record ConfigMetadata(

        /** Dot-notation path within ConfigDomain, e.g. "algorithm.maxIterations". */
        String fieldPath,

        /** Human-readable label for UI display. */
        String labelVi,

        /** Human-readable label in English. */
        String labelEn,

        /** Full description explaining what this field does. */
        String descriptionVi,

        /** Full description in English. */
        String descriptionEn,

        /** UI category for grouping. */
        ConfigCategory category,

        /**
         * How to render this field in UI.
         * Supported types: slider, number, toggle, select, multiselect, chip_group
         */
        String renderType,

        /**
         * Minimum value (inclusive) for numeric fields.
         * -Infinity if not applicable.
         */
        double min,

        /**
         * Maximum value (inclusive) for numeric fields.
         * +Infinity if not applicable.
         */
        double max,

        /**
         * Step for slider inputs. 1.0 for integer fields.
         * 0.01 for percentage fields.
         */
        double step,

        /**
         * Allowed values for enum/set fields (select, multiselect, chip_group).
         * Each entry is a display label in Vietnamese.
         */
        Option[] allowedValues,

        /**
         * Default value when creating new config.
         */
        String defaultValue,

        /**
         * Whether this field is required (cannot be null/empty).
         */
        boolean required,

        /**
         * Condition for field visibility.
         * Format: "fieldPath:operator:value" e.g. "l04MaxPerDay:eq:3"
         * Supported operators: eq, ne, gt, lt, gte, lte, in, notIn
         * null = always visible.
         */
        String visibleWhen,

        /**
         * Condition for field editability.
         * Same format as visibleWhen.
         * null = always editable.
         */
        String editableWhen,

        /**
         * Severity when validation fails.
         */
        ValidationSeverity validationSeverity
) {

    /**
     * Configuration category for grouping fields in UI.
     */
    public enum ConfigCategory {
        GENERAL("Tổng quát", "General", 1),
        ALGORITHM("Thuật toán", "Algorithm", 2),
        ACCEPTANCE("Chiến lược chấp nhận", "Acceptance Strategy", 3),
        FAIRNESS("Công bằng", "Fairness", 4),
        COVERAGE("Phủ bì", "Coverage", 5),
        L04("Phòng khám chuyên gia", "Expert Clinic (L04)", 6),
        CONSTRAINTS("Ràng buộc", "Constraints", 7),
        PERFORMANCE("Hiệu suất", "Performance", 8);

        public final String labelVi;
        public final String labelEn;
        public final int sortOrder;

        ConfigCategory(String labelVi, String labelEn, int sortOrder) {
            this.labelVi = labelVi;
            this.labelEn = labelEn;
            this.sortOrder = sortOrder;
        }
    }

    /**
     * Validation failure severity.
     */
    public enum ValidationSeverity {
        /** Hard error — must fix before saving. */
        ERROR,
        /** Soft warning — can save but Admin should be aware. */
        WARNING,
        /** Info — no action needed, just informational. */
        INFO
    }

    /**
     * An allowed value option for select/multiselect fields.
     */
    public record Option(String value, String labelVi, String labelEn) {
        public Option(String value, String labelVi) {
            this(value, labelVi, labelVi);
        }
    }

    // ─── Convenience factories ─────────────────────────────────────────────

    /** Creates metadata for a boolean toggle field. */
    public static ConfigMetadata toggle(String fieldPath, String labelVi, String descriptionVi,
                                        ConfigCategory category, boolean defaultValue) {
        return new ConfigMetadata(
                fieldPath, labelVi, labelVi, descriptionVi, descriptionVi,
                category, "toggle",
                0, 1, 1,
                null, String.valueOf(defaultValue),
                false, null, null, ValidationSeverity.ERROR
        );
    }

    /** Creates metadata for an integer field with min/max bounds. */
    public static ConfigMetadata integer(String fieldPath, String labelVi, String descriptionVi,
                                         ConfigCategory category, int defaultValue,
                                         int min, int max) {
        return new ConfigMetadata(
                fieldPath, labelVi, labelVi, descriptionVi, descriptionVi,
                category, "number",
                min, max, 1,
                null, String.valueOf(defaultValue),
                true, null, null, ValidationSeverity.ERROR
        );
    }

    /** Creates metadata for a double field with min/max/step. */
    public static ConfigMetadata decimal(String fieldPath, String labelVi, String descriptionVi,
                                         ConfigCategory category, double defaultValue,
                                         double min, double max, double step) {
        return new ConfigMetadata(
                fieldPath, labelVi, labelVi, descriptionVi, descriptionVi,
                category, "number",
                min, max, step,
                null, String.valueOf(defaultValue),
                true, null, null, ValidationSeverity.ERROR
        );
    }

    /** Creates metadata for a percentage field (0.0–1.0 or 0–100). */
    public static ConfigMetadata percentage(String fieldPath, String labelVi, String descriptionVi,
                                            ConfigCategory category, double defaultValue,
                                            double min, double max) {
        return new ConfigMetadata(
                fieldPath, labelVi, labelVi, descriptionVi, descriptionVi,
                category, "slider",
                min, max, 0.01,
                null, String.valueOf(defaultValue),
                true, null, null, ValidationSeverity.ERROR
        );
    }

    /** Creates metadata for a single-select enum field. */
    public static ConfigMetadata select(String fieldPath, String labelVi, String descriptionVi,
                                        ConfigCategory category, String defaultValue,
                                        Option[] allowedValues) {
        return new ConfigMetadata(
                fieldPath, labelVi, labelVi, descriptionVi, descriptionVi,
                category, "select",
                0, 0, 0,
                allowedValues, defaultValue,
                true, null, null, ValidationSeverity.ERROR
        );
    }

    /** Creates metadata for a multi-select chip group field. */
    public static ConfigMetadata chipGroup(String fieldPath, String labelVi, String descriptionVi,
                                           ConfigCategory category, String[] defaultValues,
                                           Option[] allowedValues) {
        return new ConfigMetadata(
                fieldPath, labelVi, labelVi, descriptionVi, descriptionVi,
                category, "chip_group",
                0, 0, 0,
                allowedValues, defaultValues != null ? String.join(",", defaultValues) : "",
                false, null, null, ValidationSeverity.ERROR
        );
    }

    // ─── Utility methods ───────────────────────────────────────────────────

    /** Returns the Option matching the given value, or null if not found. */
    public Option findOption(String value) {
        if (allowedValues == null) return null;
        for (Option opt : allowedValues) {
            if (opt.value().equals(value)) return opt;
        }
        return null;
    }

    /** Returns the Option's Vietnamese label for a value. */
    public String getOptionLabel(String value) {
        Option opt = findOption(value);
        return opt != null ? opt.labelVi() : value;
    }

    /** Check if this field has allowed values (is an enum/select type). */
    public boolean hasAllowedValues() {
        return allowedValues != null && allowedValues.length > 0;
    }

    /** Check if this field is visible given current config values. */
    public boolean isVisible(Function<String, Object> valueProvider) {
        if (visibleWhen == null || visibleWhen.isBlank()) return true;
        return ConditionParser.evaluate(visibleWhen, valueProvider);
    }

    /** Check if this field is editable given current config values. */
    public boolean isEditable(Function<String, Object> valueProvider) {
        if (editableWhen == null || editableWhen.isBlank()) return true;
        return ConditionParser.evaluate(editableWhen, valueProvider);
    }

    /**
     * Simple condition parser for visibleWhen/editableWhen.
     * Format: "fieldPath:operator:value"
     * Operators: eq, ne, gt, lt, gte, lte
     */
    public static class ConditionParser {
        public static boolean evaluate(String condition, Function<String, Object> valueProvider) {
            if (condition == null || condition.isBlank()) return true;

            String[] parts = condition.split(":", 3);
            if (parts.length != 3) return true;

            String field = parts[0];
            String op = parts[1];
            String expected = parts[2];

            Object actual = valueProvider.apply(field);
            if (actual == null) return false;

            String actualStr = actual.toString();
            return switch (op) {
                case "eq" -> actualStr.equalsIgnoreCase(expected);
                case "ne" -> !actualStr.equalsIgnoreCase(expected);
                case "gt" -> compareNumeric(actualStr, expected) > 0;
                case "lt" -> compareNumeric(actualStr, expected) < 0;
                case "gte" -> compareNumeric(actualStr, expected) >= 0;
                case "lte" -> compareNumeric(actualStr, expected) <= 0;
                default -> true;
            };
        }

        private static int compareNumeric(String a, String b) {
            try {
                double da = Double.parseDouble(a);
                double db = Double.parseDouble(b);
                return Double.compare(da, db);
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        }
    }
}
