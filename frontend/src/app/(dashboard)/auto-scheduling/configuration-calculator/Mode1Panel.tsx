"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui";
import { api } from "@/lib/api";
import { useToast } from "@/hooks/useToast";
import { getErrorMessage } from "@/lib/errors";
import type { ConfigCalculatorResponse, SchedulePeriod, ShiftTypeCapacity, Bottleneck } from "@/types/api";

const SHIFT_META: Record<string, { label: string; color: string; bg: string; bar: string }> = {
  L01: { label: "L01 - Trực 24h", color: "text-red-600", bg: "bg-red-50", bar: "bg-red-500" },
  L02: { label: "L02 - Thông tầm", color: "text-blue-600", bg: "bg-blue-50", bar: "bg-blue-500" },
  L03: { label: "L03 - PK Dịch vụ", color: "text-green-600", bg: "bg-green-50", bar: "bg-green-500" },
  L04: { label: "L04 - PK Chuyên gia", color: "text-purple-600", bg: "bg-purple-50", bar: "bg-purple-500" },
};

const SEVERITY_META: Record<string, { color: string; icon: string }> = {
  HIGH: { color: "text-red-700 bg-red-50 border-red-200", icon: "error" },
  MEDIUM: { color: "text-amber-700 bg-amber-50 border-amber-200", icon: "warning" },
  LOW: { color: "text-blue-700 bg-blue-50 border-blue-200", icon: "info" },
};

const SHIFT_TYPES = ["L01", "L02", "L03", "L04"];

export function Mode1Panel({
  periodId,
  period,
}: {
  periodId: number;
  period?: SchedulePeriod | null;
}) {
  const router = useRouter();
  const { success, error: toastError } = useToast();
  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ConfigCalculatorResponse | null>(null);
  const [configLoaded, setConfigLoaded] = useState(false);

  // L04 advanced
  const [l04Ratio, setL04Ratio] = useState(0.5);
  const [l04Specialties, setL04Specialties] = useState<string[]>([]);
  const [l04Strategy, setL04Strategy] = useState("FAIR_DISTRIBUTE");
  const [specialtiesList, setSpecialtiesList] = useState<Array<{ id: number; name: string }>>([]);

  // Global config
  const [maxShiftsPerStaff, setMaxShiftsPerStaff] = useState(5);
  const [maxStaffPerShift, setMaxStaffPerShift] = useState(0);
  const [minStaffPerShift, setMinStaffPerShift] = useState(0);
  const [holidayMode, setHolidayMode] = useState("SKIP");
  const [l04Cross, setL04Cross] = useState(false);
  const [removedShiftTypes, setRemovedShiftTypes] = useState<string[]>([]);

  // Per-shift-type config: { minPerDay, maxPerDay, maxPerWeek }
  const [shiftConfig, setShiftConfig] = useState<Record<string, { min: number; max: number; week: number }>>({
    L01: { min: 1, max: 10, week: 0 },
    L02: { min: 1, max: 10, week: 0 },
    L03: { min: 1, max: 10, week: 0 },
    L04: { min: 1, max: 10, week: 0 },
  });

  // Prefill từ current config khi mount
  useEffect(() => {
    let cancelled = false;
    async function loadCurrentConfig() {
      try {
        const [runtimeResp, autoGenResp, specialtiesResp] = await Promise.all([
          api.getRuntimeConfig(),
          api.getAutoGenConfig(),
          api.getActiveSpecialties(),
        ]);
        if (cancelled) return;
        const runtime: any = (runtimeResp as any).data ?? runtimeResp;
        const autoGen: any = (autoGenResp as any).data ?? autoGenResp;
        const specialtiesRespData: any = (specialtiesResp as any).data ?? specialtiesResp;
        const specialtiesArr = Array.isArray(specialtiesRespData) ? specialtiesRespData : [];
        if (runtime?.maxShiftsPerStaff != null) {
          setMaxShiftsPerStaff(Number(runtime.maxShiftsPerStaff));
        }
        if (runtime?.maxStaffPerShift != null) {
          setMaxStaffPerShift(Number(runtime.maxStaffPerShift));
        }
        if (runtime?.minStaffPerShift != null) {
          setMinStaffPerShift(Number(runtime.minStaffPerShift));
        }
        if (autoGen?.holidayMode) setHolidayMode(autoGen.holidayMode);
        if (Array.isArray(autoGen?.removedShiftTypes)) {
          setRemovedShiftTypes(autoGen.removedShiftTypes);
        }
        if (autoGen?.l04CrossSpecialty != null) {
          setL04Cross(Boolean(autoGen.l04CrossSpecialty));
        }
        if (typeof autoGen?.l04CrossSpecialtyRatio === "number") {
          setL04Ratio(autoGen.l04CrossSpecialtyRatio);
        }
        if (autoGen?.l04BalanceStrategy) {
          setL04Strategy(String(autoGen.l04BalanceStrategy));
        }
        if (Array.isArray(autoGen?.l04AllowedSpecialties)) {
          setL04Specialties(autoGen.l04AllowedSpecialties.filter((x: unknown) => typeof x === "string"));
        }
        setSpecialtiesList(
          specialtiesArr.map((s: any) => ({
            id: Number(s.id),
            name: String(s.name ?? ""),
          }))
        );
        setShiftConfig({
          L01: {
            min: Number(autoGen?.l01MinPerDay ?? 1),
            max: Number(autoGen?.l01MaxPerDay ?? 10),
            week: Number(autoGen?.l01MaxPerWeek ?? 0),
          },
          L02: {
            min: Number(autoGen?.l02MinPerDay ?? 1),
            max: Number(autoGen?.l02MaxPerDay ?? 10),
            week: Number(autoGen?.l02MaxPerWeek ?? 0),
          },
          L03: {
            min: Number(autoGen?.l03MinPerDay ?? 1),
            max: Number(autoGen?.l03MaxPerDay ?? 10),
            week: Number(autoGen?.l03MaxPerWeek ?? 0),
          },
          L04: {
            min: Number(autoGen?.l04MinPerDay ?? 1),
            max: Number(autoGen?.l04MaxPerDay ?? 10),
            week: Number(autoGen?.l04MaxPerWeek ?? 0),
          },
        });
        setConfigLoaded(true);
      } catch {
        // Fallback giữ defaults — user vẫn dùng được nhưng với giá trị mặc định
        setConfigLoaded(true);
      }
    }
    void loadCurrentConfig();
    return () => {
      cancelled = true;
    };
  }, []);

  const toggleRemoved = (st: string) => {
    setRemovedShiftTypes((prev) =>
      prev.includes(st) ? prev.filter((x) => x !== st) : [...prev, st]
    );
  };

  function updateShiftConfig(st: string, field: "min" | "max" | "week", value: number) {
    setShiftConfig((prev) => ({
      ...prev,
      [st]: { ...prev[st], [field]: value },
    }));
  }

  async function handleCalculate() {
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const sc = shiftConfig;
      const resp = await api.configCalculator({
        mode: 1,
        periodId,
        algorithmType: "GREEDY",
        configOverride: {
          maxShiftsPerStaff,
          maxStaffPerShift,
          minStaffPerShift,
          holidayMode,
          l04CrossSpecialtyEnabled: l04Cross,
          l04CrossSpecialtyRatio: l04Ratio,
          l04AllowedSpecialties: l04Specialties,
          l04BalanceStrategy: l04Strategy,
          removedShiftTypes,
          l01MinPerDay: sc.L01.min, l01MaxPerDay: sc.L01.max, l01MaxPerWeek: sc.L01.week,
          l02MinPerDay: sc.L02.min, l02MaxPerDay: sc.L02.max, l02MaxPerWeek: sc.L02.week,
          l03MinPerDay: sc.L03.min, l03MaxPerDay: sc.L03.max, l03MaxPerWeek: sc.L03.week,
          l04MinPerDay: sc.L04.min, l04MaxPerDay: sc.L04.max, l04MaxPerWeek: sc.L04.week,
        },
      });
      setResult(resp?.data ?? null);
    } catch (err) {
      setError(getErrorMessage(err, "Tính toán thất bại"));
    } finally {
      setLoading(false);
    }
  }

  async function handleApply() {
    setApplying(true);
    setError(null);
    try {
      const sc = shiftConfig;
      const [runtimeResp, autoGenResp] = await Promise.all([
        api.getRuntimeConfig(),
        api.getAutoGenConfig(),
      ]);
      const runtime: any = (runtimeResp as any).data ?? runtimeResp;
      const autoGen: any = (autoGenResp as any).data ?? autoGenResp;

      await Promise.all([
        api.updateRuntimeConfig({
          weekendWeight: Number(runtime?.weekendWeight ?? 2.0),
          overnightRecoveryHours: Number(runtime?.overnightRecoveryHours ?? 24),
          greedyCoverageThreshold: Number(runtime?.greedyCoverageThreshold ?? 0.85),
          balanceScoreMin: Number(runtime?.balanceScoreMin ?? 0),
          minStaffPerShift,
          maxStaffPerShift,
          minShiftsPerStaff: Number(runtime?.minShiftsPerStaff ?? 0),
          maxShiftsPerStaff,
        }),
        api.updateAutoGenConfig({
          enabled: Boolean(autoGen?.enabled ?? true),
          l01MinPerDay: sc.L01.min, l02MinPerDay: sc.L02.min,
          l03MinPerDay: sc.L03.min, l04MinPerDay: sc.L04.min,
          l01MaxPerDay: sc.L01.max, l02MaxPerDay: sc.L02.max,
          l03MaxPerDay: sc.L03.max, l04MaxPerDay: sc.L04.max,
          l01MaxPerWeek: sc.L01.week, l02MaxPerWeek: sc.L02.week,
          l03MaxPerWeek: sc.L03.week, l04MaxPerWeek: sc.L04.week,
          holidayMode,
          removedShiftTypes,
          l04CrossSpecialty: l04Cross,
          l04CrossSpecialtyRatio: l04Ratio,
          l04BalanceStrategy: l04Strategy as "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR",
        }),
      ]);
      success("Đã áp dụng cấu hình vào thuật toán");
      router.push("/auto-scheduling/algorithm-config");
    } catch (err) {
      toastError(getErrorMessage(err, "Áp dụng cấu hình thất bại"));
    } finally {
      setApplying(false);
    }
  }

  return (
    <div className="space-y-5">
      {/* Global Config Row */}
      <div className="flex flex-wrap gap-4 items-end">
        <div className="space-y-1 min-w-[140px]">
          <label className="text-[11px] font-medium text-on-surface-variant">
            maxShiftsPerStaff {!configLoaded && <span className="text-on-surface-variant/60">(đang tải...)</span>}
          </label>
          <input type="number" min={0} max={100} value={maxShiftsPerStaff}
            onChange={(e) => setMaxShiftsPerStaff(Number(e.target.value))}
            className="w-full h-9 px-3 rounded-lg border border-outline-variant text-[13px]" />
        </div>
        <div className="space-y-1 min-w-[140px]">
          <label className="text-[11px] font-medium text-on-surface-variant">
            maxStaffPerShift (mỗi ca)
          </label>
          <input type="number" min={0} max={100} value={maxStaffPerShift}
            onChange={(e) => setMaxStaffPerShift(Number(e.target.value))}
            className="w-full h-9 px-3 rounded-lg border border-outline-variant text-[13px]" />
        </div>
        <div className="space-y-1 min-w-[140px]">
          <label className="text-[11px] font-medium text-on-surface-variant">
            minStaffPerShift (mỗi ca)
          </label>
          <input type="number" min={0} max={100} value={minStaffPerShift}
            onChange={(e) => setMinStaffPerShift(Number(e.target.value))}
            className="w-full h-9 px-3 rounded-lg border border-outline-variant text-[13px]" />
        </div>
        <div className="space-y-1 min-w-[140px]">
          <label className="text-[11px] font-medium text-on-surface-variant">Holiday Mode</label>
          <select value={holidayMode} onChange={(e) => setHolidayMode(e.target.value)}
            className="w-full h-9 px-3 rounded-lg border border-outline-variant text-[13px]">
            <option value="SKIP">SKIP — bỏ qua ngày lễ</option>
            <option value="PARTIAL">PARTIAL — sinh có giới hạn</option>
          </select>
        </div>
        <div className="space-y-1 min-w-[140px]">
          <label className="text-[11px] font-medium text-on-surface-variant">L04 Cross-Specialty</label>
          <label className="flex items-center gap-2 h-9 px-3 rounded-lg border border-outline-variant cursor-pointer">
            <input type="checkbox" checked={l04Cross} onChange={(e) => setL04Cross(e.target.checked)} />
            <span className="text-[13px]">{l04Cross ? "BẬT" : "TẮT"}</span>
          </label>
        </div>
        {l04Cross && (
          <>
            <div className="space-y-1 min-w-[140px]">
              <label className="text-[11px] font-medium text-on-surface-variant">L04 Ratio (0.0-1.0)</label>
              <input type="number" min={0} max={1} step={0.05} value={l04Ratio}
                onChange={(e) => setL04Ratio(Number(e.target.value))}
                className="w-full h-9 px-3 rounded-lg border border-outline-variant text-[13px]" />
            </div>
            <div className="space-y-1 min-w-[200px]">
              <label className="text-[11px] font-medium text-on-surface-variant">L04 Strategy</label>
              <select value={l04Strategy} onChange={(e) => setL04Strategy(e.target.value)}
                className="w-full h-9 px-3 rounded-lg border border-outline-variant text-[13px]">
                <option value="STRICT_MATCH_ONLY">STRICT_MATCH_ONLY</option>
                <option value="FAIR_DISTRIBUTE">FAIR_DISTRIBUTE</option>
                <option value="WEIGHTED_FAIR">WEIGHTED_FAIR</option>
              </select>
            </div>
            <div className="space-y-1 min-w-[280px]">
              <label className="text-[11px] font-medium text-on-surface-variant">L04 Allowed Specialties</label>
              <div className="flex gap-1.5 flex-wrap p-2 rounded-lg border border-outline-variant min-h-[36px]">
                {specialtiesList.length === 0 && (
                  <span className="text-[11px] text-on-surface-variant/60">đang tải...</span>
                )}
                {specialtiesList.map((sp) => {
                  const selected = l04Specialties.includes(sp.name);
                  return (
                    <button key={sp.id} type="button"
                      onClick={() => {
                        setL04Specialties((prev) =>
                          prev.includes(sp.name)
                            ? prev.filter((x) => x !== sp.name)
                            : [...prev, sp.name]
                        );
                      }}
                      className={`px-2.5 py-1 rounded-lg text-[11px] font-medium border transition-colors ${
                        selected
                          ? "bg-primary text-on-primary border-primary"
                          : "bg-surface-container text-on-surface-variant border-outline-variant"
                      }`}>
                      {sp.name}
                    </button>
                  );
                })}
              </div>
            </div>
          </>
        )}
        <div className="space-y-1 min-w-[200px]">
          <label className="text-[11px] font-medium text-on-surface-variant">Removed Shift Types</label>
          <div className="flex gap-1.5 flex-wrap">
            {SHIFT_TYPES.map((st) => (
              <button key={st} onClick={() => toggleRemoved(st)}
                className={`px-2.5 py-1.5 rounded-lg text-[11px] font-medium border transition-colors ${
                  removedShiftTypes.includes(st)
                    ? "bg-red-100 text-red-700 border-red-300"
                    : "bg-surface-container text-on-surface-variant border-outline-variant"
                }`}>
                {st} {removedShiftTypes.includes(st) ? "✕" : "✓"}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Per Shift Type Config Table */}
      <div className="border border-outline-variant rounded-xl overflow-hidden">
        <table className="w-full text-[13px] border-collapse">
          <thead>
            <tr className="bg-surface-container-low border-b border-outline-variant">
              <th className="py-2.5 px-3 text-left font-medium text-on-surface-variant">Loại ca</th>
              <th className="py-2.5 px-3 text-center font-medium text-on-surface-variant">Nhu cầu/ngày</th>
              <th className="py-2.5 px-3 text-center font-medium text-on-surface-variant">Trần ca/ngày</th>
              <th className="py-2.5 px-3 text-center font-medium text-on-surface-variant">Tối đa/người/tuần</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant">
            {SHIFT_TYPES.map((st) => {
              const meta = SHIFT_META[st];
              const cfg = shiftConfig[st];
              const invalid = cfg.min > cfg.max;
              return (
                <tr key={st} className={`hover:bg-surface-container-lowest ${invalid ? "bg-red-50/40" : ""}`}>
                  <td className="py-2 px-3">
                    <span className={`font-mono font-bold ${meta.color}`}>{meta.label}</span>
                  </td>
                  <td className="py-2 px-3 text-center">
                    <input type="number" min={0} max={50} value={cfg.min}
                      onChange={(e) => updateShiftConfig(st, "min", Number(e.target.value))}
                      className={`w-20 h-8 px-2 rounded-lg border text-center text-[13px] ${
                        invalid ? "border-red-400 bg-red-50" : "border-outline-variant"
                      }`} />
                  </td>
                  <td className="py-2 px-3 text-center">
                    <input type="number" min={0} max={50} value={cfg.max}
                      onChange={(e) => updateShiftConfig(st, "max", Number(e.target.value))}
                      className={`w-20 h-8 px-2 rounded-lg border text-center text-[13px] ${
                        invalid ? "border-red-400 bg-red-50" : "border-outline-variant"
                      }`} />
                  </td>
                  <td className="py-2 px-3 text-center">
                    <input type="number" min={0} max={20} value={cfg.week}
                      onChange={(e) => updateShiftConfig(st, "week", Number(e.target.value))}
                      className="w-20 h-8 px-2 rounded-lg border border-outline-variant text-center text-[13px]" />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {(() => {
        const invalidRows = SHIFT_TYPES.filter((st) => shiftConfig[st].min > shiftConfig[st].max);
        if (invalidRows.length === 0) return null;
        return (
          <div className="p-3 bg-red-50 border border-red-200 rounded-xl text-[12px] text-red-700">
            <span className="font-medium">⚠ Lỗi cấu hình:</span>{" "}
            {invalidRows.map((st) => `${st} (min=${shiftConfig[st].min} > max=${shiftConfig[st].max})`).join("; ")}
            {" — "}Nhu cầu/ngày phải ≤ Trần ca/ngày.
          </div>
        );
      })()}

      <div className="flex items-center gap-3 flex-wrap">
        <Button onClick={handleCalculate} disabled={loading}
          icon={<span className="material-symbols-outlined text-[18px]">calculate</span>}>
          {loading ? "Đang tính toán..." : "Tính toán capacity"}
        </Button>
        <Button onClick={handleApply} disabled={applying} variant="primary"
          icon={<span className="material-symbols-outlined text-[18px]">check_circle</span>}>
          {applying ? "Đang áp dụng..." : "Áp dụng cấu hình"}
        </Button>
      </div>

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-[13px] text-red-700">
          {error}
        </div>
      )}

      {/* Results */}
      {result && (
        <div className="space-y-5">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div className="p-4 bg-surface-container-low rounded-xl border border-outline-variant">
              <p className="text-[11px] text-on-surface-variant">Tổng requirement</p>
              <p className="text-title-md font-bold text-on-surface">{result.totalRequirement}</p>
            </div>
            <div className="p-4 bg-surface-container-low rounded-xl border border-outline-variant">
              <p className="text-[11px] text-on-surface-variant">Capacity tối đa</p>
              <p className="text-title-md font-bold text-primary">{result.totalCapacity}</p>
            </div>
            <div className="p-4 bg-surface-container-low rounded-xl border border-outline-variant">
              <p className="text-[11px] text-on-surface-variant">Đã assign (mô phỏng)</p>
              <p className="text-title-md font-bold text-on-surface">{result.totalAssigned}</p>
            </div>
            <div className="p-4 bg-surface-container-low rounded-xl border border-outline-variant">
              <p className="text-[11px] text-on-surface-variant">Coverage</p>
              <p className={`text-title-md font-bold ${(result.expectedCoverage ?? 0) >= 0.8 ? "text-green-600" : "text-amber-600"}`}>
                {((result.expectedCoverage ?? 0) * 100).toFixed(0)}%
              </p>
            </div>
          </div>

          <div className="space-y-3">
            <h3 className="text-label-sm font-semibold text-on-surface">Capacity theo loại ca</h3>
            {result.perShiftType?.map((st: ShiftTypeCapacity) => {
              const meta = SHIFT_META[st.shiftType] || { label: st.shiftType, color: "", bg: "", bar: "bg-gray-500" };
              const maxVal = Math.max(st.requirement, st.maxPossible, 1);
              return (
                <div key={st.shiftType} className="p-4 bg-surface-container-low rounded-xl border border-outline-variant">
                  <div className="flex items-center justify-between mb-2">
                    <span className={`font-mono font-bold text-[13px] ${meta.color}`}>{meta.label}</span>
                    <span className="text-[11px] text-on-surface-variant">
                      {st.assigned}/{st.requirement} assigned
                    </span>
                  </div>
                  <div className="h-2 bg-surface-container-high rounded-full overflow-hidden mb-1">
                    <div className={`h-full ${meta.bar} opacity-40 rounded-full`}
                      style={{ width: `${(st.requirement / maxVal) * 100}%` }} />
                  </div>
                  <div className="h-2 bg-surface-container-high rounded-full overflow-hidden">
                    <div className={`h-full ${meta.bar} rounded-full`}
                      style={{ width: `${(st.maxPossible / maxVal) * 100}%` }} />
                  </div>
                  <div className="flex justify-between text-[10px] text-on-surface-variant mt-1">
                    <span>Req: {st.requirement} | Max: {st.maxPossible}</span>
                    <span>Domain: avg {st.avgDomainSize.toFixed(1)} min {st.minDomainSize}</span>
                  </div>
                  {st.bottleneckCount > 0 && (
                    <span className="text-[10px] text-red-600 font-medium">
                      ⚠ {st.bottleneckCount} bottleneck variables
                    </span>
                  )}
                  {st.perSpecialty && Object.keys(st.perSpecialty).length > 0 && (
                    <div className="mt-1 text-[10px] text-on-surface-variant flex gap-2 flex-wrap">
                      {Object.entries(st.perSpecialty).map(([spec, count]) => (
                        <span key={spec}>{spec}: {count} ca</span>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {result.bottlenecks && result.bottlenecks.length > 0 && (
            <div className="space-y-2">
              <h3 className="text-label-sm font-semibold text-on-surface">Bottleneck ⚠</h3>
              {result.bottlenecks.map((b: Bottleneck, i: number) => {
                const sMeta = SEVERITY_META[b.severity] || SEVERITY_META.LOW;
                return (
                  <div key={i} className={`p-3 rounded-xl border ${sMeta.color}`}>
                    <div className="flex items-start gap-2">
                      <span className="material-symbols-outlined text-[16px] mt-0.5">{sMeta.icon}</span>
                      <div>
                        <p className="text-[12px] font-medium">{b.shiftType}: {b.message}</p>
                        {b.suggestion && (
                          <p className="text-[11px] opacity-80 mt-1">💡 {b.suggestion}</p>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {result.holidayImpact && result.holidayImpact.holidayDaysCount > 0 && (
            <div className="p-3 bg-blue-50 border border-blue-200 rounded-xl text-[12px] text-blue-800">
              <span className="font-medium">Kỳ nghỉ:</span> {result.holidayImpact.holidayDaysCount} ngày lễ
              (mode: {result.holidayImpact.mode})
            </div>
          )}

          {result.algorithmInfo && (
            <div className="text-[11px] text-on-surface-variant border-t border-outline-variant pt-3 mt-3">
              Thuật toán: {result.algorithmInfo.type} · {result.algorithmInfo.executionTimeMs}ms · Kết thúc: {result.algorithmInfo.terminatedBy}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
