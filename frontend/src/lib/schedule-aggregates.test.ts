import { describe, it, expect } from "vitest";
import {
  aggregateByStaff,
  classifyRatio,
  BALANCE_THRESHOLDS,
} from "./schedule-aggregates";
import type { AutoScheduleSummary } from "@/types/api";

const mk = (
  staffId: number,
  staffName: string,
  shiftTypeId: string,
): AutoScheduleSummary => ({
  scheduleId: null,
  staffId,
  staffName,
  workDate: "2026-06-01",
  shiftTypeId,
  shiftTypeName: "",
});

describe("classifyRatio", () => {
  it.each([
    [0.5, "balanced"],
    [BALANCE_THRESHOLDS.CAUTION_RATIO, "balanced"], // ≤ 1.0 là balanced
    [BALANCE_THRESHOLDS.CAUTION_RATIO + 0.01, "caution"],
    [BALANCE_THRESHOLDS.OVERLOADED_RATIO - 0.01, "caution"],
    [BALANCE_THRESHOLDS.OVERLOADED_RATIO, "overloaded"],
    [3.0, "overloaded"],
  ])("ratio %s → %s", (ratio, expected) => {
    expect(classifyRatio(ratio)).toBe(expected);
  });
});

describe("aggregateByStaff", () => {
  it("tính total + ratio + status chính xác", () => {
    // 3 nhân viên: A=3 ca, B=1, C=1 → avg=5/3≈1.67
    // A ratio=1.8 → caution (1.0 < 1.8 < 1.5? NO, 1.8 ≥ 1.5 → overloaded)
    // B,C ratio=0.6 → balanced
    const schedules = [
      mk(1, "BS. A", "L01"),
      mk(1, "BS. A", "L01"),
      mk(1, "BS. A", "L02"),
      mk(2, "BS. B", "L01"),
      mk(3, "BS. C", "L03"),
    ];
    const rows = aggregateByStaff(schedules);
    expect(rows).toHaveLength(3);

    const a = rows.find((r) => r.staffId === 1)!;
    expect(a.total).toBe(3);
    expect(a.L01).toBe(2);
    expect(a.L02).toBe(1);
    expect(a.L03).toBe(0);
    expect(a.avg).toBeCloseTo(5 / 3, 5);
    expect(a.ratio).toBeCloseTo(3 / (5 / 3), 5); // = 1.8
    expect(a.status).toBe("overloaded");

    const b = rows.find((r) => r.staffId === 2)!;
    expect(b.total).toBe(1);
    expect(b.ratio).toBeCloseTo(1 / (5 / 3), 5); // = 0.6
    expect(b.status).toBe("balanced");
  });

  it("sort stable: ratio desc → total desc → staffId asc", () => {
    // Heavy=5, Light1=1, Light2=1 → avg=7/3 ≈ 2.33
    // Heavy ratio=5/2.33≈2.14 → overloaded
    // Light1,2 ratio=0.43 → balanced; tie break by staffId asc
    const schedules = [
      ...Array.from({ length: 5 }, () => mk(99, "BS. Heavy", "L01")),
      mk(100, "BS. Light1", "L01"),
      mk(101, "BS. Light2", "L01"),
    ];
    const rows = aggregateByStaff(schedules);
    expect(rows[0].staffName).toBe("BS. Heavy");
    expect(rows[1].staffName).toBe("BS. Light1");
    expect(rows[2].staffName).toBe("BS. Light2");
  });

  it("bỏ qua shift type không thuộc L01-L04 nhưng vẫn tăng total", () => {
    const schedules = [
      mk(1, "BS. A", "L01"),
      mk(1, "BS. A", "UNKNOWN"),
    ];
    const rows = aggregateByStaff(schedules);
    expect(rows[0].total).toBe(2);
    expect(rows[0].L01).toBe(1);
    expect(rows[0].L02).toBe(0);
  });

  it("trả về mảng rỗng khi input rỗng", () => {
    expect(aggregateByStaff([])).toEqual([]);
  });
});