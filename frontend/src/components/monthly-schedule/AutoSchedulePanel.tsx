"use client";

import { memo, useEffect, useState } from "react";
import { AutoScheduleMatrixGrid } from "./AutoScheduleMatrixGrid";
import { ShiftTypeBreakdownCard } from "./ShiftTypeBreakdownCard";
import { TemplateActionsSplitButton } from "./TemplateActionsSplitButton";
import { FeasibilityReportCard } from "./FeasibilityReportCard";
import { Badge } from "@/components/ui/Badge";
import { EmptyState } from "@/components/ui/EmptyState";
import { Button } from "@/components/ui/Button";
import { KPICard } from "@/components/ui/KPICard";
import type { AutoScheduleResult, SchedulePeriod, Staff } from "@/types/api";
import { parseNumber, formatCoverageRate, formatPercent } from "@/lib/number-utils";
import { useAlgorithmProgress } from "@/hooks/useAlgorithmProgress";

type AlgorithmType = "GREEDY" | "FAIR_GREEDY" | "CSP_MRV_FC" | "V10_LOCAL_SEARCH";
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
	  skipExisting?: boolean;
	  onSetSkipExisting?: (skip: boolean) => void;
	};

/**
 * Thông tin hiển thị cho mỗi thuật toán. Tất cả dùng chung 1 bảng màu
 * primary (xanh đậm) — 5 thuật toán trông đồng nhất, chỉ khác icon + label.
 */
const ALGO_CONFIG: Record<AlgorithmType, {
  icon: string;
  label: string;
  desc: string;
}> = {
  GREEDY:        { icon: "bolt",         label: "Greedy",       desc: "Nhanh, tham lam" },
  FAIR_GREEDY:   { icon: "autorenew",    label: "Fair Greedy",  desc: "Cân bằng luân phiên" },
  CSP_MRV_FC:    { icon: "account_tree", label: "CSP-MRV-FC",   desc: "CSP + MRV + Forward Checking" },
  V10_LOCAL_SEARCH: { icon: "psychology", label: "v10 Local Search", desc: "Tabu + mẫu hóa + thống kê tăng dần" },
};

// ponytail: format milliseconds theo góc nhìn người dùng. <1s hiển thị ms, ngược lại giây 1 chữ số.
function formatElapsed(ms: number): string {
  if (ms < 0) return "0s";
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  const m = Math.floor(ms / 60_000);
  const s = Math.floor((ms % 60_000) / 1000);
  return `${m}m${s}s`;
}

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

  // Live elapsed counter: tick mỗi 500ms khi đang chạy để hiển thị số giây đếm được.
  // ponytail: tick 500ms đủ mượt cho cảm giác real-time mà không tốn re-render.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!runningAutoSchedule) return;
    const id = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(id);
  }, [runningAutoSchedule]);
  const liveElapsedMs = runningAutoSchedule && progress.startedAt
    ? Math.max(0, now - new Date(progress.startedAt).getTime())
    : null;

  const unassignedDays = previewResult?.unassignedDays ?? [];
  const totalMissing = unassignedDays.reduce((sum: number, d: unknown) => sum + ((d as { missingCount?: number }).missingCount ?? 0), 0);
  const coverageRateRaw = previewResult?.coverageRate ?? null;
  const balanceScoreRaw = previewResult?.balanceScore ?? null;
  const conflictCountRaw = previewResult?.conflictCount ?? null;
  const kpiAvailable = previewResult?.status !== "TEMPLATE_APPLIED"
    && coverageRateRaw !== null;
  const coverageRate = previewResult
    ? Math.min(Math.round(parseNumber(coverageRateRaw ?? 0)), 100)
    : 0;
  const balanceScore = previewResult ? parseNumber(balanceScoreRaw ?? 0) : 0;
  const statusMsgOk = message?.toLowerCase().includes("thành công") || message?.toLowerCase().includes("đã áp dụng");
  const statusMsgNeutral = message?.toLowerCase().includes("đã hủy");

  const algoResultInfo = previewResult ? ALGO_CONFIG[previewResult.algorithmType as AlgorithmType] : null;

  // KPI tone helpers
  const coverageTone = !kpiAvailable
    ? "neutral"
    : coverageRate >= 90 ? "success" : coverageRate >= 70 ? "info" : "error";
  const balanceTone = !kpiAvailable
    ? "neutral"
    : balanceScore >= 75 ? "success" : balanceScore >= 50 ? "warning" : "error";
  const conflictTone = !kpiAvailable
    ? "neutral"
    : (conflictCountRaw ?? 0) === 0 ? "success" : "error";

  return (
    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
      {/* ── Top control bar ─────────────────────────────────── */}
      <div className="border-b border-outline-variant">

        {/* Row 1: algorithm pills (left) + actions (right).
            On narrow viewports each block stays on its own line. */}
        <div className="flex flex-col gap-3 p-4 lg:flex-row lg:flex-wrap lg:items-center lg:justify-between">
          {/* Algorithm pills */}
          <div className="flex items-center gap-2 min-w-0">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-fixed">
              <span className="material-symbols-outlined text-[18px] text-primary" aria-hidden="true">psychology</span>
            </div>
            <div className="flex gap-1.5 overflow-x-auto [scrollbar-width:thin]">
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
                    className={`inline-flex shrink-0 items-center gap-1.5 px-3 py-2 rounded-lg text-label-sm font-semibold border transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${
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

          {/* Right actions — wraps to next line on narrow viewports,
              scrolls horizontally as a fallback on extra-narrow ones. */}
          <div className="flex items-center gap-2 overflow-x-auto [scrollbar-width:thin] lg:flex-wrap">
            {isManager && selectedPeriodId && (
              <div className="shrink-0">
                <TemplateActionsSplitButton
                  onApplyTemplate={() => onApplyTemplate?.()}
                  onSaveTemplate={onSaveTemplate}
                />
              </div>
            )}
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
            {liveElapsedMs !== null && (
              <span role="status" aria-live="off" aria-label="Thời gian đã chạy">
                <Badge tone="neutral" size="sm">
                  <span className="material-symbols-outlined text-[12px]">timer</span>
                  <span className="font-mono tabular-nums">{formatElapsed(liveElapsedMs)}</span>
                </Badge>
              </span>
            )}
            {!runningAutoSchedule && previewResult && previewResult.executionTimeMs > 0 && (
              <Badge tone="neutral" size="sm" aria-label="Tổng thời gian thuật toán đã chạy">
                <span className="material-symbols-outlined text-[12px]">timer</span>
                <span className="font-mono tabular-nums">{formatElapsed(previewResult.executionTimeMs)}</span>
              </Badge>
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

      {/* ── Feasibility Check (before running) ───────────────────── */}
      {!previewResult && selectedPeriodId && isDraft && (
        <div className="p-4">
          <FeasibilityReportCard
            periodId={selectedPeriodId}
            onRunScheduling={onPreview}
          />
        </div>
      )}

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
              icon="event_available"
              label="Ca tạo"
              value={previewResult.totalSchedulesCreated}
              tone="success"
            />
            <KPICard
              icon="radio_button_checked"
              label="Tỷ lệ phủ"
              value={kpiAvailable ? `${coverageRate}%` : "—"}
              helper={kpiAvailable ? undefined : "Nhấn Chạy để tính"}
              tone={coverageTone}
            />
            <KPICard
              icon="balance"
              label="Cân bằng"
              value={kpiAvailable ? `${Math.round(Number(balanceScore))}%` : "—"}
              helper={kpiAvailable ? undefined : "Nhấn Chạy để tính"}
              tone={balanceTone}
            />
            <KPICard
              icon={kpiAvailable && (conflictCountRaw ?? 0) > 0 ? "warning" : "check_circle"}
              label="Xung đột"
              value={kpiAvailable ? (conflictCountRaw ?? 0) : "—"}
              helper={kpiAvailable ? undefined : "Nhấn Chạy để tính"}
              tone={conflictTone}
            />
          </div>

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
            // ponytail: warning uses amber (not tertiary/orange) for intuitive
            // yellow-amber severity distinct from ok=green and critical=red.
            const toneStyles = {
              ok: "bg-secondary-container/20 border-secondary/30",
              warning: "bg-amber-100 dark:bg-amber-900/30 border-amber-300 dark:border-amber-700",
              critical: "bg-error-container/20 border-error/30",
            };
            const iconStyles = {
              ok: "bg-secondary-container text-secondary",
              warning: "bg-amber-200 dark:bg-amber-800 text-amber-700 dark:text-amber-200",
              critical: "bg-error-container text-error",
            };
            const textStyles = {
              ok: "text-secondary",
              warning: "text-amber-700 dark:text-amber-300",
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
