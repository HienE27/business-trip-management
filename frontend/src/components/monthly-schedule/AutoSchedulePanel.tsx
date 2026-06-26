"use client";

import { memo, useEffect, useState } from "react";
import { AutoScheduleMatrixGrid } from "./AutoScheduleMatrixGrid";
import { Badge } from "@/components/ui/Badge";
import { EmptyState } from "@/components/ui/EmptyState";
import { Button } from "@/components/ui/Button";
import { KPICard } from "@/components/ui/KPICard";
import type { AutoScheduleResult, SchedulePeriod, Staff } from "@/types/api";

type AlgorithmType = "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING";
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
  autoGenerateRequirements: boolean;
  onSetAutoGenerateRequirements: (value: boolean) => void;
  onPreview: () => void;
  onApplyPreview: () => void;
  onResetEdits: () => void;
  onEditPreviewItem?: (item: import("@/types/api").AutoScheduleSummary) => void;
  onSetAlgorithmType: (type: AlgorithmType) => void;
  onSaveTemplate?: () => void;
  onApplyTemplate?: () => void;
  isManager?: boolean;
  holidayMode?: "SKIP" | "PARTIAL" | null;
  onSetHolidayMode?: (mode: "SKIP" | "PARTIAL" | null) => void;
};

const ALGO_CONFIG: Record<AlgorithmType, { icon: string; label: string; color: string; bg: string; hover: string }> = {
  GREEDY: { icon: "bolt", label: "Greedy", color: "text-primary", bg: "bg-primary", hover: "hover:bg-primary/90" },
  ROUND_ROBIN: { icon: "autorenew", label: "Round Robin", color: "text-secondary", bg: "bg-secondary", hover: "hover:bg-secondary/90" },
  BACKTRACKING: { icon: "route", label: "Backtracking", color: "text-tertiary", bg: "bg-tertiary", hover: "hover:bg-tertiary/90" },
};

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
  autoGenerateRequirements,
  onSetAutoGenerateRequirements,
  onPreview,
  onApplyPreview,
  onResetEdits,
  onEditPreviewItem,
  onSetAlgorithmType,
  onSaveTemplate,
  onApplyTemplate,
  isManager = true,
  holidayMode,
  onSetHolidayMode,
}: AutoSchedulePanelProps) {
  const [viewMode, setViewMode] = useState<"week" | "month">("month");
  const [selectedStaffIds, setSelectedStaffIds] = useState<Set<number>>(new Set());
  const [staffFilterOpen, setStaffFilterOpen] = useState(false);
  const [showUnassigned, setShowUnassigned] = useState(false);
  const isDraft = selectedPeriodStatus === "DRAFT";

  useEffect(() => {
    setViewMode("month");
    setSelectedStaffIds(new Set());
  }, [selectedPeriodId]);

  const unassignedDays = previewResult?.unassignedDays ?? [];
  const totalMissing = unassignedDays.reduce((sum: number, d: unknown) => sum + ((d as { missingCount?: number }).missingCount ?? 0), 0);
  const coverageRate = previewResult ? Math.round(previewResult.coverageRate) : 0;
  const balanceScore = previewResult?.balanceScore ?? 0;
  const statusMsgOk = message?.toLowerCase().includes("thành công") || message?.toLowerCase().includes("đã áp dụng") || message?.toLowerCase().includes("đã hủy");

  const algoResultInfo = previewResult ? ALGO_CONFIG[previewResult.algorithmType as AlgorithmType] : null;

  // KPI tone helpers
  const coverageTone = coverageRate >= 90 ? "success" : coverageRate >= 70 ? "info" : "error";
  const balanceTone = balanceScore >= 0.75 ? "success" : balanceScore >= 0.5 ? "warning" : "error";
  const conflictTone = previewResult?.conflictCount === 0 ? "success" : "error";

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
                    className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-label-sm font-semibold transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${
                      sel 
                        ? `${cfg.bg} ${cfg.color.replace("text-", "text-on-")}` 
                        : "border border-outline-variant bg-surface-container-low text-on-surface-variant hover:border-primary hover:bg-surface-container-low"
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
            {isDraft && (
              <label className="flex items-center gap-2 px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-low hover:border-primary transition-colors cursor-pointer">
                <div className="relative inline-flex items-center">
                  <input
                    type="checkbox"
                    checked={autoGenerateRequirements}
                    onChange={(e) => onSetAutoGenerateRequirements(e.target.checked)}
                    className="peer h-4 w-4 shrink-0 rounded border-outline text-primary cursor-pointer focus:ring-2 focus:ring-primary/30 focus:ring-offset-1 disabled:cursor-not-allowed"
                  />
                </div>
                <span className="text-label-xs font-medium text-on-surface-variant">Tạo yêu cầu tự động</span>
              </label>
            )}
            {isDraft && (
              <div className="flex items-center gap-1">
                <span className="text-label-xs text-on-surface-variant">Ngày lễ:</span>
                <select
                  value={holidayMode ?? ""}
                  onChange={(e) => onSetHolidayMode?.(e.target.value as "SKIP" | "PARTIAL" || null)}
                  className="h-8 pl-2 pr-6 rounded-lg border border-outline-variant bg-surface-container-low text-label-xs text-on-surface appearance-none cursor-pointer focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-all"
                >
                  <option value="">Mặc định (DB)</option>
                  <option value="SKIP">Bỏ qua ngày lễ</option>
                  <option value="PARTIAL">Giảm 50% dịch vụ</option>
                </select>
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
              <Badge tone="info" size="sm" className="animate-pulse">
                <span className="material-symbols-outlined text-[12px]">sync</span>
                Đang chạy thuật toán…
              </Badge>
            )}
            {message && (
              <Badge tone={statusMsgOk ? "success" : "error"} size="sm">
                <span className="material-symbols-outlined text-[12px]">{statusMsgOk ? "check_circle" : "error"}</span>
                {message}
              </Badge>
            )}
          </div>
        )}
      </div>

      {/* ── Preview results ──────────────────────────────────── */}
      {previewResult ? (
        <div className="p-4 space-y-4">
          {/* KPI Cards Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-3">
            {/* Algorithm Badge Card */}
            {algoResultInfo && (
              <div className={`flex items-center gap-3 p-3 rounded-xl border-2 ${algoResultInfo.bg.replace("bg-", "bg-")}/10 border-${algoResultInfo.bg.replace("bg-", "")}/20`}>
                <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${algoResultInfo.bg} ${algoResultInfo.color.replace("text-", "text-on-")}`}>
                  <span className="material-symbols-outlined text-[20px]" aria-hidden="true">{algoResultInfo.icon}</span>
                </div>
                <div>
                  <p className="text-label-xs text-on-surface-variant">Thuật toán</p>
                  <p className="text-label-md font-bold text-on-surface">{algoResultInfo.label}</p>
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
              value={`${coverageRate}%`}
              tone={coverageTone}
            />
            <KPICard
              icon="balance"
              label="Cân bằng"
              value={`${Math.round(balanceScore * 100)}%`}
              tone={balanceTone}
            />
            <KPICard
              icon={previewResult.conflictCount > 0 ? "warning" : "check_circle"}
              label="Xung đột"
              value={previewResult.conflictCount}
              tone={conflictTone}
            />
          </div>

          {/* Quick actions row */}
          {isManager && (
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onSaveTemplate}
                  icon={<span className="material-symbols-outlined text-[16px]">bookmark_add</span>}
                >
                  Lưu mẫu
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onApplyTemplate}
                  icon={<span className="material-symbols-outlined text-[16px]">download</span>}
                >
                  Áp dụng mẫu
                </Button>
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

          {/* Unassigned alert */}
          {unassignedDays.length > 0 && (
            <div className={`rounded-xl border p-4 ${
              totalMissing > 0 
                ? "bg-error-container/20 border-error/30" 
                : "bg-secondary-container/20 border-secondary/30"
            }`}>
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${
                    totalMissing > 0 ? "bg-error-container text-error" : "bg-secondary-container text-secondary"
                  }`}>
                    <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
                      {totalMissing > 0 ? "warning" : "check_circle"}
                    </span>
                  </div>
                  <div>
                    <p className={`text-label-md font-semibold ${totalMissing > 0 ? "text-error" : "text-secondary"}`}>
                      {totalMissing > 0 ? `${unassignedDays.length} ngày thiếu ${totalMissing} nhân sự` : "Đủ nhân sự"}
                    </p>
                    <p className="text-label-xs text-on-surface-variant">
                      {totalMissing > 0 ? "Một số ca chưa được phân bổ đủ" : "Tất cả ca đã được phân bổ"}
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
          )}

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
                  <div className="absolute right-0 top-full z-50 mt-2 w-64 rounded-xl border border-outline-variant bg-surface-container-lowest p-3 shadow-lg">
                    <p className="text-label-xs font-semibold text-on-surface-variant mb-2">Lọc theo nhân sự</p>
                    <div className="max-h-48 overflow-y-auto space-y-1">
                      {activeStaff.map(staff => {
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
                        return (
                          <label key={staff.id} className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-surface-container-low cursor-pointer">
                            <input 
                              type="checkbox" 
                              checked={sel}
                              onChange={handleToggle}
                              className="h-4 w-4 shrink-0 rounded border-outline text-primary cursor-pointer focus:ring-2 focus:ring-primary/30" 
                            />
                            <span className="text-label-xs text-on-surface truncate">{staff.fullName}</span>
                          </label>
                        );
                      })}
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

          {/* Schedule matrix */}
          <AutoScheduleMatrixGrid
            key={viewMode}
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
