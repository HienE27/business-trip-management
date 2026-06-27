"use client";

import { memo } from "react";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import type { CompensationDay, Schedule } from "@/types/api";

export type ScheduleCalendarSectionProps = {
  schedules: Schedule[];
  activeStaff: { id: number; fullName: string }[];
  selectedPeriodId: number | null;
  initialYear: number;
  initialMonth: number;
  selectedTab: string;
  compensationDays?: CompensationDay[];
  onRefresh: () => void;
  onAddDate: (date: Date) => void;
  onFilterTypeChange: (filter: string) => void;
  onViewDetail: (schedule: Schedule) => void;
  hideFilters?: boolean;
  /** Force read-only mode on the calendar (hides FAB and write actions) */
  isReadOnly?: boolean;
};

export const ScheduleCalendarSection = memo(function ScheduleCalendarSection({
  schedules,
  activeStaff,
  selectedPeriodId,
  initialYear,
  initialMonth,
  selectedTab,
  compensationDays,
  onRefresh,
  onAddDate,
  onFilterTypeChange,
  onViewDetail,
  hideFilters = false,
  isReadOnly = false,
}: ScheduleCalendarSectionProps) {
  return (
    <ScheduleCalendarWidget
      schedules={schedules}
      staffList={activeStaff}
      initialYear={initialYear}
      initialMonth={initialMonth}
      periodId={selectedPeriodId}
      selectedTab={selectedTab}
      onFilterTypeChange={onFilterTypeChange}
      compensationDays={compensationDays}
      onRefresh={onRefresh}
      onAddClick={hideFilters ? undefined : onAddDate}
      onViewDetail={onViewDetail}
      isReadOnly={isReadOnly}
      hideFilters={hideFilters}
    />
  );
});
