"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { SectionCard } from "@/components/ui/SectionCard";
import { Skeleton, SkeletonCalendar, SkeletonKPI, SkeletonTable } from "@/components/ui/Skeleton";
import { WorkflowStepper } from "@/components/monthly-schedule/WorkflowStepper";
const ConflictResolutionModal = dynamic(
  () => import("@/components/ui/ConflictResolutionModal").then((m) => m.ConflictResolutionModal),
  { loading: () => null },
);
const QuickAddModal = dynamic(
  () => import("@/components/monthly-schedule/QuickAddModal").then((m) => m.QuickAddModal),
  { loading: () => null },
);
const ScheduleMatrixView = dynamic(
  () => import("@/components/dashboard/ScheduleMatrixView").then((m) => m.ScheduleMatrixView),
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
const ExportReportPanel = dynamic(
  () => import("@/components/monthly-schedule/ExportReportPanel").then((m) => m.ExportReportPanel),
  { loading: () => null },
);

import { KPISection } from "@/components/monthly-schedule/KPISection";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/ToastProvider";
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
  const [showPublishDialog, setShowPublishDialog] = useState(false);
  const [localMessage, setLocalMessage] = useState<string | null>(null);
  const [pendingLeaveRequests, setPendingLeaveRequests] = useState(0);
  const [pendingExchanges, setPendingExchanges] = useState(0);

  const selectedPeriod = useMemo(
    () => periods.find((period) => period.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId],
  );

  // Toast hook - hiển thị thông báo nổi cho các action nghiệp vụ
  const { success: toastSuccess, error: toastError, warning: toastWarning, info: toastInfo } = useToast();

  // Watch wsState.message từ hook để fire toast khi backend trả success/error.
  // Lưu ý: Mỗi action handler (handleSendNotifications, handleExport, handlePublish,
  // handleCheckConflicts) đã gọi toast trực tiếp sau khi backend trả success/error.
  // useEffect này chỉ phục vụ các message từ hook mà KHÔNG đi qua các handler trên
  // (ví dụ: refresh workspace, auto-detection từ hook).
  const lastToastedRef = useRef<string | null>(null);
  useEffect(() => {
    if (!message) return;
    if (lastToastedRef.current === message) return;
    lastToastedRef.current = message;
    const lower = message.toLowerCase();
    if (
      lower.includes("đã gửi") ||
      lower.includes("thành công") ||
      lower.includes("đã được công bố") ||
      lower.includes("đã xuất") ||
      lower.includes("đã lưu")
    ) {
      toastSuccess(message, 5000);
    } else if (
      lower.includes("không thể") ||
      lower.includes("thất bại") ||
      lower.includes("lỗi") ||
      lower.includes("không hợp lệ") ||
      lower.includes("xung đột") ||
      lower.includes("failed")
    ) {
      if (lower.includes("xung đột") || lower.includes("conflict")) {
        toastWarning(message, 5000);
      } else {
        toastError(message, 6000);
      }
    }
  }, [message, toastSuccess, toastError, toastWarning]);

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
    focusDate,
    pendingLeaveRequests,
    pendingExchanges,
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

  // Fetch pending exchange requests count
  useEffect(() => {
    api.get<{ pending: number }>("/dashboard/exchange-requests")
      .then((res) => setPendingExchanges(res.pending ?? 0))
      .catch(() => setPendingExchanges(0));
  }, []);

  const handleCheckConflicts = useCallback(async () => {
    setCheckingConflicts(true);
    setLocalMessage(null);
    try {
      await wsActions.checkConflicts();
      toastSuccess("Đã chạy kiểm tra xung đột.", 3500);
    } catch (error) {
      toastError(getErrorMessage(error, "Không thể kiểm tra xung đột."));
    } finally {
      setCheckingConflicts(false);
    }
    setQueryState({ panel: "conflicts" });
  }, [setQueryState, wsActions, toastSuccess, toastError]);

  const handlePublish = useCallback(async () => {
    setPublishing(true);
    setLocalMessage(null);
    try {
      await wsActions.publishPeriod();
      toastSuccess("Đã công bố kỳ lịch thành công.", 5000);
    } catch (error) {
      toastError(getErrorMessage(error, "Không thể công bố kỳ lịch."));
    } finally {
      setPublishing(false);
    }
  }, [wsActions, toastSuccess, toastError]);

  const handleSendNotifications = useCallback(async () => {
    if (!selectedPeriodId) return;
    if (!canManage(role)) return;
    setNotifying(true);
    setLocalMessage(null);
    try {
      await wsActions.sendNotifications();
      setNotified(true);
      const sentCount = activeStaff.length;
      if (sentCount > 0) {
        toastSuccess(`Đã gửi thông báo đến ${sentCount} nhân sự.`, 5000);
      } else {
        toastWarning("Không có nhân sự đang hoạt động để gửi thông báo.", 5000);
      }
    } catch (error) {
      toastError(getErrorMessage(error, "Không thể gửi thông báo."));
    } finally {
      setNotifying(false);
    }
  }, [selectedPeriodId, wsActions, role, activeStaff, toastSuccess, toastError, toastWarning]);

  const handleExport = useCallback(async () => {
    if (!selectedPeriodId) return;
    setExporting(true);
    setLocalMessage(null);
    try {
      const blob = await api.exportScheduleExcel(selectedPeriodId);
      downloadBlob(blob, `lich-cong-tac-${selectedPeriod?.periodName ?? selectedPeriodId}.xlsx`);
      setLocalMessage("Đã xuất file Excel kỳ lịch.");
      toastSuccess("Đã xuất file Excel kỳ lịch.", 4000);
    } catch (error) {
      const msg = getErrorMessage(error, "Không thể xuất file. Vui lòng thử lại.");
      setLocalMessage(msg);
      toastError(msg, 6000);
    } finally {
      setExporting(false);
    }
  }, [selectedPeriod, selectedPeriodId, toastSuccess, toastError]);

  const handleWorkflowStep = useCallback((stepId: WorkflowStepId) => {
    if (stepId === "conflicts") {
      setQueryState({ panel: "conflicts" });
      // Run a fresh check so the user sees the result, not the cached one.
      if (selectedPeriodId) {
        void handleCheckConflicts();
      } else {
        toastWarning("Vui lòng chọn kỳ lịch trước khi kiểm tra xung đột.", 4000);
      }
      return;
    }
    if (stepId === "review") {
      setShowExportPanel(false);
      setShowPublishDialog(false);
      setQueryState({ panel: "overview" });
      toastInfo("Đang hiển thị bảng tổng hợp rà soát.", 2500);
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
    if (stepId === "publish") {
      if (!canManage(role)) {
        setLocalMessage("Bạn không có quyền công bố lịch. Vui lòng liên hệ quản lý.");
        toastError("Bạn không có quyền công bố lịch. Vui lòng liên hệ quản lý.", 5000);
        return;
      }
      if (conflictData?.hasConflicts) {
        setLocalMessage(`Không thể công bố: còn ${conflictData.totalConflicts} xung đột chưa xử lý.`);
        toastWarning(`Không thể công bố: còn ${conflictData.totalConflicts} xung đột chưa xử lý.`, 5000);
        return;
      }
      setShowPublishDialog(true);
      toastInfo("Mở hộp thoại xác nhận công bố kỳ lịch.", 2500);
      return;
    }
    setShowExportPanel(false);
    setShowPublishDialog(false);
    setQueryState({ panel: "summary" });
  }, [
    handleCheckConflicts,
    handleExport,
    handleSendNotifications,
    setQueryState,
    role,
    conflictData,
    selectedPeriodId,
    toastError,
    toastInfo,
    toastWarning,
  ]);

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
          className="h-9 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md text-on-surface focus:border-blue-300 focus:ring-1 focus:ring-blue-300 transition-all cursor-pointer appearance-none"
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
        <ScheduleMatrixView
          schedules={filteredSchedules}
          staffList={activeStaff}
          periodId={selectedPeriodId}
          initialYear={initialCalendar.year}
          initialMonth={initialCalendar.month}
          selectedTab={selectedTab}
          compensationDays={compensationDays}
          onRefresh={handleRefresh}
          onAddClick={handleAddDate}
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
          totalDaysInPeriod={selectedPeriod
            ? Math.ceil((new Date(selectedPeriod.endDate).getTime() - new Date(selectedPeriod.startDate).getTime()) / (1000 * 60 * 60 * 24)) + 1
            : undefined}
        />

        <ReviewSnapshotPanel
          focusDate={focusDate}
          schedules={focusSchedules}
          periodStart={selectedPeriod?.startDate}
          periodEnd={selectedPeriod?.endDate}
          onFocusDateChange={setFocusDate}
        />
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
        onDelete={() => { void wsActions.refreshWorkspace(); closeDetail(); }}
        onRefresh={() => { void wsActions.refreshWorkspace(); }}
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

      <ConfirmDialog
        open={showPublishDialog}
        onClose={() => setShowPublishDialog(false)}
        onConfirm={() => {
          setShowPublishDialog(false);
          void handlePublish();
        }}
        title="Công bố kỳ lịch?"
        description={
          selectedPeriod
            ? `Sau khi công bố, lịch ${selectedPeriod.periodName} sẽ được thông báo đến nhân sự và không thể chỉnh sửa. Bạn có chắc chắn?`
            : "Sau khi công bố, kỳ lịch sẽ được thông báo đến nhân sự và không thể chỉnh sửa. Bạn có chắc chắn?"
        }
        confirmLabel="Công bố"
        cancelLabel="Hủy"
        variant="primary"
        loading={publishing}
      />
    </>
  );
}
