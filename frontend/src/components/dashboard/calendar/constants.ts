"use client";

import type { Schedule } from "@/types/api";
import type { ScheduleTone } from "@/types/schedule";

/** Color token cho mỗi loại ca trực trên calendar grid. */
export const TONE: Record<
  ScheduleTone,
  { bg: string; text: string; border: string; dot: string }
> = {
  duty24: { bg: "bg-shift-24", text: "text-on-shift-24", border: "border-l-red-500", dot: "bg-red-500" },
  allDay: { bg: "bg-shift-all-day", text: "text-on-shift-all-day", border: "border-l-secondary", dot: "bg-secondary" },
  serviceClinic: { bg: "bg-shift-service", text: "text-on-shift-service", border: "border-l-error", dot: "bg-tertiary" },
  expertClinic: { bg: "bg-shift-expert", text: "text-on-shift-expert", border: "border-l-shift-expert", dot: "bg-shift-expert" },
  compLeave: { bg: "bg-surface-container-high", text: "text-on-surface-variant", border: "border-l-outline", dot: "bg-outline" },
  warning: { bg: "bg-tertiary-fixed", text: "text-on-tertiary-fixed", border: "border-l-error", dot: "bg-tertiary" },
  conflict: { bg: "bg-error-container", text: "text-on-error-container", border: "border-l-error", dot: "bg-error" },
  neutral: { bg: "bg-surface-container-low", text: "text-on-surface-variant", border: "border-l-outline", dot: "bg-outline" },
  empty: { bg: "", text: "", border: "", dot: "" },
};

export const SHIFT_SHORT: Record<string, string> = {
  L01: "24/24",
  L02: "TT",
  L03: "DV",
  L04: "CG",
};

export const SHIFT_FULL_LABEL: Record<string, string> = {
  L01: "Trực 24/24",
  L02: "Thông tầm",
  L03: "PK dịch vụ",
  L04: "PK chuyên gia",
};

export const SHIFT_ORDER: Record<string, number> = {
  L01: 1,
  L02: 2,
  L03: 3,
  L04: 4,
};

export const MAX_VISIBLE_GROUPS = 2;
export const WEEKDAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"] as const;

export function addDays(d: Date, days: number): Date {
  const result = new Date(d);
  result.setDate(result.getDate() + days);
  return result;
}

export function weekStartOf(d: Date): Date {
  const result = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const dow = (result.getDay() + 6) % 7;
  result.setDate(result.getDate() - dow);
  return result;
}

export function getStaffCode(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 3).toUpperCase();
  const last = parts[parts.length - 1];
  return last.slice(0, 3).toUpperCase();
}

export function shiftTypeToTone(id: string): ScheduleTone {
  switch (id) {
    case "L01": return "duty24";
    case "L02": return "allDay";
    case "L03": return "serviceClinic";
    case "L04": return "expertClinic";
    default: return "neutral";
  }
}

export function formatFullDate(date: Date) {
  return date.toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" });
}

export type CalendarItem = {
  shiftLabel: string;
  staffName: string;
  staffCode: string;
  tone: ScheduleTone;
  shiftTypeId: string;
  isOvernight: boolean;
  schedule: Schedule;
};

export type CalendarAnnotationTone = "compLeave" | "warning" | "neutral" | "holiday";

export type CalendarAnnotation = {
  date: string;
  label: string;
  tone?: CalendarAnnotationTone;
  description?: string;
  isCompensation?: boolean;
  locked?: boolean;
  isHoliday?: boolean;
  holidayName?: string;
  coverage?: { required: number; assigned: number };
  coverageShiftTypeId?: string;
};

export type CalendarCell = {
  day: number;
  isWeekend: boolean;
  isCurrentMonth: boolean;
  hasConflict: boolean;
  isCompensation: boolean;
  items: CalendarItem[];
  annotations: CalendarAnnotation[];
  dateStr: string;
  date: Date;
};

export type CalendarViewMode = "month" | "week";

export function summarizeItems(items: CalendarItem[]) {
  const map = new Map<string, { shiftTypeId: string; label: string; count: number; tone: ScheduleTone }>();
  for (const item of items) {
    const current = map.get(item.shiftTypeId) ?? {
      shiftTypeId: item.shiftTypeId,
      label: SHIFT_SHORT[item.shiftTypeId] ?? item.shiftLabel,
      count: 0,
      tone: item.tone,
    };
    current.count += 1;
    map.set(item.shiftTypeId, current);
  }
  return Array.from(map.values()).sort(
    (a, b) => (SHIFT_ORDER[a.shiftTypeId] ?? 99) - (SHIFT_ORDER[b.shiftTypeId] ?? 99)
  );
}
