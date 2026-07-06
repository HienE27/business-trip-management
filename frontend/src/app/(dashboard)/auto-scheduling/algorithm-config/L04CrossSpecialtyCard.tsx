"use client";

import { useState } from "react";

type L04CrossSpecialtyCardProps = {
  enabled: boolean;
  ratio: number;
  editing: boolean;
  onChange: (enabled: boolean, ratio: number) => void;
};

export function L04CrossSpecialtyCard({ enabled, ratio, editing, onChange }: L04CrossSpecialtyCardProps) {
  const [localRatio, setLocalRatio] = useState(ratio);

  function handleToggle() {
    onChange(!enabled, localRatio);
  }

  function handleRatioChange(value: number) {
    setLocalRatio(value);
    onChange(enabled, value);
  }

  return (
    <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 border-l-4 border-l-tertiary">
      <div className="px-5 py-4 bg-surface-container-low flex items-center gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-surface-container text-tertiary">
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">swap_horiz</span>
        </div>
        <div className="flex-1">
          <p className="text-label-md font-semibold text-on-surface tracking-tight">Cross-Specialty cho L04</p>
          <p className="text-[11px] text-on-surface-variant">Gán nhân sự từ chuyên khoa khác vào PK Chuyên gia</p>
        </div>
      </div>
      <div className="p-5 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-label-sm text-on-surface font-medium">Bật Cross-Specialty</p>
            <p className="text-[11px] text-on-surface-variant mt-0.5">Cho phép gán staff khác chuyên khoa vào L04 khi cần</p>
          </div>
          {editing ? (
            <button
              type="button"
              role="switch"
              aria-checked={enabled}
              onClick={handleToggle}
              className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${
                enabled ? "bg-tertiary border-tertiary" : "bg-surface-container-high border-outline"
              }`}
            >
              <span className={`inline-block h-5 w-5 transform rounded-full bg-white shadow-sm transition-transform ${enabled ? "translate-x-6" : "translate-x-1"}`} />
            </button>
          ) : (
            <span className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-label-sm font-semibold ${enabled ? "bg-surface-container text-tertiary border border-outline-variant" : "bg-surface-container-high text-outline"}`}>
              <span className={`h-2 w-2 rounded-full ${enabled ? "bg-tertiary" : "bg-outline"}`} />
              {enabled ? "Bật" : "Tắt"}
            </span>
          )}
        </div>

        {enabled && (
          <div className="pt-2 border-t border-outline-variant">
            <div className="flex items-center justify-between mb-3">
              <div>
                <p className="text-label-sm text-on-surface font-medium">Tỷ lệ tối đa</p>
                <p className="text-[11px] text-on-surface-variant mt-0.5">Tỷ lệ staff cross-specialty cho mỗi ca L04</p>
              </div>
              <span className="font-mono text-lg font-bold text-tertiary tabular-nums">
                {Math.round(ratio * 100)}%
              </span>
            </div>
            {editing ? (
              <div className="space-y-2">
                <input
                  type="range"
                  min="0"
                  max="100"
                  step="5"
                  value={Math.round(localRatio * 100)}
                  onChange={(e) => handleRatioChange(parseInt(e.target.value) / 100)}
                  className="w-full h-2 bg-surface-variant rounded-full appearance-none cursor-pointer accent-tertiary"
                />
                <div className="flex justify-between text-[11px] text-outline">
                  <span>0%</span>
                  <span>50%</span>
                  <span>100%</span>
                </div>
              </div>
            ) : (
              <div className="w-full bg-surface-variant rounded-full h-2 overflow-hidden">
                <div
                  className="h-full rounded-full bg-tertiary transition-all"
                  style={{ width: `${ratio * 100}%` }}
                />
              </div>
            )}
          </div>
        )}

        {!enabled && (
          <div className="flex items-start gap-2 p-3 rounded-lg bg-surface-container-low">
            <span className="material-symbols-outlined text-[16px] text-tertiary shrink-0 mt-0.5" aria-hidden="true">info</span>
            <p className="text-[12px] text-on-surface-variant leading-relaxed">
              Khi bật, hệ thống sẽ cho phép gán nhân sự từ chuyên khoa khác vào ca L04 (PK Chuyên gia) khi chuyên khoa gốc không đủ nhân sự.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
