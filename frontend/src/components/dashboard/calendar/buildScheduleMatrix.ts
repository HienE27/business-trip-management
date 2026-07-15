"use client";

import type { Schedule, CompensationDay } from "@/types/api";
import { shiftTypeToTone, SHIFT_SHORT, type CalendarItem } from "./constants";

export interface MatrixRow {
  date: Date;
  dateStr: string;
  dayOfWeek: string;
  dayLabel: string;
  isWeekend: boolean;
  isCompensation: Map<number, boolean>; // staffId → true if this day is a comp day for this staff
  cells: Map<number, CalendarItem[]>; // staffId → items
  /** Pre-computed: number of staff cells that have at least one shift this day */
  rowCount: number;
}

export interface ScheduleMatrix {
  headerCols: { id: number; fullName: string }[];
  rows: MatrixRow[];
  dateRange: { start: string; end: string };
  mode: "month" | "week";
}

const DOW_VI = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"] as const;

export function buildScheduleMatrix(
  schedules: Schedule[],
  staffList: { id: number; fullName: string }[],
  year: number,
  month: number,
  compensationDays: CompensationDay[] = [],
  weekStart?: Date,
  weekEnd?: Date,
): ScheduleMatrix {
  const isWeekMode = weekStart !== undefined && weekEnd !== undefined;
  const isDayMode = isWeekMode &&
    weekStart!.getFullYear() === weekEnd!.getFullYear() &&
    weekStart!.getMonth() === weekEnd!.getMonth() &&
    weekStart!.getDate() === weekEnd!.getDate();
  const monthLastDay = new Date(year, month + 1, 0).getDate();
  const startDay = isWeekMode ? weekStart!.getDate() : 1;
  const endDay = isWeekMode ? weekEnd!.getDate() : monthLastDay;
  const isoWeekStart = isWeekMode ? weekStart! : new Date(year, month, 1);
  const isoWeekEnd = isWeekMode ? weekEnd! : new Date(year, month, monthLastDay);

  // Build comp day lookup: staffId → Set of date strings
  const compDaysMap = new Map<number, Set<string>>();
  for (const cd of compensationDays) {
    const dateStr = cd.compensationDate.split("T")[0];
    if (!compDaysMap.has(cd.staffId)) compDaysMap.set(cd.staffId, new Set());
    compDaysMap.get(cd.staffId)!.add(dateStr);
  }

  // Build a map: staffId → Map<dateStr, CalendarItem[]>
  const staffScheduleMap = new Map<number, Map<string, CalendarItem[]>>();
  for (const s of schedules) {
    const dateStr = s.workDate.split("T")[0];
    const staffId = s.staff.id;
    if (!staffScheduleMap.has(staffId)) staffScheduleMap.set(staffId, new Map());
    const dateMap = staffScheduleMap.get(staffId)!;
    if (!dateMap.has(dateStr)) dateMap.set(dateStr, []);
    dateMap.get(dateStr)!.push({
      shiftLabel: SHIFT_SHORT[s.shiftType.id] ?? s.shiftType.id,
      staffName: s.staff.fullName,
      staffCode: "",
      tone: shiftTypeToTone(s.shiftType.id),
      shiftTypeId: s.shiftType.id,
      isOvernight: s.shiftType.id === "L01",
      schedule: s,
    });
  }

  // Build rows: one per day
  const rows: MatrixRow[] = [];
  const dayCount = isDayMode ? 1 : isWeekMode ? 7 : endDay - startDay + 1;
  for (let i = 0; i < dayCount; i++) {
    const date = isWeekMode
      ? new Date(weekStart!.getTime() + i * 86400000)
      : new Date(year, month, startDay + i);
    const dateStr = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    const dowNum = date.getDay(); // 0=Sun
    const dowLabel = DOW_VI[dowNum === 0 ? 0 : dowNum];

    const isCompensation = new Map<number, boolean>();
    const cells = new Map<number, CalendarItem[]>();
    for (const staff of staffList) {
      const compDates = compDaysMap.get(staff.id);
      isCompensation.set(staff.id, compDates?.has(dateStr) ?? false);
      const dateMap = staffScheduleMap.get(staff.id);
      cells.set(staff.id, dateMap?.get(dateStr) ?? []);
    }

    // Pre-compute rowCount: number of cells with items (excluding comp days)
    let count = 0;
    for (const [staffId, items] of cells) {
      if ((isCompensation.get(staffId) ?? false) === false && items.length > 0) count++;
    }

    rows.push({
      date,
      dateStr,
      dayOfWeek: dowLabel,
      dayLabel: `${date.getDate()}`,
      isWeekend: dowNum === 0 || dowNum === 6,
      isCompensation,
      cells,
      rowCount: count,
    });
  }

  return {
    headerCols: staffList,
    rows,
    dateRange: {
      start: `${isoWeekStart.getFullYear()}-${String(isoWeekStart.getMonth() + 1).padStart(2, "0")}-${String(isoWeekStart.getDate()).padStart(2, "0")}`,
      end: `${isoWeekEnd.getFullYear()}-${String(isoWeekEnd.getMonth() + 1).padStart(2, "0")}-${String(isoWeekEnd.getDate()).padStart(2, "0")}`,
    },
    mode: isWeekMode ? "week" : "month",
  };
}
