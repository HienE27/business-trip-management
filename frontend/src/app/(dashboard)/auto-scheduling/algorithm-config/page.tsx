"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useRole } from "@/hooks/useRole";
import { useToast } from "@/hooks/useToast";
import { EmptyState } from "@/components/ui/EmptyState";
import { Badge } from "@/components/ui/Badge";
import { BackButton } from "@/components/ui/BackButton";
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

type PresetKey = "balanced" | "fast" | "quality" | "conservative";

const ALGORITHM_PRESETS: Record<PresetKey, {
  label: string;
  description: string;
  icon: string;
  color: string;
  colorBg: string;
  config: RuntimeConfig;
}> = {
  balanced: {
    label: "Cân bằng",
    description: "Ưu tiên cân bằng tải, tốc độ trung bình",
    icon: "balance",
    color: "text-secondary",
    colorBg: "bg-secondary-container",
    config: { maxIterations: 2000, weekendWeight: 2.5, greedyCoverageThreshold: 0.90, balanceScoreMin: 0.75, autoCompensationEnabled: true, overnightRecoveryHours: 24, backtrackTimeLimitSeconds: 120, minStaffPerShift: 1, maxStaffPerShift: 0, minShiftsPerStaff: 0, maxShiftsPerStaff: 0 },
  },
  fast: {
    label: "Nhanh",
    description: "Chạy nhanh, phủ lịch nhanh (Greedy)",
    icon: "bolt",
    color: "text-tertiary",
    colorBg: "bg-tertiary-container",
    config: { maxIterations: 500, weekendWeight: 1.5, greedyCoverageThreshold: 0.75, balanceScoreMin: 0.60, autoCompensationEnabled: true, overnightRecoveryHours: 24, backtrackTimeLimitSeconds: 30, minStaffPerShift: 1, maxStaffPerShift: 0, minShiftsPerStaff: 0, maxShiftsPerStaff: 0 },
  },
  quality: {
    label: "Chất lượng cao",
    description: "Tìm lời giải tối ưu, chạy chậm hơn",
    icon: "verified",
    color: "text-primary",
    colorBg: "bg-primary-fixed",
    config: { maxIterations: 5000, weekendWeight: 3.0, greedyCoverageThreshold: 0.95, balanceScoreMin: 0.85, autoCompensationEnabled: true, overnightRecoveryHours: 24, backtrackTimeLimitSeconds: 300, minStaffPerShift: 1, maxStaffPerShift: 0, minShiftsPerStaff: 0, maxShiftsPerStaff: 0 },
  },
  conservative: {
    label: "Thận trọng",
    description: "Ít thay đổi, giữ nguyên lịch hiện tại",
    icon: "shield",
    color: "text-outline",
    colorBg: "bg-surface-container-high",
    config: { maxIterations: 1000, weekendWeight: 1.0, greedyCoverageThreshold: 0.60, balanceScoreMin: 0.50, autoCompensationEnabled: true, overnightRecoveryHours: 24, backtrackTimeLimitSeconds: 60, minStaffPerShift: 1, maxStaffPerShift: 0, minShiftsPerStaff: 0, maxShiftsPerStaff: 0 },
  },
};

const PARAM_GROUPS = [
  {
    id: "shifts",
    label: "Số ca/nhân sự",
    icon: "groups",
    color: "text-primary",
    bg: "bg-primary-fixed",
    params: ["min_staff_per_shift", "max_staff_per_shift", "min_shifts_per_staff", "max_shifts_per_staff"] as const,
    descriptions: {
      min_staff_per_shift: { label: "min_staff", unit: " người", desc: "Số nhân sự tối thiểu mỗi ca. Đặt 0 để bỏ qua.", hint: "0–10 · Mặc định: 1" },
      max_staff_per_shift: { label: "max_staff", unit: " người", desc: "Số nhân sự tối đa mỗi ca. 0 = không giới hạn.", hint: "0–20 · Mặc định: 0 (không giới hạn)" },
      min_shifts_per_staff: { label: "min_shifts", unit: " ca", desc: "Số ca trực tối thiểu mỗi nhân sự trong kỳ. 0 = không áp dụng.", hint: "0–50 · Mặc định: 0" },
      max_shifts_per_staff: { label: "max_shifts", unit: " ca", desc: "Số ca trực tối đa mỗi nhân sự trong kỳ. 0 = không giới hạn.", hint: "0–100 · Mặc định: 0 (dùng maxShiftsPerMonth)" },
    },
  },
  {
    id: "thresholds",
    label: "Ngưỡng",
    icon: "radio_button_checked",
    color: "text-primary",
    bg: "bg-primary-fixed",
    params: ["greedy_coverage_threshold", "balance_score_min"] as const,
    descriptions: {
      greedy_coverage_threshold: { label: "greedy_threshold", unit: "%", desc: "Greedy dừng sớm khi đạt ngưỡng. Giảm → chạy nhanh hơn. Tăng → phủ kỹ hơn.", hint: "0.5–1.0 · Mặc định: 0.85" },
      balance_score_min: { label: "balance_score", unit: "%", desc: "Ngưỡng cân bằng tải tối thiểu. Cao →公平 hơn nhưng khó đạt.", hint: "0.3–1.0 · Mặc định: 0.70" },
    },
  },
  {
    id: "weights",
    label: "Trọng số",
    icon: "fitness_center",
    color: "text-secondary",
    bg: "bg-secondary-container",
    params: ["weekend_weight"] as const,
    descriptions: {
      weekend_weight: { label: "weekend_weight", unit: "×", desc: "Hệ số nhân khi tính penalty T7/CN. >1 ưu tiên tránh cuối tuần. Đặt=1 để tắt ưu tiên.", hint: "1.0–5.0 · Mặc định: 2.0" },
    },
  },
  {
    id: "limits",
    label: "Giới hạn",
    icon: "speed",
    color: "text-tertiary",
    bg: "bg-tertiary-container",
    params: ["max_iterations", "backtrack_time_limit_seconds"] as const,
    descriptions: {
      max_iterations: { label: "max_iterations", unit: " lần", desc: "Số vòng lặp tối đa Backtracking. Tăng → lời giải tốt hơn nhưng chậm hơn.", hint: "100–10000 · Mặc định: 1000" },
      backtrack_time_limit_seconds: { label: "time_limit", unit: "s", desc: "Giới hạn thời gian Backtracking (giây). Hết thời gian → dừng và trả kết quả tốt nhất.", hint: "10–300 · Mặc định: 60s" },
    },
  },
  {
    id: "recovery",
    label: "Nghỉ ngơi",
    icon: "hotel",
    color: "text-error",
    bg: "bg-error-container",
    params: ["overnight_recovery_hours"] as const,
    descriptions: {
      overnight_recovery_hours: { label: "recovery_hours", unit: " giờ", desc: "Số giờ nghỉ bắt buộc giữa hai ca trực 24/24. Thường đặt 24 giờ.", hint: "12–72 giờ · Mặc định: 24" },
    },
  },
];

/* ─── Tab System ─────────────────────────────────────────── */

type TabKey = "config" | "history" | "reference";

function TabBar({ active, onChange }: { active: TabKey; onChange: (t: TabKey) => void }) {
  const tabs: { key: TabKey; label: string; icon: string }[] = [
    { key: "config", label: "Cấu hình", icon: "tune" },
    { key: "history", label: "Lịch sử chạy", icon: "history" },
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

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.getRuntimeConfig();
      const data = (res as unknown as { data: RuntimeConfig }).data;
      setConfig(data);
      setForm(data);
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
        cfg.maxStaffPerShift === p.maxStaffPerShift
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
      await api.updateRuntimeConfig(form);
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
          <div className="h-6 w-32 bg-surface-container-low rounded animate-pulse mb-4" />
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            {[1, 2, 3, 4].map(i => <div key={i} className="h-20 bg-surface-container-low rounded-xl animate-pulse" />)}
          </div>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
          {[1, 2, 3, 4, 5].map(i => <div key={i} className="h-40 bg-surface-container-low rounded-xl animate-pulse" />)}
        </div>
      </div>
    );
  }

  if (!config || !form) return null;

  return (
    <div className="space-y-4">
      {/* Presets + Edit toolbar */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
        <div className="flex items-center justify-between gap-4 flex-wrap mb-4">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-[20px]" aria-hidden="true">bookmark</span>
            <p className="text-title-sm font-semibold text-on-surface">Cấu hình nhanh</p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {editing ? (
              <>
                <button onClick={handleReset}
                  className="px-4 py-2 rounded-lg border border-outline-variant text-label-sm font-medium text-on-surface-variant hover:bg-surface-container-low transition-colors cursor-pointer">
                  Hủy bỏ
                </button>
                <button onClick={handleSave} disabled={saving}
                  className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-label-sm font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors cursor-pointer">
                  {saving ? (
                    <><span className="size-4 animate-spin rounded-full border-2 border-on-primary border-t-transparent" /></>
                  ) : (
                    <><span className="material-symbols-outlined text-[16px]" aria-hidden="true">save</span> Lưu thay đổi</>
                  )}
                </button>
              </>
            ) : (
              <button onClick={() => setEditing(true)}
                className="flex items-center gap-2 px-4 py-2 rounded-lg border border-outline-variant text-label-sm font-medium text-on-surface-variant hover:bg-surface-container-low hover:border-primary transition-colors cursor-pointer">
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">edit</span> Chỉnh sửa
              </button>
            )}
          </div>
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          {(Object.entries(ALGORITHM_PRESETS) as [PresetKey, typeof ALGORITHM_PRESETS[PresetKey]][]).map(([key, preset]) => {
            const isActive = activePreset === key;
            return (
              <button key={key} type="button" onClick={() => applyPreset(key)}
                className={`group relative flex items-start gap-3 p-3 rounded-xl border-2 text-left transition-all cursor-pointer ${
                  isActive 
                    ? `border-primary ${preset.colorBg} shadow-sm` 
                    : "border-outline-variant bg-surface-container-low hover:border-primary/50 hover:bg-surface-container-lowest"
                }`}>
                <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${isActive ? preset.colorBg : 'bg-surface-container-high'} transition-colors`}>
                  <span className={`material-symbols-outlined text-[20px] ${isActive ? preset.color : "text-on-surface-variant"}`} aria-hidden="true">{preset.icon}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className={`text-label-md font-semibold ${isActive ? preset.color : "text-on-surface"}`}>{preset.label}</p>
                  <p className="text-[11px] text-on-surface-variant mt-0.5 leading-relaxed line-clamp-2">{preset.description}</p>
                </div>
                {isActive && (
                  <div className="absolute top-2 right-2">
                    <span className="material-symbols-outlined text-primary text-[14px]" aria-hidden="true">check_circle</span>
                  </div>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Parameter groups */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
        {PARAM_GROUPS.map(group => (
          <div key={group.id} className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow">
            <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center gap-2">
              <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${group.bg} ${group.color}`}>
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{group.icon}</span>
              </div>
              <p className="text-label-md font-semibold text-on-surface">{group.label}</p>
            </div>
            <div className="p-4 space-y-4">
              {group.params.map(param => {
                const desc = group.descriptions[param as keyof typeof group.descriptions] ?? { label: param, unit: "", desc: "", hint: "" };
                const cfgKey = param === "greedy_coverage_threshold" ? "greedyCoverageThreshold"
                  : param === "balance_score_min" ? "balanceScoreMin"
                  : param === "backtrack_time_limit_seconds" ? "backtrackTimeLimitSeconds"
                  : param === "weekend_weight" ? "weekendWeight"
                  : param === "overnight_recovery_hours" ? "overnightRecoveryHours"
                  : param === "min_staff_per_shift" ? "minStaffPerShift"
                  : param === "max_staff_per_shift" ? "maxStaffPerShift"
                  : param === "min_shifts_per_staff" ? "minShiftsPerStaff"
                  : param === "max_shifts_per_staff" ? "maxShiftsPerStaff"
                  : "maxIterations" as keyof RuntimeConfig;
                const min = param === "greedy_coverage_threshold" || param === "balance_score_min" ? 0.3 : param === "weekend_weight" ? 1 : 0;
                const max = param === "greedy_coverage_threshold" || param === "balance_score_min" ? 1 : param === "weekend_weight" ? 5 : param === "max_iterations" || param === "min_staff_per_shift" ? 10 : param === "max_staff_per_shift" || param === "max_shifts_per_staff" ? 100 : param === "min_shifts_per_staff" ? 50 : 300;
                const step = param === "greedy_coverage_threshold" || param === "balance_score_min" || param === "weekend_weight" ? 0.05 : 1;
                const numVal = typeof form[cfgKey] === "number" ? form[cfgKey] as number : 0;
                const display = param === "greedy_coverage_threshold" || param === "balance_score_min"
                  ? `${Math.round(numVal * 100)}%`
                  : param === "weekend_weight" ? numVal.toFixed(1) + "×"
                  : param === "backtrack_time_limit_seconds" ? `${numVal}s`
                  : param === "overnight_recovery_hours" ? `${numVal}h`
                  : param === "min_staff_per_shift" || param === "max_staff_per_shift" || param === "min_shifts_per_staff" || param === "max_shifts_per_staff"
                  ? numVal === 0 ? "Tắt" : numVal.toLocaleString()
                  : numVal.toLocaleString();
                const pct = Math.min(100, Math.max(0, ((numVal - min) / (max - min)) * 100));

                return (
                  <div key={param}>
                    <div className="flex items-start justify-between gap-3 mb-2">
                      <div className="flex-1 min-w-0">
                        <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/50 px-1.5 py-0.5 rounded">{desc.label}</code>
                        <p className="text-[11px] text-on-surface-variant mt-1 leading-relaxed">{desc.desc}</p>
                        <p className="text-[10px] text-outline mt-0.5">{desc.hint}</p>
                      </div>
                      {editing ? (
                        <input type="number" step={step} min={min} max={max}
                          className="h-9 w-24 rounded-lg border border-outline-variant bg-surface-container-low px-2 text-label-sm font-mono text-on-surface text-right focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors"
                          value={numVal}
                          onChange={e => setForm(f => f ? { ...f, [cfgKey]: step < 1 ? parseFloat(e.target.value) || 0 : parseInt(e.target.value) || 0 } : f)} />
                      ) : (
                        <span className="font-mono text-xl font-bold text-on-surface shrink-0">{display}</span>
                      )}
                    </div>
                    <div className="w-full bg-surface-variant rounded-full h-2 overflow-hidden">
                      <div className={`h-full rounded-full ${group.color.replace("text-", "bg-")} transition-all duration-300`} style={{ width: `${pct}%` }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ))}

        {/* Auto-compensation toggle */}
        <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow">
          <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center gap-2">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-secondary-container text-secondary">
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">event_available</span>
            </div>
            <p className="text-label-md font-semibold text-on-surface">Nghỉ bù tự động</p>
          </div>
          <div className="p-4 flex items-center justify-between">
            <div className="flex-1">
              <p className="text-label-sm text-on-surface font-medium">Tạo ngày nghỉ bù</p>
              <p className="text-[11px] text-on-surface-variant mt-0.5">Tự động tạo sau ca trực 24/24</p>
            </div>
            {editing ? (
              <button type="button" role="switch" aria-checked={form.autoCompensationEnabled}
                onClick={() => setForm(f => f ? { ...f, autoCompensationEnabled: !f.autoCompensationEnabled } : f)}
                className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${form.autoCompensationEnabled ? "bg-secondary border-secondary" : "bg-surface-container-high border-outline"}`}>
                <span className={`inline-block h-5 w-5 transform rounded-full bg-white shadow-sm transition-transform ${form.autoCompensationEnabled ? "translate-x-6" : "translate-x-1"}`} />
              </button>
            ) : (
              <span className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-label-sm font-semibold ${form.autoCompensationEnabled ? "bg-secondary-container text-secondary" : "bg-surface-container-high text-outline"}`}>
                <span className={`h-2 w-2 rounded-full ${form.autoCompensationEnabled ? "bg-secondary" : "bg-outline"}`} />
                {form.autoCompensationEnabled ? "Bật" : "Tắt"}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ─── Metrics History ─────────────────────────────────────── */

function MetricsHistory() {
  const [metrics, setMetrics] = useState<AlgorithmMetrics[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.getAllMetrics();
      setMetrics(((res as { data?: unknown[] })?.data ?? []).slice(0, 10) as AlgorithmMetrics[]);
    } catch {
      setMetrics([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

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
            {metrics.map(m => (
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
                  <span className="font-label-sm font-semibold text-on-surface">
                    {m.totalSchedulesCreated ?? 0}
                  </span>
                </td>
                <td className="px-3 py-2.5 text-right">
                  <div className="flex items-center justify-end gap-1.5">
                    <div className="w-12 bg-surface-variant rounded-full h-1">
                      <div className={`h-1 rounded-full ${parseNumber(m.coverageRate) >= 90 ? "bg-secondary" : parseNumber(m.coverageRate) >= 70 ? "bg-tertiary" : "bg-error"}`}
                        style={{ width: `${Math.min(100, parseNumber(m.coverageRate))}%` }} />
                    </div>
                    <span className={`text-label-xs font-semibold w-9 text-right ${parseNumber(m.coverageRate) >= 90 ? "text-secondary" : parseNumber(m.coverageRate) >= 70 ? "text-tertiary" : "text-error"}`}>
                      {Math.round(parseNumber(m.coverageRate))}%
                    </span>
                  </div>
                </td>
                <td className="px-3 py-2.5 text-right">
                  <div className="flex items-center justify-end gap-1.5">
                    <div className="w-12 bg-surface-variant rounded-full h-1">
                      <div className={`h-1 rounded-full ${parseNumber(m.balanceScore) >= 75 ? "bg-secondary" : parseNumber(m.balanceScore) >= 50 ? "bg-tertiary" : "bg-error"}`}
                        style={{ width: `${Math.min(100, parseNumber(m.balanceScore))}%` }} />
                    </div>
                    <span className={`text-label-xs font-semibold w-9 text-right ${parseNumber(m.balanceScore) >= 75 ? "text-secondary" : parseNumber(m.balanceScore) >= 50 ? "text-tertiary" : "text-error"}`}>
                      {Math.round(parseNumber(m.balanceScore))}%
                    </span>
                  </div>
                </td>
                <td className="px-3 py-2.5 text-right">
                  <span className={`inline-flex items-center gap-1 text-label-xs font-semibold ${m.conflictCount === 0 ? "text-secondary" : "text-error"}`}>
                    {m.conflictCount > 0 && <span className="material-symbols-outlined text-[10px]" aria-hidden="true">warning</span>}
                    {m.conflictCount}
                  </span>
                </td>
                <td className="px-3 py-2.5 text-right text-label-xs text-on-surface-variant">
                  {m.executionTimeMs < 1000 ? `${m.executionTimeMs}ms` : `${(m.executionTimeMs / 1000).toFixed(1)}s`}
                </td>
                <td className="px-3 py-2.5 text-label-xs text-on-surface-variant whitespace-nowrap">
                  {new Date(m.createdAt).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}
                </td>
                <td className="px-3 py-2.5">
                  <button className="h-7 w-7 flex items-center justify-center rounded-lg hover:bg-surface-container-low transition-colors cursor-pointer"
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
      desc: "Ngưỡng cân bằng tải tối thiểu. Cao →公平 hơn nhưng khó đạt.", range: "30%–100%" },
    { key: "overnight_recovery_hours", icon: "hotel", color: "text-error", bg: "bg-error-container",
      desc: "Số giờ nghỉ bắt buộc giữa hai ca trực 24/24.", range: "12–72 giờ" },
    { key: "backtrack_time_limit_seconds", icon: "timer", color: "text-tertiary", bg: "bg-tertiary-container",
      desc: "Giới hạn thời gian Backtracking. Hết thời gian → dừng, trả kết quả tốt nhất.", range: "10–300 giây" },
  ];

  const algos = [
    { name: "GREEDY", speed: "Rất nhanh", quality: "Tốt", best: "Phủ lịch nhanh, dữ liệu lớn" },
    { name: "ROUND_ROBIN", speed: "Nhanh", quality: "Trung bình", best: "Chia đều tải, nhanh hơn Backtrack" },
    { name: "BACKTRACKING", speed: "Chậm", quality: "Tối ưu", best: "Tìm lời giải tốt nhất, kỳ nhỏ" },
  ];

  return (
    <div className="space-y-4">
      {/* Algorithm quick-ref cards */}
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">
        {items.map(item => (
          <div key={item.key} className="bg-surface-container-lowest rounded-xl border border-outline-variant p-3 flex gap-3">
            <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${item.bg} ${item.color}`}>
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{item.icon}</span>
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1 flex-wrap">
                <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/20 px-1 py-0.5 rounded">{item.key}</code>
                <span className="text-[10px] font-semibold bg-surface-container-low text-on-surface-variant px-1.5 py-0.5 rounded border border-outline-variant/30">{item.range}</span>
              </div>
              <p className="text-[11px] text-on-surface-variant leading-relaxed line-clamp-2">{item.desc}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Algorithm comparison compact */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        <div className="px-4 py-2.5 bg-surface-container-low border-b border-outline-variant">
          <p className="text-label-sm font-semibold text-on-surface">So sánh thuật toán</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="bg-surface-container-low border-b border-outline-variant">
                {["Thuật toán", "Tốc độ", "Chất lượng", "Phù hợp"].map(h => (
                  <th key={h} scope="col" className="px-3 py-2 text-label-xs font-semibold uppercase tracking-wide text-on-surface-variant text-left">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/50">
              {algos.map(row => (
                <tr key={row.name} className="hover:bg-surface-container-low transition-colors">
                  <td className="px-3 py-2.5"><span className="text-label-sm font-semibold text-on-surface">{row.name}</span></td>
                  <td className="px-3 py-2.5 text-label-xs text-on-surface-variant">{row.speed}</td>
                  <td className="px-3 py-2.5 text-label-xs text-on-surface-variant">{row.quality}</td>
                  <td className="px-3 py-2.5 text-label-xs text-on-surface-variant">{row.best}</td>
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
    if (editing && presets.length > 0) {
      setShowDropdown(true);
    }
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
            className="h-7 w-36 rounded border border-primary bg-surface pl-2 pr-6 text-[11px] font-mono text-on-surface focus:outline-none focus:ring-1 focus:ring-primary/20"
            value={value}
            onChange={e => setValue(e.target.value)}
            onKeyDown={e => { if (e.key === "Enter") handleSave(); if (e.key === "Escape") { setEditing(false); setValue(config.paramValue); } }}
            onBlur={() => setTimeout(() => setShowDropdown(false), 150)}
            autoFocus />
          {presets.length > 0 && showDropdown && (
            <div className="absolute z-50 mt-1 w-full bg-surface-container-lowest border border-outline-variant rounded shadow-lg max-h-40 overflow-y-auto">
              {presets.map(p => (
                <div key={p.value}
                  className="px-2 py-1 text-[11px] font-mono cursor-pointer hover:bg-surface-container-low transition-colors"
                  onMouseDown={(e) => { e.preventDefault(); setValue(p.value); setShowDropdown(false); }}>
                  {p.label}
                </div>
              ))}
            </div>
          )}
          <span className="material-symbols-outlined absolute right-1 top-1/2 -translate-y-1/2 text-[12px] text-outline pointer-events-none" aria-hidden="true">expand_more</span>
        </div>
        <button onClick={handleSave} disabled={saving}
          className="h-7 w-7 flex items-center justify-center rounded bg-primary text-white hover:bg-primary/90 disabled:opacity-50 transition-colors cursor-pointer">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">check</span>
        </button>
        <button onClick={() => { setEditing(false); setValue(config.paramValue); }}
          className="h-7 w-7 flex items-center justify-center rounded border border-outline-variant hover:bg-surface-container-low transition-colors cursor-pointer">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">close</span>
        </button>
      </div>
    );
  }

  return (
    <button onClick={() => setEditing(true)}
      className="group/val flex items-center gap-1 cursor-pointer" title="Click để sửa">
      <span className="font-mono text-[12px] text-on-surface bg-surface-container-low px-2 py-0.5 rounded border border-transparent group-hover/val:border-primary transition-colors max-w-[180px] truncate block">
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
        <input className="h-7 w-40 rounded border border-primary bg-surface px-2 text-[11px] text-on-surface focus:outline-none focus:ring-1 focus:ring-primary/20"
          value={desc} onChange={e => setDesc(e.target.value)} placeholder="Mô tả..." autoFocus />
        <button onClick={handleSaveDesc} disabled={saving}
          className="h-7 w-7 flex items-center justify-center rounded bg-primary text-white hover:bg-primary/90 disabled:opacity-50 transition-colors cursor-pointer">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">check</span>
        </button>
        <button onClick={() => { setEditingDesc(false); setDesc(config.description); }}
          className="h-7 w-7 flex items-center justify-center rounded border border-outline-variant hover:bg-surface-container-low transition-colors cursor-pointer">
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">close</span>
        </button>
      </div>
    );
  }

  return (
    <>
      <button onClick={() => setEditingDesc(true)}
        className="h-7 w-7 flex items-center justify-center rounded hover:bg-surface-container-low transition-colors cursor-pointer"
        title="Sửa mô tả" aria-label="Sửa mô tả">
        <span className="material-symbols-outlined text-[14px] text-on-surface-variant" aria-hidden="true">edit_note</span>
      </button>
      <button onClick={() => setConfirmOpen(true)}
        className="h-7 w-7 flex items-center justify-center rounded hover:bg-error-container transition-colors cursor-pointer"
        title="Xóa" aria-label="Xóa">
        <span className="material-symbols-outlined text-[14px] text-error" aria-hidden="true">delete</span>
      </button>
      {confirmOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-black/40" onClick={() => setConfirmOpen(false)} aria-hidden="true" />
          <div className="relative w-full max-w-sm rounded-xl border border-outline-variant bg-surface-container-lowest shadow-2xl p-5 space-y-3">
            <div className="flex items-center gap-2">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-error-container text-error">
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">warning</span>
              </div>
              <div>
                <h3 className="text-label-md font-semibold text-on-surface">Xóa cấu hình?</h3>
                <code className="text-[11px] text-primary font-mono">{config.paramKey}</code>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setConfirmOpen(false)}
                className="px-3 py-1.5 rounded-lg border border-outline-variant text-label-sm text-on-surface hover:bg-surface-container-low transition-colors cursor-pointer">
                Hủy
              </button>
              <button onClick={() => { handleDelete(); setConfirmOpen(false); }}
                className="px-3 py-1.5 rounded-lg bg-error text-label-sm font-semibold text-on-error hover:bg-error/90 transition-colors cursor-pointer">
                Xóa
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

/* ─── Config Row (legacy — kept for reference) ────── */

function ConfigRow({ config, onSave, onDelete }: {
  config: ConfigEntry;
  onSave: (updated: EditingConfig) => void;
  onDelete: () => void;
}) {
  const { error: toastError } = useToast();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<EditingConfig>({ paramValue: config.paramValue, description: config.description });
  const [saving, setSaving] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  useEffect(() => { setForm({ paramValue: config.paramValue, description: config.description }); }, [config]);

  const handleSave = async () => {
    if (form.paramValue === config.paramValue && form.description === config.description) { setEditing(false); return; }
    setSaving(true);
    try {
      await api.updateAlgorithmConfig(config.paramKey, { paramValue: form.paramValue ?? config.paramValue, description: form.description ?? config.description });
      onSave(form);
      setEditing(false);
    } catch (err) {
      toastError(getErrorMessage(err, "Lưu thất bại"));
    } finally { setSaving(false); }
  };

  const handleDelete = async () => {
    try { await api.deleteAlgorithmConfig(config.paramKey); onDelete(); }
    catch (err) { toastError(getErrorMessage(err, "Xóa thất bại")); }
  };

  const typeColors: Record<string, string> = {
    NUMBER: "bg-primary-fixed text-primary",
    STRING: "bg-surface-container-low text-on-surface-variant",
    BOOLEAN: "bg-secondary-container text-secondary",
    JSON: "bg-tertiary-container text-tertiary",
  };

  return (
    <>
      <div className="px-4 py-2.5 hover:bg-surface-container-low transition-colors">
        <div className="flex items-start gap-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-1.5 mb-1 flex-wrap">
              <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/20 px-1 py-0.5 rounded">{config.paramKey}</code>
              <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold uppercase ${typeColors[config.valueType] ?? typeColors.STRING}`}>
                {config.valueType}
              </span>
            </div>
            {editing ? (
              <input className="h-8 w-full max-w-xs rounded-lg border border-primary bg-surface px-3 text-label-sm font-mono text-on-surface transition-all focus:outline-none focus:ring-1 focus:ring-primary/20"
                value={form.paramValue ?? ""} onChange={e => setForm(f => ({ ...f, paramValue: e.target.value }))} />
            ) : (
              <p className="text-label-sm font-mono text-on-surface bg-surface-container-low px-2 py-0.5 rounded border border-outline-variant/30 inline-block max-w-xs truncate" title={config.paramValue}>
                {config.paramValue}
              </p>
            )}
            {editing ? (
              <textarea className="mt-1.5 w-full resize-none rounded-lg border border-primary bg-surface px-3 py-1.5 text-label-xs text-on-surface transition-all focus:outline-none focus:ring-1 focus:ring-primary/20"
                rows={2} value={form.description ?? ""} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                placeholder="Mô tả..." />
            ) : (
              <p className="text-[11px] text-on-surface-variant mt-1 line-clamp-2 leading-relaxed">{config.description || "—"}</p>
            )}
          </div>
          <div className="flex items-center gap-0.5 shrink-0">
            {editing ? (
              <>
                <button onClick={() => { setEditing(false); setForm({ paramValue: config.paramValue, description: config.description }); }}
                  className="h-7 px-2.5 rounded-lg border border-outline-variant text-[11px] text-on-surface-variant hover:bg-surface-container-low transition-colors cursor-pointer">
                  Hủy
                </button>
                <button onClick={handleSave} disabled={saving}
                  className="h-7 px-2.5 rounded-lg bg-primary text-[11px] font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors cursor-pointer">
                  Lưu
                </button>
              </>
            ) : (
              <>
                <button onClick={() => setEditing(true)}
                  className="flex h-7 w-7 items-center justify-center rounded-lg hover:bg-surface-container-low transition-colors cursor-pointer"
                  title="Sửa" aria-label="Sửa">
                  <span className="material-symbols-outlined text-[15px] text-on-surface-variant" aria-hidden="true">edit</span>
                </button>
                <button onClick={() => setConfirmOpen(true)}
                  className="flex h-7 w-7 items-center justify-center rounded-lg hover:bg-error-container transition-colors cursor-pointer"
                  title="Xóa" aria-label="Xóa">
                  <span className="material-symbols-outlined text-[15px] text-error" aria-hidden="true">delete</span>
                </button>
              </>
            )}
          </div>
        </div>
      </div>

      {confirmOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-black/40" onClick={() => setConfirmOpen(false)} aria-hidden="true" />
          <div className="relative w-full max-w-sm rounded-xl border border-outline-variant bg-surface-container-lowest shadow-2xl p-6 space-y-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-error-container text-error">
                <span className="material-symbols-outlined text-[20px]" aria-hidden="true">warning</span>
              </div>
              <div>
                <h3 className="text-title-md font-semibold text-on-surface">Xóa cấu hình?</h3>
                <p className="text-label-sm text-on-surface-variant mt-0.5">
                  <code className="font-mono text-primary">{config.paramKey}</code>
                </p>
              </div>
            </div>
            <p className="text-label-md text-on-surface-variant">Hành động này không thể hoàn tác.</p>
            <div className="flex justify-end gap-2">
              <button onClick={() => setConfirmOpen(false)}
                className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors cursor-pointer">
                Hủy
              </button>
              <button onClick={() => { handleDelete(); setConfirmOpen(false); }}
                className="px-4 py-2 rounded-lg bg-error text-label-md font-semibold text-on-error hover:bg-error/90 transition-colors cursor-pointer">
                Xóa
              </button>
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
      <div className="relative w-full max-w-md rounded-xl border border-outline-variant bg-surface-container-lowest shadow-2xl overflow-hidden">
        <div className="px-6 py-4 border-b border-outline-variant bg-surface-container-low flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-fixed text-primary">
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">add</span>
            </div>
            <div>
              <h2 className="text-title-md font-semibold text-on-surface">Thêm cấu hình mới</h2>
              <p className="text-label-xs text-on-surface-variant">Tạo thông số vận hành cho thuật toán</p>
            </div>
          </div>
          <button onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors cursor-pointer"
            aria-label="Đóng">
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">close</span>
          </button>
        </div>

        <div className="p-6 space-y-4">
          <div>
            <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-key">
              Tên thông số <span className="text-error">*</span>
            </label>
            <div className="relative">
              <select id="cfg-key"
                className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md font-mono text-on-surface appearance-none transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer"
                value={form.paramKey} onChange={e => handlePresetChange(e.target.value)}>
                {PRESET_PARAMS.map(p => <option key={p.value} value={p.value}>{p.label}</option>)}
              </select>
              <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[18px] pointer-events-none" aria-hidden="true">expand_more</span>
            </div>
            <p className="text-[11px] text-outline mt-1">Chọn từ danh sách hoặc nhập tay tên tùy ý</p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-type">Kiểu dữ liệu</label>
              <div className="relative">
                <select id="cfg-type"
                  className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface appearance-none transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer"
                  value={form.valueType} onChange={e => setForm(f => ({ ...f, valueType: e.target.value }))}>
                  {VALUE_TYPES.map(t => <option key={t.value} value={t.value}>{t.label} ({t.value})</option>)}
                </select>
                <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[18px] pointer-events-none" aria-hidden="true">expand_more</span>
              </div>
            </div>
            <div>
              <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-value">Giá trị <span className="text-error">*</span></label>
              <input id="cfg-value"
                className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md font-mono text-on-surface transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                placeholder="VD: 1000, true, 2.5" value={form.paramValue}
                onChange={e => setForm(f => ({ ...f, paramValue: e.target.value }))} />
            </div>
          </div>

          <div>
            <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-desc">Mô tả</label>
            <textarea id="cfg-desc"
              className="w-full resize-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-label-md text-on-surface transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
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
          <button onClick={onClose}
            className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors cursor-pointer">
            Hủy
          </button>
          <button onClick={handleSubmit}
            disabled={!form.paramKey.trim() || !form.paramValue.trim() || creating}
            className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors cursor-pointer">
            {creating ? <><span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-on-primary border-t-transparent" /> Đang tạo...</> : <><span className="material-symbols-outlined text-[16px]" aria-hidden="true">add</span> Tạo cấu hình</>}
          </button>
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
  }, [toastError, loadConfigs]);

  const filteredConfigs = configs.filter(c => {
    if (filterType !== "ALL" && c.valueType !== filterType) return false;
    if (filter.trim()) {
      const kw = filter.toLowerCase();
      return c.paramKey.toLowerCase().includes(kw) || c.description.toLowerCase().includes(kw);
    }
    return true;
  });

  if (!isAdmin) {
    return (
      <div className="rounded-xl border border-tertiary-container bg-tertiary-container/20 p-8 flex flex-col items-center gap-3 text-center">
        <span className="material-symbols-outlined text-tertiary text-[40px]" aria-hidden="true">lock</span>
        <h2 className="text-title-lg font-semibold text-on-surface">Không có quyền truy cập</h2>
        <p className="text-body-sm text-on-surface-variant max-w-md">
          Chỉ <strong>Quản trị viên</strong> mới có quyền quản lý cấu hình thuật toán.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <BackButton href="/auto-scheduling" variant="full" label="Quay lại" className="mb-2" />

      {/* Header row: title + tabs + action */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-headline-lg font-bold text-on-surface">Cấu hình thuật toán</h1>
          <p className="text-label-sm text-on-surface-variant mt-0.5">Thiết lập thông số vận hành cho thuật toán xếp lịch</p>
        </div>
        <div className="flex items-center gap-2">
          <TabBar active={activeTab} onChange={(t) => setActiveTab(t as TabKey)} />
          <button onClick={handleSyncDescriptions} disabled={syncingDesc}
            className="flex items-center gap-1.5 rounded-lg border border-outline-variant px-3 py-2 text-label-sm font-medium text-on-surface-variant hover:bg-surface-container-low hover:border-primary disabled:opacity-50 transition-colors cursor-pointer"
            title="Cập nhật mô tả các tham số về phiên bản mặc định theo code">
            {syncingDesc ? <><span className="size-3.5 animate-spin rounded-full border border-outline-variant border-t-transparent" /> Đang đồng bộ...</> : <><span className="material-symbols-outlined text-[14px]" aria-hidden="true">sync</span> Đồng bộ</>}
          </button>
          <button onClick={() => setCreateModalOpen(true)}
            className="flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-label-sm font-semibold text-on-primary hover:bg-primary/90 transition-colors cursor-pointer">
            <span className="material-symbols-outlined text-[14px]" aria-hidden="true">add</span> Thêm
          </button>
        </div>
      </div>

      {/* Tab Content */}
      {activeTab === "config" && (
        <div className="space-y-5">
          {/* Runtime Config Editor Card */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
            <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center gap-2">
              <span className="material-symbols-outlined text-primary text-[18px]" aria-hidden="true">tune</span>
              <h2 className="text-title-sm font-semibold text-on-surface">Thông số runtime</h2>
              <span className="text-[11px] text-on-surface-variant ml-auto">Áp dụng cho mọi kỳ lịch</span>
            </div>
            <div className="p-4">
              <RuntimeConfigEditor onSaved={() => void loadConfigs()} />
            </div>
          </div>

          {/* Custom Configs — Data table */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
            <div className="px-4 py-2.5 border-b border-outline-variant bg-surface-container-low flex items-center justify-between gap-3 flex-wrap">
              <div className="flex items-center gap-2">
                <p className="text-label-sm font-semibold text-on-surface">Cấu hình tùy chỉnh</p>
                <span className="text-[11px] text-on-surface-variant">{configs.length} thông số</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="relative">
                  <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px]" aria-hidden="true">search</span>
                  <input className="h-8 pl-8 pr-3 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface w-40 focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all"
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
              <div className="p-4 space-y-1">
                {[1, 2, 3].map(i => <div key={i} className="h-10 bg-surface-container-low rounded animate-pulse" />)}
              </div>
            ) : filteredConfigs.length === 0 ? (
              <EmptyState
                icon="tune"
                title={configs.length === 0 ? "Chưa có cấu hình tùy chỉnh" : "Không tìm thấy cấu hình phù hợp"}
                description={configs.length === 0 ? "Tạo cấu hình mới để tùy chỉnh thuật toán" : "Thử thay đổi bộ lọc tìm kiếm"}
                size="compact"
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left">
                  <thead>
                    <tr className="bg-surface-container-low border-b border-outline-variant">
                      <th className="px-3 py-2 text-label-xs font-semibold text-on-surface-variant w-8">
                        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">key</span>
                      </th>
                      <th className="px-3 py-2 text-label-xs font-semibold text-on-surface-variant">Thông số</th>
                      <th className="px-3 py-2 text-label-xs font-semibold text-on-surface-variant w-20">Kiểu</th>
                      <th className="px-3 py-2 text-label-xs font-semibold text-on-surface-variant">Giá trị</th>
                      <th className="px-3 py-2 text-label-xs font-semibold text-on-surface-variant hidden lg:table-cell">Mô tả</th>
                      <th className="px-3 py-2 text-label-xs font-semibold text-on-surface-variant w-24">Cập nhật</th>
                      <th className="px-3 py-2 text-label-xs font-semibold text-on-surface-variant w-16 text-right">Hành động</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/50">
                    {filteredConfigs.map(config => (
                      <tr key={config.paramKey} className="hover:bg-surface-container-low transition-colors group">
                        <td className="px-3 py-2">
                          <span className="material-symbols-outlined text-[14px] text-outline" aria-hidden="true">settings</span>
                        </td>
                        <td className="px-3 py-2">
                          <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded">{config.paramKey}</code>
                        </td>
                        <td className="px-3 py-2">
                          <span className={`inline-flex px-1.5 py-0.5 rounded text-label-xs font-semibold uppercase ${
                            config.valueType === "NUMBER" ? "bg-primary-fixed text-primary" :
                            config.valueType === "BOOLEAN" ? "bg-secondary-container text-secondary" :
                            config.valueType === "JSON" ? "bg-tertiary-container text-tertiary" :
                            "bg-surface-container text-on-surface-variant"
                          }`}>
                            {config.valueType}
                          </span>
                        </td>
                        <td className="px-3 py-2">
                          <ConfigValueCell config={config} />
                        </td>
                        <td className="px-3 py-2 hidden lg:table-cell">
                          <p className="text-[11px] text-on-surface-variant line-clamp-1" title={config.description}>{config.description || "—"}</p>
                        </td>
                        <td className="px-3 py-2">
                          <p className="text-[11px] text-outline">{config.updatedBy || "—"}</p>
                          <p className="text-[10px] text-outline/60">{new Date(config.updatedAt).toLocaleDateString("vi-VN")}</p>
                        </td>
                        <td className="px-3 py-2">
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
      {activeTab === "reference" && <ReferenceSection />}

      {/* Create Modal */}
      <CreateConfigModal
        open={createModalOpen}
        onClose={() => { setCreateModalOpen(false); setCreateMsg(null); }}
        onCreate={handleCreate}
        creating={creating}
        message={createMsg}
      />
    </div>
  );
}
