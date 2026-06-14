"use client";

import { memo, useEffect, useMemo, useState } from "react";
import { EmptyState } from "@/components/ui/EmptyState";
import { SectionCard } from "@/components/ui/SectionCard";
import type { AutoScheduleResult } from "@/types/api";
import type { Staff } from "@/types/api";
import { ALGORITHM_OPTIONS, SHIFT_TYPE_BADGES } from "./constants";
import type { ScheduleTab } from "./types";

const PAGE_SIZE = 50;

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
  selectedPeriodId: number | null;
  selectedPeriodStatus?: string;
  conflictKeys: Set<string>;
  onPreview: () => void;
  onApplyPreview: () => void;
  onResetEdits: () => void;
  onEditStaff: (workDate: string, shiftTypeId: string, staffId: number) => void;
  onSetAlgorithmType: (type: AlgorithmType) => void;
};

export const AutoSchedulePanel = memo(function AutoSchedulePanel({
  previewResult,
  editedPreview,
  activeStaff,
  applyingPreview,
  runningAutoSchedule,
  message,
  algorithmType,
  selectedPeriodId,
  selectedPeriodStatus,
  conflictKeys,
  onPreview,
  onApplyPreview,
  onResetEdits,
  onEditStaff,
  onSetAlgorithmType,
}: AutoSchedulePanelProps) {
  const [page, setPage] = useState(1);
  const isDraft = selectedPeriodStatus === "DRAFT";

  const sortedRows = useMemo(() => {
    if (!previewResult) return [];
    return [...previewResult.schedules].sort((a, b) => {
      const dateCompare = a.workDate.localeCompare(b.workDate);
      return dateCompare !== 0 ? dateCompare : a.shiftTypeId.localeCompare(b.shiftTypeId);
    });
  }, [previewResult]);

  const editedMap = useMemo(() => {
    const map = new Map<string, number>();
    for (const edit of editedPreview) {
      map.set(`${edit.workDate}-${edit.shiftTypeId}`, edit.staffId);
    }
    return map;
  }, [editedPreview]);

  const totalPages = Math.max(1, Math.ceil(sortedRows.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages);
  const visibleRows = sortedRows.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  useEffect(() => {
    setPage(1);
  }, [previewResult]);

  const statusMessageIsSuccess = message?.toLowerCase().includes("thành công") || message?.toLowerCase().includes("đã áp dụng") || message?.toLowerCase().includes("đã hủy") || message?.toLowerCase().includes("đã làm mới");

  return (
    <SectionCard
      title="Auto schedule & coverage"
      description="Chỉnh sửa phương án trước khi áp dụng vào kỳ lịch."
      action={
        <button
          type="button"
          onClick={onPreview}
          className="rounded-lg bg-primary px-4 py-2 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          disabled={runningAutoSchedule || !selectedPeriodId || !isDraft}
        >
          {runningAutoSchedule ? "Đang chạy preview" : previewResult ? "Làm mới preview" : "Chạy preview"}
        </button>
      }
    >
      {previewResult ? (
        <div className="space-y-4 p-5">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <div className="rounded-lg border border-outline-variant bg-surface p-3">
              <p className="text-label-sm text-on-surface-variant">Đã tạo</p>
              <p className="mt-1 text-title-lg font-bold text-on-surface">{previewResult.totalSchedulesCreated}</p>
            </div>
            <div className="rounded-lg border border-outline-variant bg-surface p-3">
              <p className="text-label-sm text-on-surface-variant">Coverage</p>
              <p className="mt-1 text-title-lg font-bold text-on-surface">{previewResult.coverageRate}%</p>
            </div>
            <div className="rounded-lg border border-outline-variant bg-surface p-3">
              <p className="text-label-sm text-on-surface-variant">Balance</p>
              <p className="mt-1 text-title-lg font-bold text-on-surface">{previewResult.balanceScore}</p>
            </div>
            <div className="rounded-lg border border-outline-variant bg-surface p-3">
              <p className="text-label-sm text-on-surface-variant">Xung đột</p>
              <p className={`mt-1 text-title-lg font-bold ${previewResult.conflictCount > 0 ? "text-error" : "text-secondary"}`}>{previewResult.conflictCount}</p>
            </div>
          </div>

          <div className="overflow-hidden rounded-lg border border-outline-variant">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-outline-variant bg-surface-container-low px-4 py-3">
              <p className="text-label-sm text-on-surface-variant">
                Phương án — đang hiển thị {visibleRows.length}/{sortedRows.length} dòng, tối đa {PAGE_SIZE} dòng mỗi trang
              </p>
              <div className="flex items-center gap-3">
                {editedPreview.length > 0 && (
                  <button type="button" onClick={onResetEdits} className="text-label-sm text-error transition-colors hover:text-error/80">
                    Hủy thay đổi
                  </button>
                )}
                {totalPages > 1 && (
                  <div className="flex items-center gap-2 text-label-sm text-on-surface-variant">
                    <button
                      type="button"
                      onClick={() => setPage((value) => Math.max(1, value - 1))}
                      disabled={safePage === 1}
                      className="rounded border border-outline-variant px-2 py-1 disabled:opacity-50"
                      aria-label="Trang preview trước"
                    >
                      Trước
                    </button>
                    <span>{safePage}/{totalPages}</span>
                    <button
                      type="button"
                      onClick={() => setPage((value) => Math.min(totalPages, value + 1))}
                      disabled={safePage === totalPages}
                      className="rounded border border-outline-variant px-2 py-1 disabled:opacity-50"
                      aria-label="Trang preview sau"
                    >
                      Sau
                    </button>
                  </div>
                )}
              </div>
            </div>
            <div className="max-h-80 overflow-x-auto overflow-y-auto">
              <table className="w-full text-left">
                <thead className="sticky top-0 z-10 border-b border-outline-variant bg-surface-container-low">
                  <tr>
                    <th className="px-3 py-2 text-label-xs uppercase text-on-surface-variant">Ngày</th>
                    <th className="px-3 py-2 text-label-xs uppercase text-on-surface-variant">Loại lịch</th>
                    <th className="px-3 py-2 text-label-xs uppercase text-on-surface-variant">Nhân sự</th>
                    <th className="w-28 px-3 py-2 text-center text-label-xs uppercase text-on-surface-variant">Tình trạng</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant">
                  {visibleRows.map((item, index) => {
                    const editKey = `${item.workDate}-${item.shiftTypeId}`;
                    const currentStaffId = editedMap.get(editKey) ?? item.staffId;
                    const isEdited = editedMap.has(editKey);
                    const hasPotentialConflict = conflictKeys.has(`${item.workDate.split("T")[0]}-${item.shiftTypeId}`);
                    const badge = SHIFT_TYPE_BADGES[item.shiftTypeId as ScheduleTab] ?? "bg-surface-container-low text-on-surface border-outline-variant";

                    return (
                      <tr key={`${item.workDate}-${item.shiftTypeId}-${item.staffId}-${index}`} className={`transition-colors hover:bg-surface-container-low ${hasPotentialConflict ? "bg-error-container/30" : ""}`}>
                        <td className="whitespace-nowrap px-3 py-2 text-label-sm text-on-surface">
                          {new Date(item.workDate).toLocaleDateString("vi-VN")}
                        </td>
                        <td className="px-3 py-2 text-label-sm text-on-surface">
                          <span className={`inline-flex items-center gap-1.5 rounded border px-2 py-0.5 text-[11px] font-semibold ${badge}`}>
                            {item.shiftTypeName}
                          </span>
                        </td>
                        <td className="px-3 py-2">
                          <div className="relative">
                            <select
                              value={currentStaffId}
                              onChange={(event) => onEditStaff(item.workDate, item.shiftTypeId, Number(event.target.value))}
                              className="h-8 w-full max-w-[220px] cursor-pointer appearance-none rounded border border-transparent bg-surface pl-2 pr-7 text-label-sm transition-colors hover:border-primary focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20"
                              aria-label={`Chọn nhân sự cho ${item.shiftTypeName} ngày ${new Date(item.workDate).toLocaleDateString("vi-VN")}`}
                            >
                              {activeStaff.map((staff) => (
                                <option key={staff.id} value={staff.id}>{staff.fullName}</option>
                              ))}
                            </select>
                            <span className="material-symbols-outlined pointer-events-none absolute right-1 top-1/2 -translate-y-1/2 text-[14px] text-outline" aria-hidden="true">expand_more</span>
                          </div>
                        </td>
                        <td className="px-3 py-2 text-center">
                          <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold ${hasPotentialConflict ? "bg-error-container text-error" : isEdited ? "bg-amber-50 text-amber-700" : "bg-secondary-container text-on-secondary-container"}`}>
                            <span className="material-symbols-outlined text-[14px]" aria-hidden="true">{hasPotentialConflict ? "warning" : isEdited ? "edit" : "auto_mode"}</span>
                            {hasPotentialConflict ? "Cần kiểm tra" : isEdited ? "Đã sửa" : "Tự động"}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={onApplyPreview}
              disabled={applyingPreview || !previewResult}
              className="flex items-center gap-2 rounded-lg bg-secondary px-4 py-2 text-label-md font-medium text-on-secondary transition-colors hover:bg-secondary/90 disabled:cursor-not-allowed disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {applyingPreview ? (
                <><div className="size-4 animate-spin rounded-full border-2 border-on-secondary border-t-transparent" aria-hidden="true" />Đang áp dụng…</>
              ) : (
                <><span className="material-symbols-outlined text-[18px]" aria-hidden="true">check_circle</span>Áp dụng phương án{editedPreview.length > 0 ? ` (${editedPreview.length} thay đổi)` : ""}</>
              )}
            </button>
            {editedPreview.length > 0 && (
              <p className="text-label-sm text-on-surface-variant">{editedPreview.length} thay đổi đang chờ áp dụng</p>
            )}
          </div>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-3 p-8">
          <EmptyState icon="auto_mode" title="Chưa có preview" description="Hãy chạy Auto Schedule để tạo phương án trước khi áp dụng." />
          {runningAutoSchedule && (
            <div className="flex items-center gap-2 text-label-sm text-on-surface-variant" aria-live="polite">
              <div className="size-4 animate-spin rounded-full border-2 border-primary border-t-transparent" aria-hidden="true" />
              Đang tạo preview...
            </div>
          )}
          {message && (
            <div className={`${statusMessageIsSuccess ? "border-secondary/20 bg-secondary-container text-on-secondary-container" : "border-error/20 bg-error-container text-error"} flex max-w-sm items-center gap-2 rounded-lg border px-4 py-2 text-center text-label-sm`} role="status">
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{statusMessageIsSuccess ? "check_circle" : "error"}</span>
              {message}
            </div>
          )}
          {!isDraft && (
            <div className="flex items-center gap-1.5 rounded-lg bg-tertiary-container px-3 py-1.5 text-label-sm text-on-tertiary-container">
              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">info</span>
              Chỉ kỳ lịch ở trạng thái <strong>DRAFT</strong> mới có thể xếp tự động
            </div>
          )}
          <div className="flex flex-wrap items-center justify-center gap-3">
            <div className="relative">
              <select
                className="h-10 cursor-pointer appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest pl-3 pr-8 text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20"
                value={algorithmType}
                onChange={(event) => onSetAlgorithmType(event.target.value as AlgorithmType)}
                aria-label="Chọn thuật toán auto schedule"
              >
                {ALGORITHM_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
              <span className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-[20px] text-outline" aria-hidden="true">expand_more</span>
            </div>
            <button
              type="button"
              onClick={onPreview}
              disabled={runningAutoSchedule || !selectedPeriodId || !isDraft}
              className="rounded-lg bg-primary px-4 py-2 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {runningAutoSchedule ? "Đang tạo preview…" : "Chạy Auto Schedule"}
            </button>
          </div>
        </div>
      )}
    </SectionCard>
  );
});
