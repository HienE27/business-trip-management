"use client";

import { useCallback, useEffect, useState } from "react";
import type { PresetKey, RuntimeConfig as PresetRuntimeConfig } from "@/components/algorithm-config/PresetSelector";
import { PresetSelector } from "@/components/algorithm-config/PresetSelector";
import { PresetSandboxModal, type PresetEntry } from "@/components/algorithm-config/PresetSandboxModal";
import { Button } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { getParamValidation } from "@/lib/validation/algorithmConfig";
import { useToast } from "@/hooks/useToast";
import type { RuntimeConfig } from "./types";
import { PARAM_KEY_TO_CFG } from "./types";
import { ALGORITHM_PRESETS, detectPreset } from "./presets";
import {
  PARAM_GROUPS,
  SHIFT_TYPE_GROUPS,
  READ_ONLY_GROUP_IDS,
  calcProgressPct,
  formatParamDisplay,
  getParamBounds,
  getParamProgressColor,
} from "./paramConfig";
import { ShiftTypeGroupCard } from "./ShiftTypeGroupCard";
import { HolidayModeField } from "./HolidayModeField";
import { RemovedShiftTypesField } from "./RemovedShiftTypesField";
import { ShiftTypeCrossSpecialtyCard } from "./ShiftTypeCrossSpecialtyCard";
import { sanitizeAllowedSpecialties } from "./crossSpecialty";
import { BusinessRulesCard } from "./BusinessRulesCard";
import { ConfigDiffModal } from "./ConfigDiffModal";
import { getChangedKeys } from "./diff";
import { mergeRuntimeAndAutoGen } from "./merge";
import { AutoCalculateDialog, type AutoCalculateResult, type AutoCalculateInput } from "./AutoCalculateDialog";
import type { DashboardData, DashboardSummary, ShiftStatistics } from "@/types/api";

type Props = { onSaved?: () => void };

export function RuntimeConfigEditor({ onSaved }: Props) {
  const { success, error } = useToast();
  const [config, setConfig] = useState<RuntimeConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<RuntimeConfig | null>(null);
  const [activePreset, setActivePreset] = useState<PresetKey | null>(null);
  const [customPresets, setCustomPresets] = useState<Record<string, { label: string; tagline: string; config: Partial<RuntimeConfig> }>>({});
  const [showDiff, setShowDiff] = useState(false);
  const [sandboxOpen, setSandboxOpen] = useState(false);
  const [autoCalcOpen, setAutoCalcOpen] = useState(false);
  const [savedCalcPresets, setSavedCalcPresets] = useState<{ id: string; name: string; config: AutoCalculateInput }[]>([]);
  const [allSpecialties, setAllSpecialties] = useState<string[]>([]);
  const [scheduleStats, setScheduleStats] = useState<{
    totalStaff: number;
    avgShiftsPerStaff: number;
    coverageDays: number;
    periodDays: number;
  } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, resAutoGen, specialtiesRes, dashboardRes] = await Promise.all([
        api.getRuntimeConfig(),
        api.getAutoGenConfig(),
        api.getActiveSpecialties(),
        api.getDashboard(),
      ]);
      // unwrapped by api-client interceptor
      // The backend returns the full RuntimeConfig object; the api-client method
      // is declared with only a subset of fields to avoid coupling to backend
      // schema drift, but the runtime object actually carries the whole shape.
      // Cast through unknown to bridge the two views safely.
      // BUG-CARD-NO-PERSIST: api-client.getRuntimeConfig() / getAutoGenConfig()
      // return the full ApiResponse envelope (because they call this.request
      // directly instead of this.get), so we MUST unwrap `.data` before
      // feeding the values into mergeRuntimeAndAutoGen(). Without this, the
      // merged config is empty, the form fields fall back to 0, and any save
      // overwrites the user's persisted values with zeros.
      const runtimeResp = res as unknown as { data?: RuntimeConfig };
      const autoGenResp = resAutoGen as unknown as { data?: RuntimeConfig };
      const data = (runtimeResp.data ?? (res as unknown as RuntimeConfig)) as RuntimeConfig;
      const autoGen = (autoGenResp.data ?? (resAutoGen as unknown as RuntimeConfig)) as RuntimeConfig;
      const specialties = ((specialtiesRes as { data?: Array<{ name: string }> })?.data ?? []).map((s) => s.name);
      const summary = (dashboardRes as { summary?: { totalStaff: number } })?.summary ?? { totalStaff: 0 };
      const shiftStats = (dashboardRes as { shiftStatistics?: ShiftStatistics })?.shiftStatistics;

      setAllSpecialties(specialties);
      const merged = mergeRuntimeAndAutoGen(data, autoGen);
      // BUG-CARD-NO-PERSIST: This log is intentional for diagnosing the
      // "values reset to 0 after Save" issue on algorithm-config page.
      // See https://trellis.atlassian.net/browse/BUG-CONFIG-CARD-NO-PERSIST
      // The merged config is what we hand to the form state and what gets
      // POSTed on save. If l01MinPerDay shows 0 here, the BE response is the
      // source — verify with curl. If merged has 7 but the form later reads 0,
      // there's a stale closure / re-render problem downstream.
      console.log("[algorithm-config] load() raw runtime =", JSON.stringify(data));
      console.log("[algorithm-config] load() raw autoGen =", JSON.stringify(autoGen));
      console.log("[algorithm-config] load() merged =", JSON.stringify({
        l01MinPerDay: merged.l01MinPerDay,
        l02MinPerDay: merged.l02MinPerDay,
        l03MinPerDay: merged.l03MinPerDay,
        l04MinPerDay: merged.l04MinPerDay,
        l01MaxPerDay: merged.l01MaxPerDay,
        l01MaxPerWeek: merged.l01MaxPerWeek,
      }));
      // Strip legacy "__NONE__" sentinels that may have leaked into the
      // persisted allowlist via the older "Bỏ chọn tất cả" button. The
      // backend treats an empty list as "all eligible", so we map a
      // sentinel-only list back to an empty list.
      const mergedSanitized = {
        ...merged,
        l04AllowedSpecialties: sanitizeAllowedSpecialties(merged.l04AllowedSpecialties),
      };
      setConfig(mergedSanitized);
      setForm(mergedSanitized);

      // Calculate schedule stats for suggestion algorithm
      if (summary && shiftStats) {
        const totalShifts = shiftStats.L01Count + shiftStats.L02Count + shiftStats.L03Count + shiftStats.L04Count;
        const avgShifts = summary.totalStaff > 0 ? totalShifts / summary.totalStaff : 0;
        setScheduleStats({
          totalStaff: summary.totalStaff,
          avgShiftsPerStaff: Math.round(avgShifts * 10) / 10,
          coverageDays: Math.round(avgShifts * 4), // rough estimate
          periodDays: 30,
        });
      }
    } catch {
      error("Không thể tải cấu hình runtime");
    } finally {
      setLoading(false);
    }
  }, [error]);

  useEffect(() => { void load(); }, [load]);

  // Re-detect preset khi form thay đổi
  useEffect(() => {
    if (form) {
      const detected = detectPreset(form);
      if (detected) {
        setActivePreset(detected);
      } else {
        // Check custom presets too
        const customMatch = Object.keys(customPresets).find(key => {
          const cp = customPresets[key];
          return (
            form.weekendWeight === cp.config.weekendWeight &&
            form.greedyCoverageThreshold === cp.config.greedyCoverageThreshold
          );
        });
        setActivePreset((customMatch as PresetKey | undefined) || null);
      }
    }
  }, [form, customPresets]);

  // Keyboard shortcuts: Ctrl+S = save, Ctrl+Z = reset, Escape = cancel edit
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      // Ignore if typing in input/textarea
      const target = e.target as HTMLElement;
      if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable) {
        return;
      }

      if (e.ctrlKey || e.metaKey) {
        if (e.key === "s") {
          e.preventDefault();
          if (editing && form) {
            void handleSave();
          }
        } else if (e.key === "z" && !e.shiftKey) {
          e.preventDefault();
          if (editing) {
            handleReset();
          }
        } else if (e.key === "e") {
          e.preventDefault();
          if (!editing) {
            setEditing(true);
          }
        }
      } else if (e.key === "Escape" && editing) {
        e.preventDefault();
        handleReset();
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [editing, form]);

  function applyPreset(key: PresetKey, presetConfig?: Partial<RuntimeConfig>) {
    const preset = key.startsWith("custom_")
      ? customPresets[key]
      : ALGORITHM_PRESETS[key];

    if (!preset) return;

    const newConfig = 'config' in preset ? preset.config : {};
    const configToApply = presetConfig ?? newConfig;

    setForm(prev => prev ? { ...prev, ...configToApply } : prev);
    setActivePreset(key);
    setEditing(true);
  }

  function handleReset() {
    if (config) {
      setForm(config);
      setEditing(false);
    }
  }

  async function handleSave() {
    if (!form) return;
    setSaving(true);
    try {
      const autoGenPayload = {
        enabled: true,
        holidayMode: form.holidayMode ?? "SKIP",
        // BUG-CARD-NO-PERSIST: log form snapshot at save time so we can
        // confirm whether the form state held the loaded values or had been
        // reset to 0 by some re-render path before this point.
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
        // L04 cross-specialty (L01/L02/L03 reserved for future use — currently unused).
        // Strip any "__NONE__" sentinel that may have leaked into form state
        // from legacy saves so the persisted allowlist only contains real
        // specialty names.
        l04CrossSpecialty: form.l04CrossSpecialty ?? false,
        l04CrossSpecialtyRatio: form.l04CrossSpecialtyRatio ?? 0.3,
        l04AllowedSpecialties: sanitizeAllowedSpecialties(form.l04AllowedSpecialties),
        l04BalanceStrategy: form.l04BalanceStrategy ?? "FAIR_DISTRIBUTE",
      };
      // Runtime config: only the fields the backend DTO accepts
      const runtimePayload = {
        weekendWeight: form.weekendWeight ?? 1,
        overnightRecoveryHours: form.overnightRecoveryHours ?? 24,
        greedyCoverageThreshold: form.greedyCoverageThreshold ?? 0.95,
        balanceScoreMin: form.balanceScoreMin ?? 0.6,
        minStaffPerShift: form.minStaffPerShift ?? 0,
        maxStaffPerShift: form.maxStaffPerShift ?? 0,
        minShiftsPerStaff: form.minShiftsPerStaff ?? 0,
        maxShiftsPerStaff: form.maxShiftsPerStaff ?? 0,
        l01MaxPerWeek: form.l01MaxPerWeek ?? 0,
        l02MaxPerWeek: form.l02MaxPerWeek ?? 0,
        l03MaxPerWeek: form.l03MaxPerWeek ?? 0,
        l04MaxPerWeek: form.l04MaxPerWeek ?? 0,
      };
      // Sequential: save runtime-config then auto-gen-config to avoid
      // concurrent lock contention on the algorithm_config table.
      // If the first call fails, do not attempt the second.
      // BUG-CARD-NO-PERSIST: log the exact payload being POSTed so we can
      // see whether the form state held the loaded values when save fired.
      console.log("[algorithm-config] handleSave() payload =", JSON.stringify({
        formSnapshot: {
          l01MinPerDay: form.l01MinPerDay,
          l02MinPerDay: form.l02MinPerDay,
          l03MinPerDay: form.l03MinPerDay,
          l04MinPerDay: form.l04MinPerDay,
          l01MaxPerDay: form.l01MaxPerDay,
          l01MaxPerWeek: form.l01MaxPerWeek,
        },
        autoGenPayload,
        runtimePayload,
      }));
      await api.updateRuntimeConfig(runtimePayload);
      await api.updateAutoGenConfig(autoGenPayload);
      setConfig(form);
      setEditing(false);
      success("Đã lưu cấu hình thuật toán");
      onSaved?.();
    } catch (err) {
      error(getErrorMessage(err, "Lưu thất bại"));
    } finally {
      setSaving(false);
    }
  }

  function setField<K extends keyof RuntimeConfig>(key: K, value: RuntimeConfig[K]) {
    setForm(prev => prev ? { ...prev, [key]: value } : prev);
  }

  function handleAutoCalculate(result: AutoCalculateResult) {
    setForm(prev => prev ? { ...prev, ...result } : prev);
    setEditing(true);
    success("Đã áp dụng giá trị tự động tính. Nhấn 'Lưu thay đổi' để lưu vào DB.");
  }

  function handleSaveCustomPreset(key: PresetKey, name: string, config: Partial<RuntimeConfig>) {
    setCustomPresets(prev => ({
      ...prev,
      [key]: { label: name, tagline: "Preset tùy chỉnh", config },
    }));
    success(`Đã tạo preset "${name}"`);
  }

  function handleDeleteCustomPreset(key: PresetKey) {
    const name = customPresets[key]?.label || "Preset";
    setCustomPresets(prev => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
    if (activePreset === key) {
      setActivePreset(null);
    }
    success(`Đã xóa preset "${name}"`);
  }

  // Copy current config to clipboard
  function handleCopyConfig() {
    const json = JSON.stringify(form, null, 2);
    void navigator.clipboard.writeText(json).then(() => {
      success("Đã copy cấu hình vào clipboard");
    }).catch(() => {
      error("Không thể copy clipboard");
    });
  }

  // Paste config from clipboard
  async function handlePasteConfig() {
    try {
      const text = await navigator.clipboard.readText();
      const parsed = JSON.parse(text);
      if (parsed && typeof parsed === "object") {
        setForm(prev => prev ? { ...prev, ...parsed } : prev);
        setEditing(true);
        success("Đã paste cấu hình từ clipboard");
      }
    } catch {
      error("Không thể paste: clipboard rỗng hoặc định dạng không hợp lệ");
    }
  }

  if (loading) return <EditorSkeleton />;
  if (!config || !form) return null;

  const changes = getChangedKeys(config, form);
  const isDirty = changes.length > 0;

  return (
    <div className="space-y-5">
      {/* Keyboard shortcuts hint */}
      <div className="flex items-center justify-end gap-4 text-[11px] text-on-surface-variant">
        <span className="flex items-center gap-1">
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">Ctrl</kbd>
          <span>+</span>
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">E</kbd>
          <span>Sửa</span>
        </span>
        <span className="flex items-center gap-1">
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">Ctrl</kbd>
          <span>+</span>
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">S</kbd>
          <span>Lưu</span>
        </span>
        <span className="flex items-center gap-1">
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">Ctrl</kbd>
          <span>+</span>
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">Z</kbd>
          <span>Hoàn tác</span>
        </span>
        <span className="flex items-center gap-1">
          <kbd className="px-1.5 py-0.5 bg-surface-container-low rounded border border-outline-variant font-mono text-[10px]">Esc</kbd>
          <span>Hủy</span>
        </span>
      </div>

      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-5">
        <div className="flex items-center justify-between gap-4 flex-wrap mb-4">
          <div className="flex items-center gap-2 flex-wrap">
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
            {isDirty && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-tertiary-container text-tertiary border border-tertiary/20">
                <span className="material-symbols-outlined text-[12px]">edit</span>
                Tùy chỉnh
              </span>
            )}
            {editing && changes.length > 0 && (
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
            )}
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {/* Quick actions */}
            <Button
              variant="ghost"
              size="sm"
              onClick={handleCopyConfig}
              icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">content_copy</span>}
              className="text-[11px]"
              title="Copy cấu hình"
            >
              Copy
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={handlePasteConfig}
              icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">content_paste</span>}
              className="text-[11px]"
              title="Paste cấu hình"
            >
              Paste
            </Button>
            <div className="w-px h-6 bg-outline-variant mx-1" />
            {editing ? (
              <>
                <Button variant="secondary" size="sm" onClick={handleReset}>Hủy bỏ</Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => void handleSave()}
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
        <PresetSelector
          presets={{ ...ALGORITHM_PRESETS, ...customPresets }}
          activePreset={activePreset}
          currentConfig={form}
          onApply={applyPreset}
          onSaveCustomPreset={handleSaveCustomPreset}
          onDeleteCustomPreset={handleDeleteCustomPreset}
          scheduleStats={scheduleStats || undefined}
        />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        {PARAM_GROUPS
          .filter(g => !g.hidden)
          .map(group => {
            const effectiveEditing = group.readOnly ? false : editing;
            return (
              <ParamGroupCard
                key={group.id}
                group={group}
                form={form}
                editing={effectiveEditing}
                onChange={setField}
              />
            );
          })}
        <AutoCompensationCard />
        {/* L04 Cross-specialty — business config, giữ nguyên */}
        <ShiftTypeCrossSpecialtyCard
          shiftType="L04"
          shiftTypeName="PK Chuyên gia"
          enabled={form.l04CrossSpecialty ?? false}
          ratio={form.l04CrossSpecialtyRatio ?? 0.3}
          allowedSpecialties={sanitizeAllowedSpecialties(form.l04AllowedSpecialties)}
          allSpecialties={allSpecialties}
          editing={editing}
          balanceStrategy={form.l04BalanceStrategy ?? "FAIR_DISTRIBUTE"}
          showSpecialtyConfig={true}
          onChange={(enabled, ratio, allowedSpecialties, balanceStrategy) => {
            setForm(prev => prev ? { ...prev, l04CrossSpecialty: enabled, l04CrossSpecialtyRatio: ratio, l04AllowedSpecialties: allowedSpecialties, l04BalanceStrategy: balanceStrategy } : prev);
          }}
        />
      </div>

      <div>
        <div className="flex items-center justify-between gap-3 mb-3 flex-wrap">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-on-surface-variant text-[16px]" aria-hidden="true">calendar_view_month</span>
            <p className="text-label-sm font-medium text-on-surface-variant">Giới hạn theo loại lịch</p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setAutoCalcOpen(true)}
            icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">calculate</span>}
            className="rounded-full !bg-primary-fixed !text-primary !border !border-primary/20 hover:!bg-primary/10 px-2.5 py-1 text-[11px]"
            title="Tự động tính toán min/max từ mục tiêu ca/người/tháng"
          >
            Tự động tính
          </Button>
        </div>
        <div className="flex flex-wrap gap-3">
          {SHIFT_TYPE_GROUPS.map(group => (
            <ShiftTypeGroupCard
              key={group.id}
              group={group}
              form={form}
              editing={editing}
              onChange={(key, val) => setField(key as keyof RuntimeConfig, val)}
            />
          ))}
        </div>
      </div>

      {/* Business Rules — đặt cuối trang */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        <BusinessRulesCard />
      </div>

      <ConfigDiffModal
        open={showDiff}
        onClose={() => setShowDiff(false)}
        config={config}
        form={form}
        onApply={() => { setShowDiff(false); void handleSave(); }}
      />

      <PresetSandboxModal
        open={sandboxOpen}
        onClose={() => setSandboxOpen(false)}
        presets={ALGORITHM_PRESETS as unknown as Record<string, PresetEntry>}
        currentConfig={(form ?? config) as unknown as Record<string, number | boolean | string>}
        onApply={(preset) => applyPreset(preset.key as PresetKey)}
      />

      <AutoCalculateDialog
        open={autoCalcOpen}
        onClose={() => setAutoCalcOpen(false)}
        onApply={handleAutoCalculate}
        onSavePreset={(name, config) => {
          const id = `preset-${Date.now()}`;
          setSavedCalcPresets(prev => [...prev, { id, name, config }]);
          success(`Đã lưu preset "${name}"`);
        }}
        savedPresets={savedCalcPresets}
        initialValues={{
          periodDays: 30,
          periodWeeks: 4,
          targetsPerStaffPerMonth: { L01: 7, L02: 8, L03: 9, L04: 16 },
          eligibleStaff: { L01: 8, L02: 8, L03: 8, L04: 20 },
        }}
        currentConfig={form ? {
          l01MinPerDay: Number(form.l01MinPerDay ?? 1),
          l01MaxPerDay: Number(form.l01MaxPerDay ?? 3),
          l01MinPerWeek: Number(form.l01MinPerWeek ?? 2),
          l01MaxPerWeek: Number(form.l01MaxPerWeek ?? 3),
          l02MinPerDay: Number(form.l02MinPerDay ?? 1),
          l02MaxPerDay: Number(form.l02MaxPerDay ?? 3),
          l02MinPerWeek: Number(form.l02MinPerWeek ?? 2),
          l02MaxPerWeek: Number(form.l02MaxPerWeek ?? 3),
          l03MinPerDay: Number(form.l03MinPerDay ?? 1),
          l03MaxPerDay: Number(form.l03MaxPerDay ?? 3),
          l03MinPerWeek: Number(form.l03MinPerWeek ?? 2),
          l03MaxPerWeek: Number(form.l03MaxPerWeek ?? 3),
          l04MinPerDay: Number(form.l04MinPerDay ?? 1),
          l04MaxPerDay: Number(form.l04MaxPerDay ?? 10),
          l04MinPerWeek: Number(form.l04MinPerWeek ?? 4),
          l04MaxPerWeek: Number(form.l04MaxPerWeek ?? 6),
        } : null}
      />
    </div>
  );
}

function EditorSkeleton() {
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

/* ─── Param Group Card (Collapsible) ─────────────────────────── */

type ParamGroupCardProps = {
  group: typeof PARAM_GROUPS[number];
  form: RuntimeConfig;
  editing: boolean;
  onChange: <K extends keyof RuntimeConfig>(key: K, value: RuntimeConfig[K]) => void;
};

const CATEGORY_LABELS: Record<string, string> = {
  business: "Nghiệp vụ",
  advanced: "Nâng cao",
  monitoring: "Theo dõi",
  internal: "Nội bộ",
};

function ParamGroupCard({ group, form, editing, onChange }: ParamGroupCardProps) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className={`bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 ${group.accent}`}>
      <button
        type="button"
        onClick={() => setCollapsed(!collapsed)}
        className="w-full px-5 py-4 bg-surface-container-low flex items-center justify-between gap-3 hover:bg-surface-container transition-colors"
        aria-expanded={!collapsed}
      >
        <div className="flex items-center gap-3 min-w-0">
          <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${group.bg} ${group.color}`}>
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{group.icon}</span>
          </div>
          <div className="flex flex-col items-start gap-0.5 min-w-0">
            <div className="flex items-center gap-2">
              <p className="text-label-md font-semibold text-on-surface tracking-tight">{group.label}</p>
              {group.readOnly && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-surface-container border border-outline text-on-surface-variant">
                  <span className="material-symbols-outlined text-[10px]" aria-hidden="true">lock</span>
                  Chỉ đọc
                </span>
              )}
            </div>
            {group.groupDesc && (
              <p className="text-[11px] text-on-surface-variant leading-tight line-clamp-2">{group.groupDesc}</p>
            )}
          </div>
        </div>
        <span className={`material-symbols-outlined text-[20px] text-on-surface-variant transition-transform duration-200 ${collapsed ? "" : "rotate-180"}`} aria-hidden="true">
          expand_more
        </span>
      </button>

      <div className={`overflow-hidden transition-all duration-300 ${collapsed ? "max-h-0" : "max-h-[1000px]"}`}>
        <div className="p-5 space-y-5">
          {group.params.map(param => {
            const desc = group.descriptions[param] ?? { label: param, desc: "", hint: "" };
            const cfgKey = PARAM_KEY_TO_CFG[param] ?? "weekendWeight";
            return (
              <ParamField
                key={param}
                param={param}
                desc={desc}
                cfgKey={cfgKey}
                groupId={group.id}
                form={form}
                editing={editing}
                onChange={onChange}
              />
            );
          })}
        </div>
      </div>
    </div>
  );
}

type ParamFieldProps = {
  param: string;
  desc: { label: string; desc: string; hint: string };
  cfgKey: keyof RuntimeConfig;
  groupId: string;
  form: RuntimeConfig;
  editing: boolean;
  onChange: <K extends keyof RuntimeConfig>(key: K, value: RuntimeConfig[K]) => void;
};

const TRACKING_ONLY_PARAMS = new Set(["min_staff_per_shift", "min_shifts_per_staff", "overnight_recovery_hours"]);

// Number spinner input với +/- buttons cho nhập liệu nhanh
function NumberSpinner({ value, min, max, step, onChange, disabled }: {
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
  disabled?: boolean;
}) {
  // Local state để cho phép empty input
  const [localVal, setLocalVal] = useState(value.toString());
  const [isFocused, setIsFocused] = useState(false);

  // Sync khi value thay đổi từ bên ngoài (vd: preset applied)
  useEffect(() => {
    if (!isFocused) {
      setLocalVal(value.toString());
    }
  }, [value, isFocused]);

  function handleDecrement() {
    const newVal = Math.max(min, value - step);
    const result = step < 1 ? Math.round(newVal * 100) / 100 : newVal;
    onChange(result);
    setLocalVal(result.toString());
  }

  function handleIncrement() {
    const newVal = Math.min(max, value + step);
    const result = step < 1 ? Math.round(newVal * 100) / 100 : newVal;
    onChange(result);
    setLocalVal(result.toString());
  }

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const raw = e.target.value;
    setLocalVal(raw);
    // Bug config-not-persist (RuntimeConfigEditor): the previous version of
    // this component only propagated the typed value to the parent on blur.
    // If a user typed a number then clicked "Lưu thay đổi" without first
    // blurring the input (common in dense forms with many parameters), the
    // parent's `form` state still held the old value, so the save payload
    // silently contained the stale number and the DB never updated. Commit
    // every valid keystroke to the parent immediately so that any save path
    // — keyboard shortcut, button click, or programmatic dispatch — always
    // uses the latest value the user has entered.
    if (raw === "") return; // allow empty while editing; commit on blur
    const parsed = step < 1 ? parseFloat(raw) || 0 : parseInt(raw, 10);
    if (Number.isNaN(parsed)) return;
    const clamped = Math.max(min, Math.min(max, parsed));
    if (clamped === value) return; // no-op when nothing changed
    onChange(clamped);
  }

  function handleBlur() {
    setIsFocused(false);
    // Parse and validate on blur
    const raw = localVal.trim();
    if (raw === "") {
      // Empty = 0
      setLocalVal("0");
      onChange(0);
    } else {
      const parsed = step < 1 ? parseFloat(raw) || 0 : parseInt(raw) || 0;
      const clamped = Math.max(min, Math.min(max, parsed));
      setLocalVal(clamped.toString());
      onChange(clamped);
    }
  }

  function handleFocus() {
    setIsFocused(true);
  }

  return (
    <div className={`flex items-center gap-1 ${disabled ? "opacity-50" : ""}`}>
      <button
        type="button"
        onClick={handleDecrement}
        disabled={disabled || value <= min}
        className="flex items-center justify-center h-8 w-7 rounded-lg border border-outline-variant bg-surface-container-low hover:bg-surface-container text-on-surface transition-colors disabled:opacity-40 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        title="Giảm"
      >
        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">remove</span>
      </button>
      <input
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        disabled={disabled}
        className="h-8 w-16 rounded-lg border border-outline-variant bg-surface-container-lowest px-2 text-center text-[13px] font-mono font-semibold text-on-surface tabular-nums focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors disabled:cursor-not-allowed"
        value={localVal}
        onChange={handleInputChange}
        onFocus={handleFocus}
        onBlur={handleBlur}
        placeholder="—"
      />
      <button
        type="button"
        onClick={handleIncrement}
        disabled={disabled || value >= max}
        className="flex items-center justify-center h-8 w-7 rounded-lg border border-outline-variant bg-surface-container-low hover:bg-surface-container text-on-surface transition-colors disabled:opacity-40 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        title="Tăng"
      >
        <span className="material-symbols-outlined text-[14px]" aria-hidden="true">add</span>
      </button>
    </div>
  );
}

function ParamField({ param, desc, cfgKey, groupId, form, editing, onChange }: ParamFieldProps) {
  if (param === "holiday_mode") {
    return (
      <HolidayModeField
        desc={desc}
        value={form.holidayMode ?? "SKIP"}
        editing={editing}
        onChange={(v) => onChange("holidayMode", v)}
      />
    );
  }
  if (param === "removed_shift_types") {
    return (
      <RemovedShiftTypesField
        desc={desc}
        current={form.removedShiftTypes ?? []}
        editing={editing}
        onChange={(v) => onChange("removedShiftTypes", v)}
      />
    );
  }

  const numVal = typeof form[cfgKey] === "number" ? (form[cfgKey] as number) : 0;
  const { min, max, step } = getParamBounds(param);
  const display = formatParamDisplay(param, numVal);
  const pct = calcProgressPct(param, numVal);
  const validation = getParamValidation(param, numVal);
  const isTrackingOnly = TRACKING_ONLY_PARAMS.has(param);

  return (
    <div>
      <div className="flex items-center justify-between gap-3 mb-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 mb-0.5">
            <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/50 px-1.5 py-0.5 rounded">{desc.label}</code>
            {isTrackingOnly && (
              <span className="inline-flex items-center rounded-full border border-outline-variant bg-surface-container-low px-1.5 py-0.5 text-[9px] font-semibold text-on-surface-variant">
                Theo dõi
              </span>
            )}
            {groupId === "internal" && (
              <span className="inline-flex items-center rounded-full border border-gray-200 bg-gray-50 px-1.5 py-0.5 text-[9px] font-semibold text-gray-400">
                <span className="material-symbols-outlined text-[9px] mr-0.5" aria-hidden="true">lock</span>
                Internal
              </span>
            )}
          </div>
          <p className="text-[11px] text-on-surface-variant leading-tight">{desc.desc}</p>
        </div>
        {editing ? (
          <NumberSpinner
            value={numVal}
            min={min}
            max={max}
            step={step}
            onChange={(v) => onChange(cfgKey, v as never)}
          />
        ) : (
          <span className="font-mono text-lg font-bold text-on-surface shrink-0 tabular-nums tabular-nums">{display}</span>
        )}
      </div>
      {validation && (
        <div
          className={`flex items-start gap-1.5 mb-1.5 px-2 py-1 rounded-md border text-[10px] leading-tight ${
            validation.level === "error"
              ? "bg-error-container/30 text-error border-error/40"
              : "bg-tertiary-container/30 text-tertiary border-tertiary/40"
          }`}
          role={validation.level === "error" ? "alert" : "status"}
        >
          <span className="material-symbols-outlined text-[11px] shrink-0 mt-0.5" aria-hidden="true">
            {validation.level === "error" ? "error" : "warning"}
          </span>
          <span>{validation.message}</span>
        </div>
      )}
      <div className="w-full bg-surface-variant rounded-full h-1.5 overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-300 ${getParamProgressColor(groupId)}`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

/* ─── Auto Compensation Card ───────────────────────────────── */

function AutoCompensationCard() {
  return (
    <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 border-l-4 border-l-teal-500">
      <div className="px-5 py-4 bg-surface-container-low flex items-center gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-teal-50 text-teal-600">
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">event_available</span>
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <p className="text-label-md font-semibold text-on-surface tracking-tight">Nghỉ bù tự động</p>
            <span className="px-1.5 py-0.5 rounded text-[10px] bg-surface-container text-outline uppercase tracking-wide">Chưa áp dụng trong Scheduler v1.0</span>
          </div>
          <p className="text-[11px] text-on-surface-variant leading-tight mt-0.5">Luôn bật trong Scheduler v1.0</p>
        </div>
      </div>
      <div className="p-5 flex items-center justify-between gap-4">
        <div className="flex-1">
          <p className="text-label-sm text-on-surface font-medium">Tạo ngày nghỉ bù</p>
          <p className="text-[11px] text-on-surface-variant mt-0.5">Scheduler v1.0 luôn tạo nghỉ bù sau ca L01</p>
        </div>
        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-label-sm font-semibold bg-secondary-container text-on-secondary-container border border-on-secondary-container/20">
          <span className="h-2 w-2 rounded-full bg-secondary" />
          Always On
        </span>
      </div>
    </div>
  );
}