"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui";
import { api } from "@/lib/api";
import { useToast } from "@/hooks/useToast";
import { getErrorMessage } from "@/lib/errors";
import type { ConfigCalculatorResponse, SchedulePeriod, Bottleneck, ConfigChange, ShiftTypeCapacity } from "@/types/api";

export function Mode2Panel({
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

  // Target inputs (per type)
  const [targetL01, setTargetL01] = useState(30);
  const [targetL02, setTargetL02] = useState(30);
  const [targetL03, setTargetL03] = useState(30);
  const [targetL04, setTargetL04] = useState(60);

  // Group toggles (allow backend to tune these config groups)
  const [enableStaffing, setEnableStaffing] = useState(true);
  const [enablePerShift, setEnablePerShift] = useState(true);
  const [enableHoliday, setEnableHoliday] = useState(true);
  const [enableL04, setEnableL04] = useState(true);

  async function handleCalculate() {
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const enabledGroups: string[] = [];
      if (enableStaffing) enabledGroups.push("staffing");
      if (enablePerShift) enabledGroups.push("perShift");
      if (enableHoliday) enabledGroups.push("holiday");
      if (enableL04) enabledGroups.push("l04");

      const body: Record<string, unknown> = {
        mode: 2,
        periodId,
        targetShifts: {
          L01: targetL01,
          L02: targetL02,
          L03: targetL03,
          L04: targetL04,
        },
        enabledGroups,
      };
      body.algorithmType = "GREEDY";
      const resp = await api.configCalculator(body as any);
      setResult(resp?.data ?? null);
    } catch (err) {
      setError(getErrorMessage(err, "Tính toán thất bại"));
    } finally {
      setLoading(false);
    }
  }

  async function handleApply() {
    if (!result?.recommendedConfig) {
      toastError("Chưa có cấu hình đề xuất — hãy tính toán trước");
      return;
    }
    setApplying(true);
    setError(null);
    try {
      const rec = result.recommendedConfig as Record<string, unknown>;
      const [runtimeResp, autoGenResp] = await Promise.all([
        api.getRuntimeConfig(),
        api.getAutoGenConfig(),
      ]);
      const runtime: any = (runtimeResp as any).data ?? runtimeResp;
      const autoGen: any = (autoGenResp as any).data ?? autoGenResp;

      const num = (v: unknown, fallback: number) =>
        typeof v === "number" ? v : fallback;
      const str = (v: unknown, fallback: string) =>
        typeof v === "string" && v.length > 0 ? v : fallback;
      const arr = (v: unknown): string[] =>
        Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];

      await Promise.all([
        api.updateRuntimeConfig({
          weekendWeight: num(rec.weekendWeight, Number(runtime?.weekendWeight ?? 2.0)),
          overnightRecoveryHours: num(rec.overnightRecoveryHours, Number(runtime?.overnightRecoveryHours ?? 24)),
          greedyCoverageThreshold: num(rec.greedyCoverageThreshold, Number(runtime?.greedyCoverageThreshold ?? 0.85)),
          balanceScoreMin: Number(runtime?.balanceScoreMin ?? 0),
          minStaffPerShift: num(rec.minStaffPerShift, 0),
          maxStaffPerShift: num(rec.maxStaffPerShift, 0),
          minShiftsPerStaff: num(rec.minShiftsPerStaff, 0),
          maxShiftsPerStaff: num(rec.maxShiftsPerStaff, 0),
        }),
        api.updateAutoGenConfig({
          enabled: Boolean(autoGen?.enabled ?? true),
          l01MinPerDay: num(rec.l01MinPerDay, 1),
          l02MinPerDay: num(rec.l02MinPerDay, 1),
          l03MinPerDay: num(rec.l03MinPerDay, 1),
          l04MinPerDay: num(rec.l04MinPerDay, 1),
          l01MaxPerDay: num(rec.l01MaxPerDay, 10),
          l02MaxPerDay: num(rec.l02MaxPerDay, 10),
          l03MaxPerDay: num(rec.l03MaxPerDay, 10),
          l04MaxPerDay: num(rec.l04MaxPerDay, 10),
          l01MaxPerWeek: num(rec.l01MaxPerWeek, 0),
          l02MaxPerWeek: num(rec.l02MaxPerWeek, 0),
          l03MaxPerWeek: num(rec.l03MaxPerWeek, 0),
          l04MaxPerWeek: num(rec.l04MaxPerWeek, 0),
          holidayMode: str(rec.holidayMode, "SKIP"),
          removedShiftTypes: arr(rec.removedShiftTypes),
          l04BalanceStrategy: str(rec.l04BalanceStrategy, "FAIR_DISTRIBUTE") as
            | "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR",
        }),
      ]);
      success("Đã áp dụng cấu hình đề xuất vào thuật toán");
      router.push("/auto-scheduling/algorithm-config");
    } catch (err) {
      toastError(getErrorMessage(err, "Áp dụng cấu hình thất bại"));
    } finally {
      setApplying(false);
    }
  }

  return (
    <div className="space-y-5">
      {/* Target Input */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {[
          { label: "L01 - Trực 24h", val: targetL01, set: setTargetL01, color: "text-red-600" },
          { label: "L02 - Thông tầm", val: targetL02, set: setTargetL02, color: "text-blue-600" },
          { label: "L03 - PK Dịch vụ", val: targetL03, set: setTargetL03, color: "text-green-600" },
          { label: "L04 - PK Chuyên gia", val: targetL04, set: setTargetL04, color: "text-purple-600" },
        ].map((item) => (
          <div key={item.label} className="space-y-1">
            <label className={`text-[11px] font-medium ${item.color}`}>{item.label}</label>
            <input type="number" min={0} max={500} value={item.val}
              onChange={(e) => item.set(Math.max(0, Number(e.target.value)))}
              className="w-full h-10 px-3 rounded-lg border border-outline-variant text-[14px] font-mono" />
          </div>
        ))}
      </div>

      <div className="flex items-center gap-3 flex-wrap">
        <Button onClick={handleCalculate} disabled={loading}
          icon={<span className="material-symbols-outlined text-[18px]">target</span>}>
          {loading ? "Đang tính..." : "Tìm cấu hình"}
        </Button>
        <Button onClick={handleApply} disabled={applying || !result?.recommendedConfig}
          icon={<span className="material-symbols-outlined text-[18px]">check_circle</span>}>
          {applying ? "Đang áp dụng..." : "Áp dụng cấu hình đề xuất"}
        </Button>
      </div>

      {/* Group toggles — cho phép backend điều chỉnh nhóm nào */}
      <div className="p-4 bg-surface-container-low rounded-xl border border-outline-variant space-y-3">
        <div>
          <p className="text-[12px] font-medium text-on-surface">Cho phép điều chỉnh các nhóm cấu hình:</p>
          <p className="text-[10px] text-on-surface-variant/70 mt-0.5">
            Bật/tắt để backend biết nhóm nào được phép tune khi tìm cấu hình đạt target.
          </p>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {[
            { key: "staffing", label: "Giới hạn xếp lịch", desc: "maxShiftsPerStaff, maxStaffPerShift, minStaffPerShift", state: enableStaffing, set: setEnableStaffing },
            { key: "perShift", label: "Giới hạn theo loại ca", desc: "L01-L04: minPerDay, maxPerDay, maxPerWeek", state: enablePerShift, set: setEnablePerShift },
            { key: "holiday", label: "Ngày lễ", desc: "holidayMode, removedShiftTypes", state: enableHoliday, set: setEnableHoliday },
            { key: "l04", label: "PK Chuyên gia", desc: "l04CrossSpecialty, ratio, strategy, specialties", state: enableL04, set: setEnableL04 },
          ].map((g) => (
            <label key={g.key}
              className={`flex flex-col gap-1 p-3 rounded-lg border cursor-pointer transition-colors ${
                g.state
                  ? "bg-primary-fixed border-primary text-primary"
                  : "bg-surface-container-lowest border-outline-variant text-on-surface-variant"
              }`}>
              <span className="flex items-center gap-2 text-[12px] font-medium">
                <input type="checkbox" checked={g.state} onChange={(e) => g.set(e.target.checked)} />
                {g.label}
              </span>
              <span className="text-[10px] opacity-70">{g.desc}</span>
            </label>
          ))}
        </div>
      </div>

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-xl text-[13px] text-red-700">
          {error}
        </div>
      )}

      {/* Results */}
      {result && (
        <div className="space-y-5">
          {/* Feasibility badge */}
          <div className={`p-4 rounded-xl border ${
            result.feasible
              ? "bg-green-50 border-green-200 text-green-800"
              : "bg-red-50 border-red-200 text-red-800"
          }`}>
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[20px]">
                {result.feasible ? "check_circle" : "cancel"}
              </span>
              <span className="font-semibold text-[14px]">
                {result.feasible
                  ? "Mục tiêu khả thi!"
                  : "Mục tiêu không khả thi với cấu hình hiện tại"}
              </span>
            </div>
            {result.message && (
              <p className="text-[12px] mt-2 opacity-80">{result.message}</p>
            )}
            {result.recommendedAlgorithm && (
              <p className="text-[12px] mt-1">
                Thuật toán đề xuất: <strong>{result.recommendedAlgorithm}</strong>
              </p>
            )}
          </div>

          {/* Target vs Achieved */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {["L01", "L02", "L03", "L04"].map((st) => {
              const target = [targetL01, targetL02, targetL03, targetL04][
                ["L01", "L02", "L03", "L04"].indexOf(st)
              ];
              const achieved = result.perShiftType?.find((s: ShiftTypeCapacity) => s.shiftType === st);
              return (
                <div key={st} className="p-3 bg-surface-container-low rounded-xl border border-outline-variant">
                  <p className="text-[11px] font-mono font-bold">{st}</p>
                  <p className="text-[13px]">
                    Target: <strong>{target}</strong>
                  </p>
                  <p className="text-[13px]">
                    Đạt: <strong className={achieved && achieved.assigned >= target ? "text-green-600" : "text-red-600"}>
                      {achieved?.assigned ?? 0}
                    </strong>
                  </p>
                </div>
              );
            })}
          </div>

          {/* Config Changes */}
          {result.configChanges && result.configChanges.length > 0 && (
            <div>
              <h3 className="text-label-sm font-semibold text-on-surface mb-2">Thay đổi cấu hình đề xuất</h3>
              {(() => {
                const groups: Array<{
                  key: string;
                  title: string;
                  match: (field: string) => boolean;
                }> = [
                  {
                    key: "staffing",
                    title: "Giới hạn xếp lịch",
                    match: (f) => f.includes("StaffPerStaff") || f.includes("StaffPerShift"),
                  },
                  {
                    key: "perShift",
                    title: "Giới hạn theo loại ca (L01-L04)",
                    match: (f) => /L0[1-4](MinPerDay|MaxPerDay|MaxPerWeek)/.test(f),
                  },
                  {
                    key: "holiday",
                    title: "Ngày lễ",
                    match: (f) => f === "holidayMode" || f === "removedShiftTypes",
                  },
                  {
                    key: "l04",
                    title: "PK Chuyên gia (cross-specialty)",
                    match: (f) => f.startsWith("l04"),
                  },
                ];
                const groupedChanges = groups.map((g) => ({
                  ...g,
                  changes: (result.configChanges ?? []).filter((c: ConfigChange) => g.match(c.field)),
                }));
                const empty = groupedChanges.every((g) => g.changes.length === 0);
                if (empty) {
                  return (
                    <p className="text-[12px] text-on-surface-variant p-3 bg-surface-container-low rounded-lg">
                      Không có thay đổi nào — cấu hình hiện tại đã đạt target.
                    </p>
                  );
                }
                return (
                  <div className="space-y-4">
                    {groupedChanges.map((g) => (
                      <div key={g.key} className="rounded-xl border border-outline-variant overflow-hidden">
                        <div className="px-3 py-2 bg-surface-container-low border-b border-outline-variant flex items-center gap-2">
                          <span className="text-[12px] font-semibold text-on-surface">{g.title}</span>
                          <span className="text-[10px] text-on-surface-variant/70">
                            ({g.changes.length} thay đổi)
                          </span>
                        </div>
                        {g.changes.length === 0 ? (
                          <p className="px-3 py-2 text-[11px] text-on-surface-variant/60 italic">
                            Không có điều chỉnh.
                          </p>
                        ) : (
                          <table className="w-full text-[12px] border-collapse">
                            <thead>
                              <tr className="bg-surface-container-lowest/50 border-b border-outline-variant">
                                <th className="py-2 px-3 text-left font-medium text-on-surface-variant">Tham số</th>
                                <th className="py-2 px-3 text-left font-medium text-on-surface-variant">Giá trị cũ</th>
                                <th className="py-2 px-3 text-left font-medium text-on-surface-variant">Giá trị mới</th>
                                <th className="py-2 px-3 text-left font-medium text-on-surface-variant">Lý do</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-outline-variant">
                              {g.changes.map((c: ConfigChange, i: number) => (
                                <tr key={i} className="hover:bg-surface-container-lowest/50">
                                  <td className="py-2 px-3 font-mono font-medium">{c.field}</td>
                                  <td className="py-2 px-3">{JSON.stringify(c.fromValue)}</td>
                                  <td className="py-2 px-3 font-semibold text-primary">{JSON.stringify(c.toValue)}</td>
                                  <td className="py-2 px-3 text-on-surface-variant">{c.reason}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        )}
                      </div>
                    ))}
                  </div>
                );
              })()}
            </div>
          )}

          {/* Blockers */}
          {result.bottlenecks && result.bottlenecks.length > 0 && !result.feasible && (
            <div>
              <h3 className="text-label-sm font-semibold text-on-surface mb-2">Nguyên nhân không khả thi</h3>
              {result.bottlenecks.map((b: Bottleneck, i: number) => (
                <div key={i} className={`p-3 mb-2 rounded-xl border ${
                  b.severity === "HIGH"
                    ? "bg-red-50 border-red-200 text-red-700"
                    : "bg-amber-50 border-amber-200 text-amber-700"
                }`}>
                  <p className="text-[12px] font-medium">{b.shiftType}: {b.message}</p>
                  {b.suggestion && (
                    <p className="text-[11px] mt-1 opacity-80">💡 {b.suggestion}</p>
                  )}
                </div>
              ))}
            </div>
          )}

          {/* Summary stats */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-[11px] text-on-surface-variant border-t border-outline-variant pt-3">
            <div>Tổng requirement: <strong>{result.totalRequirement}</strong></div>
            <div>Tổng capacity: <strong>{result.totalCapacity}</strong></div>
            <div>Coverage: <strong>{((result.expectedCoverage ?? 0) * 100).toFixed(0)}%</strong></div>
            <div>Fairness: <strong>{(result.expectedFairness ?? 0).toFixed(2)}</strong></div>
          </div>

          {result.algorithmInfo && (
            <div className="text-[11px] text-on-surface-variant">
              Thuật toán: {result.algorithmInfo.type} · {result.algorithmInfo.executionTimeMs}ms · {result.algorithmInfo.terminatedBy}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
