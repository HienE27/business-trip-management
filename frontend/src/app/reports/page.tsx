"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type {
  DashboardData,
  DashboardSummary,
  ShiftStatistics,
  StaffWorkloadStatistics,
  SchedulePeriod,
} from "@/types/api";

export default function ReportsPage() {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number>(1);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [shiftStats, setShiftStats] = useState<ShiftStatistics | null>(null);
  const [workloads, setWorkloads] = useState<StaffWorkloadStatistics[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);

  const fetchPeriods = useCallback(async () => {
    try {
      setMessage(null);
      const res = await api.get<SchedulePeriod[]>("/periods");
      const nextPeriods = res ?? [];
      setPeriods(nextPeriods);
      if (nextPeriods.length > 0) {
        const preferredPeriod =
          nextPeriods.find((period) => period.status === "PUBLISHED" || period.status === "DRAFT") ?? nextPeriods[0];
        setSelectedPeriodId(preferredPeriod.id);
      }
    } catch (err) {
      setPeriods([]);
      setMessage(getErrorMessage(err, "Không thể tải danh sách kỳ."));
    }
  }, []);

  const fetchData = useCallback(async (periodId: number) => {
    try {
      setLoading(true);
      setMessage(null);
      const [dashRes, shiftsRes, workloadsRes] = await Promise.all([
        api.get<DashboardData>("/dashboard"),
        api.get<ShiftStatistics>("/dashboard/shifts"),
        api.get<StaffWorkloadStatistics[]>(`/dashboard/workload/period/${periodId}`),
      ]);
      if (dashRes) {
        setSummary(dashRes.summary);
        setShiftStats(dashRes.shiftStatistics);
      } else if (shiftsRes) {
        setShiftStats(shiftsRes);
      }
      setWorkloads(workloadsRes ?? []);
    } catch (err) {
      setSummary(null);
      setShiftStats(null);
      setWorkloads([]);
      setMessage(getErrorMessage(err, "Không thể tải dữ liệu báo cáo."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPeriods();
  }, [fetchPeriods]);

  useEffect(() => {
    if (selectedPeriodId) fetchData(selectedPeriodId);
  }, [selectedPeriodId, fetchData]);

  const kpiCards = useMemo(() => {
    if (!summary) return [];
    return [
      {
        label: "Tổng ca trực",
        value: String(summary.totalSchedules),
        helper: "+12%",
        helperTone: "positive",
        icon: "calendar_month",
        iconClass: "bg-primary-fixed text-primary",
      },
      {
        label: "Tổng nhân sự",
        value: String(summary.activeStaff),
        helper: `${summary.totalStaff} NV`,
        helperTone: "neutral",
        icon: "group",
        iconClass: "bg-secondary-container text-secondary",
      },
      {
        label: "Yêu cầu chờ",
        value: String(summary.pendingLeaveRequests + summary.pendingScheduleExchanges),
        helper: "Cần xử lý",
        helperTone: summary.pendingLeaveRequests + summary.pendingScheduleExchanges > 0 ? "alert" : "good",
        icon: "warning",
        iconClass: summary.pendingLeaveRequests + summary.pendingScheduleExchanges > 0
          ? "bg-error-container text-error"
          : "bg-secondary-container text-secondary",
      },
      {
        label: "Độ cân bằng",
        value: "92%",
        helper: "Tốt",
        helperTone: "good",
        icon: "balance",
        iconClass: "bg-tertiary-fixed text-tertiary",
      },
    ];
  }, [summary]);

  const monthlyDetailRows = useMemo(() => {
    return workloads.map((w) => ({
      initials: w.staffName.split(" ").filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase() ?? "").join(""),
      name: w.staffName,
      role: "Nhân viên",
      duty2424: w.L01Count,
      allDay: w.L02Count,
      service: w.L03Count,
      compLeave: w.leaveDays,
      hours: `${w.scheduleCount * 8}h`,
      status: w.scheduleCount > 10 ? "Quá tải" : w.scheduleCount > 7 ? "Cao" : "Ổn định",
      statusTone: w.scheduleCount > 10 ? "error" : w.scheduleCount > 7 ? "tertiary" : "secondary",
    }));
  }, [workloads]);

  function getHelperBadgeClass(tone: string) {
    switch (tone) {
      case "positive": return "bg-secondary-container text-secondary font-semibold";
      case "alert": return "text-error font-semibold";
      case "neutral": return "bg-surface-container-high text-on-surface-variant";
      case "good": return "text-tertiary font-semibold";
      default: return "bg-surface-container-high text-on-surface-variant";
    }
  }

  function getStatusBadgeClass(tone: string) {
    switch (tone) {
      case "error": return "bg-error-container text-error border border-error/20";
      case "tertiary": return "bg-tertiary-fixed/30 text-tertiary border border-tertiary/20";
      case "secondary": return "bg-secondary-container text-secondary border border-secondary/20";
      case "primary": return "bg-primary-fixed/30 text-primary border border-primary/20";
      default: return "bg-surface-container-high text-on-surface-variant border border-outline-variant";
    }
  }

  function getWorkloadBarClass(tone: string) {
    switch (tone) {
      case "error": return "bg-error";
      case "tertiary": return "bg-tertiary";
      case "primary": return "bg-primary";
      default: return "bg-primary";
    }
  }

  return (
    <DashboardShell
      activeCode="M06-REPORTS"
      description="Tổng quan hoạt động và phân bổ nguồn lực đội ngũ y tế."
      title="Thống kê & Báo cáo"
    >
      <div className="space-y-4 pb-12">
        {message && (
          <div className="rounded-xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm text-on-surface">
            {message}
          </div>
        )}

        <section className="mb-2 flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <p className="text-[14px] text-on-surface-variant">
              Tổng quan hoạt động và phân bổ nguồn lực đội ngũ y tế
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <select
                className="appearance-none h-10 rounded-lg border border-outline-variant bg-surface px-4 pr-9 text-label-md text-on-surface shadow-sm transition-colors hover:bg-surface-container-low focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20"
                value={selectedPeriodId}
                onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
              >
                {periods.map((p) => (
                  <option key={p.id} value={p.id}>{p.periodName}</option>
                ))}
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[18px] text-on-surface-variant">
                expand_more
              </span>
            </div>
            <button
              className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary shadow-sm transition-colors hover:opacity-90 disabled:opacity-50"
              onClick={async () => {
                try {
                  const res = await fetch(
                    `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1"}/dashboard/export/workload/${selectedPeriodId}`,
                    { credentials: "include" }
                  );
                  if (!res.ok) throw new Error("Export failed");
                  const blob = await res.blob();
                  const url = URL.createObjectURL(blob);
                  const a = document.createElement("a");
                  a.href = url;
                  a.download = `bao-cao-tai-nhan-su-${selectedPeriodId}.xlsx`;
                  a.click();
                  URL.revokeObjectURL(url);
                } catch {
                  alert("Không thể xuất báo cáo. Vui lòng thử lại.");
                }
              }}
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">download</span>
              Xuat bao cao
            </button>
          </div>
        </section>

        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : (
          <>
            <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {kpiCards.map((card) => (
                <div
                  className="flex cursor-default flex-col justify-between rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low"
                  key={card.label}
                >
                  <div className="flex items-start justify-between">
                    <h3 className="text-label-sm uppercase tracking-wider text-on-surface-variant">{card.label}</h3>
                    <div className={`rounded-lg p-2 ${card.iconClass}`}>
                      <span
                        className="material-symbols-outlined text-[18px]"
                        style={{ fontVariationSettings: "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24" }}
                      >
                        {card.icon}
                      </span>
                    </div>
                  </div>
                  <div className="mt-4 flex items-baseline gap-3">
                    <span className={`text-display-lg font-bold ${card.helperTone === "alert" ? "text-error" : "text-on-surface"}`}>
                      {card.value}
                    </span>
                    {card.helperTone === "positive" ? (
                      <span className="flex items-center gap-0.5 rounded-full px-2 py-0.5 text-[11px] font-semibold bg-secondary-container text-secondary">
                        <span className="material-symbols-outlined text-[12px]">arrow_upward</span>
                        {card.helper}
                      </span>
                    ) : card.helperTone === "good" ? (
                      <div className="w-full space-y-1">
                        <div className="flex items-center justify-between">
                          <span className="text-label-sm text-on-surface font-semibold">{card.helper}</span>
                        </div>
                        <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-variant">
                          <div className="h-full rounded-full bg-primary" style={{ width: card.value }} />
                        </div>
                      </div>
                    ) : (
                      <span className={`text-[11px] ${getHelperBadgeClass(card.helperTone)} rounded-full px-2 py-0.5`}>{card.helper}</span>
                    )}
                  </div>
                </div>
              ))}
            </section>

            <section className="grid grid-cols-1 gap-4 lg:grid-cols-3">
              {/* Shift Distribution Chart */}
              <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-6 shadow-sm lg:col-span-2">
                <div className="mb-6 flex items-center justify-between">
                  <h3 className="font-title-lg text-on-surface">Phân bổ ca trực theo loại</h3>
                </div>
                {shiftStats ? (
                  <div className="flex flex-col gap-4">
                    {[
                      { label: "Trực 24/24", key: "L01Count" as const, color: "bg-primary" },
                      { label: "Thông tầm", key: "L02Count" as const, color: "bg-secondary" },
                      { label: "Phòng khám dịch vụ", key: "L03Count" as const, color: "bg-tertiary" },
                      { label: "Phòng khám chuyên gia", key: "L04Count" as const, color: "bg-outline" },
                    ].map(({ label, key, color }) => {
                      const value = shiftStats[key];
                      const max = Math.max(shiftStats.L01Count, shiftStats.L02Count, shiftStats.L03Count, shiftStats.L04Count, 1);
                      return (
                        <div className="space-y-1" key={key}>
                          <div className="flex justify-between text-sm">
                            <span className="text-on-surface">{label}</span>
                            <span className="font-semibold text-on-surface">{value}</span>
                          </div>
                          <div className="h-3 w-full rounded-full bg-surface-variant overflow-hidden">
                            <div className={`h-full rounded-full ${color}`} style={{ width: `${(value / max) * 100}%` }} />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <p className="text-center text-on-surface-variant py-8">Chưa có dữ liệu phân bổ.</p>
                )}
              </div>

              {/* Top Workloads */}
              <div className="flex flex-col rounded-lg border border-outline-variant bg-surface-container-lowest p-6 shadow-sm">
                <h3 className="mb-6 font-title-lg text-on-surface">Top khối lượng cao</h3>
                <div className="flex-1 space-y-6 overflow-y-auto pr-1">
                  {monthlyDetailRows.slice(0, 5).map((item) => (
                    <div className="space-y-2" key={item.name}>
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-medium text-on-surface">{item.name}</span>
                        <span className={`text-sm font-bold ${item.statusTone === "error" ? "text-error" : item.statusTone === "tertiary" ? "text-tertiary" : "text-primary"}`}>
                          {item.hours}
                        </span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-surface-variant">
                        <div className={`h-full rounded-full ${getWorkloadBarClass(item.statusTone)}`} style={{ width: `${(item.duty2424 / 10) * 100}%` }} />
                      </div>
                    </div>
                  ))}
                  {monthlyDetailRows.length === 0 && (
                    <p className="text-center text-sm text-on-surface-variant py-4">Chưa có dữ liệu workload.</p>
                  )}
                </div>
              </div>
            </section>

            {/* Monthly Detail Table */}
            <section className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm">
              <div className="flex flex-col items-start justify-between gap-4 border-b border-outline-variant bg-surface-container-lowest p-6 sm:flex-row sm:items-center">
                <h3 className="text-[20px] font-semibold leading-[28px] text-on-surface">Chi tiết chỉ tiêu tháng</h3>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface-container-low">
                      <th className="px-6 py-4 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Nhân sự</th>
                      <th className="px-6 py-4 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Chức vụ</th>
                      <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Trực 24/24</th>
                      <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Thông tầm</th>
                      <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Dịch vụ</th>
                      <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Nghỉ bù</th>
                      <th className="px-6 py-4 text-right text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Tổng giờ</th>
                      <th className="px-6 py-4 text-center text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant text-sm">
                    {monthlyDetailRows.length === 0 ? (
                      <tr>
                        <td className="px-6 py-10 text-center text-sm text-on-surface-variant" colSpan={8}>
                          Chưa có dữ liệu nhân sự nào.
                        </td>
                      </tr>
                    ) : (
                      monthlyDetailRows.map((row) => (
                        <tr className="group transition-colors hover:bg-surface-container" key={row.name}>
                          <td className="px-6 py-4">
                            <div className="flex items-center gap-3">
                              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-container/20 text-xs font-bold text-primary">
                                {row.initials}
                              </div>
                              <span className="text-sm font-medium text-on-surface transition-colors group-hover:text-primary">
                                {row.name}
                              </span>
                            </div>
                          </td>
                          <td className="px-6 py-4 text-on-surface-variant">{row.role}</td>
                          <td className="px-6 py-4 text-center font-medium text-on-surface">{row.duty2424}</td>
                          <td className="px-6 py-4 text-center font-medium text-on-surface">{row.allDay}</td>
                          <td className="px-6 py-4 text-center font-medium text-on-surface">{row.service}</td>
                          <td className="px-6 py-4 text-center text-outline">{row.compLeave}</td>
                          <td className="px-6 py-4 text-right font-bold text-on-surface">{row.hours}</td>
                          <td className="px-6 py-4 text-center">
                            <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-[11px] font-bold ${getStatusBadgeClass(row.statusTone)}`}>
                              {row.status}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          </>
        )}
      </div>
    </DashboardShell>
  );
}
