import type { ScheduleTab } from "@/components/monthly-schedule/types";

export type ShiftColorSet = {
  bg: string;
  text: string;
  dot: string;
  label: string;
};

/**
 * SHIFT_COLORS — Single source of truth for shift type colors.
 * Maps ScheduleTab → Tailwind utility classes that reference @theme CSS custom properties.
 *
 * Design token source: globals.css @theme block
 *   --color-shift-24:       #dbe1ff  → text: #00174b  (L01 Trực 24/24)
 *   --color-shift-all-day:  #dcfce7  → text: #14532d  (L02 Thông tầm)
 *   --color-shift-service:  #ffedd5  → text: #7c2d12  (L03 PK Dịch vụ)
 *   --color-shift-expert:   #f3e8ff  → text: #4c1d95  (L04 PK Chuyên gia)
 *
 * WHY: These use Tailwind utilities (bg-blue-50, text-blue-700) rather than
 * CSS custom properties because Tailwind v4 @theme variables do NOT automatically
 * generate matching bg-*, text-*, border-* utility classes. The shift color
 * tokens are intentionally separate from the primary surface token system.
 *
 * IMPORTANT: When updating colors, update BOTH globals.css @theme AND these maps.
 */
export const SHIFT_COLORS: Record<ScheduleTab, ShiftColorSet> = {
  ALL: { bg: "bg-surface-container-low", text: "text-on-surface", dot: "bg-outline", label: "Tất cả" },
  L01: { bg: "bg-shift-24",       text: "text-on-shift-24",       dot: "bg-blue-500",    label: "Trực 24/24" },
  L02: { bg: "bg-shift-all-day", text: "text-on-shift-all-day",   dot: "bg-green-500",   label: "Thông tầm" },
  L03: { bg: "bg-shift-service", text: "text-on-shift-service",   dot: "bg-orange-500",  label: "Dịch vụ" },
  L04: { bg: "bg-shift-expert",  text: "text-on-shift-expert",    dot: "bg-purple-500",  label: "Chuyên gia" },
};

export const SHIFT_TYPE_BADGES: Record<ScheduleTab, string> = {
  ALL: "bg-surface-container-low text-on-surface border border-outline",
  L01: "bg-shift-24 text-on-shift-24 border border-shift-24",
  L02: "bg-shift-all-day text-on-shift-all-day border border-shift-all-day",
  L03: "bg-shift-service text-on-shift-service border border-shift-service",
  L04: "bg-shift-expert text-on-shift-expert border border-shift-expert",
};
