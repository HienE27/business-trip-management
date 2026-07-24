"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { PresetKey } from "@/components/algorithm-config/PresetSelector";
import { PresetSelector } from "@/components/algorithm-config/PresetSelector";
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
  // Target ca/người/tháng — USER-EDITABLE (trước đây hardcode → recommend bơm
  // minPerDay lên 299, sinh 25K+ slots → thuật toán chạy 200s+).
  // Default hợp lý cho bệnh viện ~900 NS: 2 ca/người/tháng cho L01-L03 (đủ
  // nghỉ), 5 cho L04 (phòng khám chuyên gia, chủ trì).
  const [targetPerStaffPerMonth, setTargetPerStaffPerMonth] = useState({
    L01: 2, L02: 2, L03: 2, L04: 5,
  });

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
      // Load target_per_month từ DB (ưu tiên giá trị đã lưu, fallback default)
      setTargetPerStaffPerMonth({
        L01: merged.l01TargetPerMonth ?? 2,
        L02: merged.l02TargetPerMonth ?? 2,
        L03: merged.l03TargetPerMonth ?? 2,
        L04: merged.l04TargetPerMonth ?? 5,
      });
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

  // AUTO-FILL: khi user đổi targetPerStaffPerMonth → tự động tính lại min/max
  // per day/week trong form (cùng công thức backend dùng trong recommendAutoGenConfig).
  // User vẫn có thể chỉnh tay các ô min/max sau khi auto-fill (chỉ ghi đè nếu form
  // chưa dirty hoặc user chủ động bấm "Tính lại theo target").
  const [autoFillEnabled, setAutoFillEnabled] = useState(true);
  useEffect(() => {
    if (!form || !autoFillEnabled) return;
    const days = PERIOD_DAYS;       // 31
    const weeks = PERIOD_WEEKS;     // 5
    // Tính totalStaff từ staffAnalysis trực tiếp (state này đã được load
    // trước effect này → không dùng useMemo ở trên để tránh used-before-decl).
    const staff = Math.max(1, staffAnalysis.reduce((s, a) => s + a.staffCount, 0) || 924);
    const l04Elig = Math.max(1, staff);
    const numSpecs = Math.max(1, staffAnalysis.length || 6);
    const effectiveL04 = Math.max(1, Math.min(l04Elig, Math.ceil(staff / numSpecs)));

    const compute = (target: number, elig: number) => {
      if (target <= 0) return { minPerDay: 0, maxPerDay: 0, minPerWeek: 0, maxPerWeek: 0 };
      const minPerDay = Math.max(1, Math.ceil((target * elig) / days));
      const minPerWeek = Math.max(1, Math.ceil(target / weeks));
      const maxPerWeek = Math.max(minPerWeek + 1, Math.ceil((target / weeks) * 1.5));
      const maxPerDay = Math.max(minPerDay, Math.ceil(maxPerWeek * 1.2));
      return { minPerDay, maxPerDay, minPerWeek, maxPerWeek };
    };

    const l01 = compute(targetPerStaffPerMonth.L01, staff);
    const l02 = compute(targetPerStaffPerMonth.L02, staff);
    const l03 = compute(targetPerStaffPerMonth.L03, staff);
    const l04 = compute(targetPerStaffPerMonth.L04, effectiveL04);

    setForm(prev => prev ? {
      ...prev,
      l01MinPerDay: l01.minPerDay, l01MaxPerDay: l01.maxPerDay,
      l01MinPerWeek: l01.minPerWeek, l01MaxPerWeek: l01.maxPerWeek,
      l02MinPerDay: l02.minPerDay, l02MaxPerDay: l02.maxPerDay,
      l02MinPerWeek: l02.minPerWeek, l02MaxPerWeek: l02.maxPerWeek,
      l03MinPerDay: l03.minPerDay, l03MaxPerDay: l03.maxPerDay,
      l03MinPerWeek: l03.minPerWeek, l03MaxPerWeek: l03.maxPerWeek,
      l04MinPerDay: l04.minPerDay, l04MaxPerDay: l04.maxPerDay,
      l04MinPerWeek: l04.minPerWeek, l04MaxPerWeek: l04.maxPerWeek,
      l01TargetPerMonth: targetPerStaffPerMonth.L01,
      l02TargetPerMonth: targetPerStaffPerMonth.L02,
      l03TargetPerMonth: targetPerStaffPerMonth.L03,
      l04TargetPerMonth: targetPerStaffPerMonth.L04,
    } : prev);
  }, [targetPerStaffPerMonth, autoFillEnabled, staffAnalysis]); // eslint-disable-line react-hooks/exhaustive-deps -- form intentionally omitted; effect sets form (would loop)

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
    // eslint-disable-next-line react-hooks/exhaustive-deps -- bind once on mount; re-binding on every form change would re-attach the listener without semantic benefit
  }, []);

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
        l04AllowedSpecialties: form.l04AllowedSpecialties ?? [],
        l01AllowedSpecialties: form.l01AllowedSpecialties ?? [],
        l02AllowedSpecialties: form.l02AllowedSpecialties ?? [],
        l03AllowedSpecialties: form.l03AllowedSpecialties ?? [],
        l04BalanceStrategy: form.l04BalanceStrategy ?? "FAIR_DISTRIBUTE",
        // Persist target_per_month để UI refresh không reset (trước đây hardcode).
        l01TargetPerMonth: targetPerStaffPerMonth.L01,
        l02TargetPerMonth: targetPerStaffPerMonth.L02,
        l03TargetPerMonth: targetPerStaffPerMonth.L03,
        l04TargetPerMonth: targetPerStaffPerMonth.L04,
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
        // Truyền giá trị từ state (user-editable) — trước đây hardcode
        // {L01:8, L02:7, L03:8, L04:10} → recommend bơm minPerDay lên 299.
        targetPerStaffPerMonth: targetPerStaffPerMonth,
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
        l01AllowedSpecialties: updated.l01AllowedSpecialties ?? [],
        l02AllowedSpecialties: updated.l02AllowedSpecialties ?? [],
        l03AllowedSpecialties: updated.l03AllowedSpecialties ?? [],
        l04BalanceStrategy: updated.l04BalanceStrategy ?? "FAIR_DISTRIBUTE",
        // Preserve target_per_month để UI refresh không reset (recommend không đổi target).
        l01TargetPerMonth: targetPerStaffPerMonth.L01,
        l02TargetPerMonth: targetPerStaffPerMonth.L02,
        l03TargetPerMonth: targetPerStaffPerMonth.L03,
        l04TargetPerMonth: targetPerStaffPerMonth.L04,
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
      {/* Preset selector — bao gồm Lab-Eval (demo/đánh giá chuyên khoa).
          Preset CHỈ nạp form, KHÔNG tự lưu; người dùng vẫn bấm "Lưu thay đổi"
          để ghi vào DB. Đặc tính Hiến yêu cầu: "auto được, nhưng bắt buộc manual". */}
      <div className="space-y-1.5">
        <PresetSelector activePreset={activePreset} onApply={(k) => applyPreset(k)} />
        {activePreset === "labEval" && (
          <p className="text-[11px] text-purple-700 bg-purple-50 border border-purple-200 rounded-lg px-2.5 py-1.5 flex items-center gap-1.5">
            <span className="material-symbols-outlined text-[14px]" aria-hidden="true">info</span>
            Lab-Eval là cấu hình đánh giá (L04 dày, cross OFF, auto-adjust OFF). Nút này chỉ nạp form — bấm <strong className="font-semibold">Lưu thay đổi</strong> để ghi vào DB. Không tự thay cấu hình production.
          </p>
        )}
      </div>

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

        {/* Target ca/người/tháng — USER-EDITABLE. Tự auto-fill các ô min/max per day/week
            trong form khi toggle "Auto-fill" bật. Persist vào DB khi user bấm "Lưu thay đổi". */}
        <div className="mb-4 p-3 rounded-lg bg-surface-container-low/40 border border-outline-variant/40">
          <div className="flex items-center gap-2 mb-2">
            <span className="material-symbols-outlined text-primary text-[16px]" aria-hidden="true">target</span>
            <p className="text-label-sm font-semibold text-on-surface">Mục tiêu ca / người / tháng</p>
            <label className="ml-auto flex items-center gap-1.5 cursor-pointer">
              <input type="checkbox" className="sr-only peer"
                checked={autoFillEnabled}
                onChange={(e) => setAutoFillEnabled(e.target.checked)} />
              <span className={`text-[10px] px-1.5 py-0.5 rounded border ${autoFillEnabled ? "bg-primary/10 border-primary/40 text-primary" : "bg-surface-container-low border-outline-variant text-on-surface-variant"}`}>
                {autoFillEnabled ? "Auto-fill ON" : "Auto-fill OFF"}
              </span>
              <div className="w-7 h-4 bg-surface-variant rounded-full peer peer-checked:bg-primary after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:bg-white after:rounded-full after:h-3 after:w-3 after:transition-all peer-checked:after:translate-x-3 relative" />
            </label>
          </div>
          <div className="grid grid-cols-4 gap-2">
            {([
              { key: "L01" as const, label: "L01 Trực", color: "text-red-700" },
              { key: "L02" as const, label: "L02 TT", color: "text-blue-700" },
              { key: "L03" as const, label: "L03 PK", color: "text-green-700" },
              { key: "L04" as const, label: "L04 CG", color: "text-purple-700" },
            ]).map(({ key, label, color }) => {
              const target = targetPerStaffPerMonth[key];
              // Live preview: tính minPerDay mà backend sẽ sinh ra
              const elig = key === "L04"
                ? Math.max(1, Math.min(totalStaff, Math.ceil(totalStaff / Math.max(1, staffAnalysis.length || 6))))
                : Math.max(1, totalStaff);
              const previewMinPerDay = target > 0 ? Math.max(1, Math.ceil((target * elig) / PERIOD_DAYS)) : 0;
              return (
                <div key={key}>
                  <label className={`block text-[10px] font-semibold mb-0.5 ${color}`}>{label}</label>
                  <input
                    type="number"
                    min={0}
                    max={31}
                    value={target}
                    onChange={(e) => setTargetPerStaffPerMonth(prev => ({
                      ...prev,
                      [key]: Math.max(0, parseInt(e.target.value) || 0),
                    }))}
                    className="h-8 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                  {autoFillEnabled && target > 0 && (
                    <p className="text-[9px] text-on-surface-variant mt-0.5 text-center">
                      → min/day: {previewMinPerDay}
                    </p>
                  )}
                </div>
              );
            })}
          </div>
          <p className="text-[10px] text-on-surface-variant mt-1.5">
            {autoFillEnabled
              ? "Auto-fill đang bật: đổi target → ô min/max per day/week tự tính lại. Bấm 'Lưu thay đổi' để commit DB."
              : "Auto-fill đang tắt: chỉ chỉnh tay các ô min/max. Target vẫn lưu vào DB khi bấm Lưu."}
          </p>
        </div>

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

			        {/* weekendWeight — chỉ áp dụng cho GREEDY */}
			        <div className="mt-4">
			          <SliderField
			            label="weekendWeight"
			            desc="Trong số cuối tuần — chỉ áp dụng cho thuật toán GREEDY. Giá trị càng cao càng ưu tiên giảm ca cuối tuần."
			            value={form.weekendWeight ?? 2.0}
			            min={0}
			            max={5}
			            step={0.5}
			            format={(v) => v.toFixed(1)}
			            onChange={(v) => setField("weekendWeight", v)}
			          />
			        </div>

			        {/* beamWidth — Beam Search width & SA iteration multiplier */}
			        <div className="mt-4">
			          <div className="flex items-center justify-between">
			            <label className="text-label-sm font-medium text-on-surface">Beam Width</label>
			            <input type="number" min={1} max={50} step={1}
			              value={form.beamWidth ?? 5}
			              onChange={(e) => setField("beamWidth", parseInt(e.target.value) || 5)}
			              className="h-9 w-24 rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
			            />
			          </div>
			          <p className="text-[10px] text-on-surface-variant mt-0.5">
			            Độ rộng Beam Search (mặc định 5). Với SA scheduler, dùng để tính số vòng lặp (beamWidth × 100).
			          </p>
			        </div>

			        {/* Scorer weights — quyết định tỷ trọng coverage / fairness / constraint */}
		        <div className="mt-4 grid grid-cols-1 sm:grid-cols-3 gap-4">
		          <SliderField
		            label="Coverage Weight"
		            desc="Trọng số coverage (0.0–1.0). Cao → ưu tiên lấp đầy ca."
		            value={form.coverageWeight ?? 0.40}
		            min={0} max={1} step={0.05}
		            format={(v) => v.toFixed(2)}
		            onChange={(v) => setField("coverageWeight", v)}
		          />
		          <SliderField
		            label="Fairness Weight"
		            desc="Trọng số fairness (0.0–1.0). Cao → ưu tiên phân bổ công bằng."
		            value={form.fairnessWeight ?? 0.35}
		            min={0} max={1} step={0.05}
		            format={(v) => v.toFixed(2)}
		            onChange={(v) => setField("fairnessWeight", v)}
		          />
		          <SliderField
		            label="Constraint Weight"
		            desc="Trọng số constraint (0.0–1.0). Cao → ưu tiên kỷ luật ràng buộc."
		            value={form.constraintWeight ?? 0.25}
		            min={0} max={1} step={0.05}
		            format={(v) => v.toFixed(2)}
		            onChange={(v) => setField("constraintWeight", v)}
		          />
		        </div>

		        {/* Scorer thresholds & penalties */}
		        <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
		          <div>
		            <label className="block text-label-sm font-medium text-on-surface mb-1">Pass Threshold</label>
		            <input type="number" min={0} max={100} step={1}
		              value={form.passThreshold ?? 80}
		              onChange={(e) => setField("passThreshold", parseFloat(e.target.value) || 0)}
		              className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
		            />
		            <p className="text-[10px] text-on-surface-variant mt-0.5">Ngưỡng đạt (0-100)</p>
		          </div>
		          <div>
		            <label className="block text-label-sm font-medium text-on-surface mb-1">Hard Violation Penalty</label>
		            <input type="number" min={0} max={100} step={0.5}
		              value={form.hardViolationPenalty ?? 25}
		              onChange={(e) => setField("hardViolationPenalty", parseFloat(e.target.value) || 0)}
		              className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
		            />
		            <p className="text-[10px] text-on-surface-variant mt-0.5">Phạt / vi phạm HARD</p>
		          </div>
		          <div>
		            <label className="block text-label-sm font-medium text-on-surface mb-1">Soft Violation Penalty</label>
		            <input type="number" min={0} max={50} step={0.5}
		              value={form.softViolationPenalty ?? 5}
		              onChange={(e) => setField("softViolationPenalty", parseFloat(e.target.value) || 0)}
		              className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
		            />
		            <p className="text-[10px] text-on-surface-variant mt-0.5">Phạt / vi phạm SOFT</p>
		          </div>
		          <div>
		            <label className="block text-label-sm font-medium text-on-surface mb-1">Target CV</label>
		            <input type="number" min={0} max={1} step={0.01}
		              value={form.targetCv ?? 0.10}
		              onChange={(e) => setField("targetCv", parseFloat(e.target.value) || 0)}
		              className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
		            />
		            <p className="text-[10px] text-on-surface-variant mt-0.5">CV ≤ target → 100 điểm</p>
		          </div>
		          <div>
		            <label className="block text-label-sm font-medium text-on-surface mb-1">Worst CV</label>
		            <input type="number" min={0} max={1} step={0.01}
		              value={form.worstCv ?? 0.50}
		              onChange={(e) => setField("worstCv", parseFloat(e.target.value) || 0)}
		              className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
		            />
			            <p className="text-[10px] text-on-surface-variant mt-0.5">CV ≥ worst → 0 điểm</p>
			          </div>
			        </div>

		        {/* Rebalance rounds */}
		        <div className="mt-4">
		          <p className="text-label-sm font-medium text-on-surface mb-2">Rebalance Rounds</p>
		          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
		            <div>
		              <label className="block text-label-sm font-medium text-on-surface mb-1">Total</label>
		              <input type="number" min={0} max={500} step={1}
		                value={form.rebalanceRoundsTotal ?? 80}
		                onChange={(e) => setField("rebalanceRoundsTotal", parseInt(e.target.value) || 0)}
		                className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
		              />
		              <p className="text-[10px] text-on-surface-variant mt-0.5">RRHC total, SA fairness (default 80)</p>
		            </div>
		            <div>
		              <label className="block text-label-sm font-medium text-on-surface mb-1">Per-type</label>
		              <input type="number" min={0} max={500} step={1}
		                value={form.rebalanceRoundsPerType ?? 30}
		                onChange={(e) => setField("rebalanceRoundsPerType", parseInt(e.target.value) || 0)}
		                className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
		              />
		              <p className="text-[10px] text-on-surface-variant mt-0.5">RRHC per-type, Beam per-type (default 30)</p>
		            </div>
		            <div>
		              <label className="block text-label-sm font-medium text-on-surface mb-1">EG / Beam total</label>
		              <input type="number" min={0} max={500} step={1}
		                value={form.rebalanceRoundsEg ?? 40}
		                onChange={(e) => setField("rebalanceRoundsEg", parseInt(e.target.value) || 0)}
		                className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
		              />
		              <p className="text-[10px] text-on-surface-variant mt-0.5">EG per-type, Beam total (default 40)</p>
		            </div>
		            <div>
		              <label className="block text-label-sm font-medium text-on-surface mb-1">Post-save</label>
		              <input type="number" min={0} max={500} step={1}
		                value={form.rebalanceRoundsPostSave ?? 100}
		                onChange={(e) => setField("rebalanceRoundsPostSave", parseInt(e.target.value) || 0)}
		                className="h-9 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
		              />
		              <p className="text-[10px] text-on-surface-variant mt-0.5">Post-process rebalance (default 100)</p>
		            </div>
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
