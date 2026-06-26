"use client";

import { memo } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import type { SchedulePeriod } from "@/types/api";
import { formatDateRange } from "./utils";
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

  const statusConfig = {
    DRAFT: { label: "Nháp", tone: "info" as const },
    PUBLISHED: { label: "Đã công bố", tone: "success" as const },
    ARCHIVED: { label: "Đã lưu trữ", tone: "neutral" as const },
  };
  const statusInfo = selectedPeriod ? statusConfig[selectedPeriod.status as keyof typeof statusConfig] : null;

  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant shadow-sm overflow-hidden">
      {/* Compact Header Row */}
      <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low">
        <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-3">
          {/* Left: Period selector + status */}
          <div className="flex flex-wrap items-center gap-3">
            <select
              className="h-9 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 appearance-none cursor-pointer"
              value={selectedPeriodId ?? ""}
              onChange={(event) => onPeriodChange(Number(event.target.value))}
              aria-label="Chọn kỳ lịch"
            >
              {periods.map((period) => (
                <option key={period.id} value={period.id}>{period.periodName}</option>
              ))}
            </select>
            {statusInfo && (
              <Badge tone={statusInfo.tone} dot showDot size="sm">
                {statusInfo.label}
              </Badge>
            )}
            <span className="text-label-xs text-on-surface-variant hidden sm:inline">
              {formatDateRange(selectedPeriod)}
            </span>
            <select
              className="h-9 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 appearance-none cursor-pointer"
              value={selectedTab}
              onChange={(event) => onTabChange(event.target.value as ScheduleTab)}
              aria-label="Chọn loại lịch"
            >
              <option value="ALL">Tất cả loại</option>
              {TAB_OPTIONS.map((option) => (
                <option key={option.id} value={option.id}>{SHIFT_TYPE_LABELS[option.id] ?? option.label}</option>
              ))}
            </select>
          </div>

          {/* Right: Action buttons */}
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={onRefresh}
              loading={refreshing}
              icon={<span className="material-symbols-outlined text-[16px]">sync</span>}
            />
            <Button
              variant="ghost"
              size="sm"
              onClick={onExport}
              disabled={!hasSelectedPeriod || exporting}
              loading={exporting}
              icon={<span className="material-symbols-outlined text-[16px]">download</span>}
            />
          </div>
        </div>
      </div>

      {/* Body: Period info + Action buttons */}
      <div className="p-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          {/* Left: Period name + description */}
          <div className="min-w-0">
            <h2 className="text-headline-md text-on-surface truncate">
              {selectedPeriod?.periodName ?? "Chưa có kỳ lịch"}
            </h2>
            <p className="mt-0.5 text-label-sm text-on-surface-variant">
              {isDraft ? "Đang soạn thảo" : selectedPeriod?.status === "PUBLISHED" ? "Đã công bố" : "Đã lưu trữ"}
              {" · "}{formatDateRange(selectedPeriod)}
            </p>
          </div>

          {/* Right: Primary actions */}
          <div className="flex flex-wrap items-center gap-2">
            <Link href="/auto-scheduling">
              <Button
                variant="primary"
                size="sm"
                icon={<span className="material-symbols-outlined text-[16px]">auto_mode</span>}
              >
                Tự động xếp lịch
              </Button>
            </Link>
            <Button
              variant="secondary"
              size="sm"
              onClick={onCheckConflicts}
              disabled={checkingConflicts || !hasSelectedPeriod || !isDraft}
              loading={checkingConflicts}
              icon={<span className="material-symbols-outlined text-[16px]">warning</span>}
            >
              Kiểm tra xung đột
            </Button>
            {canPublish && (
              <Button
                variant="secondary"
                size="sm"
                onClick={onPublish}
                disabled={publishing || !hasSelectedPeriod || !isDraft}
                loading={publishing}
                icon={<span className="material-symbols-outlined text-[16px]">publish</span>}
              >
                Công bố lịch
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={onShowSummary}
              icon={<span className="material-symbols-outlined text-[16px]">assessment</span>}
            >
              Rà soát
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
});
