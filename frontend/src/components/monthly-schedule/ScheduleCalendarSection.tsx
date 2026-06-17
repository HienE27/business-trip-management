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
  compensationDays?: CompensationDay[];
  onRefresh: () => void;
  onFocusDate: (date: string) => void;
  onAddDate: (date: Date) => void;
  onStaffFilterChange: (staffId: number | null) => void;
  onSpecialtyFilterChange: (specialtyId: number | null) => void;
  onViewDetail: (schedule: Schedule) => void;
  onViewModeChange: (view: ViewMode) => void;
  /** Khi true: ẩn filter trên toolbar calendar (dashboard). Mặc định false (monthly-schedule hiển thị filter). */
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
  compensationDays,
  onRefresh,
  onFocusDate,
  onAddDate,
  onStaffFilterChange,
  onSpecialtyFilterChange,
  onViewDetail,
  onViewModeChange,
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
      showViewToggle={false}
      compensationDays={compensationDays}
      hideFilters={hideFilters}
    />
  );
});
