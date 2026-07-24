"use client";

import { memo } from "react";

export type PresetKey = "balanced" | "fast" | "quality" | "conservative" | "labEval" | "custom";

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

// Lab-Eval được tách riêng vì nó không phải là chế độ vận hành bình thường —
// đây là preset "Lab / Demo" (L04 dày, cross OFF, auto-adjust OFF) dành cho
// đánh giá chuyên khoa theo lời Hiến: "auto được, nhưng bắt buộc manual".
// Nút áp dụng nạp config lab vào form, vẫn ghi rõ là cấu hình thủ công đã lưu.
const LAB_EVAL_BUTTON = {
  key: "labEval" as PresetKey,
  label: "Lab-Eval",
  icon: "science",
  color: "text-purple-600",
  bg: "bg-purple-50",
  border: "border-purple-400",
};

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

      {/* Separator — Lab-Eval là chế độ đánh giá riêng, không thuộc nhóm vận hành */}
      <span className="h-7 w-px bg-outline-variant mx-1" aria-hidden="true" />

      {(() => {
        const { key, label, icon, color, bg, border } = LAB_EVAL_BUTTON;
        const isActive = activePreset === key;
        return (
          <button
            type="button"
            onClick={() => onApply(key)}
            aria-pressed={isActive}
            title="Preset Lab/Demo: nạp config L04 dày, cross-specialty OFF, auto-adjust OFF. Vẫn lưu thủ công — không tự động thay config production."
            className={`flex items-center gap-2 px-3 py-2 rounded-xl border-2 border-dashed transition-all duration-200 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary min-w-[130px] ${
              isActive
                ? `${border} ${bg} shadow-sm`
                : "border-outline-variant bg-surface-container-low/60 hover:border-purple-400/60 hover:bg-purple-50/40 hover:shadow-sm active:scale-[0.98]"
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
      })()}
    </div>
  );
});
