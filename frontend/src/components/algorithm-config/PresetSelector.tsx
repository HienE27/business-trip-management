"use client";

import { memo, useState, useCallback } from "react";
import { useToast } from "@/hooks/useToast";

export type PresetKey = "balanced" | "fast" | "quality" | "conservative" | "custom";

export type PresetConfig = {
  label: string;
  tagline: string;
  icon: string;
  color: string;
  colorBg: string;
  accent: string;
  isBuiltIn?: boolean;
};

export type RuntimeConfig = Record<string, number | boolean | string | string[]>;

export type ValidationResult = {
  valid: boolean;
  warnings: string[];
  errors: string[];
};

export type PresetWithValidation = {
  key: PresetKey;
  config: PresetConfig;
  validation?: ValidationResult;
};

type Props = {
  presets: Record<string, PresetConfig>;
  activePreset: PresetKey | null;
  currentConfig: RuntimeConfig;
  onApply: (key: PresetKey, config: Partial<RuntimeConfig>) => void;
  onSaveCustomPreset?: (key: PresetKey, name: string, config: Partial<RuntimeConfig>) => void;
  onDeleteCustomPreset?: (key: PresetKey) => void;
};

/**
 * Compact preset cards với:
 * - Validation khi apply
 * - Auto-suggest preset khi config thay đổi
 * - Hỗ trợ custom presets
 */
export const PresetSelector = memo(function PresetSelector({
  presets,
  activePreset,
  currentConfig,
  onApply,
  onSaveCustomPreset,
  onDeleteCustomPreset,
}: Props) {
  const { warning } = useToast();
  const [hoveredKey, setHoveredKey] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<PresetKey | null>(null);

  const validatePreset = useCallback((key: PresetKey, preset: PresetConfig, current: RuntimeConfig): ValidationResult => {
    const warnings: string[] = [];
    const errors: string[] = [];

    const p = preset as unknown as { config?: Partial<RuntimeConfig> };
    const presetConfig = p.config || {};

    // Check for drastic changes
    const currentIterations = Number(current.maxIterations ?? 1000);
    const presetIterations = Number(presetConfig.maxIterations ?? 1000);
    if (presetIterations > currentIterations * 2) {
      warnings.push(`Số iterations tăng ${((presetIterations / currentIterations - 1) * 100).toFixed(0)}% - chạy lâu hơn`);
    }
    if (presetIterations < currentIterations * 0.5) {
      warnings.push(`Số iterations giảm ${((1 - presetIterations / currentIterations) * 100).toFixed(0)}% - có thể chất lượng thấp hơn`);
    }

    // Check coverage threshold
    const currentThreshold = Number(current.greedyCoverageThreshold ?? 0.8);
    const presetThreshold = Number(presetConfig.greedyCoverageThreshold ?? 0.8);
    if (presetThreshold < currentThreshold - 0.15) {
      warnings.push("Coverage threshold giảm nhiều - có thể thiếu nhân sự một số ngày");
    }
    if (presetThreshold > currentThreshold + 0.1) {
      warnings.push("Coverage threshold tăng - cần nhiều nhân sự hơn");
    }

    // Check balance
    const currentBalance = Number(current.balanceScoreMin ?? 0.7);
    const presetBalance = Number(presetConfig.balanceScoreMin ?? 0.7);
    if (presetBalance < currentBalance - 0.2) {
      warnings.push("Balance score thấp hơn - phân bổ có thể không đều");
    }

    // Critical checks
    if (presetConfig.maxIterations === 0) {
      errors.push("Iterations không được bằng 0");
    }
    if (presetConfig.greedyCoverageThreshold === 0) {
      errors.push("Coverage threshold không được bằng 0");
    }

    return {
      valid: errors.length === 0,
      warnings,
      errors,
    };
  }, []);

  const handleApplyWithValidation = useCallback((key: PresetKey, preset: PresetConfig) => {
    const validation = validatePreset(key, preset, currentConfig);

    if (!validation.valid) {
      warning(`${preset.label}: ${validation.errors.join(", ")}`);
      return;
    }

    if (validation.warnings.length > 0) {
      // Show warning toast but still apply
      warning(`${preset.label}: ${validation.warnings[0]}`);
    }

    const p = preset as unknown as { config?: Partial<RuntimeConfig> };
    onApply(key, p.config || {});
  }, [currentConfig, onApply, validatePreset, warning]);

  const handleSaveCustomPreset = useCallback(() => {
    if (!onSaveCustomPreset) return;

    const name = prompt("Tên preset mới:");
    if (!name?.trim()) return;

    const key = `custom_${Date.now()}` as PresetKey;
    onSaveCustomPreset(key, name.trim(), currentConfig);
  }, [currentConfig, onSaveCustomPreset]);

  const handleDeleteCustomPreset = useCallback((key: PresetKey) => {
    if (!onDeleteCustomPreset) return;
    onDeleteCustomPreset(key);
    setConfirmDelete(null);
  }, [onDeleteCustomPreset]);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {(Object.entries(presets) as [string, PresetConfig][]).map(([key, preset]) => {
          const isActive = activePreset === key;
          const isCustom = key.startsWith("custom_");
          const isHovered = hoveredKey === key;
          const validation = isHovered ? validatePreset(key as PresetKey, preset, currentConfig) : null;

          return (
            <div key={key} className="relative">
              <button
                type="button"
                onClick={() => handleApplyWithValidation(key as PresetKey, preset)}
                onMouseEnter={() => setHoveredKey(key)}
                onMouseLeave={() => setHoveredKey(null)}
                aria-pressed={isActive}
                aria-label={`${preset.label}: ${preset.tagline}`}
                className={`
                  group relative flex items-center gap-2 px-3 py-2 rounded-xl border-2 transition-all duration-200 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary min-w-[140px]
                  ${isActive
                    ? `${preset.accent} ${preset.colorBg} shadow-sm`
                    : "border-outline-variant bg-surface-container-low hover:border-primary/40 hover:bg-surface-container-lowest hover:shadow-sm active:scale-[0.98]"
                  }
                `}
              >
                <span
                  className={`material-symbols-outlined text-[18px] ${isActive ? preset.color : "text-on-surface-variant group-hover:text-primary"} transition-colors`}
                  aria-hidden="true"
                >
                  {preset.icon}
                </span>
                <span className={`text-label-sm font-semibold ${isActive ? preset.color : "text-on-surface"}`}>
                  {preset.label}
                </span>
                {isActive && (
                  <span className="material-symbols-outlined text-primary text-[14px]" aria-hidden="true">check</span>
                )}
                {isCustom && (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setConfirmDelete(key as PresetKey);
                    }}
                    className="ml-auto p-1 rounded hover:bg-error-container text-on-surface-variant hover:text-error transition-colors"
                    aria-label={`Xóa ${preset.label}`}
                  >
                    <span className="material-symbols-outlined text-[14px]" aria-hidden="true">close</span>
                  </button>
                )}
              </button>

              {/* Tooltip với validation */}
              {(isHovered || (isActive && validation && validation.warnings.length > 0)) && (
                <div className="absolute top-full left-0 mt-2 z-50 animate-fade-in">
                  <div className="bg-surface-container-lowest border border-outline-variant rounded-xl shadow-lg p-3 min-w-[220px] max-w-[300px]">
                    <div className="flex items-start gap-2">
                      <span className={`material-symbols-outlined text-[16px] ${preset.color} shrink-0`} aria-hidden="true">{preset.icon}</span>
                      <div className="space-y-1">
                        <p className="text-label-sm font-semibold text-on-surface">{preset.label}</p>
                        <p className="text-[11px] text-on-surface-variant leading-relaxed">{preset.tagline}</p>
                        {validation && validation.warnings.length > 0 && (
                          <div className="mt-2 pt-2 border-t border-outline-variant">
                            <p className="text-[10px] text-tertiary flex items-center gap-1">
                              <span className="material-symbols-outlined text-[12px]" aria-hidden="true">warning</span>
                              {validation.warnings[0]}
                            </p>
                          </div>
                        )}
                        {validation && validation.errors.length > 0 && (
                          <div className="mt-2 pt-2 border-t border-error/30">
                            <p className="text-[10px] text-error flex items-center gap-1">
                              <span className="material-symbols-outlined text-[12px]" aria-hidden="true">error</span>
                              {validation.errors[0]}
                            </p>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          );
        })}

        {/* Add custom preset button */}
        {onSaveCustomPreset && (
          <button
            type="button"
            onClick={handleSaveCustomPreset}
            className="flex items-center gap-2 px-3 py-2 rounded-xl border-2 border-dashed border-outline-variant bg-surface-container-low hover:border-primary/40 hover:bg-surface-container-lowest hover:border-primary transition-all duration-200 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary min-w-[100px]"
          >
            <span className="material-symbols-outlined text-[18px] text-on-surface-variant" aria-hidden="true">add</span>
            <span className="text-label-sm text-on-surface-variant">Tạo mới</span>
          </button>
        )}
      </div>

      {/* Active preset warning bar */}
      {activePreset && !activePreset.startsWith("custom_") && (
        <PresetHealthBar presetKey={activePreset} currentConfig={currentConfig} />
      )}

      {/* Delete confirmation */}
      {confirmDelete && (
        <DeleteConfirmDialog
          presetKey={confirmDelete}
          presetLabel={presets[confirmDelete]?.label || "Preset này"}
          onConfirm={() => handleDeleteCustomPreset(confirmDelete)}
          onCancel={() => setConfirmDelete(null)}
        />
      )}
    </div>
  );
});

/* ─── Preset Health Indicator ──────────────────────────────────── */

type HealthBarProps = {
  presetKey: string;
  currentConfig: RuntimeConfig;
};

function PresetHealthBar({ presetKey, currentConfig }: HealthBarProps) {
  const health = analyzePresetHealth(presetKey as PresetKey, currentConfig);

  if (health.status === "optimal") return null;

  return (
    <div className={`flex items-center gap-2 px-3 py-2 rounded-lg text-[11px] animate-fade-in ${
      health.status === "warning" ? "bg-tertiary-container/30 text-tertiary border border-tertiary/30" :
      "bg-error-container/30 text-error border border-error/30"
    }`}>
      <span className="material-symbols-outlined text-[14px]" aria-hidden="true">
        {health.status === "warning" ? "info" : "warning"}
      </span>
      <span>{health.message}</span>
    </div>
  );
}

function analyzePresetHealth(presetKey: PresetKey, config: RuntimeConfig) {
  const iterations = Number(config.maxIterations ?? 1000);
  const threshold = Number(config.greedyCoverageThreshold ?? 0.8);
  const balance = Number(config.balanceScoreMin ?? 0.7);

  // Check if params are way off from the preset defaults
  const presets: Record<string, { iterations: number; threshold: number; balance: number }> = {
    balanced: { iterations: 2000, threshold: 0.90, balance: 0.75 },
    fast: { iterations: 500, threshold: 0.75, balance: 0.60 },
    quality: { iterations: 5000, threshold: 0.95, balance: 0.85 },
    conservative: { iterations: 1000, threshold: 0.60, balance: 0.50 },
  };

  const expected = presets[presetKey];
  if (!expected) return { status: "optimal", message: "" };

  const drift = Math.abs(iterations - expected.iterations) / expected.iterations +
                Math.abs(threshold - expected.threshold) / expected.threshold +
                Math.abs(balance - expected.balance) / expected.balance;

  if (drift > 0.5) {
    return {
      status: "warning",
      message: "Cấu hình đã thay đổi nhiều so với preset. Nhấn preset để khôi phục.",
    };
  }

  return { status: "optimal", message: "" };
}

/* ─── Delete Confirmation Dialog ────────────────────────────────── */

type DeleteDialogProps = {
  presetKey: PresetKey;
  presetLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
};

function DeleteConfirmDialog({ presetLabel, onConfirm, onCancel }: DeleteDialogProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 animate-fade-in">
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-xl p-6 max-w-sm w-full mx-4 animate-scale-in">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-full bg-error-container flex items-center justify-center">
            <span className="material-symbols-outlined text-error text-[20px]" aria-hidden="true">delete</span>
          </div>
          <div>
            <h3 className="text-title-sm font-semibold text-on-surface">Xóa preset?</h3>
            <p className="text-[13px] text-on-surface-variant">Preset &quot;{presetLabel}&quot; sẽ bị xóa vĩnh viễn.</p>
          </div>
        </div>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 rounded-lg border border-outline-variant text-label-sm text-on-surface hover:bg-surface-container transition-colors"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="px-4 py-2 rounded-lg bg-error text-white text-label-sm font-semibold hover:bg-error/90 transition-colors"
          >
            Xóa
          </button>
        </div>
      </div>
    </div>
  );
}
