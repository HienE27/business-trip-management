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
 * Segmented control style preset selector - pill tabs liền mạch.
 * Compact, modern, và dễ scan.
 */
export const PresetSelector = memo(function PresetSelector({ presets, activePreset, onApply }: Props) {
  const [hoveredKey, setHoveredKey] = useState<PresetKey | null>(null);
  const showTooltip = hoveredKey && activePreset !== hoveredKey;

  return (
    <div className="space-y-3">
      {/* Segmented control */}
      <div className="relative inline-flex bg-surface-container-low rounded-xl p-1 border border-outline-variant">
        {(Object.entries(presets) as [PresetKey, PresetConfig][]).map(([key, preset], idx, arr) => {
          const isActive = activePreset === key;
          const isFirst = idx === 0;
          const isLast = idx === arr.length - 1;

          return (
            <button
              key={key}
              type="button"
              onClick={() => onApply(key)}
              onMouseEnter={() => setHoveredKey(key)}
              onMouseLeave={() => setHoveredKey(null)}
              aria-pressed={isActive}
              className={`
                relative flex items-center gap-1.5 px-4 py-2 rounded-lg transition-all duration-200
                focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary
                ${isActive 
                  ? `font-semibold ${preset.color} ${preset.colorBg}` 
                  : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }
              `}
            >
              {isActive && (
                <span className={`material-symbols-outlined text-[14px] ${preset.color}`} aria-hidden="true">check</span>
              )}
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{preset.icon}</span>
              <span className="text-label-sm">{preset.label}</span>
            </button>
          );
        })}
      </div>

      {/* Tooltip / description bar - hiển thị tagline của preset đang hover hoặc active */}
      {showTooltip && hoveredKey && (
        <div className="flex items-center gap-2 px-4 py-2 bg-surface-container-low rounded-lg border border-outline-variant animate-fade-in">
          <span className="material-symbols-outlined text-primary text-[16px]" aria-hidden="true">info</span>
          <p className="text-[13px] text-on-surface-variant">
            {presets[hoveredKey].tagline}
          </p>
        </div>
      )}

      {/* Active preset detail - hiển thị thông tin chi tiết khi có preset active */}
      {activePreset && (
        <PresetDetailCard preset={presets[activePreset]} />
      )}
    </div>
  );
});

type DetailProps = {
  preset: PresetConfig;
};

function PresetDetailCard({ preset }: DetailProps) {
  const details = getPresetDetails(preset.label);

  return (
    <div className={`flex flex-wrap items-center gap-4 px-4 py-3 rounded-xl border ${preset.colorBg} ${preset.accent.replace("border-", "border-").replace("text-", "text-")} animate-fade-in`}>
      <div className="flex items-center gap-2">
        <span className={`material-symbols-outlined text-[20px] ${preset.color}`} aria-hidden="true">{preset.icon}</span>
        <span className={`text-label-md font-semibold ${preset.color}`}>{preset.label}</span>
      </div>
      <div className="w-px h-5 bg-current opacity-30" />
      <div className="flex flex-wrap items-center gap-3 text-[12px] text-on-surface-variant">
        {details.map((detail, idx) => (
          <span key={idx} className="flex items-center gap-1">
            <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{detail.icon}</span>
            {detail.text}
          </span>
        ))}
      </div>
    </div>
  );
}

function getPresetDetails(label: string) {
  const presets: Record<string, { icon: string; text: string }[]> = {
    "Cân bằng": [
      { icon: "speed", text: "Tốc độ vừa phải" },
      { icon: "verified", text: "Chất lượng tốt" },
      { icon: "balance", text: "Cân bằng tải" },
    ],
    "Nhanh": [
      { icon: "bolt", text: "Ưu tiên tốc độ" },
      { icon: "flash_on", text: "Phủ lịch nhanh" },
      { icon: "schedule", text: "Thời gian ngắn" },
    ],
    "Chất lượng cao": [
      { icon: "star", text: "Tối ưu hóa cao" },
      { icon: "psychology", text: "Tìm lời giải tốt nhất" },
      { icon: "hourglass_top", text: "Chạy chậm hơn" },
    ],
    "Thận trọng": [
      { icon: "shield", text: "Ít thay đổi" },
      { icon: "history", text: "Giữ nguyên lịch" },
      { icon: "warning", text: "Rủi ro thấp" },
    ],
  };
  return presets[label] ?? [];
}
