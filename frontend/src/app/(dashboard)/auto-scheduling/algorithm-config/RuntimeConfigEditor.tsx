"use client";

import { useCallback, useEffect, useState } from "react";
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

export function RuntimeConfigEditor({ onSaved }: Props) {
  const { success, error } = useToast();
  const [config, setConfig] = useState<RuntimeConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<RuntimeConfig | null>(null);
  const [activePreset, setActivePreset] = useState<PresetKey | null>(null);

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

      {/* Preset selector */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-5">
        <div className="flex items-center gap-2 mb-3">
          <span className="material-symbols-outlined text-primary text-[18px]" aria-hidden="true">bookmark</span>
          <p className="text-title-sm font-semibold text-on-surface">Cau hinh nhanh</p>
          {isDirty() && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-tertiary-container text-tertiary border border-tertiary/20">
              <span className="material-symbols-outlined text-[12px]">edit</span>
              Tu chinh
            </span>
          )}
        </div>
        <PresetSelector activePreset={activePreset} onApply={applyPreset} />
      </div>

      {/* 3 core runtime params */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <SliderField
          label="Weekend Weight"
          desc="Trong so cuoi tuan"
          value={form.weekendWeight ?? 1}
          min={1} max={5} step={0.05}
          format={(v) => `${v.toFixed(1)}x`}
          onChange={(v) => setField("weekendWeight", v)}
        />
        <SliderField
          label="Coverage Threshold"
          desc="Nguong phu lich"
          value={form.greedyCoverageThreshold ?? 0.8}
          min={0.3} max={1} step={0.05}
          format={(v) => `${Math.round(v * 100)}%`}
          onChange={(v) => setField("greedyCoverageThreshold", v)}
        />
        <SliderField
          label="Balance Score Min"
          desc="Diem can bang toi thieu"
          value={form.balanceScoreMin ?? 0.7}
          min={0.3} max={1} step={0.05}
          format={(v) => `${Math.round(v * 100)}%`}
          onChange={(v) => setField("balanceScoreMin", v)}
        />
      </div>

      {/* Auto-gen: shift type min/max per day */}
      <div>
        <div className="flex items-center gap-2 mb-3">
          <span className="material-symbols-outlined text-on-surface-variant text-[16px]" aria-hidden="true">calendar_view_month</span>
          <p className="text-label-sm font-medium text-on-surface-variant">Gioi han theo loai lich (min/max ngay)</p>
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
            </div>
          ))}
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
