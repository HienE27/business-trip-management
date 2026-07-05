import type { ScheduleTab } from "@/components/monthly-schedule/types";

export type ShiftColorSet = {
  bg: string;
  text: string;
  dot: string;
  label: string;
};

/**
 * SHIFT_COLORS — Single source of truth for shift type colors.
 * Maps ScheduleTab → Tailwind utility classes.
 *
 * Spec (FRONTEND_UI_SYSTEM.mdc §7):
 *   L01 Trực 24/24       → red-50/100  + red-500/800
 *   L02 Thông tầm         → blue-50/100 + blue-500/800
 *   L03 PK dịch vụ        → green-50/100 + green-500/800
 *   L04 PK chuyên gia     → purple-50/100 + purple-500/800
 *
 * Background tints dùng trực tiếp Tailwind utilities để giữ contrast tốt
 * ở cả light & dark mode và tránh phải khai báo thêm CSS custom properties.
 */
export const SHIFT_COLORS: Record<ScheduleTab, ShiftColorSet> = {
  ALL: { bg: "bg-surface-container-low", text: "text-on-surface", dot: "bg-outline", label: "Tất cả" },
  L01: { bg: "bg-red-100",    text: "text-red-800",    dot: "bg-red-500",    label: "Trực 24/24" },
  L02: { bg: "bg-blue-100",   text: "text-blue-800",   dot: "bg-blue-500",   label: "Thông tầm" },
  L03: { bg: "bg-green-100",  text: "text-green-800",  dot: "bg-green-500",  label: "Dịch vụ" },
  L04: { bg: "bg-purple-100", text: "text-purple-800", dot: "bg-purple-500", label: "Chuyên gia" },
};

export const SHIFT_TYPE_BADGES: Record<ScheduleTab, string> = {
  ALL: "bg-surface-container-low text-on-surface border border-outline",
  L01: "bg-red-100 text-red-800 border border-red-500",
  L02: "bg-blue-100 text-blue-800 border border-blue-500",
  L03: "bg-green-100 text-green-800 border border-green-500",
  L04: "bg-purple-100 text-purple-800 border border-purple-500",
};
