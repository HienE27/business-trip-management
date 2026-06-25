"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/Skeleton";

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
      <p className="text-label-sm text-on-surface-variant text-center py-4">
        Chưa có lịch sử chạy thuật toán cho kỳ này.
      </p>
    );
  }

  return (
    <div className="overflow-x-auto max-h-48">
      <table className="w-full text-left border-collapse" aria-label="Page Table">
        <thead className="bg-surface-container-low border-b border-outline-variant sticky top-0">
          <tr>
            <th scope="col" className="p-2 text-label-xs text-on-surface-variant uppercase">Thuật toán</th>
            <th scope="col" className="p-2 text-label-xs text-on-surface-variant uppercase">Thời gian</th>
            <th scope="col" className="p-2 text-label-xs text-on-surface-variant uppercase">Tỷ lệ phủ</th>
            <th scope="col" className="p-2 text-label-xs text-on-surface-variant uppercase">Cân bằng</th>
            <th scope="col" className="p-2 text-label-xs text-on-surface-variant uppercase">Xung đột</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant/30">
          {metrics.map((m) => (
            <tr key={m.id} className="hover:bg-surface transition-colors">
              <td className="p-2 text-label-sm text-primary font-semibold">{m.algorithmType}</td>
              <td className="p-2 text-label-sm text-on-surface">{m.executionTimeMs}ms</td>
              <td className="p-2 text-label-sm text-on-surface">{typeof m.coverageRate === 'number' ? `${Math.round(m.coverageRate)}%` : '—'}</td>
              <td className="p-2 text-label-sm text-on-surface">{typeof m.balanceScore === 'number' ? m.balanceScore.toFixed(2) : '—'}</td>
              <td className="p-2 text-label-sm">
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

export default function AutoSchedulingPage() {
  const role = useRole();
  const isManager = canManage(role);
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
  const { previewResult, editedPreview, removedShifts, removedShiftTypes, applying, running, message, algorithmType } = autoState;
  const { runPreview, applyPreview, saveAsTemplate, previewTemplate, applyTemplateWithEdits, editShiftType, resetEdits, clearPreview, setMessage, setAlgorithmType } = autoActions;
  const [autoGenReq, setAutoGenReq] = useState(true);
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
      const draft = list.find((p) => p.status === "DRAFT") ?? list[0] ?? null;
      setSelectedPeriodId(draft?.id ?? null);
    } catch (err) {
      setLoadMessage("Không thể tải dữ liệu workspace. Vui lòng thử lại.");
      console.warn("[AutoSchedule] loadWorkspace failed:", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadWorkspace(); }, [loadWorkspace]);

  // Reset preview when period changes
  useEffect(() => { clearPreview(); }, [selectedPeriodId, clearPreview]);

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId) ?? null;
  const canRun = isManager && selectedPeriodId !== null && !running;

  const handleRunPreview = () => {
    if (!selectedPeriodId) return;
    void runPreview(selectedPeriodId, excludedStaffIds, autoGenReq);
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

  const handleSuggestReplacement = async (schedule: AutoScheduleSummary) => {
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
      // Build the merged edits from the template preview table.
      // Only include items where the user changed the staff assignment.
      // slotId may be a composite string for PATTERN templates; convert to number if numeric.
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
    return (
      <>
        <div className="space-y-4">
          <Skeleton className="h-32 rounded-xl" />
          <Skeleton className="h-64 rounded-xl" />
        </div>
      </>
    );
  }

  return (
    <div className="space-y-4">
      {loadMessage && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error flex items-start gap-2">
          <span className="material-symbols-outlined text-[18px] shrink-0">error</span>
          {loadMessage}
        </div>
      )}

      {/* Header row: title + period selector + quick links */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <h1 className="sr-only">Xếp lịch tự động</h1>
        <div className="flex items-center gap-2 flex-wrap">
          {/* Period selector */}
          <div className="relative">
            <label htmlFor="auto-period-select" className="sr-only">Kỳ xếp lịch</label>
            <select
              id="auto-period-select"
              className="h-8 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 pr-7 text-label-sm text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20"
              value={selectedPeriodId ?? ""}
              onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
            >
              {periods.map((p) => (
                <option key={p.id} value={p.id}>{p.periodName}</option>
              ))}
            </select>
            <span className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[14px]">expand_more</span>
          </div>
          {selectedPeriod && (
            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold ${selectedPeriod.status === "DRAFT" ? "bg-primary-fixed text-primary" : "bg-secondary-container text-on-secondary-container"}`}>
              {selectedPeriod.status === "DRAFT" ? "Nháp" : "Đã công bố"}
            </span>
          )}
          {/* Quick links */}
          <RuntimeParamsChips compact />
          <Link href="/auto-scheduling/algorithm-config" className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-xs text-on-surface hover:border-primary/40 transition-colors">
            <span className="material-symbols-outlined text-[13px]">tune</span> Cấu hình
          </Link>
          <Link href="/auto-scheduling/history" className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-xs text-on-surface hover:border-primary/40 transition-colors">
            <span className="material-symbols-outlined text-[13px]">history</span> Lịch sử
          </Link>
          <Link href="/monthly-schedule" className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest text-label-xs text-on-surface hover:border-primary/40 transition-colors">
            <span className="material-symbols-outlined text-[13px]">calendar_month</span> Lịch trực
          </Link>
          {isManager && (
            <button type="button" onClick={() => setBulkModalOpen(true)}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-primary text-label-xs font-semibold text-on-primary hover:bg-primary/90 transition-colors cursor-pointer"
              title="Công bố hàng loạt">
              <span className="material-symbols-outlined text-[13px]">bolt</span>
            </button>
          )}
        </div>
      </div>

      {/* Main AutoScheduling panel — contains algorithm, rules, exclusions, KPI, table, actions */}
      {!isManager ? (
        <div className="rounded-xl border border-tertiary/30 bg-tertiary/5 p-5 flex items-center gap-3">
          <span className="material-symbols-outlined text-tertiary text-[22px]">lock</span>
          <p className="text-body-sm text-on-surface">
            Chỉ <strong>Quản lý</strong> hoặc <strong>Admin</strong> mới có quyền chạy tự động xếp lịch.
          </p>
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
          autoGenerateRequirements={autoGenReq}
          onSetAutoGenerateRequirements={setAutoGenReq}
          isManager={isManager}
          onSaveTemplate={() => setSaveModalOpen(true)}
          onApplyTemplate={openApplyTemplateModal}
        />
      )}

      {/* Staff exclusions */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        <div className="px-4 py-2.5 border-b border-outline-variant bg-surface-container-low flex items-center gap-2">
          <span className="material-symbols-outlined text-[16px] text-on-surface-variant" aria-hidden="true">block</span>
          <p className="text-label-sm font-semibold text-on-surface">Ngoại lệ nhân sự</p>
          <span className="text-[11px] text-on-surface-variant">Loại trừ nhân sự khỏi lịch tự động</span>
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
        <div className="grid gap-3 lg:grid-cols-2">
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
            <div className="px-3 py-2 border-b border-outline-variant bg-surface-container-low">
              <p className="text-label-sm font-semibold text-on-surface">Biểu đồ cân bằng</p>
            </div>
            <div className="p-3">
              <AlgorithmBalanceChart schedules={previewResult.schedules} />
            </div>
          </div>

          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
            <div className="px-3 py-2 border-b border-outline-variant bg-surface-container-low">
              <p className="text-label-sm font-semibold text-on-surface">Khối lượng theo nhân sự</p>
            </div>
            <div className="p-3">
              <WorkloadChart periodId={selectedPeriodId!} previewSchedules={previewResult?.schedules} />
            </div>
          </div>
        </div>
      )}

      {previewResult && <MetricsHistorySection periodId={selectedPeriodId} />}

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
