"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { SectionCard } from "@/components/ui/SectionCard";
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
import { formatDate } from "@/lib/date";
import { getErrorMessage } from "@/lib/errors";
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
              <td className="p-2 text-label-sm text-on-surface">{typeof m.coverageRate === 'number' ? `${Math.round(m.coverageRate * 100)}%` : '—'}</td>
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
  const { runPreview, applyPreview, saveAsTemplate, previewTemplate, applyTemplateWithEdits, editStaff, editShiftType, removeShift, resetEdits, clearPreview, setMessage, setAlgorithmType } = autoActions;
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
  const [editingStaffIds, setEditingStaffIds] = useState<Map<number, number>>(new Map());

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
      const next = new Map(prev);
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
    <>
      {loadMessage && (
        <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
          {loadMessage}
        </div>
      )}
      {/* Quick links */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-center gap-2">
          <span className="text-label-sm text-on-surface-variant">Xem nhanh:</span>
          <Link
            href="/auto-scheduling/algorithm-config"
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface hover:bg-surface-container-low hover:border-primary/40 transition-colors"
          >
            <span className="material-symbols-outlined text-[14px]">tune</span>
            Cấu hình thuật toán
          </Link>
          <Link
            href="/auto-scheduling/history"
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface hover:bg-surface-container-low hover:border-primary/40 transition-colors"
          >
            <span className="material-symbols-outlined text-[14px]">history</span>
            Lịch sử chạy
          </Link>
        </div>
      </div>

      {/* Period Selector */}
      <SectionCard
        title="Kỳ lịch"
        description="Chọn kỳ lịch cần xếp tự động. Chỉ kỳ ở trạng thái Nháp mới có thể chỉnh sửa."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <label htmlFor="auto-period-select" className="sr-only">Kỳ xếp lịch</label>
              <select
                id="auto-period-select"
                className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md text-on-surface appearance-none cursor-pointer focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 min-w-[200px]"
                value={selectedPeriodId ?? ""}
                onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
              >
                {periods.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.periodName}
                  </option>
                ))}
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[18px]">expand_more</span>
            </div>
            {selectedPeriod && (
              <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold ${
                selectedPeriod.status === "DRAFT"
                  ? "bg-primary-fixed text-primary"
                  : "bg-secondary-container text-on-secondary-container"
              }`}>
                {selectedPeriod.status === "DRAFT" ? "Nháp" : "Đã công bố"}
              </span>
            )}
            {isManager && (
              <button
                type="button"
                onClick={() => setBulkModalOpen(true)}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-on-primary text-[11px] font-semibold hover:bg-primary/90 transition-colors"
                title="Công bố hàng loạt nhiều kỳ lịch"
              >
                <span className="material-symbols-outlined text-[14px]">bolt</span>
                Công bố hàng loạt
              </button>
            )}
          </div>
        }
      >
        {selectedPeriod ? (
          <div className="p-4 bg-surface-container-low rounded-lg space-y-2">
            <p className="text-body-sm text-on-surface">
              <span className="font-semibold">Thời gian:</span>{" "}
              {formatDate(selectedPeriod.startDate)} –{" "}
              {formatDate(selectedPeriod.endDate)}
            </p>
            {selectedPeriod.status !== "DRAFT" && (
              <p className="mt-1 text-label-sm text-error flex items-center gap-1">
                <span className="material-symbols-outlined text-[14px]">warning</span>
                Kỳ lịch đã công bố — chỉ có thể xem, không chỉnh sửa.
              </p>
            )}
          </div>
        ) : (
          <p className="text-body-sm text-on-surface-variant">Chưa có kỳ lịch nào.</p>
        )}
      </SectionCard>

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
          conflictKeys={new Set()}
          onPreview={handleRunPreview}
          onApplyPreview={() => setApplyModalOpen(true)}
          onResetEdits={handleResetEdits}
          onEditStaff={(workDate, shiftTypeId, staffId) => editStaff(workDate, shiftTypeId, staffId)}
          onSetAlgorithmType={setAlgorithmType}
          isManager={isManager}
          onSaveTemplate={() => setSaveModalOpen(true)}
          onApplyTemplate={openApplyTemplateModal}
        />
      )}

      {/* Staff exclusions — collapsible after panel */}
      <SectionCard
        title="Ngoại lệ nhân sự"
        description="Loại trừ nhân sự khỏi lịch tự động (đi công tác, nghỉ dài ngày)."
      >
        <StaffExclusionTable
          staff={activeStaff}
          excludedIds={excludedStaffIds}
          onExclusionsChange={setExcludedStaffIds}
          loading={loading}
        />
      </SectionCard>

      {/* Charts + History — only when preview exists */}
      {previewResult && (
        <div className="space-y-4">
          <div className="grid gap-4 lg:grid-cols-2">
            <SectionCard title="Biểu đồ cân bằng" description="Phân bổ ca trực theo thuật toán.">
              <div className="p-4">
                <AlgorithmBalanceChart schedules={previewResult.schedules} />
              </div>
            </SectionCard>

            <SectionCard title="Khối lượng theo nhân sự" description="Biểu đồ phân bổ ca trực theo nhân sự trong kỳ lịch.">
              <div className="p-4">
                <WorkloadChart periodId={selectedPeriodId!} previewSchedules={previewResult?.schedules} />
              </div>
            </SectionCard>
          </div>

          <SectionCard title="Lịch sử chạy thuật toán" description="Các lần chạy trước đó cho kỳ lịch này.">
            <MetricsHistorySection periodId={selectedPeriodId} />
          </SectionCard>
        </div>
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
        onStaffEdit={handleStaffEdit}
        onClearSelection={() => { setSelectedTemplateId(null); setTemplatePreview(null); }}
      />

      <BulkPublishModal
        open={bulkModalOpen}
        periods={periods}
        onClose={() => { setBulkModalOpen(false); }}
        onRefresh={loadWorkspace}
      />
    </>
  );
}
