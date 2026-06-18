"use client";

import type { Schedule } from "@/types/api";
import {
  getStaffCode,
  shiftTypeToTone,
  SHIFT_SHORT,
  type CalendarAnnotation,
  type CalendarCell,
  type CalendarItem,
} from "./constants";

/**
 * Tính toán grid calendar cho 1 tháng:
 * - Padding đầu/cuối bằng ngày tháng trước/sau.
 * - Map schedules theo ngày.
 * - Map annotations theo ngày.
 * - Trả về `cells[]` để render + `month` label + `today` reference.
 *
 * Thuần logic, không phụ thuộc React — dễ test.
 */
export function buildCalendar(
  schedules: Schedule[],
  annotations: CalendarAnnotation[] = [],
  year: number,
  month: number
): { month: string; cells: CalendarCell[]; today: Date } {
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);
  const startDayOfWeek = (firstDay.getDay() + 6) % 7;
  const daysInMonth = lastDay.getDate();

  const prevMonth = month === 0 ? 12 : month;
  const prevYear = month === 0 ? year - 1 : year;
  const prevMonthLastDay = new Date(prevYear, prevMonth, 0).getDate();

  const prevDays: number[] = [];
  for (let i = startDayOfWeek - 1; i >= 0; i--) prevDays.push(prevMonthLastDay - i);

  const totalCells = prevDays.length + daysInMonth;
  const remaining = totalCells % 7 === 0 ? 0 : 7 - (totalCells % 7);

  const scheduleMap = new Map<string, Schedule[]>();
  for (const s of schedules) {
    const key = s.workDate.split("T")[0];
    if (!scheduleMap.has(key)) scheduleMap.set(key, []);
    scheduleMap.get(key)!.push(s);
  }

  const annotationMap = new Map<string, CalendarAnnotation[]>();
  for (const annotation of annotations) {
    const key = annotation.date;
    if (!annotationMap.has(key)) annotationMap.set(key, []);
    annotationMap.get(key)!.push(annotation);
  }

  const fmt = (y: number, m: number, d: number) =>
    `${y}-${String(m + 1).padStart(2, "0")}-${String(d).padStart(2, "0")}`;

  const cells: CalendarCell[] = [];
  const isCompDay = (anns: CalendarAnnotation[]) =>
    anns.some((a) => a.tone === "compLeave" || a.isCompensation);

  for (const d of prevDays) {
    const m = prevMonth - 1;
    const y = m < 0 ? prevYear - 1 : prevYear;
    const mm = m < 0 ? 11 : m;
    const dateStr = fmt(y, mm, d);
    const dow = new Date(y, mm, d).getDay();
    const anns = annotationMap.get(dateStr) ?? [];
    cells.push({
      day: d,
      isWeekend: dow === 0 || dow === 6,
      isCurrentMonth: false,
      hasConflict: false,
      isCompensation: isCompDay(anns),
      items: [],
      annotations: anns,
      dateStr,
      date: new Date(y, mm, d),
    });
  }

  for (let d = 1; d <= daysInMonth; d++) {
    const dateStr = fmt(year, month, d);
    const daySchedules = scheduleMap.get(dateStr) ?? [];
    const hasConflict = daySchedules.some((s) => s.hasConflict);
    const anns = annotationMap.get(dateStr) ?? [];
    const items: CalendarItem[] = daySchedules.map((s) => ({
      shiftLabel: SHIFT_SHORT[s.shiftType.id] ?? s.shiftType.id,
      staffName: s.staff.fullName,
      staffCode: getStaffCode(s.staff.fullName),
      tone: shiftTypeToTone(s.shiftType.id),
      shiftTypeId: s.shiftType.id,
      isOvernight: s.shiftType.id === "L01",
      schedule: s,
    }));
    cells.push({
      day: d,
      isWeekend: false,
      isCurrentMonth: true,
      hasConflict,
      isCompensation: isCompDay(anns),
      items,
      annotations: anns,
      dateStr,
      date: new Date(year, month, d),
    });
  }

  for (let d = 1; d <= remaining; d++) {
    const nm = month === 11 ? 0 : month + 1;
    const ny = month === 11 ? year + 1 : year;
    const dateStr = fmt(ny, nm, d);
    const dow = new Date(ny, nm, d).getDay();
    const anns = annotationMap.get(dateStr) ?? [];
    cells.push({
      day: d,
      isWeekend: dow === 0 || dow === 6,
      isCurrentMonth: false,
      hasConflict: false,
      isCompensation: isCompDay(anns),
      items: [],
      annotations: anns,
      dateStr,
      date: new Date(ny, nm, d),
    });
  }

  const monthName = new Date(year, month, 1).toLocaleDateString("vi-VN", { month: "long", year: "numeric" });
  return {
    month: monthName.charAt(0).toUpperCase() + monthName.slice(1),
    cells,
    today: new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate()),
  };
}

/** Tính 7 ngày trong tuần chứa `weekStart`. */
export function buildWeekCells(weekStart: Date, schedules: Schedule[], annotations: CalendarAnnotation[] = []) {
  const scheduleMap = new Map<string, Schedule[]>();
  for (const s of schedules) {
    const key = s.workDate.split("T")[0];
    if (!scheduleMap.has(key)) scheduleMap.set(key, []);
    scheduleMap.get(key)!.push(s);
  }
  const annotationMap = new Map<string, CalendarAnnotation[]>();
  for (const a of annotations) {
    if (!annotationMap.has(a.date)) annotationMap.set(a.date, []);
    annotationMap.get(a.date)!.push(a);
  }
  const isCompDay = (anns: CalendarAnnotation[]) =>
    anns.some((a) => a.tone === "compLeave" || a.isCompensation);
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

  const cells: CalendarCell[] = [];
  for (let i = 0; i < 7; i++) {
    const date = new Date(weekStart.getFullYear(), weekStart.getMonth(), weekStart.getDate() + i);
    const dateStr = fmt(date);
    const dow = date.getDay();
    const daySchedules = scheduleMap.get(dateStr) ?? [];
    const anns = annotationMap.get(dateStr) ?? [];
    const items: CalendarItem[] = daySchedules.map((s) => ({
      shiftLabel: SHIFT_SHORT[s.shiftType.id] ?? s.shiftType.id,
      staffName: s.staff.fullName,
      staffCode: getStaffCode(s.staff.fullName),
      tone: shiftTypeToTone(s.shiftType.id),
      shiftTypeId: s.shiftType.id,
      isOvernight: s.shiftType.id === "L01",
      schedule: s,
    }));
    cells.push({
      day: date.getDate(),
      isWeekend: dow === 0 || dow === 6,
      isCurrentMonth: date.getMonth() === weekStart.getMonth(),
      hasConflict: daySchedules.some((s) => s.hasConflict),
      isCompensation: isCompDay(anns),
      items,
      annotations: anns,
      dateStr,
      date,
    });
  }
  return cells;
}
