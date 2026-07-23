"use client";

import { useEffect, useState } from "react";
import { IconButton } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useToast } from "@/hooks/useToast";
import type { ConfigEntry } from "./types";

type ValuePreset = { label: string; value: string };

const VALUE_PRESETS: Record<string, ValuePreset[]> = {
  NUMBER: [
    { label: "0.5 — Khá thấp", value: "0.5" }, { label: "0.7 — Cân bằng", value: "0.7" },
    { label: "0.85 — Khá cao", value: "0.85" }, { label: "1.0 — Mặc định", value: "1" },
    { label: "2 — Gấp đôi", value: "2" }, { label: "3 — Gấp ba", value: "3" },
    { label: "10", value: "10" }, { label: "24 — 1 ngày", value: "24" }, { label: "60", value: "60" },
    { label: "100", value: "100" }, { label: "300", value: "300" }, { label: "500", value: "500" },
    { label: "1000", value: "1000" },
  ],
  BOOLEAN: [
    { label: "true — Bật", value: "true" }, { label: "false — Tắt", value: "false" },
  ],
  STRING: [
    { label: "SKIP — Bỏ qua ngày lễ", value: "SKIP" },
    { label: "PARTIAL — Vẫn xếp lịch nhưng giảm cường độ", value: "PARTIAL" },
    { label: "GREEDY — Chạy nhanh, phủ lịch nhanh (mặc định)", value: "GREEDY" },
    { label: "ROUND_ROBIN — Xếp lịch theo vòng tròn", value: "ROUND_ROBIN" },
    { label: "BALANCED — Cân bằng tải, tốc độ trung bình", value: "BALANCED" },
    { label: "MINIMAL_CHANGE — Giữ nguyên lịch hiện tại, thay đổi ít nhất", value: "MINIMAL_CHANGE" },
  ],
  JSON: [
    { label: "Mặc định {}", value: "{}" }, { label: "Mảng rỗng []", value: "[]" },
    { label: '{"enabled": true}', value: '{"enabled": true}' },
    { label: '{"strict": false}', value: '{"strict": false}' },
  ],
};

type Props = { config: ConfigEntry };

export function ConfigValueCell({ config }: Props) {
  const { error: toastError } = useToast();
  // CRITICAL (bug-config-persist): `config.paramValue` may come back as
  // undefined/null when the backend serialises an entry whose column is
  // empty. Using `?? ""` keeps `value` always a string so the controlled
  // <input> does not render the literal "undefined" into the DOM and
  // so save() never serialises `undefined` into the payload.
  const initialValue =
    typeof config.paramValue === "string" ? config.paramValue : (config.paramValue ?? "");
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState<string>(String(initialValue));
  const [saving, setSaving] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const presets = VALUE_PRESETS[config.valueType] ?? [];

  // Keep local state in sync when the parent row updates (e.g. after
  // page refresh or another component updated the same config).
  useEffect(() => {
    setValue(typeof config.paramValue === "string" ? config.paramValue : "");
  }, [config.paramValue]);

  useEffect(() => {
    if (editing && presets.length > 0) setShowDropdown(true);
  }, [editing, presets.length]);

  const handleSave = async () => {
    // Bug-config-persist: previous code stored `undefined` into the
    // payload whenever the row's paramValue was missing, which produced
    // `NaN` after backend parsing and made "Save" silently fail (toast
    // "Thành công" nhưng DB giữ nguyên giá trị cũ).
    const safeValue = typeof value === "string" ? value : "";
    const safeOriginal = typeof config.paramValue === "string" ? config.paramValue : "";
    if (safeValue === safeOriginal) { setEditing(false); return; }
    setSaving(true);
    try {
      await api.updateAlgorithmConfig(config.paramKey, { paramValue: safeValue, description: config.description });
      setEditing(false);
    } catch (err) {
      toastError(getErrorMessage(err, "Lưu thất bại"));
      setValue(safeOriginal);
    } finally { setSaving(false); }
  };

  if (editing) {
    return (
      <div className="flex items-center gap-1">
        <div className="relative">
          <input
            className="h-7 w-36 rounded-lg border border-primary bg-surface pl-2.5 pr-6 text-[11px] font-mono text-on-surface focus:outline-none focus:ring-1 focus:ring-primary/20"
            value={value}
            onChange={e => setValue(e.target.value)}
            onKeyDown={e => {
              if (e.key === "Enter") handleSave();
              if (e.key === "Escape") { setEditing(false); setValue(typeof config.paramValue === "string" ? config.paramValue : ""); }
            }}
            onBlur={() => setTimeout(() => setShowDropdown(false), 150)}
            autoFocus
          />
          {presets.length > 0 && showDropdown && (
            <div className="absolute z-50 mt-1 w-full bg-surface-container-lowest border border-outline-variant rounded-lg shadow-lg max-h-40 overflow-y-auto">
              {presets.map(p => (
                <div
                  key={p.value}
                  className="px-2.5 py-1.5 text-[11px] font-mono cursor-pointer hover:bg-surface-container-low active:scale-[0.98] transition-colors"
                  onMouseDown={(e) => { e.preventDefault(); setValue(p.value); setShowDropdown(false); }}
                >
                  {p.label}
                </div>
              ))}
            </div>
          )}
          <span className="material-symbols-outlined absolute right-1.5 top-1/2 -translate-y-1/2 text-[12px] text-outline pointer-events-none" aria-hidden="true">expand_more</span>
        </div>
        <IconButton label="Lưu" variant="primary" size="sm" disabled={saving} loading={saving} onClick={handleSave} className="text-white">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">check</span>
        </IconButton>
        <IconButton label="Hủy" variant="ghost" size="sm" onClick={() => { setEditing(false); setValue(typeof config.paramValue === "string" ? config.paramValue : ""); }} className="border border-outline-variant text-on-surface">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">close</span>
        </IconButton>
      </div>
    );
  }

  return (
    <button
      onClick={() => setEditing(true)}
      className="group/val flex items-center gap-1 cursor-pointer"
      title="Click để sửa"
      aria-label={`Sửa giá trị ${config.paramKey}`}
    >
      <span className="font-mono text-[12px] text-on-surface bg-surface-container-low px-2.5 py-0.5 rounded-lg border border-transparent group-hover/val:border-primary transition-colors max-w-[180px] truncate block tabular-nums">
        {config.paramValue}
      </span>
      <span className="material-symbols-outlined text-[12px] text-outline opacity-0 group-hover/val:opacity-100 transition-opacity" aria-hidden="true">edit</span>
    </button>
  );
}