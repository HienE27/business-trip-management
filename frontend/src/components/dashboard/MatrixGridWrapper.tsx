"use client";

import { useCallback, useEffect, useMemo, useState, memo } from "react";
import { ScheduleMatrixGrid } from "@/components/dashboard/ScheduleMatrixGrid";
import type { ConflictDetail, Schedule } from "@/types/api";

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
  /** Conflict dates — Set of date strings (YYYY-MM-DD) */
  conflictDates?: Set<string>;
  /** When true: only show rows that have conflicts */
  showConflictOnly?: boolean;
  onViewDetail?: (schedule: Schedule) => void;
  onEdit?: (schedule: Schedule) => void;
  onDelete?: (schedule: Schedule) => void;
  onResolve?: (conflict: ConflictDetail) => void;
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

export const MatrixGridWrapper = memo(function MatrixGridWrapper({
  schedules,
  staffList,
  year,
  month,
  viewMode,
  compensationDays,
  shiftTypeFilter,
  conflictDates,
  showConflictOnly: initialShowConflictOnly,
  onViewDetail,
  onEdit,
  onDelete,
  onResolve,
  onCellClick,
  onRefresh,
  canEdit,
}: MatrixGridWrapperProps) {
  const [weekOffset, setWeekOffset] = useState(0);
  const [searchQuery, setSearchQuery] = useState("");
  const [showConflictOnly, setShowConflictOnly] = useState(initialShowConflictOnly ?? false);

  // Derive anchor Monday from schedules — memoized, runs once per schedules array ref
  const anchorMonday = useMemo(() => {
    const { weekStart } = deriveWeekFromSchedules(schedules);
    return weekStart;
  }, [schedules]);

  // Sync weekOffset to 0 when a new schedule period loads (schedules.length changes)
  useEffect(() => {
    setWeekOffset(0);
  }, [schedules.length]);

  const currentWeek = useMemo(
    () => getWeekRangeOffset(anchorMonday, weekOffset),
    [anchorMonday, weekOffset]
  );

  const weekRange = viewMode === "week" ? currentWeek : undefined;

  const gridYear = viewMode === "week" && weekRange ? weekRange.weekStart.getFullYear() : year;
  const gridMonth = viewMode === "week" && weekRange ? weekRange.weekStart.getMonth() : month;

  const handlePrevWeek = useCallback(() => setWeekOffset((o) => o - 1), []);
  const handleNextWeek = useCallback(() => setWeekOffset((o) => o + 1), []);

  // Filter staff columns by search query
  const filteredStaffList = useMemo(() => {
    if (!searchQuery.trim()) return staffList;
    const q = searchQuery.toLowerCase();
    return staffList.filter((s) => s.fullName.toLowerCase().includes(q));
  }, [staffList, searchQuery]);

  return (
    <div className="flex flex-col gap-3">
      {/* Search bar */}
      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[20px]" aria-hidden="true">search</span>
          <input
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm kiếm nhân sự..."
            className="w-full pl-10 pr-4 py-2.5 bg-surface-container-low rounded-lg border border-transparent
              focus:border-primary focus:bg-surface-container-lowest focus:ring-1 focus:ring-primary/20 focus:outline-none
              text-body-sm text-on-surface placeholder:text-outline transition-all"
            aria-label="Tìm kiếm nhân sự trong ma trận"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-outline hover:text-on-surface transition-colors"
              aria-label="Xóa tìm kiếm"
            >
              <span className="material-symbols-outlined text-[18px]">close</span>
            </button>
          )}
        </div>
        {searchQuery && filteredStaffList.length > 0 && (
          <span className="text-label-sm text-on-surface-variant shrink-0">
            {filteredStaffList.length}/{staffList.length} nhân sự
          </span>
        )}
        {searchQuery && filteredStaffList.length === 0 && (
          <span className="text-label-sm text-error shrink-0">Không tìm thấy nhân sự</span>
        )}
      </div>

      {/* Conflict filter toggle */}
      {conflictDates && conflictDates.size > 0 && (
        <button
          type="button"
          onClick={() => setShowConflictOnly((v) => !v)}
          className={`flex items-center gap-1.5 rounded-lg px-3 py-2 text-label-sm font-medium transition-all border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
            showConflictOnly
              ? "bg-error-container text-on-error-container border-error/20"
              : "bg-surface-container-low text-on-surface-variant border-transparent hover:bg-surface-container-high hover:text-on-surface"
          }`}
          title="Chỉ hiện ngày có xung đột"
        >
          <span className="material-symbols-outlined text-[16px]">warning</span>
          Chỉ xung đột
          <span className={`inline-flex items-center justify-center w-5 h-5 rounded-full text-[11px] font-bold ${
            showConflictOnly ? "bg-error text-white" : "bg-error/20 text-error"
          }`}>
            {conflictDates.size}
          </span>
        </button>
      )}

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
        staffList={filteredStaffList}
        year={gridYear}
        month={gridMonth}
        weekStart={weekRange?.weekStart}
        weekEnd={weekRange?.weekEnd}
        compensationDays={compensationDays}
        shiftTypeFilter={shiftTypeFilter}
        conflictDates={showConflictOnly ? conflictDates : undefined}
        onViewDetail={onViewDetail}
        onEdit={onEdit}
        onDelete={onDelete}
        onResolve={onResolve}
        onCellClick={onCellClick}
        onRefresh={onRefresh}
        canEdit={canEdit}
      />
    </div>
  );
});
