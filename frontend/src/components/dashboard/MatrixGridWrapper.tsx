"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ScheduleMatrixGrid } from "@/components/dashboard/ScheduleMatrixGrid";
import type { Schedule } from "@/types/api";

export type MatrixViewMode = "month" | "week";

export type MatrixGridWrapperProps = {
  schedules: Schedule[];
  staffList: { id: number; fullName: string }[];
  /** Year for month-mode grid */
  year: number;
  /** Month (0-indexed) for month-mode grid */
  month: number;
  /** View mode: month = whole month, week = 7-day navigation */
  viewMode: MatrixViewMode;
  /** Compensation days */
  compensationDays?: import("@/types/api").CompensationDay[];
  /** Filter by shift type (L01/L02/L03/L04) */
  shiftTypeFilter?: string;
  onViewDetail?: (schedule: Schedule) => void;
  onCellClick?: (date: Date, staffId: number) => void;
  onRefresh?: () => void;
  canEdit?: boolean;
};

function parseLocalDate(dateStr: string): Date {
  const [y, m, d] = dateStr.split("T")[0].split("-").map(Number);
  return new Date(y, m - 1, d);
}

function getMondayOf(date: Date): Date {
  const dow = (date.getDay() + 6) % 7;
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - dow);
}

function getWeekRangeOffset(base: Date, weeksOffset: number) {
  const newMonday = new Date(base.getFullYear(), base.getMonth(), base.getDate() + weeksOffset * 7);
  const dow = (newMonday.getDay() + 6) % 7;
  const monday = new Date(newMonday.getFullYear(), newMonday.getMonth(), newMonday.getDate() - dow);
  const sunday = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6);
  return { weekStart: monday, weekEnd: sunday };
}

function formatWeekLabel(weekStart: Date, weekEnd: Date): string {
  const fmt = (d: Date) =>
    `${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")}/${d.getFullYear()}`;
  return `${fmt(weekStart)} – ${fmt(weekEnd)}`;
}

/** Derive week Mon–Sun from earliest schedule date */
function deriveWeekFromSchedules(schedules: Schedule[]): { weekStart: Date; weekEnd: Date } {
  let earliest: Date | null = null;
  for (const s of schedules) {
    const d = parseLocalDate(s.workDate);
    if (!earliest || d < earliest) earliest = d;
  }
  if (earliest) {
    const monday = getMondayOf(earliest);
    return { weekStart: monday, weekEnd: new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + 6) };
  }
  return getWeekRangeOffset(new Date(), 0);
}

export function MatrixGridWrapper({
  schedules,
  staffList,
  year,
  month,
  viewMode,
  compensationDays,
  shiftTypeFilter,
  onViewDetail,
  onCellClick,
  onRefresh,
  canEdit,
}: MatrixGridWrapperProps) {
  const [weekOffset, setWeekOffset] = useState(0);

  // Derive anchor Monday from schedules — memoized, runs once per schedules array ref
  const anchorMonday = useMemo(() => {
    const { weekStart } = deriveWeekFromSchedules(schedules);
    return weekStart;
  }, [schedules]);

  // Sync weekOffset to 0 when a new schedule period loads (schedules.length changes)
  // Use flushSync to update state synchronously and avoid double-render
  const prevSchedulesLen = useRef(schedules.length);
  if (schedules.length !== prevSchedulesLen.current) {
    prevSchedulesLen.current = schedules.length;
    setWeekOffset(0);
  }

  const currentWeek = useMemo(
    () => getWeekRangeOffset(anchorMonday, weekOffset),
    [anchorMonday, weekOffset]
  );

  const weekRange = viewMode === "week" ? currentWeek : undefined;

  const gridYear = viewMode === "week" && weekRange ? weekRange.weekStart.getFullYear() : year;
  const gridMonth = viewMode === "week" && weekRange ? weekRange.weekStart.getMonth() : month;

  const handlePrevWeek = useCallback(() => setWeekOffset((o) => o - 1), []);
  const handleNextWeek = useCallback(() => setWeekOffset((o) => o + 1), []);

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
        schedules={schedules}
        staffList={staffList}
        year={gridYear}
        month={gridMonth}
        weekStart={weekRange?.weekStart}
        weekEnd={weekRange?.weekEnd}
        compensationDays={compensationDays}
        shiftTypeFilter={shiftTypeFilter}
        onViewDetail={onViewDetail}
        onCellClick={onCellClick}
        onRefresh={onRefresh}
        canEdit={canEdit}
      />
    </div>
  );
}
