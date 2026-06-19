import { describe, it, expect } from 'vitest';
import {
  pickShiftCount,
  pickCap,
  computeSummary,
  isOverloaded,
  type StaffWorkloadRow,
} from './workloadUtils';

const rows: StaffWorkloadRow[] = [
  {
    staff: { id: 1, fullName: 'BS. A', maxShiftsPerMonth: 6 },
    L01: 2,
    L02: 1,
    L03: 0,
    L04: 0,
    total: 3,
  },
  {
    staff: { id: 2, fullName: 'BS. B', maxShiftsPerMonth: 6 },
    L01: 5,
    L02: 0,
    L03: 0,
    L04: 0,
    total: 5,
  },
  {
    staff: { id: 3, fullName: 'BS. C', maxShiftsPerMonth: 8 },
    L01: 1,
    L02: 1,
    L03: 4,
    L04: 2,
    total: 8,
  },
];

describe('workloadUtils.pickShiftCount', () => {
  it('returns total for ALL', () => {
    expect(pickShiftCount(rows[0], 'ALL')).toBe(3);
  });

  it('returns per-shift counts', () => {
    expect(pickShiftCount(rows[0], 'L01')).toBe(2);
    expect(pickShiftCount(rows[0], 'L02')).toBe(1);
    expect(pickShiftCount(rows[2], 'L03')).toBe(4);
    expect(pickShiftCount(rows[2], 'L04')).toBe(2);
  });
});

describe('workloadUtils.pickCap', () => {
  it('uses the staff cap for ALL', () => {
    expect(pickCap(rows[0], 'ALL')).toBe(6);
  });

  it('scales to ~60% of the cap for per-shift views', () => {
    // 6 * 0.6 = 3.6 -> 4
    expect(pickCap(rows[0], 'L01')).toBe(4);
    // 8 * 0.6 = 4.8 -> 5
    expect(pickCap(rows[2], 'L04')).toBe(5);
  });

  it('falls back to at least 1', () => {
    expect(pickCap(rows[0], 'L01')).toBeGreaterThanOrEqual(1);
  });
});

describe('workloadUtils.isOverloaded', () => {
  it('flags rows above the per-view cap', () => {
    expect(isOverloaded(rows[1], 'L01', 4)).toBe(true); // 5 > 4
    expect(isOverloaded(rows[0], 'L01', 4)).toBe(false); // 2 <= 4
  });
});

describe('workloadUtils.computeSummary', () => {
  it('reports totals, max, avg, and overloaded count', () => {
    const summary = computeSummary(rows, 'ALL');
    expect(summary.total).toBe(16); // 3 + 5 + 8
    expect(summary.max).toBe(8);
    expect(summary.avg).toBe(Math.round(16 / 3));
    // No row exceeds its cap (max=8 for BS.C), so no overload.
    expect(summary.overloaded).toBe(0);
  });

  it('flags overload when totals exceed the cap', () => {
    const skewed: StaffWorkloadRow[] = [
      { staff: { id: 1, fullName: 'A', maxShiftsPerMonth: 6 }, L01: 0, L02: 0, L03: 0, L04: 0, total: 9 },
      { staff: { id: 2, fullName: 'B', maxShiftsPerMonth: 6 }, L01: 0, L02: 0, L03: 0, L04: 0, total: 2 },
    ];
    expect(computeSummary(skewed, 'ALL').overloaded).toBe(1);
  });

  it('overloaded count uses the per-view cap', () => {
    // L01 counts: 2, 5, 1 — only BS. B with 5 exceeds cap 4
    const summary = computeSummary(rows, 'L01');
    expect(summary.overloaded).toBe(1);
  });

  it('returns zeros for empty input', () => {
    const summary = computeSummary([], 'ALL');
    expect(summary).toEqual({ total: 0, max: 0, avg: 0, overloaded: 0 });
  });
});