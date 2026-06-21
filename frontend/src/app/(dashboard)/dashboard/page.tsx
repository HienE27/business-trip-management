"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import { EmptyState } from "@/components/ui/EmptyState";
import { SkeletonDashboardKPIGrid } from "@/components/ui/Skeleton";
import { KPICard } from "@/components/ui/KPICard";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { formatDate } from "@/lib/date";
import { buildCalendarAnnotations, buildCoverageMap } from "@/components/monthly-schedule/utils";
import { useSchedulePeriodData } from "@/hooks/useSchedulePeriodData";
import { useScheduleFilters } from "@/hooks/useScheduleFilters";
import type { DashboardData } from "@/types/api";

type WorkflowStep = {
  label: string;
  description: string;
  icon: string;
  href: string;
  accent: string;
};

const QUICK_ACTIONS: WorkflowStep[] = [
  {
    label: "Lập lịch tháng",
    description: "Auto schedule, conflict check, publish.",
    icon: "calendar_month",
    href: "/monthly-schedule",
    accent: "border-l-primary bg-primary-fixed/20",
  },
  {
    label: "Nhân sự",
    description: "Thêm, cập nhật hồ sơ nhân viên.",
    icon: "groups",
    href: "/staff",
    accent: "border-l-secondary bg-secondary-fixed/20",
  },
  {
    label: "Duyệt đổi trực",
    description: "Xử lý yêu cầu đổi ca từ nhân viên.",
    icon: "swap_horiz",
    href: "/swap-requests",
    accent: "border-l-tertiary bg-tertiary-fixed/20",
  },
  {
    label: "Nghỉ phép",
    description: "Xem và cân đối yêu cầu nghỉ phép.",
    icon: "event_busy",
    href: "/leave-requests",
    accent: "border-l-outline bg-surface-container-low",
  },
  {
    label: "Báo cáo",
    description: "Tổng hợp kỳ lịch, thống kê xung đột.",
    icon: "assessment",
    href: "/reports",
    accent: "border-l-secondary bg-secondary-fixed/20",
  },
  {
    label: "Nhật ký",
    description: "Tra cứu vết thay đổi toàn hệ thống.",
    icon: "history",
    href: "/audit-history",
    accent: "border-l-outline bg-surface-container-low",
  },
];

export default function DashboardPage() {
  const router = useRouter();
  const data = useSchedulePeriodData({ conflictPollMs: 60000 });

  const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
  const [exporting, setExporting] = useState(false);
  const filters = useScheduleFilters({ basePath: "/dashboard" });
  const { selectedTab, selectedStaffId, setStaffId, setDate } = filters;

  const {
    periods,
    selectedPeriodId,
    selectedPeriod,
    schedules,
    activeStaff,
    conflictData,
    compensationDays,
    requirements,
    loading,
    message,
    setSelectedPeriodId,
    refresh,
    setMessage,
  } = data;

  // Dashboard summary fetch (KPIs, shift statistics) — chỉ dashboard cần.
  useEffect(() => {
    let active = true;
    api
      .get<DashboardData>("/dashboard")
      .then((res) => {
        if (active) setDashboardData(res);
      })
      .catch((error: unknown) => {
        if (active) setMessage(getErrorMessage(error, "Không thể tải dữ liệu dashboard."));
      });
    return () => {
      active = false;
    };
  }, [setMessage]);

  const handleExport = useCallback(async () => {
    if (!selectedPeriodId) return;
    setExporting(true);
    try {
      const blob = await api.exportScheduleExcel(selectedPeriodId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `schedule-export-${selectedPeriodId}.xlsx`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (error) {
      setMessage(getErrorMessage(error, "Xuất file thất bại. Vui lòng thử lại."));
    } finally {
      setExporting(false);
    }
  }, [selectedPeriodId, setMessage]);

  const handleExportPdf = useCallback(async () => {
    if (!selectedPeriodId) return;
    setExporting(true);
    try {
      const blob = await api.exportSchedulePdf(selectedPeriodId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `lich-cong-tac-${selectedPeriodId}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (error) {
      setMessage(getErrorMessage(error, "Xuất PDF thất bại. Vui lòng thử lại."));
    } finally {
      setExporting(false);
    }
  }, [selectedPeriodId, setMessage]);

  const totalConflicts = conflictData?.totalConflicts ?? 0;
  const pendingExchanges = dashboardData?.summary.pendingScheduleExchanges ?? 0;
  const pendingLeave = dashboardData?.summary.pendingLeaveRequests ?? 0;
  const activeStaffCount = dashboardData?.summary.activeStaff ?? 0;
  const totalSchedules = dashboardData?.summary.totalSchedules ?? 0;
  const L01Count = dashboardData?.shiftStatistics?.L01Count ?? 0;
  const L02Count = dashboardData?.shiftStatistics?.L02Count ?? 0;

  const calendarAnnotations = useMemo(
    () => buildCalendarAnnotations(compensationDays, conflictData?.conflicts ?? []),
    [compensationDays, conflictData]
  );

  const computedCoverages = useMemo(() => buildCoverageMap(requirements), [requirements]);

  const initialCalendarYear = useMemo(() => {
    if (selectedPeriod?.startDate) return new Date(selectedPeriod.startDate).getFullYear();
    return new Date().getFullYear();
  }, [selectedPeriod]);

  const initialCalendarMonth = useMemo(() => {
    if (selectedPeriod?.startDate) return new Date(selectedPeriod.startDate).getMonth();
    return new Date().getMonth();
  }, [selectedPeriod]);

  const staffList = useMemo(
    () => activeStaff.map((s) => ({ id: s.id, fullName: s.fullName })),
    [activeStaff]
  );

  return (
    <>
      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
          {message}
        </div>
      )}

      {/* Compact bar: alerts + period selector + export actions */}
      <div className="flex flex-col gap-2">
        {(totalConflicts > 0 || pendingExchanges > 0 || pendingLeave > 0) && (
          <div className="flex flex-wrap gap-1.5 rounded-lg border border-tertiary/30 bg-tertiary/5 px-2.5 py-2">
            {totalConflicts > 0 && (
              <Link href="/reports/conflicts" className="inline-flex items-center gap-1.5 rounded-full bg-error-container text-on-error-container border border-error/20 px-3 py-1 text-[12px] font-semibold hover:bg-error-container/70 transition-colors">
                <span className="w-1.5 h-1.5 rounded-full bg-error shrink-0" />
                {totalConflicts} xung đột
              </Link>
            )}
            {pendingExchanges > 0 && (
              <Link href="/swap-requests" className="inline-flex items-center gap-1.5 rounded-full bg-tertiary/5 text-on-tertiary border border-tertiary/20 px-3 py-1 text-[12px] font-semibold hover:bg-tertiary/10 transition-colors">
                <span className="w-1.5 h-1.5 rounded-full bg-tertiary shrink-0" />
                {pendingExchanges} đổi trực
              </Link>
            )}
            {pendingLeave > 0 && (
              <Link href="/leave-requests" className="inline-flex items-center gap-1.5 rounded-full bg-primary/5 text-primary border border-primary/20 px-3 py-1 text-[12px] font-semibold hover:bg-primary/10 transition-colors">
                <span className="w-1.5 h-1.5 rounded-full bg-primary shrink-0" />
                {pendingLeave} nghỉ phép
              </Link>
            )}
          </div>
        )}

        {periods.length === 0 && !loading ? (
          <EmptyState
            icon="calendar_month"
            title="Chưa có kỳ lịch nào"
            description="Hãy tạo kỳ lịch mới để bắt đầu phân công trực."
          />
        ) : periods.length > 0 ? (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 shadow-sm">
            <div className="flex items-center gap-1.5 text-[11px] text-on-surface-variant shrink-0">
              <span className="material-symbols-outlined text-[14px]">calendar_month</span>
              <span className="font-semibold">Kỳ lịch:</span>
            </div>
            <div className="relative">
              <select
                className="h-7 pl-2.5 pr-7 bg-surface-container-low border border-transparent rounded-md text-[12px] text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all min-w-[180px]"
                value={selectedPeriodId ?? ""}
                onChange={(e) => {
                  const val = Number(e.target.value);
                  if (val) setSelectedPeriodId(val);
                }}
              >
                {periods.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.periodName} ({formatDate(p.startDate)} – {formatDate(p.endDate)})
                  </option>
                ))}
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-1 top-1/2 -translate-y-1/2 text-outline text-[14px]">expand_more</span>
            </div>
            {selectedPeriod && (
              <span className={`inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[11px] font-semibold ${
                selectedPeriod.status === "PUBLISHED"
                  ? "bg-secondary-container text-on-secondary-container"
                  : selectedPeriod.status === "ARCHIVED"
                  ? "bg-surface-container-highest text-outline"
                  : "bg-primary-fixed text-primary"
              }`}>
                {selectedPeriod.status === "PUBLISHED" ? "Đã công bố" : selectedPeriod.status === "ARCHIVED" ? "Đã lưu trữ" : "Nháp"}
              </span>
            )}
            <div className="ml-auto flex items-center gap-1.5">
              {selectedPeriodId && (
                <>
                  <button
                    onClick={() => void handleExportPdf()}
                    disabled={exporting}
                    className="inline-flex items-center gap-1 px-2 py-1 rounded-md bg-surface-container-low border border-outline-variant text-[11px] font-medium text-on-surface hover:bg-surface-container transition-colors disabled:opacity-50"
                  >
                    <span className="material-symbols-outlined text-[14px]">picture_as_pdf</span>
                    {exporting ? "…" : "PDF"}
                  </button>
                  <button
                    onClick={() => void handleExport()}
                    disabled={exporting}
                    className="inline-flex items-center gap-1 px-2 py-1 rounded-md bg-surface-container-low border border-outline-variant text-[11px] font-medium text-on-surface hover:bg-surface-container transition-colors disabled:opacity-50"
                  >
                    <span className="material-symbols-outlined text-[14px]">{exporting ? "hourglass_empty" : "download"}</span>
                    {exporting ? "…" : "Excel"}
                  </button>
                </>
              )}
              <Link
                href="/monthly-schedule"
                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-primary text-on-primary text-[11px] font-medium hover:bg-primary/90 transition-colors"
              >
                <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
                Lập lịch
              </Link>
            </div>
          </div>
        ) : null}
      </div>

      {loading ? (
        <SkeletonDashboardKPIGrid />
      ) : (
        <div className="grid gap-2 grid-cols-2 sm:grid-cols-4">
          <KPICard label="Nhân sự đang hoạt động" value={activeStaffCount} icon="groups" tone="info" helper="Trên tổng nhân sự" />
          <KPICard label="Tổng phân công" value={totalSchedules} icon="event_available" tone="success" helper="Trong kỳ đang vận hành" />
          <KPICard label="Xung đột phát hiện" value={totalConflicts} icon="warning" tone={totalConflicts > 0 ? "error" : "neutral"} helper={totalConflicts > 0 ? "Cần xử lý trước publish" : "Không phát hiện"} />
          <KPICard label="Yêu cầu chờ duyệt" value={pendingExchanges + pendingLeave} icon="pending_actions" tone={pendingExchanges + pendingLeave > 0 ? "warning" : "neutral"} helper="Đổi trực + nghỉ phép" />
          <KPICard label="Trực 24/24" value={L01Count} icon="emergency" tone="info" />
          <KPICard label="Thông tầm" value={L02Count} icon="schedule" tone="success" />
          <KPICard label="PK dịch vụ" value={dashboardData?.shiftStatistics?.L03Count ?? 0} icon="medical_services" tone="warning" />
          <KPICard label="PK chuyên gia" value={dashboardData?.shiftStatistics?.L04Count ?? 0} icon="stethoscope" tone="neutral" />
        </div>
      )}

      {selectedPeriod && (
        <>
        <section>
          <div className="flex items-center justify-between gap-3 mb-3">
            <h2 className="text-title-lg text-on-surface shrink-0">
              Lịch kỳ {selectedPeriod.periodName}
            </h2>
            <div className="flex items-center gap-2">
              {staffList.length > 0 && (
                <div className="relative">
                  <select
                    className="h-7 pl-3 pr-8 bg-surface-container-low border border-outline-variant rounded-md text-label-sm text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all min-w-[160px]"
                    value={selectedStaffId ?? ""}
                    onChange={(e) => {
                      const val = e.target.value ? Number(e.target.value) : null;
                      setStaffId(val);
                    }}
                  >
                    <option value="">Tất cả nhân sự</option>
                    {staffList.map((s) => (
                      <option key={s.id} value={s.id}>{s.fullName}</option>
                    ))}
                  </select>
                  <span className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[16px]">expand_more</span>
                </div>
              )}
              <Link
                href="/monthly-schedule"
                className="inline-flex items-center gap-1 text-label-sm text-primary hover:underline shrink-0"
              >
                Mở lịch tháng
                <span className="material-symbols-outlined text-[16px]">chevron_right</span>
              </Link>
            </div>
          </div>
          <div className="rounded-lg border border-outline-variant bg-surface-container-lowest overflow-hidden shadow-sm">
            <ScheduleCalendarWidget
              schedules={schedules}
              calendarAnnotations={calendarAnnotations}
              coverages={computedCoverages}
              staffList={staffList}
              staffFilter={selectedStaffId}
              specialtyList={[]}
              initialYear={initialCalendarYear}
              initialMonth={initialCalendarMonth}
              periodId={selectedPeriod.id}
              compensationDays={compensationDays}
              viewMode="matrix"
              isReadOnly={true}
              canEditOverride={true}
              selectedTab={selectedTab}
              onFilterTypeChange={(filter) => filters.setTab(filter as "L01" | "L02" | "L03" | "L04" | "ALL")}
              onRefresh={() => void refresh()}
              onDayClick={(date) => {
                const dateStr = date.toISOString().slice(0, 10);
                setDate(dateStr);
                router.push(`/monthly-schedule?date=${dateStr}`);
              }}
              onStaffFilterChange={(staffId) => setStaffId(staffId)}
            />
          </div>
        </section>

        <section>
          <h2 className="mb-3 text-title-lg text-on-surface">Thao tác nhanh</h2>
          <div className="grid gap-2 grid-cols-2 sm:grid-cols-3 lg:grid-cols-6">
            {QUICK_ACTIONS.map((action) => (
              <Link
                key={action.href}
                href={action.href}
                className={`group flex flex-col items-center gap-2 rounded-lg border border-l-4 border-outline-variant bg-surface-container-lowest p-3 shadow-sm hover:-translate-y-0.5 hover:shadow-md transition-all text-center ${action.accent}`}
              >
                <span className="material-symbols-outlined text-[20px] text-on-surface group-hover:text-primary transition-colors">
                  {action.icon}
                </span>
                <div>
                  <h3 className="text-label-md font-semibold text-on-surface leading-tight">
                    {action.label}
                  </h3>
                </div>
              </Link>
            ))}
          </div>
        </section>
        </>
      )}
    </>
  );
}
