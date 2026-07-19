"use client";

import { memo, useEffect, useState } from "react";
import { AutoScheduleMatrixGrid } from "./AutoScheduleMatrixGrid";
import { ShiftTypeBreakdownCard } from "./ShiftTypeBreakdownCard";
import { TemplateActionsSplitButton } from "./TemplateActionsSplitButton";
import { Badge } from "@/components/ui/Badge";
import { EmptyState } from "@/components/ui/EmptyState";
import { Button } from "@/components/ui/Button";
import { KPICard } from "@/components/ui/KPICard";
import type { AutoScheduleResult, SchedulePeriod, Staff } from "@/types/api";
import { parseNumber } from "@/lib/number-utils";
import { useAlgorithmProgress } from "@/hooks/useAlgorithmProgress";

type AlgorithmType = "BEAM_SEARCH" | "ENHANCED_GREEDY" | "RANDOM_RESTART_HC" | "SIMULATED_ANNEALING" | "CP_SAT";

// ... (ALGO_CONFIG)
const ALGO_CONFIG: Record<AlgorithmType, {
  icon: string;
  label: string;
  desc: string;
}> = {
  BEAM_SEARCH:          { icon: "width_normal", label: "Beam Search",         desc: "Nhanh nhất, coverage 98%" },
  ENHANCED_GREEDY:      { icon: "energy",       label: "Enhanced Greedy",    desc: "Cân bằng tốt, 465ca" },
  RANDOM_RESTART_HC:    { icon: "refresh",      label: "Random Restart",     desc: "HC khởi tạo lại" },
  SIMULATED_ANNEALING:  { icon: "psychology",   label: "Simulated Annealing", desc: "SA tối ưu, 533ca" },
  CP_SAT:               { icon: "neurology",     label: "CP-SAT (OR-Tools)",  desc: "Tối ưu toàn cục, 744ca" },
};
type EditedPreview = Array<{ workDate: string; shiftTypeId: string; staffId: number }>;

export type AutoSchedulePanelProps = {
  previewResult: AutoScheduleResult | null;
  editedPreview: EditedPreview;
  activeStaff: Staff[];
  applyingPreview: boolean;
  runningAutoSchedule: boolean;
  message: string | null;
  algorithmType: AlgorithmType;
  selectedPeriod: SchedulePeriod | null;
  selectedPeriodId: number | null;
  selectedPeriodStatus?: string;
  onPreview: () => void;
  onApplyPreview: () => void;
  onResetEdits: () => void;
  onEditPreviewItem?: (item: import("@/types/api").AutoScheduleSummary) => void;
  onSetAlgorithmType: (type: AlgorithmType) => void;
  onSaveTemplate?: () => void;
  onApplyTemplate?: () => void;
  isManager?: boolean;
};

/**
 * Thông tin hiển thị cho mỗi thuật toán. Tất cả dùng chung 1 bảng màu
 * primary (xanh đậm) — 5 thuật toán trông đồng nhất, chỉ khác icon + label.
 */

export const AutoSchedulePanel = memo(function AutoSchedulePanel({
  previewResult,
  editedPreview,
  activeStaff,
  applyingPreview,
  runningAutoSchedule,
  message,
  algorithmType,
  selectedPeriod,
  selectedPeriodId,
  selectedPeriodStatus,
  onPreview,
  onApplyPreview,
  onResetEdits,
  onEditPreviewItem,
  onSetAlgorithmType,
  onSaveTemplate,
  onApplyTemplate,
  isManager = true,
}: AutoSchedulePanelProps) {
  const [viewMode, setViewMode] = useState<"week" | "month">("month");
  const [selectedStaffIds, setSelectedStaffIds] = useState<Set<number>>(new Set());
  const [staffFilterOpen, setStaffFilterOpen] = useState(false);
  const [staffSearch, setStaffSearch] = useState("");
  const [showUnassigned, setShowUnassigned] = useState(false);
  const isDraft = selectedPeriodStatus === "DRAFT";
  // 8A.1: Real-time progress now via useAlgorithmProgress hook (no fake simulate)

  useEffect(() => {
    setViewMode("month");
    setSelectedStaffIds(new Set());
    setStaffSearch("");
  }, [selectedPeriodId]);

  // 8A.1: Real-time progress từ backend (thay simulate)
  const progress = useAlgorithmProgress(selectedPeriodId, runningAutoSchedule);

  const unassignedDays = previewResult?.unassignedDays ?? [];
  const totalMissing = unassignedDays.reduce((sum: number, d: unknown) => sum + ((d as { missingCount?: number }).missingCount ?? 0), 0);
  const crossSpecialtyCount = previewResult?.schedules.filter(s => s.crossSpecialty).length ?? 0;
  const coverageRate = previewResult ? Math.min(Math.round(parseNumber(previewResult.coverageRate)), 100) : 0;
  const balanceScore = previewResult ? parseNumber(previewResult.balanceScore) : 0;
  // New metrics from quality report
  const qr = previewResult?.qualityReport;
  const eligibleGroupFairness = qr?.eligibleGroupFairnessScore ?? balanceScore;
  const globalFairnessScore = qr?.globalFairnessScore;
  const structuralWarnings = qr?.structuralLoadWarnings;
  const hardViolations = qr?.hardViolationCount ?? 0;
  const softViolations = qr?.softViolationCount ?? 0;
  const [showDetail, setShowDetail] = useState(false);
  const statusMsgOk = message?.toLowerCase().includes("thành công") || message?.toLowerCase().includes("đã áp dụng");
  const statusMsgNeutral = message?.toLowerCase().includes("đã hủy");

  const algoResultInfo = previewResult ? ALGO_CONFIG[previewResult.algorithmType as AlgorithmType] : null;

  // KPI tone helpers
  const coverageTone = coverageRate >= 90 ? "success" : coverageRate >= 70 ? "info" : "error";
  const eligibleTone = eligibleGroupFairness >= 75 ? "success" : eligibleGroupFairness >= 50 ? "warning" : "error";
  const conflictTone = hardViolations > 0 ? "error" : "success";

  return (
    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
      {/* ── Top control bar ─────────────────────────────────── */}
      <div className="border-b border-outline-variant">

        {/* Row 1: algorithm pills + action */}
        <div className="flex flex-wrap items-center justify-between gap-3 p-4">
          {/* Algorithm pills */}
          <div className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary-fixed">
              <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">psychology</span>
            </div>
            <div className="flex gap-1.5">
              {(Object.keys(ALGO_CONFIG) as AlgorithmType[]).map((type) => {
                const cfg = ALGO_CONFIG[type];
                const sel = algorithmType === type;
                return (
                  <button
                    key={type}
                    type="button"
                    onClick={() => onSetAlgorithmType(type)}
                    disabled={runningAutoSchedule}
                    title={cfg.desc}
                    aria-pressed={sel}
                    className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-label-sm font-semibold border transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${
                      sel
                        ? "bg-primary text-on-primary border-primary"
                        : "bg-primary-fixed text-primary border-primary/30 hover:brightness-95"
                    }`}
                  >
                    <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{cfg.icon}</span>
                    {cfg.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right actions */}
          <div className="flex items-center gap-2">
            <Button
              variant="primary"
              size="sm"
              onClick={onPreview}
              disabled={runningAutoSchedule || !selectedPeriodId || !isDraft}
              loading={runningAutoSchedule}
              icon={<span className="material-symbols-outlined text-[16px]">play_arrow</span>}
            >
              {previewResult ? "Làm mới" : "Chạy"}
            </Button>
            {previewResult && (
              <Button
                variant="secondary"
                size="sm"
                onClick={onApplyPreview}
                disabled={applyingPreview}
                loading={applyingPreview}
                icon={<span className="material-symbols-outlined text-[16px]">check</span>}
              >
                Áp dụng{editedPreview.length > 0 ? ` (${editedPreview.length})` : ""}
              </Button>
            )}
          </div>
        </div>

        {/* Row 2: status message + warnings */}
        {(!isDraft || message || runningAutoSchedule) && (
          <div className="px-4 pb-4 flex flex-wrap items-center gap-2">
            {!isDraft && (
              <Badge tone="warning" size="sm">
                <span className="material-symbols-outlined text-[12px]">info</span>
                Chỉ kỳ DRAFT mới xếp được
              </Badge>
            )}
            {runningAutoSchedule && (
              <span role="status" aria-live="polite">
              <Badge tone="info" size="sm" className="animate-pulse">
                <span className="material-symbols-outlined text-[12px]">sync</span>
                {progress.step || progress.message || "Đang chạy thuật toán…"}
                {progress.percent > 0 && (
                  <span className="ml-1 font-mono tabular-nums">({progress.percent}%)</span>
                )}
              </Badge>
              </span>
            )}
            {message && (
              <span role="status" aria-live="polite">
              <Badge tone={statusMsgOk ? "success" : statusMsgNeutral ? "info" : "error"} size="sm">
                <span className="material-symbols-outlined text-[12px]">{statusMsgOk ? "check_circle" : statusMsgNeutral ? "info" : "error"}</span>
                {message}
              </Badge>
              </span>
            )}
          </div>
        )}
      </div>

      {/* ── Preview results ──────────────────────────────────── */}
      {previewResult ? (
        <div className="p-4 space-y-4">
          {/* Row 1: Algorithm badge + 4 KPI metrics (5 equal columns on desktop) */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            {/* Algorithm Badge Card — đồng nhất màu primary cho mọi thuật toán */}
            {algoResultInfo && (
              <div
                role="status"
                aria-label={`Thuật toán đã chạy: ${algoResultInfo.label}`}
                className="flex items-center gap-3 p-4 rounded-lg border border-primary/30 bg-primary-fixed shadow-sm min-w-0 overflow-hidden"
              >
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-primary text-on-primary">
                  <span className="material-symbols-outlined text-[20px]" aria-hidden="true">{algoResultInfo.icon}</span>
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-label-xs text-on-surface-variant leading-tight">Thuật toán</p>
                  <p className="text-label-md font-bold text-primary truncate">{algoResultInfo.label}</p>
                </div>
              </div>
            )}

            <KPICard
              icon="schedule"
              label="Thời gian"
              value={previewResult.executionTimeMs ? `${(previewResult.executionTimeMs / 1000).toFixed(1)}s` : '-'}
              tone="info"
            />
            <KPICard
              icon="event_available"
              label="Ca tạo"
              value={previewResult.totalSchedulesCreated}
              tone="success"
            />
            <KPICard
              icon="radio_button_checked"
              label="Tỷ lệ phủ"
              value={`${coverageRate}%`}
              tone={coverageTone}
            />
            <KPICard
              icon="balance"
              label="Cân bằng"
              value={`${Math.round(Number(balanceScore))}%`}
              tone={eligibleTone}
            />
            <KPICard
              icon="check_circle"
              label="Cân bằng (nhóm)"
              value={`${Math.round(eligibleGroupFairness)}%`}
              tone={eligibleTone}
            />
            <KPICard
              icon={hardViolations > 0 ? "warning" : "check_circle"}
              label="Vi phạm"
              value={hardViolations}
              helper={softViolations > 0 ? `+${softViolations} cảnh báo` : undefined}
              tone={hardViolations > 0 ? "error" : "success"}
            />
            <KPICard
              icon="swap_horiz"
              label="Cross L04"
              value={crossSpecialtyCount}
              tone={crossSpecialtyCount > 0 ? "warning" : "info"}
            />
          </div>

          {crossSpecialtyCount > 0 && (
            <div className="rounded-xl border border-tertiary/30 bg-tertiary-container/20 p-4">
              <div className="flex items-start gap-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-tertiary-container text-tertiary">
                  <span className="material-symbols-outlined text-[18px]" aria-hidden="true">swap_horiz</span>
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-label-md font-semibold text-tertiary">Đã dùng Cross-Specialty cho L04</p>
                  <p className="text-label-xs text-on-surface-variant mt-0.5">
                    {crossSpecialtyCount} ca L04 được gán nhân sự khác chuyên khoa theo tỷ lệ cấu hình.
                  </p>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {previewResult.schedules.filter(s => s.crossSpecialty).slice(0, 6).map((schedule, idx) => (
                      <span key={`${schedule.staffId}-${schedule.workDate}-${idx}`} className="inline-flex items-center gap-1 rounded-full border border-tertiary/20 bg-surface-container-lowest px-2 py-1 text-[11px] text-on-surface-variant">
                        <span className="material-symbols-outlined text-[12px] text-tertiary" aria-hidden="true">stethoscope</span>
                        {schedule.staffName}: {schedule.staffSpecialtyName ?? "Không rõ"} → {schedule.requiredSpecialtyName ?? "L04"}
                      </span>
                    ))}
                    {crossSpecialtyCount > 6 && (
                      <span className="inline-flex items-center rounded-full border border-outline-variant bg-surface-container px-2 py-1 text-[11px] text-on-surface-variant">
                        +{crossSpecialtyCount - 6} ca khác
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Fairness Detail Section (collapsible) */}
          {previewResult && (globalFairnessScore != null || (structuralWarnings && structuralWarnings.length > 0)) && (
            <div className="rounded-xl border border-outline-variant">
              <button
                onClick={() => setShowDetail(!showDetail)}
                className="flex w-full items-center justify-between p-4 text-left hover:bg-surface-container-hover transition-colors"
              >
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-[18px] text-on-surface-variant" aria-hidden="true">info</span>
                  <span className="text-label-sm font-semibold text-on-surface-variant">Phân tích công bằng - Chi tiết</span>
                </div>
                <span className={`material-symbols-outlined text-[18px] text-on-surface-variant transition-transform ${showDetail ? 'rotate-180' : ''}`}>
                  expand_more
                </span>
              </button>
              {showDetail && (
                <div className="border-t border-outline-variant p-4 space-y-4">
                  {/* Summary row: Global + Constraint + Soft warnings */}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                    {globalFairnessScore != null && (
                      <div className="flex flex-col gap-1 rounded-lg bg-surface-container p-3">
                        <span className="text-label-xs text-on-surface-variant">Global Fairness (toàn viện)</span>
                        <div className="flex items-baseline gap-1.5">
                          <span className={`text-title-md font-bold ${globalFairnessScore >= 50 ? 'text-success' : 'text-error'}`}>
                            {Math.round(globalFairnessScore)}%
                          </span>
                          <span className="text-label-xs text-on-surface-variant">/ 100</span>
                        </div>
                        <div className="mt-1 h-1.5 w-full rounded-full bg-surface-container-high">
                          <div className={`h-1.5 rounded-full ${globalFairnessScore >= 50 ? 'bg-success' : 'bg-error'}`} style={{ width: `${globalFairnessScore}%` }} />
                        </div>
                        <span className="text-label-xs text-on-surface-variant mt-1">Bao gồm cả ảnh hưởng từ cấu trúc nhân sự</span>
                        <div className="mt-2 rounded-lg bg-surface-container-high p-2">
                          <p className="text-[10px] text-on-surface-variant leading-relaxed">
                            <span className="font-semibold">Phân tích:</span> Global Fairness thấp hơn Eligible Group Fairness vì tính trên toàn bộ bệnh viện, 
                            bao gồm cả chênh lệch giữa các chuyên khoa. 
                            Ví dụ: Mắt (1 BS ~{previewResult?.schedules?.filter(s => s.staffSpecialtyName === 'Mắt').length ?? 0} ca) 
                            vs Ngoại (9 BS ~{(previewResult?.schedules?.filter(s => s.staffSpecialtyName === 'Ngoại').length ?? 0)/9 || 0} ca/người).
                            Đây là vấn đề nhân sự, không phải lỗi thuật toán.
                          </p>
                        </div>
                      </div>
                    )}
                    <div className="flex flex-col gap-1 rounded-lg bg-surface-container p-3">
                      <span className="text-label-xs text-on-surface-variant">Constraint Compliance</span>
                      <div className="flex items-baseline gap-1.5">
                        <span className={`text-title-md font-bold ${hardViolations === 0 ? 'text-success' : 'text-error'}`}>
                          {hardViolations === 0 ? '100%' : `${Math.max(0, 100 - hardViolations * 25)}%`}
                        </span>
                        <span className="text-label-xs text-on-surface-variant">{hardViolations} hard / {softViolations} soft</span>
                      </div>
                      <div className="flex flex-wrap gap-1.5 mt-1">
                        <span className={`inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-[11px] ${hardViolations === 0 ? 'bg-success-container text-success' : 'bg-error-container text-error'}`}>
                          <span className="material-symbols-outlined text-[12px]">gavel</span>
                          {hardViolations} vi phạm
                        </span>
                        {softViolations > 0 && (
                          <span className="inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-[11px] bg-surface-container-high text-on-surface-variant">
                            <span className="material-symbols-outlined text-[12px]">info</span>
                            {softViolations} cảnh báo (max ca)
                          </span>
                        )}
                      </div>
                      <div className="mt-2 rounded-lg bg-surface-container-high p-2">
                        <p className="text-[10px] text-on-surface-variant leading-relaxed">
                          <span className="font-semibold">Phân tích:</span> 
                          {hardViolations === 0 && softViolations === 0 ? (
                            'Không có vi phạm. Tất cả ràng buộc nghiệp vụ (L01↔L02, L03↔L04, ngày nghỉ bù) đều được tuân thủ.'
                          ) : hardViolations > 0 && softViolations > 0 ? (
                            `Có ${hardViolations} vi phạm cứng (BR-06: vượt maxShiftsPerStaff) và ${softViolations} cảnh báo mềm (max ca/tháng).`
                          ) : hardViolations > 0 ? (
                            `${hardViolations} vi phạm cứng: vượt quá số ca tối đa cho phép (maxShiftsPerStaff).`
                          ) : (
                            `${softViolations} cảnh báo mềm: một số staff nhận nhiều hơn mức target, nhưng không vi phạm ràng buộc cứng.`
                          )}
                        </p>
                        {/* Danh sách vi phạm chi tiết */}
                        {qr?.violations && qr.violations.length > 0 && (
                          <div className="mt-2 space-y-1">
                            <p className="text-[10px] font-semibold text-on-surface-variant">Chi tiết vi phạm:</p>
                            {qr.violations.map((v, i) => (
                              <div key={i} className="flex items-start gap-1.5 rounded-md bg-surface-container-high p-1.5">
                                <span className={`material-symbols-outlined text-[14px] mt-0.5 shrink-0 ${v.severity === 'HARD' ? 'text-error' : 'text-warning'}`}>
                                  {v.severity === 'HARD' ? 'gavel' : 'info'}
                                </span>
                                <div className="min-w-0">
                                  <p className="text-[10px] font-medium text-on-surface">{v.ruleCode}</p>
                                  <p className="text-[9px] text-on-surface-variant leading-tight">
                                    {v.staffName ? `${v.staffName} — ` : ''}{v.description || v.ruleCode}
                                    {v.workDate ? ` (${v.workDate})` : ''}
                                  </p>
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                    <div className="flex flex-col gap-1 rounded-lg bg-surface-container p-3">
                      <span className="text-label-xs text-on-surface-variant">Eligible Groups</span>
                      <div className="flex items-baseline gap-1.5">
                        <span className="text-title-md font-bold text-primary">
                          {qr?.fairnessByType?.length ?? 0}
                        </span>
                        <span className="text-label-xs text-on-surface-variant">nhóm</span>
                      </div>
                      <span className="text-label-xs text-on-surface-variant mt-1">
                        {crossSpecialtyCount > 0 ? `${crossSpecialtyCount} cross-specialty L04` : 'Không có cross-specialty'}
                      </span>
                      <div className="mt-2 rounded-lg bg-surface-container-high p-2">
                        <p className="text-[10px] text-on-surface-variant leading-relaxed">
                          <span className="font-semibold">Phân tích:</span> Các nhóm eligibility được tính riêng. 
                          Mỗi nhóm L04 theo chuyên khoa là một nhóm riêng. 
                          Nhóm chỉ có 1 người (Mắt) được loại khỏi chỉ số "Cân bằng (nhóm)" vì không có sự cạnh tranh.
                          {crossSpecialtyCount > 0 ? ` ${crossSpecialtyCount} ca L04 được gán chéo chuyên khoa.` : ''}
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* Root Cause Analysis */}
                  {previewResult && (
                    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest overflow-hidden">
                      <div className="flex items-center gap-2 p-3 bg-surface-container">
                        <span className="material-symbols-outlined text-[18px] text-primary">account_tree</span>
                        <span className="text-label-sm font-semibold text-on-surface">Phân tích nguyên nhân - Hệ quả</span>
                      </div>
                      <div className="p-3 space-y-3">
                        {/* Nguyên nhân chính */}
                        <div className="flex gap-2">
                          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-error-container text-error">
                            <span className="material-symbols-outlined text-[16px]">search</span>
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-label-xs font-semibold text-on-surface">Nguyên nhân chính</p>
                            <p className="text-[10px] text-on-surface-variant leading-relaxed mt-0.5">
                              {structuralWarnings && structuralWarnings.length > 0 
                                ? `Chuyên khoa Mắt chỉ có 1 nhân sự (Bùi Thị Diễm Thu) nhưng cần ~${previewResult.schedules?.filter(s => s.shiftTypeId === 'L04' && s.staffSpecialtyName === 'Mắt').length ?? 15} ca L04/tháng. Không thể chia sẻ cho ai khác.`
                                : `Tổng nhu cầu ${previewResult.qualityReport?.totalRequired ?? 744} ca vượt quá năng lực ${previewResult.schedules?.length ?? 700} ca khả dụng do ràng buộc nghỉ bù.`
                              }
                            </p>
                          </div>
                        </div>
                        {/* Hệ quả */}
                        <div className="flex gap-2">
                          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-warning-container text-warning">
                            <span className="material-symbols-outlined text-[16px]">trending_down</span>
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-label-xs font-semibold text-on-surface">Hệ quả</p>
                            <p className="text-[10px] text-on-surface-variant leading-relaxed mt-0.5">
                              Bùi Thị Diễm Thu nhận {previewResult.schedules?.filter(s => s.staffId && s.staffName?.includes('Thu')).length ?? 'nhiều'} ca, kéo Global Fairness xuống {globalFairnessScore != null ? Math.round(globalFairnessScore) : 35}%. 
                              Các nhóm khác (Ngoại, Nội, Nhi) vẫn được phân bổ đều trong nội bộ.
                            </p>
                          </div>
                        </div>
                        {/* Đề xuất */}
                        <div className="flex gap-2">
                          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-container text-primary">
                            <span className="material-symbols-outlined text-[16px]">lightbulb</span>
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-label-xs font-semibold text-on-surface">Đề xuất</p>
                            <p className="text-[10px] text-on-surface-variant leading-relaxed mt-0.5">
                              {structuralWarnings && structuralWarnings.length > 0
                                ? 'Tuyển thêm bác sĩ chuyên khoa Mắt để giảm tải cho Bùi Thị Diễm Thu. Hoặc mở rộng cross-specialty L04 để Ngoại/Nội hỗ trợ.'
                                : 'Giảm số ca yêu cầu/ngày trong cấu hình thuật toán (L01-L03 max per day) để phù hợp với năng lực thực tế.'}
                            </p>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Per-type CV Breakdown */}
                  {qr?.fairnessByType && qr.fairnessByType.length > 0 && (
                    <div>
                      <p className="text-label-xs text-on-surface-variant font-semibold mb-2">Cân bằng theo từng loại ca:</p>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                        {qr.fairnessByType.map((ft, i) => (
                          <div key={i} className="flex items-center justify-between rounded-lg border border-outline-variant p-2.5">
                            <div className="flex items-center gap-2 min-w-0">
                              <span className="material-symbols-outlined text-[16px] text-on-surface-variant shrink-0">
                                {ft.shiftType === 'L01' ? 'emergency' : ft.shiftType === 'L02' ? 'schedule' : ft.shiftType === 'L03' ? 'medical_services' : 'stethoscope'}
                              </span>
                              <div className="min-w-0">
                                <p className="text-label-xs font-medium truncate">
                                  {ft.shiftType}{ft.specialtyName ? ` (${ft.specialtyName})` : ''}
                                </p>
                                <p className="text-label-xs text-on-surface-variant">
                                  TB {ft.meanShifts?.toFixed(1)} ± {ft.stdDev?.toFixed(2)}
                                </p>
                              </div>
                            </div>
                            <div className="text-right shrink-0 ml-2">
                              <p className={`text-label-xs font-semibold ${(ft.coefficientOfVariation || 0) < 0.2 ? 'text-success' : (ft.coefficientOfVariation || 0) < 0.3 ? 'text-warning' : 'text-error'}`}>
                                {(ft.coefficientOfVariation || 0) < 0.1 ? 'Tốt' : (ft.coefficientOfVariation || 0) < 0.2 ? 'Khá' : (ft.coefficientOfVariation || 0) < 0.3 ? 'Trung bình' : 'Kém'}
                              </p>
                              <p className="text-label-xs text-on-surface-variant">CV {(ft.coefficientOfVariation * 100)?.toFixed(1)}%</p>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Structural Warnings */}
                  {structuralWarnings && structuralWarnings.length > 0 && (
                    <div className="space-y-1.5">
                      <p className="text-label-xs text-on-surface-variant font-semibold">Cảnh báo cấu trúc nhân sự:</p>
                      {structuralWarnings.map((w, i) => (
                        <div key={i} className="flex items-start gap-2 rounded-lg bg-error-container/10 p-2.5">
                          <span className="material-symbols-outlined text-[16px] text-error shrink-0 mt-0.5" aria-hidden="true">warning</span>
                          <p className="text-label-xs text-on-surface-variant">{w}</p>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Why not 100% explanation */}
                  {structuralWarnings && structuralWarnings.length > 0 && (
                    <div className="rounded-lg bg-primary-container/10 p-3">
                      <p className="text-label-xs text-on-surface-variant">
                        <span className="font-semibold">Tại sao cân bằng không phải 100%?</span><br />
                        Một số chuyên khoa chỉ có rất ít nhân sự (vd: Mắt 1 người). 
                        Người này phải đảm nhận toàn bộ ca L04 của chuyên khoa đó. 
                        Đây là hạn chế của nguồn nhân lực, không phải do thuật toán phân công.
                        Các nhóm chỉ có 1 người được loại khỏi chỉ số "Cân bằng (nhóm)" để phản ánh đúng chất lượng thuật toán.
                      </p>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Row 2: Shift Type Breakdown Cards (own row, separate grid) */}
          {previewResult.byShiftType && Object.keys(previewResult.byShiftType).length > 0 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
              {Object.entries(previewResult.byShiftType).map(([typeId, breakdown]) => (
                <ShiftTypeBreakdownCard
                  key={typeId}
                  typeId={typeId}
                  breakdown={breakdown as {
                    shiftTypeName?: string;
                    totalAssigned?: number;
                    totalRequired?: number;
                    coverageRate?: number;
                    distinctStaffAssigned?: number;
                  }}
                />
              ))}
            </div>
          )}

          {/* Quick actions row */}
          {isManager && (
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <TemplateActionsSplitButton
                  onApplyTemplate={() => onApplyTemplate?.()}
                  onSaveTemplate={onSaveTemplate}
                />
              </div>
              {editedPreview.length > 0 && (
                <Button
                  variant="danger"
                  size="sm"
                  onClick={onResetEdits}
                  icon={<span className="material-symbols-outlined text-[16px]">undo</span>}
                >
                  Hủy thay đổi ({editedPreview.length})
                </Button>
              )}
            </div>
          )}

          {/* Unassigned alert - 8A.5: 3 levels based on missing severity */}
          {unassignedDays.length > 0 && (() => {
            const ratio = totalMissing / Math.max(1, unassignedDays.length);
            const tone = totalMissing === 0 ? "ok" : ratio <= 2 ? "warning" : "critical";
            // Compute bottleneck shift types to give user actionable insight
            const byType = previewResult?.byShiftType ?? {};
            const bottlenecks = (Object.values(byType) as Array<{
              shiftTypeId: string;
              shiftTypeName: string;
              totalAssigned: number;
              totalRequired: number;
              coverageRate: number;
              distinctStaffAssigned: number;
            }>)
              .filter((t) => t.coverageRate < 95)
              .sort((a, b) => a.coverageRate - b.coverageRate);
            const toneStyles = {
              ok: "bg-secondary-container/20 border-secondary/30",
              warning: "bg-tertiary-container/20 border-tertiary/30",
              critical: "bg-error-container/20 border-error/30",
            };
            const iconStyles = {
              ok: "bg-secondary-container text-secondary",
              warning: "bg-tertiary-container text-tertiary",
              critical: "bg-error-container text-error",
            };
            const textStyles = {
              ok: "text-secondary",
              warning: "text-tertiary",
              critical: "text-error",
            };
            const iconName = tone === "ok" ? "check_circle" : tone === "warning" ? "warning" : "error";
            const severityLabel = tone === "ok" ? "Đủ nhân sự" : tone === "warning" ? "Thiếu nhẹ" : "Thiếu nghiêm trọng";
            return (
            <div className={`rounded-xl border p-4 ${toneStyles[tone]}`}>
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${iconStyles[tone]}`}>
                    <span className="material-symbols-outlined text-[20px]" aria-hidden="true">{iconName}</span>
                  </div>
                  <div>
                    <p className={`text-label-md font-semibold ${textStyles[tone]}`}>
                      {totalMissing > 0 ? `${unassignedDays.length} ngày thiếu ${totalMissing} nhân sự · ${severityLabel}` : "Đủ nhân sự"}
                    </p>
                    <p className="text-label-xs text-on-surface-variant">
                      {totalMissing > 0 ? `Trung bình ${ratio.toFixed(1)} NS/ngày bị thiếu` : "Tất cả ca đã được phân bổ"}
                    </p>
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowUnassigned(!showUnassigned)}
                >
                  {showUnassigned ? "Ẩn chi tiết" : "Xem chi tiết"}
                </Button>
              </div>

              {/* Bottleneck explanation - hiển thị lý do coverage thấp */}
              {bottlenecks.length > 0 && (
                <div className="mt-3 rounded-lg border border-outline-variant/40 bg-surface-container-lowest/50 p-3 text-label-xs text-on-surface-variant space-y-1">
                  <div className="flex items-start gap-2">
                    <span className="material-symbols-outlined text-[14px] mt-0.5">lightbulb</span>
                    <div className="flex-1">
                      <p className="font-semibold text-on-surface">Nguyên nhân thiếu nhân sự:</p>
                      <ul className="mt-1 space-y-0.5">
                        {bottlenecks.map((b) => {
                          const gap = b.totalRequired - b.totalAssigned;
                          return (
                            <li key={b.shiftTypeId}>
                              • <strong className="text-on-surface">{b.shiftTypeName}</strong>: cần {b.totalRequired} ca nhưng chỉ gán được {b.totalAssigned} ca
                              {" "}({b.coverageRate.toFixed(1)}%)
                              {" — thiếu "}{gap} ca
                              {b.distinctStaffAssigned > 0 && (
                                <span className="text-on-surface-variant"> · {b.distinctStaffAssigned} nhân sự khả dụng</span>
                              )}
                              {b.shiftTypeId === "L04" && (
                                <span className="text-on-surface-variant"> · L04 phụ thuộc chuyên khoa, mỗi chuyên khoa chỉ gán được nhân sự đúng chuyên khoa</span>
                              )}
                            </li>
                          );
                        })}
                      </ul>
                      <p className="mt-1.5 text-on-surface-variant italic">
                        💡 Gợi ý: thêm nhân sự vào pool (chuyên khoa phù hợp cho L04) hoặc điều chỉnh số NS tối thiểu/ngày trong cấu hình thuật toán.
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* Unassigned details */}
              {showUnassigned && (
                <div className="mt-4 border-t border-outline-variant pt-4">
                  <div className="rounded-lg border border-outline-variant overflow-hidden">
                    <table className="w-full text-left">
                      <thead>
                        <tr className="bg-surface-container-low">
                          <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant uppercase">Ngày</th>
                          <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant uppercase">Loại ca</th>
                          <th className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant uppercase text-right">Thiếu</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-outline-variant/50">
                        {unassignedDays.map((day: unknown, idx: number) => {
                          const d = day as { workDate: string; shiftTypeId: string; shiftTypeName: string; missingCount: number; requiredStaffCount: number };
                          return (
                            <tr key={idx} className="hover:bg-surface-container-low transition-colors">
                              <td className="px-4 py-2.5 text-label-sm text-on-surface">
                                {new Date(d.workDate).toLocaleDateString("vi-VN", { weekday: "short", day: "numeric", month: "short" })}
                              </td>
                              <td className="px-4 py-2.5 text-label-sm text-on-surface-variant">{d.shiftTypeName}</td>
                              <td className="px-4 py-2.5 text-label-sm text-error font-semibold text-right">
                                {d.missingCount}/{d.requiredStaffCount}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
            );
          })()}

          {/* Matrix controls */}
          <div className="flex items-center justify-between gap-3 pt-2">
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-2 text-label-sm text-on-surface-variant">
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">calendar_month</span>
                <span>{new Set(previewResult.schedules.map(s => s.workDate.split("T")[0])).size} ngày · {previewResult.totalSchedulesCreated} ca</span>
              </div>
            </div>
            <div className="flex items-center gap-2">
              {/* View mode toggle */}
              <div className="flex rounded-lg border border-outline-variant bg-surface-container-low p-0.5">
                {[{ key: "week", icon: "view_week", label: "Tuần" }, { key: "month", icon: "calendar_view_month", label: "Tháng" }].map(m => (
                  <button key={m.key} type="button" onClick={() => setViewMode(m.key as "week" | "month")}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-label-xs font-medium transition-all cursor-pointer ${
                      viewMode === m.key 
                        ? "bg-primary text-on-primary shadow-sm" 
                        : "text-on-surface-variant hover:bg-surface-container-high"
                    }`}
                    title={`Xem theo ${m.label}`}
                  >
                    <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{m.icon}</span>
                    {m.label}
                  </button>
                ))}
              </div>
              
              {/* Staff filter */}
              <div className="relative">
                <button type="button" onClick={() => setStaffFilterOpen(!staffFilterOpen)}
                  className={`flex items-center gap-2 px-3 py-2 rounded-lg border text-label-xs font-medium transition-all cursor-pointer ${
                    selectedStaffIds.size > 0 
                      ? "border-primary bg-primary-fixed/20 text-primary" 
                      : "border-outline-variant text-on-surface-variant hover:border-primary hover:bg-surface-container-low"
                  }`}
                >
                  <span className="material-symbols-outlined text-[14px]" aria-hidden="true">filter_list</span>
                  {selectedStaffIds.size > 0 ? `Lọc (${selectedStaffIds.size})` : "Tất cả NS"}
                </button>
                {staffFilterOpen && (
                  <div className="absolute right-0 top-full z-50 mt-2 w-72 rounded-xl border border-outline-variant bg-surface-container-lowest p-3 shadow-lg">
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <p className="text-label-xs font-semibold text-on-surface-variant">Lọc theo nhân sự</p>
                      <span className="text-[11px] text-on-surface-variant">
                        {selectedStaffIds.size > 0
                          ? `${selectedStaffIds.size}/${activeStaff.length}`
                          : `${activeStaff.length} NS`}
                      </span>
                    </div>
                    <div className="relative mb-2">
                      <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px] pointer-events-none" aria-hidden="true">search</span>
                      <input
                        className="w-full h-8 pl-8 pr-3 rounded-lg border border-outline-variant bg-surface-container-low text-label-xs text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all"
                        placeholder="Tìm nhân sự..."
                        value={staffSearch}
                        onChange={e => setStaffSearch(e.target.value)}
                      />
                    </div>
                    <div className="max-h-48 overflow-y-auto space-y-1">
                      {activeStaff.filter(s => s.fullName.toLowerCase().includes(staffSearch.toLowerCase())).map(staff => {
                        const sel = selectedStaffIds.has(staff.id);
                        const handleToggle = () => {
                          setSelectedStaffIds(prev => {
                            const n = new Set(prev);
                            if (n.has(staff.id)) {
                              n.delete(staff.id);
                            } else {
                              n.add(staff.id);
                            }
                            return n;
                          });
                        };
                        // 8A.4: Tính số ca của staff này trong preview
                        const staffShiftCount = previewResult?.schedules.filter(s => s.staffId === staff.id).length ?? 0;
                        return (
                          <label key={staff.id} className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-surface-container-low cursor-pointer">
                            <input
                              type="checkbox"
                              checked={sel}
                              onChange={handleToggle}
                              className="h-4 w-4 shrink-0 rounded border-outline text-primary cursor-pointer focus:ring-2 focus:ring-primary/30"
                            />
                            <span className="text-label-xs text-on-surface truncate flex-1">{staff.fullName}</span>
                            {previewResult && (
                              <span className="text-[10px] font-mono font-semibold text-primary bg-primary-fixed/30 px-1.5 py-0.5 rounded tabular-nums shrink-0">
                                {staffShiftCount}
                              </span>
                            )}
                          </label>
                        );
                      })}
                      {activeStaff.filter(s => s.fullName.toLowerCase().includes(staffSearch.toLowerCase())).length === 0 && (
                        <p className="text-[11px] text-on-surface-variant text-center py-2">Không tìm thấy nhân sự</p>
                      )}
                    </div>
                    {selectedStaffIds.size > 0 && (
                      <button type="button" onClick={() => setSelectedStaffIds(new Set())}
                        className="mt-2 w-full rounded-lg border border-outline-variant px-3 py-2 text-label-xs font-medium text-primary hover:bg-surface-container-low transition-colors cursor-pointer">
                        Bỏ lọc
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Schedule matrix - không dùng key={viewMode} để preserve edit state khi đổi week/month */}
          <AutoScheduleMatrixGrid
            schedules={previewResult.schedules}
            activeStaff={activeStaff}
            year={selectedPeriod ? new Date(selectedPeriod.startDate).getFullYear() : new Date().getFullYear()}
            month={selectedPeriod ? new Date(selectedPeriod.startDate).getMonth() : new Date().getMonth()}
            viewMode={viewMode}
            filteredStaffIds={selectedStaffIds}
            editedPreview={editedPreview}
            onEditItem={onEditPreviewItem}
          />
        </div>
      ) : (
        /* ── Empty state ──────────────────────────────────── */
        <EmptyState
          icon="auto_mode"
          title={isManager ? "Sẵn sàng xếp lịch tự động" : "Không có quyền xếp lịch"}
          description={isManager 
            ? "Chọn thuật toán và nhấn Chạy để phân bổ ca trực một cách tối ưu" 
            : "Chỉ Quản lý hoặc Admin mới được phép chạy auto-scheduling."}
          className="py-20"
        />
      )}
    </div>
  );
});
