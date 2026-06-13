"use client";

import { memo } from "react";
import Link from "next/link";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import { EmptyState } from "@/components/ui/EmptyState";
import type { Schedule, Specialty, Staff } from "@/types/api";
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
  onRefresh: () => void;
  onFocusDate: (date: string) => void;
  onAddDate: (date: Date) => void;
  onStaffFilterChange: (staffId: number | null) => void;
  onSpecialtyFilterChange: (specialtyId: number | null) => void;
  onViewDetail: (schedule: Schedule) => void;
  onViewModeChange: (view: ViewMode) => void;
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
  onRefresh,
  onFocusDate,
  onAddDate,
  onStaffFilterChange,
  onSpecialtyFilterChange,
  onViewDetail,
  onViewModeChange,
}: ScheduleCalendarSectionProps) {
  if (schedules.length === 0) {
    return (
      <EmptyState
        icon="calendar_month"
        title="Chưa có phân công cho loại lịch này"
        description="Hãy dùng tính năng Tự động xếp lịch hoặc thêm lịch thủ công."
        action={
          <Link
            href="/auto-scheduling"
            className="rounded-lg bg-primary px-4 py-2 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined align-middle mr-1 text-[18px]">auto_mode</span>
            Tự động xếp lịch
          </Link>
        }
      />
    );
  }

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
    />
  );
});
