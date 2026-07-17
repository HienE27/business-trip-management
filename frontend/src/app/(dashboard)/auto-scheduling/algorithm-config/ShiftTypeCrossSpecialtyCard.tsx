"use client";

import { useState, useEffect } from "react";
import type { BalanceStrategy } from "./types";

export type ShiftTypeId = "L01" | "L02" | "L03" | "L04";

type ShiftTypeCrossSpecialtyProps = {
  shiftType: ShiftTypeId;
  shiftTypeName: string;
  enabled: boolean;
  ratio: number;
  allowedSpecialties: string[];
  allSpecialties: string[];
  editing: boolean;
  balanceStrategy: BalanceStrategy;
  /**
   * Nếu false, card không hiển thị section cấu hình chuyên khoa.
   * Dùng cho L01/L02/L03 — theo nghiệp vụ, các loại ca này
   * không có ràng buộc chuyên khoa (tất cả 6 khoa đều eligible).
   */
  showSpecialtyConfig?: boolean;
  onChange: (enabled: boolean, ratio: number, allowedSpecialties: string[], balanceStrategy: BalanceStrategy) => void;
};

const SHIFT_TYPE_CONFIG: Record<ShiftTypeId, {
  icon: string;
  color: string;
  colorBg: string;
  borderColor: string;
  description: string;
}> = {
  L01: {
    icon: "emergency",
    color: "text-red-600",
    colorBg: "bg-red-50",
    borderColor: "border-l-red-500",
    description: "Cho phép nhân sự khác chuyên khoa tham gia Trực 24/24",
  },
  L02: {
    icon: "schedule",
    color: "text-blue-600",
    colorBg: "bg-blue-50",
    borderColor: "border-l-blue-500",
    description: "Cho phép nhân sự khác chuyên khoa tham gia Thông tầm",
  },
  L03: {
    icon: "medical_services",
    color: "text-green-600",
    colorBg: "bg-green-50",
    borderColor: "border-l-green-500",
    description: "Cho phép nhân sự khác chuyên khoa tham gia PK Dịch vụ",
  },
  L04: {
    icon: "stethoscope",
    color: "text-purple-600",
    colorBg: "bg-purple-50",
    borderColor: "border-l-purple-500",
    description: "Cho phép nhân sự khác chuyên khoa tham gia PK Chuyên gia",
  },
};

const BALANCE_STRATEGY_OPTIONS: { value: BalanceStrategy; label: string; desc: string }[] = [
  { value: "STRICT_MATCH_ONLY", label: "Strict match", desc: "Chỉ chuyên khoa khớp" },
  { value: "FAIR_DISTRIBUTE", label: "Fair distribute", desc: "Round-robin đều" },
  { value: "WEIGHTED_FAIR", label: "Weighted fair", desc: "Ưu tiên ít ca + fairness" },
];

// RESERVED — not used in scheduler v1.0
const RESERVED_BALANCE_WARNING = "Balance strategy là reserved field. Thay đổi hiện không ảnh hưởng scheduler v1.0.";

export function ShiftTypeCrossSpecialtyCard({
  shiftType,
  shiftTypeName,
  enabled,
  ratio,
  allowedSpecialties,
  allSpecialties,
  editing,
  balanceStrategy,
  showSpecialtyConfig = true,
  onChange,
}: ShiftTypeCrossSpecialtyProps) {
  const [localRatio, setLocalRatio] = useState(ratio);
  const [localAllowed, setLocalAllowed] = useState<string[]>(allowedSpecialties);
  const [localStrategy, setLocalStrategy] = useState<BalanceStrategy>(balanceStrategy);
  const [selectionMode, setSelectionMode] = useState<"all" | "partial" | "none">(() => {
    if (allowedSpecialties.length === 0 || allowedSpecialties.length === allSpecialties.length) {
      return "all";
    } else if (allowedSpecialties.length > 0) {
      return "partial";
    }
    return "none";
  });

  const config = SHIFT_TYPE_CONFIG[shiftType];

  useEffect(() => {
    setLocalAllowed(allowedSpecialties);
    if (allowedSpecialties.length === 0 || allowedSpecialties.length === allSpecialties.length) {
      setSelectionMode("all");
    } else if (allowedSpecialties.length > 0) {
      setSelectionMode("partial");
    } else {
      setSelectionMode("none");
    }
  }, [allowedSpecialties, allSpecialties]);

  useEffect(() => {
    setLocalStrategy(balanceStrategy);
  }, [balanceStrategy]);

  function isSpecialtySelected(specialty: string): boolean {
    if (selectionMode === "all") return true;
    if (selectionMode === "none") return false;
    return localAllowed.includes(specialty);
  }

  function handleToggle() {
    const newEnabled = !enabled;
    onChange(newEnabled, localRatio, localAllowed, localStrategy);
  }

  function handleRatioChange(value: number) {
    setLocalRatio(value);
    onChange(enabled, value, localAllowed, localStrategy);
  }

  function handleSpecialtyToggle(specialty: string) {
    let newAllowed: string[];
    if (selectionMode === "all") {
      newAllowed = allSpecialties.filter(s => s !== specialty);
      setSelectionMode("partial");
    } else if (selectionMode === "none") {
      newAllowed = [specialty];
      setSelectionMode("partial");
    } else {
      if (localAllowed.includes(specialty)) {
        newAllowed = localAllowed.filter(s => s !== specialty);
        if (newAllowed.length === 0) setSelectionMode("none");
      } else {
        newAllowed = [...localAllowed, specialty];
      }
    }
    setLocalAllowed(newAllowed);
    onChange(enabled, localRatio, newAllowed, localStrategy);
  }

  function handleSelectAll() {
    setLocalAllowed([]);
    setSelectionMode("all");
    onChange(enabled, localRatio, [], localStrategy);
  }

  function handleClearAll() {
    const noneMarker = ["__NONE__"];
    setLocalAllowed(noneMarker);
    setSelectionMode("none");
    onChange(enabled, localRatio, noneMarker, localStrategy);
  }

  function handleStrategyChange(value: BalanceStrategy) {
    setLocalStrategy(value);
    onChange(enabled, localRatio, localAllowed, value);
  }

  return (
    <div className={`bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 ${config.borderColor}`}>
      <div className="px-5 py-4 bg-surface-container-low flex items-center gap-3">
        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${config.colorBg} ${config.color}`}>
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{config.icon}</span>
        </div>
        <div className="flex-1">
          <p className="text-label-md font-semibold text-on-surface tracking-tight">
            {shiftTypeName} - Cross-specialty
          </p>
          <p className="text-[11px] text-on-surface-variant">{config.description}</p>
        </div>
      </div>
      <div className="p-5 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-label-sm text-on-surface font-medium">Bật cross-specialty</p>
            <p className="text-[11px] text-on-surface-variant mt-0.5">Cho phép nhân sự khác chuyên khoa</p>
          </div>
          {editing ? (
            <button
              type="button"
              role="switch"
              aria-checked={enabled}
              onClick={handleToggle}
              className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${
                enabled ? `bg-primary border-primary` : "bg-surface-container-high border-outline"
              }`}
            >
              <span className={`inline-block h-5 w-5 transform rounded-full bg-white shadow-sm transition-transform ${enabled ? "translate-x-6" : "translate-x-1"}`} />
            </button>
          ) : (
            <span className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-label-sm font-semibold ${enabled ? `${config.colorBg} ${config.color} border border-outline-variant` : "bg-surface-container-high text-outline"}`}>
              <span className={`h-2 w-2 rounded-full ${enabled ? config.color.replace("text-", "bg-") : "bg-outline"}`} />
              {enabled ? "Bật" : "Tắt"}
            </span>
          )}
        </div>

        {enabled && (
          <div className="pt-2 border-t border-outline-variant space-y-3">
            {/* Specialty config: chỉ hiện cho L04 */}
            {showSpecialtyConfig && (
              <>
                {/* Quick actions */}
                <div className="flex items-center justify-between">
                  <p className="text-label-sm text-on-surface font-medium">
                    {selectionMode === "all" ? "Tất cả chuyên khoa" : selectionMode === "none" ? "Không có chuyên khoa nào" : `${localAllowed.length}/${allSpecialties.length} chuyên khoa`}
                  </p>
                  {editing && (
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={handleSelectAll}
                        className="text-[11px] text-primary hover:underline"
                      >
                        Chọn tất cả
                      </button>
                      <span className="text-outline">|</span>
                      <button
                        type="button"
                        onClick={handleClearAll}
                        className={`text-[11px] ${config.color.replace("text-", "hover:text-")} hover:underline`}
                      >
                        Bỏ chọn tất cả
                      </button>
                    </div>
                  )}
                </div>

                {/* Specialty chips */}
                {editing ? (
                  <div className="flex flex-wrap gap-2">
                    {allSpecialties.map((specialty) => {
                      const isSelected = isSpecialtySelected(specialty);
                      return (
                        <button
                          key={specialty}
                          type="button"
                          onClick={() => handleSpecialtyToggle(specialty)}
                          className={`px-3 py-1.5 rounded-lg text-label-sm font-medium transition-all ${
                            isSelected
                              ? `${config.colorBg} ${config.color} border ${config.color.replace("text-", "border-")}`
                              : "bg-surface-container text-on-surface-variant border border-outline hover:border-outline hover:text-on-surface"
                          }`}
                        >
                          {specialty}
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {selectionMode === "all" ? (
                      <span className="text-[12px] text-on-surface-variant">
                        Tất cả chuyên khoa được phép
                      </span>
                    ) : selectionMode === "none" ? (
                      <span className="text-[12px] text-error">
                        Không có chuyên khoa nào được phép
                      </span>
                    ) : (
                      localAllowed.map((specialty) => (
                        <span
                          key={specialty}
                          className={`px-2.5 py-1 rounded-lg text-[12px] font-medium ${config.colorBg} ${config.color}`}
                        >
                          {specialty}
                        </span>
                      ))
                    )}
                  </div>
                )}
              </>
            )}

            {!showSpecialtyConfig && (
              <p className="text-[11px] text-on-surface-variant italic">
                Theo nghiệp vụ, tất cả chuyên khoa đều eligible cho loại ca này.
              </p>
            )}

            {/* Cross-specialty ratio */}
            <div className="flex items-center justify-between mt-3 pt-3 border-t border-outline-variant/50">
              <div>
                <p className="text-label-sm text-on-surface font-medium">Tối đa {Math.round(ratio * 100)}% nhân sự ngoài chuyên khoa</p>
              </div>
              <span className={`font-mono text-lg font-bold ${config.color} tabular-nums`}>
                {Math.round(ratio * 100)}%
              </span>
            </div>
            {editing && (
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                value={Math.round(localRatio * 100)}
                onChange={(e) => handleRatioChange(parseInt(e.target.value) / 100)}
                className={`w-full h-2 bg-surface-variant rounded-full appearance-none cursor-pointer accent-current ${config.color}`}
              />
            )}

            {/* Balance strategy — Reserved for future implementation */}
            <div className="flex items-center justify-between mt-3 pt-3 border-t border-outline-variant/50">
              <div>
                <p className="text-label-sm text-on-surface font-medium">
                  Balance strategy
                  <span className="ml-2 px-1.5 py-0.5 rounded text-[10px] bg-surface-container text-outline uppercase tracking-wide">Chưa dùng</span>
                </p>
                <p className="text-[11px] text-on-surface-variant mt-0.5">
                  Phân bổ staff ngoài chuyên khoa thế nào — chưa dùng trong scheduler v1.0
                </p>
              </div>
            </div>
            {editing ? (
              <>
              <div className="flex flex-wrap gap-2">
                {BALANCE_STRATEGY_OPTIONS.map((opt) => {
                  const active = localStrategy === opt.value;
                  return (
                    <button
                      key={opt.value}
                      type="button"
                      onClick={() => handleStrategyChange(opt.value)}
                      className={`flex-1 min-w-[120px] px-3 py-2 rounded-lg text-left transition-colors border ${
                        active
                          ? `${config.colorBg} border-${opt.value} ${config.color}`
                          : "bg-surface-container-low border-outline-variant hover:bg-surface-container"
                      }`}
                    >
                      <p className={`text-label-sm font-semibold ${active ? config.color : "text-on-surface"}`}>
                        {opt.label}
                      </p>
                      <p className={`text-[10px] mt-0.5 ${active ? config.color.replace("text-", "text-opacity-70 ") : "text-on-surface-variant"}`}>
                        {opt.desc}
                      </p>
                    </button>
                  );
                })}
              </div>
              <p className="text-[11px] text-tertiary-600 mt-2 flex items-start gap-1">
                <span className="material-symbols-outlined text-[12px] shrink-0 mt-0.5">info</span>
                {RESERVED_BALANCE_WARNING}
              </p>
              </>
            ) : (
              <>
              <div className="flex items-center gap-2">
                <span className={`px-2.5 py-1 rounded-md text-label-sm font-medium ${config.colorBg} ${config.color}`}>
                  {BALANCE_STRATEGY_OPTIONS.find(o => o.value === localStrategy)?.label ?? localStrategy}
                </span>
                <span className="text-[11px] text-on-surface-variant">
                  {BALANCE_STRATEGY_OPTIONS.find(o => o.value === localStrategy)?.desc ?? ""}
                </span>
              </div>
              <p className="text-[11px] text-tertiary-600 mt-2 flex items-start gap-1">
                <span className="material-symbols-outlined text-[12px] shrink-0 mt-0.5">info</span>
                {RESERVED_BALANCE_WARNING}
              </p>
              </>
            )}

            {!enabled && (
              <div className="flex items-start gap-2 p-3 rounded-lg bg-surface-container-low">
                <span className={`material-symbols-outlined text-[16px] ${config.color} shrink-0 mt-0.5`} aria-hidden="true">info</span>
                <p className="text-[12px] text-on-surface-variant leading-relaxed">
                  Chỉ nhân sự thuộc chuyên khoa được chọn mới tham gia {shiftTypeName}.
                  Bật cross-specialty để mở rộng pool nhân sự.
                </p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
