"use client";

import { memo } from "react";
import Link from "next/link";
import { SectionCard } from "@/components/ui/SectionCard";
import type { SchedulePeriod } from "@/types/api";
import { formatDateRange, getStatusBadgeClass } from "./utils";

export type ScheduleHeaderProps = {
  periods: SchedulePeriod[];
  selectedPeriodId: number | null;
  selectedPeriod: SchedulePeriod | null;
  refreshing: boolean;
  exporting: boolean;
  checkingConflicts: boolean;
  publishing: boolean;
  canPublish: boolean;
  onPeriodChange: (periodId: number) => void;
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
  refreshing,
  exporting,
  checkingConflicts,
  publishing,
  canPublish,
  onPeriodChange,
  onRefresh,
  onExport,
  onCheckConflicts,
  onPublish,
  onShowSummary,
}: ScheduleHeaderProps) {
  const isDraft = selectedPeriod?.status === "DRAFT";
  const hasSelectedPeriod = selectedPeriodId !== null;

  return (
    <SectionCard
      title="Kỳ lịch đang vận hành"
      description="Gom tất cả thao tác điều phối vào một màn trung tâm thay cho các route CRUD rời rạc trước đây."
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
            {exporting ? "Đang xuất" : "Xuất Excel"}
          </button>
        </div>
      }
    >
      <div className="grid gap-4 p-5 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-3">
            <span className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-[12px] font-semibold ${getStatusBadgeClass(selectedPeriod?.status)}`}>
              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">event_available</span>
              {selectedPeriod?.status ?? "DRAFT"}
            </span>
            <span className="text-body-sm text-on-surface-variant">{formatDateRange(selectedPeriod)}</span>
          </div>
          <div>
            <h2 className="text-headline-md text-on-surface">{selectedPeriod?.periodName ?? "Chưa có kỳ lịch"}</h2>
            <p className="mt-1 text-body-sm leading-6 text-on-surface-variant">
              Luồng vận hành tập trung: auto schedule, conflict check, review, publish và notify.
            </p>
          </div>
        </div>

        <div className="grid gap-2 md:grid-cols-2 lg:grid-cols-1">
          <Link
            href="/auto-scheduling"
            className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[18px]">auto_mode</span>
            Auto Schedule
          </Link>
          <button
            type="button"
            onClick={onCheckConflicts}
            className="inline-flex items-center justify-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            disabled={checkingConflicts || !hasSelectedPeriod || !isDraft}
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">warning</span>
            {checkingConflicts ? "Đang kiểm tra" : "Conflict Check"}
          </button>
          {canPublish && (
            <button
              type="button"
              onClick={onPublish}
              className="inline-flex items-center justify-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              disabled={publishing || !hasSelectedPeriod || !isDraft}
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">publish</span>
              {publishing ? "Đang publish" : "Publish"}
            </button>
          )}
          <button
            type="button"
            onClick={onShowSummary}
            className="inline-flex items-center justify-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2.5 text-label-md font-medium text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">assessment</span>
            Review & Report
          </button>
        </div>
      </div>
    </SectionCard>
  );
});
