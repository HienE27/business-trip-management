"use client";

import type { Schedule } from "@/types/api";
import type { ScheduleTone } from "@/types/schedule";

/** Color token cho mỗi loại ca trực trên calendar grid. */
export const TONE: Record<
  ScheduleTone,
  { bg: string; text: string; border: string; dot: string }
> = {
  duty24: { bg: "bg-shift-24", text: "text-on-shift-24", border: "border-l-shift-24", dot: "bg-shift-24" },
  allDay: { bg: "bg-shift-all-day", text: "text-on-shift-all-day", border: "border-l-shift-all-day", dot: "bg-shift-all-day" },
  serviceClinic: { bg: "bg-shift-service", text: "text-on-shift-service", border: "border-l-shift-service", dot: "bg-shift-service" },
  expertClinic: { bg: "bg-shift-expert", text: "text-on-shift-expert", border: "border-l-shift-expert", dot: "bg-shift-expert" },
  compLeave: { bg: "bg-surface-container-high", text: "text-on-surface-variant", border: "border-l-outline", dot: "bg-outline" },
  warning: { bg: "bg-amber-100", text: "text-amber-800", border: "border-l-amber-500", dot: "bg-amber-500" },
  conflict: { bg: "bg-red-100", text: "text-red-800", border: "border-l-red-500", dot: "bg-red-500" },
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

export function shiftTypeToTone(id: string): ScheduleTone {
  switch (id) {
    case "L01": return "duty24";
    case "L02": return "allDay";
    case "L03": return "serviceClinic";
    case "L04": return "expertClinic";
    default: return "neutral";
  }
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
