// Re-export from single source of truth
export { SHIFT_COLORS, SHIFT_TYPE_BADGES } from "@/lib/shift-colors";
export type { ShiftColorSet } from "@/lib/shift-colors";

// Legacy shift object — used by dashboard/page.tsx
import { SHIFT_COLORS } from "@/lib/shift-colors";

export const shift = {
  L01: {
    bg: SHIFT_COLORS.L01.bg,
    border: "border-l-blue-500",
    badge: "bg-blue-50 text-blue-700 border border-blue-200",
    text: SHIFT_COLORS.L01.text,
  },
  L02: {
    bg: SHIFT_COLORS.L02.bg,
    border: "border-l-green-500",
    badge: "bg-green-50 text-green-700 border border-green-200",
    text: SHIFT_COLORS.L02.text,
  },
  L03: {
    bg: SHIFT_COLORS.L03.bg,
    border: "border-l-orange-500",
    badge: "bg-orange-50 text-orange-700 border border-orange-200",
    text: SHIFT_COLORS.L03.text,
  },
  L04: {
    bg: SHIFT_COLORS.L04.bg,
    border: "border-l-purple-500",
    badge: "bg-purple-50 text-purple-700 border border-purple-200",
    text: SHIFT_COLORS.L04.text,
  },
} as const;

export type ShiftType = keyof typeof shift;
