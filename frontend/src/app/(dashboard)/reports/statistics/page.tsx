"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { BackButton } from "@/components/ui/BackButton";
import { useToast } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { SchedulePeriod, StaffShiftStatistics, ShiftType } from "@/types/api";

const SHIFT_TYPE_COLORS: Record<string, { label: string; bg: string; text: string }> = {
  L01: { label: "Trực 24/24", bg: "bg-shift-24", text: "text-on-shift-24" },
  L02: { label: "Thông tầm", bg: "bg-shift-all-day", text: "text-on-shift-all-day" },
  L03: { label: "PK Dịch vụ", bg: "bg-shift-service", text: "text-on-shift-service" },
  L04: { label: "PK Chuyên gia", bg: "bg-shift-expert", text: "text-on-shift-expert" },
};

export default function StatisticsReportPage() {
  return <StatisticsReportContent />;
}

function StatisticsReportContent() {
  const { error: toastError } = useToast();
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<SchedulePeriod | null>(null);
  const [stats, setStats] = useState<StaffShiftStatistics[]>([]);
  const [shiftTypes, setShiftTypes] = useState<ShiftType[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetching, setFetching] = useState(false);
  const [shiftTypeFilter, setShiftTypeFilter] = useState<string>("");
  const [message, setMessage] = useState<string | null>(null);
  const [visibleCount, setVisibleCount] = useState(20);

  const STEPS = [20, 50, 100, 200];

  const fetchPeriods = useCallback(async () => {
    try {
      setLoading(true);
      const [periodsData, shiftTypesData] = await Promise.all([
        api.get<SchedulePeriod[]>("/periods"),
        api.get<ShiftType[]>("/shift-types"),
      ]);
      setPeriods(periodsData ?? []);
      setShiftTypes(shiftTypesData ?? []);
      // Prefer PUBLISHED, fall back to DRAFT
      const published = (periodsData ?? []).find((p) => p.status === "PUBLISHED");
      if (published) {
        setSelectedPeriod(published);
      } else {
        const draft = (periodsData ?? []).find((p) => p.status === "DRAFT");
        if (draft) setSelectedPeriod(draft);
      }
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi tải danh sách kỳ lịch."));
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchStats = useCallback(async (periodId: number, shiftTypeId?: string, signal?: AbortSignal) => {
    try {
      setFetching(true);
      setMessage(null);
      const data = await api.getStaffStatistics(periodId, shiftTypeId, { signal });
      if (signal?.aborted) return;
      setStats(data ?? []);
    } catch (err) {
      if (signal?.aborted) return;
      setMessage(getErrorMessage(err, "Lỗi tải thống kê nhân sự."));
      toastError("Không thể tải thống kê");
    } finally {
      if (!signal?.aborted) setFetching(false);
    }
  }, [toastError]);

  useEffect(() => {
    void fetchPeriods();
  }, [fetchPeriods]);

  useEffect(() => {
    if (!selectedPeriod) return;
    setVisibleCount(20);
    const controller = new AbortController();
    void fetchStats(selectedPeriod.id, shiftTypeFilter || undefined, controller.signal);
    return () => controller.abort();
  }, [selectedPeriod, shiftTypeFilter, fetchStats]);

  const summaryStats = useMemo(() => {
    if (stats.length === 0) return null;
    const totalShifts = stats.reduce((sum, s) => sum + s.totalShifts, 0);
    const totalHours = stats.reduce((sum, s) => sum + (s.totalHours ?? 0), 0);
    const avgShifts = totalShifts / stats.length;
    const maxShifts = Math.max(...stats.map((s) => s.totalShifts), 1);
    const minShifts = Math.min(...stats.map((s) => s.totalShifts), 0);
    return { totalShifts, totalHours, avgShifts, maxShifts, minShifts, staffCount: stats.length };
  }, [stats]);

  const maxShiftsInList = useMemo(
    () => Math.max(...stats.map((s) => s.totalShifts), 1),
    [stats]
  );

  return (
    <div className="space-y-4">
      <BackButton href="/reports" variant="full" label="Quay lại" className="mb-4" />

      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
          {message}
        </div>
      )}

      {/* Header & Filters */}
      <section className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <div className="flex items-center gap-3">
          <span className="material-symbols-outlined text-[22px] text-primary">assessment</span>
          <div>
            <h2 className="text-[16px] font-semibold text-on-surface">Thống kê nhân sự</h2>
            <p className="text-[12px] text-on-surface-variant">Phân bổ ca trực theo nhân sự trong kỳ lịch.</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Period selector */}
          <div className="relative min-w-[200px]">
            <label htmlFor="stats-period" className="sr-only">Chọn kỳ lịch</label>
            <select
              id="stats-period"
              className="w-full appearance-none rounded-lg border border-outline-variant bg-surface px-3 py-2.5 text-[14px] text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary cursor-pointer pr-10"
              value={selectedPeriod?.id ?? ""}
              onChange={(e) => {
                const p = periods.find((x) => x.id === Number(e.target.value));
                setSelectedPeriod(p ?? null);
              }}
            >
              <option value="">Chọn kỳ lịch</option>
              {periods.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.periodName}{" "}
                  ({p.status === "PUBLISHED" ? "Đã công bố" : p.status === "DRAFT" ? "Nháp" : "Lưu trữ"})
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">
              expand_more
            </span>
          </div>

          {/* Shift type filter */}
          <div className="relative min-w-[180px]">
            <label htmlFor="stats-shift-type" className="sr-only">Lọc theo loại ca</label>
            <select
              id="stats-shift-type"
              className="w-full appearance-none rounded-lg border border-outline-variant bg-surface px-3 py-2.5 text-[14px] text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary cursor-pointer pr-10"
              value={shiftTypeFilter}
              onChange={(e) => setShiftTypeFilter(e.target.value)}
            >
              <option value="">Tất cả loại ca</option>
              {shiftTypes.map((st) => (
                <option key={st.id} value={st.id}>
                  {st.name} ({st.id})
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">
              expand_more
            </span>
          </div>
        </div>
      </section>

      {/* KPI Summary Cards */}
      {!loading && !fetching && summaryStats && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <KpiCard
            label="Tổng nhân sự"
            value={summaryStats.staffCount}
            icon="groups"
            accent="bg-primary-fixed text-primary"
          />
          <KpiCard
            label="TB ca/nhân"
            value={summaryStats.avgShifts.toFixed(1)}
            icon="trending_flat"
            accent="bg-secondary-container text-secondary"
          />
          <KpiCard
            label="Cao nhất"
            value={summaryStats.maxShifts}
            icon="arrow_upward"
            accent="bg-tertiary-fixed text-tertiary"
          />
          <KpiCard
            label="Thấp nhất"
            value={summaryStats.minShifts}
            icon="arrow_downward"
            accent="bg-surface-container-high text-outline"
          />
        </div>
      )}

      {/* Content */}
      {!selectedPeriod && !loading ? (
        <EmptyState message="Chọn một kỳ lịch để xem thống kê." icon="bar_chart" />
      ) : loading ? (
        <LoadingState message="Đang tải kỳ lịch..." />
      ) : fetching ? (
        <LoadingState message="Đang tải thống kê..." />
      ) : stats.length === 0 ? (
        <EmptyState message="Không có dữ liệu thống kê cho kỳ lịch này." icon="info" />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm">
          <table className="w-full text-left">
            <thead>
              <tr className="bg-surface-container-low border-b border-outline-variant">
                <th className="px-4 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide">
                  Nhân sự
                </th>
                <th className="px-4 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide">
                  Mã NV
                </th>
                <th className="px-4 py-3 text-label-sm text-on-surface-variant uppercase tracking-wide">
                  Chuyên khoa
                </th>
                <th className="px-4 py-3 text-center text-label-sm text-on-surface-variant uppercase tracking-wide">
                  L01
                </th>
                <th className="px-4 py-3 text-center text-label-sm text-on-surface-variant uppercase tracking-wide">
                  L02
                </th>
                <th className="px-4 py-3 text-center text-label-sm text-on-surface-variant uppercase tracking-wide">
                  L03
                </th>
                <th className="px-4 py-3 text-center text-label-sm text-on-surface-variant uppercase tracking-wide">
                  L04
                </th>
                <th className="px-4 py-3 text-center text-label-sm text-on-surface-variant uppercase tracking-wide">
                  Tổng
                </th>
                <th className="px-4 py-3 text-center text-label-sm text-on-surface-variant uppercase tracking-wide">
                  Giờ
                </th>
                <th className="px-4 py-3 text-center text-label-sm text-on-surface-variant uppercase tracking-wide min-w-[120px]">
                  {/* REPORTS-STATS-002: when a shift type is filtered the
                      workload percentage column represents that type, so
                      rename it to avoid implying an "all types" ratio. */}
                  {shiftTypeFilter ? `Tỷ lệ ${shiftTypeFilter}` : "Tỷ lệ"}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant">
              {stats.slice(0, visibleCount).map((s) => {
                const shiftTypeInfo = shiftTypeFilter ? SHIFT_TYPE_COLORS[shiftTypeFilter] : null;
                // REPORTS-STATS-002: dim the L0nCount cells that aren't part
                // of the active shiftTypeFilter so the focused column stands
                // out without losing the cross-type totals entirely.
                const dimmedCls = shiftTypeFilter ? "opacity-40" : "";
                return (
                  <tr
                    key={s.staffId}
                    className="hover:bg-surface-container-low transition-colors"
                  >
                    <td className="px-4 py-3">
                      <span className="text-[14px] font-medium text-on-surface">{s.staffName}</span>
                    </td>
                    <td className="px-4 py-3">
                      <span className="text-[13px] text-on-surface-variant font-mono">{s.staffCode ?? "—"}</span>
                    </td>
                    <td className="px-4 py-3">
                      <span className="text-[13px] text-on-surface-variant">{s.specialtyName ?? "—"}</span>
                    </td>
                    <td className={`px-4 py-3 text-center ${dimmedCls}`}>
                      <Badge count={s.L01Count} color="bg-red-100 text-red-800" />
                    </td>
                    <td className={`px-4 py-3 text-center ${dimmedCls}`}>
                      <Badge count={s.L02Count} color="bg-blue-100 text-blue-800" />
                    </td>
                    <td className={`px-4 py-3 text-center ${dimmedCls}`}>
                      <Badge count={s.L03Count} color="bg-green-100 text-green-800" />
                    </td>
                    <td className={`px-4 py-3 text-center ${dimmedCls}`}>
                      <Badge count={s.L04Count} color="bg-purple-100 text-purple-800" />
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className="text-[14px] font-bold text-primary">{s.totalShifts}</span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className="text-[13px] text-on-surface-variant">
                        {typeof s.totalHours === "number" ? s.totalHours.toFixed(1) : s.totalHours}h
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <div className="w-16 bg-surface-variant rounded-full h-1.5 overflow-hidden">
                          <div
                            className={`h-1.5 rounded-full transition-all ${
                              shiftTypeInfo?.bg ?? "bg-primary"
                            }`}
                            style={{ width: `${Math.max(s.workloadPercentage ?? 0, 1)}%` }}
                          />
                        </div>
                        <span className="text-[12px] text-on-surface-variant min-w-[40px]">
                          {(s.workloadPercentage ?? 0).toFixed(1)}%
                        </span>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination controls (show more pattern) */}
      {!loading && !fetching && stats.length > 0 && (
        <div className="flex items-center justify-between gap-3 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-3">
          <p className="text-[12px] text-on-surface-variant">
            Hiển thị <span className="font-semibold text-on-surface">{Math.min(visibleCount, stats.length)}</span> / {stats.length} nhân sự
          </p>
          <div className="flex items-center gap-2">
            {visibleCount < stats.length && (
              <button
                type="button"
                onClick={() => setVisibleCount(c => Math.min(c + 50, stats.length))}
                className="h-8 px-3 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm font-medium text-on-surface hover:bg-surface-container transition-colors cursor-pointer"
              >
                Xem thêm 50
              </button>
            )}
            {visibleCount < stats.length && (
              <button
                type="button"
                onClick={() => setVisibleCount(stats.length)}
                className="h-8 px-3 rounded-lg bg-primary text-on-primary text-label-sm font-medium hover:bg-primary/90 transition-colors cursor-pointer"
              >
                Hiện tất cả
              </button>
            )}
            {visibleCount > STEPS[0] && (
              <button
                type="button"
                onClick={() => setVisibleCount(STEPS[0])}
                className="h-8 px-3 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm font-medium text-on-surface-variant hover:bg-surface-container transition-colors cursor-pointer"
              >
                Thu gọn
              </button>
            )}
          </div>
        </div>
      )}

      {/* Legend */}
      {!loading && !fetching && stats.length > 0 && (
        <div className="flex flex-wrap items-center gap-4 rounded-lg border border-outline-variant bg-surface-container-lowest p-3">
          <span className="text-[12px] text-on-surface-variant font-medium">Màu loại ca:</span>
          {Object.entries(SHIFT_TYPE_COLORS).map(([key, val]) => (
            <span key={key} className={`inline-flex items-center gap-1.5 text-[12px] ${val.text}`}>
              <span className={`inline-block w-3 h-3 rounded-sm ${val.bg}`} />
              {key} — {val.label}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function KpiCard({
  label,
  value,
  icon,
  accent,
}: {
  label: string;
  value: string | number;
  icon: string;
  accent: string;
}) {
  return (
    <article className="flex flex-col justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
      <div className="flex justify-between items-start">
        <p className="text-label-sm text-on-surface-variant">{label}</p>
        <span className={`material-symbols-outlined p-1.5 rounded-md ${accent} text-[18px]`}>
          {icon}
        </span>
      </div>
      <p className="mt-3 text-display-lg font-bold text-on-surface">{value}</p>
    </article>
  );
}

function Badge({ count, color }: { count: number; color: string }) {
  if (count === 0) {
    return <span className="text-[13px] text-outline">0</span>;
  }
  return (
    <span className={`inline-flex items-center justify-center rounded-full px-2 py-0.5 text-[13px] font-semibold min-w-[24px] ${color}`}>
      {count}
    </span>
  );
}

function LoadingState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-outline-variant bg-surface py-20 gap-4">
      <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      <p className="text-on-surface-variant">{message}</p>
    </div>
  );
}

function EmptyState({ message, icon }: { message: string; icon: string }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4">
      <span className="material-symbols-outlined text-5xl text-outline">{icon}</span>
      <p className="text-on-surface-variant">{message}</p>
    </div>
  );
}
