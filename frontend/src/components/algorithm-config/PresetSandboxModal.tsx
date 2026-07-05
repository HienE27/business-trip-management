"use client";

import { useState, useMemo } from "react";
import { Button } from "@/components/ui/Button";
import { formatPercent } from "@/lib/number-utils";

export type RuntimeConfig = Record<string, number | boolean | string>;

export type PresetEntry = {
  key: string;
  label: string;
  tagline: string;
  icon: string;
  config: RuntimeConfig;
};

type Props = {
  open: boolean;
  onClose: () => void;
  presets: Record<string, PresetEntry>;
  currentConfig: RuntimeConfig;
  onApply: (preset: PresetEntry) => void;
};

/**
 * Sandbox cho phép user thử preset mà không commit:
 * - Side-by-side so sánh current vs preset
 * - Hiển thị diff (tăng/giảm) cho từng tham số
 * - Nhấn "Áp dụng & lưu" để ghi đè form
 */
export function PresetSandboxModal({ open, onClose, presets, currentConfig, onApply }: Props) {
  const [selectedKey, setSelectedKey] = useState<string | null>(null);

  const selected = selectedKey ? presets[selectedKey] : null;

  const numericKeys = useMemo(() => {
    const keys = new Set<string>();
    if (currentConfig) {
      Object.keys(currentConfig).forEach(k => {
        const v = (currentConfig as Record<string, unknown>)[k];
        if (typeof v === "number" || typeof v === "boolean") keys.add(k);
      });
    }
    return keys;
  }, [currentConfig]);

  const diffCount = useMemo(() => {
    if (!selected) return 0;
    let count = 0;
    numericKeys.forEach(k => {
      const a = (currentConfig as Record<string, unknown>)[k];
      const b = (selected.config as Record<string, unknown>)[k];
      if (a !== b) count++;
    });
    return count;
  }, [selected, currentConfig, numericKeys]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-fade-in"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="preset-sandbox-title"
    >
      <div
        className="bg-surface-container-lowest rounded-2xl shadow-2xl border border-outline-variant max-w-3xl w-full max-h-[85vh] flex flex-col animate-scale-in"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="px-6 py-4 border-b border-outline-variant flex items-center justify-between shrink-0">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-fixed">
              <span className="material-symbols-outlined text-[20px] text-primary">science</span>
            </div>
            <div>
              <h2 id="preset-sandbox-title" className="text-headline-md text-on-surface">Preset Sandbox</h2>
              <p className="text-body-sm text-on-surface-variant">So sánh và áp dụng preset mà không ảnh hưởng cấu hình hiện tại</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className="p-2 rounded-lg hover:bg-surface-container-low transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Preset grid */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {Object.entries(presets).map(([key, preset]) => {
              const isActive = selectedKey === key;
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => setSelectedKey(key)}
                  aria-pressed={isActive}
                  className={`flex flex-col items-center gap-2 p-4 rounded-xl border-2 text-center transition-all cursor-pointer ${
                    isActive
                      ? "border-primary bg-primary-fixed/50 shadow-sm"
                      : "border-outline-variant bg-surface-container-low hover:border-primary/40 hover:bg-surface-container-lowest"
                  }`}
                >
                  <span className={`material-symbols-outlined text-[28px] ${isActive ? "text-primary" : "text-on-surface-variant"}`} aria-hidden="true">
                    {preset.icon}
                  </span>
                  <p className={`text-label-md font-semibold ${isActive ? "text-primary" : "text-on-surface"}`}>{preset.label}</p>
                  <p className="text-[10px] text-on-surface-variant line-clamp-2 leading-snug">{preset.tagline}</p>
                </button>
              );
            })}
          </div>

          {/* Diff preview */}
          {selected && (
            <div className="bg-surface-container-low rounded-2xl border border-outline-variant p-5 animate-slide-up">
              <div className="flex items-center justify-between mb-4">
                <p className="text-label-md font-semibold text-on-surface flex items-center gap-2">
                  <span className="material-symbols-outlined text-[18px] text-primary">compare_arrows</span>
                  So sánh: Hiện tại → <span className="text-primary">{selected.label}</span>
                </p>
                <span className="px-2.5 py-1 rounded-full bg-tertiary-container/30 text-on-tertiary-container text-label-sm font-semibold border border-tertiary/20">
                  {diffCount} tham số thay đổi
                </span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-72 overflow-y-auto">
                {Array.from(numericKeys).map(k => {
                  const a = (currentConfig as Record<string, unknown>)[k];
                  const b = (selected.config as Record<string, unknown>)[k];
                  const changed = a !== b;
                  return (
                    <div
                      key={k}
                      className={`flex items-center justify-between px-3 py-2 rounded-lg text-label-sm ${
                        changed ? "bg-tertiary-container/10 border border-tertiary/20" : "bg-surface-container-lowest"
                      }`}
                    >
                      <span className="font-mono text-[12px] text-on-surface-variant truncate">{k}</span>
                      <div className="flex items-center gap-2 shrink-0">
                        <span className="text-on-surface tabular-nums">{formatVal(a)}</span>
                        <span className="material-symbols-outlined text-[14px] text-on-surface-variant">arrow_forward</span>
                        <span className={`tabular-nums font-semibold ${changed ? "text-primary" : "text-on-surface"}`}>{formatVal(b)}</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {!selected && (
            <div className="text-center py-8 text-on-surface-variant text-body-sm">
              Chọn một preset phía trên để xem so sánh.
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-outline-variant flex items-center justify-end gap-2 shrink-0">
          <Button variant="ghost" onClick={onClose}>Đóng</Button>
          <Button
            variant="primary"
            disabled={!selected}
            onClick={() => { if (selected) { onApply(selected); onClose(); } }}
          >
            Áp dụng & lưu
          </Button>
        </div>
      </div>
    </div>
  );
}

function formatVal(v: unknown): string {
  if (typeof v === "boolean") return v ? "Bật" : "Tắt";
  if (typeof v === "number") {
    if (v === 0) return "0";
    if (Math.abs(v) < 1) return formatPercent(v, 2);
    return v.toString();
  }
  return String(v ?? "—");
}