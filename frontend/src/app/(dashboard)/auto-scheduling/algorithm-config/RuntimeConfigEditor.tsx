"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { PresetKey } from "@/components/algorithm-config/PresetSelector";
import { Button } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useToast } from "@/hooks/useToast";
import type { RuntimeConfig } from "./types";
import { ALGORITHM_PRESETS, detectPreset } from "./presets";
import { mergeRuntimeAndAutoGen } from "./merge";

type Props = { onSaved?: () => void };

const SHIFT_TYPES = [
  { key: "l01", label: "L01 - Truc 24/24", color: "border-red-400" },
  { key: "l02", label: "L02 - Thong tan", color: "border-blue-400" },
  { key: "l03", label: "L03 - PK Dich vu", color: "border-green-400" },
  { key: "l04", label: "L04 - PK Chuyen gia", color: "border-purple-400" },
] as const;

const PERIOD_DAYS = 31;
const PERIOD_WEEKS = 5;

type StaffAnalysis = {
  specialtyName: string;
  staffCount: number;
  l04PerMonth: number;
  isSolo: boolean;
};

type RecommendResult = {
  recommendedConfig: Record<string, unknown>;
  recommendedRuntimeConfig?: { maxShiftsPerStaff: number };
  totalShiftsExpected: number;
  rationale: string;
};

export function RuntimeConfigEditor({ onSaved }: Props) {
  const { success, error } = useToast();
  const [config, setConfig] = useState<RuntimeConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<RuntimeConfig | null>(null);
  const [activePreset, setActivePreset] = useState<PresetKey | null>(null);

  // Staff analysis state
  const [staffAnalysis, setStaffAnalysis] = useState<StaffAnalysis[]>([]);
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [recommending, setRecommending] = useState(false);
  const [recommendResult, setRecommendResult] = useState<RecommendResult | null>(null);
  const [applyingRecommend, setApplyingRecommend] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, resAutoGen] = await Promise.all([
        api.getRuntimeConfig(),
        api.getAutoGenConfig(),
      ]);
      const data = (res as unknown as { data: RuntimeConfig }).data;
      const autoGen = (resAutoGen as unknown as { data: RuntimeConfig }).data;
      const merged = mergeRuntimeAndAutoGen(data, autoGen);
      setConfig(merged);
      setForm(merged);
      setActivePreset(detectPreset(merged));
    } catch {
      error("Khong the tai cau hinh runtime");
    } finally {
      setLoading(false);
    }
  }, [error]);

  useEffect(() => { void load(); }, [load]);

  // Load staff analysis on mount
  useEffect(() => {
    (async () => {
      setAnalysisLoading(true);
      try {
        const staffListRes = await api.getActiveStaff();
        const staffList: Array<{ specialty?: { id: number; name: string } | null }> = staffListRes.data ?? [];
        const specMap = new Map<string, number>();
        for (const s of staffList) {
          const name = s.specialty?.name ?? "Không có chuyên khoa";
          specMap.set(name, (specMap.get(name) ?? 0) + 1);
        }
        const analysis: StaffAnalysis[] = [];
        for (const [name, count] of specMap) {
          const l04PerMonth = Math.min(Math.max(1, count) * 8, 31);
          analysis.push({ specialtyName: name, staffCount: count, l04PerMonth, isSolo: count === 1 });
        }
        analysis.sort((a, b) => b.staffCount - a.staffCount);
        setStaffAnalysis(analysis);
      } catch {
        // silent — analysis is non-critical
      } finally {
        setAnalysisLoading(false);
      }
    })();
  }, []);

  // Re-detect preset when form changes
  useEffect(() => {
    if (form) setActivePreset(detectPreset(form));
  }, [form]);

  // Keyboard shortcuts: Ctrl+S save, Ctrl+Z reset
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement;
      if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable) return;
      if (e.ctrlKey || e.metaKey) {
        if (e.key === "s") { e.preventDefault(); void handleSave(); }
        else if (e.key === "z" && !e.shiftKey) { e.preventDefault(); handleReset(); }
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  });

  function applyPreset(key: PresetKey) {
    const preset = ALGORITHM_PRESETS[key];
    if (!preset) return;
    setForm(prev => prev ? { ...prev, ...preset.config } : prev);
    setActivePreset(key);
  }

  function handleReset() {
    if (config) { setForm(config); setActivePreset(detectPreset(config)); }
  }

  async function handleSave() {
    if (!form) return;
    setSaving(true);
    try {
      const autoGenPayload = {
        enabled: true,
        holidayMode: form.holidayMode ?? "SKIP",
        l01MinPerDay: form.l01MinPerDay ?? 0, l02MinPerDay: form.l02MinPerDay ?? 0,
        l03MinPerDay: form.l03MinPerDay ?? 0, l04MinPerDay: form.l04MinPerDay ?? 0,
        l01MaxPerDay: form.l01MaxPerDay ?? 0, l02MaxPerDay: form.l02MaxPerDay ?? 0,
        l03MaxPerDay: form.l03MaxPerDay ?? 0, l04MaxPerDay: form.l04MaxPerDay ?? 0,
        l01MinPerWeek: form.l01MinPerWeek ?? 0, l02MinPerWeek: form.l02MinPerWeek ?? 0,
        l03MinPerWeek: form.l03MinPerWeek ?? 0, l04MinPerWeek: form.l04MinPerWeek ?? 0,
        l01MaxPerWeek: form.l01MaxPerWeek ?? 0, l02MaxPerWeek: form.l02MaxPerWeek ?? 0,
        l03MaxPerWeek: form.l03MaxPerWeek ?? 0, l04MaxPerWeek: form.l04MaxPerWeek ?? 0,
        removedShiftTypes: form.removedShiftTypes ?? [],
        l04CrossSpecialty: form.l04CrossSpecialty ?? false,
        l04CrossSpecialtyRatio: form.l04CrossSpecialtyRatio ?? 0.3,
        l04BalanceStrategy: form.l04BalanceStrategy ?? "FAIR_DISTRIBUTE",
      };
      await api.updateRuntimeConfig(form);
      await api.updateAutoGenConfig(autoGenPayload);
      setConfig(form);
      setActivePreset(detectPreset(form));
      success("Da luu cau hinh thuat toan");
      onSaved?.();
    } catch (err) {
      error(getErrorMessage(err, "Luu that bai"));
    } finally {
      setSaving(false);
    }
  }

  function setField<K extends keyof RuntimeConfig>(key: K, value: RuntimeConfig[K]) {
    setForm(prev => prev ? { ...prev, [key]: value } : prev);
  }

  function isDirty(): boolean {
    if (!config || !form) return false;
    return JSON.stringify(config) !== JSON.stringify(form);
  }

  const totalStaff = useMemo(() =>
    staffAnalysis.reduce((s, a) => s + a.staffCount, 0),
  [staffAnalysis]);

  // Build eligibleStaff map for recommend API — backend expects shift type IDs (L01..L04) as keys
  const eligibleStaffMap = useMemo(() => {
    const t = totalStaff;
    return { L01: t, L02: t, L03: t, L04: t };
  }, [totalStaff]);

  async function handleRecommend() {
    setRecommending(true);
    setRecommendResult(null);
    try {
      const res = await api.recommendAutoGenConfig({
        periodDays: PERIOD_DAYS,
        periodWeeks: PERIOD_WEEKS,
        totalStaff,
        eligibleStaff: eligibleStaffMap,
        targetPerStaffPerMonth: { L01: 8, L02: 7, L03: 8, L04: 10 },
        expandNonL04Eligibility: true,
        expandedSpecialties: staffAnalysis.map(a => a.specialtyName),
        maxShiftsPerStaff: form?.maxShiftsPerStaff,
      }) as unknown as { data: RecommendResult };
      setRecommendResult(res.data);
      success("Phân tích hoàn tất");
    } catch (err) {
      error(getErrorMessage(err, "Phân tích thất bại"));
    } finally {
      setRecommending(false);
    }
  }

  async function handleApplyRecommend() {
    if (!recommendResult || !form) return;
    setApplyingRecommend(true);
    try {
      const rc = recommendResult.recommendedConfig;
      const updated = {
        ...form,
        maxShiftsPerStaff: recommendResult.recommendedRuntimeConfig?.maxShiftsPerStaff ?? form.maxShiftsPerStaff,
        autoAdjustConfig: false,
        l01MinPerDay: (rc.l01MinPerDay as number) ?? form.l01MinPerDay,
        l01MaxPerDay: (rc.l01MaxPerDay as number) ?? form.l01MaxPerDay,
        l02MinPerDay: (rc.l02MinPerDay as number) ?? form.l02MinPerDay,
        l02MaxPerDay: (rc.l02MaxPerDay as number) ?? form.l02MaxPerDay,
        l03MinPerDay: (rc.l03MinPerDay as number) ?? form.l03MinPerDay,
        l03MaxPerDay: (rc.l03MaxPerDay as number) ?? form.l03MaxPerDay,
        l04MinPerDay: (rc.l04MinPerDay as number) ?? form.l04MinPerDay,
        l04MaxPerDay: (rc.l04MaxPerDay as number) ?? form.l04MaxPerDay,
        l01MinPerWeek: (rc.l01MinPerWeek as number) ?? form.l01MinPerWeek,
        l01MaxPerWeek: (rc.l01MaxPerWeek as number) ?? form.l01MaxPerWeek,
        l02MinPerWeek: (rc.l02MinPerWeek as number) ?? form.l02MinPerWeek,
        l02MaxPerWeek: (rc.l02MaxPerWeek as number) ?? form.l02MaxPerWeek,
        l03MinPerWeek: (rc.l03MinPerWeek as number) ?? form.l03MinPerWeek,
        l03MaxPerWeek: (rc.l03MaxPerWeek as number) ?? form.l03MaxPerWeek,
        l04MinPerWeek: (rc.l04MinPerWeek as number) ?? form.l04MinPerWeek,
        l04MaxPerWeek: (rc.l04MaxPerWeek as number) ?? form.l04MaxPerWeek,
        l04CrossSpecialty: (rc.l04CrossSpecialty as boolean) ?? form.l04CrossSpecialty,
        holidayMode: (rc.holidayMode as string) ?? form.holidayMode,
      };
      setForm(updated);
      // Save immediately
      const autoGenPayload = {
        enabled: true,
        holidayMode: updated.holidayMode ?? "SKIP",
        l01MinPerDay: updated.l01MinPerDay ?? 0, l02MinPerDay: updated.l02MinPerDay ?? 0,
        l03MinPerDay: updated.l03MinPerDay ?? 0, l04MinPerDay: updated.l04MinPerDay ?? 0,
        l01MaxPerDay: updated.l01MaxPerDay ?? 0, l02MaxPerDay: updated.l02MaxPerDay ?? 0,
        l03MaxPerDay: updated.l03MaxPerDay ?? 0, l04MaxPerDay: updated.l04MaxPerDay ?? 0,
        l01MinPerWeek: updated.l01MinPerWeek ?? 0, l02MinPerWeek: updated.l02MinPerWeek ?? 0,
        l03MinPerWeek: updated.l03MinPerWeek ?? 0, l04MinPerWeek: updated.l04MinPerWeek ?? 0,
        l01MaxPerWeek: updated.l01MaxPerWeek ?? 0, l02MaxPerWeek: updated.l02MaxPerWeek ?? 0,
        l03MaxPerWeek: updated.l03MaxPerWeek ?? 0, l04MaxPerWeek: updated.l04MaxPerWeek ?? 0,
        removedShiftTypes: updated.removedShiftTypes ?? [],
        l04CrossSpecialty: updated.l04CrossSpecialty ?? false,
        l04CrossSpecialtyRatio: updated.l04CrossSpecialtyRatio ?? 0.3,
        l04AllowedSpecialties: updated.l04AllowedSpecialties ?? [],
        l04BalanceStrategy: updated.l04BalanceStrategy ?? "FAIR_DISTRIBUTE",
      };
      await api.updateAutoGenConfig(autoGenPayload);
      await api.updateRuntimeConfig(updated);
      setConfig(updated);
      setActivePreset(detectPreset(updated));
      success("Đã áp dụng đề xuất cấu hình");
      setRecommendResult(null);
      onSaved?.();
    } catch (err) {
      error(getErrorMessage(err, "Áp dụng thất bại"));
    } finally {
      setApplyingRecommend(false);
    }
  }

  if (loading) return <EditorSkeleton />;
  if (!config || !form) return null;

  return (
    <div className="space-y-5">
      {/* Keyboard shortcuts hint */}
      <div className="flex items-center justify-end gap-4 text-[11px] text-on-surface-variant">
        <span className="flex items-center gap-1">
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">Ctrl</kbd>
          <span>+</span>
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">S</kbd>
          <span>Luu</span>
        </span>
        <span className="flex items-center gap-1">
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">Ctrl</kbd>
          <span>+</span>
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">Z</kbd>
          <span>Huy</span>
        </span>
      </div>

      {/* ════════════════════════════════════════════════════════
          Staff Analysis & Auto-Adjust Section
         ════════════════════════════════════════════════════════ */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-5">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-[18px]" aria-hidden="true">groups</span>
            <p className="text-title-sm font-semibold text-on-surface">Phan tich nhan su & De xuat</p>
          </div>
          <span className="text-[11px] text-on-surface-variant px-2 py-0.5 bg-surface-container-low rounded-full">
            {totalStaff} nhan su
          </span>
        </div>

        {/* Per-specialty breakdown */}
        {analysisLoading ? (
          <div className="h-16 bg-surface-container-low rounded animate-pulse" />
        ) : staffAnalysis.length > 0 ? (
          <div className="space-y-2 mb-4">
            {staffAnalysis.map(a => (
              <div key={a.specialtyName}
                className="flex items-center gap-3 px-3 py-2 rounded-lg bg-surface-container-low/40"
              >
                <span className="text-label-sm font-medium text-on-surface w-24 truncate">{a.specialtyName}</span>
                <div className="flex-1 h-5 bg-surface-container-low rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-500 text-[10px] text-white flex items-center justify-end pr-1.5
                      ${a.isSolo ? 'bg-orange-500' : 'bg-primary'}`}
                    style={{ width: `${(a.staffCount / Math.max(...staffAnalysis.map(x => x.staffCount))) * 100}%` }}
                  >
                    {a.staffCount}
                  </div>
                </div>
                <span className="text-[11px] text-on-surface-variant tabular-nums w-24 text-right">
                  L04: ~{a.l04PerMonth} ca/thang
                </span>
                {a.isSolo && (
                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold bg-orange-100 text-orange-700 border border-orange-300 whitespace-nowrap">
                    <span className="material-symbols-outlined text-[12px]">warning</span>
                    Solo
                  </span>
                )}
              </div>
            ))}
          </div>
        ) : null}

        {/* Recommendation result */}
        {recommendResult && (
          <div className="mb-4 p-3 rounded-lg bg-secondary-container/20 border border-secondary/30">
            <div className="flex items-center gap-2 mb-2">
              <span className="material-symbols-outlined text-secondary text-[16px]">lightbulb</span>
              <p className="text-label-sm font-semibold text-on-surface">Ket qua phan tich</p>
            </div>
            <p className="text-[12px] text-on-surface-variant whitespace-pre-line mb-2">{recommendResult.rationale}</p>
            <div className="flex items-center gap-4 text-[11px] text-on-surface-variant">
              <span>Tong ca du kien: <strong className="text-on-surface">{recommendResult.totalShiftsExpected}</strong></span>
            </div>
            <div className="flex gap-2 mt-3">
              <Button variant="primary" size="sm" onClick={() => void handleApplyRecommend()} loading={applyingRecommend}>
                Ap dung de xuat
              </Button>
              <Button variant="secondary" size="sm" onClick={() => setRecommendResult(null)}>
                Bo qua
              </Button>
            </div>
          </div>
        )}

        <Button
          variant="secondary"
          size="sm"
          onClick={() => void handleRecommend()}
          loading={recommending}
          icon={<span className="material-symbols-outlined text-[16px]" aria-hidden="true">auto_awesome</span>}
        >
          Phan tich & De xuat cau hinh
        </Button>
      </div>

	      {/* Per-shift limits: min/max per day + per week */}
      <div>
        <div className="flex items-center gap-2 mb-3">
          <span className="material-symbols-outlined text-on-surface-variant text-[16px]" aria-hidden="true">calendar_view_month</span>
          <p className="text-label-sm font-medium text-on-surface-variant">Gioi han theo loai lich</p>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
          {SHIFT_TYPES.map(({ key, label, color }) => (
            <div key={key} className={`bg-surface-container-lowest rounded-xl border border-outline-variant ${color} border-l-4 p-4 space-y-3`}>
              <p className="text-label-sm font-semibold text-on-surface">{label}</p>
              <div className="grid grid-cols-2 gap-2">
                <NumberInput
                  label="Min/ngay"
                  value={form[`${key}MinPerDay` as keyof RuntimeConfig] as number ?? 0}
                  onChange={(v) => setField(`${key}MinPerDay` as keyof RuntimeConfig, v as never)}
                />
                <NumberInput
                  label="Max/ngay"
                  value={form[`${key}MaxPerDay` as keyof RuntimeConfig] as number ?? 0}
                  onChange={(v) => setField(`${key}MaxPerDay` as keyof RuntimeConfig, v as never)}
                />
              </div>
              <div className="grid grid-cols-2 gap-2 pt-1 border-t border-outline-variant/40">
                <NumberInput
                  label="Min/tuan"
                  value={form[`${key}MinPerWeek` as keyof RuntimeConfig] as number ?? 0}
                  onChange={(v) => setField(`${key}MinPerWeek` as keyof RuntimeConfig, v as never)}
                />
                <NumberInput
                  label="Max/tuan"
                  value={form[`${key}MaxPerWeek` as keyof RuntimeConfig] as number ?? 0}
                  onChange={(v) => setField(`${key}MaxPerWeek` as keyof RuntimeConfig, v as never)}
                />
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Advanced config */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-5">
        <div className="flex items-center gap-2 mb-4">
          <span className="material-symbols-outlined text-on-surface-variant text-[16px]" aria-hidden="true">tune</span>
          <p className="text-label-sm font-medium text-on-surface-variant">Cau hinh nang cao</p>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* l04CrossSpecialty toggle */}
          <div className="flex items-center justify-between px-3 py-2.5 rounded-lg bg-surface-container-low/40">
            <div>
              <p className="text-label-sm font-medium text-on-surface">L04 Cross-Specialty</p>
              <p className="text-[10px] text-on-surface-variant">Cho phep nguoi ngoai chuyen khoa nhan L04</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" className="sr-only peer"
                checked={form.l04CrossSpecialty ?? false}
                onChange={(e) => setField("l04CrossSpecialty", e.target.checked)}
              />
              <div className="w-9 h-5 bg-surface-variant rounded-full peer peer-checked:bg-primary peer-focus:ring-2 peer-focus:ring-primary/20 after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:after:translate-x-4" />
            </label>
          </div>

          {/* autoCompensationEnabled toggle */}
          <div className="flex items-center justify-between px-3 py-2.5 rounded-lg bg-surface-container-low/40">
            <div>
              <p className="text-label-sm font-medium text-on-surface">Tu dong nghỉ bu</p>
              <p className="text-[10px] text-on-surface-variant">Tao ngay nghỉ bu sau L01</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" className="sr-only peer"
                checked={form.autoCompensationEnabled ?? false}
                onChange={(e) => setField("autoCompensationEnabled", e.target.checked)}
              />
              <div className="w-9 h-5 bg-surface-variant rounded-full peer peer-checked:bg-primary peer-focus:ring-2 peer-focus:ring-primary/20 after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:after:translate-x-4" />
            </label>
          </div>

          {/* autoAdjustConfig toggle */}
          <div className="flex items-center justify-between px-3 py-2.5 rounded-lg bg-surface-container-low/40">
            <div>
              <p className="text-label-sm font-medium text-on-surface">Tu dong tinh chinh (Auto-Adjust)</p>
              <p className="text-[10px] text-on-surface-variant">Tu dong giam deu L01-L04 neu qua tai</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" className="sr-only peer"
                checked={form.autoAdjustConfig ?? true}
                onChange={(e) => setField("autoAdjustConfig", e.target.checked)}
              />
              <div className="w-9 h-5 bg-surface-variant rounded-full peer peer-checked:bg-primary peer-focus:ring-2 peer-focus:ring-primary/20 after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:after:translate-x-4" />
            </label>
          </div>

          {/* holidayMode dropdown */}
          <div>
            <label className="block text-label-sm font-medium text-on-surface mb-1">Holiday Mode</label>
            <select
              className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-[13px] font-medium text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              value={form.holidayMode ?? "SKIP"}
              onChange={(e) => setField("holidayMode", e.target.value)}
            >
              <option value="SKIP">SKIP - Bo qua ngay le</option>
              <option value="PARTIAL">PARTIAL - Giam tai</option>
            </select>
          </div>

          {/* maxShiftsPerStaff */}
          <div>
            <label className="block text-label-sm font-medium text-on-surface mb-1">Max ca/nguoi</label>
            <input type="number" min={0}
              value={form.maxShiftsPerStaff ?? 0}
              onChange={(e) => setField("maxShiftsPerStaff", parseInt(e.target.value) || 0)}
              className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
            <p className="text-[10px] text-on-surface-variant mt-0.5">0 = tu dong</p>
          </div>
        </div>
      </div>

      {/* Save / Reset buttons */}
      <div className="flex items-center justify-end gap-3">
        <Button variant="secondary" size="sm" onClick={handleReset}>Huy bo</Button>
        <Button
          variant="primary"
          size="sm"
          onClick={() => void handleSave()}
          disabled={saving || !isDirty()}
          loading={saving}
          icon={!saving ? <span className="material-symbols-outlined text-[16px]" aria-hidden="true">save</span> : undefined}
        >
          Luu thay doi
        </Button>
      </div>
    </div>
  );
}

/* ─── Slider Field ──────────────────────────────────────── */

function SliderField({ label, desc, value, min, max, step, format, onChange }: {
  label: string; desc: string; value: number;
  min: number; max: number; step: number;
  format: (v: number) => string;
  onChange: (v: number) => void;
}) {
  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
      <p className="text-label-sm font-semibold text-on-surface">{label}</p>
      <p className="text-[11px] text-on-surface-variant mb-3">{desc}</p>
      <div className="flex items-center gap-3">
        <input
          type="range"
          min={min} max={max} step={step}
          value={value}
          onChange={(e) => onChange(parseFloat(e.target.value))}
          className="flex-1 h-1.5 bg-surface-variant rounded-full appearance-none cursor-pointer accent-primary"
        />
        <span className="font-mono text-sm font-bold text-on-surface tabular-nums w-14 text-right">{format(value)}</span>
      </div>
    </div>
  );
}

/* ─── Number Input ──────────────────────────────────────── */

function NumberInput({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <div>
      <label className="block text-[11px] text-on-surface-variant mb-1">{label}</label>
      <input
        type="number"
        min={0}
        value={value}
        onChange={(e) => onChange(parseInt(e.target.value) || 0)}
        className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors"
      />
    </div>
  );
}

/* ─── Skeleton ──────────────────────────────────────────── */

function EditorSkeleton() {
  return (
    <div className="space-y-4">
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
        <div className="h-6 w-40 bg-surface-container-low rounded animate-pulse mb-4" />
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          {[1, 2, 3, 4].map(i => <div key={i} className="h-20 bg-surface-container-low rounded-xl animate-pulse" />)}
        </div>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[1, 2, 3].map(i => <div key={i} className="h-28 bg-surface-container-low rounded-xl animate-pulse" />)}
      </div>
    </div>
  );
}
