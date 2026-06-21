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
}

export interface ScheduleMatrix {
  headerCols: { id: number; fullName: string }[];
  rows: MatrixRow[];
  dateRange: { start: string; end: string };
}

const DOW_VI = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"] as const;

function formatDate(year: number, month: number, day: number): string {
  return `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

export function buildScheduleMatrix(
  schedules: Schedule[],
  staffList: { id: number; fullName: string }[],
  year: number,
  month: number,
  compensationDays: CompensationDay[] = [],
): ScheduleMatrix {
  const lastDay = new Date(year, month + 1, 0);
  const daysInMonth = lastDay.getDate();

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

  // Build rows: one per day of the month
  const rows: MatrixRow[] = [];
  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = formatDate(year, month, d);
    const date = new Date(year, month, d);
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

    rows.push({
      date,
      dateStr,
      dayOfWeek: dowLabel,
      dayLabel: `${d}`,
      isWeekend: dowNum === 0 || dowNum === 6,
      isCompensation,
      cells,
    });
  }

  return {
    headerCols: staffList,
    rows,
    dateRange: {
      start: formatDate(year, month, 1),
      end: formatDate(year, month, daysInMonth),
    },
  };
}
