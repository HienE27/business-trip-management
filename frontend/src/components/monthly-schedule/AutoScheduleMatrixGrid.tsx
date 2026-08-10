"use client";

import { useEffect, useMemo, useState, useCallback } from "react";
import { ScheduleMatrixGrid } from "@/components/dashboard/ScheduleMatrixGrid";
import { deriveCompensationDaysFromPreview } from "@/lib/schedule/previewCompensation";
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
  /**
   * Optional list of compensation days loaded from the backend (DB-stored
   * comp days for the period). Used to render "🌙 Nghỉ bù" cells alongside
   * any preview-derived comp days from L01 schedules in the current run.
   */
  compensationDays?: import("@/types/api").CompensationDay[];
  onViewDetail?: (schedule: Schedule) => void;
  /** Called when user clicks an item chip in the preview grid */
  onEditItem?: (item: AutoScheduleSummary) => void;
  /** Called when user clicks an empty cell to add a new assignment */
  onAddItem?: (date: Date, staffId: number) => void;
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
      isOvernight: false,
    },
    workDate: item.workDate,
    notes: "",
    hasConflict: false,
    createdAt: "",
    updatedAt: "",
  };
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
  compensationDays = [],
  onViewDetail,
  onEditItem,
  onAddItem,
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

  // Merge comp days from two sources:
  // 1. DB comp days (passed as prop) — available after Apply or for existing period data
  // 2. Derived from this run's L01 preview — available immediately after algorithm finishes
  //    (before Apply, DB may be empty because skipExisting clears it first)
  // The union of both ensures NB cells appear at all times during the workflow.
  const derivedCompDays = useMemo(() => {
    const derived = deriveCompensationDaysFromPreview(
      adaptedWithEdits.map((s) => ({
        staffId: s.staff.id,
        workDate: s.workDate,
        shiftTypeId: s.shiftType.id,
      })),
    );
    const derivedAsCompDays: import("@/types/api").CompensationDay[] = derived.map((d) => ({
      id: d.id,
      staffId: d.staffId,
      staffName: "",
      compensationDate: d.compensationDate,
      shiftDate: d.shiftDate,
    }));
    // De-duplicate: prefer DB comp days (positive id) over derived (negative id)
    const dbIds = new Set(compensationDays.map((c) => `${c.staffId}|${c.compensationDate.split("T")[0]}`));
    const filtered = derivedAsCompDays.filter(
      (d) => !dbIds.has(`${d.staffId}|${d.compensationDate}`),
    );
    return [...compensationDays, ...filtered];
  }, [adaptedWithEdits, compensationDays]);

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
        compensationDays={derivedCompDays}
        onViewDetail={onViewDetail}
        onItemClickOverride={onEditItem ? (schedule) => {
          const item = filteredSchedules.find(
            (s) => s.workDate === schedule.workDate && s.staffId === schedule.staff.id && s.shiftTypeId === schedule.shiftType.id
          );
          if (item) onEditItem(item);
        } : undefined}
        onCellClick={onAddItem}
        onRefresh={onRefresh}
        canEdit={false}
      />
    </div>
  );
}
