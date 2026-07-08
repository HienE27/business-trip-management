"use client";

import { memo, useState, useCallback, useMemo } from "react";
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

export type SuggestionReason = {
  type: "speed" | "quality" | "balance" | "coverage";
  message: string;
  score: number;
};

type Props = {
  presets: Record<string, PresetConfig>;
  activePreset: PresetKey | null;
  currentConfig: RuntimeConfig;
  onApply: (key: PresetKey, config: Partial<RuntimeConfig>) => void;
  onSaveCustomPreset?: (key: PresetKey, name: string, config: Partial<RuntimeConfig>) => void;
  onDeleteCustomPreset?: (key: PresetKey) => void;
  scheduleStats?: {
    totalStaff: number;
    avgShiftsPerStaff: number;
    coverageDays: number;
    periodDays: number;
  };
};

/**
 * PresetSelector với:
 * - Validation khi apply
 * - Auto-suggest preset dựa trên data thực tế
 * - Visual comparison overlay (A vs B vs current)
 * - A/B preview cho kết quả scheduling
 */
export const PresetSelector = memo(function PresetSelector({
  presets,
  activePreset,
  currentConfig,
  onApply,
  onSaveCustomPreset,
  onDeleteCustomPreset,
  scheduleStats,
}: Props) {
  const { warning } = useToast();
  const [hoveredKey, setHoveredKey] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<PresetKey | null>(null);
  const [showComparison, setShowComparison] = useState(false);
  const [compareA, setCompareA] = useState<PresetKey | null>(null);
  const [compareB, setCompareB] = useState<PresetKey | null>(null);
  const [showSuggestion, setShowSuggestion] = useState(false);

  // ─── Validation Logic ──────────────────────────────────────────────
  const validatePreset = useCallback((key: PresetKey, preset: PresetConfig, current: RuntimeConfig): ValidationResult => {
    const warnings: string[] = [];
    const errors: string[] = [];

    const p = preset as unknown as { config?: Partial<RuntimeConfig> };
    const presetConfig = p.config || {};

    const currentIterations = Number(current.maxIterations ?? 1000);
    const presetIterations = Number(presetConfig.maxIterations ?? 1000);
    if (presetIterations > currentIterations * 2) {
      warnings.push(`Iterations tăng ${((presetIterations / currentIterations - 1) * 100).toFixed(0)}% - chạy lâu hơn`);
    }
    if (presetIterations < currentIterations * 0.5) {
      warnings.push(`Iterations giảm ${((1 - presetIterations / currentIterations) * 100).toFixed(0)}% - có thể chất lượng thấp hơn`);
    }

    const currentThreshold = Number(current.greedyCoverageThreshold ?? 0.8);
    const presetThreshold = Number(presetConfig.greedyCoverageThreshold ?? 0.8);
    if (presetThreshold < currentThreshold - 0.15) {
      warnings.push("Coverage threshold giảm nhiều - có thể thiếu nhân sự một số ngày");
    }

    const currentBalance = Number(current.balanceScoreMin ?? 0.7);
    const presetBalance = Number(presetConfig.balanceScoreMin ?? 0.7);
    if (presetBalance < currentBalance - 0.2) {
      warnings.push("Balance score thấp hơn - phân bổ có thể không đều");
    }

    if (presetConfig.maxIterations === 0) errors.push("Iterations không được bằng 0");
    if (presetConfig.greedyCoverageThreshold === 0) errors.push("Coverage threshold không được bằng 0");

    return { valid: errors.length === 0, warnings, errors };
  }, []);

  // ─── Smart Suggestion Logic ───────────────────────────────────────
  const getSuggestion = useCallback((): { preset: PresetKey; reasons: SuggestionReason[] } | null => {
    if (!scheduleStats) return null;

    const { totalStaff, avgShiftsPerStaff, coverageDays, periodDays } = scheduleStats;
    const coverageRatio = coverageDays / periodDays;
    const loadPerStaff = avgShiftsPerStaff > 0 ? avgShiftsPerStaff / totalStaff : 0.5;

    const scores: Record<PresetKey, number> = {
      balanced: 0,
      fast: 0,
      quality: 0,
      conservative: 0,
      custom: 0,
    };

    const reasons: Record<PresetKey, SuggestionReason[]> = {
      balanced: [],
      fast: [],
      quality: [],
      conservative: [],
      custom: [],
    };

    // Speed priority: deadline gần
    if (periodDays <= 7) {
      scores.fast += 30;
      reasons.fast.push({ type: "speed", message: "Kỳ lịch ngắn - ưu tiên tốc độ", score: 30 });
    }

    // Quality priority: coverage thấp hoặc load cao
    if (coverageRatio < 0.7) {
      scores.quality += 25;
      reasons.quality.push({ type: "coverage", message: "Coverage thấp - cần tìm lời giải tối ưu", score: 25 });
    }
    if (loadPerStaff > 0.8) {
      scores.quality += 20;
      reasons.quality.push({ type: "quality", message: "Tải cao - cần cân bằng kỹ", score: 20 });
    }

    // Balance: trung bình
    scores.balanced += 15;

    // Conservative: có lịch ổn định
    if (coverageRatio >= 0.85 && loadPerStaff < 0.6) {
      scores.conservative += 25;
      reasons.conservative.push({ type: "balance", message: "Lịch ổn định - ít thay đổi", score: 25 });
    }

    // Fast: nhân sự đủ
    if (totalStaff >= 15) {
      scores.fast += 15;
      reasons.fast.push({ type: "speed", message: "Đủ nhân sự - chạy nhanh", score: 15 });
    }

    // Find best
    let bestPreset: PresetKey = "balanced";
    let bestScore = 0;
    for (const [key, score] of Object.entries(scores)) {
      if (score > bestScore && !key.startsWith("custom_")) {
        bestScore = score;
        bestPreset = key as PresetKey;
      }
    }

    return bestScore > 10 ? { preset: bestPreset, reasons: reasons[bestPreset] } : null;
  }, [scheduleStats]);

  const suggestion = useMemo(() => getSuggestion(), [getSuggestion]);

  // ─── Handlers ───────────────────────────────────────────────────────
  const handleApplyWithValidation = useCallback((key: PresetKey, preset: PresetConfig) => {
    const validation = validatePreset(key, preset, currentConfig);

    if (!validation.valid) {
      warning(`${preset.label}: ${validation.errors.join(", ")}`);
      return;
    }

    if (validation.warnings.length > 0) {
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

  const presetEntries = Object.entries(presets) as [string, PresetConfig][];
  const builtInKeys = presetEntries.filter(([k]) => !k.startsWith("custom_")).map(([k]) => k as PresetKey);

  return (
    <div className="space-y-3">
      {/* Suggestion Banner */}
      {suggestion && !activePreset && (
        <div
          className="flex items-center justify-between gap-3 px-4 py-3 rounded-xl bg-primary-fixed/50 border border-primary/20 animate-slide-down cursor-pointer hover:bg-primary-fixed transition-colors"
          onClick={() => setShowSuggestion(!showSuggestion)}
        >
          <div className="flex items-center gap-3">
            <span className="material-symbols-outlined text-primary text-[20px]" aria-hidden="true">auto_awesome</span>
            <div>
              <p className="text-label-sm font-semibold text-primary">Gợi ý: {presets[suggestion.preset]?.label}</p>
              <p className="text-[11px] text-on-surface-variant">{suggestion.reasons[0]?.message}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                handleApplyWithValidation(suggestion.preset, presets[suggestion.preset]);
              }}
              className="px-3 py-1.5 rounded-lg bg-primary text-white text-[11px] font-semibold hover:bg-primary/90 transition-colors"
            >
              Áp dụng
            </button>
            <span className="material-symbols-outlined text-primary text-[16px] transition-transform" style={{ transform: showSuggestion ? "rotate(180deg)" : "none" }}>
              expand_more
            </span>
          </div>
        </div>
      )}

      {/* Suggestion Details */}
      {showSuggestion && suggestion && (
        <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant animate-fade-in">
          <p className="text-label-sm font-semibold text-on-surface mb-3">Lý do gợi ý:</p>
          <div className="space-y-2">
            {suggestion.reasons.map((r, i) => (
              <div key={i} className="flex items-center gap-2 text-[12px] text-on-surface-variant">
                <span className={`material-symbols-outlined text-[14px] ${
                  r.type === "speed" ? "text-amber-500" :
                  r.type === "quality" ? "text-emerald-500" :
                  r.type === "coverage" ? "text-blue-500" : "text-purple-500"
                }`} aria-hidden="true">
                  {r.type === "speed" ? "bolt" : r.type === "quality" ? "verified_user" : r.type === "coverage" ? "event_available" : "balance"}
                </span>
                <span>{r.message}</span>
                <span className="ml-auto text-primary font-semibold">+{r.score}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div className="flex flex-wrap gap-2">
          {presetEntries.map(([key, preset]) => {
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
                  <span className={`material-symbols-outlined text-[18px] ${isActive ? preset.color : "text-on-surface-variant group-hover:text-primary"} transition-colors`} aria-hidden="true">
                    {preset.icon}
                  </span>
                  <span className={`text-label-sm font-semibold ${isActive ? preset.color : "text-on-surface"}`}>
                    {preset.label}
                  </span>
                  {isActive && <span className="material-symbols-outlined text-primary text-[14px]" aria-hidden="true">check</span>}
                  {isCustom && (
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); setConfirmDelete(key as PresetKey); }}
                      className="ml-auto p-1 rounded hover:bg-error-container text-on-surface-variant hover:text-error transition-colors"
                      aria-label={`Xóa ${preset.label}`}
                    >
                      <span className="material-symbols-outlined text-[14px]" aria-hidden="true">close</span>
                    </button>
                  )}
                </button>

                {isHovered && validation && (validation.warnings.length > 0 || validation.errors.length > 0) && (
                  <div className="absolute top-full left-0 mt-2 z-50 animate-fade-in">
                    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl shadow-lg p-3 min-w-[220px] max-w-[300px]">
                      <div className="space-y-1">
                        <p className="text-label-sm font-semibold text-on-surface">{preset.label}</p>
                        <p className="text-[11px] text-on-surface-variant">{preset.tagline}</p>
                        {validation.warnings.length > 0 && (
                          <div className="mt-2 pt-2 border-t border-outline-variant">
                            <p className="text-[10px] text-tertiary flex items-center gap-1">
                              <span className="material-symbols-outlined text-[12px]" aria-hidden="true">warning</span>
                              {validation.warnings[0]}
                            </p>
                          </div>
                        )}
                        {validation.errors.length > 0 && (
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
                )}
              </div>
            );
          })}

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

        {/* Comparison Button */}
        <button
          type="button"
          onClick={() => {
            if (!compareA) setCompareA(builtInKeys[0]);
            if (!compareB) setCompareB(builtInKeys[1]);
            setShowComparison(true);
          }}
          className="flex items-center gap-2 px-3 py-2 rounded-lg bg-surface-container-low border border-outline-variant hover:border-primary/40 hover:bg-surface-container transition-colors text-[12px]"
        >
          <span className="material-symbols-outlined text-[16px]" aria-hidden="true">compare</span>
          So sánh
        </button>
      </div>

      {/* Health Bar */}
      {activePreset && !activePreset.startsWith("custom_") && (
        <PresetHealthBar presetKey={activePreset} currentConfig={currentConfig} />
      )}

      {/* Comparison Modal */}
      {showComparison && (
        <ComparisonOverlay
          presets={presets}
          compareA={compareA || builtInKeys[0]}
          compareB={compareB || builtInKeys[1]}
          currentConfig={currentConfig}
          onChangeA={setCompareA}
          onChangeB={setCompareB}
          onApply={(key) => {
            handleApplyWithValidation(key, presets[key]);
            setShowComparison(false);
          }}
          onClose={() => setShowComparison(false)}
        />
      )}

      {/* Delete Confirmation */}
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

/* ─── Comparison Overlay ──────────────────────────────────────────── */

type ComparisonProps = {
  presets: Record<string, PresetConfig>;
  compareA: PresetKey;
  compareB: PresetKey;
  currentConfig: RuntimeConfig;
  onChangeA: (key: PresetKey) => void;
  onChangeB: (key: PresetKey) => void;
  onApply: (key: PresetKey) => void;
  onClose: () => void;
};

function ComparisonOverlay({ presets, compareA, compareB, currentConfig, onChangeA, onChangeB, onApply, onClose }: ComparisonProps) {
  const params = ["maxIterations", "greedyCoverageThreshold", "balanceScoreMin", "weekendWeight", "backtrackTimeLimitSeconds"] as const;

  const builtInPresets = Object.entries(presets)
    .filter(([k]) => !k.startsWith("custom_"))
    .map(([k, v]) => ({ key: k as PresetKey, ...v }));

  const getValue = (key: PresetKey | null, param: string) => {
    if (!key) return 0;
    const p = presets[key] as unknown as { config?: Partial<RuntimeConfig> };
    return Number(p?.config?.[param] ?? currentConfig[param] ?? 0);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 animate-fade-in" onClick={onClose}>
      <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant shadow-xl w-full max-w-3xl mx-4 animate-scale-in" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <div className="flex items-center gap-3">
            <span className="material-symbols-outlined text-primary text-[24px]" aria-hidden="true">compare</span>
            <h2 className="text-title-md font-semibold text-on-surface">So sánh Preset</h2>
          </div>
          <button type="button" onClick={onClose} className="p-2 rounded-lg hover:bg-surface-container transition-colors">
            <span className="material-symbols-outlined text-[20px]" aria-hidden="true">close</span>
          </button>
        </div>

        {/* Preset Selectors */}
        <div className="flex items-center justify-center gap-8 px-6 py-4 bg-surface-container-low">
          <div className="flex flex-col items-center gap-2">
            <span className="text-[11px] text-on-surface-variant uppercase tracking-wide">Preset A</span>
            <select
              value={compareA}
              onChange={e => onChangeA(e.target.value as PresetKey)}
              className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-sm cursor-pointer"
            >
              {builtInPresets.map(p => (
                <option key={p.key} value={p.key}>{p.label}</option>
              ))}
            </select>
          </div>
          <span className="material-symbols-outlined text-[24px] text-on-surface-variant" aria-hidden="true">vs</span>
          <div className="flex flex-col items-center gap-2">
            <span className="text-[11px] text-on-surface-variant uppercase tracking-wide">Preset B</span>
            <select
              value={compareB}
              onChange={e => onChangeB(e.target.value as PresetKey)}
              className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-sm cursor-pointer"
            >
              {builtInPresets.map(p => (
                <option key={p.key} value={p.key}>{p.label}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Comparison Table */}
        <div className="p-6">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-outline-variant">
                  <th className="text-left py-3 px-4 text-label-sm text-on-surface-variant font-semibold">Thông số</th>
                  <th className="text-center py-3 px-4 text-label-sm text-on-surface-variant font-semibold min-w-[120px]">Hiện tại</th>
                  <th className={`text-center py-3 px-4 text-label-sm font-semibold min-w-[120px] ${presets[compareA]?.colorBg || ""}`}>
                    {presets[compareA]?.label}
                  </th>
                  <th className={`text-center py-3 px-4 text-label-sm font-semibold min-w-[120px] ${presets[compareB]?.colorBg || ""}`}>
                    {presets[compareB]?.label}
                  </th>
                </tr>
              </thead>
              <tbody>
                {params.map(param => {
                  const current = Number(currentConfig[param] ?? 0);
                  const valA = getValue(compareA, param);
                  const valB = getValue(compareB, param);
                  const max = getParamMax(param);

                  return (
                    <tr key={param} className="border-b border-outline-variant/50 hover:bg-surface-container-low transition-colors">
                      <td className="py-3 px-4">
                        <p className="text-label-sm font-medium text-on-surface">{formatParamLabel(param)}</p>
                        <p className="text-[10px] text-on-surface-variant">{formatParamDesc(param)}</p>
                      </td>
                      <td className="text-center py-3 px-4">
                        <div className="relative">
                          <BarChart value={current} max={max} color="bg-surface-variant" />
                          <span className="font-mono text-[13px] font-semibold text-on-surface">{formatValue(current, param)}</span>
                        </div>
                      </td>
                      <td className="text-center py-3 px-4">
                        <div className="relative">
                          <BarChart value={valA} max={max} color={presets[compareA]?.accent?.replace("border-", "bg-") || "bg-blue-400"} />
                          <span className={`font-mono text-[13px] font-semibold ${current === valA ? "text-primary" : "text-on-surface"}`}>
                            {formatValue(valA, param)}
                          </span>
                          {valA !== current && (
                            <span className={`absolute -top-1 -right-1 text-[10px] px-1 rounded ${valA > current ? "text-emerald-500" : "text-rose-500"}`}>
                              {valA > current ? "+" : ""}{((valA - current) / (max || 1) * 100).toFixed(0)}%
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="text-center py-3 px-4">
                        <div className="relative">
                          <BarChart value={valB} max={max} color={presets[compareB]?.accent?.replace("border-", "bg-") || "bg-emerald-400"} />
                          <span className={`font-mono text-[13px] font-semibold ${current === valB ? "text-primary" : "text-on-surface"}`}>
                            {formatValue(valB, param)}
                          </span>
                          {valB !== current && (
                            <span className={`absolute -top-1 -right-1 text-[10px] px-1 rounded ${valB > current ? "text-emerald-500" : "text-rose-500"}`}>
                              {valB > current ? "+" : ""}{((valB - current) / (max || 1) * 100).toFixed(0)}%
                            </span>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Action Buttons */}
          <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-outline-variant">
            <button type="button" onClick={onClose} className="px-4 py-2 rounded-lg border border-outline-variant text-label-sm hover:bg-surface-container transition-colors">
              Đóng
            </button>
            <button
              type="button"
              onClick={() => onApply(compareA)}
              className={`px-4 py-2 rounded-lg text-white text-label-sm font-semibold transition-colors ${presets[compareA]?.accent?.replace("border-", "bg-") || "bg-blue-500"} hover:opacity-90`}
            >
              Áp dụng {presets[compareA]?.label}
            </button>
            <button
              type="button"
              onClick={() => onApply(compareB)}
              className={`px-4 py-2 rounded-lg text-white text-label-sm font-semibold transition-colors ${presets[compareB]?.accent?.replace("border-", "bg-") || "bg-emerald-500"} hover:opacity-90`}
            >
              Áp dụng {presets[compareB]?.label}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ─── Bar Chart Component ──────────────────────────────────────────── */

function BarChart({ value, max, color }: { value: number; max: number; color: string }) {
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0;
  return (
    <div className="w-full h-2 bg-surface-variant rounded-full overflow-hidden mb-1">
      <div className={`h-full rounded-full transition-all duration-300 ${color}`} style={{ width: `${pct}%` }} />
    </div>
  );
}

/* ─── Health Bar ───────────────────────────────────────────────────── */

type HealthBarProps = { presetKey: string; currentConfig: RuntimeConfig };

function PresetHealthBar({ presetKey, currentConfig }: HealthBarProps) {
  const health = analyzePresetHealth(presetKey as PresetKey, currentConfig);
  if (health.status === "optimal") return null;

  return (
    <div className={`flex items-center gap-2 px-3 py-2 rounded-lg text-[11px] animate-fade-in ${
      health.status === "warning" ? "bg-tertiary-container/30 text-tertiary border border-tertiary/30" :
      "bg-error-container/30 text-error border border-error/30"
    }`}>
      <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{health.status === "warning" ? "info" : "warning"}</span>
      <span>{health.message}</span>
    </div>
  );
}

function analyzePresetHealth(presetKey: PresetKey, config: RuntimeConfig) {
  const iterations = Number(config.maxIterations ?? 1000);
  const threshold = Number(config.greedyCoverageThreshold ?? 0.8);
  const balance = Number(config.balanceScoreMin ?? 0.7);

  const presets: Record<string, { iterations: number; threshold: number; balance: number }> = {
    balanced: { iterations: 2000, threshold: 0.90, balance: 0.75 },
    fast: { iterations: 500, threshold: 0.75, balance: 0.60 },
    quality: { iterations: 5000, threshold: 0.95, balance: 0.85 },
    conservative: { iterations: 1000, threshold: 0.60, balance: 0.50 },
  };

  const expected = presets[presetKey];
  if (!expected) return { status: "optimal" as const, message: "" };

  const drift = Math.abs(iterations - expected.iterations) / expected.iterations +
                Math.abs(threshold - expected.threshold) / expected.threshold +
                Math.abs(balance - expected.balance) / expected.balance;

  if (drift > 0.5) {
    return { status: "warning" as const, message: "Cấu hình đã thay đổi nhiều so với preset. Nhấn preset để khôi phục." };
  }

  return { status: "optimal" as const, message: "" };
}

/* ─── Delete Confirmation Dialog ───────────────────────────────────── */

type DeleteDialogProps = { presetKey: PresetKey; presetLabel: string; onConfirm: () => void; onCancel: () => void };

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
          <button type="button" onClick={onCancel} className="px-4 py-2 rounded-lg border border-outline-variant text-label-sm hover:bg-surface-container transition-colors">Hủy</button>
          <button type="button" onClick={onConfirm} className="px-4 py-2 rounded-lg bg-error text-white text-label-sm font-semibold hover:bg-error/90 transition-colors">Xóa</button>
        </div>
      </div>
    </div>
  );
}

/* ─── Helpers ─────────────────────────────────────────────────────── */

function formatParamLabel(param: string): string {
  const labels: Record<string, string> = {
    maxIterations: "Iterations",
    greedyCoverageThreshold: "Coverage",
    balanceScoreMin: "Balance",
    weekendWeight: "Weekend",
    backtrackTimeLimitSeconds: "Backtrack",
  };
  return labels[param] || param;
}

function formatParamDesc(param: string): string {
  const descs: Record<string, string> = {
    maxIterations: "Số lần lặp tối đa",
    greedyCoverageThreshold: "Ngưỡng phủ lịch",
    balanceScoreMin: "Điểm cân bằng tối thiểu",
    weekendWeight: "Trọng số cuối tuần",
    backtrackTimeLimitSeconds: "Giới hạn backtrack (s)",
  };
  return descs[param] || "";
}

function getParamMax(param: string): number {
  const maxes: Record<string, number> = {
    maxIterations: 5000,
    greedyCoverageThreshold: 1,
    balanceScoreMin: 1,
    weekendWeight: 5,
    backtrackTimeLimitSeconds: 300,
  };
  return maxes[param] || 100;
}

function formatValue(value: number, param: string): string {
  if (param === "greedyCoverageThreshold" || param === "balanceScoreMin") {
    return `${(value * 100).toFixed(0)}%`;
  }
  if (param === "weekendWeight") {
    return value.toFixed(1);
  }
  if (param === "backtrackTimeLimitSeconds") {
    return `${value}s`;
  }
  return value.toString();
}
