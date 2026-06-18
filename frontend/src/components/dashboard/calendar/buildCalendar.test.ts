import { describe, it, expect } from "vitest";
import { buildCalendar, buildWeekCells } from "@/components/dashboard/calendar/buildCalendar";
import type { Schedule } from "@/types/api";

const baseSchedule = (overrides: Partial<Schedule> = {}): Schedule => ({
  id: 1,
  periodId: 1,
  staff: { id: 1, fullName: "Nguyễn Văn A", specialtyName: null },
  shiftType: { id: "L01", name: "Trực 24/24", isOvernight: true },
  workDate: "2026-06-15T00:00:00",
  notes: null,
  hasConflict: false,
  createdAt: "",
  updatedAt: "",
  ...overrides,
});

describe("buildCalendar", () => {
  it("trả về 35 hoặc 42 cells (5 hoặc 6 tuần)", () => {
    const { cells } = buildCalendar([], [], 2026, 5);
    expect([35, 42]).toContain(cells.length);
    expect(cells.length % 7).toBe(0);
  });

  it("cells[0] và cells[length-1] thuộc tháng trước/sau (isCurrentMonth = false)", () => {
    // Tháng 6/2026 bắt đầu T2 (1/6)
    const { cells } = buildCalendar([], [], 2026, 5);
    const firstInMonth = cells.find((c) => c.isCurrentMonth);
    expect(firstInMonth?.day).toBe(1);
    expect(firstInMonth?.dateStr).toBe("2026-06-01");
  });

  it("group schedules theo ngày", () => {
    const schedules = [
      baseSchedule({ id: 1, workDate: "2026-06-15T00:00:00" }),
      baseSchedule({ id: 2, workDate: "2026-06-15T00:00:00" }),
      baseSchedule({ id: 3, workDate: "2026-06-16T00:00:00" }),
    ];
    const { cells } = buildCalendar(schedules, [], 2026, 5);
    const day15 = cells.find((c) => c.dateStr === "2026-06-15");
    const day16 = cells.find((c) => c.dateStr === "2026-06-16");
    expect(day15?.items.length).toBe(2);
    expect(day16?.items.length).toBe(1);
  });

  it("mark cell có conflict khi có schedule hasConflict = true", () => {
    const schedules = [baseSchedule({ hasConflict: true })];
    const { cells } = buildCalendar(schedules, [], 2026, 5);
    const cell = cells.find((c) => c.dateStr === "2026-06-15");
    expect(cell?.hasConflict).toBe(true);
  });

  it("mark cell là compensation day khi có annotation compLeave", () => {
    const annotations = [
      { date: "2026-06-15", label: "Nghỉ bù", tone: "compLeave" as const },
    ];
    const { cells } = buildCalendar([], annotations, 2026, 5);
    const cell = cells.find((c) => c.dateStr === "2026-06-15");
    expect(cell?.isCompensation).toBe(true);
  });

  it("month label vi-VN, viết hoa chữ cái đầu", () => {
    const { month } = buildCalendar([], [], 2026, 5);
    expect(month).toMatch(/^Tháng 6 năm 2026$/);
  });

  it("staffCode lấy 3 ký tự đầu của họ (giữ nguyên dấu)", () => {
    const schedules = [baseSchedule({ staff: { id: 1, fullName: "Trần Thị Bích", specialtyName: null } })];
    const { cells } = buildCalendar(schedules, [], 2026, 5);
    const cell = cells.find((c) => c.items.length > 0);
    // Hàm không loại bỏ dấu; tên "Bích" → "BÍC" viết hoa
    expect(cell?.items[0]?.staffCode).toBe("BÍC");
  });

  it("isOvernight = true cho L01", () => {
    const schedules = [baseSchedule({ shiftType: { id: "L01", name: "Trực 24/24", isOvernight: true } })];
    const { cells } = buildCalendar(schedules, [], 2026, 5);
    const cell = cells.find((c) => c.items.length > 0);
    expect(cell?.items[0]?.isOvernight).toBe(true);
  });

  it("padding prev/next month cells có isCurrentMonth = false", () => {
    const { cells } = buildCalendar([], [], 2026, 5);
    const paddingCount = cells.filter((c) => !c.isCurrentMonth).length;
    expect(paddingCount).toBeGreaterThan(0);
  });
});

describe("buildWeekCells", () => {
  it("trả về đúng 7 cells", () => {
    const weekStart = new Date(2026, 5, 15); // T2 15/6/2026
    const cells = buildWeekCells(weekStart, []);
    expect(cells.length).toBe(7);
  });

  it("ngày đầu tuần = weekStart, ngày cuối = weekStart + 6", () => {
    const weekStart = new Date(2026, 5, 15);
    const cells = buildWeekCells(weekStart, []);
    expect(cells[0]?.dateStr).toBe("2026-06-15");
    expect(cells[6]?.dateStr).toBe("2026-06-21");
  });

  it("đánh dấu weekend đúng (T7 + CN)", () => {
    const weekStart = new Date(2026, 5, 15);
    const cells = buildWeekCells(weekStart, []);
    expect(cells[5]?.isWeekend).toBe(true); // T7
    expect(cells[6]?.isWeekend).toBe(true); // CN
    expect(cells[0]?.isWeekend).toBe(false);
  });
});
