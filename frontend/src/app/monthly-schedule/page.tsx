"use client";

import { useCallback, useMemo, useState } from "react";
import { WorkflowShell } from "@/components/layout/WorkflowShell";
import { SkeletonCalendar, SkeletonKPI, SkeletonTable } from "@/components/ui/Skeleton";
import { ConflictResolutionModal } from "@/components/ui/ConflictResolutionModal";
import { ConflictSection } from "@/components/monthly-schedule/ConflictSection";
import { CoverageSection } from "@/components/monthly-schedule/CoverageSection";
import { KPISection } from "@/components/monthly-schedule/KPISection";
import { QuickAddModal } from "@/components/monthly-schedule/QuickAddModal";
import { ReviewSnapshotPanel } from "@/components/monthly-schedule/ReviewSnapshotPanel";
import { ScheduleCalendarSection } from "@/components/monthly-schedule/ScheduleCalendarSection";
import { ScheduleHeader } from "@/components/monthly-schedule/ScheduleHeader";
import { ScheduleTabs } from "@/components/monthly-schedule/ScheduleTabs";
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

export default function MonthlySchedulePage() {
  const role = useRole();
  const [wsState, wsActions] = useScheduleWorkspace();
  const {
    selectedTab,
    selectedPanel,
    viewMode,
    parsedScheduleId,
    parsedStaffId,
    parsedSpecialtyId,
    setQueryState,
    openScheduleDetail,
    closeScheduleDetail,
  } = useMonthlyScheduleUrlState();

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
  const [staffFilterId, setStaffFilterId] = useState<number | null>(parsedStaffId);
  const [specialtyFilterId, setSpecialtyFilterId] = useState<number | null>(parsedSpecialtyId);
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

  const clearTransientMessages = useCallback(() => {
    setLocalMessage(null);
    wsActions.clearMessage();
  }, [wsActions]);

  const handlePeriodChange = useCallback((periodId: number) => {
    wsActions.setSelectedPeriodId(periodId);
    setNotified(false);
    setFocusDate(null);
    setSelectedConflict(null);
  }, [wsActions]);

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
    <WorkflowShell
      section="monthly-schedule"
      title="Lập lịch tháng"
      description="Điều phối kỳ lịch theo workflow: auto schedule, conflict check, review, publish và notify."
    >
      <section className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
        <ScheduleHeader
          periods={periods}
          selectedPeriodId={selectedPeriodId}
          selectedPeriod={selectedPeriod}
          refreshing={refreshing}
          exporting={exporting}
          checkingConflicts={checkingConflicts}
          publishing={publishing}
          canPublish={canManage(role)}
          onPeriodChange={handlePeriodChange}
          onRefresh={handleRefresh}
          onExport={handleExport}
          onCheckConflicts={handleCheckConflicts}
          onPublish={handlePublish}
          onShowSummary={() => showPanel("summary")}
        />

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
      </section>

      {displayMessage && (
        <div className="rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-3 text-body-sm text-on-surface shadow-sm" role="status" aria-live="polite">
          {displayMessage}
        </div>
      )}

      <KPISection kpis={kpis} />

      <ScheduleTabs
        selectedTab={selectedTab}
        viewMode={viewMode}
        onTabChange={(tab) => setQueryState({ tab })}
        onViewChange={(view) => setQueryState({ view })}
      >
        <div className="grid gap-4 p-5 md:grid-cols-1 xl:grid-cols-[minmax(0,1fr)_340px]">
          <div className="space-y-4">
            <ScheduleCalendarSection
              schedules={filteredSchedules}
              calendarAnnotations={calendarAnnotations}
              coverages={computedCoverages}
              activeStaff={activeStaff}
              specialties={specialties}
              staffFilterId={staffFilterId}
              specialtyFilterId={specialtyFilterId}
              selectedPeriodId={selectedPeriodId}
              initialYear={initialCalendar.year}
              initialMonth={initialCalendar.month}
              viewMode={viewMode}
              onRefresh={handleRefresh}
              onFocusDate={setFocusDate}
              onAddDate={setAddModalDate}
              onStaffFilterChange={setStaffFilterId}
              onSpecialtyFilterChange={setSpecialtyFilterId}
              onViewDetail={(schedule) => openScheduleDetail(schedule.id)}
              onViewModeChange={(view) => setQueryState({ view })}
            />
          </div>

          <div className="space-y-4">
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
              coverageGaps={conflictData?.coverageGaps ?? []}
              hasCoverageGaps={conflictData?.hasCoverageGaps ?? false}
              totalCoverageGaps={conflictData?.totalCoverageGaps ?? 0}
            />

            <ReviewSnapshotPanel focusDate={focusDate} schedules={focusSchedules} />
          </div>
        </div>
      </ScheduleTabs>

      <QuickAddModal
        date={addModalDate}
        periodId={selectedPeriodId}
        defaultShiftTypeId={selectedTab}
        staffList={activeStaff}
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
        onClose={closeDetail}
      />

      <ConflictResolutionModal
        open={resolvingConflict !== null}
        onClose={() => setResolvingConflict(null)}
        conflict={resolvingConflict}
        onRefresh={handleConflictRefresh}
      />
    </WorkflowShell>
  );
}
