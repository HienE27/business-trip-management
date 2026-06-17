"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import { EmptyState } from "@/components/ui/EmptyState";
import { SkeletonDashboardKPIGrid } from "@/components/ui/Skeleton";
import { KPICard } from "@/components/ui/KPICard";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/date";
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

export default function DashboardPage() {
  const router = useRouter();
  const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [allSchedules, setAllSchedules] = useState<Schedule[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [compensationDays, setCompensationDays] = useState<import("@/types/api").CompensationDay[]>([]);
  const [requirements, setRequirements] = useState<import("@/types/api").ShiftRequirement[]>([]);
  const [staffList, setStaffList] = useState<{ id: number; fullName: string }[]>([]);
  const [selectedStaffId, setSelectedStaffId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);
  const ignoreRef = useRef(false);

  const load = useCallback(async (periodId?: number) => {
    setLoading(true);
    setMessage(null);

    try {
      const [dashboardRes, periodsRes] = await Promise.allSettled([
        api.get<DashboardData>("/dashboard"),
        api.get<SchedulePeriod[]>("/periods"),
      ]);

      const dashboard =
        dashboardRes.status === "fulfilled" ? dashboardRes.value : null;
      const periodList =
        periodsRes.status === "fulfilled" ? periodsRes.value ?? [] : [];

      if (dashboardRes.status === "rejected") {
        setMessage((prev) => prev || "Không thể tải dữ liệu dashboard.");
      }
      if (periodsRes.status === "rejected") {
        setMessage((prev) => prev || "Không thể tải danh sách kỳ lịch.");
      }

      setDashboardData(dashboard);
      setPeriods(periodList);

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

        const [scheduleRes, conflictRes, compDaysRes, reqRes, staffRes] = await Promise.allSettled([
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
          api.get<import("@/types/api").Staff[]>("/staff/active"),
        ]);

        if (scheduleRes.status === "fulfilled") {
          const all = scheduleRes.value ?? [];
          setAllSchedules(all);
        } else {
          setMessage((prev) => prev || "Không thể tải danh sách phân công.");
        }
        if (conflictRes.status === "fulfilled") {
          setConflictData(conflictRes.value);
        } else {
          setMessage((prev) => prev || "Không thể tải dữ liệu xung đột.");
        }
        if (compDaysRes.status === "fulfilled") {
          setCompensationDays(compDaysRes.value ?? []);
        }
        if (reqRes.status === "fulfilled") {
          setRequirements(reqRes.value ?? []);
        }
        if (staffRes.status === "fulfilled") {
          setStaffList(
            (staffRes.value ?? []).map((s) => ({
              id: s.id,
              fullName: s.fullName,
            }))
          );
        } else {
          setMessage((prev) => prev || "Không thể tải danh sách nhân sự.");
        }
      }
    } catch {
      setMessage("Đã xảy ra lỗi khi tải dữ liệu dashboard.");
    } finally {
      setLoading(false);
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

  const handleExportPdf = useCallback(async () => {
    const periodId = selectedPeriodId;
    if (!periodId) return;

    setExporting(true);
    try {
      const token = localStorage.getItem("medschedule.token");
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1"}/dashboard/export/schedule/${periodId}/pdf`,
        {
          headers: {
            Authorization: `Bearer ${token ?? ""}`,
          },
        }
      );
      if (!response.ok) throw new Error("Export PDF failed");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `lich-cong-tac-${periodId}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch {
      setMessage("Xuất PDF thất bại. Vui lòng thử lại.");
    } finally {
      setExporting(false);
    }
  }, [selectedPeriodId]);

  useEffect(() => {
    void load();
  }, [load]);

  // Real-time conflict polling every 60s
  useEffect(() => {
    if (!selectedPeriodId) return;
    ignoreRef.current = false;
    const interval = setInterval(async () => {
      if (ignoreRef.current) return;
      try {
        const data = await api.get<ConflictCheckResponse>(
          `/schedules/conflicts/check/${selectedPeriodId}`
        );
        if (!ignoreRef.current) setConflictData(data);
      } catch {
        // polling errors are silently skipped to avoid spamming the UI
      }
    }, 60000);
    return () => {
      ignoreRef.current = true;
      clearInterval(interval);
    };
  }, [selectedPeriodId]);

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

      {/* Period Selector */}
      {periods.length === 0 && !loading ? (
        <EmptyState
          icon="calendar_month"
          title="Chưa có kỳ lịch nào"
          description="Hãy tạo kỳ lịch mới để bắt đầu phân công trực."
        />
      ) : periods.length > 0 ? (
        <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 shadow-sm">
          <div className="flex items-center gap-1.5 text-[11px] text-on-surface-variant shrink-0">
            <span className="material-symbols-outlined text-[14px]">calendar_month</span>
            <span className="font-semibold">Kỳ lịch:</span>
          </div>
          <div className="relative">
            <select
              className="h-7 pl-2.5 pr-7 bg-surface-container-low border border-transparent rounded-md text-[12px] text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all min-w-[180px]"
              value={selectedPeriodId ?? activePeriod?.id ?? ""}
              onChange={(e) => {
                const val = Number(e.target.value);
                if (val) handlePeriodChange(val);
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
          {activePeriod && (
            <span className={`inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-label-sm font-semibold ${
              activePeriod.status === "PUBLISHED"
                ? "bg-secondary-container text-on-secondary-container"
                : activePeriod.status === "ARCHIVED"
                ? "bg-surface-container-highest text-outline"
                : "bg-primary-fixed text-primary"
            }`}>
              {activePeriod.status === "PUBLISHED" ? "Đã công bố" : activePeriod.status === "ARCHIVED" ? "Đã lưu trữ" : "Nháp"}
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

      {/* KPI Grid */}
      {loading ? (
        <SkeletonDashboardKPIGrid />
      ) : (
        <section className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <KPICard
            label="Nhân sự đang hoạt động"
            value={activeStaff}
            icon="groups"
            tone="info"
            helper="Trên tổng nhân sự"
          />
          <KPICard
            label="Tổng phân công"
            value={totalSchedules}
            icon="event_available"
            tone="success"
            helper="Trong kỳ đang vận hành"
          />
          <KPICard
            label="Xung đột phát hiện"
            value={totalConflicts}
            icon="warning"
            tone={totalConflicts > 0 ? "error" : "neutral"}
            helper={totalConflicts > 0 ? "Cần xử lý trước publish" : "Không phát hiện"}
          />
          <KPICard
            label="Yêu cầu chờ duyệt"
            value={pendingExchanges + pendingLeave}
            icon="pending_actions"
            tone={pendingExchanges + pendingLeave > 0 ? "warning" : "neutral"}
            helper="Đổi trực + nghỉ phép"
          />
        </section>
      )}

      {/* Shift Stats Row */}
      {!loading && (
        <section className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <KPICard
            label="Trực 24/24"
            value={L01Count}
            icon="emergency"
            tone="info"
          />
          <KPICard
            label="Thông tầm"
            value={L02Count}
            icon="schedule"
            tone="success"
          />
          <KPICard
            label="PK dịch vụ"
            value={dashboardData?.shiftStatistics?.L03Count ?? 0}
            icon="medical_services"
            tone="warning"
          />
          <KPICard
            label="PK chuyên gia"
            value={dashboardData?.shiftStatistics?.L04Count ?? 0}
            icon="stethoscope"
            tone="neutral"
          />
        </section>
      )}

      {/* Quick Actions Grid */}
      <section>
        <h2 className="mb-3 text-title-lg text-on-surface">
          Thao tác nhanh
        </h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {QUICK_ACTIONS.map((action) => (
            <Link
              key={action.href}
              href={action.href}
              className={`group flex flex-col gap-2 rounded-lg border border-l-4 border-outline-variant bg-surface-container-lowest p-3 shadow-sm hover:-translate-y-0.5 hover:shadow-md transition-all ${action.accent}`}
            >
              <span className="material-symbols-outlined text-[18px] text-on-surface group-hover:text-primary transition-colors">
                {action.icon}
              </span>
              <div>
                <h3 className="text-title-md font-semibold text-on-surface leading-tight">
                  {action.label}
                </h3>
                <p className="mt-0.5 text-label-sm text-on-surface-variant leading-snug">
                  {action.description}
                </p>
              </div>
              <span className="material-symbols-outlined text-[16px] text-on-surface-variant group-hover:text-primary group-hover:translate-x-0.5 transition-all self-start">
                arrow_forward
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* Schedule Calendar Widget */}
      {activePeriod && (
        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-title-lg text-on-surface">
              Lịch kỳ {activePeriod.periodName}
            </h2>
            <div className="flex items-center gap-2">
              {/* Staff filter */}
              {staffList.length > 0 && (
                <div className="relative">
                  <select
                    className="h-7 pl-3 pr-8 bg-surface-container-low border border-outline-variant rounded-md text-label-sm text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all min-w-[160px]"
                    value={selectedStaffId ?? ""}
                    onChange={(e) => {
                      const val = e.target.value ? Number(e.target.value) : null;
                      setSelectedStaffId(val);
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
                className="inline-flex items-center gap-1 text-label-sm text-primary hover:underline"
              >
                Mở lịch tháng
                <span className="material-symbols-outlined text-[16px]">
                  chevron_right
                </span>
              </Link>
            </div>
          </div>
          <div className="rounded-lg border border-outline-variant bg-surface-container-lowest overflow-hidden shadow-sm">
            <ScheduleCalendarWidget
              schedules={allSchedules}
              calendarAnnotations={calendarAnnotations}
              coverages={computedCoverages}
              staffList={staffList}
              staffFilter={selectedStaffId}
              specialtyList={[]}
              initialYear={initialCalendarYear}
              initialMonth={initialCalendarMonth}
              periodId={activePeriod.id}
              isReadOnly={true}
              onRefresh={() => void load()}
              onDayClick={(date) => {
                router.push(`/monthly-schedule?date=${date.toISOString().slice(0, 10)}`);
              }}
              onStaffFilterChange={(staffId) => setSelectedStaffId(staffId)}
            />
          </div>
        </section>
      )}

    </DashboardShell>
  );
}
