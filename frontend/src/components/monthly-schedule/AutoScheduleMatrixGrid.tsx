"use client";

import { useEffect, useMemo, useState, useCallback } from "react";
import { ScheduleMatrixGrid } from "@/components/dashboard/ScheduleMatrixGrid";
import type { Schedule } from "@/types/api";
import type { AutoScheduleSummary } from "@/types/api";

export type ViewMode = "month" | "week";

export type AutoScheduleMatrixGridProps = {
  schedules: AutoScheduleSummary[];
  activeStaff: { id: number; fullName: string }[];
  year: number;
  month: number;
  viewMode: ViewMode;
  filteredStaffIds: Set<number>;
  editedPreview: Array<{ workDate: string; shiftTypeId: string; staffId: number }>;
  onViewDetail?: (schedule: Schedule) => void;
  onCellClick?: (date: Date, staffId: number) => void;
  onRefresh?: () => void;
};

function adaptToSchedule(item: AutoScheduleSummary): Schedule {
  return {
    id: item.scheduleId ?? 0,
    periodId: 0,
    staff: {
      id: item.staffId,
      fullName: item.staffName,
    },
    shiftType: {
      id: item.shiftTypeId,
      name: item.shiftTypeName,
    },
    workDate: item.workDate,
    notes: "",
    isActive: true,
    createdAt: "",
    updatedAt: "",
  } as Schedule;
}

/** Parse YYYY-MM-DD (or YYYY-MM-DDTHH:...) into a Date at local midnight */
function parseLocalDate(dateStr: string): Date {
  const [y, m, d] = dateStr.split("T")[0].split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** Given any Date, return Monday of that week as a local-midnight Date */
function getMondayOf(date: Date): Date {
  const dow = (date.getDay() + 6) % 7; // Mon=0
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - dow);
}

/** Return Mon–Sun for the week containing the given date */
function getWeekRange(containingDate: Date): { weekStart: Date; weekEnd: Date } {
  const monday = getMondayOf(containingDate);
  const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6);
  return { weekStart: monday, weekEnd: sunday };
}

/** Return Mon–Sun for the week starting N weeks after/from base */
function getWeekRangeOffset(base: Date, weeksOffset: number): { weekStart: Date; weekEnd: Date } {
  const newMonday = new Date(base.getFullYear(), base.getMonth(), base.getDate() + weeksOffset * 7);
  return getWeekRange(newMonday);
}

/** Find the earliest schedule date */
function getEarliestDate(
  schedules: AutoScheduleSummary[],
  editedPreview: Array<{ workDate: string; shiftTypeId: string; staffId: number }>
): Date | null {
  let earliest: Date | null = null;
  for (const s of schedules) {
    const d = parseLocalDate(s.workDate);
    if (!earliest || d < earliest) earliest = d;
  }
  for (const e of editedPreview) {
    const d = parseLocalDate(e.workDate);
    if (!earliest || d < earliest) earliest = d;
  }
  return earliest;
}

function formatWeekLabel(weekStart: Date, weekEnd: Date): string {
  const fmt = (d: Date) =>
    `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")}/${d.getFullYear()}`;
  return `${fmt(weekStart)} – ${fmt(weekEnd)}`;
}

export function AutoScheduleMatrixGrid({
  schedules,
  activeStaff,
  year,
  month,
  viewMode,
  filteredStaffIds,
  editedPreview,
  onViewDetail,
  onCellClick,
  onRefresh,
}: AutoScheduleMatrixGridProps) {
  // Filter staff columns
  const filteredStaff = useMemo(
    () =>
      filteredStaffIds.size === 0
        ? activeStaff
        : activeStaff.filter((s) => filteredStaffIds.has(s.id)),
    [activeStaff, filteredStaffIds]
  );

  // Filter schedules to only selected staff
  const filteredSchedules = useMemo(
    () =>
      filteredStaffIds.size === 0
        ? schedules
        : schedules.filter((s) => filteredStaffIds.has(s.staffId)),
    [schedules, filteredStaffIds]
  );

  const adapted = useMemo<Schedule[]>(
    () => filteredSchedules.map(adaptToSchedule),
    [filteredSchedules]
  );

  const adaptedWithEdits = useMemo<Schedule[]>(() => {
    if (editedPreview.length === 0) return adapted;
    const base = new Map(adapted.map((s) => [`${s.workDate}_${s.shiftType.id}_${s.staff.id}`, s]));
    for (const edit of editedPreview) {
      const key = `${edit.workDate}_${edit.shiftTypeId}_${edit.staffId}`;
      base.set(key, {
        ...adaptToSchedule({ scheduleId: null, staffId: edit.staffId, staffName: "", workDate: edit.workDate, shiftTypeId: edit.shiftTypeId, shiftTypeName: "" }),
        id: 0,
      });
    }
    return Array.from(base.values());
  }, [adapted, editedPreview]);

  // Derive the anchor Monday for week navigation.
  // On mount / when schedules change, anchor resets to the earliest schedule's week.
  const anchorMonday = useMemo(() => {
    const earliest = getEarliestDate(filteredSchedules, editedPreview);
    if (earliest) return getMondayOf(earliest);
    return getMondayOf(new Date());
  }, [filteredSchedules, editedPreview]);

  // currentWeekOffset: 0 = anchor week, -1 = prev, +1 = next, etc.
  const [weekOffset, setWeekOffset] = useState(0);

  const currentWeek = useMemo(
    () => getWeekRangeOffset(anchorMonday, weekOffset),
    [anchorMonday, weekOffset]
  );

  const weekRange = useMemo(() => {
    if (viewMode !== "week") return undefined;
    return currentWeek;
  }, [viewMode, currentWeek]);

  const handlePrevWeek = useCallback(() => {
    setWeekOffset((o) => o - 1);
  }, []);

  const handleNextWeek = useCallback(() => {
    setWeekOffset((o) => o + 1);
  }, []);

  // When switching back to week mode, reset offset to 0
  useEffect(() => {
    if (viewMode === "week") setWeekOffset(0);
  }, [viewMode]);

  // Year/month for the grid
  const gridYear = useMemo(() => {
    if (viewMode === "week" && weekRange) return weekRange.weekStart.getFullYear();
    return year;
  }, [viewMode, weekRange, year]);

  const gridMonth = useMemo(() => {
    if (viewMode === "week" && weekRange) return weekRange.weekStart.getMonth();
    return month;
  }, [viewMode, weekRange, month]);

  return (
    <div className="flex flex-col gap-3">
      {viewMode === "week" && (
        <div className="flex items-center justify-between rounded-lg border border-outline-variant bg-surface-container-low px-4 py-2">
          <button
            type="button"
            onClick={handlePrevWeek}
            className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-surface-container-high transition-colors text-on-surface"
            title="Tuần trước"
          >
            <span className="material-symbols-outlined text-[22px]">chevron_left</span>
          </button>

          <span className="font-label-md text-label-md text-on-surface min-w-[200px] text-center">
            {weekRange ? formatWeekLabel(weekRange.weekStart, weekRange.weekEnd) : ""}
          </span>

          <button
            type="button"
            onClick={handleNextWeek}
            className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-surface-container-high transition-colors text-on-surface"
            title="Tuần sau"
          >
            <span className="material-symbols-outlined text-[22px]">chevron_right</span>
          </button>
        </div>
      )}

      <ScheduleMatrixGrid
        schedules={adaptedWithEdits}
        staffList={filteredStaff}
        year={gridYear}
        month={gridMonth}
        weekStart={weekRange?.weekStart}
        weekEnd={weekRange?.weekEnd}
        onViewDetail={onViewDetail}
        onCellClick={onCellClick}
        onRefresh={onRefresh}
        canEdit={false}
      />
    </div>
  );
}
