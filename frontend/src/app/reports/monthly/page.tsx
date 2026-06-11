"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { SchedulePeriod, Schedule, ConflictCheckResponse } from "@/types/api";

export default function ReportsMonthlyPage() {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<SchedulePeriod | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingPeriod, setLoadingPeriod] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const fetchPeriods = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.get<SchedulePeriod[]>("/periods");
      const all = data ?? [];
      setPeriods(all);
      const active = all.find((p) => p.status === "PUBLISHED" || p.status === "DRAFT");
      if (active) setSelectedPeriod(active);
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể tải danh sách kỳ lịch."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchPeriods();
  }, [fetchPeriods]);

  const fetchPeriodData = useCallback(async (periodId: number) => {
    try {
      setLoadingPeriod(true);
      const [schedRes, conflictRes] = await Promise.allSettled([
        api.get<Schedule[]>(`/schedules/period/${periodId}`),
        api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`),
      ]);
      if (schedRes.status === "fulfilled") setSchedules(schedRes.value ?? []);
      if (conflictRes.status === "fulfilled") setConflictData(conflictRes.value);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi tải dữ liệu kỳ lịch."));
    } finally {
      setLoadingPeriod(false);
    }
  }, []);

  useEffect(() => {
    if (selectedPeriod) void fetchPeriodData(selectedPeriod.id);
  }, [selectedPeriod, fetchPeriodData]);

  const stats = useMemo(() => {
    const L01 = schedules.filter((s) => s.shiftType.id === "L01").length;
    const L02 = schedules.filter((s) => s.shiftType.id === "L02").length;
    const L03 = schedules.filter((s) => s.shiftType.id === "L03").length;
    const L04 = schedules.filter((s) => s.shiftType.id === "L04").length;
    const uniqueStaff = new Set(schedules.map((s) => s.staff.id)).size;
    const conflicts = conflictData?.totalConflicts ?? 0;
    return { L01, L02, L03, L04, uniqueStaff, conflicts, total: schedules.length };
  }, [schedules, conflictData]);

  const coverageRate = useMemo(() => {
    if (!selectedPeriod) return 0;
    const days = Math.ceil(
      (new Date(selectedPeriod.endDate).getTime() - new Date(selectedPeriod.startDate).getTime()) /
        (1000 * 60 * 60 * 24),
    ) + 1;
    return Math.min(100, Math.round((stats.L01 / days) * 100));
  }, [selectedPeriod, stats.L01]);

  return (
    <DashboardShell
      activeSection="reports"
      title="Báo cáo kỳ lịch"
      description="Tổng hợp phân bổ lịch, trạng thái và xung đột của kỳ lịch được chọn."
    >
      {/* Period selector */}
      <section className="flex items-center justify-between gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <div className="flex items-center gap-4">
          <span className="material-symbols-outlined text-[22px] text-primary">calendar_month</span>
          <div>
            <h2 className="text-[16px] font-semibold text-on-surface">Chọn kỳ lịch</h2>
            <p className="text-[12px] text-on-surface-variant">Xem báo cáo chi tiết theo kỳ xếp lịch.</p>
          </div>
        </div>
        <div className="relative min-w-[280px]">
          <select
            className="w-full appearance-none rounded-lg border border-outline-variant bg-surface px-3 py-2.5 text-[14px] text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary cursor-pointer pr-10"
            value={selectedPeriod?.id ?? ""}
            onChange={(e) => {
              const p = periods.find((x) => x.id === Number(e.target.value));
              if (p) setSelectedPeriod(p);
            }}
          >
            <option value="">Chọn kỳ lịch</option>
            {periods.map((p) => (
              <option key={p.id} value={p.id}>
                {p.periodName} ({p.status})
              </option>
            ))}
          </select>
          <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
        </div>
      </section>

      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
          {message}
        </div>
      )}

      {!selectedPeriod && !loading ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4">
          <span className="material-symbols-outlined text-5xl text-outline">calendar_month</span>
          <p className="text-on-surface-variant">Chọn một kỳ lịch để xem báo cáo.</p>
        </div>
      ) : loading || loadingPeriod ? (
        <div className="flex items-center justify-center py-20">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : selectedPeriod ? (
        <div className="space-y-6">
          {/* Period info */}
          <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <div className="flex items-start justify-between">
              <div>
                <h3 className="text-[18px] font-semibold text-on-surface">{selectedPeriod.periodName}</h3>
                <p className="mt-1 text-[13px] text-on-surface-variant">
                  {new Date(selectedPeriod.startDate).toLocaleDateString("vi-VN")} —{" "}
                  {new Date(selectedPeriod.endDate).toLocaleDateString("vi-VN")}
                </p>
              </div>
              <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[12px] font-semibold ${
                selectedPeriod.status === "PUBLISHED"
                  ? "bg-secondary-container text-secondary"
                  : selectedPeriod.status === "DRAFT"
                    ? "bg-primary-fixed text-primary"
                    : "bg-surface-container-high text-outline"
              }`}>
                <span className={`h-2 w-2 rounded-full ${
                  selectedPeriod.status === "PUBLISHED" ? "bg-secondary" :
                  selectedPeriod.status === "DRAFT" ? "bg-primary" : "bg-outline"
                }`} />
                {selectedPeriod.status}
              </span>
            </div>
          </section>

          {/* KPI Grid */}
          <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[
              { label: "Tổng phân công", value: stats.total, icon: "event_available", accent: "bg-primary-fixed text-primary" },
              { label: "Số nhân sự", value: stats.uniqueStaff, icon: "groups", accent: "bg-secondary-container text-secondary" },
              { label: "Phủ lịch", value: `${coverageRate}%`, icon: "check_circle", accent: "bg-surface-container text-on-surface-variant" },
              { label: "Xung đột", value: stats.conflicts, icon: "warning", accent: stats.conflicts > 0 ? "bg-error-container text-error" : "bg-surface-container text-outline" },
            ].map((kpi) => (
              <article key={kpi.label} className="flex flex-col justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm hover:bg-surface-container-low transition-colors">
                <div className="flex justify-between items-start">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">{kpi.label}</p>
                  <span className={`material-symbols-outlined p-1.5 rounded-md ${kpi.accent} text-[18px]`}>
                    {kpi.icon}
                  </span>
                </div>
                <p className="mt-3 text-display-lg font-bold text-on-surface">{kpi.value}</p>
              </article>
            ))}
          </section>

          {/* Shift breakdown */}
          <section className="grid gap-4 sm:grid-cols-4">
            {[
              { label: "Trực 24/24", count: stats.L01, color: "border-l-red-500 bg-red-50" },
              { label: "Thông tầm", count: stats.L02, color: "border-l-blue-500 bg-blue-50" },
              { label: "PK dịch vụ", count: stats.L03, color: "border-l-green-500 bg-green-50" },
              { label: "PK chuyên gia", count: stats.L04, color: "border-l-purple-500 bg-purple-50" },
            ].map((stat) => (
              <div key={stat.label} className={`flex items-center justify-between rounded-lg border border-l-4 p-4 ${stat.color}`}>
                <span className="text-[13px] font-medium text-on-surface">{stat.label}</span>
                <span className="text-headline-md font-bold text-on-surface">{stat.count}</span>
              </div>
            ))}
          </section>

          {/* Conflict list */}
          {conflictData && conflictData.conflicts.length > 0 && (
            <section className="rounded-xl border border-error/30 bg-error-container/10 p-5 shadow-sm">
              <h3 className="text-[16px] font-semibold text-error mb-4 flex items-center gap-2">
                <span className="material-symbols-outlined text-[20px]">warning</span>
                Xung đột ({conflictData.totalConflicts})
              </h3>
              <div className="space-y-3">
                {conflictData.conflicts.slice(0, 5).map((c) => (
                  <div key={c.scheduleId} className="flex items-start gap-3 rounded-lg border border-error/20 bg-surface-container-lowest p-3">
                    <span className="material-symbols-outlined text-[18px] text-error shrink-0 mt-0.5">error</span>
                    <div>
                      <p className="text-[13px] font-medium text-on-surface">{c.staffName}</p>
                      <p className="text-[12px] text-on-surface-variant">
                        {new Date(c.workDate).toLocaleDateString("vi-VN")} — {c.shiftTypeName}
                      </p>
                      {c.conflictReasons.map((reason, i) => (
                        <p key={i} className="text-[11px] text-error mt-0.5">{reason}</p>
                      ))}
                    </div>
                  </div>
                ))}
                {conflictData.conflicts.length > 5 && (
                  <p className="text-center text-[12px] text-outline pt-1">
                    +{conflictData.conflicts.length - 5} xung đột khác
                  </p>
                )}
              </div>
            </section>
          )}

          {/* Recent assignments */}
          <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <h3 className="text-[16px] font-semibold text-on-surface mb-4">Phân công gần đây</h3>
            {schedules.length === 0 ? (
              <p className="text-center text-on-surface-variant py-8">Chưa có phân công nào trong kỳ này.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface-container-low">
                      <th className="px-4 py-3 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Nhân sự</th>
                      <th className="px-4 py-3 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Loại lịch</th>
                      <th className="px-4 py-3 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Ngày</th>
                      <th className="px-4 py-3 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {schedules.slice(0, 20).map((s) => (
                      <tr key={s.id} className="transition-colors hover:bg-surface-container-lowest">
                        <td className="px-4 py-3 text-[13px] font-medium text-on-surface">{s.staff.fullName}</td>
                        <td className="px-4 py-3">
                          <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${
                            s.shiftType.id === "L01" ? "bg-red-50 text-red-700" :
                            s.shiftType.id === "L02" ? "bg-blue-50 text-blue-700" :
                            s.shiftType.id === "L03" ? "bg-green-50 text-green-700" :
                            "bg-purple-50 text-purple-700"
                          }`}>
                            {s.shiftType.id}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-[13px] text-on-surface-variant">
                          {new Date(s.workDate).toLocaleDateString("vi-VN")}
                        </td>
                        <td className="px-4 py-3">
                          {s.hasConflict ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-error-container px-2.5 py-0.5 text-[11px] font-semibold text-error">
                              <span className="h-1.5 w-1.5 rounded-full bg-error" /> Có xung đột
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 rounded-full bg-secondary-container px-2.5 py-0.5 text-[11px] font-semibold text-secondary">
                              <span className="h-1.5 w-1.5 rounded-full bg-secondary" /> OK
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {schedules.length > 20 && (
                  <p className="text-center text-[12px] text-outline pt-3 py-3">
                    Hiển thị 20 / {schedules.length} phân công
                  </p>
                )}
              </div>
            )}
          </section>
        </div>
      ) : null}
    </DashboardShell>
  );
}
