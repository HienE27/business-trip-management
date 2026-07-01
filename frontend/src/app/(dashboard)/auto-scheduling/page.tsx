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

// Heavy chart/panel components — code-split so they don't block initial paint
const WorkloadChart = dynamic(
  () => import("@/components/auto-scheduling/WorkloadChart").then((m) => m.WorkloadChart),
  { loading: () => <Skeleton className="h-64 rounded-xl" /> },
);
const AlgorithmBalanceChart = dynamic(
  () => import("@/components/auto-scheduling/AlgorithmBalanceChart").then((m) => m.AlgorithmBalanceChart),
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
import type { SchedulePeriod, Staff, AlgorithmMetrics, ReplacementSuggestion, AutoScheduleSummary, ScheduleTemplate, TemplatePreviewItem } from "@/types/api";
import { parseNumber, formatCoverageRate, formatPercent } from "@/lib/number-utils";

function MetricsHistorySection({ periodId }: { periodId: number | null }) {
  const [metrics, setMetrics] = useState<AlgorithmMetrics[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!periodId) return;
    let ignore = false;
    setLoading(true);
    api.getMetricsByPeriod(periodId)
      .then((data) => { if (!ignore && data) setMetrics(data as AlgorithmMetrics[]); })
      .catch(() => { if (!ignore) setMetrics([]); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [periodId]);

  if (loading) return <Skeleton className="h-20 rounded-xl" />;
  if (!metrics.length) {
    return (
      <div className="py-8 text-center">
        <p className="text-label-sm text-on-surface-variant">
          Chưa có lịch sử chạy thuật toán cho kỳ này.
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto max-h-48">
      <table className="w-full text-left border-collapse" aria-label="Lịch sử thuật toán">
        <thead className="bg-surface-container-low border-b border-outline-variant sticky top-0">
          <tr>
            <th scope="col" className="p-3 text-label-xs text-on-surface-variant uppercase">Thuật toán</th>
            <th scope="col" className="p-3 text-label-xs text-on-surface-variant uppercase">Tổng ca</th>
            <th scope="col" className="p-3 text-label-xs text-on-surface-variant uppercase">Thời gian</th>
            <th scope="col" className="p-3 text-label-xs text-on-surface-variant uppercase">Tỷ lệ phủ</th>
            <th scope="col" className="p-3 text-label-xs text-on-surface-variant uppercase">Cân bằng</th>
            <th scope="col" className="p-3 text-label-xs text-on-surface-variant uppercase">Xung đột</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant/30">
          {metrics.map((m) => (
            <tr key={m.id} className="hover:bg-surface-container-low transition-colors">
              <td className="p-3 text-label-sm text-primary font-semibold">{m.algorithmType}</td>
              <td className="p-3 text-label-sm text-on-surface font-semibold">{m.totalSchedulesCreated ?? 0}</td>
              <td className="p-3 text-label-sm text-on-surface">{m.executionTimeMs}ms</td>
              <td className="p-3 text-label-sm text-on-surface">{formatCoverageRate(m.coverageRate)}</td>
              <td className="p-3 text-label-sm text-on-surface">{formatPercent(m.balanceScore, 1)}</td>
              <td className="p-3 text-label-sm">
                <span className={m.conflictCount > 0 ? "text-error font-semibold" : "text-secondary"}>
                  {m.conflictCount}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

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
  const [loading, setLoading] = useState(true);
  const [loadMessage, setLoadMessage] = useState<string | null>(null);
  const [applyModalOpen, setApplyModalOpen] = useState(false);
  const [suggestionsModalOpen, setSuggestionsModalOpen] = useState(false);
  const [suggestionsData, setSuggestionsData] = useState<ReplacementSuggestion | null>(null);
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);

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

  useEffect(() => { void loadWorkspace(); }, [loadWorkspace]);

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
    if (!previewResult) return;
    const merged: Array<{ workDate: string; shiftTypeId: string; staffId: number }> = [
      ...previewResult.schedules.map((s) => ({
        workDate: s.workDate,
        shiftTypeId: s.shiftTypeId,
        staffId: s.staffId,
      })),
      ...editedPreview,
    ];
    await applyPreview(selectedPeriodId, merged, () => {
      setApplyModalOpen(false);
      void loadWorkspace();
    });
  };

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const _handleSuggestReplacement = async (schedule: AutoScheduleSummary) => {
    if (!schedule.scheduleId) return;
    setSuggestionsData(null);
    setSuggestionsLoading(true);
    setSuggestionsModalOpen(true);
    try {
      const data = await api.suggestReplacements(schedule.scheduleId);
      setSuggestionsData(data);
    } catch {
      setSuggestionsData(null);
    } finally {
      setSuggestionsLoading(false);
    }
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
        <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4 p-4">
          {/* Left: Period selector */}
          <div className="flex flex-wrap items-center gap-3">
            {/* Period selector */}
            <div className="relative">
              <label htmlFor="auto-period-select" className="sr-only">Kỳ xếp lịch</label>
              <select
                id="auto-period-select"
                className="h-11 rounded-lg border-2 border-outline-variant bg-surface-container-low px-4 pr-10 text-label-sm text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-colors"
                value={selectedPeriodId ?? ""}
                onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
              >
                {periods.map((p) => (
                  <option key={p.id} value={p.id}>{p.periodName}</option>
                ))}
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[18px]" aria-hidden="true">expand_more</span>
            </div>
            {selectedPeriod && (
              <Badge tone={selectedPeriod.status === "DRAFT" ? "info" : "success"} dot size="sm">
                {selectedPeriod.status === "DRAFT" ? "Nháp" : "Đã công bố"}
              </Badge>
            )}
            <RuntimeParamsChips compact />
          </div>

          {/* Right: Action links */}
          <div className="flex items-center gap-2">
            <Link href="/auto-scheduling/algorithm-config">
              <Button variant="secondary" size="sm" icon={<span className="material-symbols-outlined text-[16px]">tune</span>}>
                Cấu hình
              </Button>
            </Link>
            <Link href="/auto-scheduling/history">
              <Button variant="secondary" size="sm" icon={<span className="material-symbols-outlined text-[16px]">history</span>}>
                Lịch sử
              </Button>
            </Link>
            <Link href="/monthly-schedule">
              <Button variant="secondary" size="sm" icon={<span className="material-symbols-outlined text-[16px]">calendar_month</span>}>
                Lịch trực
              </Button>
            </Link>
            {isManager && (
              <Button
                variant="primary"
                size="sm"
                icon={<span className="material-symbols-outlined text-[16px]">bolt</span>}
                onClick={() => setBulkModalOpen(true)}
              >
                Công bố hàng loạt
              </Button>
            )}
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

      {/* Staff exclusions */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
        <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-fixed">
            <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">block</span>
          </div>
          <div>
            <p className="text-title-sm font-semibold text-on-surface">Ngoại lệ nhân sự</p>
            <p className="text-label-xs text-on-surface-variant">Loại trừ nhân sự khỏi lịch tự động</p>
          </div>
        </div>
        <StaffExclusionTable
          staff={activeStaff}
          excludedIds={excludedStaffIds}
          onExclusionsChange={setExcludedStaffIds}
          loading={loading}
        />
      </div>

      {/* Charts + History — only when preview exists */}
      {previewResult && (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden hover:shadow-md transition-shadow">
              <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center gap-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary-fixed">
                  <span className="material-symbols-outlined text-[16px] text-primary" aria-hidden="true">balance</span>
                </div>
                <p className="text-title-sm font-semibold text-on-surface">Biểu đồ cân bằng</p>
              </div>
              <div className="p-4">
                <AlgorithmBalanceChart schedules={previewResult.schedules} />
              </div>
            </div>

            <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden hover:shadow-md transition-shadow">
              <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center gap-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-secondary-container">
                  <span className="material-symbols-outlined text-[16px] text-secondary" aria-hidden="true">group</span>
                </div>
                <p className="text-title-sm font-semibold text-on-surface">Khối lượng theo nhân sự</p>
              </div>
              <div className="p-4">
                <WorkloadChart periodId={selectedPeriodId!} previewSchedules={previewResult?.schedules} />
              </div>
            </div>
          </div>

          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
            <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low flex items-center gap-3">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-tertiary-container">
                <span className="material-symbols-outlined text-[16px] text-tertiary" aria-hidden="true">history</span>
              </div>
              <p className="text-title-sm font-semibold text-on-surface">Lịch sử thuật toán</p>
            </div>
            <div className="p-4">
              <MetricsHistorySection periodId={selectedPeriodId} />
            </div>
          </div>
        </>
      )}

      <ApplyConfirmationModal
        open={applyModalOpen}
        onClose={() => setApplyModalOpen(false)}
        selectedPeriod={selectedPeriod}
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
        loading={suggestionsLoading}
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
    </div>
  );
}
