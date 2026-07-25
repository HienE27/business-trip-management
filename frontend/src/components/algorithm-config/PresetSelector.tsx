"use client";

import { memo } from "react";

export type PresetKey = "balanced" | "fast" | "quality" | "conservative" | "custom";

export type PresetConfig = {
  label: string;
  tagline: string;
  icon: string;
  color: string;
  colorBg: string;
  accent: string;
};

type Props = {
  activePreset: PresetKey | null;
  onApply: (key: PresetKey) => void;
};

const BUTTONS: { key: PresetKey; label: string; icon: string; color: string; bg: string; border: string }[] = [
  { key: "fast", label: "Nhanh", icon: "bolt", color: "text-amber-600", bg: "bg-amber-50", border: "border-amber-400" },
  { key: "balanced", label: "Cân bằng", icon: "psychology", color: "text-blue-600", bg: "bg-blue-50", border: "border-blue-400" },
  { key: "quality", label: "Chất lượng cao", icon: "verified_user", color: "text-emerald-600", bg: "bg-emerald-50", border: "border-emerald-400" },
  { key: "conservative", label: "Thận trọng", icon: "shield", color: "text-slate-600", bg: "bg-slate-100", border: "border-slate-400" },
];

export const PresetSelector = memo(function PresetSelector({ activePreset, onApply }: Props) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {BUTTONS.map(({ key, label, icon, color, bg, border }) => {
        const isActive = activePreset === key;
        return (
          <button
            key={key}
            type="button"
            onClick={() => onApply(key)}
            aria-pressed={isActive}
            className={`flex items-center gap-2 px-3 py-2 rounded-xl border-2 transition-all duration-200 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary min-w-[130px] ${
              isActive
                ? `${border} ${bg} shadow-sm`
                : "border-outline-variant bg-surface-container-low hover:border-primary/40 hover:bg-surface-container-lowest hover:shadow-sm active:scale-[0.98]"
            }`}
          >
            <span className={`material-symbols-outlined text-[18px] ${isActive ? color : "text-on-surface-variant"}`} aria-hidden="true">
              {icon}
            </span>
            <span className={`text-label-sm font-semibold ${isActive ? color : "text-on-surface"}`}>
              {label}
            </span>
            {isActive && <span className="material-symbols-outlined text-primary text-[14px] ml-auto" aria-hidden="true">check</span>}
          </button>
        );
      })}
    </div>
  );
});
