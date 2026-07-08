"use client";

import { useCallback, useEffect, useState } from "react";
import type { PresetKey } from "@/components/algorithm-config/PresetSelector";
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
  calcProgressPct,
  formatParamDisplay,
  getParamBounds,
  getParamProgressColor,
} from "./paramConfig";
import { ShiftTypeGroupCard } from "./ShiftTypeGroupCard";
import { HolidayModeField } from "./HolidayModeField";
import { RemovedShiftTypesField } from "./RemovedShiftTypesField";
import { L04SpecialtyConfig } from "./L04CrossSpecialtyCard";
import { ConfigDiffModal } from "./ConfigDiffModal";
import { getChangedKeys } from "./diff";
import { mergeRuntimeAndAutoGen } from "./merge";
import { AutoCalculateDialog, type AutoCalculateResult } from "./AutoCalculateDialog";

type Props = { onSaved?: () => void };

export function RuntimeConfigEditor({ onSaved }: Props) {
  const { success, error } = useToast();
  const [config, setConfig] = useState<RuntimeConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<RuntimeConfig | null>(null);
  const [activePreset, setActivePreset] = useState<PresetKey | null>(null);
  const [showDiff, setShowDiff] = useState(false);
  const [sandboxOpen, setSandboxOpen] = useState(false);
  const [autoCalcOpen, setAutoCalcOpen] = useState(false);
  const [allSpecialties, setAllSpecialties] = useState<string[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [res, resAutoGen, specialtiesRes] = await Promise.all([
        api.getRuntimeConfig(),
        api.getAutoGenConfig(),
        api.getActiveSpecialties(),
      ]);
      const data = (res as unknown as { data: RuntimeConfig }).data;
      const autoGen = (resAutoGen as unknown as { data: RuntimeConfig }).data;
      const specialties = ((specialtiesRes as unknown as { data: { id: number; name: string }[] }).data ?? []).map(s => s.name);
      setAllSpecialties(specialties);
      const merged = mergeRuntimeAndAutoGen(data, autoGen);
      setConfig(merged);
      setForm(merged);
    } catch {
      error("Không thể tải cấu hình runtime");
    } finally {
      setLoading(false);
    }
  }, [error]);

  useEffect(() => { void load(); }, [load]);

  // Re-detect preset khi form thay đổi
  useEffect(() => {
    if (form) setActivePreset(detectPreset(form));
  }, [form]);

  function applyPreset(key: PresetKey) {
    const preset = ALGORITHM_PRESETS[key];
    setForm(prev => prev ? { ...prev, ...preset.config } : prev);
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
        l04CrossSpecialty: form.l04CrossSpecialty ?? false,
        l04CrossSpecialtyRatio: form.l04CrossSpecialtyRatio ?? 0.3,
      };
      await Promise.all([
        api.updateRuntimeConfig(form),
        api.updateAutoGenConfig(autoGenPayload),
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
  }

  function setField<K extends keyof RuntimeConfig>(key: K, value: RuntimeConfig[K]) {
    setForm(prev => prev ? { ...prev, [key]: value } : prev);
  }

  function handleAutoCalculate(result: AutoCalculateResult) {
    setForm(prev => prev ? { ...prev, ...result } : prev);
    setEditing(true);
    success("Đã áp dụng giá trị tự động tính. Nhấn 'Lưu thay đổi' để lưu vào DB.");
  }

  if (loading) return <EditorSkeleton />;
  if (!config || !form) return null;

  const changes = getChangedKeys(config, form);
  const isDirty = changes.length > 0;

  return (
    <div className="space-y-5">
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
            {editing ? (
              <>
                <Button variant="secondary" size="sm" onClick={handleReset}>Hủy bỏ</Button>
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
        <PresetSelector presets={ALGORITHM_PRESETS} activePreset={activePreset} onApply={applyPreset} />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        {PARAM_GROUPS.map(group => (
          <ParamGroupCard
            key={group.id}
            group={group}
            form={form}
            editing={editing}
            onChange={setField}
          />
        ))}
        <AutoCompensationCard form={form} editing={editing} onChange={setField} />
        <L04SpecialtyConfig
          enabled={form.l04CrossSpecialty ?? false}
          ratio={form.l04CrossSpecialtyRatio ?? 0.3}
          allowedSpecialties={form.l04AllowedSpecialties ?? []}
          allSpecialties={allSpecialties}
          editing={editing}
          onChange={(enabled, ratio, allowedSpecialties) => {
            setForm(prev => prev ? { ...prev, l04CrossSpecialty: enabled, l04CrossSpecialtyRatio: ratio, l04AllowedSpecialties: allowedSpecialties } : prev);
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
        initialValues={{
          periodDays: 30,
          periodWeeks: 4,
          targetsPerStaffPerMonth: { L01: 7, L02: 8, L03: 9, L04: 16 },
          eligibleStaff: { L01: 8, L02: 8, L03: 8, L04: 20 },
        }}
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

/* ─── Param Group Card ─────────────────────────────────────── */

type ParamGroupCardProps = {
  group: typeof PARAM_GROUPS[number];
  form: RuntimeConfig;
  editing: boolean;
  onChange: <K extends keyof RuntimeConfig>(key: K, value: RuntimeConfig[K]) => void;
};

function ParamGroupCard({ group, form, editing, onChange }: ParamGroupCardProps) {
  return (
    <div className={`bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 ${group.accent}`}>
      <div className="px-5 py-4 bg-surface-container-low flex items-center gap-3">
        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${group.bg} ${group.color}`}>
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{group.icon}</span>
        </div>
        <p className="text-label-md font-semibold text-on-surface tracking-tight">{group.label}</p>
      </div>
      <div className="p-5 space-y-5">
        {group.params.map(param => {
          const desc = group.descriptions[param] ?? { label: param, desc: "", hint: "" };
          const cfgKey = PARAM_KEY_TO_CFG[param] ?? "maxIterations";
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

type AutoCompProps = {
  form: RuntimeConfig;
  editing: boolean;
  onChange: <K extends keyof RuntimeConfig>(key: K, value: RuntimeConfig[K]) => void;
};

function AutoCompensationCard({ form, editing, onChange }: AutoCompProps) {
  return (
    <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 border-l-4 border-l-teal-500">
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
          <button
            type="button"
            role="switch"
            aria-checked={form.autoCompensationEnabled}
            onClick={() => onChange("autoCompensationEnabled", !form.autoCompensationEnabled)}
            className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${
              form.autoCompensationEnabled ? "bg-teal-500 border-teal-500" : "bg-surface-container-high border-outline"
            }`}
          >
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
  );
}