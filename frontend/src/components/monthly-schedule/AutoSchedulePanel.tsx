"use client";

import { memo, useEffect, useState } from "react";
import { AutoScheduleMatrixGrid } from "./AutoScheduleMatrixGrid";
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
};

const ALGO_COLORS: Record<AlgorithmType, { icon: string; active: string; inactive: string }> = {
  GREEDY: { icon: "bolt", active: "bg-primary text-on-primary", inactive: "text-primary bg-primary-fixed hover:bg-primary-fixed/70" },
  ROUND_ROBIN: { icon: "autorenew", active: "bg-secondary text-on-secondary", inactive: "text-secondary bg-secondary-container hover:bg-secondary-container/70" },
  BACKTRACKING: { icon: "route", active: "bg-tertiary text-on-tertiary", inactive: "text-tertiary bg-tertiary-container hover:bg-tertiary-container/70" },
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

  return (
    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
      {/* ── Top control bar ─────────────────────────────────── */}
      <div className="flex flex-col gap-0 border-b border-outline-variant">

        {/* Row 1: algorithm pills + action */}
        <div className="flex flex-wrap items-center gap-2 p-3">
          {/* Algorithm pills */}
          <div className="flex items-center gap-1.5 flex-1 min-w-0">
            <span className="material-symbols-outlined text-[16px] text-on-surface-variant shrink-0" aria-hidden="true">psychology</span>
            <div className="flex gap-1.5 flex-wrap">
              {(Object.keys(ALGO_COLORS) as AlgorithmType[]).map((type) => {
                const cfg = ALGO_COLORS[type];
                const sel = algorithmType === type;
                return (
                  <button
                    key={type}
                    type="button"
                    onClick={() => onSetAlgorithmType(type)}
                    className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-label-sm font-semibold transition-all cursor-pointer ${sel ? cfg.active : cfg.inactive}`}
                  >
                    <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{cfg.icon}</span>
                    {type.replace("_", " ")}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right actions */}
          <div className="flex items-center gap-1.5 shrink-0">
            {isDraft && (
              <label className="flex items-center gap-1.5 text-label-xs text-on-surface-variant cursor-pointer">
                <input
                  type="checkbox"
                  checked={autoGenerateRequirements}
                  onChange={(e) => onSetAutoGenerateRequirements(e.target.checked)}
                  className="h-3.5 w-3.5 rounded border-outline text-primary accent-primary"
                />
                Auto-gen
              </label>
            )}
            <button
              type="button"
              onClick={onPreview}
              disabled={runningAutoSchedule || !selectedPeriodId || !isDraft}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary text-on-primary text-label-sm font-semibold hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {runningAutoSchedule ? (
                <><div className="size-3.5 animate-spin rounded-full border border-on-primary border-t-transparent" /> Đang chạy…</>
              ) : (
                <><span className="material-symbols-outlined text-[14px]" aria-hidden="true">play_arrow</span> {previewResult ? "Làm mới" : "Chạy"}</>
              )}
            </button>
            {previewResult && (
              <button
                type="button"
                onClick={onApplyPreview}
                disabled={applyingPreview}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-secondary text-on-secondary text-label-sm font-semibold hover:bg-secondary/90 disabled:opacity-50 transition-colors"
              >
                {applyingPreview ? (
                  <><div className="size-3.5 animate-spin rounded-full border border-on-secondary border-t-transparent" /> Đang áp dụng…</>
                ) : (
                  <><span className="material-symbols-outlined text-[14px]" aria-hidden="true">check</span> Áp dụng{editedPreview.length > 0 ? `+${editedPreview.length}` : ""}</>
                )}
              </button>
            )}
          </div>
        </div>

        {/* Row 2: status message + warnings */}
        {(!isDraft || message || runningAutoSchedule) && (
          <div className="px-3 pb-3 flex flex-wrap items-center gap-2">
            {!isDraft && (
              <span className="inline-flex items-center gap-1 px-2 py-1 rounded-md bg-tertiary-container text-on-tertiary-container text-label-xs font-semibold border border-tertiary/20">
                <span className="material-symbols-outlined text-[12px]">info</span>
                Chỉ kỳ DRAFT mới xếp được
              </span>
            )}
            {runningAutoSchedule && (
              <span className="inline-flex items-center gap-1 text-label-xs text-primary">
                <div className="size-3 animate-spin rounded-full border border-primary border-t-transparent" />
                Đang chạy thuật toán…
              </span>
            )}
            {message && (
              <span className={`inline-flex items-center gap-1 text-label-xs font-medium ${
                statusMsgOk ? "text-secondary" : "text-error"
              }`}>
                <span className="material-symbols-outlined text-[12px]">{statusMsgOk ? "check_circle" : "error"}</span>
                {message}
              </span>
            )}
          </div>
        )}
      </div>

      {/* ── Preview results ──────────────────────────────────── */}
      {previewResult ? (
        <div className="p-3 space-y-3">
          {/* KPI strip */}
          <div className="flex flex-wrap gap-2">
            {[
              { icon: "event_available", label: "Tạo", value: previewResult.totalSchedulesCreated, tone: "text-secondary" },
              { icon: "radio_button_checked", label: "Phủ", value: `${coverageRate}%`, tone: coverageRate >= 90 ? "text-secondary" : coverageRate >= 70 ? "text-primary" : "text-error" },
              { icon: "balance", label: "CB", value: `${Math.round(balanceScore * 100)}%`, tone: balanceScore >= 0.75 ? "text-secondary" : "text-primary" },
              { icon: previewResult.conflictCount > 0 ? "warning" : "check_circle", label: "XĐ", value: previewResult.conflictCount, tone: previewResult.conflictCount > 0 ? "text-error" : "text-secondary" },
            ].map((kpi) => (
              <div key={kpi.label} className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-low border border-outline-variant">
                <span className="material-symbols-outlined text-[14px] text-on-surface-variant" aria-hidden="true">{kpi.icon}</span>
                <span className={`font-bold text-[15px] tabular-nums ${kpi.tone}`}>{kpi.value}</span>
                <span className="text-label-xs text-on-surface-variant">{kpi.label}</span>
              </div>
            ))}

            {/* Quick actions */}
            {isManager && (
              <div className="ml-auto flex items-center gap-1">
                <button type="button" onClick={onSaveTemplate}
                  className="flex items-center gap-1 px-2 py-1.5 rounded-lg border border-outline-variant text-label-xs text-on-surface hover:bg-surface-container-low transition-colors cursor-pointer"
                  title="Lưu mẫu">
                  <span className="material-symbols-outlined text-[12px]">bookmark_add</span> Lưu mẫu
                </button>
                <button type="button" onClick={onApplyTemplate}
                  className="flex items-center gap-1 px-2 py-1.5 rounded-lg border border-outline-variant text-label-xs text-on-surface hover:bg-surface-container-low transition-colors cursor-pointer"
                  title="Áp dụng mẫu">
                  <span className="material-symbols-outlined text-[12px]">download</span> Mẫu
                </button>
                {editedPreview.length > 0 && (
                  <button type="button" onClick={onResetEdits}
                    className="flex items-center gap-1 px-2 py-1.5 rounded-lg text-label-xs text-error hover:bg-error-container transition-colors cursor-pointer">
                    <span className="material-symbols-outlined text-[12px]">undo</span> Hủy {editedPreview.length}
                  </button>
                )}
              </div>
            )}
          </div>

          {/* Unassigned alert */}
          {unassignedDays.length > 0 && (
            <div className={`flex items-center justify-between gap-2 px-3 py-2 rounded-lg border ${totalMissing > 0 ? "bg-error-container border-error/30" : "bg-secondary-container border-secondary/30"}`}>
              <div className="flex items-center gap-2">
                <span className={`material-symbols-outlined text-[16px] ${totalMissing > 0 ? "text-error" : "text-secondary"}`} aria-hidden="true">
                  {totalMissing > 0 ? "warning" : "check_circle"}
                </span>
                <span className={`text-label-sm font-semibold ${totalMissing > 0 ? "text-error" : "text-secondary"}`}>
                  {totalMissing > 0 ? `${unassignedDays.length} ngày thiếu ${totalMissing} NS` : "Đủ nhân sự"}
                </span>
              </div>
              <button
                type="button"
                onClick={() => setShowUnassigned(!showUnassigned)}
                className="text-label-xs text-on-surface-variant hover:text-on-surface transition-colors cursor-pointer"
              >
                {showUnassigned ? "Ẩn" : "Chi tiết"}
              </button>
            </div>
          )}

          {/* Unassigned details */}
          {showUnassigned && unassignedDays.length > 0 && (
            <div className="border border-outline-variant rounded-lg overflow-hidden">
              <div className="overflow-x-auto max-h-32">
                <table className="w-full text-left">
                  <thead>
                    <tr className="bg-surface-container-low border-b border-outline-variant">
                      <th className="p-2 text-label-xs text-on-surface-variant uppercase">Ngày</th>
                      <th className="p-2 text-label-xs text-on-surface-variant uppercase">Loại ca</th>
                      <th className="p-2 text-label-xs text-on-surface-variant uppercase text-right">Thiếu</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant/30">
                    {unassignedDays.map((day: unknown, idx: number) => {
                      const d = day as { workDate: string; shiftTypeId: string; shiftTypeName: string; missingCount: number; requiredStaffCount: number };
                      return (
                        <tr key={idx} className="hover:bg-surface-container-lowest transition-colors">
                          <td className="p-2 text-label-sm text-on-surface">
                            {new Date(d.workDate).toLocaleDateString("vi-VN", { weekday: "short", day: "numeric", month: "short" })}
                          </td>
                          <td className="p-2 text-label-sm text-on-surface-variant">{d.shiftTypeName}</td>
                          <td className="p-2 text-label-sm text-error font-semibold text-right">{d.missingCount}/{d.requiredStaffCount}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Matrix header + view toggle + staff filter */}
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="text-label-xs text-on-surface-variant">
                {new Set(previewResult.schedules.map(s => s.workDate.split("T")[0])).size} ngày · {previewResult.totalSchedulesCreated} ca
              </span>
            </div>
            <div className="flex items-center gap-2">
              <div className="flex rounded-lg border border-outline-variant bg-surface-container-low p-0.5">
                {[{ key: "week", icon: "view_week" }, { key: "month", icon: "calendar_view_month" }].map(m => (
                  <button key={m.key} type="button" onClick={() => setViewMode(m.key as "week" | "month")}
                    className={`p-1.5 rounded-md transition-colors ${viewMode === m.key ? "bg-primary text-on-primary" : "text-on-surface-variant hover:bg-surface-container-high"}`}>
                    <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{m.icon}</span>
                  </button>
                ))}
              </div>
              <div className="relative">
                <button type="button" onClick={() => setStaffFilterOpen(!staffFilterOpen)}
                  className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border text-label-xs font-medium transition-colors cursor-pointer ${
                    selectedStaffIds.size > 0 ? "border-primary bg-primary-fixed/20 text-primary" : "border-outline-variant text-on-surface-variant hover:border-primary"
                  }`}>
                  <span className="material-symbols-outlined text-[12px]" aria-hidden="true">filter_list</span>
                  {selectedStaffIds.size > 0 ? `Lọc (${selectedStaffIds.size})` : "Tất cả NS"}
                </button>
                {staffFilterOpen && (
                  <div className="absolute right-0 top-full z-50 mt-1 w-56 rounded-xl border border-outline-variant bg-surface-container-lowest p-2 shadow-lg">
                    <div className="max-h-40 overflow-y-auto space-y-0.5">
                      {activeStaff.map(staff => {
                        const sel = selectedStaffIds.has(staff.id);
                        return (
                          <label key={staff.id} className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-surface-container-low cursor-pointer">
                            <input type="checkbox" checked={sel}
                              onChange={() => setSelectedStaffIds(prev => { const n = new Set(prev); n.has(staff.id) ? n.delete(staff.id) : n.add(staff.id); return n; })}
                              className="h-3.5 w-3.5 rounded border-outline text-primary accent-primary" />
                            <span className="text-label-xs text-on-surface truncate">{staff.fullName}</span>
                          </label>
                        );
                      })}
                    </div>
                    {selectedStaffIds.size > 0 && (
                      <button type="button" onClick={() => setSelectedStaffIds(new Set())}
                        className="mt-2 w-full text-center text-label-xs text-primary hover:underline cursor-pointer">
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
        <div className="flex flex-col items-center justify-center gap-3 py-12 px-6 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-fixed">
            <span className="material-symbols-outlined text-[28px] text-primary" aria-hidden="true">auto_mode</span>
          </div>
          {!isManager ? (
            <>
              <div className="text-center">
                <p className="font-semibold text-on-surface">Không có quyền xếp lịch</p>
                <p className="text-label-sm text-on-surface-variant mt-1">Chỉ Quản lý hoặc Admin mới được phép chạy auto-scheduling.</p>
              </div>
            </>
          ) : (
            <>
              <div className="text-center">
                <p className="font-semibold text-on-surface">Sẵn sàng xếp lịch tự động</p>
                <p className="text-label-sm text-on-surface-variant mt-1">Chọn thuật toán phù hợp và nhấn <strong>Chạy</strong> để phân bổ ca trực.</p>
              </div>
              <div className="flex items-center gap-4 text-label-xs text-on-surface-variant">
                <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[12px] text-secondary">check_circle</span> Phát hiện xung đột</span>
                <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[12px] text-secondary">check_circle</span> Tạo nghỉ bù</span>
                <span className="flex items-center gap-1"><span className="material-symbols-outlined text-[12px] text-secondary">check_circle</span> Cân bằng tải</span>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
});
