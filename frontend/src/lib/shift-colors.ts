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
 * All colors reference CSS custom properties (--color-shift-*) so that
 * @media (prefers-color-scheme: dark) can flip to dark-mode semantics
 * in globals.css without duplicating every component.
 *
 * Spec (FRONTEND_UI_SYSTEM.mdc §7):
 *   L01 Trực 24/24 → --color-shift-24 (dark red tint)
 *   L02 Thông tầm  → --color-shift-all-day (dark green tint)
 *   L03 PK dịch vụ → --color-shift-service (dark orange tint)
 *   L04 PK chuyên gia → --color-shift-expert (dark purple tint)
 */
export const SHIFT_COLORS: Record<ScheduleTab, ShiftColorSet> = {
  ALL: { bg: "bg-surface-container-low", text: "text-on-surface", dot: "bg-outline", label: "Tất cả" },
  L01: { bg: "bg-shift-24",    text: "text-on-shift-24",    dot: "bg-shift-24",    label: "Trực 24/24" },
  L02: { bg: "bg-shift-all-day", text: "text-on-shift-all-day", dot: "bg-shift-all-day", label: "Thông tầm" },
  L03: { bg: "bg-shift-service", text: "text-on-shift-service", dot: "bg-shift-service", label: "Dịch vụ" },
  L04: { bg: "bg-shift-expert", text: "text-on-shift-expert", dot: "bg-shift-expert", label: "Chuyên gia" },
};

export const SHIFT_TYPE_BADGES: Record<ScheduleTab, string> = {
  ALL: "bg-surface-container-low text-on-surface border border-outline",
  L01: "bg-shift-24 text-on-shift-24 border border-shift-24",
  L02: "bg-shift-all-day text-on-shift-all-day border border-shift-all-day",
  L03: "bg-shift-service text-on-shift-service border border-shift-service",
  L04: "bg-shift-expert text-on-shift-expert border border-shift-expert",
};
