"use client";

import { memo } from "react";
import Link from "next/link";
import { SectionCard } from "@/components/ui/SectionCard";
import type { SchedulePeriod } from "@/types/api";
import { formatDateRange, getStatusBadgeClass } from "./utils";
import { TAB_OPTIONS, SHIFT_TYPE_LABELS } from "./constants";
import type { ScheduleTab } from "./types";

export type ScheduleHeaderProps = {
  periods: SchedulePeriod[];
  selectedPeriodId: number | null;
  selectedPeriod: SchedulePeriod | null;
  selectedTab: ScheduleTab;
  refreshing: boolean;
  exporting: boolean;
  checkingConflicts: boolean;
  publishing: boolean;
  canPublish: boolean;
  onPeriodChange: (periodId: number) => void;
  onTabChange: (tab: ScheduleTab) => void;
  onRefresh: () => void;
  onExport: () => void;
  onCheckConflicts: () => void;
  onPublish: () => void;
  onShowSummary: () => void;
};

export const ScheduleHeader = memo(function ScheduleHeader({
  periods,
  selectedPeriodId,
  selectedPeriod,
  selectedTab,
  refreshing,
  exporting,
  checkingConflicts,
  publishing,
  canPublish,
  onPeriodChange,
  onTabChange,
  onRefresh,
  onExport,
  onCheckConflicts,
  onPublish,
  onShowSummary,
}: ScheduleHeaderProps) {
  const isDraft = selectedPeriod?.status === "DRAFT";
  const hasSelectedPeriod = selectedPeriodId !== null;

  const statusLabel: Record<string, string> = {
    DRAFT: "Nháp",
    PUBLISHED: "Đã công bố",
    ARCHIVED: "Đã lưu trữ",
  };

  return (
    <SectionCard
      title="Kỳ lịch đang vận hành"
      description="Kỳ lịch hiện tại — chọn kỳ, kiểm tra xung đột, xuất báo cáo và công bố lịch."
      action={
        <div className="flex flex-wrap items-center gap-2">
          <select
            className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20"
            value={selectedPeriodId ?? ""}
            onChange={(event) => onPeriodChange(Number(event.target.value))}
            aria-label="Chọn kỳ lịch"
          >
            {periods.map((period) => (
              <option key={period.id} value={period.id}>{period.periodName}</option>
            ))}
          </select>
          <select
            className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20"
            value={selectedTab}
            onChange={(event) => onTabChange(event.target.value as ScheduleTab)}
            aria-label="Chọn loại lịch"
          >
            <option value="ALL">Tất cả loại lịch</option>
            {TAB_OPTIONS.map((option) => (
              <option key={option.id} value={option.id}>{SHIFT_TYPE_LABELS[option.id] ?? option.label}</option>
            ))}
          </select>
          <button
            type="button"
            onClick={onRefresh}
            className="inline-flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2 text-label-md text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            aria-label="Làm mới dữ liệu kỳ lịch"
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">sync</span>
            {refreshing ? "Đang tải..." : "Làm mới"}
          </button>
          <button
            type="button"
            onClick={onExport}
            disabled={!hasSelectedPeriod || exporting}
            className="inline-flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2 text-label-md text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            aria-label="Xuất Excel kỳ lịch"
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">download</span>
            {exporting ? "Đang xuất..." : "Xuất Excel"}
          </button>
        </div>
      }
    >
      <div className="grid gap-3 p-3 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-label-sm font-semibold ${getStatusBadgeClass(selectedPeriod?.status)}`}>
              <span className="material-symbols-outlined text-[20px]" aria-hidden="true">event_available</span>
              {selectedPeriod ? (statusLabel[selectedPeriod.status] ?? selectedPeriod.status) : "—"}
            </span>
            <span className="text-label-md text-on-surface-variant">{formatDateRange(selectedPeriod)}</span>
          </div>
          <div>
            <h2 className="text-headline-md text-on-surface">{selectedPeriod?.periodName ?? "Chưa có kỳ lịch"}</h2>
            <p className="mt-0.5 text-label-md leading-5 text-on-surface-variant">
              Kỳ lịch — xếp lịch tự động, kiểm tra xung đột, rà soát và công bố.
            </p>
          </div>
        </div>

        <div className="grid gap-2 lg:grid-cols-1">
          <Link
            href="/auto-scheduling"
            className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[16px]">auto_mode</span>
            Tự động xếp lịch
          </Link>
          <button
            type="button"
            onClick={onCheckConflicts}
            className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            disabled={checkingConflicts || !hasSelectedPeriod || !isDraft}
          >
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">warning</span>
            {checkingConflicts ? "Đang kiểm tra..." : "Kiểm tra xung đột"}
          </button>
          {canPublish && (
            <button
              type="button"
              onClick={onPublish}
              className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              disabled={publishing || !hasSelectedPeriod || !isDraft}
            >
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">publish</span>
              {publishing ? "Đang công bố..." : "Công bố lịch"}
            </button>
          )}
          <button
            type="button"
            onClick={onShowSummary}
            className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">assessment</span>
            Rà soát & Báo cáo
          </button>
        </div>
      </div>
    </SectionCard>
  );
});
