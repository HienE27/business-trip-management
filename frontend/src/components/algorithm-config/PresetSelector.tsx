"use client";

import { memo } from "react";

export type PresetKey = "balanced" | "fast" | "quality" | "conservative";

export type PresetConfig = {
  label: string;
  tagline: string;
  icon: string;
  color: string;
  colorBg: string;
  accent: string;
};

type Props = {
  presets: Record<PresetKey, PresetConfig>;
  activePreset: PresetKey | null;
  onApply: (key: PresetKey) => void;
};

/**
 * Grid các preset card (balanced/fast/quality/conservative) để user chọn nhanh.
 * Được dùng trong RuntimeConfigEditor.
 */
export const PresetSelector = memo(function PresetSelector({ presets, activePreset, onApply }: Props) {
  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
      {(Object.entries(presets) as [PresetKey, PresetConfig][]).map(([key, preset]) => {
        const isActive = activePreset === key;
        return (
          <button
            key={key}
            type="button"
            onClick={() => onApply(key)}
            aria-pressed={isActive}
            className={`group relative flex items-start gap-3 p-4 rounded-2xl border-2 text-left transition-all duration-200 cursor-pointer ${
              isActive
                ? `${preset.accent} ${preset.colorBg} shadow-sm`
                : "border-outline-variant bg-surface-container-low hover:border-primary/40 hover:bg-surface-container-lowest hover:shadow-sm active:scale-[0.98]"
            }`}
          >
            <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${isActive ? preset.colorBg : "bg-surface-container-high"} transition-colors`}>
              <span
                className={`material-symbols-outlined text-[20px] ${isActive ? preset.color : "text-on-surface-variant"}`}
                aria-hidden="true"
              >
                {preset.icon}
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <p className={`text-label-md font-semibold ${isActive ? preset.color : "text-on-surface"}`}>{preset.label}</p>
              <p className="text-[11px] text-on-surface-variant mt-0.5 leading-relaxed line-clamp-2">{preset.tagline}</p>
            </div>
            {isActive && (
              <div className="absolute top-2 right-2">
                <span className="material-symbols-outlined text-primary text-[14px]" aria-hidden="true">check_circle</span>
              </div>
            )}
          </button>
        );
      })}
    </div>
  );
});