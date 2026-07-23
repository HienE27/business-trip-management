"use client";

import { useEffect, useState } from "react";
import type { RuntimeConfig } from "./types";
import type { ShiftTypeGroup } from "./paramConfig";
import { getShiftRowLabel, getShiftRowIcon, getShiftRowTooltip, getShiftRowUnit } from "./paramConfig";
import { getParamValidation, type ValidationResult } from "@/lib/validation/algorithmConfig";

type Props = {
  group: ShiftTypeGroup;
  form: RuntimeConfig;
  editing: boolean;
  onChange: (key: string, value: number) => void;
};

// Compact number spinner for shift type cards
function CompactSpinner({ value, onChange, disabled }: {
  value: number;
  onChange: (v: number) => void;
  disabled?: boolean;
}) {
  // Local state để cho phép empty input
  const [localVal, setLocalVal] = useState(value.toString());
  const [isFocused, setIsFocused] = useState(false);

  // Sync khi value thay đổi từ bên ngoài
  useEffect(() => {
    if (!isFocused) {
      setLocalVal(value.toString());
    }
  }, [value, isFocused]);

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const raw = e.target.value;
    setLocalVal(raw);
    // Mirror the fix from NumberSpinner in RuntimeConfigEditor: commit every
    // valid keystroke to the parent immediately so that pressing "Lưu thay
    // đổi" right after typing (without blurring) actually persists the value.
    // Otherwise the parent form stays stale and the save payload silently
    // reverts to the previous value.
    if (raw === "") return; // allow empty while editing; commit on blur
    const parsed = Math.max(0, Math.min(99, parseInt(raw, 10) || 0));
    if (parsed === value) return; // no-op when nothing changed
    onChange(parsed);
  }

  function handleBlur() {
    setIsFocused(false);
    const raw = localVal.trim();
    if (raw === "") {
      setLocalVal("0");
      onChange(0);
    } else {
      const parsed = Math.max(0, Math.min(99, parseInt(raw) || 0));
      setLocalVal(parsed.toString());
      onChange(parsed);
    }
  }

  function handleFocus() {
    setIsFocused(true);
  }

  return (
    <div className={`flex items-center gap-0.5 ${disabled ? "opacity-50" : ""}`}>
      <button
        type="button"
        onClick={() => {
          const newVal = Math.max(0, value - 1);
          onChange(newVal);
          setLocalVal(newVal.toString());
        }}
        disabled={disabled || value <= 0}
        className="flex items-center justify-center h-6 w-5 rounded border border-outline-variant bg-surface-container-low hover:bg-surface-container text-on-surface transition-colors disabled:opacity-30 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary"
        title="Giảm"
      >
        <span className="material-symbols-outlined text-[12px]" aria-hidden="true">remove</span>
      </button>
      <input
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        disabled={disabled}
        className="h-6 w-10 rounded border border-outline-variant bg-surface-container-lowest px-1 text-center text-[11px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-colors disabled:cursor-not-allowed"
        value={localVal}
        onChange={handleInputChange}
        onFocus={handleFocus}
        onBlur={handleBlur}
        placeholder="—"
      />
      <button
        type="button"
        onClick={() => {
          const newVal = Math.min(99, value + 1);
          onChange(newVal);
          setLocalVal(newVal.toString());
        }}
        disabled={disabled || value >= 99}
        className="flex items-center justify-center h-6 w-5 rounded border border-outline-variant bg-surface-container-low hover:bg-surface-container text-on-surface transition-colors disabled:opacity-30 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary"
        title="Tăng"
      >
        <span className="material-symbols-outlined text-[12px]" aria-hidden="true">add</span>
      </button>
    </div>
  );
}

export function ShiftTypeGroupCard({ group, form, editing, onChange }: Props) {
  return (
    <div
      className={`bg-surface-container-lowest rounded-xl border ${group.borderColor} overflow-hidden flex flex-col w-[210px] shrink-0 group/card shadow-sm hover:shadow-md transition-shadow`}
      style={{ minHeight: 200 }}
    >
      <div className={`px-3 py-2 border-b ${group.borderColor}/30 bg-surface-container-low flex items-start gap-2 shrink-0`}>
        <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${group.colorBg} ${group.color}`}>
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{group.icon}</span>
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-label-sm font-bold text-on-surface leading-tight">{group.label}</p>
          <p className="text-[10px] text-on-surface-variant leading-tight">{group.subtitle}</p>
        </div>
      </div>

      <div className="px-3 py-1.5 bg-surface-container-low/50 border-b border-outline-variant/30">
        <p className="text-[9px] text-on-surface-variant leading-tight">{group.description}</p>
      </div>

      <div className="flex flex-col divide-y divide-outline-variant/40 flex-1">
        {group.params.map((param) => {
          const numVal = typeof form[param] === "number" ? (form[param] as number) : 0;
          const isMaxParam = param.includes("Max");
          const display = numVal === 0
            ? (isMaxParam ? "Không giới hạn" : "Tắt")
            : numVal.toString();
          const label = getShiftRowLabel(param);
          const iconName = getShiftRowIcon(param);
          const tooltip = getShiftRowTooltip(param);
          const unit = getShiftRowUnit(param);
          const validation = getParamValidation(param, numVal);
          const crossValidation = getCrossFieldValidation(param, numVal, form);
          const effectiveValidation: ValidationResult | null =
            crossValidation ?? validation;
          const isMinPerDay = param.endsWith("MinPerDay");
          return (
            <div
              key={param}
              className="flex flex-col px-3 py-2 hover:bg-surface-container-low/50 transition-colors group/row"
              title={tooltip}
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex flex-col min-w-0 leading-tight">
                  <span className={`flex items-center gap-1 font-mono text-[10px] font-semibold px-1 py-0.5 rounded w-fit ${
                    param.endsWith("MinPerDay")
                      ? "bg-secondary-container/70 text-on-secondary-container"
                      : param.endsWith("MaxPerDay")
                      ? "bg-amber-100 text-amber-800"
                      : "bg-error-container/70 text-on-error-container"
                  }`}>
                    <span className="material-symbols-outlined text-[10px] shrink-0" aria-hidden="true">{iconName}</span>
                    {label}
                  </span>
                  {unit && (
                    <span className="text-[9px] text-on-surface-variant mt-0.5">{unit}</span>
                  )}
                  {isMinPerDay && (
                    <span className="text-[9px] text-on-surface-variant/70 italic mt-0.5">Sinh yêu cầu mỗi ngày nếu nhân lực cho phép</span>
                  )}
                </div>
                <div className="flex items-center shrink-0">
                  {editing ? (
                    <CompactSpinner
                      value={numVal}
                      onChange={(v) => onChange(param, v)}
                    />
                  ) : (
                    <span className="font-mono text-xs font-bold text-on-surface w-10 text-right shrink-0 tabular-nums">{display}</span>
                  )}
                </div>
              </div>
              {effectiveValidation && (
                <div
                  className={`mt-1 flex items-start gap-1 px-1.5 py-0.5 rounded text-[9px] leading-tight ${
                    effectiveValidation.level === "error"
                      ? "bg-error-container/40 text-error"
                      : "bg-tertiary-container/40 text-tertiary"
                  }`}
                  role={effectiveValidation.level === "error" ? "alert" : "status"}
                >
                  <span className="material-symbols-outlined text-[10px] shrink-0 mt-0.5" aria-hidden="true">
                    {effectiveValidation.level === "error" ? "error" : "warning"}
                  </span>
                  <span className="line-clamp-2">{effectiveValidation.message}</span>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function getCrossFieldValidation(
  param: string,
  value: number,
  form: RuntimeConfig,
): ValidationResult | null {
  const isMin = param.endsWith("MinPerDay") || param.endsWith("MinPerWeek");
  const isMax = param.endsWith("MaxPerDay") || param.endsWith("MaxPerWeek");
  if (!isMin && !isMax) return null;
  const scope = param.endsWith("Day") ? "ngày" : "tuần";
  const counterpartKey = isMin
    ? param.replace("MinPer", "MaxPer")
    : param.replace("MaxPer", "MinPer");
  const counterpartVal = form[counterpartKey as keyof RuntimeConfig];
  if (typeof counterpartVal !== "number") return null;
  if (isMin && value > counterpartVal && counterpartVal > 0) {
    return {
      level: "error",
      message: `Min/${scope} (${value}) > Max/${scope} (${counterpartVal}) — không khả thi.`,
    };
  }
  if (isMax && value > 0 && value < counterpartVal) {
    return {
      level: "error",
      message: `Max/${scope} (${value}) < Min/${scope} (${counterpartVal}) — không khả thi.`,
    };
  }
  return null;
}