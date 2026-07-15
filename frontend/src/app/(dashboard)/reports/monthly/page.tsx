"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ExportControls } from "@/components/reports/ExportControls";
import { useToast } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { BackButton } from "@/components/ui/BackButton";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";
import type { SchedulePeriod, ShiftStatistics } from "@/types/api";

// Shift labels — references CSS custom properties from globals.css @theme.
// bg-shift-* and text-on-shift-* are defined in globals.css.
const SHIFT_LABELS: Record<string, { label: string; color: string; bg: string }> = {
  L01: { label: "Trực 24/24", color: "text-on-shift-24", bg: "bg-shift-24" },
  L02: { label: "Thông tầm", color: "text-on-shift-all-day", bg: "bg-shift-all-day" },
  L03: { label: "PK Dịch vụ", color: "text-on-shift-service", bg: "bg-shift-service" },
  L04: { label: "PK Chuyên gia", color: "text-on-shift-expert", bg: "bg-shift-expert" },
};

// Chart bar colors — references CSS custom properties from globals.css @theme.
// --color-chart-24 / chart-tt / chart-dv / chart-cg defined in @theme block.
const CHART_COLORS: Record<string, string> = {
  L01: "var(--color-chart-24)",
  L02: "var(--color-chart-tt)",
  L03: "var(--color-chart-dv)",
  L04: "var(--color-chart-cg)",
};

export default function ReportsMonthlyPage() {
  return <ReportsMonthlyContent />;
}

function ReportsMonthlyContent() {
  const { success: toastSuccess, error: toastError } = useToast();
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<SchedulePeriod | null>(null);
  const [stats, setStats] = useState<ShiftStatistics | null>(null);
  const [, setScheduleCount] = useState(0);
  const [staffCount, setStaffCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const fetchPeriods = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.get<SchedulePeriod[]>("/periods");
      setPeriods(data ?? []);
      const active = data?.find((p) => p.status === "PUBLISHED" || p.status === "DRAFT");
      if (active) setSelectedPeriod(active);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi tải danh sách kỳ lịch."));
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchReport = useCallback(async (periodId: number) => {
    try {
      setChecking(true);
      setMessage(null);
      const [statsRes, scheduleData] = await Promise.allSettled([
        api.get<ShiftStatistics>("/dashboard/shifts", { periodId }),
        api.get<unknown>(`/schedules/period/${periodId}`),
      ]);
      if (statsRes.status === "fulfilled") setStats(statsRes.value ?? null);
      if (scheduleData.status === "fulfilled") {
        // Extract schedules from paginated response if needed
        const scheduleValue = scheduleData.value;
        const schedulesArray = (scheduleValue && typeof scheduleValue === 'object' && 'content' in scheduleValue)
          ? (scheduleValue as { content: unknown[] }).content ?? []
          : Array.isArray(scheduleValue) ? scheduleValue : [];
        setScheduleCount(schedulesArray.length);
        // Staff count from active endpoint filtered by period is approximated via schedule unique staff
        const uniqueStaff = new Set<string>();
        for (const s of schedulesArray as Record<string, unknown>[]) {
          if (s["staffId"] != null) uniqueStaff.add(String(s["staffId"]));
        }
        setStaffCount(uniqueStaff.size);
      }
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi tải báo cáo kỳ lịch."));
    } finally {
      setChecking(false);
    }
  }, []);

  useEffect(() => {
    void fetchPeriods();
  }, [fetchPeriods]);

  useEffect(() => {
    if (selectedPeriod) void fetchReport(selectedPeriod.id);
  }, [selectedPeriod, fetchReport]);

  useAutoDismiss(message, () => setMessage(null));

  const handleExportSuccess = useCallback((msg: string) => {
    toastSuccess(msg);
  }, [toastSuccess]);

  const handleExportError = useCallback((msg: string) => {
    setMessage(msg);
    toastError(msg);
  }, [toastError]);

  const shiftItems = useMemo(() => {
    if (!stats) return [];
    return [
      { key: "L01", label: SHIFT_LABELS.L01.label, color: SHIFT_LABELS.L01.color, bg: SHIFT_LABELS.L01.bg, count: stats.L01Count },
      { key: "L02", label: SHIFT_LABELS.L02.label, color: SHIFT_LABELS.L02.color, bg: SHIFT_LABELS.L02.bg, count: stats.L02Count },
      { key: "L03", label: SHIFT_LABELS.L03.label, color: SHIFT_LABELS.L03.color, bg: SHIFT_LABELS.L03.bg, count: stats.L03Count },
      { key: "L04", label: SHIFT_LABELS.L04.label, color: SHIFT_LABELS.L04.color, bg: SHIFT_LABELS.L04.bg, count: stats.L04Count },
    ];
  }, [stats]);

  const totalShift = useMemo(() => {
    if (!stats) return 0;
    return stats.L01Count + stats.L02Count + stats.L03Count + stats.L04Count;
  }, [stats]);

  const maxShift = useMemo(() => Math.max(...shiftItems.map((i) => i.count), 1), [shiftItems]);

  return (
    <>
      <BackButton href="/reports" variant="full" label="Quay lại" className="mb-4" />

      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
          {message}
        </div>
      )}

      {/* Period Selector */}
      <section className="flex items-center justify-between gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm flex-wrap">
        <div className="flex items-center gap-3">
          <span className="material-symbols-outlined text-[22px] text-primary">calendar_month</span>
          <div>
            <h2 className="text-[16px] font-semibold text-on-surface">Báo cáo kỳ lịch</h2>
            <p className="text-[12px] text-on-surface-variant">Chọn kỳ lịch để xem báo cáo tổng hợp.</p>
          </div>
        </div>
        <div className="relative min-w-[280px]">
          <label htmlFor="report-monthly-period" className="sr-only">Chọn kỳ lịch</label>
          <select
            id="report-monthly-period"
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
                {p.periodName}{" "}
                (
                {p.status === "PUBLISHED" ? "Đã công bố" : p.status === "DRAFT" ? "Nháp" : "Đã lưu trữ"}
                )
              </option>
            ))}
          </select>
          <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">
            expand_more
          </span>
        </div>
      </section>

      {!selectedPeriod && !loading ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4">
          <span className="material-symbols-outlined text-5xl text-outline">bar_chart</span>
          <p className="text-on-surface-variant">Chọn một kỳ lịch để xem báo cáo.</p>
        </div>
      ) : loading ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-outline-variant bg-surface py-20 gap-4">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-on-surface-variant">Đang tải kỳ lịch...</p>
        </div>
      ) : (
        <div className="space-y-5">
          {/* Period info */}
          {selectedPeriod && (
            <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
              <div className="flex items-center justify-between flex-wrap gap-3">
                <div>
                  <h3 className="text-[18px] font-bold text-on-surface">{selectedPeriod.periodName}</h3>
                  <p className="text-[13px] text-on-surface-variant mt-0.5">
                    {new Date(selectedPeriod.startDate).toLocaleDateString("vi-VN", { day: "2-digit", month: "long", year: "numeric" })}
                    {" — "}
                    {new Date(selectedPeriod.endDate).toLocaleDateString("vi-VN", { day: "2-digit", month: "long", year: "numeric" })}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span
                    className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[12px] font-semibold ${
                      selectedPeriod.status === "PUBLISHED"
                        ? "bg-secondary-container text-on-secondary-container"
                        : selectedPeriod.status === "DRAFT"
                        ? "bg-primary-fixed text-primary"
                        : "bg-surface-container-high text-outline"
                    }`}
                  >
                    {selectedPeriod.status === "PUBLISHED"
                      ? "Đã công bố"
                      : selectedPeriod.status === "DRAFT"
                      ? "Nháp"
                      : "Đã lưu trữ"}
                  </span>
                  <ExportControls
                    periodId={selectedPeriod.id}
                    showWorkload
                    onSuccess={handleExportSuccess}
                    onError={handleExportError}
                  />
                </div>
              </div>
            </div>
          )}

          {/* KPI Row */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[
              {
                label: "Tổng ca trực",
                value: checking ? "—" : totalShift,
                icon: "event_available",
                accent: "bg-primary-fixed text-primary",
              },
              {
                label: "Nhân sự được phân",
                value: checking ? "—" : staffCount,
                icon: "groups",
                accent: "bg-secondary-container text-secondary",
              },
              {
                label: "Tỷ lệ phủ (%)",
                value: checking ? "—" : `${totalShift > 0 && staffCount > 0 ? Math.round((totalShift / (staffCount * 4)) * 100) : 0}%`,
                icon: "donut_large",
                accent: "bg-tertiary-fixed text-tertiary",
              },
              {
                label: "Trạng thái",
                value: selectedPeriod?.status === "PUBLISHED" ? "Đã công bố" : selectedPeriod?.status === "DRAFT" ? "Nháp" : "Lưu trữ",
                icon: selectedPeriod?.status === "PUBLISHED" ? "check_circle" : "edit_note",
                accent:
                  selectedPeriod?.status === "PUBLISHED"
                    ? "bg-secondary-container text-secondary"
                    : "bg-primary-fixed text-primary",
              },
            ].map((kpi) => (
              <article
                key={kpi.label}
                className="flex flex-col justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm"
              >
                <div className="flex justify-between items-start">
                  <p className="text-label-sm text-on-surface-variant">{kpi.label}</p>
                  <span className={`material-symbols-outlined p-1.5 rounded-md ${kpi.accent} text-[18px]`}>{kpi.icon}</span>
                </div>
                <p className="mt-3 text-display-lg font-bold text-on-surface">
                  {checking ? "—" : kpi.value}
                </p>
              </article>
            ))}
          </div>

          {/* Shift Breakdown */}
          {checking ? (
            <div className="flex items-center justify-center rounded-xl border border-outline-variant bg-surface py-16">
              <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : stats ? (
            <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
              <h3 className="text-[16px] font-semibold text-on-surface mb-5">Phân bổ theo loại ca</h3>
              <div className="space-y-4">
                {shiftItems.map((item) => (
                  <div key={item.key} className="flex items-center gap-4">
                    <div className="w-36 shrink-0">
                      <p className={`text-[13px] font-semibold ${item.color}`}>{item.label}</p>
                      <p className="text-[12px] text-outline">
                        #{item.key}
                      </p>
                    </div>
                    <div className="flex-1 bg-surface-variant rounded-full h-4 overflow-hidden">
                      <div
                        className={`h-4 rounded-full transition-all ${item.bg.replace("bg-", "bg-")}`}
                        style={{
                          width: `${Math.max((item.count / maxShift) * 100, 2)}%`,
                          backgroundColor: CHART_COLORS[item.key] ?? "var(--color-outline)",
                        }}
                      />
                    </div>
                    <span className="text-[14px] font-bold text-on-surface min-w-[40px] text-right">
                      {item.count}
                    </span>
                    <span className="text-[12px] text-outline min-w-[50px]">
                      ({totalShift > 0 ? Math.round((item.count / totalShift) * 100) : 0}%)
                    </span>
                  </div>
                ))}
              </div>
              <div className="mt-5 pt-4 border-t border-outline-variant flex items-center justify-between">
                <span className="text-[14px] font-semibold text-on-surface">Tổng cộng</span>
                <span className="text-[16px] font-bold text-primary">{totalShift} ca</span>
              </div>
            </div>
          ) : null}
        </div>
      )}
    </>
  );
}
