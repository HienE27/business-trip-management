"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import { EmptyState } from "@/components/ui/EmptyState";
import { api } from "@/lib/api";
import type {
  DashboardData,
  Schedule,
  SchedulePeriod,
  ConflictCheckResponse,
} from "@/types/api";

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

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString("vi-VN");
}

export default function DashboardPage() {
  const router = useRouter();
  const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [recentSchedules, setRecentSchedules] = useState<Schedule[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [compensationDays, setCompensationDays] = useState<import("@/types/api").CompensationDay[]>([]);
  const [requirements, setRequirements] = useState<import("@/types/api").ShiftRequirement[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);

  const load = useCallback(async (periodId?: number) => {
    let cancelled = false;
    setLoading(true);
    setMessage(null);

    try {
      const [dashboardRes, periodsRes] = await Promise.allSettled([
        api.get<DashboardData>("/dashboard"),
        api.get<SchedulePeriod[]>("/periods"),
      ]);

      if (cancelled) return;

      const dashboard =
        dashboardRes.status === "fulfilled" ? dashboardRes.value : null;
      const periodList =
        periodsRes.status === "fulfilled" ? periodsRes.value ?? [] : [];

      setDashboardData(dashboard);

      const activePeriod = periodList.find(
        (p) => p.status === "DRAFT" || p.status === "PUBLISHED"
      );

      const targetPeriodId = periodId ?? activePeriod?.id;
      const targetPeriod = targetPeriodId
        ? periodList.find((p) => p.id === targetPeriodId) ?? activePeriod
        : activePeriod;

      if (targetPeriod) {
        if (periodId !== undefined) {
          setSelectedPeriodId(targetPeriod.id);
        }

        const [scheduleRes, conflictRes, compDaysRes, reqRes] = await Promise.allSettled([
          api.get<Schedule[]>(`/schedules/period/${targetPeriod.id}`),
          api.get<ConflictCheckResponse>(
            `/schedules/conflicts/check/${targetPeriod.id}`
          ),
          api.get<import("@/types/api").CompensationDay[]>(
            `/schedules/compensation-days/${targetPeriod.id}`
          ),
          api.get<import("@/types/api").ShiftRequirement[]>(
            `/shift-requirements/period/${targetPeriod.id}`
          ),
        ]);

        if (cancelled) return;

        if (scheduleRes.status === "fulfilled") {
          const all = scheduleRes.value ?? [];
          setRecentSchedules(all.slice(0, 6));
        }
        if (conflictRes.status === "fulfilled") {
          setConflictData(conflictRes.value);
        }
        if (compDaysRes.status === "fulfilled") {
          setCompensationDays(compDaysRes.value ?? []);
        }
        if (reqRes.status === "fulfilled") {
          setRequirements(reqRes.value ?? []);
        }
      }
    } finally {
      if (!cancelled) {
        setLoading(false);
      }
    }
  }, []);

  const handlePeriodChange = useCallback(
    (periodId: number) => {
      setSelectedPeriodId(periodId);
      void load(periodId);
    },
    [load]
  );

  const handleExport = useCallback(async () => {
    const periodId = selectedPeriodId;
    if (!periodId) return;

    setExporting(true);
    try {
      const token = localStorage.getItem("medschedule.token");
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1"}/dashboard/export/schedule/${periodId}`,
        {
          headers: {
            Authorization: `Bearer ${token ?? ""}`,
          },
        }
      );
      if (!response.ok) throw new Error("Export failed");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `schedule-export-${periodId}.xlsx`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch {
      setMessage("Xuất file thất bại. Vui lòng thử lại.");
    } finally {
      setExporting(false);
    }
  }, [selectedPeriodId]);

  useEffect(() => {
    void load();
  }, [load]);

  const totalConflicts = conflictData?.totalConflicts ?? 0;
  const pendingExchanges = dashboardData?.summary.pendingScheduleExchanges ?? 0;
  const pendingLeave = dashboardData?.summary.pendingLeaveRequests ?? 0;
  const activeStaff = dashboardData?.summary.activeStaff ?? 0;
  const totalSchedules = dashboardData?.summary.totalSchedules ?? 0;
  const L01Count = dashboardData?.shiftStatistics?.L01Count ?? 0;
  const L02Count = dashboardData?.shiftStatistics?.L02Count ?? 0;

  const activePeriod = useMemo(() => {
    if (selectedPeriodId) {
      return periods.find((p) => p.id === selectedPeriodId) ?? null;
    }
    return periods.find((p) => p.status === "DRAFT" || p.status === "PUBLISHED") ?? null;
  }, [periods, selectedPeriodId]);

  const calendarAnnotations = useMemo(() => {
    const compAnnotations = compensationDays.map((cd) => ({
      date: cd.compensationDate.split("T")[0],
      label: `Nghỉ bù · ${cd.staffName}`,
      tone: "compLeave" as const,
      description: `Ngày nghỉ bù của ${cd.staffName} — không thể xếp lịch`,
    }));
    const conflictAnnotations = (conflictData?.conflicts ?? []).map((conflict) => ({
      date: conflict.workDate.split("T")[0],
      label: `Xung đột · ${conflict.staffName}`,
      tone: "warning" as const,
      description: conflict.conflictReasons.join(" • "),
    }));
    return [...compAnnotations, ...conflictAnnotations];
  }, [compensationDays, conflictData]);

  const computedCoverages = useMemo(() => {
    const map: Record<string, { required: number; assigned: number }> = {};
    for (const req of requirements) {
      const key = req.workDate.split("T")[0];
      const prev = map[key] ?? { required: 0, assigned: 0 };
      map[key] = {
        required: prev.required + req.requiredStaffCount,
        assigned: prev.assigned + req.assignedStaffCount,
      };
    }
    return map;
  }, [requirements]);

  const initialCalendarYear = useMemo(() => {
    if (activePeriod?.startDate) {
      return new Date(activePeriod.startDate).getFullYear();
    }
    return new Date().getFullYear();
  }, [activePeriod]);

  const initialCalendarMonth = useMemo(() => {
    if (activePeriod?.startDate) {
      return new Date(activePeriod.startDate).getMonth();
    }
    return new Date().getMonth();
  }, [activePeriod]);

  return (
    <DashboardShell
      activeSection="dashboard"
      description="Theo dõi KPI, cảnh báo vận hành và tác vụ quan trọng trong kỳ lịch hiện hành."
      title="Tổng quan"
    >
      {message && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
          {message}
        </div>
      )}

      {/* Alert Banner */}
      {(totalConflicts > 0 || pendingExchanges > 0 || pendingLeave > 0) && (
        <div className="flex flex-wrap gap-3 rounded-xl border border-tertiary-container bg-tertiary-container/30 p-4">
          {totalConflicts > 0 && (
            <span className="inline-flex items-center gap-2 rounded-full bg-error-container px-3 py-1 text-[12px] font-semibold text-error">
              <span className="h-2 w-2 rounded-full bg-error" />
              {totalConflicts} xung đột
            </span>
          )}
          {pendingExchanges > 0 && (
            <span className="inline-flex items-center gap-2 rounded-full bg-tertiary-fixed px-3 py-1 text-[12px] font-semibold text-tertiary">
              <span className="h-2 w-2 rounded-full bg-tertiary" />
              {pendingExchanges} đổi trực chờ duyệt
            </span>
          )}
          {pendingLeave > 0 && (
            <span className="inline-flex items-center gap-2 rounded-full bg-primary-fixed px-3 py-1 text-[12px] font-semibold text-primary">
              <span className="h-2 w-2 rounded-full bg-primary" />
              {pendingLeave} nghỉ phép chờ duyệt
            </span>
          )}
        </div>
      )}

      {/* Period Selector */}
      {periods.length > 0 && (
        <div className="flex flex-wrap items-center gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="flex items-center gap-2 text-label-sm text-on-surface-variant shrink-0">
            <span className="material-symbols-outlined text-[16px]">calendar_month</span>
            <span className="font-semibold uppercase tracking-wide">Kỳ lịch:</span>
          </div>
          <div className="relative">
            <select
              className="h-9 pl-3 pr-8 bg-surface-container-low border border-transparent rounded-lg text-label-md text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all min-w-[200px]"
              value={selectedPeriodId ?? activePeriod?.id ?? ""}
              onChange={(e) => {
                const val = Number(e.target.value);
                if (val) handlePeriodChange(val);
              }}
            >
              {periods.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.periodName} ({new Date(p.startDate).toLocaleDateString("vi-VN")} – {new Date(p.endDate).toLocaleDateString("vi-VN")})
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[18px]">expand_more</span>
          </div>
          {activePeriod && (
            <div className="flex items-center gap-1.5">
              <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold ${
                activePeriod.status === "PUBLISHED"
                  ? "bg-secondary-container text-on-secondary-container"
                  : activePeriod.status === "ARCHIVED"
                  ? "bg-surface-container-highest text-outline"
                  : "bg-primary-fixed text-primary"
              }`}>
                {activePeriod.status === "PUBLISHED" ? "Đã công bố" : activePeriod.status === "ARCHIVED" ? "Đã lưu trữ" : "Nháp"}
              </span>
            </div>
          )}
          <div className="ml-auto flex items-center gap-2">
            {selectedPeriodId && (
              <button
                onClick={() => void handleExport()}
                disabled={exporting}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-low border border-outline-variant text-label-sm font-medium text-on-surface hover:bg-surface-container transition-colors disabled:opacity-50"
              >
                <span className="material-symbols-outlined text-[16px]">{exporting ? "hourglass_empty" : "download"}</span>
                {exporting ? "Đang xuất…" : "Xuất Excel"}
              </button>
            )}
            <Link
              href="/monthly-schedule"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-on-primary text-label-sm font-medium hover:bg-primary/90 transition-colors"
            >
              <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
              Quản lý kỳ lịch
            </Link>
          </div>
        </div>
      )}

      {/* KPI Grid */}
      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[
          {
            label: "Nhân sự đang hoạt động",
            value: activeStaff,
            icon: "groups",
            accent: "bg-primary-fixed text-primary",
            helper: "Trên tổng nhân sự",
          },
          {
            label: "Tổng phân công",
            value: totalSchedules,
            icon: "event_available",
            accent: "bg-secondary-container text-secondary",
            helper: "Trong kỳ đang vận hành",
          },
          {
            label: "Xung đột phát hiện",
            value: totalConflicts,
            icon: "warning",
            accent:
              totalConflicts > 0
                ? "bg-error-container text-error"
                : "bg-surface-container-low text-outline",
            helper:
              totalConflicts > 0 ? "Cần xử lý trước publish" : "Không phát hiện",
          },
          {
            label: "Yêu cầu chờ duyệt",
            value: pendingExchanges + pendingLeave,
            icon: "pending_actions",
            accent:
              pendingExchanges + pendingLeave > 0
                ? "bg-tertiary-fixed text-tertiary"
                : "bg-surface-container-low text-outline",
            helper: "Đổi trực + nghỉ phép",
          },
        ].map((kpi) => (
          <div
            key={kpi.label}
            className="flex flex-col justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm hover:bg-surface-container-low transition-colors"
          >
            <div className="flex justify-between items-start">
              <h3 className="text-label-sm text-on-surface-variant uppercase tracking-wider">
                {kpi.label}
              </h3>
              <span
                className={`material-symbols-outlined ${kpi.accent} p-1.5 rounded-md text-[20px]`}
              >
                {kpi.icon}
              </span>
            </div>
            <div className="flex items-baseline gap-2 mt-3">
              <span className="text-display-lg text-on-surface font-bold">
                {loading ? "—" : kpi.value}
              </span>
            </div>
            <p className="mt-1 text-label-sm text-on-surface-variant">
              {kpi.helper}
            </p>
          </div>
        ))}
      </section>

      {/* Shift Stats Row */}
      <section className="grid gap-4 grid-cols-2 sm:grid-cols-4">
        {[
          { label: "Trực 24/24", count: L01Count, bg: "bg-blue-50", border: "border-blue-300", text: "text-blue-700" },
          { label: "Thông tầm", count: L02Count, bg: "bg-emerald-50", border: "border-emerald-300", text: "text-emerald-700" },
          { label: "PK dịch vụ", count: dashboardData?.shiftStatistics?.L03Count ?? 0, bg: "bg-amber-50", border: "border-amber-300", text: "text-amber-700" },
          { label: "PK chuyên gia", count: dashboardData?.shiftStatistics?.L04Count ?? 0, bg: "bg-purple-50", border: "border-purple-300", text: "text-purple-700" },
        ].map((stat) => (
          <div
            key={stat.label}
            className={`flex items-center justify-between rounded-lg border px-4 py-3 bg-surface-container-lowest ${stat.border} ${stat.text}`}
          >
            <span className="text-label-md font-medium">{stat.label}</span>
            <span className="text-headline-md font-bold">
              {loading ? "—" : stat.count}
            </span>
          </div>
        ))}
      </section>

      {/* Quick Actions Grid */}
      <section>
        <h2 className="mb-4 text-title-lg text-on-surface font-semibold">
          Thao tác nhanh
        </h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {QUICK_ACTIONS.map((action) => (
            <Link
              key={action.href}
              href={action.href}
              className={`group flex flex-col gap-3 rounded-xl border border-l-4 p-5 shadow-sm hover:shadow-md transition-shadow bg-surface-container-lowest ${action.accent}`}
            >
              <span className="material-symbols-outlined text-[24px] text-on-surface group-hover:text-primary transition-colors">
                {action.icon}
              </span>
              <div>
                <h3 className="text-title-lg font-semibold text-on-surface">
                  {action.label}
                </h3>
                <p className="mt-1 text-label-sm text-on-surface-variant leading-relaxed">
                  {action.description}
                </p>
              </div>
              <span className="material-symbols-outlined text-[18px] text-on-surface-variant group-hover:text-primary group-hover:translate-x-1 transition-all self-start">
                arrow_forward
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* Schedule Calendar Widget */}
      {activePeriod && recentSchedules.length > 0 && (
        <section>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-title-lg text-on-surface font-semibold">
              Lịch kỳ {activePeriod.periodName}
            </h2>
            <Link
              href="/monthly-schedule"
              className="inline-flex items-center gap-1 text-label-sm text-primary hover:underline"
            >
              Mở lịch tháng
              <span className="material-symbols-outlined text-[16px]">
                chevron_right
              </span>
            </Link>
          </div>
          <ScheduleCalendarWidget
            schedules={recentSchedules}
            calendarAnnotations={calendarAnnotations}
            coverages={computedCoverages}
            staffList={[]}
            specialtyList={[]}
            initialYear={initialCalendarYear}
            initialMonth={initialCalendarMonth}
            periodId={activePeriod.id}
            onRefresh={() => void load()}
            onDayClick={(date) => {
              router.push(`/monthly-schedule?date=${date.toISOString().slice(0, 10)}`);
            }}
          />
        </section>
      )}

      {/* Recent Schedules */}
      <section>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-title-lg text-on-surface font-semibold">
            Phân công gần đây
          </h2>
          <Link
            href="/monthly-schedule"
            className="inline-flex items-center gap-1 text-label-sm text-primary hover:underline"
          >
            Xem tất cả
            <span className="material-symbols-outlined text-[16px]">
              chevron_right
            </span>
          </Link>
        </div>

        {loading ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className="animate-pulse rounded-xl border border-outline-variant bg-surface-container-lowest p-5"
              >
                <div className="h-4 w-24 rounded bg-surface-container-high" />
                <div className="mt-3 h-3 w-32 rounded bg-surface-container" />
              </div>
            ))}
          </div>
        ) : recentSchedules.length === 0 ? (
          <EmptyState
            icon="calendar_today"
            title="Chưa có phân công nào trong kỳ đang vận hành"
            description="Bắt đầu lập lịch để thấy phân công hiển thị tại đây."
            action={
              <Link
                href="/monthly-schedule"
                className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90"
              >
                <span className="material-symbols-outlined text-[18px]">auto_mode</span>
                Bắt đầu lập lịch
              </Link>
            }
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {recentSchedules.map((schedule) => {
              const colorMap: Record<string, { bg: string; border: string; text: string; badge: string }> = {
                L01: { bg: "bg-red-50", border: "border-red-300", text: "text-red-700", badge: "bg-red-100 text-red-700" },
                L02: { bg: "bg-blue-50", border: "border-blue-300", text: "text-blue-700", badge: "bg-blue-100 text-blue-700" },
                L03: { bg: "bg-orange-50", border: "border-orange-300", text: "text-orange-700", badge: "bg-orange-100 text-orange-700" },
                L04: { bg: "bg-purple-50", border: "border-purple-300", text: "text-purple-700", badge: "bg-purple-100 text-purple-700" },
              };
              const color = colorMap[schedule.shiftType.id] ?? colorMap.L01;

              return (
                <article
                  key={schedule.id}
                  className={`flex flex-col gap-3 rounded-xl border border-l-4 p-4 shadow-sm bg-surface-container-lowest ${color.bg} ${color.border}`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-label-sm font-semibold text-on-surface">
                      {schedule.staff.fullName}
                    </span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${color.badge}`}
                    >
                      {schedule.shiftType.id}
                    </span>
                  </div>
                  <div className="text-label-sm text-on-surface-variant">
                    <span className="material-symbols-outlined text-[14px] align-middle mr-1">
                      calendar_today
                    </span>
                    {formatDate(schedule.workDate)}
                  </div>
                  <div className="text-label-sm text-on-surface-variant">
                    <span className="material-symbols-outlined text-[14px] align-middle mr-1">
                      {schedule.shiftType.id === "L01" ? "emergency" : "schedule"}
                    </span>
                    {schedule.shiftType.name}
                  </div>
                  {schedule.hasConflict && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-error-container px-2 py-0.5 text-[10px] font-semibold text-error">
                      <span className="h-1.5 w-1.5 rounded-full bg-error" />
                      Có xung đột
                    </span>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </section>
    </DashboardShell>
  );
}
