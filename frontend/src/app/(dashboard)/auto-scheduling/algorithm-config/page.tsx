"use client";

import { useCallback, useEffect, useState } from "react";
import { Button, IconButton } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useRole } from "@/hooks/useRole";
import { useToast } from "@/hooks/useToast";
import { EmptyState } from "@/components/ui/EmptyState";
import { BackButton } from "@/components/ui/BackButton";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { FormSelect } from "@/components/ui/FormSelect";
import { getParamValidation } from "@/lib/validation/algorithmConfig";
import { PresetSelector, type PresetKey } from "@/components/algorithm-config/PresetSelector";
import { PresetSandboxModal, type PresetEntry } from "@/components/algorithm-config/PresetSandboxModal";
import { ConfigAuditLog } from "@/components/algorithm-config/ConfigAuditLog";
import { parseNumber } from "@/lib/number-utils";
import type { ApiResponse } from "@/types/api";

/* ─── Types ──────────────────────────────────────────────── */

type ConfigEntry = {
  paramKey: string;
  paramValue: string;
  valueType: "STRING" | "NUMBER" | "BOOLEAN" | "JSON";
  description: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
};

type EditingConfig = Partial<Pick<ConfigEntry, "paramValue" | "description">>;

type RuntimeConfig = {
  maxIterations: number;
  weekendWeight: number;
  overnightRecoveryHours: number;
  greedyCoverageThreshold: number;
  balanceScoreMin: number;
  autoCompensationEnabled: boolean;
  backtrackTimeLimitSeconds: number;
  minStaffPerShift: number;
  maxStaffPerShift: number;
  minShiftsPerStaff: number;
  maxShiftsPerStaff: number;
  l01MinPerDay?: number; l02MinPerDay?: number; l03MinPerDay?: number; l04MinPerDay?: number;
  l01MaxPerDay?: number; l02MaxPerDay?: number; l03MaxPerDay?: number; l04MaxPerDay?: number;
  l01MinPerWeek?: number; l02MinPerWeek?: number; l03MinPerWeek?: number; l04MinPerWeek?: number;
  l01MaxPerWeek?: number; l02MaxPerWeek?: number; l03MaxPerWeek?: number; l04MaxPerWeek?: number;
  holidayMode?: string;
  removedShiftTypes?: string[];  // L01..L04 to skip when auto-generating requirements
};

type AlgorithmMetrics = {
  id: number;
  algorithmType: string;
  executionTimeMs: number;
  coverageRate: number;
  balanceScore: number;
  conflictCount: number;
  totalSchedulesCreated?: number;
  periodId?: number;
  periodName?: string;
  createdAt: string;
};

type PresetConfig = {
  label: string;
  tagline: string;
  icon: string;
  color: string;
  colorBg: string;
  accent: string;
  config: RuntimeConfig;
};

const ALGORITHM_PRESETS: Record<PresetKey, PresetConfig> = {
  balanced: {
    label: "Cân bằng",
    tagline: "Tốc độ & chất lượng hài hòa",
    icon: "psychology",
    color: "text-blue-600",
    colorBg: "bg-blue-50",
    accent: "border-blue-400",
    config: { maxIterations: 2000, weekendWeight: 2.5, greedyCoverageThreshold: 0.90, balanceScoreMin: 0.75, autoCompensationEnabled: true, overnightRecoveryHours: 24, backtrackTimeLimitSeconds: 120, minStaffPerShift: 1, maxStaffPerShift: 0, minShiftsPerStaff: 0, maxShiftsPerStaff: 0 },
  },
  fast: {
    label: "Nhanh",
    tagline: "Phủ lịch nhanh, ưu tiên tốc độ",
    icon: "bolt",
    color: "text-amber-600",
    colorBg: "bg-amber-50",
    accent: "border-amber-400",
    config: { maxIterations: 500, weekendWeight: 1.5, greedyCoverageThreshold: 0.75, balanceScoreMin: 0.60, autoCompensationEnabled: true, overnightRecoveryHours: 24, backtrackTimeLimitSeconds: 30, minStaffPerShift: 1, maxStaffPerShift: 0, minShiftsPerStaff: 0, maxShiftsPerStaff: 0 },
  },
  quality: {
    label: "Chất lượng cao",
    tagline: "Tìm lời giải tối ưu, chạy chậm hơn",
    icon: "verified_user",
    color: "text-emerald-600",
    colorBg: "bg-emerald-50",
    accent: "border-emerald-400",
    config: { maxIterations: 5000, weekendWeight: 3.0, greedyCoverageThreshold: 0.95, balanceScoreMin: 0.85, autoCompensationEnabled: true, overnightRecoveryHours: 24, backtrackTimeLimitSeconds: 300, minStaffPerShift: 1, maxStaffPerShift: 0, minShiftsPerStaff: 0, maxShiftsPerStaff: 0 },
  },
  conservative: {
    label: "Thận trọng",
    tagline: "Ít thay đổi, giữ nguyên lịch hiện tại",
    icon: "shield",
    color: "text-slate-600",
    colorBg: "bg-slate-100",
    accent: "border-slate-400",
    config: { maxIterations: 1000, weekendWeight: 1.0, greedyCoverageThreshold: 0.60, balanceScoreMin: 0.50, autoCompensationEnabled: true, overnightRecoveryHours: 24, backtrackTimeLimitSeconds: 60, minStaffPerShift: 1, maxStaffPerShift: 0, minShiftsPerStaff: 0, maxShiftsPerStaff: 0 },
  },
};

const PARAM_GROUPS = [
  {
    id: "shifts",
    label: "Số ca / nhân sự",
    icon: "groups",
    color: "text-blue-600",
    bg: "bg-blue-50",
    accent: "border-l-4 border-l-blue-500",
    params: ["min_staff_per_shift", "max_staff_per_shift", "min_shifts_per_staff", "max_shifts_per_staff"] as const,
    descriptions: {
      min_staff_per_shift: { label: "min_staff", desc: "Số nhân sự tối thiểu mỗi ca. Đặt 0 để bỏ qua.", hint: "0–10 · Mặc định: 1" },
      max_staff_per_shift: { label: "max_staff", desc: "Số nhân sự tối đa mỗi ca. 0 = không giới hạn.", hint: "0–20 · Mặc định: 0 (không giới hạn)" },
      min_shifts_per_staff: { label: "min_shifts", desc: "Số ca trực tối thiểu mỗi nhân sự trong kỳ. 0 = không áp dụng.", hint: "0–50 · Mặc định: 0" },
      max_shifts_per_staff: { label: "max_shifts", desc: "Số ca trực tối đa mỗi nhân sự trong kỳ. 0 = không giới hạn.", hint: "0–100 · Mặc định: 0" },
    },
  },
  {
    id: "thresholds",
    label: "Ngưỡng xếp lịch",
    icon: "donut_small",
    color: "text-blue-600",
    bg: "bg-blue-50",
    accent: "border-l-4 border-l-blue-500",
    params: ["greedy_coverage_threshold", "balance_score_min"] as const,
    descriptions: {
      greedy_coverage_threshold: { label: "greedy_threshold", desc: "Greedy dừng sớm khi đạt ngưỡng. Giảm → chạy nhanh hơn. Tăng → phủ kỹ hơn.", hint: "0.5–1.0 · Mặc định: 0.85" },
      balance_score_min: { label: "balance_score", desc: "Ngưỡng cân bằng tải tối thiểu. Cao → phân bổ công bằng hơn nhưng khó đạt.", hint: "0.3–1.0 · Mặc định: 0.70" },
    },
  },
  {
    id: "weights",
    label: "Trọng số & ngày lễ",
    icon: "event_note",
    color: "text-teal-600",
    bg: "bg-teal-50",
    accent: "border-l-4 border-l-teal-500",
    params: ["weekend_weight", "holiday_mode"] as const,
    descriptions: {
      weekend_weight: { label: "weekend_weight", desc: "Hệ số nhân khi tính penalty T7/CN. >1 ưu tiên tránh cuối tuần. Đặt=1 để tắt ưu tiên.", hint: "1.0–5.0 · Mặc định: 2.0" },
      holiday_mode: { label: "holiday_mode", desc: "Xử lý ngày lễ: SKIP = bỏ qua, PARTIAL = giảm cường độ xếp lịch.", hint: "SKIP · PARTIAL" },
    },
  },
  {
    id: "excluded",
    label: "Loại lịch bỏ qua",
    icon: "block",
    color: "text-red-600",
    bg: "bg-red-50",
    accent: "border-l-4 border-l-red-500",
    params: ["removed_shift_types"] as const,
    descriptions: {
      removed_shift_types: { label: "removed_shift_types", desc: "Các mã loại lịch (L01..L04) bị bỏ qua khi tự động tạo yêu cầu cho kỳ mới.", hint: "Nhấn chip để bật/tắt · Mặc định: rỗng" },
    },
  },
  {
    id: "limits",
    label: "Giới hạn thuật toán",
    icon: "memory",
    color: "text-indigo-600",
    bg: "bg-indigo-50",
    accent: "border-l-4 border-l-indigo-500",
    params: ["max_iterations", "backtrack_time_limit_seconds"] as const,
    descriptions: {
      max_iterations: { label: "max_iterations", desc: "Số vòng lặp tối đa Backtracking. Tăng → lời giải tốt hơn nhưng chậm hơn.", hint: "100–10000 · Mặc định: 1000" },
      backtrack_time_limit_seconds: { label: "time_limit", desc: "Giới hạn thời gian Backtracking (giây). Hết thời gian → dừng và trả kết quả tốt nhất.", hint: "10–300 · Mặc định: 60s" },
    },
  },
  {
    id: "recovery",
    label: "Nghỉ ngơi",
    icon: "hotel",
    color: "text-rose-600",
    bg: "bg-rose-50",
    accent: "border-l-4 border-l-rose-500",
    params: ["overnight_recovery_hours"] as const,
    descriptions: {
      overnight_recovery_hours: { label: "recovery_hours", desc: "Số giờ nghỉ bắt buộc giữa hai ca trực 24/24. Thường đặt 24 giờ.", hint: "12–72 giờ · Mặc định: 24" },
    },
  },
];

/* ─── Shift-type limit config ─────────────────────────────── */

const SHIFT_TYPE_GROUPS = [
  {
    id: "l01", label: "L01", subtitle: "Trực 24/24", icon: "emergency",
    color: "text-red-600", colorBg: "bg-red-50",
    borderColor: "border-red-400",
    description: "Ca trực liên tục 24h, có nghỉ bù",
    params: ["l01MinPerDay", "l01MaxPerDay", "l01MinPerWeek", "l01MaxPerWeek"] as const,
  },
  {
    id: "l02", label: "L02", subtitle: "Thông tầm", icon: "schedule",
    color: "text-blue-600", colorBg: "bg-blue-50",
    borderColor: "border-blue-400",
    description: "Ca ngày, không nghỉ trưa",
    params: ["l02MinPerDay", "l02MaxPerDay", "l02MinPerWeek", "l02MaxPerWeek"] as const,
  },
  {
    id: "l03", label: "L03", subtitle: "PK Dịch vụ", icon: "medical_services",
    color: "text-green-600", colorBg: "bg-green-50",
    borderColor: "border-green-400",
    description: "Ca khám dịch vụ, buổi sáng hoặc chiều",
    params: ["l03MinPerDay", "l03MaxPerDay", "l03MinPerWeek", "l03MaxPerWeek"] as const,
  },
  {
    id: "l04", label: "L04", subtitle: "PK Chuyên gia", icon: "stethoscope",
    color: "text-purple-600", colorBg: "bg-purple-50",
    borderColor: "border-purple-400",
    description: "Ca khám chuyên sâu, thời gian dài hơn",
    params: ["l04MinPerDay", "l04MaxPerDay", "l04MinPerWeek", "l04MaxPerWeek"] as const,
  },
] as const;

function ShiftTypeGroupCard({ group, form, editing, onChange }: {
  group: typeof SHIFT_TYPE_GROUPS[number];
  form: RuntimeConfig;
  editing: boolean;
  onChange: (key: string, value: number) => void;
}) {
  const ROW_LABELS: Record<string, string> = {
    l01MinPerDay: "T.min/người", l01MaxPerDay: "T.max/người", l01MinPerWeek: "T.min/tuần", l01MaxPerWeek: "T.max/tuần",
    l02MinPerDay: "T.min/người", l02MaxPerDay: "T.max/người", l02MinPerWeek: "T.min/tuần", l02MaxPerWeek: "T.max/tuần",
    l03MinPerDay: "T.min/người", l03MaxPerDay: "T.max/người", l03MinPerWeek: "T.min/tuần", l03MaxPerWeek: "T.max/tuần",
    l04MinPerDay: "T.min/người", l04MaxPerDay: "T.max/người", l04MinPerWeek: "T.min/tuần", l04MaxPerWeek: "T.max/tuần",
  };
  const ROW_TOOLTIPS: Record<string, string> = {
    l01MinPerDay: "Số nhân sự tối thiểu mỗi ngày", l01MaxPerDay: "Số nhân sự tối đa mỗi ngày",
    l01MinPerWeek: "Số ca trực tối thiểu mỗi tuần", l01MaxPerWeek: "Số ca trực tối đa mỗi tuần",
    l02MinPerDay: "Số nhân sự tối thiểu mỗi ngày", l02MaxPerDay: "Số nhân sự tối đa mỗi ngày",
    l02MinPerWeek: "Số ca trực tối thiểu mỗi tuần", l02MaxPerWeek: "Số ca trực tối đa mỗi tuần",
    l03MinPerDay: "Số nhân sự tối thiểu mỗi ngày", l03MaxPerDay: "Số nhân sự tối đa mỗi ngày",
    l03MinPerWeek: "Số ca trực tối thiểu mỗi tuần", l03MaxPerWeek: "Số ca trực tối đa mỗi tuần",
    l04MinPerDay: "Số nhân sự tối thiểu mỗi ngày", l04MaxPerDay: "Số nhân sự tối đa mỗi ngày",
    l04MinPerWeek: "Số ca trực tối thiểu mỗi tuần", l04MaxPerWeek: "Số ca trực tối đa mỗi tuần",
  };

  return (
    <div className={`bg-surface-container-lowest rounded-xl border ${group.borderColor} overflow-hidden flex flex-col w-[180px] shrink-0 group/card`} style={{ minHeight: 160 }}>
      <div className={`px-3 py-2 border-b ${group.borderColor}/30 bg-surface-container-low flex items-start gap-2 shrink-0`}>
        <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${group.colorBg} ${group.color}`}>
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{group.icon}</span>
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-label-sm font-bold text-on-surface leading-tight">{group.label}</p>
          <p className="text-[10px] text-on-surface-variant leading-tight">{group.subtitle}</p>
        </div>
      </div>
      {/* Mô tả loại lịch */}
      <div className="px-3 py-1.5 bg-surface-container-low/50 border-b border-outline-variant/30">
        <p className="text-[9px] text-on-surface-variant leading-tight">{group.description}</p>
      </div>
      {/* 4 rows — one per param */}
      <div className="flex flex-col divide-y divide-outline-variant/40 flex-1">
        {group.params.map((param) => {
          const numVal = typeof form[param] === "number" ? (form[param] as number) : 0;
          const display = numVal === 0 ? "Tắt" : numVal.toString();
          const label = ROW_LABELS[param] ?? param;
          const tooltip = ROW_TOOLTIPS[param] ?? "";
          return (
            <div key={param} className="flex items-center justify-between gap-2 px-3 py-2 hover:bg-surface-container-low/50 transition-colors group/row" title={tooltip}>
              <div className="flex items-center gap-1.5 min-w-0">
                <span className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/50 px-1 py-0.5 rounded leading-none whitespace-nowrap shrink-0">
                  {label}
                </span>
                <span className="material-symbols-outlined text-[12px] text-on-surface-variant/60 hover:text-primary transition-colors shrink-0 cursor-help" aria-hidden="true">info</span>
              </div>
              <div className="flex items-center shrink-0">
                {editing ? (
                  <input type="number" min={0} max={99} step={1}
                    className="h-8 w-16 rounded-lg border border-outline-variant bg-surface-container-low px-2 text-center text-[13px] font-mono text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors tabular-nums"
                    value={numVal}
                    onChange={(e) => onChange(param, parseInt(e.target.value) || 0)}
                  />
                ) : (
                  <span className="font-mono text-sm font-bold text-on-surface w-12 text-right shrink-0 tabular-nums">{display}</span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ─── Tab System ─────────────────────────────────────────── */

type TabKey = "config" | "history" | "audit" | "reference";

function TabBar({ active, onChange }: { active: TabKey; onChange: (t: TabKey) => void }) {
  const tabs: { key: TabKey; label: string; icon: string }[] = [
    { key: "config", label: "Cấu hình", icon: "tune" },
    { key: "history", label: "Lịch sử chạy", icon: "history" },
    { key: "audit", label: "Nhật ký thay đổi", icon: "manage_history" },
    { key: "reference", label: "Tham khảo", icon: "info" },
  ];

  return (
    <div className="inline-flex items-center gap-1 p-1 bg-surface-container-low rounded-xl border border-outline-variant" role="tablist">
      {tabs.map(tab => (
        <button
          key={tab.key}
          role="tab"
          aria-selected={active === tab.key}
          onClick={() => onChange(tab.key)}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-label-md font-medium transition-all cursor-pointer ${
            active === tab.key
              ? "bg-surface-container-lowest text-primary shadow-sm"
              : "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-lowest/50"
          }`}
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{tab.icon}</span>
          {tab.label}
        </button>
      ))}
    </div>
  );
}

/* ─── Runtime Config Editor ──────────────────────────────── */

function RuntimeConfigEditor({ onSaved }: { onSaved?: () => void }) {
  const { success, error } = useToast();
  const [config, setConfig] = useState<RuntimeConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<RuntimeConfig | null>(null);
  const [activePreset, setActivePreset] = useState<PresetKey | null>(null);
  const [showDiff, setShowDiff] = useState(false);
  const [sandboxOpen, setSandboxOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, resAutoGen] = await Promise.all([
        api.getRuntimeConfig(),
        api.getAutoGenConfig(),
      ]);
      const data = (res as unknown as { data: RuntimeConfig }).data;
      const autoGen = (resAutoGen as unknown as { data: RuntimeConfig }).data;
      // Safe merge: autoGen keys có prefix "l01...l04" và "holidayMode" mới ghi đè data
      // Prevent runtime config bị ghi đè bởi auto-gen cho các keys không liên quan
      const autoGenOverrideKeys = new Set([
        "holidayMode",
        "removedShiftTypes",
        "l01MinPerDay", "l01MaxPerDay", "l01MinPerWeek", "l01MaxPerWeek",
        "l02MinPerDay", "l02MaxPerDay", "l02MinPerWeek", "l02MaxPerWeek",
        "l03MinPerDay", "l03MaxPerDay", "l03MinPerWeek", "l03MaxPerWeek",
        "l04MinPerDay", "l04MaxPerDay", "l04MinPerWeek", "l04MaxPerWeek",
      ]);
      const merged: RuntimeConfig = { ...data };
      for (const key of Object.keys(autoGen) as (keyof RuntimeConfig)[]) {
        if (autoGenOverrideKeys.has(key as string) && autoGen[key] !== undefined) {
          (merged as Record<string, unknown>)[key as string] = autoGen[key];
        }
      }
      setConfig(merged);
      setForm(merged);
    } catch {
      error("Không thể tải cấu hình runtime");
    } finally {
      setLoading(false);
    }
  }, [error]);

  useEffect(() => { void load(); }, [load]);

  const checkPreset = useCallback((cfg: RuntimeConfig): PresetKey | null => {
    for (const [key, preset] of Object.entries(ALGORITHM_PRESETS) as [PresetKey, typeof ALGORITHM_PRESETS[PresetKey]][]) {
      const p = preset.config;
      if (
        cfg.maxIterations === p.maxIterations &&
        cfg.weekendWeight === p.weekendWeight &&
        cfg.greedyCoverageThreshold === p.greedyCoverageThreshold &&
        cfg.balanceScoreMin === p.balanceScoreMin &&
        cfg.backtrackTimeLimitSeconds === p.backtrackTimeLimitSeconds &&
        cfg.minStaffPerShift === p.minStaffPerShift &&
        cfg.maxStaffPerShift === p.maxStaffPerShift &&
        cfg.minShiftsPerStaff === p.minShiftsPerStaff &&
        cfg.maxShiftsPerStaff === p.maxShiftsPerStaff &&
        cfg.overnightRecoveryHours === p.overnightRecoveryHours
      ) return key;
    }
    return null;
  }, []);

  useEffect(() => {
    if (form) setActivePreset(checkPreset(form));
  }, [form, checkPreset]);

  const applyPreset = (key: PresetKey) => {
    const preset = ALGORITHM_PRESETS[key];
    setForm({ ...form!, ...preset.config });
    setActivePreset(key);
    setEditing(true);
  };

  const handleSave = async () => {
    if (!form) return;
    setSaving(true);
    try {
      await Promise.all([
        api.updateRuntimeConfig(form),
        api.updateAutoGenConfig({
          enabled: true,
          holidayMode: form.holidayMode ?? "SKIP",
          l01MinPerDay: form.l01MinPerDay ?? 0,
          l02MinPerDay: form.l02MinPerDay ?? 0,
          l03MinPerDay: form.l03MinPerDay ?? 0,
          l04MinPerDay: form.l04MinPerDay ?? 0,
          l01MaxPerDay: form.l01MaxPerDay ?? 0,
          l02MaxPerDay: form.l02MaxPerDay ?? 0,
          l03MaxPerDay: form.l03MaxPerDay ?? 0,
          l04MaxPerDay: form.l04MaxPerDay ?? 0,
          l01MinPerWeek: form.l01MinPerWeek ?? 0,
          l02MinPerWeek: form.l02MinPerWeek ?? 0,
          l03MinPerWeek: form.l03MinPerWeek ?? 0,
          l04MinPerWeek: form.l04MinPerWeek ?? 0,
          l01MaxPerWeek: form.l01MaxPerWeek ?? 0,
          l02MaxPerWeek: form.l02MaxPerWeek ?? 0,
          l03MaxPerWeek: form.l03MaxPerWeek ?? 0,
          l04MaxPerWeek: form.l04MaxPerWeek ?? 0,
          removedShiftTypes: form.removedShiftTypes ?? [],
        }),
      ]);
      setConfig(form);
      setEditing(false);
      success("Đã lưu cấu hình thuật toán");
      onSaved?.();
    } catch (err) {
      error(getErrorMessage(err, "Lưu thất bại"));
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    if (config) {
      setForm(config);
      setEditing(false);
      setActivePreset(checkPreset(config));
    }
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
          <div className="h-6 w-40 bg-surface-container-low rounded animate-pulse mb-4" />
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            {[1, 2, 3, 4].map(i => <div key={i} className="h-20 bg-surface-container-low rounded-xl animate-pulse" />)}
          </div>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {[1, 2, 3, 4, 5, 6].map(i => <div key={i} className="h-44 bg-surface-container-low rounded-xl animate-pulse" />)}
        </div>
      </div>
    );
  }

  if (!config || !form) return null;

  return (
    <div className="space-y-5">
      {/* Presets */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-5">
        <div className="flex items-center justify-between gap-4 flex-wrap mb-4">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-[20px]" aria-hidden="true">bookmark</span>
            <p className="text-title-sm font-semibold text-on-surface">Cấu hình nhanh</p>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setSandboxOpen(true)}
              icon={<span className="material-symbols-outlined text-[12px]" aria-hidden="true">science</span>}
              className="rounded-full !bg-primary-fixed !text-primary !border !border-primary/20 hover:!bg-primary/10 px-2 py-0.5 text-[11px]"
              title="Mở sandbox so sánh preset"
            >
              Sandbox
            </Button>
            {!loading && config && form && config !== form && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-tertiary-container text-tertiary border border-tertiary/20">
                <span className="material-symbols-outlined text-[12px]">edit</span>
                Tùy chỉnh
              </span>
            )}
            {/* Feature A: Diff count badge */}
            {editing && (() => {
              if (!config || !form) return null;
              const changes = (Object.keys(form) as (keyof RuntimeConfig)[]).filter(k =>
                JSON.stringify(config[k]) !== JSON.stringify(form[k])
              );
              if (changes.length === 0) return null;
              return (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowDiff(true)}
                  icon={<span className="material-symbols-outlined text-[12px]" aria-hidden="true">difference</span>}
                  iconPosition="right"
                  className="rounded-full !bg-tertiary-container !text-tertiary !border !border-tertiary/30 hover:!bg-tertiary-container/80 px-2.5 py-1 text-[11px]"
                  title="Xem chi tiết thay đổi"
                >
                  {changes.length} thay đổi
                </Button>
              );
            })()}
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {editing ? (
              <>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={handleReset}
                >
                  Hủy bỏ
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={handleSave}
                  disabled={saving}
                  loading={saving}
                  icon={!saving ? <span className="material-symbols-outlined text-[16px]" aria-hidden="true">save</span> : undefined}
                >
                  Lưu thay đổi
                </Button>
              </>
            ) : (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setEditing(true)}
                icon={<span className="material-symbols-outlined text-[16px]" aria-hidden="true">edit</span>}
              >
                Chỉnh sửa
              </Button>
            )}
          </div>
        </div>
        {/* Preset cards — 2×2 on mobile, 4×1 on lg */}
        <PresetSelector
          presets={ALGORITHM_PRESETS}
          activePreset={activePreset}
          onApply={applyPreset}
        />
      </div>

      {/* Parameter groups */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        {PARAM_GROUPS.map(group => (
          <div key={group.id} className={`bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 ${group.accent}`}>
            <div className="px-5 py-4 bg-surface-container-low flex items-center gap-3">
              <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${group.bg} ${group.color}`}>
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{group.icon}</span>
              </div>
              <p className="text-label-md font-semibold text-on-surface tracking-tight">{group.label}</p>
            </div>
            <div className="p-5 space-y-5">
              {group.params.map(param => {
                const desc = group.descriptions[param as keyof typeof group.descriptions] ?? { label: param, desc: "", hint: "" };
                const cfgKey = param === "greedy_coverage_threshold" ? "greedyCoverageThreshold"
                  : param === "balance_score_min" ? "balanceScoreMin"
                  : param === "backtrack_time_limit_seconds" ? "backtrackTimeLimitSeconds"
                  : param === "weekend_weight" ? "weekendWeight"
                  : param === "overnight_recovery_hours" ? "overnightRecoveryHours"
                  : param === "min_staff_per_shift" ? "minStaffPerShift"
                  : param === "max_staff_per_shift" ? "maxStaffPerShift"
                  : param === "min_shifts_per_staff" ? "minShiftsPerStaff"
                  : param === "max_shifts_per_staff" ? "maxShiftsPerStaff"
                  : param === "holiday_mode" ? "holidayMode"
                  : "maxIterations" as keyof RuntimeConfig;

                // holiday_mode là select dropdown
                if (param === "holiday_mode") {
                  const value = form.holidayMode ?? "SKIP";
                  return (
                    <div key={param} className="flex items-center justify-between gap-3">
                      <div className="flex-1 min-w-0">
                        <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/50 px-1.5 py-0.5 rounded">{desc.label}</code>
                        <p className="text-[11px] text-on-surface-variant mt-1 leading-relaxed">{desc.desc}</p>
                        <p className="text-[10px] text-outline mt-0.5">{desc.hint}</p>
                      </div>
                      {editing ? (
                        <select
                          className="h-9 w-28 rounded-xl border border-outline-variant bg-surface-container-low px-2.5 text-label-sm text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors"
                          value={value}
                          onChange={e => setForm(f => f ? { ...f, holidayMode: e.target.value } : f)}>
                          <option value="SKIP">SKIP — Bỏ qua</option>
                          <option value="PARTIAL">PARTIAL — Giảm</option>
                        </select>
                      ) : (
                        <span className={`px-3 py-1 rounded-full text-label-sm font-semibold border ${value === "SKIP" ? "bg-teal-50 text-teal-700 border-teal-200" : "bg-amber-50 text-amber-700 border-amber-200"}`}>
                          {value}
                        </span>
                      )}
                    </div>
                  );
                }

                // 8A.9: Removed shift types — multi-select chips
                if (param === "removed_shift_types") {
                  const currentTypes = form.removedShiftTypes ?? [];
                  const ALL_TYPES = ["L01", "L02", "L03", "L04"] as const;
                  const toggle = (code: string) => {
                    setForm(f => {
                      if (!f) return f;
                      const next = currentTypes.includes(code)
                        ? currentTypes.filter(c => c !== code)
                        : [...currentTypes, code];
                      return { ...f, removedShiftTypes: next };
                    });
                  };
                  return (
                    <div key={param} className="flex items-start justify-between gap-3">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <code className="font-mono text-[12px] font-semibold text-primary bg-primary-fixed/50 px-1.5 py-0.5 rounded">{desc.label}</code>
                          <span className="material-symbols-outlined text-[14px] text-on-surface-variant/60 hover:text-primary transition-colors cursor-help" aria-hidden="true">info</span>
                        </div>
                        <p className="text-[12px] text-on-surface-variant mt-1 leading-relaxed">{desc.desc}</p>
                        <p className="text-[11px] text-outline mt-0.5">{desc.hint}</p>
                      </div>
                      {editing ? (
                        <div className="flex flex-wrap gap-1.5 justify-end max-w-[60%]">
                          {ALL_TYPES.map(code => {
                            const active = currentTypes.includes(code);
                            return (
                              <button
                                key={code}
                                type="button"
                                onClick={() => toggle(code)}
                                className={`px-2.5 py-1 rounded-full text-label-sm font-semibold border transition-all ${
                                  active
                                    ? "bg-error-container text-on-error-container border-error/30 hover:bg-error-container/80"
                                    : "bg-surface-container-low text-on-surface-variant border-outline-variant hover:border-primary hover:text-primary"
                                }`}
                              >
                                {active && <span className="material-symbols-outlined text-[12px] mr-0.5 align-middle">block</span>}
                                {code}
                              </button>
                            );
                          })}
                        </div>
                      ) : (
                        <div className="flex flex-wrap gap-1.5 justify-end max-w-[60%]">
                          {currentTypes.length === 0 ? (
                            <span className="px-3 py-1 rounded-full text-label-sm font-semibold border bg-secondary-container/30 text-on-secondary-container border-on-secondary-container/10">
                              Không bỏ qua
                            </span>
                          ) : (
                            currentTypes.map(code => (
                              <span
                                key={code}
                                className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-label-sm font-semibold border bg-error-container text-on-error-container border-error/30"
                              >
                                <span className="material-symbols-outlined text-[12px]">block</span>
                                {code}
                              </span>
                            ))
                          )}
                        </div>
                      )}
                    </div>
                  );
                }

                const min = param === "greedy_coverage_threshold" || param === "balance_score_min" ? 0.3 : param === "weekend_weight" ? 1 : 0;
                const max = param === "greedy_coverage_threshold" || param === "balance_score_min" ? 1 : param === "weekend_weight" ? 5 : param === "max_iterations" || param === "min_staff_per_shift" ? 10 : param === "max_staff_per_shift" || param === "max_shifts_per_staff" ? 100 : param === "min_shifts_per_staff" ? 50 : 300;
                const step = param === "greedy_coverage_threshold" || param === "balance_score_min" || param === "weekend_weight" ? 0.05 : 1;
                const numVal = typeof form[cfgKey] === "number" ? form[cfgKey] as number : 0;
                const display = param === "greedy_coverage_threshold" || param === "balance_score_min"
                  ? `${Math.round(numVal * 100)}%`
                  : param === "weekend_weight" ? numVal.toFixed(1) + "×"
                  : param === "backtrack_time_limit_seconds" ? `${numVal}s`
                  : param === "overnight_recovery_hours" ? `${numVal}h`
                  : numVal === 0 ? "Tắt" : numVal.toLocaleString();
                const pct = Math.min(100, Math.max(0, ((numVal - min) / (max - min)) * 100));

                return (
                  <div key={param}>
                    <div className="flex items-start justify-between gap-3 mb-2">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <code className="font-mono text-[12px] font-semibold text-primary bg-primary-fixed/50 px-1.5 py-0.5 rounded">{desc.label}</code>
                          <span className="material-symbols-outlined text-[14px] text-on-surface-variant/60 hover:text-primary transition-colors cursor-help" aria-hidden="true">info</span>
                        </div>
                        <p className="text-[12px] text-on-surface-variant mt-1 leading-relaxed">{desc.desc}</p>
                        <p className="text-[11px] text-outline mt-0.5">{desc.hint}</p>
                      </div>
                      {editing ? (
                        <input type="number" step={step} min={min} max={max}
                          className="h-9 w-24 rounded-xl border border-outline-variant bg-surface-container-low px-2.5 text-label-sm font-mono text-on-surface text-right tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors"
                          value={numVal}
                          onChange={e => setForm(f => f ? { ...f, [cfgKey]: step < 1 ? parseFloat(e.target.value) || 0 : parseInt(e.target.value) || 0 } : f)} />
                      ) : (
                        <span className="font-mono text-xl font-bold text-on-surface shrink-0 tabular-nums">{display}</span>
                      )}
                    </div>
                    {/* Feature D: Smart validation warning */}
                    {(() => {
                      const validation = getParamValidation(param, numVal);
                      if (!validation) return null;
                      const toneClass = validation.level === "error"
                        ? "bg-error-container/30 text-error border-error/40"
                        : "bg-tertiary-container/30 text-tertiary border-tertiary/40";
                      const icon = validation.level === "error" ? "error" : "warning";
                      return (
                        <div className={`flex items-start gap-1.5 mt-1.5 px-2 py-1.5 rounded-md border text-[11px] leading-tight ${toneClass}`}>
                          <span className="material-symbols-outlined text-[12px] shrink-0 mt-0.5" aria-hidden="true">{icon}</span>
                          <span>{validation.message}</span>
                        </div>
                      );
                    })()}
                    <div className="w-full bg-surface-variant rounded-full h-2 overflow-hidden mt-2">
                      <div className={`h-full rounded-full transition-all duration-500 ${group.id === "shifts" ? "bg-blue-500" : group.id === "thresholds" ? "bg-blue-500" : group.id === "weights" ? "bg-teal-500" : group.id === "limits" ? "bg-indigo-500" : group.id === "recovery" ? "bg-rose-500" : "bg-blue-500"}`}
                        style={{ width: `${pct}%` }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ))}

        {/* Auto-compensation toggle */}
        <div className={`bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 border-l-4 border-l-teal-500`}>
          <div className="px-5 py-4 bg-surface-container-low flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-teal-50 text-teal-600">
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">event_available</span>
            </div>
            <p className="text-label-md font-semibold text-on-surface tracking-tight">Nghỉ bù tự động</p>
          </div>
          <div className="p-5 flex items-center justify-between gap-4">
            <div className="flex-1">
              <p className="text-label-sm text-on-surface font-medium">Tạo ngày nghỉ bù</p>
              <p className="text-[11px] text-on-surface-variant mt-0.5">Tự động tạo sau ca trực 24/24</p>
            </div>
            {editing ? (
              <button type="button" role="switch" aria-checked={form.autoCompensationEnabled}
                onClick={() => setForm(f => f ? { ...f, autoCompensationEnabled: !f.autoCompensationEnabled } : f)}
                className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${form.autoCompensationEnabled ? "bg-teal-500 border-teal-500" : "bg-surface-container-high border-outline"}`}>
                <span className={`inline-block h-5 w-5 transform rounded-full bg-white shadow-sm transition-transform ${form.autoCompensationEnabled ? "translate-x-6" : "translate-x-1"}`} />
              </button>
            ) : (
              <span className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-label-sm font-semibold ${form.autoCompensationEnabled ? "bg-teal-50 text-teal-700 border border-teal-200" : "bg-surface-container-high text-outline"}`}>
                <span className={`h-2 w-2 rounded-full ${form.autoCompensationEnabled ? "bg-teal-500" : "bg-outline"}`} />
                {form.autoCompensationEnabled ? "Bật" : "Tắt"}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Shift-type limit cards */}
      <div>
        <div className="flex items-center gap-2 mb-3">
          <span className="material-symbols-outlined text-on-surface-variant text-[16px]" aria-hidden="true">calendar_view_month</span>
          <p className="text-label-sm font-medium text-on-surface-variant">Giới hạn theo loại lịch</p>
        </div>
        <div className="flex flex-wrap gap-3">
          {SHIFT_TYPE_GROUPS.map(group => (
            <ShiftTypeGroupCard
              key={group.id}
              group={group}
              form={form}
              editing={editing}
              onChange={(key, val) => setForm(f => f ? { ...f, [key]: val } : f)}
            />
          ))}
        </div>
      </div>

      {/* Feature A: Config Diff Modal */}
      {showDiff && config && form && (() => {
        const changes = (Object.keys(form) as (keyof RuntimeConfig)[]).filter(k =>
          JSON.stringify(config[k]) !== JSON.stringify(form[k])
        );
        return (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
            <div className="absolute inset-0 bg-black/40" onClick={() => setShowDiff(false)} aria-hidden="true" />
            <div className="relative w-full max-w-2xl max-h-[80vh] rounded-2xl border border-outline-variant bg-surface-container-lowest shadow-2xl flex flex-col overflow-hidden">
              <div className="px-6 py-4 border-b border-outline-variant bg-surface-container-low flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-tertiary-container text-tertiary">
                    <span className="material-symbols-outlined text-[18px]">difference</span>
                  </span>
                  <div>
                    <h2 className="text-title-md font-semibold text-on-surface">So sánh thay đổi</h2>
                    <p className="text-label-xs text-on-surface-variant">{changes.length} thông số đã thay đổi</p>
                  </div>
                </div>
                <IconButton
                  label="Đóng"
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowDiff(false)}
                  className="text-on-surface-variant"
                >
                  <span className="material-symbols-outlined text-[18px]" aria-hidden="true">close</span>
                </IconButton>
              </div>
              <div className="flex-1 overflow-y-auto p-4">
                {changes.length === 0 ? (
                  <p className="text-center text-on-surface-variant py-8">Không có thay đổi</p>
                ) : (
                  <table className="w-full text-left">
                    <thead className="bg-surface-container-low sticky top-0">
                      <tr>
                        <th className="px-3 py-2 text-[11px] font-semibold uppercase text-on-surface-variant">Thông số</th>
                        <th className="px-3 py-2 text-[11px] font-semibold uppercase text-on-surface-variant">Giá trị cũ</th>
                        <th className="px-3 py-2 text-[11px] font-semibold uppercase text-on-surface-variant">Giá trị mới</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-outline-variant/50">
                      {changes.map(key => (
                        <tr key={key} className="hover:bg-surface-container-low transition-colors">
                          <td className="px-3 py-2.5">
                            <code className="font-mono text-[12px] font-semibold text-primary">{key}</code>
                          </td>
                          <td className="px-3 py-2.5">
                            <span className="inline-block px-2 py-1 rounded-md bg-error-container/30 text-error line-through font-mono text-[12px] tabular-nums">
                              {String(config[key as keyof RuntimeConfig])}
                            </span>
                          </td>
                          <td className="px-3 py-2.5">
                            <span className="inline-block px-2 py-1 rounded-md bg-secondary-container/30 text-secondary font-mono text-[12px] tabular-nums font-bold">
                              {String(form[key as keyof RuntimeConfig])}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
              <div className="px-6 py-3 border-t border-outline-variant bg-surface-container-low flex justify-end gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setShowDiff(false)}
                >
                  Đóng
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => { setShowDiff(false); handleSave(); }}
                >
                  Áp dụng thay đổi
                </Button>
              </div>
            </div>
          </div>
        );
      })()}

      {/* Feature C: Preset sandbox modal */}
      <PresetSandboxModal
        open={sandboxOpen}
        onClose={() => setSandboxOpen(false)}
        presets={ALGORITHM_PRESETS as unknown as Record<string, PresetEntry>}
        currentConfig={(form ?? (config as RuntimeConfig)) as unknown as Record<string, number | boolean | string>}
        onApply={(preset) => {
          applyPreset(preset.key as PresetKey);
        }}
      />
    </div>
  );
}

/* ─── Metrics History ─────────────────────────────────────── */

function MetricsHistory() {
  const [metrics, setMetrics] = useState<AlgorithmMetrics[]>([]);
  const [loading, setLoading] = useState(true);
  const [historyFilter, setHistoryFilter] = useState("");
  const [algoFilter, setAlgoFilter] = useState<"ALL" | "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING" | "GENETIC" | "CSP_MRV_FC">("ALL");
  const [coverageFilter, setCoverageFilter] = useState<"ALL" | "high" | "medium" | "low">("ALL");

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.getAllMetrics();
      setMetrics(((res as { data?: unknown[] })?.data ?? []) as AlgorithmMetrics[]);
    } catch {
      setMetrics([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const filteredMetrics = metrics.filter(m => {
    if (algoFilter !== "ALL" && m.algorithmType !== algoFilter) return false;
    const coverage = parseNumber(m.coverageRate);
    if (coverageFilter === "high" && coverage < 90) return false;
    if (coverageFilter === "medium" && (coverage < 70 || coverage >= 90)) return false;
    if (coverageFilter === "low" && coverage >= 70) return false;
    if (historyFilter.trim()) {
      const kw = historyFilter.toLowerCase();
      return m.algorithmType.toLowerCase().includes(kw) || (m.periodName?.toLowerCase().includes(kw) ?? false);
    }
    return true;
  }).slice(0, 20);

  if (loading) {
    return (
      <div className="space-y-2">
        {[1, 2, 3].map(i => <div key={i} className="h-12 bg-surface-container-low rounded-xl animate-pulse" />)}
      </div>
    );
  }

  if (metrics.length === 0) {
    return (
      <EmptyState
        icon="history"
        title="Chưa có lần chạy nào"
        description="Chạy thuật toán để xem lịch sử tại đây"
        className="rounded-xl border border-dashed border-outline-variant bg-surface-container-lowest"
      />
    );
  }

  return (
    <div className="space-y-3">
      {/* Filter bar */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-3 flex items-center gap-2 flex-wrap">
        <div className="relative flex-1 min-w-[180px]">
          <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px]" aria-hidden="true">search</span>
          <input
            className="w-full h-8 pl-8 pr-3 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all"
            placeholder="Tìm theo thuật toán hoặc kỳ..."
            value={historyFilter}
            onChange={e => setHistoryFilter(e.target.value)}
          />
        </div>
        <div className="relative">
          <select
            className="h-8 pl-2.5 pr-7 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer transition-all"
            value={algoFilter}
            onChange={e => setAlgoFilter(e.target.value as typeof algoFilter)}
          >
            <option value="ALL">Tất cả thuật toán</option>
            <option value="GREEDY">Greedy</option>
            <option value="ROUND_ROBIN">Round Robin</option>
            <option value="BACKTRACKING">Backtracking</option>
            <option value="GENETIC">Di truyền</option>
            <option value="CSP_MRV_FC">CSP-MRV-FC</option>
          </select>
          <span className="material-symbols-outlined absolute right-1.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px] pointer-events-none" aria-hidden="true">expand_more</span>
        </div>
        <div className="relative">
          <select
            className="h-8 pl-2.5 pr-7 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer transition-all"
            value={coverageFilter}
            onChange={e => setCoverageFilter(e.target.value as typeof coverageFilter)}
          >
            <option value="ALL">Tất cả phủ lịch</option>
            <option value="high">≥ 90% (Tốt)</option>
            <option value="medium">70-90% (Trung bình)</option>
            <option value="low">&lt; 70% (Thấp)</option>
          </select>
          <span className="material-symbols-outlined absolute right-1.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px] pointer-events-none" aria-hidden="true">expand_more</span>
        </div>
        <span className="text-[11px] text-on-surface-variant ml-auto">{filteredMetrics.length}/{metrics.length} kết quả</span>
      </div>

      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left">
          <thead>
            <tr className="bg-surface-container-low border-b border-outline-variant">
              {["Thuật toán", "Tổng ca", "Phủ lịch", "Cân bằng", "Xung đột", "Thời gian", "Ngày chạy", "Chi tiết"].map((h, i) => (
                <th key={h} scope="col" className={`px-3 py-2.5 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant ${[1, 2, 3, 4, 5].includes(i) ? "text-right" : ""}`}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant/50">
            {filteredMetrics.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center">
                  <p className="text-label-sm text-on-surface-variant">Không có kết quả phù hợp với bộ lọc</p>
                </td>
              </tr>
            ) : filteredMetrics.map(m => (
              <tr key={m.id} className="hover:bg-surface-container-low transition-colors">
                <td className="px-3 py-2.5">
                  <div className="flex items-center gap-2">
                    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-primary-fixed text-primary">
                      <span className="material-symbols-outlined text-[14px]" aria-hidden="true">auto_mode</span>
                    </span>
                    <div>
                      <span className="text-label-sm font-semibold text-on-surface">{m.algorithmType}</span>
                      {m.periodName && <p className="text-[10px] text-on-surface-variant">{m.periodName}</p>}
                    </div>
                  </div>
                </td>
                <td className="px-3 py-2.5 text-right">
                  <span className="font-label-sm font-semibold text-on-surface tabular-nums">
                    {m.totalSchedulesCreated ?? 0}
                  </span>
                </td>
                <td className="px-3 py-2.5 text-right">
                  <div className="flex items-center justify-end gap-1.5">
                    <div className="w-12 bg-surface-variant rounded-full h-1">
                      <div className={`h-1 rounded-full transition-all duration-500 ${parseNumber(m.coverageRate) >= 90 ? "bg-blue-500" : parseNumber(m.coverageRate) >= 70 ? "bg-amber-500" : "bg-red-500"}`}
                        style={{ width: `${Math.min(100, parseNumber(m.coverageRate))}%` }} />
                    </div>
                    <span className={`text-label-xs font-semibold w-9 text-right tabular-nums ${parseNumber(m.coverageRate) >= 90 ? "text-blue-600" : parseNumber(m.coverageRate) >= 70 ? "text-amber-600" : "text-red-600"}`}>
                      {Math.round(parseNumber(m.coverageRate))}%
                    </span>
                  </div>
                </td>
                <td className="px-3 py-2.5 text-right">
                  <div className="flex items-center justify-end gap-1.5">
                    <div className="w-12 bg-surface-variant rounded-full h-1">
                      <div className={`h-1 rounded-full transition-all duration-500 ${parseNumber(m.balanceScore) >= 75 ? "bg-blue-500" : parseNumber(m.balanceScore) >= 50 ? "bg-amber-500" : "bg-red-500"}`}
                        style={{ width: `${Math.min(100, parseNumber(m.balanceScore))}%` }} />
                    </div>
                    <span className={`text-label-xs font-semibold w-9 text-right tabular-nums ${parseNumber(m.balanceScore) >= 75 ? "text-blue-600" : parseNumber(m.balanceScore) >= 50 ? "text-amber-600" : "text-red-600"}`}>
                      {Math.round(parseNumber(m.balanceScore))}%
                    </span>
                  </div>
                </td>
                <td className="px-3 py-2.5 text-right">
                  <span className={`inline-flex items-center gap-1 text-label-xs font-semibold tabular-nums ${m.conflictCount === 0 ? "text-blue-600" : "text-red-600"}`}>
                    {m.conflictCount > 0 && <span className="material-symbols-outlined text-[10px]" aria-hidden="true">warning</span>}
                    {m.conflictCount}
                  </span>
                </td>
                <td className="px-3 py-2.5 text-right text-label-xs text-on-surface-variant tabular-nums">
                  {m.executionTimeMs < 1000 ? `${m.executionTimeMs}ms` : `${(m.executionTimeMs / 1000).toFixed(1)}s`}
                </td>
                <td className="px-3 py-2.5 text-label-xs text-on-surface-variant whitespace-nowrap">
                  {new Date(m.createdAt).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}
                </td>
                <td className="px-3 py-2.5">
                  <button className="h-7 w-7 flex items-center justify-center rounded-lg hover:bg-surface-container-low active:scale-95 transition-all cursor-pointer"
                    title="Xem chi tiết" aria-label="Xem chi tiết">
                    <span className="material-symbols-outlined text-[16px] text-on-surface-variant" aria-hidden="true">visibility</span>
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
    </div>
  );
}

/* ─── Reference Section ────────────────────────────────────── */

function ReferenceSection() {
  const items = [
    { key: "max_iterations", icon: "loop", color: "text-primary", bg: "bg-primary-fixed",
      desc: "Số vòng lặp tối đa Backtracking. Tăng → lời giải tốt hơn nhưng chậm hơn.", range: "100–10,000" },
    { key: "weekend_weight", icon: "weekend", color: "text-secondary", bg: "bg-secondary-container",
      desc: "Hệ số nhân penalty T7/CN. >1 ưu tiên tránh cuối tuần. Đặt=1 để tắt.", range: "1.0–5.0" },
    { key: "greedy_coverage_threshold", icon: "radio_button_checked", color: "text-primary", bg: "bg-primary-fixed",
      desc: "Greedy dừng sớm khi đạt ngưỡng. Giảm → chạy nhanh. Tăng → phủ kỹ hơn.", range: "50%–100%" },
    { key: "balance_score_min", icon: "balance", color: "text-secondary", bg: "bg-secondary-container",
      desc: "Ngưỡng cân bằng tải tối thiểu. Cao → phân bổ công bằng hơn nhưng khó đạt.", range: "30%–100%" },
    { key: "overnight_recovery_hours", icon: "hotel", color: "text-error", bg: "bg-error-container",
      desc: "Số giờ nghỉ bắt buộc giữa hai ca trực 24/24.", range: "12–72 giờ" },
    { key: "backtrack_time_limit_seconds", icon: "timer", color: "text-tertiary", bg: "bg-tertiary-container",
      desc: "Giới hạn thời gian Backtracking. Hết thời gian → dừng, trả kết quả tốt nhất.", range: "10–300 giây" },
  ];

  const algos = [
    { name: "GREEDY", speed: "Rất nhanh", quality: "Tốt", best: "Phủ lịch nhanh, dữ liệu lớn" },
    { name: "ROUND_ROBIN", speed: "Nhanh", quality: "Trung bình", best: "Chia đều tải, nhanh hơn Backtrack" },
    { name: "BACKTRACKING", speed: "Chậm", quality: "Tối ưu", best: "Tìm lời giải tốt nhất, kỳ nhỏ" },
    { name: "CSP_MRV_FC", speed: "Trung bình", quality: "Tối ưu", best: "CSP + MRV + Forward Checking — fallback an toàn cho kỳ over-constrained" },
  ];

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">
        {items.map(item => (
          <div key={item.key} className="bg-surface-container-lowest rounded-2xl border border-outline-variant p-4 flex gap-3 hover:shadow-sm transition-shadow duration-200">
            <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${item.bg} ${item.color}`}>
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{item.icon}</span>
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1 flex-wrap">
                <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded">{item.key}</code>
                <span className="text-[10px] font-semibold bg-surface-container-low text-on-surface-variant px-1.5 py-0.5 rounded border border-outline-variant/30">{item.range}</span>
              </div>
              <p className="text-[11px] text-on-surface-variant leading-relaxed line-clamp-2">{item.desc}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden">
        <div className="px-5 py-3 bg-surface-container-low border-b border-outline-variant">
          <p className="text-label-sm font-semibold text-on-surface">So sánh thuật toán</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="bg-surface-container-low border-b border-outline-variant">
                {["Thuật toán", "Tốc độ", "Chất lượng", "Phù hợp"].map(h => (
                  <th key={h} scope="col" className="px-4 py-2.5 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant text-left">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/50">
              {algos.map(row => (
                <tr key={row.name} className="hover:bg-surface-container-low transition-colors">
                  <td className="px-4 py-3"><span className="text-label-sm font-semibold text-on-surface">{row.name}</span></td>
                  <td className="px-4 py-3 text-label-xs text-on-surface-variant">{row.speed}</td>
                  <td className="px-4 py-3 text-label-xs text-on-surface-variant">{row.quality}</td>
                  <td className="px-4 py-3 text-label-xs text-on-surface-variant">{row.best}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

/* ─── Config Value Cell ─────────────────────────────── */

const VALUE_PRESETS: Record<string, { label: string; value: string }[]> = {
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
    { label: "BACKTRACKING — Tìm lời giải tối ưu, chạy chậm hơn", value: "BACKTRACKING" },
    { label: "BALANCED — Cân bằng tải, tốc độ trung bình", value: "BALANCED" },
    { label: "MINIMAL_CHANGE — Giữ nguyên lịch hiện tại, thay đổi ít nhất", value: "MINIMAL_CHANGE" },
  ],
  JSON: [
    { label: "Mặc định {}", value: "{}" }, { label: "Mảng rỗng []", value: "[]" },
    { label: "{\"enabled\": true}", value: '{"enabled": true}' },
    { label: "{\"strict\": false}", value: '{"strict": false}' },
  ],
};

function ConfigValueCell({ config }: { config: ConfigEntry }) {
  const { error: toastError } = useToast();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(config.paramValue);
  const [saving, setSaving] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const presets = VALUE_PRESETS[config.valueType] ?? [];

  useEffect(() => { setValue(config.paramValue); }, [config.paramValue]);

  useEffect(() => {
    if (editing && presets.length > 0) setShowDropdown(true);
  }, [editing, presets.length]);

  const handleSave = async () => {
    if (value === config.paramValue) { setEditing(false); return; }
    setSaving(true);
    try {
      await api.updateAlgorithmConfig(config.paramKey, { paramValue: value, description: config.description });
      setEditing(false);
    } catch (err) {
      toastError(getErrorMessage(err, "Lưu thất bại"));
      setValue(config.paramValue);
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
            onKeyDown={e => { if (e.key === "Enter") handleSave(); if (e.key === "Escape") { setEditing(false); setValue(config.paramValue); } }}
            onBlur={() => setTimeout(() => setShowDropdown(false), 150)}
            autoFocus />
          {presets.length > 0 && showDropdown && (
            <div className="absolute z-50 mt-1 w-full bg-surface-container-lowest border border-outline-variant rounded-lg shadow-lg max-h-40 overflow-y-auto">
              {presets.map(p => (
                <div key={p.value}
                  className="px-2.5 py-1.5 text-[11px] font-mono cursor-pointer hover:bg-surface-container-low active:scale-[0.98] transition-colors"
                  onMouseDown={(e) => { e.preventDefault(); setValue(p.value); setShowDropdown(false); }}>
                  {p.label}
                </div>
              ))}
            </div>
          )}
          <span className="material-symbols-outlined absolute right-1.5 top-1/2 -translate-y-1/2 text-[12px] text-outline pointer-events-none" aria-hidden="true">expand_more</span>
        </div>
        <IconButton
          label="Lưu"
          variant="primary"
          size="sm"
          disabled={saving}
          loading={saving}
          onClick={handleSave}
          className="text-white"
        >
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">check</span>
        </IconButton>
        <IconButton
          label="Hủy"
          variant="ghost"
          size="sm"
          onClick={() => { setEditing(false); setValue(config.paramValue); }}
          className="border border-outline-variant text-on-surface"
        >
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">close</span>
        </IconButton>
      </div>
    );
  }

  return (
    <button onClick={() => setEditing(true)}
      className="group/val flex items-center gap-1 cursor-pointer" title="Click để sửa">
      <span className="font-mono text-[12px] text-on-surface bg-surface-container-low px-2.5 py-0.5 rounded-lg border border-transparent group-hover/val:border-primary transition-colors max-w-[180px] truncate block tabular-nums">
        {config.paramValue}
      </span>
      <span className="material-symbols-outlined text-[12px] text-outline opacity-0 group-hover/val:opacity-100 transition-opacity" aria-hidden="true">edit</span>
    </button>
  );
}

/* ─── Config Row Inline Actions ─────────────────── */

function ConfigRowInline({ config, onSave, onDelete }: {
  config: ConfigEntry;
  onSave: (updated: EditingConfig) => void;
  onDelete: () => void;
}) {
  const { error: toastError } = useToast();
  const [editingDesc, setEditingDesc] = useState(false);
  const [desc, setDesc] = useState(config.description);
  const [saving, setSaving] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => { setDesc(config.description); }, [config.description]);

  const handleSaveDesc = async () => {
    if (desc === config.description) { setEditingDesc(false); return; }
    setSaving(true);
    try {
      await api.updateAlgorithmConfig(config.paramKey, { paramValue: config.paramValue, description: desc });
      onSave({ paramValue: config.paramValue, description: desc });
      setEditingDesc(false);
    } catch (err) {
      toastError(getErrorMessage(err, "Lưu thất bại"));
    } finally { setSaving(false); }
  };

  const handleDelete = async () => {
    try { await api.deleteAlgorithmConfig(config.paramKey); onDelete(); }
    catch (err) { toastError(getErrorMessage(err, "Xóa thất bại")); }
  };

  if (editingDesc) {
    return (
      <div className="flex items-center gap-1">
        <input className="h-7 w-40 rounded-lg border border-primary bg-surface px-2.5 text-[11px] text-on-surface focus:outline-none focus:ring-1 focus:ring-primary/20"
          value={desc} onChange={e => setDesc(e.target.value)} placeholder="Mô tả..." autoFocus />
        <IconButton
          label="Lưu mô tả"
          variant="primary"
          size="sm"
          disabled={saving}
          loading={saving}
          onClick={handleSaveDesc}
          className="text-white"
        >
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">check</span>
        </IconButton>
        <IconButton
          label="Hủy"
          variant="ghost"
          size="sm"
          onClick={() => { setEditingDesc(false); setDesc(config.description); }}
          className="border border-outline-variant text-on-surface"
        >
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">close</span>
        </IconButton>
      </div>
    );
  }

  return (
    <>
      <IconButton
        label="Sửa mô tả"
        variant="ghost"
        size="sm"
        onClick={() => setEditingDesc(true)}
        className="text-on-surface-variant"
      >
        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">edit_note</span>
      </IconButton>
      <IconButton
        label="Xóa"
        variant="ghost"
        size="sm"
        onClick={() => setConfirmOpen(true)}
        className="text-error hover:bg-error-container"
      >
        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete</span>
      </IconButton>
      {confirmOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-black/40" onClick={() => setConfirmOpen(false)} aria-hidden="true" />
          <div className="relative w-full max-w-sm rounded-2xl border border-outline-variant bg-surface-container-lowest shadow-2xl p-5 space-y-3">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-error-container text-error">
                <span className="material-symbols-outlined text-[20px]" aria-hidden="true">warning</span>
              </div>
              <div>
                <h3 className="text-label-md font-semibold text-on-surface">Xóa cấu hình?</h3>
                <code className="text-[11px] text-primary font-mono">{config.paramKey}</code>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setConfirmOpen(false)}
              >
                Hủy
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={() => { handleDelete(); setConfirmOpen(false); }}
              >
                Xóa
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

/* ─── Create Config Modal ─────────────────────────────── */

function CreateConfigModal({ open, onClose, onCreate, creating, message }: {
  open: boolean;
  onClose: () => void;
  onCreate: (form: { paramKey: string; paramValue: string; valueType: string; description: string }) => Promise<void>;
  creating: boolean;
  message?: { type: "success" | "error"; text: string } | null;
}) {
  const [form, setForm] = useState({ paramKey: "", paramValue: "", valueType: "STRING", description: "" });
  const VALUE_TYPES = [
    { value: "STRING", label: "Chuỗi" },
    { value: "NUMBER", label: "Số" },
    { value: "BOOLEAN", label: "Đúng/Sai" },
    { value: "JSON", label: "JSON" },
  ];
  const PRESET_PARAMS = [
    { label: "Chọn thông số...", value: "" },
    { label: "max_iterations — Số vòng lặp tối đa", value: "max_iterations" },
    { label: "weekend_weight — Hệ số nhân cuối tuần", value: "weekend_weight" },
    { label: "greedy_coverage_threshold — Ngưỡng phủ lịch (Greedy)", value: "greedy_coverage_threshold" },
    { label: "balance_score_min — Ngưỡng cân bằng tải", value: "balance_score_min" },
    { label: "overnight_recovery_hours — Giờ nghỉ giữa các ca", value: "overnight_recovery_hours" },
    { label: "backtrack_time_limit_seconds — Giới hạn thời gian Backtrack", value: "backtrack_time_limit_seconds" },
    { label: "staff_preference_weight — Trọng số ưu tiên nhân sự", value: "staff_preference_weight" },
    { label: "specialty_match_weight — Trọng số chuyên khoa", value: "specialty_match_weight" },
    { label: "min_staff_per_shift — Tối thiểu nhân sự/ca", value: "min_staff_per_shift" },
    { label: "max_staff_per_shift — Tối đa nhân sự/ca", value: "max_staff_per_shift" },
    { label: "conflict_penalty — Điểm phạt xung đột", value: "conflict_penalty" },
    { label: "timeout_seconds — Thời gian chờ tối đa", value: "timeout_seconds" },
  ];
  const PRESET_VALUES: Record<string, { value: string; type: string; description: string }> = {
    max_iterations: { value: "1000", type: "NUMBER", description: "Số vòng lặp tối đa của thuật toán Backtracking" },
    weekend_weight: { value: "2.0", type: "NUMBER", description: "Hệ số nhân penalty cuối tuần (T7/CN)" },
    greedy_coverage_threshold: { value: "0.85", type: "NUMBER", description: "Ngưỡng phủ lịch để Greedy dừng sớm" },
    balance_score_min: { value: "0.70", type: "NUMBER", description: "Ngưỡng cân bằng tải tối thiểu" },
    overnight_recovery_hours: { value: "24", type: "NUMBER", description: "Số giờ nghỉ bắt buộc giữa hai ca trực 24/24" },
    backtrack_time_limit_seconds: { value: "60", type: "NUMBER", description: "Giới hạn thời gian chạy Backtracking (giây)" },
    staff_preference_weight: { value: "1.5", type: "NUMBER", description: "Trọng số cho sở thích ca trực của nhân sự" },
    specialty_match_weight: { value: "2.0", type: "NUMBER", description: "Trọng số cho việc khớp chuyên khoa" },
    min_staff_per_shift: { value: "1", type: "NUMBER", description: "Số nhân sự tối thiểu cần thiết mỗi ca" },
    max_staff_per_shift: { value: "5", type: "NUMBER", description: "Số nhân sự tối đa mỗi ca" },
    conflict_penalty: { value: "100", type: "NUMBER", description: "Điểm phạt khi phát hiện xung đột lịch" },
    timeout_seconds: { value: "300", type: "NUMBER", description: "Thời gian chờ tối đa cho mỗi lần chạy (giây)" },
  };

  useEffect(() => {
    if (open) setForm({ paramKey: "", paramValue: "", valueType: "STRING", description: "" });
  }, [open]);

  const handlePresetChange = (key: string) => {
    if (!key) { setForm({ paramKey: "", paramValue: "", valueType: "STRING", description: "" }); return; }
    const preset = PRESET_VALUES[key];
    if (preset) {
      setForm({ paramKey: key, paramValue: preset.value, valueType: preset.type, description: preset.description });
    } else {
      setForm(f => ({ ...f, paramKey: key }));
    }
  };

  if (!open) return null;

  const handleSubmit = async () => {
    if (!form.paramKey.trim() || !form.paramValue.trim()) return;
    await onCreate(form);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} aria-hidden="true" />
      <div className="relative w-full max-w-md rounded-2xl border border-outline-variant bg-surface-container-lowest shadow-2xl overflow-hidden">
        <div className="px-6 py-5 border-b border-outline-variant bg-surface-container-low flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary-fixed text-primary">
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">add</span>
            </div>
            <div>
              <h2 className="text-title-md font-semibold text-on-surface">Thêm cấu hình mới</h2>
              <p className="text-label-xs text-on-surface-variant">Tạo thông số vận hành cho thuật toán</p>
            </div>
          </div>
          <IconButton
            label="Đóng"
            variant="ghost"
            size="sm"
            onClick={onClose}
            className="text-on-surface-variant"
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">close</span>
          </IconButton>
        </div>

        <div className="p-6 space-y-4">
          <div>
            <FormSelect
              id="cfg-key"
              label="Tên thông số"
              required
              value={form.paramKey}
              onChange={(e) => handlePresetChange(e.target.value)}
              options={PRESET_PARAMS.map((p) => ({ value: p.value, label: p.label }))}
              className="!font-mono !text-label-md"
            />
            <p className="text-[11px] text-outline mt-1">Chọn từ danh sách hoặc nhập tên tùy ý</p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <FormSelect
              id="cfg-type"
              label="Kiểu dữ liệu"
              value={form.valueType}
              onChange={(e) => setForm((f) => ({ ...f, valueType: e.target.value }))}
              options={VALUE_TYPES.map((t) => ({ value: t.value, label: `${t.label} (${t.value})` }))}
              className="!text-label-md"
            />
            <div>
              <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-value">Giá trị <span className="text-error">*</span></label>
              <input id="cfg-value"
                className="h-10 w-full rounded-xl border border-outline-variant bg-surface-container-low px-3 text-label-md font-mono text-on-surface transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                placeholder="VD: 1000, true, 2.5" value={form.paramValue}
                onChange={e => setForm(f => ({ ...f, paramValue: e.target.value }))} />
            </div>
          </div>

          <div>
            <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-desc">Mô tả</label>
            <textarea id="cfg-desc"
              className="w-full resize-none rounded-xl border border-outline-variant bg-surface-container-low px-3 py-2.5 text-label-md text-on-surface transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              rows={2} placeholder="Giải thích thông số này dùng để làm gì..."
              value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
          </div>

          {message && (
            <div className={`rounded-lg px-4 py-3 text-label-sm ${message.type === "success" ? "bg-secondary-container text-secondary" : "bg-error-container text-error"}`}>
              {message.text}
            </div>
          )}
        </div>

        <div className="px-6 py-4 border-t border-outline-variant bg-surface-container-low flex justify-end gap-2">
          <Button
            variant="secondary"
            size="md"
            onClick={onClose}
          >
            Hủy
          </Button>
          <Button
            variant="primary"
            size="md"
            onClick={handleSubmit}
            disabled={!form.paramKey.trim() || !form.paramValue.trim() || creating}
            loading={creating}
            icon={!creating ? <span className="material-symbols-outlined text-[16px]" aria-hidden="true">add</span> : undefined}
          >
            Tạo cấu hình
          </Button>
        </div>
      </div>
    </div>
  );
}

/* ─── Main Page ────────────────────────────────────────── */

export default function AlgorithmConfigPage() {
  const role = useRole();
  const isAdmin = role === "ADMIN";
  const { success, error: toastError } = useToast();

  const [activeTab, setActiveTab] = useState<TabKey>("config");
  const [configs, setConfigs] = useState<ConfigEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createMsg, setCreateMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [filter, setFilter] = useState("");
  const [filterType, setFilterType] = useState("ALL");
  const [sortBy, setSortBy] = useState<"key" | "updatedAt">("key");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");

  const loadConfigs = useCallback(async () => {
    let ignore = false;
    setLoading(true);
    api.getAllAlgorithmConfigs()
      .then(data => { if (!ignore) setConfigs((data as ApiResponse<ConfigEntry[]>)?.data ?? []); })
      .catch(() => { if (!ignore) setConfigs([]); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, []);

  useEffect(() => { void loadConfigs(); }, [loadConfigs]);

  const handleCreate = async (form: { paramKey: string; paramValue: string; valueType: string; description: string }) => {
    setCreating(true);
    try {
      await api.createAlgorithmConfig({ paramKey: form.paramKey.trim(), paramValue: form.paramValue.trim(), valueType: form.valueType, description: form.description.trim() });
      setCreateMsg({ type: "success", text: "Đã tạo cấu hình mới." });
      await loadConfigs();
    } catch (err) {
      setCreateMsg({ type: "error", text: getErrorMessage(err, "Tạo thất bại.") });
    } finally {
      setCreating(false);
    }
  };

  const [syncingDesc, setSyncingDesc] = useState(false);
  const [syncConfirmOpen, setSyncConfirmOpen] = useState(false);
  const handleSyncDescriptions = useCallback(async () => {
    setSyncingDesc(true);
    try {
      await api.syncAlgorithmConfigDescriptions();
      success("Đã đồng bộ mô tả. Đang tải lại danh sách...");
      await loadConfigs();
    } catch (e) {
      toastError(getErrorMessage(e, "Đồng bộ mô tả thất bại"));
    } finally {
      setSyncingDesc(false);
    }
  }, [toastError, loadConfigs, success]);

  const LEGACY_AUTO_GEN_KEYS = new Set([
    "auto_generate_requirements",
    "auto_gen_holiday_mode",
    "auto_gen_l01_per_day",
    "auto_gen_l02_per_day",
    "auto_gen_l03_per_day",
    "auto_gen_l04_per_day",
    "auto_gen_l01_per_week",
    "auto_gen_l02_per_week",
    "auto_gen_l03_per_week",
    "auto_gen_l04_per_week",
  ]);

  const filteredConfigs = configs.filter(c => {
    if (LEGACY_AUTO_GEN_KEYS.has(c.paramKey)) return false;
    if (filterType !== "ALL" && c.valueType !== filterType) return false;
    if (filter.trim()) {
      const kw = filter.toLowerCase();
      return c.paramKey.toLowerCase().includes(kw) || c.description.toLowerCase().includes(kw);
    }
    return true;
  }).sort((a, b) => {
    const dir = sortDir === "asc" ? 1 : -1;
    if (sortBy === "key") return dir * a.paramKey.localeCompare(b.paramKey);
    return dir * (new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime());
  });

  if (!isAdmin) {
    return (
      <div className="rounded-2xl border border-tertiary-container bg-tertiary-container/20 p-8 flex flex-col items-center gap-3 text-center">
        <span className="material-symbols-outlined text-tertiary text-[40px]" aria-hidden="true">lock</span>
        <h2 className="text-title-lg font-semibold text-on-surface">Không có quyền truy cập</h2>
        <p className="text-body-sm text-on-surface-variant max-w-md">
          Chỉ <strong>Quản trị viên</strong> mới có quyền quản lý cấu hình thuật toán.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <BackButton href="/auto-scheduling" variant="full" label="Quay lại" className="mb-1" />

      {/* Header */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-headline-lg font-bold text-on-surface tracking-tight">Cấu hình thuật toán</h1>
          <p className="text-label-sm text-on-surface-variant mt-0.5">Thiết lập thông số vận hành cho thuật toán xếp lịch</p>
        </div>
        <div className="flex items-center gap-2.5">
          <TabBar active={activeTab} onChange={(t) => setActiveTab(t as TabKey)} />
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setSyncConfirmOpen(true)}
            disabled={syncingDesc}
            loading={syncingDesc}
            icon={!syncingDesc ? <span className="material-symbols-outlined text-[14px]" aria-hidden="true">sync</span> : undefined}
            title="Cập nhật mô tả các tham số về phiên bản mặc định theo code"
          >
            Đồng bộ
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => setCreateModalOpen(true)}
            icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">add</span>}
          >
            Thêm
          </Button>
        </div>
      </div>

      {/* Tab Content */}
      {activeTab === "config" && (
        <div className="space-y-5">
          {/* Runtime Config Editor Card */}
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden">
            <div className="px-5 py-3.5 border-b border-outline-variant bg-surface-container-low flex items-center gap-2.5">
              <span className="material-symbols-outlined text-primary text-[18px]" aria-hidden="true">tune</span>
              <h2 className="text-title-sm font-semibold text-on-surface">Thông số runtime</h2>
              <span className="text-[11px] text-on-surface-variant ml-auto hidden sm:block">Áp dụng cho mọi kỳ lịch</span>
            </div>
            <div className="p-5">
              <RuntimeConfigEditor onSaved={() => void loadConfigs()} />
            </div>
          </div>

          {/* Custom Configs */}
          <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden">
            <div className="px-5 py-3 border-b border-outline-variant bg-surface-container-low flex items-center justify-between gap-3 flex-wrap">
              <div className="flex items-center gap-2">
                <p className="text-label-sm font-semibold text-on-surface">Cấu hình tùy chỉnh</p>
                <span className="text-[11px] text-on-surface-variant">{configs.length} thông số</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="relative">
                  <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px]" aria-hidden="true">search</span>
                  <input className="h-8 pl-8.5 pr-3 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface w-40 focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all"
                    placeholder="Tìm..." value={filter} onChange={e => setFilter(e.target.value)} />
                </div>
                <div className="relative">
                  <select className="h-8 pl-2.5 pr-7 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer transition-all"
                    value={filterType} onChange={e => setFilterType(e.target.value)}>
                    <option value="ALL">Tất cả</option>
                    <option value="STRING">STRING</option>
                    <option value="NUMBER">NUMBER</option>
                    <option value="BOOLEAN">BOOLEAN</option>
                    <option value="JSON">JSON</option>
                  </select>
                  <span className="material-symbols-outlined absolute right-1.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px] pointer-events-none" aria-hidden="true">expand_more</span>
                </div>
              </div>
            </div>

            {loading ? (
              <div className="p-5 space-y-2">
                {[1, 2, 3].map(i => <div key={i} className="h-10 bg-surface-container-low rounded-lg animate-pulse" />)}
              </div>
            ) : filteredConfigs.length === 0 ? (
              <EmptyState
                icon="tune"
                title={configs.length === 0 ? "Chưa có cấu hình tùy chỉnh" : "Không tìm thấy cấu hình phù hợp"}
                description={configs.length === 0 ? "Tạo cấu hình mới để tùy chỉnh thuật toán" : "Thử thay đổi bộ lọc tìm kiếm"}
                size="compact"
              />
            ) : (
              <div className="overflow-x-auto max-h-[480px] overflow-y-auto">
                <table className="w-full text-left">
                  <thead className="sticky top-0 z-10">
                    <tr className="bg-surface-container-low border-b border-outline-variant shadow-sm">
                      <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant w-8">
                        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">key</span>
                      </th>
                      <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant">
                        <button
                          type="button"
                          onClick={() => { if (sortBy === "key") setSortDir(sortDir === "asc" ? "desc" : "asc"); else { setSortBy("key"); setSortDir("asc"); } }}
                          className="flex items-center gap-1 hover:text-primary transition-colors uppercase cursor-pointer"
                        >
                          Thông số
                          <span className="material-symbols-outlined text-[12px]">
                            {sortBy === "key" ? (sortDir === "asc" ? "arrow_upward" : "arrow_downward") : "unfold_more"}
                          </span>
                        </button>
                      </th>
                      <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant w-20">Kiểu</th>
                      <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant">Giá trị</th>
                      <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant hidden lg:table-cell">Mô tả</th>
                      <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant w-24">
                        <button
                          type="button"
                          onClick={() => { if (sortBy === "updatedAt") setSortDir(sortDir === "asc" ? "desc" : "asc"); else { setSortBy("updatedAt"); setSortDir("desc"); } }}
                          className="flex items-center gap-1 hover:text-primary transition-colors uppercase cursor-pointer"
                        >
                          Cập nhật
                          <span className="material-symbols-outlined text-[12px]">
                            {sortBy === "updatedAt" ? (sortDir === "asc" ? "arrow_upward" : "arrow_downward") : "unfold_more"}
                          </span>
                        </button>
                      </th>
                      <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant w-16 text-right">Hành động</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/50">
                    {filteredConfigs.map(config => (
                      <tr key={config.paramKey} className="hover:bg-surface-container-low transition-colors group">
                        <td className="px-4 py-3">
                          <span className="material-symbols-outlined text-[14px] text-outline" aria-hidden="true">settings</span>
                        </td>
                        <td className="px-4 py-3">
                          <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded">{config.paramKey}</code>
                        </td>
                        <td className="px-4 py-3">
                          <span className={`inline-flex px-1.5 py-0.5 rounded text-label-xs font-semibold uppercase ${
                            config.valueType === "NUMBER" ? "bg-primary-fixed text-primary" :
                            config.valueType === "BOOLEAN" ? "bg-secondary-container text-secondary" :
                            config.valueType === "JSON" ? "bg-tertiary-container text-tertiary" :
                            "bg-surface-container text-on-surface-variant"
                          }`}>
                            {config.valueType}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <ConfigValueCell config={config} />
                        </td>
                        <td className="px-4 py-3 hidden lg:table-cell">
                          <p className="text-[11px] text-on-surface-variant line-clamp-1" title={config.description}>{config.description || "—"}</p>
                        </td>
                        <td className="px-4 py-3">
                          <p className="text-[11px] text-outline">{config.updatedBy || "—"}</p>
                          <p className="text-[10px] text-outline/60">{new Date(config.updatedAt).toLocaleDateString("vi-VN")}</p>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <ConfigRowInline config={config}
                              onSave={updated => setConfigs(prev => prev.map(c => c.paramKey === config.paramKey ? { ...c, ...updated } : c))}
                              onDelete={() => setConfigs(prev => prev.filter(c => c.paramKey !== config.paramKey))} />
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {activeTab === "history" && <MetricsHistory />}
      {activeTab === "audit" && <ConfigAuditLog />}
      {activeTab === "reference" && <ReferenceSection />}

      <CreateConfigModal
        open={createModalOpen}
        onClose={() => { setCreateModalOpen(false); setCreateMsg(null); }}
        onCreate={handleCreate}
        creating={creating}
        message={createMsg}
      />

      <ConfirmDialog
        open={syncConfirmOpen}
        onClose={() => setSyncConfirmOpen(false)}
        onConfirm={() => { setSyncConfirmOpen(false); void handleSyncDescriptions(); }}
        title="Đồng bộ mô tả tham số?"
        description="Hành động này sẽ reset toàn bộ mô tả về phiên bản mặc định trong code. Mô tả tùy chỉnh sẽ bị mất."
        confirmLabel="Đồng bộ"
        cancelLabel="Hủy"
        variant="danger"
        loading={syncingDesc}
      />
    </div>
  );
}
