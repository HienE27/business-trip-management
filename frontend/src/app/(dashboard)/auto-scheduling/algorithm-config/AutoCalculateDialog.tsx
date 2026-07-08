"use client";

import { useState, useMemo, useEffect, useRef, useCallback } from "react";
import { Button, FormInput } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";

type ShiftTypeId = "L01" | "L02" | "L03" | "L04";

const SHIFT_META: Record<ShiftTypeId, { label: string; subtitle: string; color: string; bgColor: string; borderColor: string; chartColor: string }> = {
  L01: { label: "L01", subtitle: "Trực 24/24", color: "text-red-600", bgColor: "bg-red-50", borderColor: "border-red-500", chartColor: "#ef4444" },
  L02: { label: "L02", subtitle: "Thông tầm", color: "text-blue-600", bgColor: "bg-blue-50", borderColor: "border-blue-500", chartColor: "#3b82f6" },
  L03: { label: "L03", subtitle: "PK Dịch vụ", color: "text-green-600", bgColor: "bg-green-50", borderColor: "border-green-500", chartColor: "#22c55e" },
  L04: { label: "L04", subtitle: "PK Chuyên gia", color: "text-purple-600", bgColor: "bg-purple-50", borderColor: "border-purple-500", chartColor: "#a855f7" },
};

export type AutoCalculateInput = {
  periodDays: number;
  periodWeeks: number;
  targetsPerStaffPerMonth: Record<ShiftTypeId, number>;
  eligibleStaff: Record<ShiftTypeId, number>;
};

export type AutoCalculateResult = {
  l01MinPerDay: number; l01MaxPerDay: number; l01MinPerWeek: number; l01MaxPerWeek: number;
  l02MinPerDay: number; l02MaxPerDay: number; l02MinPerWeek: number; l02MaxPerWeek: number;
  l03MinPerDay: number; l03MaxPerDay: number; l03MinPerWeek: number; l03MaxPerWeek: number;
  l04MinPerDay: number; l04MaxPerDay: number; l04MinPerWeek: number; l04MaxPerWeek: number;
};

type Props = {
  open: boolean;
  onClose: () => void;
  onApply: (result: AutoCalculateResult) => void;
  onSavePreset?: (name: string, config: AutoCalculateInput) => void;
  initialValues?: Partial<AutoCalculateInput>;
  currentConfig?: AutoCalculateResult | null;
  savedPresets?: { id: string; name: string; config: AutoCalculateInput }[];
};

/* ─── Quick Presets ────────────────────────────────────────────────── */

type QuickPreset = {
  id: string;
  label: string;
  icon: string;
  description: string;
  periodDays: number;
  periodWeeks: number;
  targets: Record<ShiftTypeId, number>;
  eligible: Record<ShiftTypeId, number>;
};

const QUICK_PRESETS: QuickPreset[] = [
  {
    id: "standard",
    label: "Tháng chuẩn",
    icon: "calendar_view_month",
    description: "30 ngày / 4 tuần - phân bổ đều",
    periodDays: 30,
    periodWeeks: 4,
    targets: { L01: 7, L02: 8, L03: 9, L04: 16 },
    eligible: { L01: 8, L02: 8, L03: 8, L04: 20 },
  },
  {
    id: "short",
    label: "Tháng ngắn",
    icon: "event",
    description: "28 ngày / 4 tuần - T2 đầu tháng",
    periodDays: 28,
    periodWeeks: 4,
    targets: { L01: 7, L02: 7, L03: 8, L04: 14 },
    eligible: { L01: 8, L02: 8, L03: 8, L04: 20 },
  },
  {
    id: "long",
    label: "Tháng dài",
    icon: "date_range",
    description: "31 ngày / 5 tuần - có T7 dài",
    periodDays: 31,
    periodWeeks: 5,
    targets: { L01: 8, L02: 9, L03: 10, L04: 18 },
    eligible: { L01: 8, L02: 8, L03: 8, L04: 20 },
  },
  {
    id: "special",
    label: "Kỳ đặc biệt",
    icon: "stars",
    description: "14 ngày / 2 tuần - cao cấp hơn",
    periodDays: 14,
    periodWeeks: 2,
    targets: { L01: 3, L02: 4, L03: 4, L04: 8 },
    eligible: { L01: 8, L02: 8, L03: 8, L04: 20 },
  },
];

/* ─── Validation ──────────────────────────────────────────────────── */

type ValidationWarning = {
  type: "error" | "warning" | "info";
  key: string;
  message: string;
};

function validateInput(input: AutoCalculateInput): ValidationWarning[] {
  const warnings: ValidationWarning[] = [];

  for (const tid of ["L01", "L02", "L03", "L04"] as ShiftTypeId[]) {
    const target = input.targetsPerStaffPerMonth[tid] ?? 0;
    const eligible = input.eligibleStaff[tid] ?? 0;
    const weeks = input.periodWeeks;

    if (target < 1) {
      warnings.push({ type: "error", key: `${tid}_target_low`, message: `${tid} - Target/người quá thấp (< 1)` });
    }
    if (target > 25) {
      warnings.push({ type: "warning", key: `${tid}_target_high`, message: `${tid} - Target/người cao (> 25) có thể quá tải` });
    }
    if (eligible < 2) {
      warnings.push({ type: "error", key: `${tid}_eligible_low`, message: `${tid} - Cần tối thiểu 2 người đủ điều kiện` });
    }
    const avgPerWeek = target / weeks;
    if (avgPerWeek > 6) {
      warnings.push({ type: "warning", key: `${tid}_workload_high`, message: `${tid} - ${avgPerWeek.toFixed(1)} ca/tuần/người có thể quá tải` });
    }
    const ratio = eligible / (target || 1);
    if (ratio < 0.5) {
      warnings.push({ type: "warning", key: `${tid}_balance`, message: `${tid} - Tỷ lệ eligible/target thấp, cần mở rộng eligibility` });
    }
  }

  if (input.periodDays < 7) {
    warnings.push({ type: "error", key: "period_too_short", message: "Kỳ lịch quá ngắn (< 7 ngày)" });
  }
  if (input.periodDays > 31) {
    warnings.push({ type: "warning", key: "period_too_long", message: "Kỳ lịch dài (> 31 ngày) - cần đánh giá kỹ" });
  }

  return warnings;
}

function computeConfig(input: AutoCalculateInput): AutoCalculateResult {
  const out: Record<string, number> = {};
  for (const tid of ["L01", "L02", "L03", "L04"] as ShiftTypeId[]) {
    const targetPerStaff = input.targetsPerStaffPerMonth[tid] ?? 0;
    const eligible = Math.max(1, input.eligibleStaff[tid]);
    const days = Math.max(1, input.periodDays);
    const weeks = Math.max(1, input.periodWeeks);

    const minPerDay = Math.max(1, Math.floor((targetPerStaff * eligible) / days));
    const minPerWeek = Math.max(1, Math.round(targetPerStaff / weeks));
    const maxPerWeek = Math.max(minPerWeek + 1, Math.round((targetPerStaff / weeks) * 1.5));
    const maxPerDay = Math.max(minPerDay, Math.ceil(maxPerWeek * 1.2));

    out[`${tid.toLowerCase()}MinPerDay`] = minPerDay;
    out[`${tid.toLowerCase()}MaxPerDay`] = maxPerDay;
    out[`${tid.toLowerCase()}MinPerWeek`] = minPerWeek;
    out[`${tid.toLowerCase()}MaxPerWeek`] = maxPerWeek;
  }
  return out as unknown as AutoCalculateResult;
}

/* ─── Undo/Redo State ────────────────────────────────────────────── */

type DialogState = {
  periodDays: number;
  periodWeeks: number;
  targets: Record<ShiftTypeId, number>;
  eligible: Record<ShiftTypeId, number>;
  expandEligibility: boolean;
};

function stateToInput(state: DialogState): AutoCalculateInput {
  return {
    periodDays: state.periodDays,
    periodWeeks: state.periodWeeks,
    targetsPerStaffPerMonth: state.targets,
    eligibleStaff: state.eligible,
  };
}

/* ─── Diff Helper ────────────────────────────────────────────────── */

type DiffValue = {
  current: number;
  new: number;
  diff: "increase" | "decrease" | "same";
};

function getDiff(current: number, next: number): DiffValue {
  if (next > current) return { current, new: next, diff: "increase" };
  if (next < current) return { current, new: next, diff: "decrease" };
  return { current, new: next, diff: "same" };
}

/* ─── Tooltip Component ──────────────────────────────────────────── */

function Tooltip({ content, children }: { content: string; children: React.ReactNode }) {
  const [show, setShow] = useState(false);

  return (
    <div className="relative inline-block">
      <div
        onMouseEnter={() => setShow(true)}
        onMouseLeave={() => setShow(false)}
      >
        {children}
      </div>
      {show && (
        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-2 bg-gray-900 text-white text-[11px] rounded-lg shadow-lg z-50 whitespace-nowrap max-w-xs">
          {content}
          <div className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-gray-900" />
        </div>
      )}
    </div>
  );
}

/* ─── Chart Component ────────────────────────────────────────────── */

function DistributionChart({
  targets,
  eligible,
}: {
  targets: Record<ShiftTypeId, number>;
  eligible: Record<ShiftTypeId, number>;
}) {
  const shiftIds: ShiftTypeId[] = ["L01", "L02", "L03", "L04"];
  const totalShifts = shiftIds.reduce((sum, tid) => sum + targets[tid] * eligible[tid], 0);

  return (
    <div className="mt-4 p-4 bg-surface-container-lowest rounded-xl border border-outline-variant">
      <h4 className="text-label-sm font-semibold text-on-surface mb-3 flex items-center gap-2">
        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">bar_chart</span>
        Phân bổ ca dự kiến
      </h4>
      <div className="space-y-3">
        {shiftIds.map((tid) => {
          const total = targets[tid] * eligible[tid];
          const percentage = totalShifts > 0 ? (total / totalShifts) * 100 : 0;
          const meta = SHIFT_META[tid];
          return (
            <div key={tid} className="flex items-center gap-3">
              <div className="w-16 flex items-center gap-2">
                <span className={`font-mono font-bold text-[12px] ${meta.color}`}>{tid}</span>
              </div>
              <div className="flex-1 h-6 bg-surface-container rounded-full overflow-hidden relative">
                <div
                  className="h-full rounded-full transition-all duration-500 ease-out"
                  style={{
                    width: `${percentage}%`,
                    backgroundColor: meta.chartColor,
                    minWidth: percentage > 0 ? "8px" : "0",
                  }}
                />
                <span className="absolute right-2 top-1/2 -translate-y-1/2 text-[11px] font-medium text-on-surface">
                  {percentage.toFixed(0)}%
                </span>
              </div>
              <div className="w-16 text-right">
                <span className="font-mono text-[12px] font-semibold text-on-surface">{total} ca</span>
              </div>
            </div>
          );
        })}
      </div>
      <div className="mt-3 pt-3 border-t border-outline-variant flex justify-between text-[11px] text-on-surface-variant">
        <span>Tổng ca kỳ</span>
        <span className="font-mono font-semibold text-primary">{totalShifts} ca</span>
      </div>
    </div>
  );
}

/* ─── Diff View Component ─────────────────────────────────────────── */

function DiffView({
  computed,
  currentConfig,
}: {
  computed: AutoCalculateResult;
  currentConfig: AutoCalculateResult | null;
}) {
  if (!currentConfig) return null;

  const shiftIds: ShiftTypeId[] = ["L01", "L02", "L03", "L04"];

  return (
    <div className="mt-4 p-4 bg-surface-container-lowest rounded-xl border border-outline-variant">
      <h4 className="text-label-sm font-semibold text-on-surface mb-3 flex items-center gap-2">
        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">compare_arrows</span>
        So sánh với cấu hình hiện tại
      </h4>
      <div className="overflow-x-auto">
        <table className="w-full text-[11px]">
          <thead>
            <tr className="border-b border-outline-variant">
              <th className="text-left py-2 px-2 font-medium text-on-surface-variant">Loại</th>
              <th className="text-center py-2 px-2 font-medium text-on-surface-variant">Min/ngày</th>
              <th className="text-center py-2 px-2 font-medium text-on-surface-variant">Max/ngày</th>
              <th className="text-center py-2 px-2 font-medium text-on-surface-variant">Min/tuần</th>
              <th className="text-center py-2 px-2 font-medium text-on-surface-variant">Max/tuần</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant">
            {shiftIds.map((tid) => {
              const meta = SHIFT_META[tid];
              const minDay = getDiff(
                currentConfig[`${tid.toLowerCase()}MinPerDay` as keyof AutoCalculateResult] as number,
                computed[`${tid.toLowerCase()}MinPerDay` as keyof AutoCalculateResult] as number
              );
              const maxDay = getDiff(
                currentConfig[`${tid.toLowerCase()}MaxPerDay` as keyof AutoCalculateResult] as number,
                computed[`${tid.toLowerCase()}MaxPerDay` as keyof AutoCalculateResult] as number
              );
              const minWeek = getDiff(
                currentConfig[`${tid.toLowerCase()}MinPerWeek` as keyof AutoCalculateResult] as number,
                computed[`${tid.toLowerCase()}MinPerWeek` as keyof AutoCalculateResult] as number
              );
              const maxWeek = getDiff(
                currentConfig[`${tid.toLowerCase()}MaxPerWeek` as keyof AutoCalculateResult] as number,
                computed[`${tid.toLowerCase()}MaxPerWeek` as keyof AutoCalculateResult] as number
              );
              const hasChange = [minDay, maxDay, minWeek, maxWeek].some(d => d.diff !== "same");

              return (
                <tr key={tid} className={hasChange ? "bg-primary-fixed/20" : ""}>
                  <td className="py-2 px-2">
                    <span className={`font-mono font-bold ${meta.color}`}>{tid}</span>
                  </td>
                  {[minDay, maxDay, minWeek, maxWeek].map((d, i) => (
                    <td key={i} className="py-2 px-2 text-center">
                      <div className="flex items-center justify-center gap-1">
                        <span className={`font-mono ${
                          d.diff === "increase" ? "text-secondary font-bold" :
                          d.diff === "decrease" ? "text-error font-bold" :
                          "text-on-surface-variant"
                        }`}>
                          {d.new}
                        </span>
                        {d.diff !== "same" && (
                          <span className={`text-[10px] ${
                            d.diff === "increase" ? "text-secondary" : "text-error"
                          }`}>
                            {d.diff === "increase" ? "↑" : "↓"}
                          </span>
                        )}
                      </div>
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="mt-3 flex items-center gap-4 text-[10px] text-on-surface-variant">
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-secondary"></span> Tăng
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-error"></span> Giảm
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-surface-container-high"></span> Không đổi
        </span>
      </div>
    </div>
  );
}

/* ─── Preset Comparison Component ────────────────────────────────── */

function PresetCompareModal({
  presets,
  currentConfig,
  onClose,
  onSelect,
}: {
  presets: { id: string; name: string; config: AutoCalculateInput }[];
  currentConfig: AutoCalculateResult | null;
  onClose: () => void;
  onSelect: (preset: { id: string; name: string; config: AutoCalculateInput }) => void;
}) {
  const [selected, setSelected] = useState<string | null>(null);

  if (presets.length === 0) {
    return (
      <div className="fixed inset-0 z-[110] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-fade-in">
        <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant shadow-lg w-full max-w-2xl max-h-[80vh] overflow-hidden p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-title-md font-semibold text-on-surface">So sánh Presets</h3>
            <button onClick={onClose} className="text-on-surface-variant hover:text-on-surface">
              <span className="material-symbols-outlined">close</span>
            </button>
          </div>
          <p className="text-on-surface-variant text-center py-8">Chưa có preset nào được lưu.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-[110] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-fade-in">
      <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant shadow-lg w-full max-w-3xl max-h-[80vh] overflow-hidden flex flex-col">
        <div className="px-6 py-4 border-b border-outline-variant flex items-center justify-between">
          <h3 className="text-title-md font-semibold text-on-surface">So sánh Presets</h3>
          <button onClick={onClose} className="text-on-surface-variant hover:text-on-surface">
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>
        <div className="p-6 overflow-y-auto flex-1">
          <div className="grid grid-cols-2 lg:grid-cols-3 gap-3 mb-6">
            {presets.map((preset) => (
              <button
                key={preset.id}
                onClick={() => setSelected(selected === preset.id ? null : preset.id)}
                className={`p-4 rounded-xl border-2 text-left transition-all ${
                  selected === preset.id
                    ? "border-primary bg-primary-fixed/50"
                    : "border-outline-variant hover:border-primary/40"
                }`}
              >
                <div className="flex items-center gap-2 mb-2">
                  <span className="material-symbols-outlined text-[18px] text-primary">bookmark</span>
                  <span className="font-semibold text-on-surface">{preset.name}</span>
                </div>
                <p className="text-[11px] text-on-surface-variant">
                  {preset.config.periodDays} ngày / {preset.config.periodWeeks} tuần
                </p>
              </button>
            ))}
          </div>

          {selected && (() => {
            const preset = presets.find(p => p.id === selected)!;
            const computed = computeConfig(preset.config);
            return (
              <div className="space-y-4">
                <h4 className="font-semibold text-on-surface">Cấu hình chi tiết: {preset.name}</h4>
                <div className="overflow-x-auto">
                  <table className="w-full text-[11px] border-collapse">
                    <thead>
                      <tr className="bg-surface-container-low border-b border-outline-variant">
                        <th className="py-2 px-3 text-left font-medium text-on-surface-variant">Loại</th>
                        <th className="py-2 px-3 text-center font-medium text-on-surface-variant">Đủ ĐK</th>
                        <th className="py-2 px-3 text-center font-medium text-on-surface-variant">Ca/kỳ</th>
                        <th className="py-2 px-3 text-center font-medium text-on-surface-variant">Min/ngày</th>
                        <th className="py-2 px-3 text-center font-medium text-on-surface-variant">Max/ngày</th>
                        <th className="py-2 px-3 text-center font-medium text-on-surface-variant">Min/tuần</th>
                        <th className="py-2 px-3 text-center font-medium text-on-surface-variant">Max/tuần</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-variant">
                      {(["L01", "L02", "L03", "L04"] as ShiftTypeId[]).map((tid) => {
                        const meta = SHIFT_META[tid];
                        return (
                          <tr key={tid}>
                            <td className="py-2 px-3">
                              <span className={`font-mono font-bold ${meta.color}`}>{tid}</span>
                            </td>
                            <td className="py-2 px-3 text-center font-mono">{preset.config.eligibleStaff[tid]}</td>
                            <td className="py-2 px-3 text-center font-mono">{preset.config.targetsPerStaffPerMonth[tid]}</td>
                            <td className="py-2 px-3 text-center font-mono">{computed[`${tid.toLowerCase()}MinPerDay` as keyof AutoCalculateResult]}</td>
                            <td className="py-2 px-3 text-center font-mono">{computed[`${tid.toLowerCase()}MaxPerDay` as keyof AutoCalculateResult]}</td>
                            <td className="py-2 px-3 text-center font-mono">{computed[`${tid.toLowerCase()}MinPerWeek` as keyof AutoCalculateResult]}</td>
                            <td className="py-2 px-3 text-center font-mono">{computed[`${tid.toLowerCase()}MaxPerWeek` as keyof AutoCalculateResult]}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => onSelect(preset)}
                  icon={<span className="material-symbols-outlined text-[16px]">check</span>}
                >
                  Áp dụng preset này
                </Button>
              </div>
            );
          })()}
        </div>
      </div>
    </div>
  );
}

/* ─── Export Modal ───────────────────────────────────────────────── */

function ExportModal({
  config,
  computed,
  onClose,
}: {
  config: AutoCalculateInput;
  computed: AutoCalculateResult;
  onClose: () => void;
}) {
  const exportData = {
    input: config,
    result: computed,
    exportedAt: new Date().toISOString(),
    version: "1.0",
  };

  const jsonString = JSON.stringify(exportData, null, 2);

  const handleCopy = () => {
    navigator.clipboard.writeText(jsonString);
  };

  const handleDownload = () => {
    const blob = new Blob([jsonString], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `auto-calc-config-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-[110] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-fade-in">
      <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant shadow-lg w-full max-w-2xl max-h-[80vh] overflow-hidden flex flex-col">
        <div className="px-6 py-4 border-b border-outline-variant flex items-center justify-between">
          <h3 className="text-title-md font-semibold text-on-surface">Xuất cấu hình</h3>
          <button onClick={onClose} className="text-on-surface-variant hover:text-on-surface">
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>
        <div className="p-6 overflow-y-auto flex-1">
          <div className="flex gap-3 mb-4">
            <Button variant="primary" size="sm" onClick={handleCopy} icon={<span className="material-symbols-outlined text-[16px]">content_copy</span>}>
              Copy JSON
            </Button>
            <Button variant="secondary" size="sm" onClick={handleDownload} icon={<span className="material-symbols-outlined text-[16px]">download</span>}>
              Tải về
            </Button>
          </div>
          <pre className="bg-surface-container-low p-4 rounded-lg text-[11px] font-mono overflow-x-auto max-h-96">
            {jsonString}
          </pre>
        </div>
      </div>
    </div>
  );
}

/* ─── Main Component ─────────────────────────────────────────────── */

export function AutoCalculateDialog({
  open,
  onClose,
  onApply,
  onSavePreset,
  initialValues,
  currentConfig,
  savedPresets = [],
}: Props) {
  const [periodDays, setPeriodDays] = useState(initialValues?.periodDays ?? 30);
  const [periodWeeks, setPeriodWeeks] = useState(initialValues?.periodWeeks ?? 4);
  const [targets, setTargets] = useState<Record<ShiftTypeId, number>>({
    L01: initialValues?.targetsPerStaffPerMonth?.L01 ?? 7,
    L02: initialValues?.targetsPerStaffPerMonth?.L02 ?? 8,
    L03: initialValues?.targetsPerStaffPerMonth?.L03 ?? 9,
    L04: initialValues?.targetsPerStaffPerMonth?.L04 ?? 16,
  });
  const [eligible, setEligible] = useState<Record<ShiftTypeId, number>>({
    L01: initialValues?.eligibleStaff?.L01 ?? 8,
    L02: initialValues?.eligibleStaff?.L02 ?? 8,
    L03: initialValues?.eligibleStaff?.L03 ?? 8,
    L04: initialValues?.eligibleStaff?.L04 ?? 20,
  });
  const [expandEligibility, setExpandEligibility] = useState(false);
  const [activePreset, setActivePreset] = useState<string | null>("standard");
  const [recommendation, setRecommendation] = useState<{
    config: AutoCalculateResult;
    totalShiftsExpected: number;
    rationale: string;
  } | null>(null);
  const [recommending, setRecommending] = useState(false);
  const [recommendError, setRecommendError] = useState<string | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [changedFields, setChangedFields] = useState<Set<string>>(new Set());
  const [showDiff, setShowDiff] = useState(true);
  const [showChart, setShowChart] = useState(true);
  const [showSaveModal, setShowSaveModal] = useState(false);
  const [showCompareModal, setShowCompareModal] = useState(false);
  const [showExportModal, setShowExportModal] = useState(false);
  const [savePresetName, setSavePresetName] = useState("");

  // Undo/Redo
  const [history, setHistory] = useState<DialogState[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const maxHistory = 20;

  const changedFieldsRef = useRef<Set<string>>(new Set());
  const dialogRef = useRef<HTMLDivElement>(null);

  const computed = useMemo(
    () =>
      computeConfig({
        periodDays,
        periodWeeks,
        targetsPerStaffPerMonth: targets,
        eligibleStaff: eligible,
      }),
    [periodDays, periodWeeks, targets, eligible]
  );

  const validation = useMemo(
    () =>
      validateInput({
        periodDays,
        periodWeeks,
        targetsPerStaffPerMonth: targets,
        eligibleStaff: eligible,
      }),
    [periodDays, periodWeeks, targets, eligible]
  );

  const errors = validation.filter((v) => v.type === "error");
  const warnings = validation.filter((v) => v.type === "warning");
  const infos = validation.filter((v) => v.type === "info");

  const totalTarget = (Object.values(targets) as number[]).reduce((s, v) => s + v, 0);
  const totalGenerated = (["L01", "L02", "L03", "L04"] as ShiftTypeId[]).reduce(
    (sum, tid) => sum + targets[tid] * eligible[tid],
    0
  );
  const totalEligible = (["L01", "L02", "L03", "L04"] as ShiftTypeId[]).reduce(
    (sum, tid) => sum + eligible[tid],
    0
  );

  // Undo/Redo functions
  const canUndo = historyIndex > 0;
  const canRedo = historyIndex < history.length - 1;

  const pushHistory = useCallback(() => {
    const newState: DialogState = {
      periodDays,
      periodWeeks,
      targets: { ...targets },
      eligible: { ...eligible },
      expandEligibility,
    };

    setHistory(prev => {
      const newHistory = prev.slice(0, historyIndex + 1);
      newHistory.push(newState);
      if (newHistory.length > maxHistory) {
        newHistory.shift();
        return newHistory;
      }
      return newHistory;
    });
    setHistoryIndex(prev => Math.min(prev + 1, maxHistory - 1));
  }, [periodDays, periodWeeks, targets, eligible, expandEligibility, historyIndex]);

  const undo = useCallback(() => {
    if (canUndo) {
      const prevState = history[historyIndex - 1];
      setPeriodDays(prevState.periodDays);
      setPeriodWeeks(prevState.periodWeeks);
      setTargets(prevState.targets);
      setEligible(prevState.eligible);
      setExpandEligibility(prevState.expandEligibility);
      setHistoryIndex(prev => prev - 1);
      setRecommendation(null);
      setChangedFields(new Set());
    }
  }, [canUndo, history, historyIndex]);

  const redo = useCallback(() => {
    if (canRedo) {
      const nextState = history[historyIndex + 1];
      setPeriodDays(nextState.periodDays);
      setPeriodWeeks(nextState.periodWeeks);
      setTargets(nextState.targets);
      setEligible(nextState.eligible);
      setExpandEligibility(nextState.expandEligibility);
      setHistoryIndex(prev => prev + 1);
      setRecommendation(null);
      setChangedFields(new Set());
    }
  }, [canRedo, history, historyIndex]);

  // Keyboard shortcuts
  useEffect(() => {
    if (!open) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      } else if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        if (errors.length === 0) {
          onApply(computed);
          onClose();
        }
      } else if (e.key === "z" && (e.ctrlKey || e.metaKey) && !e.shiftKey) {
        e.preventDefault();
        undo();
      } else if ((e.key === "z" && (e.ctrlKey || e.metaKey) && e.shiftKey) ||
                 (e.key === "y" && (e.ctrlKey || e.metaKey))) {
        e.preventDefault();
        redo();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, errors.length, computed, onClose, onApply, undo, redo]);

  // Initialize history
  useEffect(() => {
    if (open && history.length === 0) {
      pushHistory();
    }
  }, [open]);

  function applyQuickPreset(preset: QuickPreset) {
    pushHistory();
    setActivePreset(preset.id);
    setPeriodDays(preset.periodDays);
    setPeriodWeeks(preset.periodWeeks);
    setTargets({ ...preset.targets });
    setEligible({ ...preset.eligible });
    setRecommendation(null);
    setChangedFields(new Set(["period", "targets", "eligible"]));
    changedFieldsRef.current = new Set(["period", "targets", "eligible"]);
  }

  function applySavedPreset(preset: { id: string; name: string; config: AutoCalculateInput }) {
    pushHistory();
    setActivePreset(preset.id);
    setPeriodDays(preset.config.periodDays);
    setPeriodWeeks(preset.config.periodWeeks);
    setTargets(preset.config.targetsPerStaffPerMonth);
    setEligible(preset.config.eligibleStaff);
    setRecommendation(null);
    setChangedFields(new Set(["period", "targets", "eligible"]));
    changedFieldsRef.current = new Set(["period", "targets", "eligible"]);
    setShowCompareModal(false);
  }

  function handleFieldChange(field: string) {
    pushHistory();
    setActivePreset(null);
    setRecommendation(null);
    changedFieldsRef.current.add(field);
    setChangedFields(new Set(changedFieldsRef.current));
  }

  function handleSavePreset() {
    if (!savePresetName.trim()) return;
    onSavePreset?.(savePresetName.trim(), stateToInput({
      periodDays,
      periodWeeks,
      targets,
      eligible,
      expandEligibility,
    }));
    setShowSaveModal(false);
    setSavePresetName("");
  }

  async function fetchAIRecommendation() {
    setRecommending(true);
    setRecommendError(null);
    try {
      const resp = await api.recommendAutoGenConfig({
        periodDays,
        periodWeeks,
        totalStaff: 20,
        eligibleStaff: { L01: eligible.L01, L02: eligible.L02, L03: eligible.L03, L04: eligible.L04 },
        targetPerStaffPerMonth: { L01: targets.L01, L02: targets.L02, L03: targets.L03, L04: targets.L04 },
        expandNonL04Eligibility: expandEligibility,
      });
      const r = resp as unknown as {
        success: boolean;
        data: {
          recommendedConfig: AutoCalculateResult;
          totalShiftsExpected: number;
          rationale: string;
        };
      };
      setRecommendation({
        config: r.data.recommendedConfig,
        totalShiftsExpected: r.data.totalShiftsExpected,
        rationale: r.data.rationale,
      });
    } catch (err) {
      setRecommendError(getErrorMessage(err, "Không thể lấy đề xuất"));
    } finally {
      setRecommending(false);
    }
  }

  useEffect(() => {
    if (!open) {
      setRecommendation(null);
      setRecommendError(null);
      setChangedFields(new Set());
      changedFieldsRef.current = new Set();
      setHistory([]);
      setHistoryIndex(-1);
    }
  }, [open]);

  if (!open) return null;

  const shiftIds: ShiftTypeId[] = ["L01", "L02", "L03", "L04"];

  return (
    <>
      <div
        ref={dialogRef}
        className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-fade-in"
        onClick={onClose}
        role="dialog"
        aria-modal="true"
        aria-labelledby="auto-calc-title"
      >
        <div
          className="bg-surface-container-lowest rounded-2xl border border-outline-variant shadow-lg w-full max-w-4xl max-h-[90vh] overflow-y-auto animate-scale-in"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className="sticky top-0 bg-surface-container-lowest border-b border-outline-variant px-6 py-4 flex items-center justify-between z-10">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-fixed text-primary">
                <span className="material-symbols-outlined text-[20px]" aria-hidden="true">calculate</span>
              </div>
              <div>
                <h2 id="auto-calc-title" className="text-title-md font-semibold text-on-surface">
                  Tự động tính toán giới hạn
                </h2>
                <p className="text-[12px] text-on-surface-variant mt-0.5">
                  Quick preset hoặc tùy chỉnh thủ công
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {/* Undo/Redo */}
              <Tooltip content="Undo (Ctrl+Z)">
                <button
                  onClick={undo}
                  disabled={!canUndo}
                  className={`flex h-9 w-9 items-center justify-center rounded-full transition-colors ${
                    canUndo ? "text-on-surface-variant hover:bg-surface-container-high" : "text-outline cursor-not-allowed"
                  }`}
                >
                  <span className="material-symbols-outlined text-[20px]" aria-hidden="true">undo</span>
                </button>
              </Tooltip>
              <Tooltip content="Redo (Ctrl+Shift+Z)">
                <button
                  onClick={redo}
                  disabled={!canRedo}
                  className={`flex h-9 w-9 items-center justify-center rounded-full transition-colors ${
                    canRedo ? "text-on-surface-variant hover:bg-surface-container-high" : "text-outline cursor-not-allowed"
                  }`}
                >
                  <span className="material-symbols-outlined text-[20px]" aria-hidden="true">redo</span>
                </button>
              </Tooltip>
              <button
                onClick={onClose}
                className="flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container-high transition-colors"
                aria-label="Đóng"
              >
                <span className="material-symbols-outlined text-[20px]" aria-hidden="true">close</span>
              </button>
            </div>
          </div>

          <div className="p-6 space-y-6 overflow-y-auto max-h-[75vh]">
            {/* Quick Presets */}
            <section>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-label-md font-semibold text-on-surface flex items-center gap-2">
                  <span className="material-symbols-outlined text-[16px]" aria-hidden="true">bolt</span>
                  Quick Presets
                </h3>
                <div className="flex items-center gap-2">
                  {savedPresets.length > 0 && (
                    <Tooltip content="So sánh presets">
                      <button
                        onClick={() => setShowCompareModal(true)}
                        className="text-[11px] px-3 py-1.5 rounded-lg bg-surface-container-low text-on-surface-variant hover:bg-surface-container-high transition-colors flex items-center gap-1"
                      >
                        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">compare</span>
                        So sánh
                      </button>
                    </Tooltip>
                  )}
                  <Tooltip content="Xuất JSON">
                    <button
                      onClick={() => setShowExportModal(true)}
                      className="text-[11px] px-3 py-1.5 rounded-lg bg-surface-container-low text-on-surface-variant hover:bg-surface-container-high transition-colors flex items-center gap-1"
                    >
                      <span className="material-symbols-outlined text-[14px]" aria-hidden="true">upload</span>
                      Xuất
                    </button>
                  </Tooltip>
                </div>
              </div>
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                {QUICK_PRESETS.map((preset) => (
                  <button
                    key={preset.id}
                    type="button"
                    onClick={() => applyQuickPreset(preset)}
                    className={`p-4 rounded-xl border-2 text-left transition-all duration-200 hover:shadow-sm ${
                      activePreset === preset.id
                        ? "border-primary bg-primary-fixed/50"
                        : "border-outline-variant bg-surface-container-low hover:border-primary/40 hover:bg-surface-container-lowest"
                    }`}
                  >
                    <div className="flex items-center gap-2 mb-2">
                      <span className={`material-symbols-outlined text-[18px] ${
                        activePreset === preset.id ? "text-primary" : "text-on-surface-variant"
                      }`} aria-hidden="true">
                        {preset.icon}
                      </span>
                      <span className={`text-label-sm font-semibold ${
                        activePreset === preset.id ? "text-primary" : "text-on-surface"
                      }`}>
                        {preset.label}
                      </span>
                      {activePreset === preset.id && (
                        <span className="material-symbols-outlined text-primary text-[14px] ml-auto" aria-hidden="true">
                          check_circle
                        </span>
                      )}
                    </div>
                    <p className="text-[11px] text-on-surface-variant leading-relaxed">
                      {preset.description}
                    </p>
                  </button>
                ))}
              </div>

              {/* Saved Presets */}
              {savedPresets.length > 0 && (
                <div className="mt-3 pt-3 border-t border-outline-variant">
                  <h4 className="text-label-sm font-medium text-on-surface-variant mb-2">Preset đã lưu</h4>
                  <div className="flex flex-wrap gap-2">
                    {savedPresets.map((preset) => (
                      <button
                        key={preset.id}
                        onClick={() => applySavedPreset(preset)}
                        className={`px-3 py-2 rounded-lg border text-left transition-all ${
                          activePreset === preset.id
                            ? "border-primary bg-primary-fixed/50 text-primary"
                            : "border-outline-variant bg-surface-container-low hover:bg-surface-container-lowest text-on-surface"
                        }`}
                      >
                        <span className="material-symbols-outlined text-[14px] mr-1 align-middle" aria-hidden="true">bookmark</span>
                        {preset.name}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </section>

            {/* Period Info */}
            <section className="bg-surface-container-low rounded-xl p-4 border border-outline-variant">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-label-md font-semibold text-on-surface flex items-center gap-2">
                  <Tooltip content="Số ngày và tuần trong kỳ lịch cần xếp lịch">
                    <span className="material-symbols-outlined text-[16px]" aria-hidden="true">event</span>
                  </Tooltip>
                  Thông tin kỳ lịch
                  {changedFields.has("period") && (
                    <span className="w-2 h-2 rounded-full bg-primary animate-pulse" title="Đã thay đổi" />
                  )}
                </h3>
                <button
                  type="button"
                  onClick={() => setShowAdvanced(!showAdvanced)}
                  className="text-[11px] text-primary hover:underline flex items-center gap-1"
                >
                  {showAdvanced ? "Ẩn tùy chỉnh" : "Tùy chỉnh"}
                  <span className={`material-symbols-outlined text-[14px] transition-transform ${showAdvanced ? "rotate-180" : ""}`} aria-hidden="true">
                    expand_more
                  </span>
                </button>
              </div>
              <div className={`grid grid-cols-2 gap-3 ${showAdvanced ? "" : "hidden"}`}>
                <Tooltip content="Tổng số ngày trong kỳ lịch (tháng 9 = 30 ngày, tháng 2 = 28-29 ngày)">
                  <FormInput
                    label="Số ngày trong kỳ"
                    type="number"
                    min={1}
                    max={31}
                    value={periodDays}
                    onChange={(v) => { setPeriodDays(Math.max(1, parseInt(String(v)) || 1)); handleFieldChange("period"); }}
                    hint="VD: tháng 9 = 30 ngày"
                  />
                </Tooltip>
                <Tooltip content="Số tuần trong kỳ (thường là 4-5 tuần)">
                  <FormInput
                    label="Số tuần trong kỳ"
                    type="number"
                    min={1}
                    max={6}
                    value={periodWeeks}
                    onChange={(v) => { setPeriodWeeks(Math.max(1, parseInt(String(v)) || 1)); handleFieldChange("period"); }}
                    hint="Mặc định 4 tuần"
                  />
                </Tooltip>
              </div>
              {!showAdvanced && (
                <div className="flex items-center gap-4 text-[12px]">
                  <span className="flex items-center gap-1.5">
                    <span className={`font-mono font-semibold ${changedFields.has("period") ? "text-primary" : "text-on-surface"}`}>
                      {periodDays}
                    </span>
                    <span className="text-on-surface-variant">ngày</span>
                  </span>
                  <span className="text-outline">·</span>
                  <span className="flex items-center gap-1.5">
                    <span className={`font-mono font-semibold ${changedFields.has("period") ? "text-primary" : "text-on-surface"}`}>
                      {periodWeeks}
                    </span>
                    <span className="text-on-surface-variant">tuần</span>
                  </span>
                </div>
              )}
            </section>

            {/* Validation Alerts */}
            {(errors.length > 0 || warnings.length > 0) && (
              <section className={`rounded-xl border p-4 space-y-2 ${
                errors.length > 0
                  ? "bg-error-container/30 border-error/30"
                  : "bg-tertiary-container/20 border-tertiary/30"
              }`}>
                <h4 className={`text-label-sm font-semibold flex items-center gap-2 ${
                  errors.length > 0 ? "text-error" : "text-tertiary"
                }`}>
                  <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                    {errors.length > 0 ? "error" : "warning"}
                  </span>
                  {errors.length > 0 ? `${errors.length} lỗi cần sửa` : `${warnings.length} cảnh báo`}
                </h4>
                <div className="space-y-1">
                  {[...errors, ...warnings].slice(0, 5).map((v) => (
                    <div key={v.key} className={`flex items-start gap-2 text-[11px] ${
                      v.type === "error" ? "text-error" : "text-tertiary"
                    }`}>
                      <span className="material-symbols-outlined text-[12px] shrink-0 mt-0.5" aria-hidden="true">
                        {v.type === "error" ? "close" : "info"}
                      </span>
                      <span>{v.message}</span>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {/* Targets Table with Inputs */}
            <section>
              <h3 className="text-label-md font-semibold text-on-surface mb-3 flex items-center gap-2">
                <Tooltip content="Số ca mỗi nhân sự cần trực trong kỳ và số người đủ điều kiện cho mỗi loại lịch">
                  <span className="material-symbols-outlined text-[16px]" aria-hidden="true">target</span>
                </Tooltip>
                Mục tiêu phân bổ ca
                {changedFields.has("targets") && (
                  <span className="w-2 h-2 rounded-full bg-primary animate-pulse" title="Đã thay đổi" />
                )}
              </h3>
              <p className="text-[12px] text-on-surface-variant mb-3">
                Số ca <strong>mỗi người</strong> mong muốn trong cả kỳ.
              </p>
              <div className="border border-outline-variant rounded-xl overflow-hidden">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-surface-container-low border-b border-outline-variant">
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase">Loại</th>
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-center">
                        <Tooltip content="Số nhân sự đủ điều kiện để trực loại ca này">Đủ ĐK</Tooltip>
                      </th>
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-center">
                        <Tooltip content="Số ca mỗi người cần trực trong kỳ">Ca/kỳ</Tooltip>
                      </th>
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-right">Tổng ca</th>
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-right">
                        <Tooltip content="Trung bình ca/tuần/người">Ca/tuần</Tooltip>
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {shiftIds.map((tid) => {
                      const weeklyAvg = (targets[tid] / periodWeeks).toFixed(1);
                      const hasWarning = warnings.some(w => w.key.startsWith(tid));
                      const meta = SHIFT_META[tid];
                      return (
                        <tr key={tid} className={`hover:bg-surface-container-lowest transition-colors ${hasWarning ? "bg-tertiary-container/10" : ""}`}>
                          <td className="py-2 px-3">
                            <div className="flex items-center gap-2">
                              <Tooltip content={meta.subtitle}>
                                <span className={`font-mono font-bold text-[13px] ${meta.color}`}>{tid}</span>
                              </Tooltip>
                              <span className="text-[12px] text-on-surface-variant">{meta.subtitle}</span>
                            </div>
                          </td>
                          <td className="py-2 px-3 text-center">
                            <input
                              type="number"
                              min={1}
                              max={50}
                              value={eligible[tid]}
                              onChange={(e) => {
                                setEligible((prev) => ({ ...prev, [tid]: Math.max(1, parseInt(e.target.value) || 1) }));
                                handleFieldChange("eligible");
                              }}
                              className={`w-16 h-8 px-2 rounded-lg border text-label-sm text-right font-mono tabular-nums focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all ${
                                changedFields.has("eligible")
                                  ? "border-primary bg-primary-fixed/20"
                                  : "border-outline-variant bg-surface-container-lowest"
                              }`}
                            />
                          </td>
                          <td className="py-2 px-3 text-center">
                            <input
                              type="number"
                              min={0}
                              max={50}
                              value={targets[tid]}
                              onChange={(e) => {
                                setTargets((prev) => ({ ...prev, [tid]: Math.max(0, parseInt(e.target.value) || 0) }));
                                handleFieldChange("targets");
                              }}
                              className={`w-16 h-8 px-2 rounded-lg border text-label-sm text-right font-mono tabular-nums focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all ${
                                changedFields.has("targets")
                                  ? "border-primary bg-primary-fixed/20"
                                  : "border-outline-variant bg-surface-container-lowest"
                              }`}
                            />
                          </td>
                          <td className="py-2 px-3 text-right">
                            <span className={`font-mono font-semibold ${meta.color}`}>
                              {targets[tid] * eligible[tid]}
                            </span>
                          </td>
                          <td className="py-2 px-3 text-right">
                            <Tooltip content={parseFloat(weeklyAvg) > 6 ? "Quá tải!" : "Bình thường"}>
                              <span className={`font-mono text-[12px] ${
                                parseFloat(weeklyAvg) > 6 ? "text-tertiary font-bold" : "text-on-surface-variant"
                              }`}>
                                {weeklyAvg}
                              </span>
                            </Tooltip>
                          </td>
                        </tr>
                      );
                    })}
                    <tr className="bg-primary-fixed/30 font-semibold">
                      <td className="py-2 px-3 text-on-surface">Tổng</td>
                      <td className="py-2 px-3 text-center font-mono tabular-nums">{totalEligible}</td>
                      <td className="py-2 px-3 text-center font-mono tabular-nums">{totalTarget}</td>
                      <td className="py-2 px-3 text-right font-mono tabular-nums text-primary">{totalGenerated}</td>
                      <td className="py-2 px-3"></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            {/* Results Preview */}
            <section className="bg-secondary-container/20 rounded-xl p-4 border border-secondary/30">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-label-md font-semibold text-on-surface flex items-center gap-2">
                  <Tooltip content="Kết quả tính toán tự động dựa trên thông tin bạn nhập">
                    <span className="material-symbols-outlined text-[16px] text-secondary" aria-hidden="true">preview</span>
                  </Tooltip>
                  Kết quả tính toán
                  {(changedFields.size > 0) && (
                    <span className="w-2 h-2 rounded-full bg-secondary animate-pulse" title="Đã thay đổi" />
                  )}
                </h3>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setShowChart(!showChart)}
                    className={`text-[11px] px-2 py-1 rounded-lg transition-colors ${
                      showChart ? "bg-secondary-container text-secondary" : "text-on-surface-variant hover:bg-surface-container"
                    }`}
                  >
                    <span className="material-symbols-outlined text-[14px] align-middle mr-1" aria-hidden="true">bar_chart</span>
                    Chart
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowDiff(!showDiff)}
                    className={`text-[11px] px-2 py-1 rounded-lg transition-colors ${
                      showDiff ? "bg-secondary-container text-secondary" : "text-on-surface-variant hover:bg-surface-container"
                    }`}
                  >
                    <span className="material-symbols-outlined text-[14px] align-middle mr-1" aria-hidden="true">compare_arrows</span>
                    Diff
                  </button>
                </div>
              </div>

              {/* Chart */}
              {showChart && <DistributionChart targets={targets} eligible={eligible} computed={computed} />}

              {/* Diff View */}
              {showDiff && <DiffView computed={computed} currentConfig={currentConfig} />}

              {/* Options */}
              <div className="flex items-center gap-2 mt-4 px-3 py-2 rounded-lg bg-surface-container-lowest border border-outline-variant">
                <Tooltip content="Mở rộng danh sách nhân sự đủ điều kiện cho L01/L02/L03">
                  <input
                    type="checkbox"
                    id="expand-eligibility"
                    checked={expandEligibility}
                    onChange={(e) => { setExpandEligibility(e.target.checked); handleFieldChange("eligibility"); }}
                    className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
                  />
                </Tooltip>
                <label htmlFor="expand-eligibility" className="text-[12px] text-on-surface cursor-pointer flex-1">
                  Mở rộng eligibility L01/L02/L03
                </label>
              </div>

              {/* Results Table */}
              <div className="mt-4 border border-outline-variant rounded-xl overflow-hidden">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-surface-container-low border-b border-outline-variant">
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-left">Loại</th>
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-center">Min/ngày</th>
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-center">Max/ngày</th>
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-center">Min/tuần</th>
                      <th className="py-2.5 px-3 font-label-sm text-label-sm text-on-surface-variant uppercase text-center">Max/tuần</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {shiftIds.map((tid) => {
                      const c = recommendation?.config ?? computed;
                      const minDay = c[`${tid.toLowerCase()}MinPerDay` as keyof AutoCalculateResult] as number;
                      const maxDay = c[`${tid.toLowerCase()}MaxPerDay` as keyof AutoCalculateResult] as number;
                      const minWeek = c[`${tid.toLowerCase()}MinPerWeek` as keyof AutoCalculateResult] as number;
                      const maxWeek = c[`${tid.toLowerCase()}MaxPerWeek` as keyof AutoCalculateResult] as number;
                      const hasWarning = warnings.some(w => w.key.startsWith(tid));
                      const meta = SHIFT_META[tid];
                      const isFromAI = !!recommendation;
                      return (
                        <tr key={tid} className={`hover:bg-surface-container-lowest transition-colors ${hasWarning ? "bg-tertiary-container/10" : ""}`}>
                          <td className="py-2 px-3">
                            <div className="flex items-center gap-2">
                              <Tooltip content={meta.subtitle}>
                                <span className={`font-mono font-bold text-[13px] ${meta.color}`}>{tid}</span>
                              </Tooltip>
                              <span className="text-[11px] text-on-surface-variant">{meta.subtitle}</span>
                              {isFromAI && (
                                <span className="material-symbols-outlined text-[12px] text-secondary" aria-hidden="true">auto_awesome</span>
                              )}
                            </div>
                          </td>
                          <td className="py-2 px-3 text-center">
                            <span className={`font-mono font-semibold tabular-nums ${
                              isFromAI ? "text-secondary" : "text-on-surface"
                            }`}>{minDay}</span>
                          </td>
                          <td className="py-2 px-3 text-center">
                            <span className={`font-mono font-semibold tabular-nums ${
                              isFromAI ? "text-secondary" : "text-on-surface"
                            }`}>{maxDay}</span>
                          </td>
                          <td className="py-2 px-3 text-center">
                            <span className={`font-mono font-semibold tabular-nums ${
                              isFromAI ? "text-secondary" : "text-on-surface"
                            }`}>{minWeek}</span>
                          </td>
                          <td className="py-2 px-3 text-center">
                            <span className={`font-mono font-semibold tabular-nums ${
                              isFromAI ? "text-secondary" : "text-on-surface"
                            }`}>{maxWeek}</span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {recommendation && (
                <div className="mt-3 px-3 py-2 rounded-lg bg-secondary-container/30 border border-secondary/30">
                  <div className="flex items-center gap-2 text-[11px] text-secondary">
                    <span className="material-symbols-outlined text-[14px]" aria-hidden="true">auto_awesome</span>
                    <span>AI đã tối ưu các giá trị (màu xanh) dựa trên dữ liệu thực tế</span>
                  </div>
                </div>
              )}

              <p className="text-[11px] text-on-surface-variant mt-3 leading-relaxed">
                <Tooltip content="Công thức tính toán tự động">
                  <span><strong>Công thức:</strong> min/ngày = ⌈(target × eligible) / ngày⌋ · max/tuần = ⌈(target / tuần) × 1.5⌉ · max/ngày = ⌈max/tuần × 1.2⌉</span>
                </Tooltip>
              </p>

              {/* AI Recommendation */}
              <div className="mt-4 pt-3 border-t border-secondary/20">
                <div className="flex items-center justify-between gap-2 flex-wrap mb-2">
                  <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-[16px] text-secondary" aria-hidden="true">auto_awesome</span>
                    <span className="text-[13px] font-semibold text-on-surface">AI đề xuất</span>
                  </div>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={fetchAIRecommendation}
                    loading={recommending}
                    disabled={recommending}
                    icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">neurology</span>}
                  >
                    {recommending ? "Đang tính..." : "Lấy đề xuất AI"}
                  </Button>
                </div>
                {recommendError && (
                  <div className="text-[12px] text-error bg-error-container/30 border border-error/30 rounded-lg px-3 py-2 mb-2">
                    {recommendError}
                  </div>
                )}
                {recommendation && (
                  <div className="bg-surface-container-lowest rounded-lg p-3 border border-secondary/30 space-y-2">
                    <div className="flex items-baseline justify-between gap-2">
                      <span className="text-[12px] text-on-surface-variant">Tổng ca dự kiến:</span>
                      <span className="font-mono font-bold text-primary tabular-nums">{recommendation.totalShiftsExpected}</span>
                    </div>
                    <p className="text-[11px] text-on-surface leading-relaxed">
                      {recommendation.rationale}
                    </p>
                  </div>
                )}
              </div>
            </section>
          </div>

          {/* Footer */}
          <div className="sticky bottom-0 bg-surface-container-lowest border-t border-outline-variant px-6 py-4 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="text-[11px] text-on-surface-variant">
                {infos.length > 0 && (
                  <span className="flex items-center gap-1 text-info">
                    <span className="material-symbols-outlined text-[12px]" aria-hidden="true">info</span>
                    {infos.length} gợi ý
                  </span>
                )}
                {changedFields.size > 0 && (
                  <span className="flex items-center gap-1 ml-3 text-secondary">
                    <span className="material-symbols-outlined text-[12px]" aria-hidden="true">edit</span>
                    {changedFields.size} thay đổi
                  </span>
                )}
              </div>
              <div className="text-[10px] text-on-surface-variant flex items-center gap-2 border-l border-outline-variant pl-3">
                <span className="hidden sm:inline">Ctrl+Enter: Áp dụng</span>
                <span>Esc: Đóng</span>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Button variant="secondary" size="sm" onClick={() => setShowSaveModal(true)} icon={<span className="material-symbols-outlined text-[16px]">bookmark_add</span>}>
                Lưu Preset
              </Button>
              <Button variant="ghost" size="sm" onClick={onClose}>Hủy</Button>
              <Button
                variant="primary"
                size="sm"
                icon={<span className="material-symbols-outlined text-[16px]" aria-hidden="true">check</span>}
                onClick={() => {
                  onApply(computed);
                  onClose();
                }}
                disabled={errors.length > 0}
              >
                Áp dụng
              </Button>
            </div>
          </div>
        </div>
      </div>

      {/* Save Preset Modal */}
      {showSaveModal && (
        <div className="fixed inset-0 z-[110] flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-fade-in">
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant shadow-lg w-full max-w-md p-6">
            <h3 className="text-title-md font-semibold text-on-surface mb-4">Lưu Preset mới</h3>
            <FormInput
              label="Tên preset"
              type="text"
              value={savePresetName}
              onChange={(v) => setSavePresetName(String(v))}
              placeholder="VD: Cấu hình tháng 6"
              hint="Đặt tên dễ nhớ để sử dụng lại sau"
            />
            <div className="flex justify-end gap-2 mt-4">
              <Button variant="ghost" size="sm" onClick={() => setShowSaveModal(false)}>Hủy</Button>
              <Button variant="primary" size="sm" onClick={handleSavePreset} disabled={!savePresetName.trim()}>Lưu</Button>
            </div>
          </div>
        </div>
      )}

      {/* Preset Comparison Modal */}
      {showCompareModal && (
        <PresetCompareModal
          presets={savedPresets}
          currentConfig={currentConfig}
          onClose={() => setShowCompareModal(false)}
          onSelect={applySavedPreset}
        />
      )}

      {/* Export Modal */}
      {showExportModal && (
        <ExportModal
          config={stateToInput({ periodDays, periodWeeks, targets, eligible, expandEligibility })}
          computed={computed}
          onClose={() => setShowExportModal(false)}
        />
      )}
    </>
  );
}
