"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { Staff, SchedulePeriod, StaffWorkloadStatistics } from "@/types/api";

export default function ReportsStaffPage() {
  const [staffList, setStaffList] = useState<Staff[]>([]);
  const [workloads, setWorkloads] = useState<StaffWorkloadStatistics[]>([]);
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const fetchPeriods = useCallback(async () => {
    try {
      const data = await api.get<SchedulePeriod[]>("/periods");
      const published = data.filter((p) => p.status === "PUBLISHED");
      setPeriods(published);
      if (published.length > 0 && !selectedPeriodId) {
        setSelectedPeriodId(published[0].id);
      }
    } catch {
      // Silently fail
    }
  }, [selectedPeriodId]);

  const fetchData = useCallback(async () => {
    if (!selectedPeriodId) {
      setStaffList([]);
      setWorkloads([]);
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setMessage(null);
      const [staffRes, workloadRes] = await Promise.allSettled([
        api.get<Staff[]>("/staff/active"),
        api.get<StaffWorkloadStatistics[]>(`/dashboard/workload/period/${selectedPeriodId}`),
      ]);
      if (staffRes.status === "fulfilled") setStaffList(staffRes.value ?? []);
      if (workloadRes.status === "fulfilled") setWorkloads(workloadRes.value ?? []);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi tải dữ liệu nhân sự."));
    } finally {
      setLoading(false);
    }
  }, [selectedPeriodId]);

  useEffect(() => {
    void fetchPeriods();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (selectedPeriodId) {
      void fetchData();
    }
  }, [fetchData]);

  const enriched = useMemo(() => {
    const workMap = new Map(workloads.map((w) => [w.staffId, w]));
    return staffList
      .map((s) => {
        const w = workMap.get(s.id);
        return {
          staff: s,
          total: w?.scheduleCount ?? 0,
          L01: w?.L01Count ?? 0,
          L02: w?.L02Count ?? 0,
          L03: w?.L03Count ?? 0,
          L04: w?.L04Count ?? 0,
          leaveDays: w?.leaveDays ?? 0,
        };
      })
      .filter((item) => {
        if (!search.trim()) return true;
        const kw = search.toLowerCase();
        return (
          item.staff.fullName.toLowerCase().includes(kw) ||
          item.staff.username.toLowerCase().includes(kw) ||
          (item.staff.specialty?.name ?? "").toLowerCase().includes(kw)
        );
      })
      .sort((a, b) => b.total - a.total);
  }, [staffList, workloads, search]);

  const summary = useMemo(() => ({
    total: enriched.reduce((s, e) => s + e.total, 0),
    avg: enriched.length ? Math.round(enriched.reduce((s, e) => s + e.total, 0) / enriched.length) : 0,
    max: enriched.length ? Math.max(...enriched.map((e) => e.total)) : 0,
    overloaded: enriched.filter((e) => e.total > (e.staff.maxShiftsPerMonth ?? 6)).length,
  }), [enriched]);

  function getWorkloadColor(current: number, max: number) {
    const pct = max > 0 ? (current / max) * 100 : 0;
    if (pct >= 90) return "bg-error";
    if (pct >= 70) return "bg-primary";
    return "bg-secondary";
  }

  return (
    <DashboardShell
      activeSection="reports"
      title="Báo cáo khối lượng nhân sự"
      description="Theo dõi tải phân công theo nhân sự, so sánh với giới hạn ca/tháng."
    >
      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
          {message}
        </div>
      )}

      {/* Period Selector */}
      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm flex items-center gap-4">
        <label className="flex items-center gap-2">
          <span className="text-sm font-medium text-on-surface">Kỳ lịch:</span>
          <div className="relative">
            <select
              className="h-9 pl-3 pr-8 rounded-lg border border-outline-variant bg-surface-container-low text-sm text-on-surface appearance-none focus:outline-none focus:ring-1 focus:ring-primary cursor-pointer"
              value={selectedPeriodId ?? ""}
              onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
            >
              <option value="">-- Chọn kỳ lịch --</option>
              {periods.map((p) => (
                <option key={p.id} value={p.id}>{p.periodName}</option>
              ))}
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[18px] pointer-events-none">expand_more</span>
          </div>
        </label>
      </section>

      {/* Summary */}
      <section className="flex items-center justify-between">
      <section className="grid gap-4 md:grid-cols-4 flex-1">
        {[
          { label: "Tổng phân công", value: summary.total, icon: "event_available", accent: "bg-primary-fixed text-primary" },
          { label: "Trung bình / người", value: summary.avg, icon: "analytics", accent: "bg-secondary-container text-secondary" },
          { label: "Cao nhất", value: summary.max, icon: "trending_up", accent: "bg-tertiary-fixed text-tertiary" },
          { label: "Quá tải", value: summary.overloaded, icon: "warning", accent: summary.overloaded > 0 ? "bg-error-container text-error" : "bg-surface-container text-outline" },
        ].map((kpi) => (
          <article key={kpi.label} className="flex flex-col justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <div className="flex justify-between items-start">
              <p className="text-label-sm text-on-surface-variant">{kpi.label}</p>
              <span className={`material-symbols-outlined p-1.5 rounded-md ${kpi.accent} text-[18px]`}>{kpi.icon}</span>
            </div>
            <p className="mt-3 text-display-lg font-bold text-on-surface">{loading ? "—" : kpi.value}</p>
          </article>
        ))}
      </section>
      {selectedPeriodId && (
        <a
          href={`/api/v1/dashboard/export/workload/${selectedPeriodId}`}
          target="_blank"
          rel="noopener noreferrer"
          className="ml-4 inline-flex items-center gap-1.5 rounded-lg border border-primary px-3 py-1.5 text-[12px] font-medium text-primary hover:bg-primary-fixed transition-colors shrink-0"
        >
          <span className="material-symbols-outlined text-[16px]">table_view</span>
          Xuất Excel
        </a>
      )}
      </section>

      {/* Search */}
      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <div className="relative max-w-md">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">search</span>
          <input
            className="w-full rounded-lg border border-transparent bg-surface py-2.5 pl-10 pr-4 text-[14px] text-on-surface transition-all placeholder:text-outline focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-1 focus:ring-primary"
            placeholder="Tìm theo tên, mã NV hoặc khoa..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </section>

      {/* Table */}
      <section className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm">
        <div className="overflow-x-auto">
          {loading ? (
            <div className="flex items-center justify-center py-20">
              <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : enriched.length === 0 ? (
            <div className="py-20 text-center">
              <span className="material-symbols-outlined text-5xl text-outline">groups</span>
              <p className="mt-4 text-on-surface-variant">
                {search ? "Không tìm thấy nhân sự phù hợp." : "Chưa có dữ liệu nhân sự."}
              </p>
            </div>
          ) : (
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="border-b border-outline-variant bg-surface-container-low">
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Nhân sự</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Khoa</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant text-center">Tổng ca</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant text-center">L01</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant text-center">L02</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant text-center">L03</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant text-center">L04</th>
                  <th className="px-5 py-3 text-label-sm text-on-surface-variant">Tải trọng</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {enriched.map((item) => {
                  const max = item.staff.maxShiftsPerMonth ?? 6;
                  const pct = max > 0 ? (item.total / max) * 100 : 0;
                  const isOver = pct >= 100;
                  return (
                    <tr key={item.staff.id} className="transition-colors hover:bg-surface-container-lowest group">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-3">
                          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-fixed text-[12px] font-bold text-primary">
                            {item.staff.fullName.split(" ").slice(-2).map((p) => p[0]).join("").toUpperCase()}
                          </div>
                          <div>
                            <p className="text-[13px] font-semibold text-on-surface">{item.staff.fullName}</p>
                            <p className="text-[11px] text-on-surface-variant">{item.staff.username}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-3 text-[13px] text-on-surface">{item.staff.specialty?.name ?? "—"}</td>
                      <td className="px-5 py-3 text-center">
                        <span className={`text-[14px] font-bold ${isOver ? "text-error" : "text-on-surface"}`}>
                          {item.total}
                        </span>
                        <span className="text-[11px] text-outline"> / {max}</span>
                      </td>
                      {[item.L01, item.L02, item.L03, item.L04].map((count, i) => (
                        <td key={i} className="px-5 py-3 text-center text-[13px] text-on-surface-variant">{count}</td>
                      ))}
                      <td className="px-5 py-3 min-w-[140px]">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 bg-surface-variant rounded-full h-2">
                            <div
                              className={`h-2 rounded-full transition-all ${getWorkloadColor(item.total, max)}`}
                              style={{ width: `${Math.min(100, pct)}%` }}
                            />
                          </div>
                          <span className={`text-[11px] font-bold min-w-[40px] ${isOver ? "text-error" : "text-outline"}`}>
                            {pct.toFixed(0)}%
                          </span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
        {!loading && enriched.length > 0 && (
          <div className="border-t border-outline-variant px-5 py-3">
            <p className="text-[12px] text-on-surface-variant">
              Hiển thị {enriched.length} nhân sự
            </p>
          </div>
        )}
      </section>
    </DashboardShell>
  );
}
