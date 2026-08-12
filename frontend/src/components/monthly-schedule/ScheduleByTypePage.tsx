"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Skeleton } from "@/components/ui/Skeleton";
import { FormSelect } from "@/components/ui/FormSelect";
import { WorkflowStepper } from "@/components/monthly-schedule/WorkflowStepper";
import { ScheduleMatrixView } from "@/components/dashboard/ScheduleMatrixView";
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
  Holiday,
  LeaveRequest,
  PublishDryRunResponse,
  Schedule,
  SchedulePeriod,
  Specialty,
  Staff,
} from "@/types/api";
import type { MonthlyPanel, ScheduleTab, WorkflowStepId } from "@/components/monthly-schedule/types";

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
  const searchParams = useSearchParams();
  const urlPeriodId = searchParams.get("periodId");

  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(
    urlPeriodId ? Number(urlPeriodId) : null
  );
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [compensationDays, setCompensationDays] = useState<CompensationDay[]>([]);
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [leaveRequests, setLeaveRequests] = useState<LeaveRequest[]>([]);
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

  // Quick single-date picker (reuses BulkDatePickerModal)
  const [quickPickerOpen, setQuickPickerOpen] = useState(false);

  const loadBaseData = useCallback(async (options?: { keepCurrentPeriod?: boolean }) => {
    try {
      setLoading(true);
      const requests: [
        Promise<SchedulePeriod[]>,
        Promise<Staff[]>,
        Promise<LeaveRequest[]>,
        Promise<Specialty[]> | null,
      ] = [
        api.get<SchedulePeriod[]>("/periods"),
        api.get<Staff[]>("/staff/active"),
        api.get<LeaveRequest[]>("/leave-requests/status/approved"),
        isExpertMode ? api.get<Specialty[]>("/specialties/active") : null,
      ];
      const [periodData, staffData, leaveData, specialtyData] = await Promise.all(requests);
      const pList = periodData ?? [];
      setPeriods(pList);
      setActiveStaff(staffData ?? []);
      setLeaveRequests(leaveData ?? []);
      setSpecialties(specialtyData ?? []);

      // Only auto-select period on first load if no period is selected yet
      if (!options?.keepCurrentPeriod) {
        const current = selectedPeriodId;
        if (!current || !pList.find((p) => p.id === current)) {
          const draft = pList.find((p) => p.status === "DRAFT") ?? pList[0] ?? null;
          setSelectedPeriodId(draft?.id ?? null);
        }
      }
    } catch {
      setMessage("Không thể tải dữ liệu. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  }, [isExpertMode, selectedPeriodId]);

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
          api.createNotification(staff.id, {
            title: `Thông báo lịch trực – ${periodName}`,
            message: `Lịch trực của bạn đã được cập nhật. Vui lòng kiểm tra chi tiết trong hệ thống.`,
          })
        )
      );
      setNotified(true);
      setMessage(`Đã gửi thông báo đến ${activeStaff.length} nhân sự.`);
      toastSuccess(`Đã gửi thông báo đến ${activeStaff.length} nhân sự.`);
    } catch {
      setMessage("Không thể gửi thông báo.");
      toastError("Không thể gửi thông báo.");
    } finally {
      setNotifying(false);
    }
  }, [selectedPeriodId, activeStaff, periods, toastSuccess, toastError]);

  // Reload schedules only (used after publish, bulk operations)
  const reloadSchedules = useCallback(async () => {
    if (!selectedPeriodId) return;
    // Reset dryRunData to avoid showing stale coverage stats after auto-scheduling
    setDryRunData(null);
    setConflictData(null);
    try {
      setLoading(true);
      const [scheduleData, compData] = await Promise.all([
        isExpertMode
          ? api.get<Schedule[]>("/schedules/expert-clinic", {
              periodId: selectedPeriodId,
              ...(selectedSpecialtyId ? { specialtyId: selectedSpecialtyId } : {}),
            })
          : api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
        api.get<CompensationDay[]>(`/schedules/compensation-days/${selectedPeriodId}`),
      ]);
      // Extract schedules from paginated response if needed (API returns Page object)
      const schedulesArray = (scheduleData && typeof scheduleData === 'object' && 'content' in scheduleData)
        ? (scheduleData as { content: Schedule[] }).content
        : Array.isArray(scheduleData) ? scheduleData : [];
      setSchedules(schedulesArray);
      setCompensationDays(compData ?? []);
    } catch {
      setMessage(config.fetchErrorMessage);
    } finally {
      setLoading(false);
    }
  }, [selectedPeriodId, selectedSpecialtyId, isExpertMode, config.fetchErrorMessage]);

  const handlePublish = useCallback(async () => {
    if (!selectedPeriodId) return;
    setPublishing(true);
    setMessage(null);
    try {
      await api.publishPeriod(selectedPeriodId);
      setMessage("Kỳ lịch đã được công bố thành công.");
      toastSuccess("Kỳ lịch đã được công bố thành công");
      // Reload schedules to reflect new status
      await reloadSchedules();
    } catch (err) {
      const msg = getErrorMessage(err, "Không thể công bố kỳ lịch.");
      setMessage(msg);
      toastError(msg);
    } finally {
      setPublishing(false);
    }
  }, [selectedPeriodId, reloadSchedules, toastSuccess, toastError]);

  const handleWorkflowStep = useCallback((stepId: WorkflowStepId) => {
    // Guard against double-fires when a step is already in progress
    if (stepId === "conflicts" && checkingConflicts) return;
    if (stepId === "export" && exporting) return;
    if (stepId === "notify" && notifying) return;
    if (stepId === "publish" && publishing) return;

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
  }, [checkingConflicts, exporting, notifying, publishing, handleDryRunPublish, handleExport, handleSendNotifications, handlePublish]);

  // Bulk schedule handlers
  const handleBulkDatesSelected = useCallback((dates: string[]) => {
    if (dates.length === 1) {
      // Single date → open QuickAddModal with that date
      // BUGFIX (BUG#2): close BOTH pickers to prevent half-open state.
      // handleBulkDatesSelected is shared by BulkDatePickerModal (bulkPickerOpen)
      // and the quick single-date picker (quickPickerOpen). Previously only
      // setBulkPickerOpen(false) was called, so when the user entered via
      // the quick picker, quickPickerOpen stayed true and its modal remained
      // mounted under the QuickAddModal → zombie backdrop + block all interaction.
      setBulkPickerOpen(false);
      setQuickPickerOpen(false);
      const [y, m, d] = dates[0]!.split("-").map(Number);
      setAddModalDate(new Date(y!, m! - 1, d!));
    } else {
      // Multiple dates → open bulk modal
      setBulkSelectedDates(dates);
      setBulkPickerOpen(false);
      setQuickPickerOpen(false);
      setBulkModalOpen(true);
    }
  }, []);

  const handleBulkSuccess = useCallback(() => {
    void reloadSchedules();
    void handleDryRunPublish();
  }, [reloadSchedules, handleDryRunPublish]);

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

  // Effect 1: Load base data ONCE when component mounts (periods, staff, specialties)
  const [baseDataLoaded, setBaseDataLoaded] = useState(false);
  useEffect(() => {
    let cancelled = false;

    const loadBase = async () => {
      try {
        const [periodData, staffData, leaveData, specialtyData] = await Promise.all([
          api.get<SchedulePeriod[]>("/periods"),
          api.get<Staff[]>("/staff/active"),
          api.get<LeaveRequest[]>("/leave-requests/status/approved"),
          isExpertMode ? api.get<Specialty[]>("/specialties/active") : Promise.resolve(null),
        ]);

        if (cancelled) return;

        setPeriods(periodData ?? []);
        setActiveStaff(staffData ?? []);
        setLeaveRequests(leaveData ?? []);
        setSpecialties(specialtyData ?? []);

        // Auto-select DRAFT period if nothing selected
        const pList = periodData ?? [];
        if (!selectedPeriodId || !pList.find((p) => p.id === selectedPeriodId)) {
          const draft = pList.find((p) => p.status === "DRAFT") ?? pList[0] ?? null;
          if (draft?.id) setSelectedPeriodId(draft.id);
        }

        // Always mark base data as loaded, even if no period was auto-selected
        setBaseDataLoaded(true);
      } catch {
        // Silent fail for base data
      }
    };

    void loadBase();
    return () => { cancelled = true; };
  }, []); // Run once only

  // Effect 1b: Set loading=false when no period is selected (after base data loads)
  useEffect(() => {
    if (baseDataLoaded && !selectedPeriodId) {
      setLoading(false);
    }
  }, [baseDataLoaded, selectedPeriodId]);

  // Effect 2: Load schedules when period changes (FAST - no base data)
  useEffect(() => {
    if (!selectedPeriodId || !baseDataLoaded) return;

    let cancelled = false;
    setLoading(true);
    setMessage(null);

    const loadSchedules = async () => {
      try {
        const params = isExpertMode
          ? { periodId: selectedPeriodId, ...(selectedSpecialtyId ? { specialtyId: selectedSpecialtyId } : {}) }
          : {};
        const [scheduleData, compData, holidayData] = await Promise.all([
          isExpertMode
            ? api.get<Schedule[]>("/schedules/expert-clinic", params)
            : api.get<Schedule[]>(`/schedules/period/${selectedPeriodId}`),
          api.get<CompensationDay[]>(`/schedules/compensation-days/${selectedPeriodId}`),
          api.get<Holiday[]>("/holidays/active"),
        ]);
        if (!cancelled) {
          // Extract schedules from paginated response if needed (API returns Page object)
          const schedulesArray = (scheduleData && typeof scheduleData === 'object' && 'content' in scheduleData)
            ? (scheduleData as { content: Schedule[] }).content
            : Array.isArray(scheduleData) ? scheduleData : [];
          if (!isExpertMode) {
            setSchedules(schedulesArray.filter((s) => s.shiftType.id === config.shiftTypeId));
          } else {
            setSchedules(schedulesArray);
          }
          setCompensationDays(compData ?? []);
          setHolidays(holidayData ?? []);
        }
      } catch {
        if (!cancelled) setMessage(config.fetchErrorMessage);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void loadSchedules();
    return () => { cancelled = true; };
  }, [selectedPeriodId, selectedSpecialtyId, isExpertMode, config.shiftTypeId, config.fetchErrorMessage, baseDataLoaded]);

  // Sync selectedPeriodId when URL changes
  useEffect(() => {
    if (urlPeriodId) {
      const newId = Number(urlPeriodId);
      if (newId !== selectedPeriodId) {
        setSelectedPeriodId(newId);
      }
    }
  }, [urlPeriodId]); // Intentionally NOT including selectedPeriodId to avoid infinite loop

  const handleRefresh = useCallback((_id?: number) => {
    // Force re-fetch schedules from server
    void reloadSchedules();
  }, [reloadSchedules]);

  // Wrapper for onSave callback (expects Schedule, ignores it)
  const handleSaveCallback = useCallback(() => {
    handleRefresh();
  }, [handleRefresh]);

  // Wrapper for onDelete callback (expects number, ignores it)
  const handleDeleteCallback = useCallback(() => {
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

  // Listen for schedules-changed events (dispatched by auto-scheduling, template application, etc.)
  useEffect(() => {
    const handleSchedulesChanged = () => {
      void reloadSchedules();
    };
    window.addEventListener("schedules-changed", handleSchedulesChanged);
    return () => {
      window.removeEventListener("schedules-changed", handleSchedulesChanged);
    };
  }, [reloadSchedules]);

  // Load conflict data from the DB — this is the authoritative source for KPI display.
  // Without this, the "Xung đột" KPI falls back to counting schedules[].hasConflict,
  // which is stale for algorithm-created schedules until the next full conflict check.
  useEffect(() => {
    if (!selectedPeriodId) return;
    api
      .get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`)
      .then((data) => {
        setConflictData(data);
      })
      .catch((err) => {
      });
  }, [selectedPeriodId]);

  const selectedSchedule = useMemo(
    () => schedules.find((s) => s.id === detailScheduleId) ?? null,
    [schedules, detailScheduleId]
  );

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
        accent: "bg-blue-100/10 text-blue-800",
      }
    : compensationDays.length > 0
    ? {
        label: "Ngày nghỉ bù",
        value: compensationDays.length,
        icon: "bedtime",
        accent: "bg-surface-container-high text-on-surface",
      }
    : null;

  const isDraft = selectedPeriod?.status === "DRAFT";

  return (
    <>
      {message && (
        <div
          className="rounded-lg border px-4 py-3 text-sm"
          role="alert"
          style={
            dryRunData?.canPublish
              ? { borderColor: "#10b981", backgroundColor: "#dcfce7", color: "#166534" }
              : dryRunData?.hasConflicts || conflictData?.hasConflicts
              ? { borderColor: "#ef4444", backgroundColor: "#fee2e2", color: "#991b1b" }
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
            <FormSelect
              id={`${config.activeSection}-period-select`}
              label="Kỳ lịch"
              value={selectedPeriodId != null ? String(selectedPeriodId) : ""}
              onChange={(e) => {
                const next = Number(e.target.value);
                setSelectedPeriodId(next);
                if (isExpertMode) setSelectedSpecialtyId(null);
              }}
              options={[
                { value: "", label: "Chọn kỳ lịch" },
                ...periods.map((p) => ({ value: String(p.id), label: `${p.periodName} (${p.status})` })),
              ]}
              className="!bg-surface-container-lowest min-w-[200px]"
            />
            {isExpertMode && (
              <FormSelect
                id={`${config.activeSection}-specialty-filter`}
                label="Chuyên khoa"
                value={selectedSpecialtyId != null ? String(selectedSpecialtyId) : ""}
                onChange={(e) =>
                  setSelectedSpecialtyId(e.target.value ? Number(e.target.value) : null)
                }
                options={[
                  { value: "", label: "Tất cả chuyên khoa" },
                  ...specialties.map((s) => ({ value: String(s.id), label: s.name })),
                ]}
                className="!bg-surface-container-lowest min-w-[200px] shrink-0 border-l border-outline-variant pl-4"
              />
            )}
            {isManager && isDraft && (
              <button
                type="button"
                onClick={() => setBulkPickerOpen(true)}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-amber-100 text-amber-800 border border-amber-300 font-label-md hover:bg-amber-200 transition-colors"
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
                  if (!selectedPeriod) return;
                  // Open date picker so user can pick a date within the period
                  setQuickPickerOpen(true);
                }}
                className="inline-flex items-center gap-2 rounded-lg bg-blue-100 px-4 py-2.5 text-label-md font-semibold text-blue-800 hover:bg-blue-100/90 transition-colors"
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
            <div className="rounded-xl border border-emerald-300 bg-emerald-100 text-emerald-800 p-4 flex items-center gap-3">
              <span
                className="material-symbols-outlined text-[24px] text-emerald-800 shrink-0"
                style={{ fontVariationSettings: "'FILL' 1" }}
                aria-hidden="true"
              >
                check_circle
              </span>
              <div>
                <p className="text-label-md font-semibold text-emerald-800">
                  Kỳ lịch sẵn sàng công bố
                </p>
                <p className="text-label-sm text-emerald-800/80">
                  Không phát hiện xung đột hay khoảng trống phủ nào.
                </p>
              </div>
              <span className="ml-auto shrink-0 inline-flex items-center gap-1 px-3 py-1 rounded-full bg-secondary text-white text-label-sm font-semibold">
                <span className="material-symbols-outlined text-[14px]" style={{ fontVariationSettings: "'FILL' 1" }}>check</span>
                Có thể công bố
              </span>
            </div>
          )}

          {/* Conflict list */}
          {dryRunData.hasConflicts && (
            <div className="rounded-xl border border-red-300 bg-red-100 text-red-800 overflow-hidden">
              <div className="px-4 py-3 border-b border-red-300 flex items-center gap-2">
                <span className="material-symbols-outlined text-[20px] text-red-800" style={{ fontVariationSettings: "'FILL' 1" }} aria-hidden="true">
                  error
                </span>
                <h3 className="text-label-md font-semibold text-red-800">
                  {dryRunData.conflictCount} xung đột phát hiện — chặn công bố
                </h3>
              </div>
              <div className="divide-y divide-error/10 max-h-64 overflow-y-auto">
                {dryRunData.conflicts.map((conflict, idx) => (
                  <div key={idx} className="px-4 py-2.5 flex items-start gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="text-label-md font-semibold text-red-800">
                        {conflict.staffName}
                      </p>
                      <p className="text-label-sm text-red-800/80">
                        {conflict.shiftTypeName} · {new Date(conflict.workDate).toLocaleDateString("vi-VN")}
                      </p>
                      {conflict.conflictReasons.length > 0 && (
                        <p className="text-label-sm text-red-800 mt-0.5">
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
            <div className="rounded-xl border border-amber-300 bg-amber-100 text-amber-800 overflow-hidden">
              <div className="px-4 py-3 border-b border-amber-300 flex items-center gap-2">
                <span className="material-symbols-outlined text-[20px] text-tertiary" style={{ fontVariationSettings: "'FILL' 1" }} aria-hidden="true">
                  warning
                </span>
                <h3 className="text-label-md font-semibold text-amber-800">
                  {dryRunData.coverageGaps.length} khoảng trống phủ — cảnh báo
                </h3>
              </div>
              <div className="divide-y divide-tertiary/10 max-h-48 overflow-y-auto">
                {dryRunData.coverageGaps.map((gap, idx) => (
                  <div key={idx} className="px-4 py-2.5 flex items-start gap-3">
                    <span className="material-symbols-outlined text-[16px] text-tertiary mt-0.5 shrink-0" aria-hidden="true">
                      info
                    </span>
                    <p className="text-label-sm text-amber-800">{gap}</p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Coverage summary */}
          {dryRunData.staffingCoverage && (
            <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4">
              <h3 className="text-label-md font-semibold text-on-surface mb-3 flex items-center gap-2">
                <span className="material-symbols-outlined text-[18px] text-blue-800" aria-hidden="true">
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
                      ? "text-emerald-800"
                      : dryRunData.staffingCoverage.overallCoverageRate >= 80
                      ? "text-tertiary"
                      : "text-red-800"
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
            setSelectedConflict(conflict);
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
            setSelectedConflict(conflict);
          }}
        />
      )}

      {selectedPanel !== "conflicts" && (
      <section className="grid gap-3 grid-cols-2 sm:grid-cols-4">
        {([
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
            // Prefer the count from checkPeriodConflicts (DB-tracked) over the per-entity
            // hasConflict flag, because algorithm-saved schedules set hasConflict=false until
            // the next reconcile pass. The checkPeriodConflicts endpoint is the same source
            // the ConflictSection uses, so the KPI and the conflict list stay in lockstep.
            value:
              conflictData?.totalConflicts
                ?? dryRunData?.conflictCount
                ?? schedules.filter((s) => s.hasConflict === true).length,
            icon: "warning",
            accent: "bg-red-100 text-red-800",
          },
        ].filter(Boolean) as Array<{label: string; value: number; icon: string; accent: string}>).map((kpi) => (
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

      {/* Specialty filter tabs - only show when in expert clinic mode and has specialties */}
      {isExpertMode && specialties.length > 0 && (
        <div className="overflow-x-auto pb-1 scrollbar-thin">
          <div className="flex items-center gap-2 min-w-max" role="tablist" aria-label="Lọc theo chuyên khoa">
            {/* All specialties tab */}
            <button
              type="button"
              role="tab"
              aria-selected={selectedSpecialtyId === null}
              onClick={() => setSelectedSpecialtyId(null)}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-full text-label-md font-medium transition-all whitespace-nowrap ${
                selectedSpecialtyId === null
                  ? "bg-blue-100 text-blue-800 shadow-sm"
                  : "bg-surface-container-low text-on-surface-variant hover:bg-surface-container-high border border-outline-variant"
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">stethoscope</span>
              Tất cả ({specialties.length})
            </button>

            {/* Individual specialty tabs */}
            {specialties.map((specialty) => {
              const count = schedules.filter((s) => s.staff?.specialtyName === specialty.name).length;
              return (
                <button
                  key={specialty.id}
                  type="button"
                  role="tab"
                  aria-selected={selectedSpecialtyId === specialty.id}
                  onClick={() => setSelectedSpecialtyId(selectedSpecialtyId === specialty.id ? null : specialty.id)}
                  className={`flex items-center gap-2 px-4 py-2.5 rounded-full text-label-md font-medium transition-all whitespace-nowrap ${
                    selectedSpecialtyId === specialty.id
                      ? "bg-shift-expert text-on-shift-expert shadow-sm"
                      : "bg-surface-container-low text-on-surface-variant hover:bg-surface-container-high border border-outline-variant"
                  }`}
                >
                  <span className="material-symbols-outlined text-[16px]">medical_services</span>
                  {specialty.name}
                  {count > 0 && (
                    <span className={`text-[11px] font-bold px-1.5 py-0.5 rounded-full ${
                      selectedSpecialtyId === specialty.id
                        ? "bg-on-primary/20 text-blue-800"
                        : "bg-blue-100/10 text-blue-800"
                    }`}>
                      {count}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {selectedPanel !== "conflicts" && (
      <div className="flex items-center gap-1 p-1 bg-surface-container-low rounded-xl w-fit">
        <button
          type="button"
          onClick={() => setShowStats(false)}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-label-md font-semibold transition-all ${
            !showStats
              ? "bg-blue-100 text-blue-800 shadow-sm"
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
              ? "bg-blue-100 text-blue-800 shadow-sm"
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
            <span className="material-symbols-outlined text-[22px] text-blue-800">bar_chart</span>
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
        <ScheduleMatrixView
          schedules={schedules}
          staffList={activeStaff}
          periodId={selectedPeriodId}
          periodStart={selectedPeriod?.startDate}
          periodEnd={selectedPeriod?.endDate}
          initialYear={initialCalendar.year}
          initialMonth={initialCalendar.month}
          selectedTab={isExpertMode ? selectedTab : (config.shiftTypeId satisfies ScheduleTab)}
          compensationDays={compensationDays}
          onRefresh={handleRefresh}
          onAddClick={(date) => {
            if (!selectedPeriod) return;
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, "0");
            const day = String(date.getDate()).padStart(2, "0");
            const dateStr = `${year}-${month}-${day}`;
            if (
              dateStr >= selectedPeriod.startDate &&
              dateStr <= selectedPeriod.endDate
            ) {
              setAddModalDate(date);
            }
          }}
          onViewDetail={(schedule) => setDetailScheduleId(schedule.id)}
          onEdit={(schedule) => setDetailScheduleId(schedule.id)}
          onDelete={(schedule) => setDetailScheduleId(schedule.id)}
          onResolve={(conflict) => setSelectedConflict(conflict)}
          onFilterTypeChange={
            isExpertMode
              ? (filter: string) => setSelectedTab(filter as ScheduleTab)
              : () => undefined
          }
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
          leaveRequests={leaveRequests}
          onOptimisticAdd={handleOptimisticAdd}
          onCommit={handleCommit}
          onRollback={handleRollback}
          onSuccess={handleRefresh}
          onClose={() => setAddModalDate(null)}
          periodStart={selectedPeriod?.startDate}
          periodEnd={selectedPeriod?.endDate}
        />
      )}

      <ShiftDetailModal
        scheduleId={detailScheduleId}
        schedule={selectedSchedule}
        loading={false}
        canEdit={isManager}
        onClose={() => setDetailScheduleId(null)}
        onSave={handleSaveCallback}
        onDelete={handleDeleteCallback}
        onRefresh={reloadSchedules}
      />

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

      {/* Quick single-date picker (same component, different state) */}
      {selectedPeriod && (
        <BulkDatePickerModal
          open={quickPickerOpen}
          onClose={() => setQuickPickerOpen(false)}
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
