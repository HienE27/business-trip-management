"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useRouter, usePathname, useSearchParams } from "next/navigation";
import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/Skeleton";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { BackButton } from "@/components/ui/BackButton";

const ApplyConfirmationModal = dynamic(
  () => import("./ApplyConfirmationModal").then((m) => m.ApplyConfirmationModal),
  { loading: () => null },
);
const SaveTemplateModal = dynamic(
  () => import("./SaveTemplateModal").then((m) => m.SaveTemplateModal),
  { loading: () => null },
);
const SuggestionsModal = dynamic(
  () => import("./SuggestionsModal").then((m) => m.SuggestionsModal),
  { loading: () => null },
);
const ApplyTemplateModal = dynamic(
  () => import("./ApplyTemplateModal").then((m) => m.ApplyTemplateModal),
  { loading: () => null },
);
const PreviewEditModal = dynamic(
  () => import("@/components/auto-scheduling/PreviewEditModal").then((m) => m.PreviewEditModal),
  { loading: () => null },
);
const BulkPublishModal = dynamic(
  () => import("./BulkPublishModal").then((m) => m.BulkPublishModal),
  { loading: () => null },
);
const AutoSchedulingWizard = dynamic(
  () => import("@/components/auto-scheduling/AutoSchedulingWizard").then((m) => m.AutoSchedulingWizard),
  { loading: () => null },
);

// Heavy chart/panel components — code-split so they don't block initial paint
const WorkloadChart = dynamic(
  () => import("@/components/auto-scheduling/WorkloadChart").then((m) => m.WorkloadChart),
  { loading: () => <Skeleton className="h-64 rounded-xl" /> },
);
const AutoSchedulePanel = dynamic(
  () => import("@/components/monthly-schedule/AutoSchedulePanel").then((m) => m.AutoSchedulePanel),
  { loading: () => <Skeleton className="h-96 rounded-xl" /> },
);
const StaffExclusionTable = dynamic(
  () => import("@/components/auto-scheduling/StaffExclusionTable").then((m) => m.StaffExclusionTable),
  { loading: () => <Skeleton className="h-48 rounded-xl" /> },
);

import { useAutoSchedule } from "@/hooks/useAutoSchedule";
import { useRole, canManage } from "@/hooks/useRole";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { RuntimeParamsChips } from "@/components/auto-scheduling/RuntimeParamsChips";
import { CollapsibleCard } from "@/components/ui/CollapsibleCard";
import type { SchedulePeriod, Staff, ReplacementSuggestion, ScheduleTemplate, TemplatePreviewItem } from "@/types/api";

function PageHeaderSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-20 w-full rounded-xl" />
      <Skeleton className="h-16 w-full rounded-xl" />
      <Skeleton className="h-64 w-full rounded-xl" />
    </div>
  );
}

export default function AutoSchedulingPage() {
  const role = useRole();
  const isManager = canManage(role);
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [excludedStaffIds, setExcludedStaffIds] = useState<number[]>([]);
  const [autoGenEnabled, setAutoGenEnabled] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadMessage, setLoadMessage] = useState<string | null>(null);
  const [applyModalOpen, setApplyModalOpen] = useState(false);
  const [suggestionsModalOpen, setSuggestionsModalOpen] = useState(false);
  const [suggestionsData, setSuggestionsData] = useState<ReplacementSuggestion | null>(null);

  const [autoState, autoActions] = useAutoSchedule();
  const { previewResult, editedPreview, removedShiftTypes, applying, running, message, algorithmType } = autoState;
  const { runPreview, applyPreview, saveAsTemplate, previewTemplate, applyTemplateWithEdits, editShiftType, resetEdits, clearPreview, setMessage, setAlgorithmType } = autoActions;
  const [saveModalOpen, setSaveModalOpen] = useState(false);
  const [templateName, setTemplateName] = useState("");
  const [templateDesc, setTemplateDesc] = useState("");
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [templates, setTemplates] = useState<ScheduleTemplate[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [applyTemplateModalOpen, setApplyTemplateModalOpen] = useState(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [templatePreview, setTemplatePreview] = useState<TemplatePreviewItem[] | null>(null);
  const [bulkModalOpen, setBulkModalOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [editingStaffIds, setEditingStaffIds] = useState<Map<string | number, number>>(new Map());
  const [previewEditItem, setPreviewEditItem] = useState<import("@/types/api").AutoScheduleSummary | null>(null);
  const [showWizard, setShowWizard] = useState(false);

  const loadWorkspace = useCallback(async () => {
    try {
      setLoading(true);
      const [periodData, staffData] = await Promise.all([
        api.get<SchedulePeriod[]>("/periods"),
        api.get<Staff[]>("/staff/active"),
      ]);
      const list = periodData ?? [];
      setPeriods(list);
      setActiveStaff(staffData ?? []);

      // Priority 1: URL param periodId
      const urlPeriodId = searchParams ? parseInt(searchParams.get("periodId") ?? "", 10) : NaN;
      if (!isNaN(urlPeriodId) && list.some((p) => p.id === urlPeriodId)) {
        setSelectedPeriodId(urlPeriodId);
      } else {
        // Priority 2: DRAFT period
        const draft = list.find((p) => p.status === "DRAFT") ?? list[0] ?? null;
        setSelectedPeriodId(draft?.id ?? null);
      }
    } catch (err) {
      setLoadMessage("Không thể tải dữ liệu workspace. Vui lòng thử lại.");
      console.warn("[AutoSchedule] loadWorkspace failed:", err);
    } finally {
      setLoading(false);
    }
  }, [searchParams]);

  const loadAutoGenStatus = useCallback(async () => {
    // Only ADMIN/MANAGER need to see the auto-gen-disabled warning. The endpoint is
    // ADMIN-only on PUT, so for STAFF we skip the call entirely.
    if (!isManager) {
      setAutoGenEnabled(null);
      return;
    }
    try {
      const res = await api.getAutoGenConfig();
      const payload = (res as unknown as { data?: { enabled?: boolean } } | null)?.data;
      setAutoGenEnabled(typeof payload?.enabled === "boolean" ? payload.enabled : null);
    } catch {
      // Endpoint may be unavailable (e.g. user lost role mid-session) — silent fail.
      setAutoGenEnabled(null);
    }
  }, [isManager]);

  useEffect(() => { void loadWorkspace(); }, [loadWorkspace]);
  useEffect(() => { void loadAutoGenStatus(); }, [loadAutoGenStatus]);

  // Refresh auto-gen status when the user comes back to this tab (e.g. after toggling
  // the switch in /algorithm-config). Avoids stale "disabled" warnings.
  useEffect(() => {
    function onFocus() { void loadAutoGenStatus(); }
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [loadAutoGenStatus]);

  // Reset preview when period changes
  useEffect(() => { clearPreview(); }, [selectedPeriodId, clearPreview]);

  // Sync selected period to URL
  useEffect(() => {
    if (selectedPeriodId === null) return;
    const current = searchParams.get("periodId");
    const next = String(selectedPeriodId);
    if (current !== next) {
      const params = new URLSearchParams(searchParams.toString());
      params.set("periodId", next);
      router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    }
  }, [selectedPeriodId, router, pathname, searchParams]);

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId) ?? null;

  const handleRunPreview = () => {
    if (!selectedPeriodId) return;
    void runPreview(selectedPeriodId, excludedStaffIds);
  };

  const handleApplyPreview = async () => {
    if (!previewResult || !selectedPeriodId) return;
    const removedKeys = new Set(removedShiftTypes);
    const editedKeys = new Set(editedPreview.map((s) => `${s.workDate}_${s.shiftTypeId}`));
    const baseSchedules = previewResult.schedules
      .map((s) => ({
        workDate: s.workDate,
        shiftTypeId: s.shiftTypeId,
        staffId: s.staffId,
      }))
      .filter((s) => !removedKeys.has(`${s.workDate}_${s.shiftTypeId}_${s.staffId}`))
      .filter((s) => !editedKeys.has(`${s.workDate}_${s.shiftTypeId}`));
    const merged: Array<{ workDate: string; shiftTypeId: string; staffId: number }> = [
      ...baseSchedules,
      ...editedPreview,
    ];
    await applyPreview(selectedPeriodId, merged, () => {
      setApplyModalOpen(false);
      void loadWorkspace();
    });
  };

  const handleResetEdits = () => {
    resetEdits();
  };

  const handleLoadTemplates = async () => {
    try {
      setLoadingTemplates(true);
      const data = await api.get<ScheduleTemplate[]>("/schedule-templates/active");
      setTemplates(data ?? []);
      setSelectedTemplateId(null);
      setTemplatePreview(null);
    } catch {
      setTemplates([]);
    } finally {
      setLoadingTemplates(false);
    }
  };

  const handlePreviewTemplate = async (templateId: number) => {
    if (!selectedPeriodId) return;
    setSelectedTemplateId(templateId);
    setPreviewLoading(true);
    setTemplatePreview(null);
    try {
      const data = await previewTemplate(templateId, selectedPeriodId);
      setTemplatePreview(data ?? []);
    } catch {
      setTemplatePreview(null);
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleApplyTemplateConfirmed = async () => {
    if (!selectedTemplateId || !selectedPeriodId) return;
    try {
      const edits = Array.from(editingStaffIds.entries())
        .filter(([, staffId]) => staffId !== 0)
        .map(([slotId, staffId]) => {
          const slotIdNum = typeof slotId === "string" && /^\d+$/.test(slotId) ? Number(slotId) : 0;
          return { slotId: slotIdNum, assignedStaffId: staffId };
        });
      await applyTemplateWithEdits(selectedTemplateId, selectedPeriodId, edits);
      setApplyTemplateModalOpen(false);
      setTemplates([]);
      setTemplatePreview(null);
      setSelectedTemplateId(null);
      setEditingStaffIds(new Map());
      void loadWorkspace();
    } catch (error) {
      setMessage(getErrorMessage(error, "Không thể áp dụng mẫu lịch."));
    }
  };

  const handleStaffEdit = (slotId: string | number, staffId: number) => {
    setEditingStaffIds((prev) => {
      const next = new Map<string | number, number>(prev);
      next.set(slotId, staffId);
      return next;
    });
  };

  const openApplyTemplateModal = async () => {
    await handleLoadTemplates();
    setApplyTemplateModalOpen(true);
  };

  if (loading) {
    return <PageHeaderSkeleton />;
  }

  return (
    <div className="space-y-4">
      <BackButton href="/dashboard" variant="full" label="Quay lại" className="mb-2" />

      {loadMessage && (
        <div className="rounded-xl border border-error-container bg-error-container/20 px-4 py-3 text-label-sm text-error flex items-start gap-3">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-error-container">
            <span className="material-symbols-outlined text-[18px] text-error" aria-hidden="true">warning</span>
          </div>
          <div>
            <p className="font-semibold">Lỗi tải dữ liệu</p>
            <p className="text-on-surface-variant">{loadMessage}</p>
          </div>
        </div>
      )}

      {/* Warning: auto-generation is disabled in algorithm-config.
          Without it, the scheduler cannot generate shift requirements and `Chạy` will fail. */}
      {autoGenEnabled === false && (
        <div
          role="alert"
          className="rounded-xl border border-tertiary-container bg-tertiary-container/10 px-4 py-3 text-label-sm text-on-surface flex items-start gap-3"
        >
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-tertiary-container">
            <span
              className="material-symbols-outlined text-[18px] text-on-tertiary-container"
              aria-hidden="true"
              style={{ fontVariationSettings: "'FILL' 1" }}
            >auto_mode</span>
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-on-surface">Tự động tạo yêu cầu ca trực đang tắt</p>
            <p className="text-on-surface-variant">
              Thuật toán không thể sinh yêu cầu nhân sự khi chưa bật <code className="px-1 py-0.5 rounded bg-surface-container text-on-surface">auto_gen_enabled</code> trong cấu hình. Bấm <strong>Chạy</strong> sẽ thất bại với lỗi <em>Cấu hình auto-gen chưa được bật</em>.
            </p>
          </div>
          <Link
            href="/auto-scheduling/algorithm-config"
            className="shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-on-primary font-semibold hover:bg-primary-container transition-colors"
          >
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">tune</span>
            Đi tới Cấu hình
          </Link>
        </div>
      )}

      {/* Page header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-headline-lg font-bold text-on-surface flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-fixed">
              <span className="material-symbols-outlined text-[28px] text-primary" aria-hidden="true">auto_mode</span>
            </div>
            Xếp lịch tự động
          </h1>
          <p className="text-label-sm text-on-surface-variant mt-1 ml-15">Tự động phân bổ ca trực với thuật toán tối ưu</p>
        </div>
      </div>

      {/* Header controls card */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
        <div className="flex flex-col gap-4 p-4">
          {/* Row 1: Period selector + Action buttons */}
          <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4">
            {/* Left: Period selector */}
            <div className="flex flex-wrap items-center gap-3">
              {/* Period selector */}
              <div className="relative">
                <label htmlFor="auto-period-select" className="sr-only">Kỳ xếp lịch</label>
                <select
                  id="auto-period-select"
                  className="h-8 rounded-lg border border-outline-variant bg-surface-container-low px-3 pr-9 text-label-sm text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors"
                  value={selectedPeriodId ?? ""}
                  onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
                >
                  {periods.map((p) => (
                    <option key={p.id} value={p.id}>{p.periodName}</option>
                  ))}
                </select>
                <span className="material-symbols-outlined pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[16px]" aria-hidden="true">expand_more</span>
              </div>
              {selectedPeriod && (
                <>
                  <Badge tone={selectedPeriod.status === "DRAFT" ? "info" : "success"} dot size="sm">
                    {selectedPeriod.status === "DRAFT" ? "Nháp" : "Đã công bố"}
                  </Badge>
                  <span className="text-[11px] text-on-surface-variant flex items-center gap-1.5 px-2 py-1 rounded-md bg-surface-container-low border border-outline-variant/50">
                    <span className="material-symbols-outlined text-[14px]">groups</span>
                    <span className="font-semibold tabular-nums">{activeStaff.length}</span> nhân sự
                  </span>
                  {selectedPeriod.startDate && selectedPeriod.endDate && (
                    <span className="text-[11px] text-on-surface-variant hidden sm:flex items-center gap-1.5 px-2 py-1 rounded-md bg-surface-container-low border border-outline-variant/50">
                      <span className="material-symbols-outlined text-[14px]">date_range</span>
                      {new Date(selectedPeriod.startDate).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" })}
                      {" → "}
                      {new Date(selectedPeriod.endDate).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" })}
                    </span>
                  )}
                </>
              )}
            </div>

            {/* Right: Action links */}
            <div className="flex flex-wrap items-center gap-2">
              {isManager && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setShowWizard(true)}
                  icon={<span className="material-symbols-outlined text-[16px]">menu_book</span>}
                  className="whitespace-nowrap"
                >
                  Hướng dẫn
                </Button>
              )}
              <Link href="/auto-scheduling/algorithm-config">
                <Button variant="secondary" size="sm" icon={<span className="material-symbols-outlined text-[16px]">tune</span>} className="whitespace-nowrap">
                  Cấu hình
                </Button>
              </Link>
              <Link href="/auto-scheduling/history">
                <Button variant="secondary" size="sm" icon={<span className="material-symbols-outlined text-[16px]">history</span>} className="whitespace-nowrap">
                  Lịch sử
                </Button>
              </Link>
              <Link href="/monthly-schedule">
                <Button variant="secondary" size="sm" icon={<span className="material-symbols-outlined text-[16px]">calendar_month</span>} className="whitespace-nowrap">
                  Lịch trực
                </Button>
              </Link>
              {isManager && (
                <Button
                  variant="primary"
                  size="sm"
                  icon={<span className="material-symbols-outlined text-[16px]">bolt</span>}
                  onClick={() => setBulkModalOpen(true)}
                  className="whitespace-nowrap"
                >
                  Công bố hàng loạt
                </Button>
              )}
            </div>
          </div>

          {/* Row 2: Runtime params — full width strip below */}
          <div className="pt-3 border-t border-outline-variant/60">
            <RuntimeParamsChips compact />
          </div>
        </div>
      </div>

      {/* Main AutoScheduling panel */}
      {!isManager ? (
        <div className="rounded-xl border border-tertiary-container bg-tertiary-container/10 p-6 flex items-center gap-4">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-tertiary-container">
            <span className="material-symbols-outlined text-[24px] text-tertiary" aria-hidden="true">lock</span>
          </div>
          <div>
            <p className="text-title-sm font-semibold text-on-surface">Không có quyền xếp lịch</p>
            <p className="text-label-sm text-on-surface-variant mt-0.5">Chỉ <strong>Quản lý</strong> hoặc <strong>Admin</strong> mới có quyền chạy tự động xếp lịch.</p>
          </div>
        </div>
      ) : (
        <AutoSchedulePanel
          previewResult={previewResult}
          editedPreview={editedPreview}
          activeStaff={activeStaff}
          applyingPreview={applying}
          runningAutoSchedule={running}
          message={message}
          algorithmType={algorithmType}
          selectedPeriod={selectedPeriod}
          selectedPeriodId={selectedPeriodId}
          selectedPeriodStatus={selectedPeriod?.status}
          onPreview={handleRunPreview}
          onApplyPreview={() => setApplyModalOpen(true)}
          onResetEdits={handleResetEdits}
          onEditPreviewItem={(item) => setPreviewEditItem(item)}
          onSetAlgorithmType={setAlgorithmType}
          isManager={isManager}
          onSaveTemplate={() => setSaveModalOpen(true)}
          onApplyTemplate={openApplyTemplateModal}
        />
      )}

      {/* Staff exclusions — collapsible */}
      <CollapsibleCard
        title="Ngoại lệ nhân sự"
        subtitle="Loại trừ nhân sự khỏi lịch tự động"
        icon="block"
        summary={
          <div className="flex items-center gap-2">
            <Badge tone="success" size="sm">
              <span className="material-symbols-outlined text-[12px]">group</span>
              {activeStaff.length - excludedStaffIds.length} tham gia
            </Badge>
            {excludedStaffIds.length > 0 && (
              <Badge tone="error" size="sm">
                <span className="material-symbols-outlined text-[12px]">group_remove</span>
                {excludedStaffIds.length} loại trừ
              </Badge>
            )}
          </div>
        }
      >
        <StaffExclusionTable
          staff={activeStaff}
          excludedIds={excludedStaffIds}
          onExclusionsChange={setExcludedStaffIds}
          loading={loading}
        />
      </CollapsibleCard>

      {/* Charts + History — only when preview exists */}
      {previewResult && (
        <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden hover:shadow-md transition-shadow">
          <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-secondary-container">
              <span className="material-symbols-outlined text-[16px] text-on-secondary-container" aria-hidden="true">group</span>
            </div>
            <div className="min-w-0">
              <p className="text-title-sm font-semibold text-on-surface">Khối lượng theo nhân sự</p>
              <p className="text-label-xs text-on-surface-variant">Theo ca · theo loại · cân bằng</p>
            </div>
          </div>
          <div className="p-4">
            <WorkloadChart periodId={selectedPeriodId!} previewSchedules={previewResult?.schedules} />
          </div>
        </div>
      )}

      <ApplyConfirmationModal
        open={applyModalOpen}
        onClose={() => setApplyModalOpen(false)}
        previewResult={previewResult}
        editedPreview={editedPreview}
        removedShiftTypes={removedShiftTypes}
        applying={applying}
        onApply={handleApplyPreview}
      />

      <SaveTemplateModal
        open={saveModalOpen}
        onClose={() => { setSaveModalOpen(false); setTemplateName(""); setTemplateDesc(""); }}
        templateName={templateName}
        templateDesc={templateDesc}
        onTemplateNameChange={setTemplateName}
        onTemplateDescChange={setTemplateDesc}
        savingTemplate={savingTemplate}
        selectedPeriod={selectedPeriod}
        algorithmType={algorithmType}
        scheduleCount={previewResult?.totalSchedulesCreated ?? 0}
        onSave={async () => {
          if (!templateName.trim()) return;
          setSavingTemplate(true);
          await saveAsTemplate(selectedPeriodId, templateName.trim(), templateDesc.trim());
          setSavingTemplate(false);
          setSaveModalOpen(false);
          setTemplateName("");
          setTemplateDesc("");
        }}
      />

      <SuggestionsModal
        open={suggestionsModalOpen}
        onClose={() => { setSuggestionsModalOpen(false); setSuggestionsData(null); }}
        suggestionsData={suggestionsData}
        loading={false}
      />

      <ApplyTemplateModal
        open={applyTemplateModalOpen}
        templates={templates}
        loadingTemplates={loadingTemplates}
        selectedTemplateId={selectedTemplateId}
        selectedTemplate={selectedTemplateId ? templates.find(t => t.id === selectedTemplateId) ?? null : null}
        templatePreview={templatePreview}
        previewLoading={previewLoading}
        editingStaffIds={editingStaffIds}
        activeStaff={activeStaff}
        onClose={() => { setApplyTemplateModalOpen(false); setTemplates([]); setTemplatePreview(null); setSelectedTemplateId(null); }}
        onPreview={handlePreviewTemplate}
        onApply={handleApplyTemplateConfirmed}
        onSelectTemplate={(id) => { setSelectedTemplateId(id); setTemplatePreview(null); }}
        onStaffEdit={handleStaffEdit}
        onClearSelection={() => { setSelectedTemplateId(null); setTemplatePreview(null); }}
      />

      <BulkPublishModal
        open={bulkModalOpen}
        periods={periods}
        onClose={() => { setBulkModalOpen(false); }}
        onRefresh={loadWorkspace}
      />

      <PreviewEditModal
        open={previewEditItem !== null}
        onClose={() => setPreviewEditItem(null)}
        item={previewEditItem}
        staffList={activeStaff}
        shiftTypes={[
          { id: "L01", name: "Trực 24/24" },
          { id: "L02", name: "Lịch thông tầm" },
          { id: "L03", name: "Phòng khám dịch vụ" },
          { id: "L04", name: "Phòng khám chuyên gia" },
        ]}
        onSave={(workDate, shiftTypeId, staffId) => {
          if (previewEditItem) {
            editShiftType(workDate, previewEditItem.shiftTypeId, shiftTypeId, staffId);
          }
          setPreviewEditItem(null);
        }}
      />

      {showWizard && isManager && (
        <AutoSchedulingWizard
          periods={periods}
          activeStaff={activeStaff}
            onComplete={() => {
            setShowWizard(false);
            void loadWorkspace();
          }}
          onSkip={() => setShowWizard(false)}
        />
      )}
    </div>
  );
}
