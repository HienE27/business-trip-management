"use client";

import { useCallback, useEffect, useState } from "react";
import { SectionCard } from "@/components/ui/SectionCard";
import { Skeleton } from "@/components/ui/Skeleton";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorBoundary } from "@/components/ErrorBoundary";
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

function AddShiftModal({
  open,
  onClose,
  shiftTypes,
  staffList,
  selectedPeriod,
  onAdd,
}: {
  open: boolean;
  onClose: () => void;
  shiftTypes: ShiftType[];
  staffList: Staff[];
  selectedPeriod: SchedulePeriod | null;
  onAdd: (shift: AutoScheduleSummary) => void;
}) {
  const [shiftDate, setShiftDate] = useState("");
  const [shiftTypeId, setShiftTypeId] = useState("");
  const [staffId, setStaffId] = useState<number | "">("");

  const shiftTypeName = shiftTypes.find((t) => t.id === shiftTypeId)?.name ?? "";
  const staffName = staffList.find((s) => s.id === staffId)?.fullName ?? "";

  const isValid = shiftDate && shiftTypeId && staffId !== "";

  const handleSubmit = () => {
    if (!isValid) return;
    onAdd({
      workDate: shiftDate,
      shiftTypeId,
      shiftTypeName,
      staffId,
      staffName,
      scheduleId: null,
    });
    setShiftDate("");
    setShiftTypeId("");
    setStaffId("");
    onClose();
  };

  const handleClose = () => {
    setShiftDate("");
    setShiftTypeId("");
    setStaffId("");
    onClose();
  };

  if (!open) return null;

  return (
    <Modal open={open} onClose={handleClose} title="Thêm ca trực mới">
      <div className="space-y-4">
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="shift-date">
            Ngày làm việc <span className="text-error">*</span>
          </label>
          <input
            id="shift-date"
            type="date"
            className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
            value={shiftDate}
            min={selectedPeriod?.startDate ?? ""}
            max={selectedPeriod?.endDate ?? ""}
            onChange={(e) => setShiftDate(e.target.value)}
          />
        </div>
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="shift-type">
            Loại lịch <span className="text-error">*</span>
          </label>
          <div className="relative">
            <select
              id="shift-type"
              className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-low text-label-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
              value={shiftTypeId}
              onChange={(e) => setShiftTypeId(e.target.value)}
            >
              <option value="">-- Chọn loại lịch --</option>
              {shiftTypes.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[18px] pointer-events-none">expand_more</span>
          </div>
        </div>
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="shift-staff">
            Nhân sự <span className="text-error">*</span>
          </label>
          <div className="relative">
            <select
              id="shift-staff"
              className="w-full h-10 pl-3 pr-8 border border-outline-variant bg-surface-container-low text-label-md text-on-surface appearance-none focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all cursor-pointer rounded-lg"
              value={staffId}
              onChange={(e) => setStaffId(Number(e.target.value))}
            >
              <option value="">-- Chọn nhân sự --</option>
              {staffList.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.fullName}
                </option>
              ))}
            </select>
            <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[18px] pointer-events-none">expand_more</span>
          </div>
        </div>
      </div>
      <ModalFooter>
        <button
          type="button"
          onClick={handleClose}
          className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
        >
          Hủy
        </button>
        <button
          type="button"
          onClick={handleSubmit}
          disabled={!isValid}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors"
        >
          <span className="material-symbols-outlined text-[16px]">add</span>
          Thêm ca trực
        </button>
      </ModalFooter>
    </Modal>
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
      hookSetMessage("Không thể xem trước mẫu lịch. Mẫu GENERATED không hỗ trợ xem trước.");
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
    <ErrorBoundary>
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

            {/* Workload chart — shows saved schedule workload */}
            <SectionCard
              title="Khối lượng theo nhân sự"
              description="Biểu đồ phân bổ ca trực theo nhân sự trong kỳ lịch."
            >
              <div className="p-4">
                <WorkloadChart periodId={selectedPeriodId!} />
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

      {/* Apply Confirmation Modal */}
      <Modal
        open={applyModalOpen}
        onClose={() => setApplyModalOpen(false)}
        title="Xác nhận áp dụng phương án"
      >
        <p className="text-body-sm text-on-surface">
          Phương án phân công sẽ được <strong>ghi đè</strong> lên lịch hiện tại của kỳ{" "}
          <strong>{selectedPeriod?.periodName}</strong>. Hành động này không thể hoàn tác.
        </p>
        {previewResult && (
          <div className="mt-3 p-3 bg-surface-container-low rounded-lg text-label-sm text-on-surface-variant space-y-1">
            <p>Tổng ca: <strong className="text-on-surface">{previewResult.totalSchedulesCreated}</strong></p>
            <p>Tỷ lệ phủ: <strong className="text-on-surface">{Math.round(previewResult.coverageRate * 100)}%</strong></p>
            <p>Xung đột: <strong className="text-error">{previewResult.conflictCount}</strong></p>
            {editedPreview.length > 0 && (
              <p className="text-primary">Có <strong>{editedPreview.length}</strong> ca đã chỉnh sửa thủ công.</p>
            )}
            {removedShiftTypes.size > 0 && (
              <p className="text-primary">Có <strong>{removedShiftTypes.size}</strong> ca đổi loại lịch.</p>
            )}
          </div>
        )}
        <ModalFooter>
          <button
            type="button"
            onClick={() => setApplyModalOpen(false)}
            className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={handleApplyPreview}
            disabled={applying}
            className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors"
          >
            <span className="material-symbols-outlined text-[16px]">check</span>
            {applying ? "Đang áp dụng..." : "Xác nhận áp dụng"}
          </button>
        </ModalFooter>
      </Modal>

      {/* Add Shift Modal */}
      <AddShiftModal
        open={addShiftModalOpen}
        onClose={() => setAddShiftModalOpen(false)}
        shiftTypes={shiftTypes}
        staffList={activeStaff}
        selectedPeriod={selectedPeriod}
        onAdd={handleAddShift}
      />

      {/* Save Template Modal */}
      <Modal
        open={saveModalOpen}
        onClose={() => setSaveModalOpen(false)}
        title="Lưu mẫu lịch"
        description="Lưu phương án hiện tại thành mẫu để tái sử dụng cho các kỳ lịch sau."
      >
        <div className="space-y-4">
          <div>
            <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="tmpl-name">
              Tên mẫu lịch <span className="text-error">*</span>
            </label>
            <input
              id="tmpl-name"
              type="text"
              className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
              placeholder="VD: Mẫu lịch tháng 6/2026"
              value={templateName}
              onChange={(e) => setTemplateName(e.target.value)}
            />
          </div>
          <div>
            <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="tmpl-desc">
              Mô tả
            </label>
            <textarea
              id="tmpl-desc"
              className="w-full resize-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-label-md text-on-surface transition-all focus:border-primary focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20"
              rows={2}
              placeholder="Ghi chú về mẫu lịch (VD: dùng cho tháng có ngày lễ)..."
              value={templateDesc}
              onChange={(e) => setTemplateDesc(e.target.value)}
            />
          </div>
          <div className="p-3 bg-surface-container-low rounded-lg text-label-sm text-on-surface-variant space-y-1">
            <p><strong className="text-on-surface">Kỳ lịch gốc:</strong> {selectedPeriod?.periodName}</p>
            <p><strong className="text-on-surface">Thuật toán:</strong> {algorithmType}</p>
            <p><strong className="text-on-surface">Số ca:</strong> {previewResult?.totalSchedulesCreated ?? 0}</p>
          </div>
        </div>
        <ModalFooter>
          <button
            type="button"
            onClick={() => setSaveModalOpen(false)}
            className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={async () => {
              if (!templateName.trim()) return;
              setSavingTemplate(true);
              await saveAsTemplate(selectedPeriodId, templateName.trim(), templateDesc.trim());
              setSavingTemplate(false);
              setSaveModalOpen(false);
              setTemplateName("");
              setTemplateDesc("");
            }}
            disabled={!templateName.trim() || savingTemplate}
            className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors"
          >
            <span className="material-symbols-outlined text-[16px]">bookmark_add</span>
            {savingTemplate ? "Đang lưu..." : "Lưu mẫu lịch"}
          </button>
        </ModalFooter>
      </Modal>

      {/* Suggestions Modal */}
      <Modal
        open={suggestionsModalOpen}
        onClose={() => { setSuggestionsModalOpen(false); setSuggestionsData(null); }}
        title="Đề xuất người thay thế"
        description={suggestionsData ? `Lịch ${suggestionsData.shiftTypeName} ngày ${formatDate(suggestionsData.workDate)} — ${suggestionsData.originalStaffName}` : undefined}
      >
        {suggestionsLoading ? (
          <div className="py-8 text-center text-label-sm text-on-surface-variant">Đang tải...</div>
        ) : suggestionsData ? (
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {suggestionsData.suggestions.length === 0 ? (
              <EmptyState
                size="compact"
                icon="person_off"
                title="Không có người thay thế phù hợp"
                description="Hệ thống không tìm thấy nhân sự khả dụng với cùng chuyên môn và rảnh trong ngày."
              />
            ) : (
              suggestionsData.suggestions.map((s) => (
                <div
                  key={s.staffId}
                  className={`flex items-center gap-3 p-3 rounded-lg border transition-colors ${
                    s.isAvailable
                      ? "bg-surface-container-lowest border-outline-variant"
                      : "bg-surface-container-low border-outline opacity-60"
                  }`}
                >
                  <span className="material-symbols-outlined text-[18px] text-primary shrink-0">
                    {s.isAvailable ? "person" : "person_off"}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-label-md font-semibold text-on-surface truncate">{s.staffName}</p>
                    <p className="text-label-sm text-on-surface-variant">
                      {s.specialty ?? "—"} · <strong>{s.currentWorkload}</strong> ca trong kỳ
                      {s.conflicts.length > 0 && (
                        <span className="text-error"> · {s.conflicts.join(", ")}</span>
                      )}
                    </p>
                    {!s.isAvailable && s.reason && (
                      <p className="text-label-sm text-error mt-0.5">{s.reason}</p>
                    )}
                  </div>
                  <span className={`text-label-sm font-semibold shrink-0 ${
                    s.isAvailable ? "text-secondary" : "text-outline"
                  }`}>
                    {s.isAvailable ? "Có thể thay" : "Không khả dụng"}
                  </span>
                </div>
              ))
            )}
          </div>
        ) : (
          <p className="text-label-sm text-on-surface-variant text-center py-4">Không có dữ liệu.</p>
        )}
        <ModalFooter>
          <button
            type="button"
            onClick={() => { setSuggestionsModalOpen(false); setSuggestionsData(null); }}
            className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
          >
            Đóng
          </button>
        </ModalFooter>
      </Modal>

      {/* Apply Template Modal */}
      <Modal
        open={applyTemplateModalOpen}
        onClose={() => { setApplyTemplateModalOpen(false); setTemplates([]); setTemplatePreview(null); setSelectedTemplateId(null); }}
        title="Áp dụng mẫu lịch"
        description="Chọn mẫu lịch đã lưu để xem trước và chỉnh sửa trước khi áp dụng cho kỳ hiện tại."
      >
        {!selectedTemplateId ? (
          <div className="space-y-3">
            {loadingTemplates ? (
              <p className="text-label-sm text-on-surface-variant text-center py-6">Đang tải mẫu lịch...</p>
            ) : templates.length === 0 ? (
              <EmptyState
                size="compact"
                icon="bookmarks"
                title="Chưa có mẫu lịch nào"
                description="Chạy auto schedule trước rồi lưu mẫu để có thể áp dụng lại sau."
              />
            ) : (
              <div className="space-y-2 max-h-64 overflow-y-auto">
                {templates.map((t) => (
                  <div
                    key={t.id}
                    className="flex items-center justify-between gap-3 p-3 rounded-lg border border-outline-variant bg-surface-container-lowest hover:bg-surface-container-low transition-colors"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="text-label-md font-semibold text-on-surface truncate">{t.name}</p>
                      {t.description && (
                        <p className="text-label-sm text-on-surface-variant truncate">{t.description}</p>
                      )}
                      <p className="text-[11px] text-outline mt-0.5">
                        {new Date(t.createdAt).toLocaleDateString("vi-VN")}
                        {t.shiftTypeId ? " · " + t.shiftTypeId + (t.specialtyName ? " · " + t.specialtyName : "") : ""}
                        {" · " + (t.requiredStaffCount ?? 1) + " người/ca"}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        type="button"
                        onClick={() => handlePreviewTemplate(t.id)}
                        disabled={previewLoading}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface px-3 py-1.5 text-label-sm font-medium text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-50"
                      >
                        <span className="material-symbols-outlined text-[14px]">visibility</span>
                        Xem trước
                      </button>
                      <button
                        type="button"
                        onClick={handleApplyTemplateConfirmed}
                        className="shrink-0 inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-label-sm font-medium text-on-primary hover:bg-primary/90 transition-colors"
                      >
                        <span className="material-symbols-outlined text-[14px]">check</span>
                        Áp dụng
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : previewLoading ? (
          <div className="py-8 text-center text-label-sm text-on-surface-variant">
            <div className="inline-block size-6 animate-spin rounded-full border-2 border-primary border-t-transparent mr-2" />
            Đang tải bản xem trước...
          </div>
        ) : templatePreview === null ? (
          <div className="space-y-3">
            <p className="text-label-sm text-error">Không thể xem trước mẫu này (có thể là mẫu GENERATED).</p>
            <button
              type="button"
              onClick={handleApplyTemplateConfirmed}
              className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-label-sm font-semibold text-on-primary hover:bg-primary/90 transition-colors"
            >
              <span className="material-symbols-outlined text-[14px]">check</span>
              Áp dụng trực tiếp
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-label-sm text-on-surface-variant">
              Mẫu này sẽ tạo <strong className="text-on-surface">{templatePreview.length}</strong> yêu cầu nhân sự trong kỳ.
              Bạn có thể sửa nhân sự được phân công cho từng ca trước khi xác nhận.
            </p>
            <div className="overflow-x-auto max-h-80">
              <table className="w-full text-left border-collapse">
                <thead className="sticky top-0 bg-surface-container-low border-b border-outline-variant">
                  <tr>
                    <th className="p-2.5 text-label-xs text-on-surface-variant uppercase">Ngày</th>
                    <th className="p-2.5 text-label-xs text-on-surface-variant uppercase">Thứ</th>
                    <th className="p-2.5 text-label-xs text-on-surface-variant uppercase">Loại ca</th>
                    <th className="p-2.5 text-label-xs text-on-surface-variant uppercase">Chuyên khoa</th>
                    <th className="p-2.5 text-label-xs text-on-surface-variant uppercase">Số người</th>
                    <th className="p-2.5 text-label-xs text-on-surface-variant uppercase">Nhân sự phân công</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/30">
                  {templatePreview.map((item) => {
                    const currentStaffId = editingStaffIds.get(item.id) ?? item.assignedStaffId ?? 0;
                    return (
                      <tr key={item.id} className="hover:bg-surface-container-low/50 transition-colors">
                        <td className="p-2.5 text-label-sm text-on-surface">{formatDate(item.workDate)}</td>
                        <td className="p-2.5 text-label-sm text-on-surface-variant">{item.dayOfWeek}</td>
                        <td className="p-2.5 text-label-sm text-on-surface-variant">{item.shiftTypeName}</td>
                        <td className="p-2.5 text-label-sm text-on-surface-variant">{item.specialtyName ?? "—"}</td>
                        <td className="p-2.5 text-label-sm text-on-surface-variant text-center">{item.requiredStaffCount}</td>
                        <td className="p-2.5">
                          <div className="relative">
                            <select
                              className="h-8 w-full appearance-none rounded-md border border-outline-variant bg-surface-container-lowest px-2 pr-7 text-label-sm text-on-surface outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary/20"
                              value={currentStaffId}
                              onChange={(e) => handleStaffEdit(item.id, Number(e.target.value))}
                            >
                              <option value={0}>-- Chưa phân công --</option>
                              {activeStaff.map((s) => (
                                <option key={s.id} value={s.id}>{s.fullName}</option>
                              ))}
                            </select>
                            <span className="material-symbols-outlined pointer-events-none absolute right-1 top-1/2 -translate-y-1/2 text-outline text-[14px]">expand_more</span>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <p className="text-label-xs text-on-surface-variant italic">
              Lưu ý: Việc phân công nhân sự ở đây chỉ là tham khảo. Sau khi áp dụng, hệ thống sẽ tự động tạo yêu cầu nhân sự theo mẫu.
            </p>
          </div>
        )}
        <ModalFooter>
          {selectedTemplateId && templatePreview && (
            <>
              <button
                type="button"
                onClick={() => { setSelectedTemplateId(null); setTemplatePreview(null); }}
                className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
              >
                ← Quay lại danh sách
              </button>
              <button
                type="button"
                onClick={handleApplyTemplateConfirmed}
                disabled={previewLoading}
                className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors"
              >
                <span className="material-symbols-outlined text-[16px]">check</span>
                Xác nhận áp dụng
              </button>
            </>
          )}
          {!selectedTemplateId && (
            <button
              type="button"
              onClick={() => { setApplyTemplateModalOpen(false); setTemplates([]); setTemplatePreview(null); setSelectedTemplateId(null); }}
              className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
            >
              Đóng
            </button>
          )}
        </ModalFooter>
      </Modal>

      {/* Bulk Publish / Archive Modal */}
      <Modal
        open={bulkModalOpen}
        onClose={() => { setBulkModalOpen(false); setBulkResults(null); setBulkSelectedIds(new Set()); }}
        title={bulkOperation === "publish" ? "Công bố hàng loạt kỳ lịch" : "Lưu trữ hàng loạt kỳ lịch"}
        size="lg"
      >
        {!bulkResults ? (
          <>
            <div className="space-y-4">
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => { setBulkOperation("publish"); setBulkSelectedIds(new Set()); }}
                  className={`flex-1 py-2 rounded-lg text-label-md font-medium transition-colors border ${
                    bulkOperation === "publish"
                      ? "border-primary bg-primary-fixed/20 text-primary"
                      : "border-outline-variant text-on-surface-variant hover:border-primary/40"
                  }`}
                >
                  <span className="material-symbols-outlined text-[16px] align-middle mr-1">publish</span>
                  Công bố hàng loạt
                </button>
                <button
                  type="button"
                  onClick={() => { setBulkOperation("archive"); setBulkSelectedIds(new Set()); }}
                  className={`flex-1 py-2 rounded-lg text-label-md font-medium transition-colors border ${
                    bulkOperation === "archive"
                      ? "border-secondary bg-secondary-container/20 text-on-secondary-container"
                      : "border-outline-variant text-on-surface-variant hover:border-secondary/40"
                  }`}
                >
                  <span className="material-symbols-outlined text-[16px] align-middle mr-1">archive</span>
                  Lưu trữ hàng loạt
                </button>
              </div>

              <div>
                <p className="text-label-sm text-on-surface-variant mb-2">
                  {bulkOperation === "publish"
                    ? "Chọn các kỳ lịch ở trạng thái Nháp để công bố:"
                    : "Chọn các kỳ lịch ở trạng thái Đã công bố để lưu trữ:"}
                </p>
                <div className="border border-outline-variant rounded-lg overflow-hidden max-h-64 overflow-y-auto">
                  {periods
                    .filter((p) =>
                      bulkOperation === "publish" ? p.status === "DRAFT" : p.status === "PUBLISHED"
                    )
                    .map((p) => (
                      <label
                        key={p.id}
                        className="flex items-center gap-3 px-4 py-3 hover:bg-surface-container-low cursor-pointer border-b border-outline-variant last:border-b-0"
                      >
                        <input
                          type="checkbox"
                          checked={bulkSelectedIds.has(p.id)}
                          onChange={(e) => {
                            setBulkSelectedIds((prev) => {
                              const next = new Set(prev);
                              if (e.target.checked) next.add(p.id);
                              else next.delete(p.id);
                              return next;
                            });
                          }}
                          className="w-4 h-4 accent-primary"
                        />
                        <div className="flex-1">
                          <p className="text-label-md text-on-surface font-medium">{p.periodName}</p>
                          <p className="text-[11px] text-on-surface-variant">
                            {formatDate(p.startDate)} – {formatDate(p.endDate)}
                          </p>
                        </div>
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                          p.status === "DRAFT" ? "bg-primary-fixed text-primary" : "bg-secondary-container text-on-secondary-container"
                        }`}>
                          {p.status === "DRAFT" ? "Nháp" : "Đã công bố"}
                        </span>
                      </label>
                    ))}
                  {periods.filter((p) =>
                    bulkOperation === "publish" ? p.status === "DRAFT" : p.status === "PUBLISHED"
                  ).length === 0 && (
                    <EmptyState
                      size="compact"
                      icon={bulkOperation === "publish" ? "publish" : "archive"}
                      title="Không có kỳ lịch phù hợp"
                      description={
                        bulkOperation === "publish"
                          ? "Tất cả kỳ lịch đã được công bố hoặc lưu trữ."
                          : "Chưa có kỳ lịch nào ở trạng thái đã công bố."
                      }
                    />
                  )}
                </div>
                {bulkSelectedIds.size > 0 && (
                  <p className="text-label-sm text-on-surface-variant mt-2">
                    Đã chọn <strong>{bulkSelectedIds.size}</strong> kỳ lịch.
                  </p>
                )}
              </div>
            </div>
            <ModalFooter>
              <button
                type="button"
                onClick={() => { setBulkModalOpen(false); setBulkSelectedIds(new Set()); }}
                className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
              >
                Đóng
              </button>
              <button
                type="button"
                disabled={bulkSelectedIds.size === 0 || bulkSubmitting}
                onClick={async () => {
                  const ids = [...bulkSelectedIds];
                  setBulkSubmitting(true);
                  try {
                    const res = bulkOperation === "publish"
                      ? await api.bulkPublishPeriods(ids)
                      : await api.bulkArchivePeriods(ids);
                    if (res.success && res.data) {
                      setBulkResults({
                        success: res.data.successCount,
                        failure: res.data.failureCount,
                        results: res.data.results.map((r) => ({
                          id: r.id,
                          periodName: r.periodName ?? `Kỳ #${r.id}`,
                          success: r.success,
                          message: r.message,
                        })),
                      });
                      if (res.data.successCount > 0) void loadWorkspace();
                    }
                  } catch (err) {
                    setBulkResults({
                      success: 0,
                      failure: ids.length,
                      results: ids.map((id) => ({ id, periodName: `Kỳ #${id}`, success: false, message: getErrorMessage(err, "Lỗi không xác định") })),
                    });
                  } finally {
                    setBulkSubmitting(false);
                  }
                }}
                className={`inline-flex items-center gap-2 px-5 py-2 rounded-lg text-label-md font-semibold transition-colors disabled:opacity-50 ${
                  bulkOperation === "publish"
                    ? "bg-primary text-on-primary hover:bg-primary/90"
                    : "bg-secondary text-on-secondary hover:bg-secondary/90"
                }`}
              >
                {bulkSubmitting ? (
                  <><div className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" /><span>Đang xử lý...</span></>
                ) : (
                  <><span className="material-symbols-outlined text-[16px]">check</span>
                    {bulkOperation === "publish" ? `Công bố ${bulkSelectedIds.size} kỳ lịch` : `Lưu trữ ${bulkSelectedIds.size} kỳ lịch`}
                  </>
                )}
              </button>
            </ModalFooter>
          </>
        ) : (
          <>
            <div className="space-y-3">
              <div className="flex gap-4">
                <div className="flex-1 rounded-lg border border-secondary-container bg-secondary-container/10 p-3 text-center">
                  <p className="text-display-lg text-secondary font-bold">{bulkResults.success}</p>
                  <p className="text-label-sm text-on-secondary-container">Thành công</p>
                </div>
                <div className="flex-1 rounded-lg border border-error-container bg-error-container/10 p-3 text-center">
                  <p className="text-display-lg text-error font-bold">{bulkResults.failure}</p>
                  <p className="text-label-sm text-on-error-container">Thất bại</p>
                </div>
              </div>
              <div className="border border-outline-variant rounded-lg overflow-hidden max-h-48 overflow-y-auto">
                {bulkResults.results.map((r) => (
                  <div key={r.id} className="flex items-center gap-3 px-4 py-2.5 border-b border-outline-variant last:border-b-0">
                    <span className={`material-symbols-outlined text-[18px] ${r.success ? "text-secondary" : "text-error"}`}>
                      {r.success ? "check_circle" : "error"}
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className="text-label-md text-on-surface truncate">{r.periodName}</p>
                      {!r.success && <p className="text-[11px] text-error truncate">{r.message}</p>}
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <ModalFooter>
              <button
                type="button"
                onClick={() => { setBulkModalOpen(false); setBulkResults(null); setBulkSelectedIds(new Set()); }}
                className="px-4 py-2 rounded-lg bg-primary text-on-primary text-label-md font-semibold hover:bg-primary/90 transition-colors"
              >
                Đóng
              </button>
            </ModalFooter>
          </>
        )}
      </Modal>
    </>
    </ErrorBoundary>
  );
}
