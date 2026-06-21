"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Skeleton } from "@/components/ui/Skeleton";
import { WorkflowStepper } from "@/components/monthly-schedule/WorkflowStepper";
import { ScheduleCalendarSection } from "@/components/monthly-schedule/ScheduleCalendarSection";
import { QuickAddModal } from "@/components/monthly-schedule/QuickAddModal";
import { ShiftDetailModal } from "@/components/monthly-schedule/ShiftDetailModal";
import { BulkScheduleModal } from "@/components/monthly-schedule/BulkScheduleModal";
import { BulkDatePickerModal } from "@/components/monthly-schedule/BulkDatePickerModal";
import { WorkloadSummary } from "@/components/monthly-schedule/WorkloadSummary";
import { ConflictSection } from "@/components/monthly-schedule/ConflictSection";
import { useRole, canManage } from "@/hooks/useRole";
import { useToast } from "@/components/ui";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { getInitialCalendar, downloadBlob } from "@/components/monthly-schedule/utils";
import type {
  CompensationDay,
  ConflictCheckResponse,
  ConflictDetail,
  PublishDryRunResponse,
  Schedule,
  SchedulePeriod,
  Specialty,
  Staff,
} from "@/types/api";
import type { MonthlyPanel, ScheduleTab, ViewMode, WorkflowStepId } from "@/components/monthly-schedule/types";

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
  const { success: toastSuccess, error: toastError } = useToast();

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
  const [viewMode, setViewMode] = useState<ViewMode>("matrix");
  const [selectedPanel, setSelectedPanel] = useState<MonthlyPanel>("summary");
  const [showStats, setShowStats] = useState(false);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [selectedConflict, setSelectedConflict] = useState<ConflictDetail | null>(null);
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [notifying, setNotifying] = useState(false);
  const [notified, setNotified] = useState(false);
  const [dryRunData, setDryRunData] = useState<PublishDryRunResponse | null>(null);

  // Bulk schedule state
  const [bulkPickerOpen, setBulkPickerOpen] = useState(false);
  const [bulkModalOpen, setBulkModalOpen] = useState(false);
  const [bulkSelectedDates, setBulkSelectedDates] = useState<string[]>([]);
  const [bulkSubmitting, setBulkSubmitting] = useState(false);

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

  const handleDryRunPublish = useCallback(async () => {
    if (!selectedPeriodId) return;
    setCheckingConflicts(true);
    setMessage(null);
    try {
      const result = await api.dryRunPublish(selectedPeriodId);
      setDryRunData(result);
      if (result.canPublish) {
        setMessage("Kỳ lịch sẵn sàng công bố — không có xung đột.");
        toastSuccess("Kỳ lịch sẵn sàng công bố — không có xung đột.");
      } else {
        const parts: string[] = [];
        if (result.hasConflicts) parts.push(`${result.conflictCount} xung đột`);
        if (result.hasCoverageGaps) parts.push(`${result.coverageGaps.length} khoảng trống phủ`);
        setMessage(`Kỳ lịch chưa thể công bố: ${parts.join(", ")}.`);
      }
    } catch {
      setMessage("Không thể kiểm tra khả năng công bố.");
      toastError("Không thể kiểm tra khả năng công bố kỳ lịch.");
    } finally {
      setCheckingConflicts(false);
    }
  }, [selectedPeriodId, toastSuccess, toastError]);

  const handleExport = useCallback(async () => {
    if (!selectedPeriodId) return;
    setExporting(true);
    setMessage(null);
    try {
      const blob = await api.exportScheduleExcel(selectedPeriodId);
      const periodName = periods.find((p) => p.id === selectedPeriodId)?.periodName ?? String(selectedPeriodId);
      downloadBlob(blob, `lich-cong-tac-${periodName}.xlsx`);
      setMessage("Đã xuất file Excel kỳ lịch.");
    } catch (error) {
      setMessage(getErrorMessage(error, "Không thể xuất file. Vui lòng thử lại."));
    } finally {
      setExporting(false);
    }
  }, [periods, selectedPeriodId]);

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

  const handlePublish = useCallback(async () => {
    if (!selectedPeriodId) return;
    setPublishing(true);
    setMessage(null);
    try {
      const published = await api.publishPeriod(selectedPeriodId);
      setSelectedPeriodId(selectedPeriodId); // trigger re-fetch to get updated status
      setMessage("Kỳ lịch đã được công bố thành công.");
      toastSuccess("Kỳ lịch đã được công bố thành công!");
      void loadBaseData();
    } catch (err) {
      const msg = getErrorMessage(err, "Không thể công bố kỳ lịch.");
      setMessage(msg);
      toastError(msg);
    } finally {
      setPublishing(false);
    }
  }, [selectedPeriodId, loadBaseData, toastSuccess, toastError]);

  const handleWorkflowStep = useCallback((stepId: WorkflowStepId) => {
    if (stepId === "conflicts") {
      setSelectedPanel("conflicts");
      void handleDryRunPublish();
      return;
    }
    if (stepId === "export") {
      void handleExport();
      return;
    }
    if (stepId === "notify") {
      void handleSendNotifications();
      return;
    }
    if (stepId === "publish") {
      void handlePublish();
      return;
    }
    setSelectedPanel("summary");
  }, [handleDryRunPublish, handleExport, handleSendNotifications, handlePublish]);

  // Bulk schedule handlers
  const handleBulkDatesSelected = useCallback((dates: string[]) => {
    setBulkSelectedDates(dates);
    setBulkPickerOpen(false);
    setBulkModalOpen(true);
  }, []);

  const handleBulkSuccess = useCallback(() => {
    void loadBaseData();
    // Auto re-check conflicts after bulk submit
    void handleDryRunPublish();
  }, [loadBaseData, handleDryRunPublish]);

  const selectedPeriod = useMemo(
    () => periods.find((p) => p.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId]
  );

  const initialCalendar = useMemo(() => getInitialCalendar(selectedPeriod), [selectedPeriod]);

  const existingSchedules = useMemo(
    () =>
      schedules.map((s) => ({
        workDate: s.workDate,
        staffId: s.staff.id,
      })),
    [schedules]
  );

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
    setDryRunData(null);
    setNotified(false);
    setBulkModalOpen(false);
    setBulkSelectedDates([]);
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
      <div className="space-y-4">
        <Skeleton className="h-32 rounded-xl" />
        <Skeleton className="h-96 rounded-xl" />
      </div>
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

  const isDraft = selectedPeriod?.status === "DRAFT";

  return (
    <>
      {message && (
        <div
          className="rounded-lg border px-4 py-3 text-sm"
          role="alert"
          style={
            dryRunData?.canPublish
              ? { borderColor: "var(--color-secondary)", backgroundColor: "var(--color-secondary-container)", color: "var(--color-on-secondary-container)" }
              : dryRunData?.hasConflicts || conflictData?.hasConflicts
              ? { borderColor: "var(--color-error)", backgroundColor: "var(--color-error-container)", color: "var(--color-on-error-container)" }
              : { borderColor: "var(--color-outline)", backgroundColor: "var(--color-surface-container-low)", color: "var(--color-on-surface)" }
          }
        >
          {dryRunData?.canPublish ? (
            <span className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px]" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
              {message}
            </span>
          ) : (
            message
          )}
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
            {isManager && isDraft && (
              <button
                type="button"
                onClick={() => setBulkPickerOpen(true)}
                className="inline-flex items-center gap-2 rounded-lg bg-tertiary-container px-4 py-2.5 text-label-md font-semibold text-on-tertiary-container border border-tertiary/20 hover:bg-tertiary/10 transition-colors"
              >
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                  playlist_add
                </span>
                Gán hàng loạt
              </button>
            )}
            {isManager && (
              <button
                type="button"
                onClick={() => {
                  const today = new Date();
                  if (selectedPeriod && (today < new Date(selectedPeriod.startDate) || today > new Date(selectedPeriod.endDate))) {
                    return;
                  }
                  setAddModalDate(today);
                }}
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
              dryRunData={dryRunData}
              checkingConflicts={checkingConflicts}
              publishing={publishing}
              exporting={exporting}
              notifying={notifying}
              notified={notified}
              onStepSelect={handleWorkflowStep}
              onExport={handleExport}
            />
          </div>
        )}
      </div>

      {/* Dry-run publish results */}
      {selectedPanel === "conflicts" && dryRunData && (
        <section className="space-y-3">
          {/* Can publish banner */}
          {dryRunData.canPublish && (
            <div className="rounded-xl border border-secondary/30 bg-secondary-container p-4 flex items-center gap-3">
              <span
                className="material-symbols-outlined text-[24px] text-secondary shrink-0"
                style={{ fontVariationSettings: "'FILL' 1" }}
                aria-hidden="true"
              >
                check_circle
              </span>
              <div>
                <p className="text-label-md font-semibold text-on-secondary-container">
                  Kỳ lịch sẵn sàng công bố
                </p>
                <p className="text-label-sm text-on-secondary-container/80">
                  Không phát hiện xung đột hay khoảng trống phủ nào.
                </p>
              </div>
              <span className="ml-auto shrink-0 inline-flex items-center gap-1 px-3 py-1 rounded-full bg-secondary text-on-secondary text-label-sm font-semibold">
                <span className="material-symbols-outlined text-[14px]" style={{ fontVariationSettings: "'FILL' 1" }}>check</span>
                Có thể công bố
              </span>
            </div>
          )}

          {/* Conflict list */}
          {dryRunData.hasConflicts && (
            <div className="rounded-xl border border-error/30 bg-error-container overflow-hidden">
              <div className="px-4 py-3 border-b border-error/20 flex items-center gap-2">
                <span className="material-symbols-outlined text-[20px] text-error" style={{ fontVariationSettings: "'FILL' 1" }} aria-hidden="true">
                  error
                </span>
                <h3 className="text-label-md font-semibold text-on-error-container">
                  {dryRunData.conflictCount} xung đột phát hiện — chặn công bố
                </h3>
              </div>
              <div className="divide-y divide-error/10 max-h-64 overflow-y-auto">
                {dryRunData.conflicts.map((conflict, idx) => (
                  <div key={idx} className="px-4 py-2.5 flex items-start gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="text-label-md font-semibold text-on-error-container">
                        {conflict.staffName}
                      </p>
                      <p className="text-label-sm text-on-error-container/80">
                        {conflict.shiftTypeName} · {new Date(conflict.workDate).toLocaleDateString("vi-VN")}
                      </p>
                      {conflict.conflictReasons.length > 0 && (
                        <p className="text-label-sm text-error mt-0.5">
                          {conflict.conflictReasons.join(" · ")}
                        </p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Coverage gaps */}
          {dryRunData.hasCoverageGaps && (
            <div className="rounded-xl border border-tertiary/30 bg-tertiary-container overflow-hidden">
              <div className="px-4 py-3 border-b border-tertiary/20 flex items-center gap-2">
                <span className="material-symbols-outlined text-[20px] text-tertiary" style={{ fontVariationSettings: "'FILL' 1" }} aria-hidden="true">
                  warning
                </span>
                <h3 className="text-label-md font-semibold text-on-tertiary-container">
                  {dryRunData.coverageGaps.length} khoảng trống phủ — cảnh báo
                </h3>
              </div>
              <div className="divide-y divide-tertiary/10 max-h-48 overflow-y-auto">
                {dryRunData.coverageGaps.map((gap, idx) => (
                  <div key={idx} className="px-4 py-2.5 flex items-start gap-3">
                    <span className="material-symbols-outlined text-[16px] text-tertiary mt-0.5 shrink-0" aria-hidden="true">
                      info
                    </span>
                    <p className="text-label-sm text-on-tertiary-container">{gap}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Coverage summary */}
          {dryRunData.staffingCoverage && (
            <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4">
              <h3 className="text-label-md font-semibold text-on-surface mb-3 flex items-center gap-2">
                <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">
                  donut_large
                </span>
                Tổng quan phủ lịch
              </h3>
              <div className="grid grid-cols-3 gap-4 text-center">
                <div>
                  <p className="text-headline-md font-bold text-on-surface">
                    {dryRunData.staffingCoverage.totalRequired}
                  </p>
                  <p className="text-label-sm text-on-surface-variant">Tổng nhu cầu</p>
                </div>
                <div>
                  <p className="text-headline-md font-bold text-on-surface">
                    {dryRunData.staffingCoverage.totalAssigned}
                  </p>
                  <p className="text-label-sm text-on-surface-variant">Đã phân công</p>
                </div>
                <div>
                  <p className={`text-headline-md font-bold ${
                    dryRunData.staffingCoverage.overallCoverageRate >= 95
                      ? "text-secondary"
                      : dryRunData.staffingCoverage.overallCoverageRate >= 80
                      ? "text-tertiary"
                      : "text-error"
                  }`}>
                    {dryRunData.staffingCoverage.overallCoverageRate}%
                  </p>
                  <p className="text-label-sm text-on-surface-variant">Tỷ lệ phủ</p>
                </div>
              </div>
            </div>
          )}
        </section>
      )}

      {selectedPanel === "conflicts" && conflictData && !dryRunData && (
        <ConflictSection
          conflicts={conflictData.conflicts ?? []}
          selectedConflict={selectedConflict}
          selectedPeriodId={selectedPeriodId}
          onSelect={setSelectedConflict}
          onClose={() => setSelectedConflict(null)}
          onFocusDate={() => {
            setSelectedPanel("summary");
            setShowStats(false);
          }}
          onShowConflicts={() => {
            setSelectedPanel("conflicts");
          }}
          onResolve={(conflict) => {
            setSelectedConflict(conflict as unknown as ConflictDetail);
          }}
        />
      )}

      {selectedPanel === "conflicts" && dryRunData && (
        <ConflictSection
          conflicts={
            dryRunData.hasConflicts
              ? dryRunData.conflicts.map((c) => ({
                  scheduleId: c.scheduleId,
                  staffName: c.staffName,
                  workDate: c.workDate,
                  shiftTypeId: c.shiftTypeId,
                  shiftTypeName: c.shiftTypeName,
                  conflictReasons: c.conflictReasons,
                }))
              : []
          }
          selectedConflict={selectedConflict}
          selectedPeriodId={selectedPeriodId}
          onSelect={setSelectedConflict}
          onClose={() => setSelectedConflict(null)}
          onFocusDate={() => {
            setSelectedPanel("summary");
            setShowStats(false);
          }}
          onShowConflicts={() => {
            setSelectedPanel("conflicts");
          }}
          onResolve={(conflict) => {
            setSelectedConflict(conflict as unknown as ConflictDetail);
          }}
        />
      )}

      {selectedPanel !== "conflicts" && (
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
      )}

      {selectedPanel !== "conflicts" && (
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
      )}

      {selectedPanel !== "conflicts" && (showStats ? (
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
        <section
          aria-label="Chưa chọn kỳ lịch"
          className="flex flex-col items-center justify-center rounded-xl border border-dashed border-outline-variant bg-surface py-20 gap-4"
        >
          <span aria-hidden="true" className="material-symbols-outlined text-5xl text-outline">
            {config.emptyIcon}
          </span>
          <h2 className="text-on-surface-variant font-normal">{config.emptyMessage}</h2>
        </section>
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
          onAddDate={(date) => {
            if (!selectedPeriod) return;
            const start = new Date(selectedPeriod.startDate);
            const end = new Date(selectedPeriod.endDate);
            start.setHours(0, 0, 0, 0);
            end.setHours(23, 59, 59, 999);
            if (date >= start && date <= end) {
              setAddModalDate(date);
            }
          }}
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
          showViewToggle
        />
      ))}

      {selectedPeriodId && (
          <QuickAddModal
          date={addModalDate}
          periodId={selectedPeriodId}
          defaultShiftTypeId={config.shiftTypeId}
          staffList={activeStaff}
          schedules={schedules}
          compensationDays={compensationDays}
          onOptimisticAdd={handleOptimisticAdd}
          onCommit={handleCommit}
          onRollback={handleRollback}
          onSuccess={handleRefresh}
          onClose={() => setAddModalDate(null)}
          periodStart={selectedPeriod?.startDate}
          periodEnd={selectedPeriod?.endDate}
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

      {/* Bulk date picker modal */}
      {selectedPeriod && isDraft && (
        <BulkDatePickerModal
          open={bulkPickerOpen}
          onClose={() => setBulkPickerOpen(false)}
          onDatesSelected={handleBulkDatesSelected}
          periodId={selectedPeriod.id}
          periodStart={selectedPeriod.startDate}
          periodEnd={selectedPeriod.endDate}
        />
      )}

      {/* Bulk schedule modal */}
      {selectedPeriodId && (
        <BulkScheduleModal
          open={bulkModalOpen}
          onClose={() => setBulkModalOpen(false)}
          onSuccess={handleBulkSuccess}
          periodId={selectedPeriodId}
          shiftTypeId={config.shiftTypeId}
          existingSchedules={existingSchedules}
          staffList={activeStaff}
          selectedDates={bulkSelectedDates}
          submitting={bulkSubmitting}
          onSubmittingChange={setBulkSubmitting}
          compensationDays={compensationDays}
        />
      )}
    </>
  );
}
