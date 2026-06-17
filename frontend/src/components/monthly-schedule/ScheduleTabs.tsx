"use client";

import { memo, type ReactNode } from "react";
import { SectionCard } from "@/components/ui/SectionCard";
import { TAB_OPTIONS } from "./constants";
import type { ScheduleTab } from "./types";

export type ScheduleTabsProps = {
  selectedTab: ScheduleTab;
  onTabChange: (tab: ScheduleTab) => void;
  children: ReactNode;
};

export const ScheduleTabs = memo(function ScheduleTabs({ selectedTab, onTabChange, children }: ScheduleTabsProps) {
  const activeDescription = TAB_OPTIONS.find((option) => option.id === selectedTab)?.description;

  return (
    <SectionCard
      title="Phân loại lịch"
      description="Chọn loại lịch để xem chi tiết."
    >
      <div className="sticky top-0 z-10 bg-surface-container-lowest border-b border-outline-variant px-4 pt-3">
        <div className="flex flex-wrap gap-1" role="tablist" aria-label="Loại lịch">
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
                className={`inline-flex items-center gap-1.5 rounded-t-lg border border-b-0 px-3 py-1.5 text-label-md transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${isActive ? "border-primary bg-primary-fixed text-primary" : "border-outline-variant bg-surface text-on-surface-variant hover:bg-surface-container-low"}`}
              >
                <span className="rounded-full bg-surface-container-low px-1.5 py-0.5 text-label-sm font-semibold">{option.shortLabel}</span>
                {option.label}
              </button>
            );
          })}
        </div>
        <p className="pb-3 pt-2 text-label-md text-on-surface-variant">{activeDescription}</p>
      </div>
      <div id="monthly-schedule-panel" role="tabpanel">
        {children}
      </div>
    </SectionCard>
  );
});
