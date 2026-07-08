"use client";

import { memo, useState } from "react";

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
 * Compact preset cards với tooltip hiển thị mô tả khi hover.
 * Icon + tên + tooltip chi tiết
 */
export const PresetSelector = memo(function PresetSelector({ presets, activePreset, onApply }: Props) {
  return (
    <div className="flex flex-wrap gap-2">
      {(Object.entries(presets) as [PresetKey, PresetConfig][]).map(([key, preset]) => {
        const isActive = activePreset === key;
        return (
          <PresetTooltipButton
            key={key}
            preset={preset}
            isActive={isActive}
            onClick={() => onApply(key)}
          />
        );
      })}
    </div>
  );
});

type TooltipProps = {
  preset: PresetConfig;
  isActive: boolean;
  onClick: () => void;
};

function PresetTooltipButton({ preset, isActive, onClick }: TooltipProps) {
  const [showTooltip, setShowTooltip] = useState(false);

  return (
    <div className="relative">
      <button
        type="button"
        onClick={onClick}
        onMouseEnter={() => setShowTooltip(true)}
        onMouseLeave={() => setShowTooltip(false)}
        onFocus={() => setShowTooltip(true)}
        onBlur={() => setShowTooltip(false)}
        aria-pressed={isActive}
        aria-label={`${preset.label}: ${preset.tagline}`}
        className={`group relative flex items-center gap-2 px-3 py-2 rounded-xl border-2 transition-all duration-200 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
          isActive
            ? `${preset.accent} ${preset.colorBg} shadow-sm`
            : "border-outline-variant bg-surface-container-low hover:border-primary/40 hover:bg-surface-container-lowest hover:shadow-sm active:scale-[0.98]"
        }`}
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
      </button>

      {/* Tooltip */}
      <div
        className={`absolute top-full left-0 mt-2 z-50 transition-all duration-200 ${
          showTooltip ? "opacity-100 translate-y-0" : "opacity-0 -translate-y-1 pointer-events-none"
        }`}
      >
        <div className="bg-surface-container-lowest border border-outline-variant rounded-xl shadow-lg p-3 min-w-[200px] max-w-[280px]">
          <p className="text-label-sm font-semibold text-on-surface mb-1">{preset.label}</p>
          <p className="text-[12px] text-on-surface-variant leading-relaxed">{preset.tagline}</p>
        </div>
      </div>
    </div>
  );
}
