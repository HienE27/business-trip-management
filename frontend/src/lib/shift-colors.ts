import type { ScheduleTab } from "@/components/monthly-schedule/types";

export type ShiftColorSet = {
  bg: string;
  text: string;
  dot: string;
  label: string;
};

export const SHIFT_COLORS: Record<ScheduleTab, ShiftColorSet> = {
  L01: { bg: "bg-blue-50",     text: "text-blue-700",     dot: "bg-blue-500",     label: "Trực 24/24" },
  L02: { bg: "bg-green-50",    text: "text-green-700",    dot: "bg-green-500",    label: "Thông tầm" },
  L03: { bg: "bg-orange-50",   text: "text-orange-700",   dot: "bg-orange-500",   label: "Dịch vụ" },
  L04: { bg: "bg-purple-50",   text: "text-purple-700",   dot: "bg-purple-500",   label: "Chuyên gia" },
};

export const SHIFT_TYPE_BADGES: Record<ScheduleTab, string> = {
  L01: "bg-blue-50 text-blue-700 border border-blue-200",
  L02: "bg-green-50 text-green-700 border border-green-200",
  L03: "bg-orange-50 text-orange-700 border border-orange-200",
  L04: "bg-purple-50 text-purple-700 border border-purple-200",
};
