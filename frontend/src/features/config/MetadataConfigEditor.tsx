"use client";

import { useCallback, useMemo, useState } from "react";
import { useConfig } from "@/features/config/context/ConfigContext";
import { FieldRenderer } from "@/features/config/renderer/FieldRenderer";
import type { CategoryMetadata, FieldMetadata, ValidationViolation } from "@/features/config/types/ConfigMetadata";
import { Button, IconButton } from "@/components/ui";
import { useToast } from "@/hooks/useToast";

interface Props {
  onSaved?: () => void;
}

const CATEGORY_ICONS: Record<string, string> = {
  GENERAL: "settings",
  ALGORITHM: "auto_mode",
  ACCEPTANCE: "route",
  FAIRNESS: "balance",
  COVERAGE: "grid_view",
  L04: "stethoscope",
  CONSTRAINTS: "rule",
  PERFORMANCE: "speed",
};

const CATEGORY_COLORS: Record<string, string> = {
  GENERAL: "text-primary",
  ALGORITHM: "text-blue-600",
  ACCEPTANCE: "text-purple-600",
  FAIRNESS: "text-green-600",
  COVERAGE: "text-orange-600",
  L04: "text-purple-600",
  CONSTRAINTS: "text-red-600",
  PERFORMANCE: "text-teal-600",
};

/**
 * Metadata-driven config editor.
 * Renders the full config form using backend-provided metadata.
 * Single source of truth: no hardcoded labels, min/max, or options.
 *
 * Migration phases:
 * [A] Read metadata + config from backend ✅
 * [B] Render fields dynamically using FieldRenderer ✅
 * [C] Validate via /config/validate ✅
 * [D] Save via /config ✅
 * [E] Remove hardcoded params from RuntimeConfigEditor (separate task)
 */
export function MetadataConfigEditor({ onSaved }: Props) {
  const { success, error: showError } = useToast();
  const {
    metadata,
    config,
    validation,
    localDraft,
    isLoading,
    isSaving,
    hasUnsavedChanges,
    error,
    updateField,
    validate,
    save,
    reset,
    getFieldValue,
    getEffectiveConfig,
  } = useConfig();

  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [validatingDraft, setValidatingDraft] = useState(false);

  // Show categories from metadata
  const categories = useMemo(() => {
    return metadata ?? [];
  }, [metadata]);

  // Active category defaults to first if none selected
  const currentCategory = useMemo(() => {
    if (activeCategory) {
      return categories.find((c) => c.categoryKey === activeCategory) ?? categories[0];
    }
    return categories[0] ?? null;
  }, [categories, activeCategory]);

  // Effective config = saved config merged with local draft changes
  const effectiveConfig = useMemo(() => {
    return getEffectiveConfig();
  }, [getEffectiveConfig]);

  // Get validation message for a specific field
  const getFieldError = useCallback(
    (fieldPath: string): string | undefined => {
      if (!validation) return undefined;
      const violation = validation.errors.find((v: ValidationViolation) => v.fieldPath === fieldPath);
      return violation?.message;
    },
    [validation]
  );

  // Handle field change — update local draft
  const handleFieldChange = useCallback(
    (fieldPath: string, value: unknown) => {
      updateField(fieldPath, value);
    },
    [updateField]
  );

  // Validate current draft
  const handleValidate = useCallback(async () => {
    setValidatingDraft(true);
    await validate();
    setValidatingDraft(false);
  }, [validate]);

  // Save current draft
  const handleSave = useCallback(async () => {
    if (!effectiveConfig || !config) return;
    setValidatingDraft(true);
    const result = await validate();
    setValidatingDraft(false);

    if (result && !result.valid) {
      showError("Cấu hình có lỗi. Vui lòng sửa trước khi lưu.");
      return;
    }

    try {
      await save(effectiveConfig as Parameters<typeof save>[0]);
      success("Lưu cấu hình thành công!");
      onSaved?.();
    } catch (err) {
      showError("Lưu thất bại: " + String(err));
    }
  }, [effectiveConfig, config, validate, save, success, showError, onSaved]);

  // Reset local draft to saved values
  const handleReset = useCallback(() => {
    reset();
    success("Đã hủy thay đổi");
  }, [reset, success]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-label-md text-on-surface-variant">Đang tải cấu hình...</p>
        </div>
      </div>
    );
  }

  if (error || !metadata || !config) {
    return (
      <div className="p-6 bg-error-container rounded-xl">
        <p className="text-on-error-container text-label-md">
          <span className="material-symbols-outlined mr-2">error</span>
          {error ?? "Không thể tải cấu hình"}
        </p>
      </div>
    );
  }

  return (
    <div className="flex gap-6 h-full">
      {/* Category sidebar */}
      <nav className="w-52 shrink-0 flex flex-col gap-1" aria-label="Danh mục cấu hình">
        {categories.map((cat) => {
          const isActive = cat.categoryKey === currentCategory?.categoryKey;
          const fieldCount = cat.fields.length;
          const hasCategoryErrors = validation?.errors.some((e: ValidationViolation) =>
            cat.fields.some((f) => f.fieldPath === e.fieldPath)
          );

          return (
            <button
              key={cat.categoryKey}
              onClick={() => setActiveCategory(cat.categoryKey)}
              className={`
                flex items-center gap-3 px-3 py-3 rounded-lg text-left transition-colors
                ${isActive
                  ? "bg-primary text-on-primary font-semibold"
                  : "hover:bg-surface-container-low text-on-surface-variant hover:text-on-surface"
                }
              `}
            >
              <span className={`material-symbols-outlined text-[20px] ${isActive ? "" : CATEGORY_COLORS[cat.categoryKey] ?? ""}`}>
                {CATEGORY_ICONS[cat.categoryKey] ?? "settings"}
              </span>
              <div className="flex-1 min-w-0">
                <p className="text-label-md truncate">{cat.labelVi}</p>
                <p className="text-[11px] opacity-70">{fieldCount} trường</p>
              </div>
              {hasCategoryErrors && !isActive && (
                <span className="w-2 h-2 rounded-full bg-error shrink-0" />
              )}
            </button>
          );
        })}
      </nav>

      {/* Main content */}
      <div className="flex-1 flex flex-col min-w-0">
        {currentCategory && (
          <>
            {/* Category header */}
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-3">
                <span className={`material-symbols-outlined text-[24px] ${CATEGORY_COLORS[currentCategory.categoryKey] ?? "text-primary"}`}>
                  {CATEGORY_ICONS[currentCategory.categoryKey] ?? "settings"}
                </span>
                <div>
                  <h2 className="font-headline-md text-on-surface">{currentCategory.labelVi}</h2>
                  <p className="text-label-sm text-on-surface-variant">
                    {currentCategory.fields.length} trường
                  </p>
                </div>
              </div>

              {/* Validation badge */}
              {validation && (
                <div className="flex items-center gap-2">
                  {validation.errorCount > 0 && (
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-error-container text-on-error-container text-label-sm">
                      <span className="w-1.5 h-1.5 rounded-full bg-error" />
                      {validation.errorCount} lỗi
                    </span>
                  )}
                  {validation.warningCount > 0 && (
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-tertiary-container text-on-tertiary-container text-label-sm">
                      <span className="w-1.5 h-1.5 rounded-full bg-tertiary" />
                      {validation.warningCount} cảnh báo
                    </span>
                  )}
                  {validation.valid && (
                    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-secondary-container text-on-secondary-container text-label-sm">
                      <span className="material-symbols-outlined text-[12px]">check_circle</span>
                      Hợp lệ
                    </span>
                  )}
                </div>
              )}
            </div>

            {/* Field grid */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-x-8 gap-y-6 flex-1 overflow-auto pb-6">
              {currentCategory.fields.map((field) => (
                <ConfigField
                  key={field.fieldPath}
                  metadata={field}
                  value={getFieldValue(field.fieldPath)}
                  error={getFieldError(field.fieldPath)}
                  onChange={(v) => handleFieldChange(field.fieldPath, v)}
                />
              ))}
            </div>

            {/* Action bar */}
            <div className="flex items-center justify-between pt-4 border-t border-outline-variant shrink-0">
              <div className="flex items-center gap-2">
                {hasUnsavedChanges && (
                  <span className="text-label-sm text-tertiary flex items-center gap-1">
                    <span className="material-symbols-outlined text-[14px]">edit</span>
                    Có thay đổi chưa lưu
                  </span>
                )}
                <button
                  onClick={handleValidate}
                  disabled={validatingDraft || isSaving}
                  className="px-4 py-2 rounded-lg text-label-md text-primary border border-primary hover:bg-primary-fixed transition-colors disabled:opacity-50"
                >
                  {validatingDraft ? "Đang kiểm tra..." : "Kiểm tra"}
                </button>
              </div>

              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleReset}
                  disabled={!hasUnsavedChanges || isSaving}
                >
                  Hủy
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={handleSave}
                  loading={isSaving}
                  disabled={!hasUnsavedChanges}
                >
                  Lưu thay đổi
                </Button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// ─── Single field component ───────────────────────────────────────────────────

interface ConfigFieldProps {
  metadata: FieldMetadata;
  value: unknown;
  error?: string;
  onChange: (value: unknown) => void;
}

function ConfigField({ metadata, value, error, onChange }: ConfigFieldProps) {
  const [isDirty, setIsDirty] = useState(false);

  const handleChange = useCallback(
    (newValue: unknown) => {
      setIsDirty(true);
      onChange(newValue);
    },
    [onChange]
  );

  return (
    <div
      className={`
        p-4 rounded-xl border transition-colors
        ${error
          ? "bg-error-container border-error"
          : isDirty
          ? "bg-primary-fixed/30 border-primary/30"
          : "bg-surface-container-lowest border-outline-variant hover:border-primary/30"
        }
      `}
    >
      {/* Field label */}
      <div className="flex items-start justify-between mb-3">
        <div className="flex-1">
          <label className="font-label-md text-on-surface flex items-center gap-2">
            {metadata.required && <span className="text-error">*</span>}
            {metadata.label}
          </label>
          {metadata.description && (
            <p className="text-[12px] text-on-surface-variant mt-0.5 leading-relaxed">
              {metadata.description}
            </p>
          )}
        </div>
        {isDirty && (
          <span className="text-[11px] text-primary font-semibold">Đã sửa</span>
        )}
      </div>

      {/* Field renderer */}
      <FieldRenderer
        metadata={metadata}
        value={value}
        onChange={handleChange}
        error={error}
      />
    </div>
  );
}
