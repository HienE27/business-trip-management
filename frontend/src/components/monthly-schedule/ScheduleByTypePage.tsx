"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { Skeleton } from "@/components/ui/Skeleton";
import { WorkflowStepper } from "@/components/monthly-schedule/WorkflowStepper";
import { ScheduleCalendarSection } from "@/components/monthly-schedule/ScheduleCalendarSection";
import { QuickAddModal } from "@/components/monthly-schedule/QuickAddModal";
import { ShiftDetailModal } from "@/components/monthly-schedule/ShiftDetailModal";
import { WorkloadSummary } from "@/components/monthly-schedule/WorkloadSummary";
import { useRole, canManage } from "@/hooks/useRole";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { getInitialCalendar } from "@/components/monthly-schedule/utils";
import type {
  CompensationDay,
  ConflictCheckResponse,
  Schedule,
  SchedulePeriod,
  Specialty,
  Staff,
} from "@/types/api";
import type { ScheduleTab, ViewMode } from "@/components/monthly-schedule/types";
import type { MonthlyPanel, WorkflowStepId } from "@/components/monthly-schedule/types";

export type ScheduleTypeConfig = {
  /** Sidebar section key, drives the active highlight. */
  activeSection: "duty-24" | "all-day" | "service-clinic" | "expert-clinic";
  /** Shift type id used for filtering schedules and pre-filling the add modal. */
  shiftTypeId: "L01" | "L02" | "L03" | "L04";
  /** Hero title shown in the dashboard shell. */
  title: string;
  /** Long description shown under the title. */
  description: string;
  /** Short label printed inside the empty-state CTA. */
  emptyMessage: string;
  /** Material Symbol used as the empty-state glyph. */
  emptyIcon: string;
  /** Material Symbol used on the primary CTA. */
  ctaIcon: string;
  /** Label printed on the primary "add" CTA. */
  ctaLabel: string;
  /** Heading used in the first KPI card. */
  totalShiftLabel: string;
  /** Accent color for the total-shift KPI card. */
  totalShiftAccent: string;
  /** Accent color for the participating-staff KPI card. */
  staffAccent: string;
  /** Message shown when the schedule fetch fails. */
  fetchErrorMessage: string;
  /**
   * Description printed on compensation-day calendar annotations.
   * Only relevant when compensation days are fetched.
   */
  compDescription: string;
  /**
   * When true, the page loads schedules from the dedicated
   * /schedules/expert-clinic endpoint and lets the user narrow
   * them down by specialty. Compensation-day annotations and the
   * detail modal are skipped because the endpoint already filters
   * by shift type server-side.
   */
  expertClinicMode?: boolean;
};

export type ScheduleByTypePageProps = {
  config: ScheduleTypeConfig;
};

export function ScheduleByTypePage({ config }: ScheduleByTypePageProps) {
  const role = useRole();
  const isManager = canManage(role);
  const isExpertMode = config.expertClinicMode === true;

  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [compensationDays, setCompensationDays] = useState<CompensationDay[]>([]);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [selectedSpecialtyId, setSelectedSpecialtyId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [addModalDate, setAddModalDate] = useState<Date | null>(null);
  const [detailScheduleId, setDetailScheduleId] = useState<number | null>(null);
  const [selectedTab, setSelectedTab] = useState<ScheduleTab>(
    isExpertMode ? "ALL" : (config.shiftTypeId as ScheduleTab)
  );
  const [viewMode, setViewMode] = useState<ViewMode>("calendar");
  const [selectedPanel, setSelectedPanel] = useState<MonthlyPanel>("summary");
  const [showStats, setShowStats] = useState(false);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [notifying, setNotifying] = useState(false);
  const [notified, setNotified] = useState(false);

  const loadBaseData = useCallback(async () => {
    try {
      setLoading(true);
      const requests: [Promise<SchedulePeriod[]>, Promise<Staff[]>, Promise<Specialty[]> | null] = [
        api.get<SchedulePeriod[]>("/periods"),
        api.get<Staff[]>("/staff/active"),
        isExpertMode ? api.get<Specialty[]>("/specialties/active") : null,
      ];
      const [periodData, staffData, specialtyData] = await Promise.all(requests);
      const pList = periodData ?? [];
      setPeriods(pList);
      setActiveStaff(staffData ?? []);
      setSpecialties(specialtyData ?? []);
      const draft = pList.find((p) => p.status === "DRAFT") ?? pList[0] ?? null;
      setSelectedPeriodId(draft?.id ?? null);
    } catch {
      setMessage("Không thể tải dữ liệu. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  }, [isExpertMode]);

  useEffect(() => {
    void loadBaseData();
  }, [loadBaseData]);

  /**
   * Optimistic insert helpers. We add the temp schedule straight
   * into the local `schedules` array so the calendar updates
   * immediately. On success we swap the temp id for the real
   * one returned by the backend; on failure we remove it.
   * The temp id is always negative so it cannot collide with a
   * real schedule id from the server.
   */
  const handleOptimisticAdd = useCallback((tempSchedule: Schedule) => {
    setSchedules((prev) => [tempSchedule, ...prev]);
  }, []);

  const handleCommit = useCallback((tempId: number, realSchedule: Schedule) => {
    setSchedules((prev) =>
      prev.map((s) => (s.id === tempId ? realSchedule : s))
    );
  }, []);

  const handleRollback = useCallback((tempId: number) => {
    setSchedules((prev) => prev.filter((s) => s.id !== tempId));
  }, []);

  const handleCheckConflicts = useCallback(async () => {
    if (!selectedPeriodId) return;
    setCheckingConflicts(true);
    setMessage(null);
    try {
      const result = await api.get<ConflictCheckResponse>(
        `/schedules/conflicts/check/${selectedPeriodId}`
      );
      setConflictData(result ?? null);
      setMessage(
        result?.hasConflicts
          ? `Phát hiện ${result.totalConflicts} xung đột cần xử lý.`
          : "Không phát hiện xung đột trong kỳ lịch."
      );
    } catch {
      setMessage("Không thể kiểm tra xung đột.");
    } finally {
      setCheckingConflicts(false);
    }
  }, [selectedPeriodId]);

  const handlePublish = useCallback(async () => {
    if (!selectedPeriodId) return;
    setPublishing(true);
    setMessage(null);
    try {
      await api.publishPeriod(selectedPeriodId);
      setPeriods((prev) =>
        prev.map((p) =>
          p.id === selectedPeriodId ? { ...p, status: "PUBLISHED" as const } : p
        )
      );
      setMessage("Kỳ lịch đã được công bố thành công.");
    } catch {
      setMessage("Không thể công bố kỳ lịch.");
    } finally {
      setPublishing(false);
    }
  }, [selectedPeriodId]);

  const handleSendNotifications = useCallback(async () => {
    if (!selectedPeriodId || activeStaff.length === 0) return;
    setNotifying(true);
    setMessage(null);
    try {
      const periodName = periods.find((p) => p.id === selectedPeriodId)?.periodName ?? "";
      await Promise.all(
        activeStaff.map((staff) =>
          api.post("/notifications", {
            staffId: staff.id,
            title: `Thông báo lịch trực – ${periodName}`,
            message: `Lịch trực của bạn đã được cập nhật. Vui lòng kiểm tra chi tiết trong hệ thống.`,
          })
        )
      );
      setNotified(true);
      setMessage(`Đã gửi thông báo đến ${activeStaff.length} nhân sự.`);
    } catch {
      setMessage("Không thể gửi thông báo.");
    } finally {
      setNotifying(false);
    }
  }, [selectedPeriodId, activeStaff, periods]);

  const handleWorkflowStep = useCallback((stepId: WorkflowStepId) => {
    if (stepId === "conflicts") {
      setSelectedPanel("conflicts");
      void handleCheckConflicts();
      return;
    }
    if (stepId === "notify") {
      void handleSendNotifications();
      return;
    }
    setSelectedPanel("summary");
  }, [handleCheckConflicts, handleSendNotifications]);

  const selectedPeriod = useMemo(
    () => periods.find((p) => p.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId]
  );

  const initialCalendar = useMemo(() => getInitialCalendar(selectedPeriod), [selectedPeriod]);

  const handleRefresh = useCallback(() => {
    if (!selectedPeriodId) return;
    setLoading(true);
    setMessage(null);

    if (isExpertMode) {
      api.get<Schedule[]>("/schedules/expert-clinic", {
        periodId: selectedPeriodId,
        ...(selectedSpecialtyId ? { specialtyId: selectedSpecialtyId } : {}),
      })
        .then((data) => setSchedules(data ?? []))
        .catch(() => setMessage(config.fetchErrorMessage))
        .finally(() => setLoading(false));
      return;
    }

    Promise.all([
      api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
      api.get<CompensationDay[]>(`/schedules/compensation-days/${selectedPeriodId}`),
    ])
      .then(([scheduleData, compData]) => {
        setSchedules(
          (scheduleData ?? []).filter((s) => s.shiftType.id === config.shiftTypeId)
        );
        setCompensationDays(compData ?? []);
      })
      .catch(() => setMessage(config.fetchErrorMessage))
      .finally(() => setLoading(false));
  }, [
    selectedPeriodId,
    selectedSpecialtyId,
    isExpertMode,
    config.shiftTypeId,
    config.fetchErrorMessage,
  ]);

  useEffect(() => {
    handleRefresh();
  }, [handleRefresh]);

  // Reset workflow state when period changes
  useEffect(() => {
    setConflictData(null);
    setNotified(false);
  }, [selectedPeriodId]);

  const selectedSchedule = useMemo(
    () => schedules.find((s) => s.id === detailScheduleId) ?? null,
    [schedules, detailScheduleId]
  );

  const calendarAnnotations = useMemo(() => {
    if (isExpertMode) return [];
    const compByDate = new Map<string, CompensationDay[]>();
    for (const cd of compensationDays) {
      const key = cd.compensationDate.split("T")[0];
      const list = compByDate.get(key) ?? [];
      list.push(cd);
      compByDate.set(key, list);
    }
    return Array.from(compByDate.entries()).map(([date, days]) => {
      const staffNames = days.map((d) => d.staffName);
      const label =
        staffNames.length === 1
          ? `Nghỉ bù · ${staffNames[0]}`
          : `Nghỉ bù · ${staffNames[0]}${staffNames.length > 1 ? ` (+${staffNames.length - 1})` : ""}`;
      return {
        date,
        label,
        tone: "compLeave" as const,
        description: config.compDescription,
      };
    });
  }, [compensationDays, isExpertMode, config.compDescription]);

  if (loading && periods.length === 0) {
    return (
      <DashboardShell
        activeSection={config.activeSection}
        title={config.title}
        description={config.description}
      >
        <div className="space-y-4">
          <Skeleton className="h-32 rounded-xl" />
          <Skeleton className="h-96 rounded-xl" />
        </div>
      </DashboardShell>
    );
  }

  const totalKpiLabel = config.totalShiftLabel;
  const totalKpiAccent = config.totalShiftAccent;
  const totalKpiIcon = config.emptyIcon;
  const secondKpi = isExpertMode
    ? {
        label: "Chuyên khoa",
        value: specialties.length,
        icon: "local_hospital",
        accent: "bg-primary/10 text-primary",
      }
    : {
        label: "Ngày nghỉ bù",
        value: compensationDays.length,
        icon: "bedtime",
        accent: "bg-surface-container-high text-on-surface",
      };

  return (
    <DashboardShell
      activeSection={config.activeSection}
      title={config.title}
      description={config.description}
    >
      {message && (
        <div
          className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error"
          role="alert"
        >
          {message}
        </div>
      )}

      {/* Header row: period controls (left) + workflow stepper (right) */}
      <div className="flex flex-col xl:flex-row gap-3">
        <div className="flex-1 min-w-0">
          <section className="flex flex-wrap items-end gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
            <div className="min-w-[200px]">
              <label
                htmlFor={`${config.activeSection}-period-select`}
                className="mb-1.5 block text-label-sm text-on-surface-variant"
              >
                Kỳ lịch
              </label>
              <div className="relative">
                <select
                  id={`${config.activeSection}-period-select`}
                  className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
                  value={selectedPeriodId ?? ""}
                  onChange={(e) => {
                    const next = Number(e.target.value);
                    setSelectedPeriodId(next);
                    if (isExpertMode) setSelectedSpecialtyId(null);
                  }}
                >
                  <option value="">Chọn kỳ lịch</option>
                  {periods.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.periodName} ({p.status})
                    </option>
                  ))}
                </select>
                <span
                  aria-hidden="true"
                  className="material-symbols-outlined pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[18px]"
                >
                  expand_more
                </span>
              </div>
            </div>
            {isExpertMode && (
              <div className="min-w-[200px]">
                <label
                  htmlFor={`${config.activeSection}-specialty-filter`}
                  className="mb-1.5 block text-label-sm text-on-surface-variant"
                >
                  Chuyên khoa
                </label>
                <div className="relative">
                  <select
                    id={`${config.activeSection}-specialty-filter`}
                    className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest px-3 pr-10 text-label-md text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
                    value={selectedSpecialtyId ?? ""}
                    onChange={(e) =>
                      setSelectedSpecialtyId(e.target.value ? Number(e.target.value) : null)
                    }
                  >
                    <option value="">Tất cả chuyên khoa</option>
                    {specialties.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                  <span
                    aria-hidden="true"
                    className="material-symbols-outlined pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-outline text-[18px]"
                  >
                    expand_more
                  </span>
                </div>
              </div>
            )}
            {isManager && (
              <button
                type="button"
                onClick={() => setAddModalDate(new Date())}
                className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-label-md font-semibold text-on-primary hover:bg-primary/90 transition-colors"
              >
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                  {config.ctaIcon}
                </span>
                {config.ctaLabel}
              </button>
            )}
          </section>
        </div>

        {isManager && selectedPeriodId && (
          <div className="w-full xl:w-72 shrink-0">
            <WorkflowStepper
              selectedPanel={selectedPanel}
              selectedPeriod={selectedPeriod}
              schedules={schedules}
              conflictData={conflictData}
              checkingConflicts={checkingConflicts}
              publishing={publishing}
              notifying={notifying}
              notified={notified}
              onStepSelect={handleWorkflowStep}
            />
          </div>
        )}
      </div>

      <section className="grid gap-3 grid-cols-2 sm:grid-cols-4">
        {[
          {
            label: totalKpiLabel,
            value: schedules.length,
            icon: totalKpiIcon,
            accent: totalKpiAccent,
          },
          secondKpi,
          {
            label: "Nhân sự tham gia",
            value: new Set(schedules.map((s) => s.staff.id)).size,
            icon: "groups",
            accent: config.staffAccent,
          },
          {
            label: "Xung đột",
            value: schedules.filter((s) => s.hasConflict === true).length,
            icon: "warning",
            accent: "bg-error-container text-on-error-container",
          },
        ].map((kpi) => (
          <div
            key={kpi.label}
            className={`rounded-xl border border-outline-variant p-4 ${kpi.accent}`}
          >
            <p className="text-label-sm opacity-80 mb-1">{kpi.label}</p>
            <p className="text-headline-md font-bold">{kpi.value}</p>
          </div>
        ))}
      </section>

      {/* Tab bar: Lịch / Thống kê */}
      <div className="flex items-center gap-1 p-1 bg-surface-container-low rounded-xl w-fit">
        <button
          type="button"
          onClick={() => setShowStats(false)}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-label-md font-semibold transition-all ${
            !showStats
              ? "bg-primary text-on-primary shadow-sm"
              : "text-on-surface-variant hover:bg-surface-container-high"
          }`}
        >
          <span className="material-symbols-outlined text-[16px]">calendar_month</span>
          Lịch
        </button>
        <button
          type="button"
          onClick={() => setShowStats(true)}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-label-md font-semibold transition-all ${
            showStats
              ? "bg-primary text-on-primary shadow-sm"
              : "text-on-surface-variant hover:bg-surface-container-high"
          }`}
        >
          <span className="material-symbols-outlined text-[16px]">bar_chart</span>
          Thống kê
        </button>
      </div>

      {showStats ? (
        <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <span className="material-symbols-outlined text-[22px] text-primary">bar_chart</span>
            <h3 className="text-headline-md font-semibold text-on-surface">
              Thống kê phân bổ — {config.title}
            </h3>
          </div>
          {selectedPeriodId ? (
            <WorkloadSummary
              periodId={selectedPeriodId}
              shiftTypeId={config.shiftTypeId}
              groupBySpecialty={isExpertMode}
            />
          ) : (
            <p className="text-sm text-on-surface-variant">Vui lòng chọn kỳ lịch.</p>
          )}
        </section>
      ) : !selectedPeriod ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4">
          <span aria-hidden="true" className="material-symbols-outlined text-5xl text-outline">
            {config.emptyIcon}
          </span>
          <p className="text-on-surface-variant">{config.emptyMessage}</p>
        </div>
      ) : loading ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : (
        <ScheduleCalendarSection
          schedules={schedules}
          calendarAnnotations={calendarAnnotations}
          coverages={{}}
          activeStaff={activeStaff}
          specialties={isExpertMode ? specialties : []}
          staffFilterId={null}
          specialtyFilterId={isExpertMode ? selectedSpecialtyId : null}
          selectedPeriodId={selectedPeriodId}
          initialYear={initialCalendar.year}
          initialMonth={initialCalendar.month}
          viewMode={viewMode}
          selectedTab={isExpertMode ? selectedTab : (config.shiftTypeId satisfies ScheduleTab)}
          compensationDays={compensationDays}
          onRefresh={handleRefresh}
          onFocusDate={() => undefined}
          onAddDate={(date) => setAddModalDate(date)}
          onStaffFilterChange={() => undefined}
          onSpecialtyFilterChange={
            isExpertMode ? setSelectedSpecialtyId : () => undefined
          }
          onViewDetail={
            isExpertMode
              ? () => undefined
              : (schedule) => setDetailScheduleId(schedule.id)
          }
          onViewModeChange={setViewMode}
          onFilterTypeChange={
            isExpertMode
              ? (filter: string) => setSelectedTab(filter as ScheduleTab)
              : () => undefined
          }
          hideFilters={!isExpertMode}
        />
      )}

      {selectedPeriodId && (
        <QuickAddModal
          date={addModalDate}
          periodId={selectedPeriodId}
          defaultShiftTypeId={config.shiftTypeId}
          staffList={activeStaff}
          compensationDays={compensationDays}
          onOptimisticAdd={handleOptimisticAdd}
          onCommit={handleCommit}
          onRollback={handleRollback}
          onSuccess={handleRefresh}
          onClose={() => setAddModalDate(null)}
        />
      )}

      {!isExpertMode && (
        <ShiftDetailModal
          scheduleId={detailScheduleId}
          schedule={selectedSchedule}
          loading={false}
          canEdit={isManager}
          onClose={() => setDetailScheduleId(null)}
          onSave={handleRefresh}
        />
      )}
    </DashboardShell>
  );
}
