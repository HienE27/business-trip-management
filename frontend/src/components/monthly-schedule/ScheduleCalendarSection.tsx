"use client";

import { memo } from "react";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import type { CompensationDay, Schedule, Specialty, Staff } from "@/types/api";
import type { CalendarAnnotation, ViewMode } from "./types";

export type ScheduleCalendarSectionProps = {
  schedules: Schedule[];
  calendarAnnotations: CalendarAnnotation[];
  coverages: Record<string, { required: number; assigned: number }>;
  activeStaff: Staff[];
  specialties: Specialty[];
  staffFilterId: number | null;
  specialtyFilterId: number | null;
  selectedPeriodId: number | null;
  initialYear: number;
  initialMonth: number;
  viewMode: ViewMode;
  selectedTab: string;
  compensationDays?: CompensationDay[];
  onRefresh: () => void;
  onFocusDate: (date: string) => void;
  onAddDate: (date: Date) => void;
  onStaffFilterChange: (staffId: number | null) => void;
  onSpecialtyFilterChange: (specialtyId: number | null) => void;
  onViewDetail: (schedule: Schedule) => void;
  onViewModeChange: (view: ViewMode) => void;
  onFilterTypeChange: (filter: string) => void;
  /** Show the calendar/table/matrix view toggle. Default: true */
  showViewToggle?: boolean;
  /** Hide filter bar (tab type filters). Default: false */
  hideFilters?: boolean;
};

export const ScheduleCalendarSection = memo(function ScheduleCalendarSection({
  schedules,
  calendarAnnotations,
  coverages,
  activeStaff,
  specialties,
  staffFilterId,
  specialtyFilterId,
  selectedPeriodId,
  initialYear,
  initialMonth,
  viewMode,
  selectedTab,
  compensationDays,
  onRefresh,
  onFocusDate,
  onAddDate,
  onStaffFilterChange,
  onSpecialtyFilterChange,
  onViewDetail,
  onViewModeChange,
  onFilterTypeChange,
  showViewToggle = true,
  hideFilters = false,
}: ScheduleCalendarSectionProps) {
  return (
    <ScheduleCalendarWidget
      schedules={schedules}
      calendarAnnotations={calendarAnnotations}
      coverages={coverages}
      staffList={activeStaff}
      specialtyList={specialties}
      onRefresh={onRefresh}
      onDayClick={(date) => onFocusDate(date.toISOString().slice(0, 10))}
      onAddClick={onAddDate}
      staffFilter={staffFilterId}
      specialtyFilter={specialtyFilterId}
      onStaffFilterChange={onStaffFilterChange}
      onSpecialtyFilterChange={onSpecialtyFilterChange}
      initialYear={initialYear}
      initialMonth={initialMonth}
      periodId={selectedPeriodId}
      onViewDetail={onViewDetail}
      viewMode={viewMode}
      onViewModeChange={onViewModeChange}
      selectedTab={selectedTab}
      onFilterTypeChange={onFilterTypeChange}
      showViewToggle={showViewToggle}
      compensationDays={compensationDays}
      hideFilters={hideFilters}
    />
  );
});
