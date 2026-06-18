import type { Schedule } from "@/types/api";
import { SHIFT_COLORS } from "@/lib/shift-colors";

export const TONE: Record<string, { bg: string; text: string; dot: string; label: string }> = {
  L01: { bg: SHIFT_COLORS.L01.bg, text: SHIFT_COLORS.L01.text, dot: SHIFT_COLORS.L01.dot, label: SHIFT_COLORS.L01.label },
  L02: { bg: SHIFT_COLORS.L02.bg, text: SHIFT_COLORS.L02.text, dot: SHIFT_COLORS.L02.dot, label: SHIFT_COLORS.L02.label },
  L03: { bg: SHIFT_COLORS.L03.bg, text: SHIFT_COLORS.L03.text, dot: SHIFT_COLORS.L03.dot, label: SHIFT_COLORS.L03.label },
  L04: { bg: SHIFT_COLORS.L04.bg, text: SHIFT_COLORS.L04.text, dot: SHIFT_COLORS.L04.dot, label: SHIFT_COLORS.L04.label },
};

export const FALLBACK_TONE = { bg: "bg-surface-container-low", text: "text-on-surface", dot: "bg-outline" };

export const WEEKDAY_VN = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"] as const;

export type SortKey = "workDate" | "shiftType" | "staffName" | "hasConflict";
export type SortDir = "asc" | "desc";
export type FilterConflict = "all" | "conflict" | "clean";

export const PAGE_SIZE = 20;

export type ScheduleTableViewProps = {
  schedules: Schedule[];
  onEdit?: (s: Schedule) => void;
  onDelete?: (s: Schedule) => void;
  onResolveConflict?: (s: Schedule) => void;
  onViewDetail?: (s: Schedule) => void;
  canEdit?: boolean;
};

export const SHIFT_TYPE_OPTIONS = [
  { value: "all", label: "Tất cả", dot: "bg-outline-variant", title: "Tất cả loại ca" },
  { value: "L01", label: "24/24", dot: "bg-primary", title: "Trực 24/24" },
  { value: "L02", label: "TT", dot: "bg-secondary", title: "Thông tầm" },
  { value: "L03", label: "DV", dot: "bg-tertiary", title: "Dịch vụ" },
  { value: "L04", label: "CG", dot: "bg-expert", title: "Chuyên gia" },
] as const;

export const CONFLICT_OPTIONS: { value: FilterConflict; label: string }[] = [
  { value: "all", label: "Tất cả trạng thái" },
  { value: "conflict", label: "Có xung đột" },
  { value: "clean", label: "Không xung đột" },
];
