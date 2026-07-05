import type { AutoScheduleSummary } from "@/types/api";

/* ================================================================
 * schedule-aggregates.ts
 *
 * Shared logic — dùng bởi WorkloadChart (bar/stacked/balance tabs).
 *
 * Pure, no side-effect → dễ test. AutoScheduleSummary → StaffAggregate
 * trong O(n).
 * ================================================================ */

export type ShiftTypeId = "L01" | "L02" | "L03" | "L04";

export type StaffBalanceStatus = "balanced" | "caution" | "overloaded";

export interface StaffAggregate {
  staffId: number;
  staffName: string;
  L01: number;
  L02: number;
  L03: number;
  L04: number;
  /** Tổng số ca đã gán (sum L01-L04) */
  total: number;
  /** Trung bình cộng của cả team */
  avg: number;
  /** total / avg — càng >1 càng quá tải */
  ratio: number;
  status: StaffBalanceStatus;
}

/** Ngưỡng phân loại balance. */
export const BALANCE_THRESHOLDS = {
  CAUTION_RATIO: 1.0,
  OVERLOADED_RATIO: 1.5,
} as const;

export const SHIFT_LABELS: Record<ShiftTypeId, string> = {
  L01: "Trực 24/24",
  L02: "Thông tầm",
  L03: "PK Dịch vụ",
  L04: "PK Chuyên gia",
};

export function classifyRatio(ratio: number): StaffBalanceStatus {
  if (ratio >= BALANCE_THRESHOLDS.OVERLOADED_RATIO) return "overloaded";
  if (ratio > BALANCE_THRESHOLDS.CAUTION_RATIO) return "caution";
  return "balanced";
}

/**
 * Tính aggregate từ danh sách AutoScheduleSummary.
 * - Sort stable theo: ratio desc → total desc → staffId asc
 *   (để staff quá tải nhất luôn lên đầu)
 */
export function aggregateByStaff(
  schedules: Pick<AutoScheduleSummary, "staffId" | "staffName" | "shiftTypeId">[],
): StaffAggregate[] {
  const map = new Map<number, StaffAggregate>();

  for (const s of schedules) {
    const existing = map.get(s.staffId);
    const seed: StaffAggregate =
      existing ??
      {
        staffId: s.staffId,
        staffName: s.staffName,
        L01: 0,
        L02: 0,
        L03: 0,
        L04: 0,
        total: 0,
        avg: 0,
        ratio: 0,
        status: "balanced",
      };

    switch (s.shiftTypeId as ShiftTypeId) {
      case "L01":
        seed.L01++;
        break;
      case "L02":
        seed.L02++;
        break;
      case "L03":
        seed.L03++;
        break;
      case "L04":
        seed.L04++;
        break;
      default:
        // Bỏ qua shift type không nhận dạng
        break;
    }
    seed.total++;
    map.set(s.staffId, seed);
  }

  const rows = Array.from(map.values());
  const staffCount = rows.length;
  const grandTotal = rows.reduce((sum, r) => sum + r.total, 0);
  const avg = staffCount > 0 ? grandTotal / staffCount : 0;

  for (const row of rows) {
    row.avg = avg;
    row.ratio = avg > 0 ? row.total / avg : 0;
    row.status = classifyRatio(row.ratio);
  }

  return rows.sort((a, b) => {
    if (b.ratio !== a.ratio) return b.ratio - a.ratio;
    if (b.total !== a.total) return b.total - a.total;
    return a.staffId - b.staffId;
  });
}

/** Lấy top-N kèm số lượng bị ẩn (cho footer "Hiển thị N/X") */
export function topN<T>(items: T[], limit: number): { rows: T[]; hidden: number } {
  if (items.length <= limit) return { rows: items, hidden: 0 };
  return { rows: items.slice(0, limit), hidden: items.length - limit };
}