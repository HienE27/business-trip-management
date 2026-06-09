"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type {
  AlgorithmMetrics,
  AutoScheduleResult,
  AutoScheduleSummary,
  ReplacementSuggestion,
  Schedule,
  SchedulePeriod,
  ScheduleTemplate,
  ScheduleTemplateRequest,
  Staff,
  UnassignedDayReport,
} from "@/types/api";

const DAY_OPTIONS = [
  { value: 1, label: "Thứ 2" },
  { value: 2, label: "Thứ 3" },
  { value: 3, label: "Thứ 4" },
  { value: 4, label: "Thứ 5" },
  { value: 5, label: "Thứ 6" },
  { value: 6, label: "Thứ 7" },
  { value: 7, label: "Chủ nhật" },
] as const;

const SHIFT_TYPE_OPTIONS = [
  { value: "L01", label: "Lịch trực 24/24" },
  { value: "L02", label: "Lịch thông tầm" },
  { value: "L03", label: "Phòng khám dịch vụ" },
  { value: "L04", label: "Phòng khám chuyên gia" },
] as const;

const ALGORITHM_OPTIONS = [
  { value: "GREEDY", label: "Greedy" },
  { value: "ROUND_ROBIN", label: "Round Robin" },
  { value: "BACKTRACKING", label: "Backtracking" },
] as const;

type WorkloadRow = {
  staffId: number;
  staffName: string;
  specialty: string | null;
  totalShifts: number;
  L01: number;
  L02: number;
  L03: number;
  L04: number;
  workloadPercentage: number;
};

type WorkloadChartData = {
  periodId: number;
  periodName: string;
  totalSchedules: number;
  totalStaff: number;
  averageWorkload: number;
  minWorkload: number;
  maxWorkload: number;
  staffWorkloadData: WorkloadRow[];
};

type TemplateFormState = ScheduleTemplateRequest;

const DEFAULT_TEMPLATE_FORM: TemplateFormState = {
  name: "",
  description: "",
  dayOfWeek: 1,
  shiftTypeId: "L01",
  specialtyId: null,
  requiredStaffCount: 1,
};

function formatDate(date: string) {
  return new Date(date).toLocaleDateString("vi-VN");
}

function getStatusTone(message: string) {
  return message.includes("thành công") || message.includes("hoàn tất") || message.includes("xem trước")
    ? "success"
    : message.includes("Đang dùng") || message.includes("bản nháp")
      ? "info"
      : "error";
}

function formatAlgorithmLabel(value: string) {
  return ALGORITHM_OPTIONS.find((item) => item.value === value)?.label ?? value;
}

export default function AutoSchedulingPage() {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number>(1);
  const [staff, setStaff] = useState<Staff[]>([]);
  const [metrics, setMetrics] = useState<AlgorithmMetrics[]>([]);
  const [previewResult, setPreviewResult] = useState<AutoScheduleResult | null>(null);
  const [editablePreviewSchedules, setEditablePreviewSchedules] = useState<AutoScheduleSummary[]>([]);
  const [unassignedReport, setUnassignedReport] = useState<UnassignedDayReport | null>(null);
  const [workloadData, setWorkloadData] = useState<WorkloadChartData | null>(null);
  const [templates, setTemplates] = useState<ScheduleTemplate[]>([]);
  const [draftSchedules, setDraftSchedules] = useState<Schedule[]>([]);
  const [replacementSuggestion, setReplacementSuggestion] = useState<ReplacementSuggestion | null>(null);
  const [selectedScheduleId, setSelectedScheduleId] = useState<number | null>(null);
  const [templateForm, setTemplateForm] = useState<TemplateFormState>(DEFAULT_TEMPLATE_FORM);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [algorithmType, setAlgorithmType] = useState("GREEDY");
  const [excludedStaffIds, setExcludedStaffIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [templateSubmitting, setTemplateSubmitting] = useState(false);
  const [message, setMessage] = useState("");
  const [maxShiftsMin, setMaxShiftsMin] = useState(4);
  const [maxShiftsMax, setMaxShiftsMax] = useState(8);

  const selectedPeriod = useMemo(
    () => periods.find((period) => period.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId],
  );

  const hasInvalidShiftBounds = maxShiftsMin < 0 || maxShiftsMax < 0 || maxShiftsMin > maxShiftsMax;
  const hasResettableResults = Boolean(previewResult || replacementSuggestion || message);

  const workloadInsights = useMemo(() => {
    const rows = workloadData?.staffWorkloadData ?? [];
    const underAssigned = rows.filter((row) => row.totalShifts < maxShiftsMin);
    const overAssigned = rows.filter((row) => row.totalShifts > maxShiftsMax);
    return {
      underAssigned,
      overAssigned,
      withinRange: Math.max(rows.length - underAssigned.length - overAssigned.length, 0),
    };
  }, [maxShiftsMax, maxShiftsMin, workloadData]);

  const previewOutlierCount = useMemo(() => {
    const assignmentCounts = new Map<number, number>();
    for (const row of previewResult?.schedules ?? []) {
      assignmentCounts.set(row.staffId, (assignmentCounts.get(row.staffId) ?? 0) + 1);
    }
    return Array.from(assignmentCounts.values()).filter(
      (count) => count < maxShiftsMin || count > maxShiftsMax,
    ).length;
  }, [maxShiftsMax, maxShiftsMin, previewResult]);

  const excludedStaff = useMemo(
    () => staff.filter((member) => excludedStaffIds.includes(member.id)),
    [excludedStaffIds, staff],
  );

  const loadPeriodInsights = useCallback(async (periodId: number) => {
    const [reportRes, workloadRes, schedulesRes, metricsRes, templatesRes] = await Promise.all([
      api.get<UnassignedDayReport>(`/auto-schedule/unassigned/${periodId}`),
      api.get<WorkloadChartData>(`/auto-schedule/workload-chart/${periodId}`),
      api.get<Schedule[]>(`/schedules/period/${periodId}`),
      api.get<AlgorithmMetrics[]>(`/auto-schedule/metrics/period/${periodId}`),
      api.get<ScheduleTemplate[]>("/schedule-templates/active"),
    ]);

    setUnassignedReport(reportRes);
    setWorkloadData(workloadRes);
    setDraftSchedules(schedulesRes ?? []);
    setMetrics(metricsRes ?? []);
    setTemplates(templatesRes ?? []);
    setSelectedScheduleId((schedulesRes ?? [])[0]?.id ?? null);
    setSelectedTemplateId((templatesRes ?? [])[0]?.id ?? null);
  }, []);

  const fetchSetup = useCallback(async () => {
    try {
      setLoading(true);
      setMessage("");
      const [periodsRes, staffRes] = await Promise.all([
        api.get<SchedulePeriod[]>("/periods"),
        api.get<Staff[]>("/staff/active"),
      ]);
      const nextPeriods = periodsRes ?? [];
      setPeriods(nextPeriods);
      setStaff(staffRes ?? []);

      const preferredPeriod = nextPeriods.find((period) => period.status === "DRAFT" || period.status === "PUBLISHED") ?? nextPeriods[0] ?? null;
      const nextPeriodId = preferredPeriod?.id ?? 1;
      setSelectedPeriodId(nextPeriodId);

      if (preferredPeriod) {
        await loadPeriodInsights(nextPeriodId);
      } else {
        setUnassignedReport(null);
        setWorkloadData(null);
        setDraftSchedules([]);
        setMetrics([]);
        setTemplates([]);
      }
    } catch (err) {
      setPeriods([]);
      setStaff([]);
      setMetrics([]);
      setTemplates([]);
      setDraftSchedules([]);
      setUnassignedReport(null);
      setWorkloadData(null);
      setMessage(getErrorMessage(err, "Không thể tải cấu hình tự động xếp lịch."));
    } finally {
      setLoading(false);
    }
  }, [loadPeriodInsights]);

  useEffect(() => {
    fetchSetup();
  }, [fetchSetup]);

  async function refreshPeriodSection(periodId: number) {
    try {
      await loadPeriodInsights(periodId);
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể tải dữ liệu chi tiết của kỳ."));
    }
  }

  async function handlePreview() {
    if (hasInvalidShiftBounds) {
      setMessage("Giới hạn ca trực không hợp lệ. Hãy đảm bảo min nhỏ hơn hoặc bằng max.");
      return;
    }

    try {
      setRunning(true);
      setMessage("");
      const res = await api.post<AutoScheduleResult>("/auto-schedule/preview", {
        periodId: selectedPeriodId,
        algorithmType,
        maxIterations: 1000,
        excludedStaffIds,
      });
      setPreviewResult(res);
      setEditablePreviewSchedules(res?.schedules ?? []);
      setMessage("Xem trước hoàn tất. Kiểm tra kết quả bên dưới trước khi áp dụng.");
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi xem trước."));
    } finally {
      setRunning(false);
    }
  }

  async function handleRun() {
    if (hasInvalidShiftBounds) {
      setMessage("Giới hạn ca trực không hợp lệ. Hãy đảm bảo min nhỏ hơn hoặc bằng max.");
      return;
    }

    try {
      setRunning(true);
      setMessage("");
      const res = await api.post<AutoScheduleResult>("/auto-schedule", {
        periodId: selectedPeriodId,
        algorithmType,
        maxIterations: 1000,
        autoAssign: true,
        excludedStaffIds,
      });
      setPreviewResult(res);
      setEditablePreviewSchedules(res?.schedules ?? []);
      setMessage("Đã áp dụng kết quả tự động xếp lịch cho kỳ đang chọn.");
      await refreshPeriodSection(selectedPeriodId);
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi áp dụng kết quả xếp lịch."));
    } finally {
      setRunning(false);
    }
  }

  function handlePreviewStaffChange(index: number, staffId: number) {
    const selectedStaff = staff.find((member) => member.id === staffId);
    if (!selectedStaff) return;

    setEditablePreviewSchedules((prev) => prev.map((item, currentIndex) => (
      currentIndex === index
        ? { ...item, staffId, staffName: selectedStaff.fullName }
        : item
    )));
  }

  async function handleApplyEditedPreview() {
    if (!previewResult || editablePreviewSchedules.length === 0) {
      setMessage("Chưa có bản nháp để áp dụng.");
      return;
    }

    try {
      setRunning(true);
      setMessage("");
      const res = await api.post<AutoScheduleResult>("/auto-schedule/apply-preview", {
        periodId: selectedPeriodId,
        algorithmType: previewResult.algorithmType,
        excludedStaffIds,
        schedules: editablePreviewSchedules.map((item) => ({
          staffId: item.staffId,
          workDate: item.workDate,
          shiftTypeId: item.shiftTypeId,
        })),
      });
      setPreviewResult(res);
      setEditablePreviewSchedules(res?.schedules ?? []);
      setMessage("Đã áp dụng bản nháp sau khi chỉnh tay.");
      await refreshPeriodSection(selectedPeriodId);
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể áp dụng bản nháp đã chỉnh sửa."));
    } finally {
      setRunning(false);
    }
  }

  async function handleReplacementLookup() {
    if (!selectedScheduleId) {
      setMessage("Chưa có lịch để gợi ý thay thế.");
      return;
    }

    try {
      setRunning(true);
      setMessage("");
      const res = await api.get<ReplacementSuggestion>(`/auto-schedule/suggest-replacements/${selectedScheduleId}`);
      setReplacementSuggestion(res);
      setMessage("Đã tải danh sách gợi ý thay thế.");
    } catch (err) {
      setReplacementSuggestion(null);
      setMessage(getErrorMessage(err, "Không thể lấy gợi ý thay thế."));
    } finally {
      setRunning(false);
    }
  }

  async function handleApplyTemplate() {
    if (!selectedTemplateId) {
      setMessage("Chưa chọn template để áp dụng.");
      return;
    }

    try {
      setTemplateSubmitting(true);
      setMessage("");
      await api.post(`/schedule-templates/${selectedTemplateId}/apply/${selectedPeriodId}`, {});
      setMessage("Áp dụng template thành công cho kỳ đang chọn.");
      await refreshPeriodSection(selectedPeriodId);
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể áp dụng template."));
    } finally {
      setTemplateSubmitting(false);
    }
  }

  async function handleCreateTemplate() {
    try {
      setTemplateSubmitting(true);
      setMessage("");
      await api.post<ScheduleTemplate>("/schedule-templates", templateForm);
      setTemplateForm(DEFAULT_TEMPLATE_FORM);
      setMessage("Tạo template thành công.");
      await refreshPeriodSection(selectedPeriodId);
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể tạo template."));
    } finally {
      setTemplateSubmitting(false);
    }
  }

  const messageTone = getStatusTone(message);

  return (
    <DashboardShell
      activeCode="M07"
      description="Tự động phân công lịch theo thuật toán, kiểm tra ràng buộc và xem trước trước khi áp dụng."
      title="Cấu hình Tự động xếp lịch"
    >
      <div className="space-y-6">
        <div className="flex flex-wrap justify-end gap-3">
          <button
            className="flex items-center gap-2 rounded-lg border border-outline-variant px-4 py-2 font-label-md hover:bg-surface-container transition-colors disabled:cursor-not-allowed disabled:opacity-50"
            disabled={running || loading || !hasResettableResults}
            onClick={() => {
              setPreviewResult(null);
              setEditablePreviewSchedules([]);
              setReplacementSuggestion(null);
              setMessage("");
            }}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">restart_alt</span>
            Đặt lại kết quả
          </button>
          <button
            className="flex items-center gap-2 rounded-lg border border-primary px-4 py-2 font-label-md text-primary hover:bg-primary/5 transition-colors"
            disabled={running || loading || hasInvalidShiftBounds}
            onClick={handlePreview}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">preview</span>
            Xem trước
          </button>
          <button
            className="flex items-center gap-2 rounded-lg bg-primary px-5 py-2 font-label-md text-on-primary shadow-sm hover:brightness-110 disabled:opacity-50"
            disabled={running || loading || hasInvalidShiftBounds}
            onClick={handleRun}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">play_arrow</span>
            Áp dụng lịch
          </button>
        </div>

        {message && (
          <div className={`rounded-lg border px-4 py-3 text-sm ${
            messageTone === "success"
              ? "border-secondary/20 bg-secondary-container/30 text-secondary"
              : messageTone === "info"
                ? "border-primary/20 bg-primary/10 text-primary"
                : "border-error/20 bg-error-container text-error"
          }`}>
            {message}
          </div>
        )}

        <div className="grid gap-6 xl:grid-cols-12">
          <div className="space-y-6 xl:col-span-8">
            <SectionCard
              title={<span className="flex items-center gap-2"><span className="material-symbols-outlined text-primary">tune</span>Cấu hình chạy thuật toán</span>}
            >
              <div className="grid gap-4 p-6 md:grid-cols-2">
                <label className="space-y-2">
                  <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Kỳ xếp lịch</span>
                  <select
                    className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5"
                    value={selectedPeriodId}
                    onChange={(e) => {
                      const next = Number(e.target.value);
                      setSelectedPeriodId(next);
                      void refreshPeriodSection(next);
                    }}
                  >
                    {periods.map((period) => (
                      <option key={period.id} value={period.id}>{period.periodName} ({period.status})</option>
                    ))}
                  </select>
                </label>

                <label className="space-y-2">
                  <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Thuật toán</span>
                  <select
                    className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5"
                    value={algorithmType}
                    onChange={(e) => setAlgorithmType(e.target.value)}
                  >
                    {ALGORITHM_OPTIONS.map((item) => (
                      <option key={item.value} value={item.value}>{item.label}</option>
                    ))}
                  </select>
                </label>

                <label className="space-y-2 md:col-span-2">
                  <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Nhân sự loại trừ khỏi lần chạy này</span>
                  <select
                    multiple
                    className="min-h-[132px] w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5"
                    value={excludedStaffIds.map(String)}
                    onChange={(e) => {
                      const selectedValues = Array.from(e.target.selectedOptions, (option) => Number(option.value));
                      setExcludedStaffIds(selectedValues);
                    }}
                  >
                    {staff.map((member) => (
                      <option key={member.id} value={member.id}>
                        {member.fullName}{member.specialty?.name ? ` · ${member.specialty.name}` : ""}
                      </option>
                    ))}
                  </select>
                  <p className="text-xs text-on-surface-variant">Giữ Ctrl/Cmd để chọn nhiều nhân sự không tham gia tự động xếp lịch trong kỳ này.</p>
                </label>

                <label className="space-y-2">
                  <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Ca trực tối thiểu / người</span>
                  <input className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5" type="number" min={0} value={maxShiftsMin} onChange={(e) => setMaxShiftsMin(Number(e.target.value))} />
                </label>

                <label className="space-y-2">
                  <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Ca trực tối đa / người</span>
                  <input className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5" type="number" min={0} value={maxShiftsMax} onChange={(e) => setMaxShiftsMax(Number(e.target.value))} />
                </label>
              </div>

              <div className="border-t border-outline-variant px-6 py-4">
                <div className={`rounded-lg border px-4 py-3 text-sm ${
                  hasInvalidShiftBounds
                    ? "border-error/20 bg-error-container text-error"
                    : "border-primary/20 bg-primary/5 text-on-surface-variant"
                }`}>
                  {hasInvalidShiftBounds
                    ? "Giới hạn ca trực chưa hợp lệ: giá trị tối thiểu phải nhỏ hơn hoặc bằng tối đa."
                    : `Ngưỡng đang áp dụng để đọc tải nhân sự: từ ${maxShiftsMin} đến ${maxShiftsMax} ca/người.${excludedStaff.length > 0 ? ` Đang loại trừ ${excludedStaff.length} nhân sự khỏi lần chạy này.` : ""}`}
                </div>
              </div>
            </SectionCard>

            {previewResult && (
              <SectionCard
                title={<span className="flex items-center gap-2"><span className="material-symbols-outlined text-secondary">analytics</span>Kết quả xem trước / bản nháp</span>}
              >
                <div className="space-y-5 p-6">
                  <div className="grid gap-4 md:grid-cols-5">
                    {[
                      ["Tổng ca tạo", previewResult.totalSchedulesCreated],
                      ["Tỷ lệ phủ", `${Math.round(previewResult.coverageRate)}%`],
                      ["Điểm cân bằng", `${Math.round(previewResult.balanceScore)}%`],
                      ["Cảnh báo", previewResult.conflictCount],
                      ["Ngoài ngưỡng", previewOutlierCount],
                    ].map(([label, value]) => (
                      <div key={String(label)} className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                        <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">{label}</p>
                        <p className="mt-2 text-headline-md text-on-surface">{value}</p>
                      </div>
                    ))}
                  </div>
                  <div className="text-sm text-on-surface-variant">
                    {formatAlgorithmLabel(previewResult.algorithmType)} · {previewResult.executionTimeMs}ms · {new Date(previewResult.executedAt).toLocaleString("vi-VN")}
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-outline-variant bg-surface-container-low px-4 py-3 text-sm text-on-surface-variant">
                    <span>
                      Có thể chỉnh lại nhân sự trực tiếp trên từng dòng preview trước khi áp dụng vào kỳ lịch.
                    </span>
                    <button
                      type="button"
                      className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 font-label-md text-on-primary shadow-sm hover:brightness-110 disabled:opacity-50"
                      disabled={running || editablePreviewSchedules.length === 0}
                      onClick={handleApplyEditedPreview}
                    >
                      <span className="material-symbols-outlined text-[18px]">done_all</span>
                      Áp dụng bản nháp đã chỉnh
                    </button>
                  </div>
                  <div className={`rounded-lg border px-4 py-3 text-sm ${
                    previewOutlierCount > 0
                      ? "border-warning/30 bg-warning/10 text-on-surface"
                      : "border-secondary/20 bg-secondary-container/20 text-secondary"
                  }`}>
                    {previewOutlierCount > 0
                      ? `${previewOutlierCount} nhân sự đang nằm ngoài ngưỡng ${maxShiftsMin}-${maxShiftsMax} ca trong kết quả xem trước.`
                      : `Kết quả xem trước đang nằm trong ngưỡng ${maxShiftsMin}-${maxShiftsMax} ca/người.`}
                  </div>
                  <div className="max-h-[320px] overflow-auto rounded-lg border border-outline-variant">
                    <table className="w-full text-left text-sm">
                      <thead className="sticky top-0 bg-surface-container-low">
                        <tr>
                          <th className="px-3 py-2">Nhân sự</th>
                          <th className="px-3 py-2">Ngày</th>
                          <th className="px-3 py-2">Loại lịch</th>
                        </tr>
                      </thead>
                      <tbody>
                        {editablePreviewSchedules.map((item, index) => {
                          const shiftCandidates = staff.filter((member) => !excludedStaffIds.includes(member.id));
                          return (
                            <tr key={`${item.staffId}-${item.workDate}-${index}`} className="border-t border-outline-variant/50">
                              <td className="px-3 py-2">
                                <select
                                  className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2"
                                  value={item.staffId}
                                  onChange={(e) => handlePreviewStaffChange(index, Number(e.target.value))}
                                >
                                  {shiftCandidates.map((member) => (
                                    <option key={member.id} value={member.id}>
                                      {member.fullName}
                                    </option>
                                  ))}
                                </select>
                              </td>
                              <td className="px-3 py-2">{formatDate(item.workDate)}</td>
                              <td className="px-3 py-2">{item.shiftTypeName}</td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>
              </SectionCard>
            )}

            <SectionCard
              title={<span className="flex items-center gap-2"><span className="material-symbols-outlined text-tertiary">assignment_late</span>Báo cáo ngày chưa phân công</span>}
            >
              <div className="space-y-4 p-6">
                <div className="grid gap-4 md:grid-cols-3">
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                    <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">Tổng ngày thiếu</p>
                    <p className="mt-2 text-headline-md">{unassignedReport?.totalUnassignedDays ?? 0}</p>
                  </div>
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                    <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">Kỳ hiện tại</p>
                    <p className="mt-2 text-body-md">{unassignedReport?.periodName ?? selectedPeriod?.periodName ?? "—"}</p>
                  </div>
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                    <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">Trạng thái kỳ</p>
                    <p className="mt-2 text-body-md">{selectedPeriod?.status ?? "—"}</p>
                  </div>
                </div>
                <div className="max-h-[280px] overflow-auto rounded-lg border border-outline-variant">
                  {(unassignedReport?.unassignedDays ?? []).length === 0 ? (
                    <div className="px-4 py-8 text-center text-sm text-on-surface-variant">
                      Không có ngày thiếu nhân sự trong kỳ đang chọn.
                    </div>
                  ) : (
                    <table className="w-full text-left text-sm">
                      <thead className="sticky top-0 bg-surface-container-low">
                        <tr>
                          <th className="px-3 py-2">Ngày</th>
                          <th className="px-3 py-2">Loại lịch</th>
                          <th className="px-3 py-2">Chuyên khoa</th>
                          <th className="px-3 py-2">Thiếu</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(unassignedReport?.unassignedDays ?? []).map((item) => (
                          <tr key={`${item.workDate}-${item.shiftTypeId}-${item.specialty ?? "all"}`} className="border-t border-outline-variant/50">
                            <td className="px-3 py-2">{formatDate(item.workDate)} · {item.dayOfWeek}</td>
                            <td className="px-3 py-2">{item.shiftTypeName}</td>
                            <td className="px-3 py-2">{item.specialty ?? "Tất cả"}</td>
                            <td className="px-3 py-2 text-error">{item.missingCount}/{item.requiredStaffCount}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>
            </SectionCard>

            <SectionCard
              title={<span className="flex items-center gap-2"><span className="material-symbols-outlined text-primary">swap_horiz</span>Đề xuất người thay thế</span>}
            >
              <div className="space-y-4 p-6">
                <div className="grid gap-4 md:grid-cols-[1fr_auto]">
                  <select
                    className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5 disabled:cursor-not-allowed disabled:opacity-60"
                    disabled={draftSchedules.length === 0}
                    value={selectedScheduleId ?? ""}
                    onChange={(e) => setSelectedScheduleId(Number(e.target.value))}
                  >
                    {draftSchedules.length === 0 ? (
                      <option value="">Chưa có lịch nháp để gợi ý thay thế</option>
                    ) : (
                      draftSchedules.map((schedule) => (
                        <option key={schedule.id} value={schedule.id}>
                          {schedule.staff.fullName} · {formatDate(schedule.workDate)} · {schedule.shiftType.name}
                        </option>
                      ))
                    )}
                  </select>
                  <button
                    className="rounded-lg border border-primary px-4 py-2 font-label-md text-primary hover:bg-primary/5 disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={running || draftSchedules.length === 0 || !selectedScheduleId}
                    onClick={handleReplacementLookup}
                    type="button"
                  >
                    Gợi ý thay thế
                  </button>
                </div>
                {draftSchedules.length === 0 ? (
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low px-4 py-3 text-sm text-on-surface-variant">
                    Hãy tạo bản nháp hoặc tải kỳ có lịch trước khi tìm người thay thế.
                  </div>
                ) : null}
                {replacementSuggestion ? (
                  replacementSuggestion.suggestions.length === 0 ? (
                    <div className="rounded-lg border border-outline-variant bg-surface-container-low px-4 py-3 text-sm text-on-surface-variant">
                      Không tìm thấy nhân sự thay thế phù hợp cho lịch đã chọn.
                    </div>
                  ) : (
                  <div className="space-y-3">
                    <p className="text-sm text-on-surface-variant">
                      {replacementSuggestion.originalStaffName} · {formatDate(replacementSuggestion.workDate)} · {replacementSuggestion.shiftTypeName}
                    </p>
                    <div className="max-h-[260px] overflow-auto rounded-lg border border-outline-variant">
                      <table className="w-full text-left text-sm">
                        <thead className="sticky top-0 bg-surface-container-low">
                          <tr>
                            <th className="px-3 py-2">Nhân sự</th>
                            <th className="px-3 py-2">Chuyên khoa</th>
                            <th className="px-3 py-2">Khối lượng</th>
                            <th className="px-3 py-2">Khả dụng</th>
                          </tr>
                        </thead>
                        <tbody>
                          {replacementSuggestion.suggestions.map((candidate) => (
                            <tr key={candidate.staffId} className="border-t border-outline-variant/50">
                              <td className="px-3 py-2">{candidate.staffName}</td>
                              <td className="px-3 py-2">{candidate.specialty ?? "—"}</td>
                              <td className="px-3 py-2">{candidate.currentWorkload} ca</td>
                              <td className="px-3 py-2">
                                <span className={candidate.isAvailable ? "text-secondary" : "text-error"}>
                                  {candidate.isAvailable ? "Hợp lệ" : candidate.conflicts.join(", ")}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                  )
                ) : null}
              </div>
          </SectionCard>
        </div>

          <div className="space-y-6 xl:col-span-4">
            <SectionCard
              title={<span className="flex items-center gap-2"><span className="material-symbols-outlined text-secondary">monitoring</span>Cân bằng tải nhân sự</span>}
            >
              <div className="space-y-4 p-6">
                <div className="grid gap-3 sm:grid-cols-3 xl:grid-cols-1">
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                    <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">TB / người</p>
                    <p className="mt-2 text-title-lg">{workloadData?.averageWorkload ?? 0}</p>
                  </div>
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                    <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">Dưới ngưỡng</p>
                    <p className="mt-2 text-title-lg text-warning">{workloadInsights.underAssigned.length}</p>
                  </div>
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                    <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">Vượt ngưỡng</p>
                    <p className="mt-2 text-title-lg text-error">{workloadInsights.overAssigned.length}</p>
                  </div>
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                    <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">Min</p>
                    <p className="mt-2 text-title-lg">{workloadData?.minWorkload ?? 0}</p>
                  </div>
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                    <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">Max</p>
                    <p className="mt-2 text-title-lg">{workloadData?.maxWorkload ?? 0}</p>
                  </div>
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low p-4">
                    <p className="text-label-sm uppercase tracking-wide text-on-surface-variant">Đúng ngưỡng</p>
                    <p className="mt-2 text-title-lg text-secondary">{workloadInsights.withinRange}</p>
                  </div>
                </div>
                <div className="max-h-[320px] space-y-3 overflow-auto">
                  {(workloadData?.staffWorkloadData ?? []).length === 0 ? (
                    <div className="rounded-lg border border-outline-variant bg-surface-container-low px-4 py-8 text-center text-sm text-on-surface-variant">
                      Chưa có dữ liệu tải nhân sự cho kỳ đang chọn.
                    </div>
                  ) : (
                    (workloadData?.staffWorkloadData ?? []).map((row) => {
                    const isUnderAssigned = row.totalShifts < maxShiftsMin;
                    const isOverAssigned = row.totalShifts > maxShiftsMax;
                    const stateClasses = isOverAssigned
                      ? "border-error/30 bg-error-container/20"
                      : isUnderAssigned
                        ? "border-warning/30 bg-warning/10"
                        : "border-outline-variant bg-surface-container-low";
                    const progressClasses = isOverAssigned
                      ? "bg-error"
                      : isUnderAssigned
                        ? "bg-warning"
                        : "bg-primary";

                    return (
                    <div key={row.staffId} className={`rounded-lg border p-3 ${stateClasses}`}>
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <p className="font-medium text-on-surface">{row.staffName}</p>
                          <p className="text-xs text-on-surface-variant">{row.specialty ?? "Không có chuyên khoa"}</p>
                        </div>
                        <div className="text-right">
                          <p className="text-sm font-semibold text-primary">{row.totalShifts} ca</p>
                          <p className={`text-[11px] ${isOverAssigned ? "text-error" : isUnderAssigned ? "text-warning" : "text-secondary"}`}>
                            {isOverAssigned ? "Vượt ngưỡng" : isUnderAssigned ? "Dưới ngưỡng" : "Trong ngưỡng"}
                          </p>
                        </div>
                      </div>
                      <div className="mt-3 h-2 rounded-full bg-surface-container-high">
                        <div className={`h-2 rounded-full ${progressClasses}`} style={{ width: `${Math.min(row.workloadPercentage, 100)}%` }} />
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2 text-xs text-on-surface-variant">
                        <span>L01: {row.L01}</span>
                        <span>L02: {row.L02}</span>
                        <span>L03: {row.L03}</span>
                        <span>L04: {row.L04}</span>
                      </div>
                    </div>
                    );
                  })
                  )}
                </div>
              </div>
          </SectionCard>

            <SectionCard
              title={<span className="flex items-center gap-2"><span className="material-symbols-outlined text-tertiary">library_add</span>Template lịch</span>}
            >
              <div className="space-y-5 p-6">
                <div className="space-y-3">
                  <label className="space-y-1">
                    <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Tên template</span>
                    <input className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5" value={templateForm.name} onChange={(e) => setTemplateForm((cur) => ({ ...cur, name: e.target.value }))} />
                  </label>
                  <label className="space-y-1">
                    <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Mô tả</span>
                    <input className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5" value={templateForm.description ?? ""} onChange={(e) => setTemplateForm((cur) => ({ ...cur, description: e.target.value }))} />
                  </label>
                  <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-1">
                    <label className="space-y-1">
                      <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Thứ</span>
                      <select className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5" value={templateForm.dayOfWeek} onChange={(e) => setTemplateForm((cur) => ({ ...cur, dayOfWeek: Number(e.target.value) }))}>
                        {DAY_OPTIONS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                      </select>
                    </label>
                    <label className="space-y-1">
                      <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Loại lịch</span>
                      <select className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5" value={templateForm.shiftTypeId} onChange={(e) => setTemplateForm((cur) => ({ ...cur, shiftTypeId: e.target.value }))}>
                        {SHIFT_TYPE_OPTIONS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                      </select>
                    </label>
                    <label className="space-y-1">
                      <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Chuyên khoa</span>
                      <select className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5" value={templateForm.specialtyId ?? ""} onChange={(e) => setTemplateForm((cur) => ({ ...cur, specialtyId: e.target.value ? Number(e.target.value) : null }))}>
                        <option value="">Tất cả</option>
                        {staff
                          .map((member) => member.specialty)
                          .filter((specialty, index, list): specialty is NonNullable<Staff["specialty"]> => !!specialty && list.findIndex((item) => item?.id === specialty.id) === index)
                          .map((specialty) => (
                            <option key={specialty.id} value={specialty.id}>{specialty.name}</option>
                          ))}
                      </select>
                    </label>
                    <label className="space-y-1">
                      <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Số lượng cần</span>
                      <input className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5" type="number" min={1} value={templateForm.requiredStaffCount} onChange={(e) => setTemplateForm((cur) => ({ ...cur, requiredStaffCount: Number(e.target.value) }))} />
                    </label>
                  </div>
                  <button className="w-full rounded-lg bg-primary px-4 py-2.5 font-label-md text-on-primary disabled:opacity-50" disabled={templateSubmitting} onClick={handleCreateTemplate} type="button">Tạo template</button>
                </div>

                <div className="space-y-3 border-t border-outline-variant pt-4">
                  <label className="space-y-1">
                    <span className="block text-label-sm uppercase tracking-wide text-on-surface-variant">Áp dụng template</span>
                    <select className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5 disabled:cursor-not-allowed disabled:opacity-60" disabled={templates.length === 0} value={selectedTemplateId ?? ""} onChange={(e) => setSelectedTemplateId(Number(e.target.value))}>
                      {templates.length === 0 ? (
                        <option value="">Chưa có template khả dụng</option>
                      ) : (
                        templates.map((template) => (
                          <option key={template.id} value={template.id}>{template.name} · {SHIFT_TYPE_OPTIONS.find((item) => item.value === template.shiftTypeId)?.label ?? template.shiftTypeId}</option>
                        ))
                      )}
                    </select>
                  </label>
                  <button className="w-full rounded-lg border border-primary px-4 py-2.5 font-label-md text-primary disabled:cursor-not-allowed disabled:opacity-50" disabled={templateSubmitting || templates.length === 0 || !selectedTemplateId || selectedPeriod?.status !== "DRAFT"} onClick={handleApplyTemplate} type="button">Áp dụng vào kỳ DRAFT</button>
                  {templates.length === 0 ? <p className="text-xs text-on-surface-variant">Tạo ít nhất một template trước khi áp dụng vào kỳ.</p> : null}
                  {selectedPeriod?.status !== "DRAFT" ? <p className="text-xs text-error">Chỉ có thể áp dụng template cho kỳ ở trạng thái DRAFT.</p> : null}
                </div>
              </div>
            </SectionCard>

            <SectionCard
              title={<span className="flex items-center gap-2"><span className="material-symbols-outlined text-primary">insights</span>Lịch sử thuật toán</span>}
            >
              <div className="space-y-3 p-6">
                {metrics.length === 0 ? (
                  <p className="text-sm text-on-surface-variant">Chưa có lịch sử chạy thuật toán cho kỳ này.</p>
                ) : (
                  metrics.map((metric) => (
                    <div key={metric.id} className="rounded-lg border border-outline-variant bg-surface-container-low p-3">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-on-surface">{metric.algorithmType}</span>
                        <span className="text-xs text-on-surface-variant">{new Date(metric.createdAt).toLocaleDateString("vi-VN")}</span>
                      </div>
                      <div className="mt-2 grid grid-cols-3 gap-2 text-xs text-on-surface-variant">
                        <span>Phủ: {Math.round(metric.coverageRate)}%</span>
                        <span>Cân bằng: {Math.round(metric.balanceScore)}%</span>
                        <span>Xung đột: {metric.conflictCount}</span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </SectionCard>
          </div>
        </div>
      </div>
    </DashboardShell>
  );
}
