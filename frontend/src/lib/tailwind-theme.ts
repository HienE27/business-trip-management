// Single source of truth for shift color utilities.
export { SHIFT_COLORS, SHIFT_TYPE_BADGES } from "@/lib/shift-colors";
export type { ShiftColorSet } from "@/lib/shift-colors";

// shift object — used by dashboard/page.tsx
// All colors now reference CSS custom properties from globals.css @theme block.
// This is the ONLY place that should define shift color utilities.
import { SHIFT_COLORS } from "@/lib/shift-colors";

export const shift = {
  L01: {
    bg: "bg-shift-24",
    border: "border-l-[--color-shift-24]",        // references --color-shift-24
    badge: "bg-shift-24 text-on-shift-24 border border-shift-24",
    text: "text-on-shift-24",
  },
  L02: {
    bg: "bg-shift-all-day",
    border: "border-l-[--color-shift-all-day]",
    badge: "bg-shift-all-day text-on-shift-all-day border border-shift-all-day",
    text: "text-on-shift-all-day",
  },
  L03: {
    bg: "bg-shift-service",
    border: "border-l-[--color-shift-service]",
    badge: "bg-shift-service text-on-shift-service border border-shift-service",
    text: "text-on-shift-service",
  },
  L04: {
    bg: "bg-shift-expert",
    border: "border-l-[--color-shift-expert]",
    badge: "bg-shift-expert text-on-shift-expert border border-shift-expert",
    text: "text-on-shift-expert",
  },
} as const;

export type ShiftType = keyof typeof shift;
