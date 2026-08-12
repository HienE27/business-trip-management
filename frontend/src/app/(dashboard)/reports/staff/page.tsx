"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ExportControls } from "@/components/reports/ExportControls";
import { useToast, Pagination } from "@/components/ui";
import { EmptyState } from "@/components/ui/EmptyState";
import { Button } from "@/components/ui";
import { BackButton } from "@/components/ui/BackButton";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { SchedulePeriod, StaffWorkloadStatistics } from "@/types/api";
import {
  computeSummary,
  pickCap,
  pickShiftCount,
  isOverloaded,
  type WorkloadView,
} from "@/components/reports/workloadUtils";
import { WorkloadBalanceChart } from "@/components/reports/WorkloadBalanceChart";

/**
 * View modes for the staff report (§M02-F05 / §M04-F05 / §M05-F05):
 *   - "ALL"      — total schedule count per staff (M02-F05 + dashboard)
 *   - "L01"      — trực 24/24 only (M02-F05 "số ngày trực")
 *   - "L02"      — thông tầm only
 *   - "L03"      — phòng khám dịch vụ only (M04-F05)
 *   - "L04"      — phòng khám chuyên gia only (M05-F05)
 */
const VIEW_MODES: { value: WorkloadView; label: string; helper: string }[] = [
  { value: "ALL", label: "Tất cả", helper: "Tổng số ca của từng nhân sự trong kỳ" },
  { value: "L01", label: "L01 · Trực 24/24", helper: "Số ngày trực 24/24 trong tháng (M02-F05)" },
  { value: "L02", label: "L02 · Thông tầm", helper: "Số ngày thông tầm trong tháng" },
  { value: "L03", label: "L03 · PK Dịch vụ", helper: "Số ca khám dịch vụ (M04-F05)" },
  { value: "L04", label: "L04 · PK Chuyên gia", helper: "Số ca khám chuyên gia (M05-F05)" },
];

export default function ReportsStaffPage() {
  return <ReportsStaffContent />;
}

function ReportsStaffContent() {
  const { success: toastSuccess, error: toastError } = useToast();
  const [workloads, setWorkloads] = useState<StaffWorkloadStatistics[]>([]);
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [view, setView] = useState<WorkloadView>("ALL");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const fetchPeriods = useCallback(async () => {
    try {
      const data = await api.get<SchedulePeriod[]>("/periods");
      // Show all periods (DRAFT + PUBLISHED + ARCHIVED) so users can verify
      // freshly generated schedules against the period they just ran the
      // auto-scheduling algorithm on. Previously this filter was hard-locked
      // to PUBLISHED, which hid DRAFT periods and confused users into thinking
      // the algorithm had saved data when it had not.
      const sorted = [...data].sort((a, b) => {
        const da = new Date(a.startDate).getTime();
        const db = new Date(b.startDate).getTime();
        return db - da;
      });
      setPeriods(sorted);
      if (sorted.length > 0 && !selectedPeriodId) {
        setSelectedPeriodId(sorted[0].id);
      }
    } catch {
      setMessage("Không thể tải danh sách kỳ lịch.");
    }
  }, [selectedPeriodId]);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    if (!selectedPeriodId) {
      setWorkloads([]);
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setMessage(null);
      // BUGFIX (REPORTS-STAFF-001): the workload page is now the sole source
      // of rows. Previously the page also fetched the full /staff/active list
      // and left-joined it with the current workload page, which inflated the
      // table with every active staff member and broke pagination semantics.
      const workloadRes = await api.getPage<StaffWorkloadStatistics>(
        `/dashboard/workload/period/${selectedPeriodId}/page`,
        { page, size: pageSize, sort: "scheduleCount,desc" },
        { signal },
      );
      if (signal?.aborted) return;
      setWorkloads(workloadRes.content ?? []);
      setTotalPages(workloadRes.totalPages ?? 0);
      setTotalElements(workloadRes.totalElements ?? 0);
    } catch (err) {
      if (signal?.aborted) return;
      setMessage(getErrorMessage(err, "Lỗi tải dữ liệu nhân sự."));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [selectedPeriodId, page, pageSize]);

  useEffect(() => {
    void fetchPeriods();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!selectedPeriodId) return;
    setPage(0);
    const controller = new AbortController();
    void fetchData(controller.signal);
    return () => controller.abort();
  }, [fetchData, selectedPeriodId]);

  useAutoDismiss(message, () => setMessage(null));

  const enriched = useMemo(() => {
    return workloads
      .map((w) => {
        const L01 = w.L01Count;
        const L02 = w.L02Count;
        const L03 = w.L03Count;
        const L04 = w.L04Count;
        const total = w.scheduleCount;
        const shiftCount = pickShiftCount(
          {
            staff: { id: w.staffId, fullName: w.staffName, maxShiftsPerMonth: null },
            L01,
            L02,
            L03,
            L04,
            total,
          },
          view,
        );
        return {
          staff: {
            id: w.staffId,
            fullName: w.staffName,
            // username/specialty are not in the workload page response; the
            // table renders "—" when missing and pickCap falls back to 6.
            username: "",
            specialty: null as { name: string } | null,
            maxShiftsPerMonth: null,
          },
          total,
          L01,
          L02,
          L03,
          L04,
          shiftCount,
          leaveDays: w.leaveDays,
        };
      })
      .filter((item) => {
        if (!search.trim()) return true;
        return item.staff.fullName.toLowerCase().includes(search.toLowerCase());
      })
      .sort((a, b) => b.shiftCount - a.shiftCount);
  }, [workloads, search, view]);

  const summary = useMemo(() => {
    const rows = enriched.map((e) => ({
      staff: {
        id: e.staff.id,
        fullName: e.staff.fullName,
        maxShiftsPerMonth: e.staff.maxShiftsPerMonth ?? null,
      },
      L01: e.L01,
      L02: e.L02,
      L03: e.L03,
      L04: e.L04,
      total: e.total,
    }));
    return computeSummary(rows, view);
  }, [enriched, view]);

  function getWorkloadColor(current: number, max: number) {
    const pct = max > 0 ? (current / max) * 100 : 0;
    if (pct >= 90) return "bg-error";
    if (pct >= 70) return "bg-blue-100";
    return "bg-emerald-100";
  }

  // Stable callbacks (REPORTS-EXPORT-002) so ExportControls doesn't re-render
  // just because the parent re-rendered. Replaces the inline arrow functions
  // that previously created a fresh reference on every render.
  const handleExportSuccess = useCallback(
    (m: string) => toastSuccess(m),
    [toastSuccess],
  );
  const handleExportError = useCallback(
    (m: string) => {
      setMessage(m);
      toastError(m);
    },
    [toastError],
  );

  return (
    <>
      <BackButton href="/reports" variant="full" label="Quay lại" className="mb-4" />

      {message && (
        <div className="rounded-lg border border-red-300 bg-red-100 text-red-800 px-4 py-3 text-sm">
          {message}
        </div>
      )}

      {/* Period Selector */}
      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm flex flex-wrap items-center justify-between gap-4">
        <label className="flex items-center gap-2">
          <span className="text-sm font-medium text-on-surface">Kỳ lịch:</span>
          <div className="relative">
            <select
              className="h-9 pl-3 pr-8 rounded-lg border border-outline-variant bg-surface-container-low text-sm text-on-surface appearance-none focus:outline-none focus:ring-1 focus:ring-blue-300 cursor-pointer"
              value={selectedPeriodId ?? ""}
              onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
            >
              <option value="">-- Chọn kỳ lịch --</option>
              {periods.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.periodName} {p.status === "DRAFT" ? "(đang soạn)" : p.status === "PUBLISHED" ? "(đã công bố)" : "(lưu trữ)"}
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[18px] pointer-events-none">expand_more</span>
          </div>
        </label>
        {selectedPeriodId && (
          <ExportControls
            periodId={selectedPeriodId}
            pinFormat="excel-workload"
            onSuccess={handleExportSuccess}
            onError={handleExportError}
          />
        )}
      </section>

      {/* Summary KPIs */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { label: "Tổng phân công", value: summary.total, icon: "event_available", accent: "bg-blue-100 text-blue-800" },
          { label: "Trung bình / người", value: summary.avg, icon: "analytics", accent: "bg-emerald-100 text-emerald-800 border border-emerald-300" },
          { label: "Cao nhất", value: summary.max, icon: "trending_up", accent: "bg-amber-100 text-amber-800" },
          { label: "Quá tải", value: summary.overloaded, icon: "warning", accent: summary.overloaded > 0 ? "bg-red-100 text-red-800 border border-red-300" : "bg-surface-container text-outline" },
        ].map((kpi) => (
          <article key={kpi.label} className="flex flex-col justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <div className="flex justify-between items-start">
              <p className="text-label-sm text-on-surface-variant">{kpi.label}</p>
              <span className={`material-symbols-outlined p-1.5 rounded-md ${kpi.accent} text-[18px]`}>{kpi.icon}</span>
            </div>
            <p className="mt-3 text-display-lg font-bold text-on-surface">{loading ? "—" : kpi.value}</p>
          </article>
        ))}
      </div>

      {/* BUGFIX (REPORTS-EXPORT-001): the bare <a> below was removed.
          It pointed directly at the backend export endpoint without attaching
          the auth header, so users got redirected to /login when the
          cookie/session was missing. ExportControls above already exposes the
          authenticated excel-workload export — no second button needed. */}

      {/* Balance chart (M07-F09) — visible whenever there is at least one
          staff member in scope; mirrors the active view filter. */}
      {enriched.length > 0 && (
        <WorkloadBalanceChart
          view={view}
          // BUGFIX (REPORTS-STAFF-003): the chart defaulted to limit=12 and
          // silently dropped everyone else — the page header read "12 nhân sự"
          // even when the table listed 20. Pass the real scope so the chart
          // always mirrors the table.
          limit={enriched.length}
          data={enriched.map((e) => ({
            staffId: e.staff.id,
            staffName: e.staff.fullName,
            L01: e.L01,
            L02: e.L02,
            L03: e.L03,
            L04: e.L04,
            total: e.total,
            maxShiftsPerMonth: e.staff.maxShiftsPerMonth ?? null,
          }))}
        />
      )}

      {/* Search + View tabs */}
      <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="relative max-w-md flex-1">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">search</span>
            <input
              className="w-full rounded-lg border border-transparent bg-surface py-2.5 pl-10 pr-4 text-[14px] text-on-surface transition-all placeholder:text-outline focus:border-blue-300 focus:bg-surface-container-lowest focus:outline-none focus:ring-1 focus:ring-blue-300"
              placeholder="Tìm theo tên, mã NV hoặc khoa..."
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            />
            {/* REPORTS-STAFF-002: the workload endpoint has no `keyword`
                parameter, so the search box is a CLIENT-SIDE filter on the
                current page slice. Reset to page 0 on every keystroke and
                show this hint so users don't expect a global filter. */}
            {search.trim() && (
              <p className="mt-1 text-[11px] text-on-surface-variant" data-testid="staff-search-hint">
                Lọc trên trang hiện tại — backend chưa hỗ trợ tìm kiếm toàn kỳ.
              </p>
            )}
          </div>
          {/* View-mode tabs */}
          <div role="tablist" aria-label="Chọn loại lịch cần thống kê" className="flex flex-wrap gap-1 rounded-lg bg-surface-container p-1">
            {VIEW_MODES.map((mode) => {
              const active = view === mode.value;
              return (
                <button
                  key={mode.value}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  title={mode.helper}
                  onClick={() => setView(mode.value)}
                  className={`rounded-md px-3 py-1.5 text-[12px] font-semibold transition-colors ${
                    active
                      ? "bg-blue-100 text-blue-800 shadow-sm"
                      : "text-on-surface-variant hover:bg-surface-container-lowest"
                  }`}
                >
                  {mode.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Skewed-distribution warning (M02-F05). */}
        {!loading && summary.overloaded > 0 && (
          <div
            role="alert"
            data-testid="skew-warning"
            className="flex items-start gap-3 rounded-xl border-l-4 border-l-error bg-red-100 text-red-800 px-5 py-4 shadow-sm"
          >
            <div className="shrink-0 w-10 h-10 rounded-full bg-error flex items-center justify-center">
              <span className="material-symbols-outlined text-[20px] text-white" style={{ fontVariationSettings: "'FILL' 1" }}>warning</span>
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <p className="font-semibold text-[14px] text-red-800">Cảnh báo phân bổ lệch</p>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-error text-white text-[11px] font-bold">
                  {summary.overloaded} nhân sự
                </span>
              </div>
              <p className="text-[13px] bg-red-100 text-red-800 mt-1 leading-relaxed">
                Vượt ngưỡng tải tối đa cho phép.
                Cách biệt cao nhất ({summary.max}) và trung bình ({summary.avg}):{" "}
                <strong className="font-semibold">{Math.max(0, summary.max - summary.avg)} ca</strong>.
              </p>
            </div>
          </div>
        )}
      </section>

      {/* Table */}
      <section className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm">
        <div className="overflow-x-auto">
          {loading ? (
            <div className="flex items-center justify-center py-20">
              <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : enriched.length === 0 ? (
            <EmptyState
              icon={search ? "search_off" : "groups"}
              title={search ? "Không tìm thấy nhân sự phù hợp" : "Chưa có dữ liệu nhân sự"}
              description={
                search
                  ? "Thử từ khóa khác hoặc đổi kỳ lịch."
                  : "Hệ thống chưa có workload cho nhân sự trong kỳ lịch đã chọn."
              }
              action={
                search ? (
                  <Button
                    variant="secondary"
                    size="md"
                    onClick={() => setSearch("")}
                  >
                    Đặt lại tìm kiếm
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <table className="w-full border-collapse text-left" data-testid="staff-workload-table" aria-label="Page Table">
              <thead>
                <tr className="border-b border-outline-variant bg-surface-container-low">
                  <th scope="col" className="px-5 py-3 text-label-sm text-on-surface-variant">Nhân sự</th>
                  <th scope="col" className="px-5 py-3 text-label-sm text-on-surface-variant">Khoa</th>
                  <th scope="col" className="px-5 py-3 text-label-sm text-on-surface-variant text-center">Tổng ca</th>
                  <th scope="col" className="px-5 py-3 text-label-sm text-on-surface-variant text-center">L01</th>
                  <th scope="col" className="px-5 py-3 text-label-sm text-on-surface-variant text-center">L02</th>
                  <th scope="col" className="px-5 py-3 text-label-sm text-on-surface-variant text-center">L03</th>
                  <th scope="col" className="px-5 py-3 text-label-sm text-on-surface-variant text-center">L04</th>
                  <th scope="col" className="px-5 py-3 text-label-sm text-on-surface-variant text-center">Ngày nghỉ</th>
                  <th scope="col" className="px-5 py-3 text-label-sm text-on-surface-variant">Tải trọng</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {enriched.map((item) => {
                  const capForView = pickCap(
                    {
                      staff: {
                        id: item.staff.id,
                        fullName: item.staff.fullName,
                        maxShiftsPerMonth: item.staff.maxShiftsPerMonth ?? null,
                      },
                      L01: item.L01,
                      L02: item.L02,
                      L03: item.L03,
                      L04: item.L04,
                      total: item.total,
                    },
                    view,
                  );
                  const pct = capForView > 0 ? (item.shiftCount / capForView) * 100 : 0;
                  const isOver = isOverloaded(
                    {
                      staff: {
                        id: item.staff.id,
                        fullName: item.staff.fullName,
                        maxShiftsPerMonth: item.staff.maxShiftsPerMonth ?? null,
                      },
                      L01: item.L01,
                      L02: item.L02,
                      L03: item.L03,
                      L04: item.L04,
                      total: item.total,
                    },
                    view,
                    capForView,
                  );
                  return (
                    <tr key={item.staff.id} className="transition-colors hover:bg-surface-container-lowest group">
                      <td className="px-5 py-3">
                        <div className="flex items-center gap-3">
                          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-blue-800 text-[12px] font-bold text-blue-800">
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
                        <span className={`text-[14px] font-bold ${isOver ? "text-red-800" : "text-on-surface"}`}>
                          {item.shiftCount}
                        </span>
                        <span className="text-[11px] text-outline"> / {capForView}</span>
                      </td>
                      {[item.L01, item.L02, item.L03, item.L04].map((count, i) => (
                        <td key={i} className="px-5 py-3 text-center text-[13px] text-on-surface-variant">{count}</td>
                      ))}
                      <td className="px-5 py-3 text-center">
                        <span
                          className={`inline-flex items-center justify-center min-w-[28px] px-2 py-0.5 rounded-full text-[12px] font-semibold ${
                            item.leaveDays > 0
                              ? "bg-amber-100 text-amber-800 border border-amber-300-fixed-variant"
                              : "bg-surface-container text-outline"
                          }`}
                          title={item.leaveDays > 0 ? `Số ngày nghỉ được duyệt trong kỳ` : "Không có ngày nghỉ"}
                        >
                          {item.leaveDays}
                        </span>
                      </td>
                      <td className="px-5 py-3 min-w-[140px]">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 bg-surface-variant rounded-full h-2">
                            <div
                              className={`h-2 rounded-full transition-all ${getWorkloadColor(item.shiftCount, capForView)}`}
                              style={{ width: `${Math.min(100, pct)}%` }}
                            />
                          </div>
                          <span className={`text-[11px] font-bold min-w-[40px] ${isOver ? "text-red-800" : "text-outline"}`}>
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
          <Pagination
            currentPage={page + 1}
            totalPages={totalPages}
            totalItems={totalElements}
            pageSize={pageSize}
            onPageChange={(p) => setPage(p - 1)}
            onPageSizeChange={(s) => { setPageSize(s); setPage(0); }}
          />
        )}
      </section>
    </>
  );
}
