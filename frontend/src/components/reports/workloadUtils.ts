/**
 * Helpers shared between `/reports/staff` and the unit-test
 * suite. Centralised here so the threshold rules for §M02-F05
 * ("cảnh báo nếu phân bổ lệch lớn"), §M04-F05, §M05-F05 are
 * easy to test and tweak.
 *
 * Per-shift views scale the cap down to ~60% of the staff's
 * overall `maxShiftsPerMonth` because the dedicated statistic
 * counts a single schedule type, so a sensible per-type cap
 * is much lower than the all-types cap.
 */

export type WorkloadView = 'ALL' | 'L01' | 'L02' | 'L03' | 'L04';

export interface StaffWorkloadRow {
  staff: {
    id: number;
    fullName: string;
    maxShiftsPerMonth?: number | null;
  };
  L01: number;
  L02: number;
  L03: number;
  L04: number;
  total: number;
}

export interface WorkloadSummary {
  total: number;
  max: number;
  avg: number;
  overloaded: number;
}

export function pickShiftCount(row: StaffWorkloadRow, view: WorkloadView): number {
  switch (view) {
    case 'ALL':
      return row.total;
    case 'L01':
      return row.L01;
    case 'L02':
      return row.L02;
    case 'L03':
      return row.L03;
    case 'L04':
      return row.L04;
    default: {
      const _exhaustive: never = view;
      return _exhaustive;
    }
  }
}

export function pickCap(row: StaffWorkloadRow, view: WorkloadView): number {
  const raw = row.staff.maxShiftsPerMonth ?? 6;
  if (view === 'ALL') return Math.max(1, raw);
  // Per-shift cap: ~60% of the overall cap, rounded up, at least 1.
  return Math.max(1, Math.ceil(raw * 0.6));
}

export function isOverloaded(
  row: StaffWorkloadRow,
  view: WorkloadView,
  cap: number,
): boolean {
  return pickShiftCount(row, view) > cap;
}

export function computeSummary(
  rows: StaffWorkloadRow[],
  view: WorkloadView,
): WorkloadSummary {
  if (rows.length === 0) {
    return { total: 0, max: 0, avg: 0, overloaded: 0 };
  }
  const counts = rows.map((r) => pickShiftCount(r, view));
  const total = counts.reduce((s, n) => s + n, 0);
  const max = Math.max(...counts);
  const avg = Math.round(total / rows.length);
  const overloaded = rows.reduce(
    (n, r) => (isOverloaded(r, view, pickCap(r, view)) ? n + 1 : n),
    0,
  );
  return { total, max, avg, overloaded };
}