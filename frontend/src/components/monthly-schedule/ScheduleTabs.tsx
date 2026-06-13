"use client";

import { memo, type ReactNode } from "react";
import { SectionCard } from "@/components/ui/SectionCard";
import { TAB_OPTIONS } from "./constants";
import type { ScheduleTab, ViewMode } from "./types";

export type ScheduleTabsProps = {
  selectedTab: ScheduleTab;
  viewMode: ViewMode;
  onTabChange: (tab: ScheduleTab) => void;
  onViewChange: (view: ViewMode) => void;
  children: ReactNode;
};

export const ScheduleTabs = memo(function ScheduleTabs({ selectedTab, viewMode, onTabChange, onViewChange, children }: ScheduleTabsProps) {
  const activeDescription = TAB_OPTIONS.find((option) => option.id === selectedTab)?.description;

  return (
    <SectionCard
      title="Tabs loại lịch"
      description="Bốn loại lịch được điều phối trong cùng module lập lịch tháng."
      action={
        <div className="flex items-center gap-1 rounded-lg bg-surface-container-low p-1" aria-label="Chọn chế độ xem">
          <button
            type="button"
            onClick={() => onViewChange("calendar")}
            aria-label="Chế độ xem lịch theo tháng"
            aria-pressed={viewMode === "calendar"}
            className={`rounded-md px-3 py-1.5 text-label-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${viewMode === "calendar" ? "bg-surface-container-lowest text-primary shadow-sm" : "text-on-surface-variant"}`}
          >
            Calendar View
          </button>
          <button
            type="button"
            onClick={() => onViewChange("table")}
            aria-label="Chế độ xem bảng danh sách"
            aria-pressed={viewMode === "table"}
            className={`rounded-md px-3 py-1.5 text-label-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${viewMode === "table" ? "bg-surface-container-lowest text-primary shadow-sm" : "text-on-surface-variant"}`}
          >
            Table View
          </button>
        </div>
      }
    >
      <div className="border-b border-outline-variant px-5 pt-4">
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Loại lịch">
          {TAB_OPTIONS.map((option) => {
            const isActive = selectedTab === option.id;
            return (
              <button
                key={option.id}
                type="button"
                onClick={() => onTabChange(option.id)}
                aria-selected={isActive}
                aria-controls="monthly-schedule-panel"
                role="tab"
                className={`inline-flex items-center gap-2 rounded-t-lg border border-b-0 px-4 py-2 text-label-md transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${isActive ? "border-primary bg-primary-fixed text-primary" : "border-outline-variant bg-surface text-on-surface-variant hover:bg-surface-container-low"}`}
              >
                <span className="rounded-full bg-surface-container-low px-2 py-0.5 text-[11px] font-semibold">{option.shortLabel}</span>
                {option.label}
              </button>
            );
          })}
        </div>
        <p className="pb-4 pt-3 text-body-sm text-on-surface-variant">{activeDescription}</p>
      </div>
      <div id="monthly-schedule-panel" role="tabpanel">
        {children}
      </div>
    </SectionCard>
  );
});
