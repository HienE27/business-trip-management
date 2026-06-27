"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { SectionCard } from "@/components/ui/SectionCard";
import { Skeleton, SkeletonCalendar, SkeletonKPI, SkeletonTable } from "@/components/ui/Skeleton";
const ConflictResolutionModal = dynamic(
  () => import("@/components/ui/ConflictResolutionModal").then((m) => m.ConflictResolutionModal),
  { loading: () => null },
);
const QuickAddModal = dynamic(
  () => import("@/components/monthly-schedule/QuickAddModal").then((m) => m.QuickAddModal),
  { loading: () => null },
);
const ScheduleCalendarSection = dynamic(
  () => import("@/components/monthly-schedule/ScheduleCalendarSection").then((m) => m.ScheduleCalendarSection),
  { loading: () => <Skeleton className="h-64 rounded-xl" /> },
);
const ScheduleHeader = dynamic(
  () => import("@/components/monthly-schedule/ScheduleHeader").then((m) => m.ScheduleHeader),
  { loading: () => null },
);
const ShiftDetailModal = dynamic(
  () => import("@/components/monthly-schedule/ShiftDetailModal").then((m) => m.ShiftDetailModal),
  { loading: () => null },
);
const WorkflowStepper = dynamic(
  () => import("@/components/monthly-schedule/WorkflowStepper").then((m) => m.WorkflowStepper),
  { loading: () => null },
);
const ExportReportPanel = dynamic(
  () => import("@/components/monthly-schedule/ExportReportPanel").then((m) => m.ExportReportPanel),
  { loading: () => null },
);

import { KPISection } from "@/components/monthly-schedule/KPISection";
import { useRole, canManage } from "@/hooks/useRole";
import { useMonthlyScheduleDerivedData } from "@/hooks/monthly-schedule/useMonthlyScheduleDerivedData";
import { useMonthlyScheduleUrlState } from "@/hooks/monthly-schedule/useMonthlyScheduleUrlState";
import { useScheduleDetailModal } from "@/hooks/monthly-schedule/useScheduleDetailModal";
import { useScheduleWorkspace } from "@/hooks/useScheduleWorkspace";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ConflictDetail, Holiday } from "@/types/api";
import type { ConflictItem } from "@/types/schedule";
import type { MonthlyPanel, WorkflowStepId } from "@/components/monthly-schedule/types";
import { downloadBlob, getInitialCalendar } from "@/components/monthly-schedule/utils";

// Lazy-load the three bottom info panels. They live below the fold and are
// not critical for first paint of the calendar above. Each panel bundles
// its own helpers (date math, conflict resolution logic) — deferring them
// shaves ~30 KB off the initial JS payload (see
// docs/PERFORMANCE_AUDIT_2026-06-20.md).
function PanelSkeleton({ title }: { title: string }) {
  return (
    <SectionCard title={title}>
      <div className="h-32 animate-pulse rounded-lg bg-surface-container" />
    </SectionCard>
  );
}

const ConflictSection = dynamic(
  () => import("@/components/monthly-schedule/ConflictSection").then((m) => m.ConflictSection),
  {
    loading: () => <PanelSkeleton title="Xung đột" />,
    ssr: false,
  },
);
const CoverageSection = dynamic(
  () => import("@/components/monthly-schedule/CoverageSection").then((m) => m.CoverageSection),
  {
    loading: () => <PanelSkeleton title="Khoảng trống phân công" />,
    ssr: false,
  },
);
const ReviewSnapshotPanel = dynamic(
  () => import("@/components/monthly-schedule/ReviewSnapshotPanel").then((m) => m.ReviewSnapshotPanel),
  {
    loading: () => <PanelSkeleton title="Tổng quan ngày" />,
    ssr: false,
  },
);

export default function MonthlySchedulePage() {
  const role = useRole();
  const [wsState, wsActions] = useScheduleWorkspace();
  const {
    selectedTab,
    selectedPanel,
    parsedScheduleId,
    periodId: periodIdFromUrl,
    parsedStaffId: staffIdFromUrl,
    setQueryState,
    openScheduleDetail,
    closeScheduleDetail,
  } = useMonthlyScheduleUrlState();

  const [staffFilterId, setStaffFilterId] = useState<number | null>(null);

  // Sync staff filter from URL on mount / navigation
  useEffect(() => {
    setStaffFilterId(staffIdFromUrl);
  }, [staffIdFromUrl]);

  const handleStaffFilterChange = useCallback((staffId: number | null) => {
    setStaffFilterId(staffId);
    setQueryState({ staffId });
  }, [setQueryState]);

  // Sync workspace khi URL periodId thay đổi
  useEffect(() => {
    if (periodIdFromUrl && periodIdFromUrl !== wsState.selectedPeriodId) {
      wsActions.setSelectedPeriodId(periodIdFromUrl);
    }
  }, [periodIdFromUrl, wsState.selectedPeriodId, wsActions]);

  const {
    periods,
    selectedPeriodId,
    schedules,
    activeStaff,
    conflictData,
    compensationDays,
    requirements,
    specialties,
    loading,
    refreshing,
    message,
  } = wsState;

  const [selectedConflict, setSelectedConflict] = useState<ConflictDetail | null>(null);
  const [resolvingConflict, setResolvingConflict] = useState<ConflictDetail | null>(null);
  const [focusDate, setFocusDate] = useState<string | null>(null);
  const [addModalDate, setAddModalDate] = useState<Date | null>(null);
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [notifying, setNotifying] = useState(false);
  const [notified, setNotified] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [dryRunData, setDryRunData] = useState<import("@/types/api").PublishDryRunResponse | null>(null);
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [showExportPanel, setShowExportPanel] = useState(false);
  const [localMessage, setLocalMessage] = useState<string | null>(null);
  const [pendingLeaveRequests, setPendingLeaveRequests] = useState(0);

  const selectedPeriod = useMemo(
    () => periods.find((period) => period.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId],
  );

  const initialCalendar = useMemo(() => getInitialCalendar(selectedPeriod), [selectedPeriod]);

  const {
    detailScheduleId,
    detailSchedule,
    detailLoading,
    closeDetail,
  } = useScheduleDetailModal(parsedScheduleId, closeScheduleDetail);

  const {
    filteredSchedules,
    conflictList,
    calendarAnnotations,
    computedCoverages,
    coverageGapsByTab,
    kpis,
    focusSchedules,
  } = useMonthlyScheduleDerivedData({
    selectedTab,
    schedules,
    activeStaff,
    conflictData,
    compensationDays,
    requirements,
    focusDate,
    pendingLeaveRequests,
  });

  const handlePeriodChange = useCallback((periodId: number) => {
    wsActions.setSelectedPeriodId(periodId);
    setNotified(false);
    setFocusDate(null);
    setSelectedConflict(null);
    setQueryState({ periodId });
  }, [wsActions, setQueryState]);

  const handleAddDate = useCallback((date: Date) => {
    if (!selectedPeriod) return;
    const start = new Date(selectedPeriod.startDate);
    const end = new Date(selectedPeriod.endDate);
    start.setHours(0, 0, 0, 0);
    end.setHours(23, 59, 59, 999);
    if (date >= start && date <= end) {
      setAddModalDate(date);
    }
  }, [selectedPeriod]);

  const handleViewDetail = useCallback((schedule: { id: number }) => {
    openScheduleDetail(schedule.id);
  }, [openScheduleDetail]);

  const handleRefresh = useCallback(() => {
    setLocalMessage(null);
    void wsActions.refreshWorkspace();
  }, [wsActions]);

  // Fetch holidays when the selected period changes, so QuickAddModal
  // can show an advisory warning when the picked date is a holiday.
  useEffect(() => {
    if (!selectedPeriod) {
      setHolidays([]);
      return;
    }
    api.get<Holiday[]>("/holidays/active")
      .then(setHolidays)
      .catch(() => setHolidays([]));
  }, [selectedPeriod]);

  // Fetch pending leave requests count
  useEffect(() => {
    api.get<{ pending: number }>("/dashboard/leave-requests")
      .then((res) => setPendingLeaveRequests(res.pending ?? 0))
      .catch(() => setPendingLeaveRequests(0));
  }, []);

  const handleCheckConflicts = useCallback(async () => {
    setCheckingConflicts(true);
    setLocalMessage(null);
    try {
      await wsActions.checkConflicts();
    } finally {
      setCheckingConflicts(false);
    }
    setQueryState({ panel: "conflicts" });
  }, [setQueryState, wsActions]);

  const handlePublish = useCallback(async () => {
    setPublishing(true);
    setLocalMessage(null);
    try {
      await wsActions.publishPeriod();
    } finally {
      setPublishing(false);
    }
  }, [wsActions]);

  const handleSendNotifications = useCallback(async () => {
    if (!selectedPeriodId) return;
    if (!canManage(role)) return;
    setNotifying(true);
    setLocalMessage(null);
    try {
      await wsActions.sendNotifications();
      setNotified(true);
    } finally {
      setNotifying(false);
    }
  }, [selectedPeriodId, wsActions]);

  const handleExport = useCallback(async () => {
    if (!selectedPeriodId) return;
    setExporting(true);
    setLocalMessage(null);
    try {
      const blob = await api.exportScheduleExcel(selectedPeriodId);
      downloadBlob(blob, `lich-cong-tac-${selectedPeriod?.periodName ?? selectedPeriodId}.xlsx`);
      setLocalMessage("Đã xuất file Excel kỳ lịch.");
    } catch (error) {
      setLocalMessage(getErrorMessage(error, "Không thể xuất file. Vui lòng thử lại."));
    } finally {
      setExporting(false);
    }
  }, [selectedPeriod, selectedPeriodId]);

  const handleWorkflowStep = useCallback((stepId: WorkflowStepId) => {
    if (stepId === "conflicts") {
      setQueryState({ panel: "conflicts" });
      return;
    }
    if (stepId === "export") {
      setShowExportPanel(true);
      void handleExport();
      return;
    }
    if (stepId === "notify") {
      void handleSendNotifications();
      return;
    }
    setShowExportPanel(false);
    setQueryState({ panel: "summary" });
  }, [handleExport, handleSendNotifications, setQueryState]);

  const showPanel = useCallback((panel: MonthlyPanel) => {
    setQueryState({ panel });
  }, [setQueryState]);

  const handleConflictRefresh = useCallback(() => {
    void wsActions.refreshWorkspace();
    void wsActions.checkConflicts();
  }, [wsActions]);

  const displayMessage = localMessage ?? message;

  if (loading) {
    return (
      <>
        <SkeletonKPI />
        <SkeletonCalendar />
        <SkeletonTable rows={5} cols={4} />
      </>
    );
  }

  return (
    <>
      {/* Row 1: ScheduleHeader + WorkflowStepper */}
      <div className="flex flex-col xl:flex-row gap-3">
        {/* Left: ScheduleHeader — full width on mobile, flex-1 on xl */}
        <div className="flex-1 min-w-0">
          <ScheduleHeader
            periods={periods}
            selectedPeriodId={selectedPeriodId}
            selectedPeriod={selectedPeriod}
            selectedTab={selectedTab}
            refreshing={refreshing}
            exporting={exporting}
            checkingConflicts={checkingConflicts}
            publishing={publishing}
            canPublish={canManage(role)}
            onPeriodChange={handlePeriodChange}
          onTabChange={(tab) => {
              setQueryState({ tab });
            }}
            onRefresh={handleRefresh}
            onExport={handleExport}
            onCheckConflicts={handleCheckConflicts}
            onPublish={handlePublish}
            onShowSummary={() => showPanel("summary")}
          />
        </div>

        {/* Right: WorkflowStepper — narrow strip on xl */}
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
      </div>

      {displayMessage && (
        <div className="rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-3 text-body-sm text-on-surface shadow-sm" role="status" aria-live="polite">
          {displayMessage}
        </div>
      )}

      {/* Filter bar: staff picker */}
      <div className="flex flex-wrap items-center gap-3 rounded-lg border border-outline-variant bg-surface-container-low px-4 py-3">
        <span className="flex items-center gap-1.5 text-label-md text-on-surface-variant font-semibold shrink-0">
          <span className="material-symbols-outlined text-[18px] text-outline" aria-hidden="true">filter_list</span>
          Lọc nhân sự:
        </span>
        <select
          className="h-9 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all cursor-pointer appearance-none"
          value={staffFilterId ?? ""}
          onChange={(e) => {
            const val = e.target.value;
            handleStaffFilterChange(val ? Number(val) : null);
          }}
          aria-label="Lọc theo nhân sự"
        >
          <option value="">Tất cả nhân sự</option>
          {activeStaff.map((staff) => (
            <option key={staff.id} value={staff.id}>{staff.fullName}</option>
          ))}
        </select>
        {staffFilterId !== null && (
          <span className="text-label-sm text-on-surface-variant">
            đang xem lịch của <strong className="text-on-surface">
              {activeStaff.find((s) => s.id === staffFilterId)?.fullName ?? `#${staffFilterId}`}
            </strong>
          </span>
        )}
      </div>

      <KPISection kpis={kpis} />

      {/* Export report panel — shown when user clicks "Xuất báo cáo" step */}
      {showExportPanel && (
        <ExportReportPanel
          selectedPeriod={selectedPeriod}
          selectedPeriodId={selectedPeriodId}
          onClose={() => setShowExportPanel(false)}
        />
      )}

      {/* Row 2: Calendar — full width, then info panels below */}
      <div className="border border-outline-variant overflow-hidden rounded-xl">
        <ScheduleCalendarSection
          schedules={filteredSchedules}
          activeStaff={activeStaff}
          selectedPeriodId={selectedPeriodId}
          initialYear={initialCalendar.year}
          initialMonth={initialCalendar.month}
          selectedTab={selectedTab}
          compensationDays={compensationDays}
          onRefresh={handleRefresh}
          onAddDate={handleAddDate}
          onViewDetail={handleViewDetail}
          onFilterTypeChange={(filter) => setQueryState({ tab: filter as "L01" | "L02" | "L03" | "L04" | "ALL" })}
        />
      </div>

      {/* Bottom info panels — 3 columns */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
        <ConflictSection
          conflicts={conflictList}
          selectedConflict={selectedConflict}
          onSelect={setSelectedConflict}
          onClose={() => setSelectedConflict(null)}
          onFocusDate={setFocusDate}
          onShowConflicts={() => showPanel("conflicts")}
          onResolve={setResolvingConflict}
        />

        <CoverageSection
          coverageGaps={coverageGapsByTab}
          hasCoverageGaps={coverageGapsByTab.length > 0}
          totalCoverageGaps={coverageGapsByTab.length}
        />

        <ReviewSnapshotPanel focusDate={focusDate} schedules={focusSchedules} />
      </div>

      <QuickAddModal
        date={addModalDate}
        periodId={selectedPeriodId}
        defaultShiftTypeId={selectedTab === "ALL" ? "L01" : selectedTab}
        staffList={activeStaff}
        schedules={schedules}
        holidays={holidays}
        compensationDays={compensationDays}
        onSuccess={() => {
          setAddModalDate(null);
          setNotified(false);
          void wsActions.refreshWorkspace();
        }}
        onClose={() => setAddModalDate(null)}
        periodStart={selectedPeriod?.startDate}
        periodEnd={selectedPeriod?.endDate}
      />

      <ShiftDetailModal
        scheduleId={detailScheduleId}
        schedule={detailSchedule}
        loading={detailLoading}
        canEdit={canManage(role)}
        onClose={closeDetail}
        onSave={() => { void wsActions.refreshWorkspace(); closeDetail(); }}
      />

      <ConflictResolutionModal
        open={resolvingConflict !== null}
        onClose={() => {
          setResolvingConflict(null);
          setSelectedConflict(null);
        }}
        conflict={resolvingConflict}
        onRefresh={handleConflictRefresh}
      />
    </>
  );
}
