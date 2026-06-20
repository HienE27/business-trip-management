"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { WorkflowShell } from "@/components/layout/WorkflowShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { SkeletonCalendar, SkeletonKPI, SkeletonTable } from "@/components/ui/Skeleton";
import { ConflictResolutionModal } from "@/components/ui/ConflictResolutionModal";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import { KPISection } from "@/components/monthly-schedule/KPISection";
import { QuickAddModal } from "@/components/monthly-schedule/QuickAddModal";
import { ScheduleCalendarSection } from "@/components/monthly-schedule/ScheduleCalendarSection";
import { ScheduleHeader } from "@/components/monthly-schedule/ScheduleHeader";
import { ShiftDetailModal } from "@/components/monthly-schedule/ShiftDetailModal";
import { WorkflowStepper } from "@/components/monthly-schedule/WorkflowStepper";
import { useRole, canManage } from "@/hooks/useRole";
import { useMonthlyScheduleDerivedData } from "@/hooks/monthly-schedule/useMonthlyScheduleDerivedData";
import { useMonthlyScheduleUrlState } from "@/hooks/monthly-schedule/useMonthlyScheduleUrlState";
import { useScheduleDetailModal } from "@/hooks/monthly-schedule/useScheduleDetailModal";
import { useScheduleWorkspace } from "@/hooks/useScheduleWorkspace";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ConflictDetail } from "@/types/api";
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
  const scrollYRef = useRef(0);
  const {
    selectedTab,
    selectedPanel,
    viewMode,
    parsedScheduleId,
    periodId: periodIdFromUrl,
    setQueryState,
    openScheduleDetail,
    closeScheduleDetail,
  } = useMonthlyScheduleUrlState();

  // Only restore scroll when view actually changes (calendar ↔ table).
  // Tab/filter changes do NOT change view — no scroll restore needed.
  useEffect(() => {
    const saved = scrollYRef.current;
    if (saved > 0) {
      scrollYRef.current = 0;
      requestAnimationFrame(() => window.scrollTo(0, saved));
    }
  }, [viewMode]);

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
  const [resolvingConflict, setResolvingConflict] = useState<ConflictItem | null>(null);
  const [focusDate, setFocusDate] = useState<string | null>(null);
  const [addModalDate, setAddModalDate] = useState<Date | null>(null);
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [notifying, setNotifying] = useState(false);
  const [notified, setNotified] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [localMessage, setLocalMessage] = useState<string | null>(null);

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
  });

  const handlePeriodChange = useCallback((periodId: number) => {
    wsActions.setSelectedPeriodId(periodId);
    setNotified(false);
    setFocusDate(null);
    setSelectedConflict(null);
    setQueryState({ periodId });
  }, [wsActions, setQueryState]);

  const handleRefresh = useCallback(() => {
    setLocalMessage(null);
    void wsActions.refreshWorkspace();
  }, [wsActions]);

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
    if (stepId === "notify") {
      void handleSendNotifications();
      return;
    }
    setQueryState({ panel: "summary" });
  }, [handleSendNotifications, setQueryState]);

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
      <WorkflowShell
        section="monthly-schedule"
        title="Lập lịch tháng"
        description="Điều phối kỳ lịch theo workflow vận hành thay vì chỉnh sửa rời từng record."
      >
        <SkeletonKPI />
        <SkeletonCalendar />
        <SkeletonTable rows={5} cols={4} />
      </WorkflowShell>
    );
  }

  return (
    <ErrorBoundary>
    <WorkflowShell
      section="monthly-schedule"
      title="Lập lịch tháng"
      description="Điều phối kỳ lịch theo workflow: auto schedule, conflict check, review, publish và notify."
    >
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
              scrollYRef.current = window.scrollY;
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
            checkingConflicts={checkingConflicts}
            publishing={publishing}
            notifying={notifying}
            notified={notified}
            onStepSelect={handleWorkflowStep}
          />
        </div>
      </div>

      {displayMessage && (
        <div className="rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-3 text-body-sm text-on-surface shadow-sm" role="status" aria-live="polite">
          {displayMessage}
        </div>
      )}

      <KPISection kpis={kpis} />

      {/* Row 2: Calendar — full width, then info panels below */}
      <div className="border border-outline-variant overflow-hidden rounded-xl">
        <ScheduleCalendarSection
          schedules={schedules}
          calendarAnnotations={calendarAnnotations}
          coverages={computedCoverages}
          activeStaff={activeStaff}
          specialties={specialties}
          staffFilterId={null}
          specialtyFilterId={null}
          selectedPeriodId={selectedPeriodId}
          initialYear={initialCalendar.year}
          initialMonth={initialCalendar.month}
          viewMode={viewMode}
          selectedTab={selectedTab}
          compensationDays={compensationDays}
          onRefresh={handleRefresh}
          onFocusDate={setFocusDate}
          onAddDate={setAddModalDate}
          onStaffFilterChange={() => undefined}
          onSpecialtyFilterChange={() => undefined}
          onViewDetail={(schedule) => openScheduleDetail(schedule.id)}
          onViewModeChange={(view) => setQueryState({ view })}
          onFilterTypeChange={(filter) => setQueryState({ tab: filter as "L01" | "L02" | "L03" | "L04" | "ALL" })}
          hideFilters
        />
      </div>

      {/* Bottom info panels — 3 columns */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-3">
        <ConflictSection
          conflicts={conflictList}
          selectedConflict={selectedConflict}
          selectedPeriodId={selectedPeriodId}
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
        defaultShiftTypeId={selectedTab}
        staffList={activeStaff}
        compensationDays={compensationDays}
        onSuccess={() => {
          setAddModalDate(null);
          setNotified(false);
          void wsActions.refreshWorkspace();
        }}
        onClose={() => setAddModalDate(null)}
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
    </WorkflowShell>
    </ErrorBoundary>
  );
}
