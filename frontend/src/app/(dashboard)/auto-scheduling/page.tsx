"use client";

import { useCallback, useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { SectionCard } from "@/components/ui/SectionCard";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";

const ApplyConfirmationModal = dynamic(
  () => import("./ApplyConfirmationModal").then((m) => m.ApplyConfirmationModal),
  { loading: () => null },
);
const AddShiftModal = dynamic(
  () => import("./AddShiftModal").then((m) => m.AddShiftModal),
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

import { AlgorithmTip } from "@/components/auto-scheduling/AlgorithmTip";
import { BusinessRulesPanel } from "@/components/auto-scheduling/BusinessRulesPanel";
import { StaffExclusionTable } from "@/components/auto-scheduling/StaffExclusionTable";
import { useAutoSchedule } from "@/hooks/useAutoSchedule";
import { useRole, canManage } from "@/hooks/useRole";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/date";
import { getErrorMessage } from "@/lib/errors";
import type { SchedulePeriod, Staff, AlgorithmMetrics, ReplacementSuggestion, AutoScheduleSummary, ShiftType, ScheduleTemplate, TemplatePreviewItem } from "@/types/api";
import { WorkloadChart } from "@/components/auto-scheduling/WorkloadChart";
import { AlgorithmBalanceChart } from "@/components/auto-scheduling/AlgorithmBalanceChart";
import { UnassignedReportCard } from "@/components/auto-scheduling/UnassignedReportCard";

type AlgoType = "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING";

const ALGO_OPTIONS: { id: AlgoType; label: string; desc: string }[] = [
  { id: "GREEDY", label: "Tham lam (Greedy)", desc: "Ưu tiên phủ lịch nhanh, đơn giản." },
  { id: "ROUND_ROBIN", label: "Luân phiên (Round Robin)", desc: "Chia đều số ca, cân bằng tải." },
  { id: "BACKTRACKING", label: "Backtracking", desc: "Tìm kiếm sâu hơn, tối ưu hơn nhưng chậm hơn." },
];

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
      <table className="w-full text-left border-collapse">
        <thead className="bg-surface-container-low border-b border-outline-variant sticky top-0">
          <tr>
            <th className="p-2 text-label-xs text-on-surface-variant uppercase">Thuật toán</th>
            <th className="p-2 text-label-xs text-on-surface-variant uppercase">Thời gian</th>
            <th className="p-2 text-label-xs text-on-surface-variant uppercase">Tỷ lệ phủ</th>
            <th className="p-2 text-label-xs text-on-surface-variant uppercase">Cân bằng</th>
            <th className="p-2 text-label-xs text-on-surface-variant uppercase">Xung đột</th>
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

function EditableScheduleRow({
  schedule,
  allStaff,
  allShiftTypes,
  isEdited,
  isShiftTypeEdited,
  isRemoved,
  isAdded,
  onEdit,
  onShiftTypeChange,
  onRemove,
  onRestore,
  onSuggestReplacement,
}: {
  schedule: AutoScheduleSummary;
  allStaff: Staff[];
  allShiftTypes: ShiftType[];
  isEdited: boolean;
  isShiftTypeEdited?: boolean;
  isRemoved?: boolean;
  isAdded?: boolean;
  onEdit: (staffId: number) => void;
  onShiftTypeChange?: (newShiftTypeId: string) => void;
  onRemove?: () => void;
  onRestore?: () => void;
  onSuggestReplacement?: (schedule: AutoScheduleSummary) => void;
}) {
  const [staffOpen, setStaffOpen] = useState(false);
  const [typeOpen, setTypeOpen] = useState(false);

  return (
    <tr className={`hover:bg-surface transition-colors ${isEdited || isShiftTypeEdited ? "bg-primary-fixed/10" : ""} ${isAdded ? "bg-secondary-fixed/10" : ""} ${isRemoved ? "opacity-40 line-through" : ""}`}>
      <td className="p-3 text-label-sm text-on-surface">{formatDate(schedule.workDate)}</td>
      <td className="p-3">
        {isRemoved ? (
          <span className="text-label-sm text-outline line-through">{schedule.shiftTypeName}</span>
        ) : (
          <div className="relative">
            <button
              type="button"
              className={`inline-flex items-center gap-1 text-label-sm rounded px-2 py-1 border transition-colors ${
                isShiftTypeEdited
                  ? "border-primary bg-primary-fixed/20 text-primary"
                  : "border-transparent text-on-surface hover:bg-surface-container-low"
              }`}
              onClick={() => setTypeOpen((v) => !v)}
            >
              <span>{schedule.shiftTypeName}</span>
              {isShiftTypeEdited && <span className="material-symbols-outlined text-[14px] text-primary">edit</span>}
            </button>
            {typeOpen && onShiftTypeChange && (
              <>
                <div className="fixed inset-0 z-40" onClick={() => setTypeOpen(false)} />
                <div className="absolute left-0 top-full mt-1 z-50 bg-surface-container-lowest border border-outline-variant rounded-lg shadow-lg w-44 max-h-52 overflow-y-auto">
                  {allShiftTypes.map((t) => (
                    <button
                      key={t.id}
                      type="button"
                      className={`w-full text-left px-3 py-2 text-label-sm hover:bg-primary-fixed/20 transition-colors ${
                        t.id === schedule.shiftTypeId ? "bg-primary-fixed/10 text-primary font-semibold" : "text-on-surface"
                      }`}
                      onClick={() => { onShiftTypeChange(t.id); setTypeOpen(false); }}
                    >
                      {t.name}
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
        )}
      </td>
      <td className="p-3 text-label-sm">
        {isRemoved ? (
          <div className="flex items-center gap-2">
            <span className="text-label-sm text-outline line-through">{schedule.staffName}</span>
            {onRestore && (
              <button
                type="button"
                onClick={onRestore}
                className="text-label-sm text-primary hover:underline"
              >
                Hoàn tác
              </button>
            )}
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <div className="relative">
              <button
                type="button"
                className={`inline-flex items-center gap-1.5 text-label-sm rounded px-2 py-1 border transition-colors ${
                  isEdited
                    ? "border-primary bg-primary-fixed/20 text-primary"
                    : "border-transparent text-on-surface hover:bg-surface-container-low"
                }`}
                onClick={() => setStaffOpen((v) => !v)}
              >
                <span>{schedule.staffName}</span>
                {isEdited && <span className="material-symbols-outlined text-[14px] text-primary">edit</span>}
              </button>
              {staffOpen && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setStaffOpen(false)} />
                  <div className="absolute left-0 top-full mt-1 z-50 bg-surface-container-lowest border border-outline-variant rounded-lg shadow-lg w-48 max-h-56 overflow-y-auto">
                    {allStaff.map((s) => (
                      <button
                        key={s.id}
                        type="button"
                        className={`w-full text-left px-3 py-2 text-label-sm hover:bg-primary-fixed/20 transition-colors ${
                          s.id === schedule.staffId ? "bg-primary-fixed/10 text-primary font-semibold" : "text-on-surface"
                        }`}
                        onClick={() => { onEdit(s.id); setStaffOpen(false); }}
                      >
                        {s.fullName}
                      </button>
                    ))}
                  </div>
                </>
              )}
            </div>
            {onSuggestReplacement && schedule.scheduleId && (
              <button
                type="button"
                onClick={() => onSuggestReplacement(schedule)}
                className="inline-flex items-center justify-center w-7 h-7 rounded-full hover:bg-primary-fixed text-on-surface-variant hover:text-primary transition-colors shrink-0"
                title="Đề xuất người thay thế"
                aria-label="Đề xuất người thay thế"
              >
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">swap_horiz</span>
              </button>
            )}
            {onRemove && (
              <button
                type="button"
                onClick={onRemove}
                className="inline-flex items-center justify-center w-7 h-7 rounded-full hover:bg-error-container text-on-surface-variant hover:text-error transition-colors shrink-0"
                title="Xóa ca trực"
                aria-label="Xóa ca trực"
              >
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">delete</span>
              </button>
            )}
          </div>
        )}
      </td>
    </tr>
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
  const [shiftTypes, setShiftTypes] = useState<ShiftType[]>([]);
  const [addShiftModalOpen, setAddShiftModalOpen] = useState(false);
  const [removedShifts, setRemovedShifts] = useState<Set<string>>(new Set());
  const [addedShifts, setAddedShifts] = useState<AutoScheduleSummary[]>([]);

  const [autoState, autoActions] = useAutoSchedule();
  const { previewResult, editedPreview, removedShiftTypes, applying, running, message, algorithmType } = autoState;
  const { runPreview, applyPreview, saveAsTemplate, previewTemplate, applyTemplateWithEdits, editStaff, editShiftType, resetEdits, clearPreview, setMessage: hookSetMessage, setAlgorithmType } = autoActions;
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
  const [bulkSelectedIds, setBulkSelectedIds] = useState<Set<number>>(new Set());
  const [bulkOperation, setBulkOperation] = useState<"publish" | "archive">("publish");
  const [bulkSubmitting, setBulkSubmitting] = useState(false);
  const [bulkResults, setBulkResults] = useState<{ success: number; failure: number; results: Array<{ id: number; periodName: string; success: boolean; message: string }> } | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [editingStaffIds, setEditingStaffIds] = useState<Map<number, number>>(new Map());

  const loadWorkspace = useCallback(async () => {
    try {
      setLoading(true);
      const [periodData, staffData, shiftTypeData] = await Promise.all([
        api.get<SchedulePeriod[]>("/periods"),
        api.get<Staff[]>("/staff/active"),
        api.get<ShiftType[]>("/shift-types"),
      ]);
      const list = periodData ?? [];
      setPeriods(list);
      setActiveStaff(staffData ?? []);
      setShiftTypes(shiftTypeData ?? []);
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
    clearPreview();
    setRemovedShifts(new Set());
    setAddedShifts([]);
    void runPreview(selectedPeriodId);
  };

  const handleApplyPreview = async () => {
    if (!previewResult) return;

    // Build merged list: original minus removed, plus added, plus edited
    const removedKeys = removedShifts;
    const removedShiftTypeKeys = removedShiftTypes;
    const originalSchedules = previewResult.schedules.filter(
      (s) =>
        !removedKeys.has(`${s.workDate}_${s.shiftTypeId}_${s.staffId}`) &&
        !removedShiftTypeKeys.has(`${s.workDate}_${s.shiftTypeId}_${s.staffId}`)
    );
    const merged: Array<{ workDate: string; shiftTypeId: string; staffId: number }> = [
      ...originalSchedules.map((s) => ({
        workDate: s.workDate,
        shiftTypeId: s.shiftTypeId,
        staffId: s.staffId,
      })),
      ...addedShifts.map((s) => ({
        workDate: s.workDate,
        shiftTypeId: s.shiftTypeId,
        staffId: s.staffId,
      })),
      ...editedPreview,
    ];

    await applyPreview(selectedPeriodId, merged, () => {
      setApplyModalOpen(false);
      setRemovedShifts(new Set());
      setAddedShifts([]);
      void loadWorkspace();
    });
  };

  const getRowKey = (schedule: AutoScheduleSummary) =>
    `${schedule.workDate}_${schedule.shiftTypeId}_${schedule.staffId}`;

  const handleRemoveShift = (schedule: AutoScheduleSummary) => {
    const key = getRowKey(schedule);
    setRemovedShifts((prev) => {
      const next = new Set(prev);
      next.add(key);
      return next;
    });
  };

  const handleAddShift = (newShift: AutoScheduleSummary) => {
    setAddedShifts((prev) => [...prev, newShift]);
  };

  const handleRestoreAddedShift = (index: number) => {
    setAddedShifts((prev) => prev.filter((_, i) => i !== index));
  };

  const isRowEdited = (schedule: AutoScheduleSummary) => {
    return editedPreview.some(
      (e) => e.workDate === schedule.workDate && e.shiftTypeId === schedule.shiftTypeId
    );
  };

  const isRowShiftTypeEdited = (schedule: AutoScheduleSummary) => {
    return removedShiftTypes.has(`${schedule.workDate}_${schedule.shiftTypeId}_${schedule.staffId}`);
  };

  const handleRowEdit = (schedule: AutoScheduleSummary, staffId: number) => {
    editStaff(schedule.workDate, schedule.shiftTypeId, staffId);
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
    setRemovedShifts(new Set());
    setAddedShifts([]);
  };

  /**
   * Called when user picks a different shift type for a row in the preview table.
   * The old (date, shiftType, staff) entry is marked removed, and the new one
   * is added to editedPreview so it gets sent as part of the merged list.
   */
  const handleShiftTypeChange = (
    schedule: AutoScheduleSummary,
    newShiftTypeId: string
  ) => {
    const key = `${schedule.workDate}_${schedule.shiftTypeId}_${schedule.staffId}`;
    setRemovedShifts((prev) => {
      const next = new Set(prev);
      next.add(key);
      return next;
    });
    editShiftType(
      schedule.workDate,
      schedule.shiftTypeId,
      newShiftTypeId,
      schedule.staffId
    );
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
      await applyTemplateWithEdits(selectedTemplateId, selectedPeriodId, []);
      setApplyTemplateModalOpen(false);
      setTemplates([]);
      setTemplatePreview(null);
      setSelectedTemplateId(null);
      void loadWorkspace();
    } catch (error) {
      hookSetMessage(getErrorMessage(error, "Không thể áp dụng mẫu lịch."));
    }
  };

  const handleStaffEdit = (slotId: number, staffId: number) => {
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
            {!previewResult && (
              <div className="mt-2">
                <UnassignedReportCard periodId={selectedPeriodId} />
              </div>
            )}
          </div>
        ) : (
          <p className="text-body-sm text-on-surface-variant">Chưa có kỳ lịch nào.</p>
        )}
      </SectionCard>

      {/* Algorithm Config + Rules */}
      <div className="grid gap-4 lg:grid-cols-2">
        <SectionCard
          title="Cấu hình thuật toán"
          description="Chọn thuật toán và giới hạn trước khi chạy."
        >
          <div className="p-4 space-y-4">
            <fieldset>
              <legend className="text-label-md font-semibold text-on-surface mb-2">Thuật toán</legend>
              <div className="space-y-2" role="radiogroup" aria-label="Chọn thuật toán">
                {ALGO_OPTIONS.map((opt) => (
                  <label
                    key={opt.id}
                    className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                      algorithmType === opt.id
                        ? "border-primary bg-primary-fixed/20"
                        : "border-outline-variant hover:border-primary/40"
                    }`}
                  >
                    <input
                      type="radio"
                      className="mt-0.5 accent-primary cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                      name="algo-type"
                      value={opt.id}
                      checked={algorithmType === opt.id}
                      onChange={() => setAlgorithmType(opt.id)}
                    />
                    <div>
                      <p className="text-label-md font-semibold text-on-surface">{opt.label}</p>
                      <p className="text-label-sm text-on-surface-variant">{opt.desc}</p>
                    </div>
                  </label>
                ))}
              </div>
            </fieldset>
          </div>
        </SectionCard>

        <div className="space-y-4">
          <BusinessRulesPanel />
          <AlgorithmTip />
        </div>
      </div>

      {/* Staff Exclusions */}
      <SectionCard
        title="Ngoại lệ nhân sự"
        description="Chọn nhân sự bị loại trừ khỏi lịch tự động (đi công tác, nghỉ dài ngày)."
      >
        <StaffExclusionTable
          staff={activeStaff}
          excludedIds={excludedStaffIds}
          onExclusionsChange={setExcludedStaffIds}
          loading={loading}
        />
      </SectionCard>

      {/* Actions */}
      {!isManager ? (
        <div className="rounded-xl border border-tertiary/30 bg-tertiary/5 p-5 flex items-center gap-3">
          <span className="material-symbols-outlined text-tertiary text-[22px]">lock</span>
          <p className="text-body-sm text-on-surface">
            Chỉ <strong>Quản lý</strong> hoặc <strong>Admin</strong> mới có quyền chạy tự động xếp lịch.
          </p>
        </div>
      ) : (
        <div className="flex items-center gap-3 p-4 bg-surface-container-lowest rounded-xl border border-outline-variant flex-wrap">
          <button
            type="button"
            onClick={handleRunPreview}
            disabled={!canRun}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">auto_mode</span>
            {running ? "Đang chạy..." : "Tự động xếp lịch"}
          </button>
          <button
            type="button"
            onClick={openApplyTemplateModal}
            disabled={!canRun}
            className="inline-flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <span className="material-symbols-outlined text-[18px]">bookmark_added</span>
            Áp dụng mẫu lịch
          </button>
          {previewResult && (
            <>
              <button
                type="button"
                onClick={() => setApplyModalOpen(true)}
                className="inline-flex items-center gap-2 rounded-lg bg-secondary px-5 py-2.5 text-label-md font-semibold text-on-secondary hover:bg-secondary/90 transition-colors"
              >
                <span className="material-symbols-outlined text-[18px]">check</span>
                Áp dụng phương án
              </button>
              <button
                type="button"
                onClick={handleResetEdits}
                className="inline-flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
              >
                Đặt lại
              </button>
              <button
                type="button"
                onClick={() => setSaveModalOpen(true)}
                className="inline-flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
              >
                <span className="material-symbols-outlined text-[18px]">bookmark_add</span>
                Lưu mẫu lịch
              </button>
            </>
          )}
          {message && (
            <span className="text-label-sm text-on-surface-variant ml-auto">{message}</span>
          )}
        </div>
      )}

      {/* Preview Results */}
      {previewResult && (
        <div className="space-y-4">
          {/* Metrics bar */}
          <div className="grid gap-3 grid-cols-2 sm:grid-cols-4">
            {[
              { label: "Phương án", value: previewResult.algorithmType, accent: "bg-primary-fixed text-primary" },
              { label: "Tổng ca tạo", value: previewResult.totalSchedulesCreated, accent: "bg-surface-container-lowest text-on-surface" },
              { label: "Tỷ lệ phủ", value: `${Math.round(previewResult.coverageRate * 100)}%`, accent: "bg-secondary-container text-on-secondary-container" },
              { label: "Xung đột", value: previewResult.conflictCount, accent: previewResult.conflictCount > 0 ? "bg-error-container text-error" : "bg-surface-container-lowest text-outline" },
            ].map((m) => (
              <div key={m.label} className={`rounded-xl border border-outline-variant p-4 ${m.accent}`}>
                <p className="text-label-sm opacity-80 mb-1">{m.label}</p>
                <p className="text-headline-md font-bold">{m.value}</p>
              </div>
            ))}
          </div>

          {/* Unassigned Report (after preview) */}
          <UnassignedReportCard periodId={selectedPeriodId} />

          {/* M07-F09 — algorithm balance chart driven by the in-memory preview */}
          <AlgorithmBalanceChart
            schedules={previewResult.schedules}
          />

          <div className="grid gap-4 lg:grid-cols-2">
            {/* Preview table with inline edit */}
            <SectionCard
              title="Phương án phân công"
              description={`${previewResult.schedules.length} ca được tạo trong ${previewResult.executionTimeMs}ms — nhấn loại lịch hoặc nhân sự để chỉnh sửa`}
              action={
                <div className="flex items-center gap-3 flex-wrap">
                  {(editedPreview.length > 0 || removedShifts.size > 0 || removedShiftTypes.size > 0 || addedShifts.length > 0) && (
                    <span className="text-label-sm text-primary">
                      {editedPreview.length > 0 && <>{editedPreview.length} ca đã sửa</>}
                      {editedPreview.length > 0 && removedShifts.size > 0 && <>, </>}
                      {removedShifts.size > 0 && <>{removedShifts.size} ca đã xóa</>}
                      {(editedPreview.length > 0 || removedShifts.size > 0) && removedShiftTypes.size > 0 && <>, </>}
                      {removedShiftTypes.size > 0 && <>{removedShiftTypes.size} ca đổi loại lịch</>}
                      {(editedPreview.length > 0 || removedShifts.size > 0 || removedShiftTypes.size > 0) && addedShifts.length > 0 && <>, </>}
                      {addedShifts.length > 0 && <>{addedShifts.length} ca thêm mới</>}
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={() => setAddShiftModalOpen(true)}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-label-sm font-semibold text-on-primary hover:bg-primary/90 transition-colors"
                  >
                    <span className="material-symbols-outlined text-[16px]">add</span>
                    Thêm ca trực
                  </button>
                  {(editedPreview.length > 0 || removedShifts.size > 0 || removedShiftTypes.size > 0) && (
                    <button
                      type="button"
                      onClick={handleResetEdits}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface px-3 py-1.5 text-label-sm text-on-surface hover:bg-surface-container-low transition-colors"
                    >
                      <span className="material-symbols-outlined text-[16px]">restart_alt</span>
                      Hoàn tác
                    </button>
                  )}
                </div>
              }
            >
              <div className="overflow-x-auto max-h-80">
                <table className="w-full text-left border-collapse">
                  <thead className="sticky top-0 bg-surface-container-low border-b border-outline-variant">
                    <tr>
                      <th className="p-3 text-label-xs text-on-surface-variant uppercase">Ngày</th>
                      <th className="p-3 text-label-xs text-on-surface-variant uppercase">Loại lịch</th>
                      <th className="p-3 text-label-xs text-on-surface-variant uppercase">Nhân sự</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/30 overflow-y-auto">
                    {previewResult.schedules
                      .filter((s) => {
                        const key = `${s.workDate}_${s.shiftTypeId}_${s.staffId}`;
                        return !removedShifts.has(key) && !removedShiftTypes.has(key);
                      })
                      .slice(0, 50)
                      .map((s) => (
                        <EditableScheduleRow
                          key={`${s.workDate}_${s.shiftTypeId}_${s.staffId}`}
                          schedule={s}
                          allStaff={activeStaff}
                          allShiftTypes={shiftTypes}
                          isEdited={isRowEdited(s)}
                          isShiftTypeEdited={isRowShiftTypeEdited(s)}
                          onEdit={(staffId) => handleRowEdit(s, staffId)}
                          onShiftTypeChange={(newTypeId) => handleShiftTypeChange(s, newTypeId)}
                          onRemove={() => handleRemoveShift(s)}
                          onSuggestReplacement={handleSuggestReplacement}
                        />
                      ))}
                    {addedShifts.map((s, i) => (
                      <EditableScheduleRow
                        key={`added_${i}`}
                        schedule={s}
                        allStaff={activeStaff}
                        allShiftTypes={shiftTypes}
                        isEdited={false}
                        isAdded
                        onEdit={(staffId) => {
                          const updated = [...addedShifts];
                          updated[i] = { ...s, staffId, staffName: activeStaff.find((st) => st.id === staffId)?.fullName ?? s.staffName };
                          setAddedShifts(updated);
                        }}
                        onRemove={() => handleRestoreAddedShift(i)}
                      />
                    ))}
                  </tbody>
                </table>
                {(() => {
                  const visibleOriginal = previewResult.schedules.filter((s) => !removedShifts.has(`${s.workDate}_${s.shiftTypeId}_${s.staffId}`)).length;
                  const totalVisible = visibleOriginal + addedShifts.length;
                  if (previewResult.schedules.length > 50) {
                    return (
                      <p className="text-label-sm text-on-surface-variant text-center py-2 border-t border-outline-variant">
                        Hiển thị {Math.min(50, visibleOriginal)}/{previewResult.schedules.length} ca ({totalVisible} đang hiển thị)
                      </p>
                    );
                  }
                  return null;
                })()}
              </div>
            </SectionCard>

            {/* Workload chart — shows preview workload when available, otherwise saved DB workload */}
            <SectionCard
              title="Khối lượng theo nhân sự"
              description="Biểu đồ phân bổ ca trực theo nhân sự trong kỳ lịch."
            >
              <div className="p-4">
                <WorkloadChart periodId={selectedPeriodId!} previewSchedules={previewResult?.schedules} />
              </div>
            </SectionCard>
          </div>

          {/* Metrics History */}
          <SectionCard
            title="Lịch sử chạy thuật toán"
            description="Các lần chạy trước đó cho kỳ lịch này."
          >
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

      {/* Add Shift Modal */}
      <AddShiftModal
        open={addShiftModalOpen}
        onClose={() => setAddShiftModalOpen(false)}
        shiftTypes={shiftTypes}
        staffList={activeStaff}
        selectedPeriod={selectedPeriod}
        onAdd={handleAddShift}
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
        onClose={() => { setBulkModalOpen(false); setBulkResults(null); setBulkSelectedIds(new Set()); }}
        onRefresh={loadWorkspace}
      />
    </>
  );
}
