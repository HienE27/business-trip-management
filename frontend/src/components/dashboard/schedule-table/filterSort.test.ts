import { describe, it, expect } from "vitest";
import {
  filterSchedules,
  sortSchedules,
  paginate,
  applyTablePipeline,
  getUniqueStaff,
  countActiveFilters,
  getInitials,
  isSameDay,
  EMPTY_FILTERS,
} from "@/components/dashboard/schedule-table/filterSort";
import type { Schedule } from "@/types/api";

const baseSchedule = (overrides: Partial<Schedule> = {}): Schedule => ({
  id: 1,
  periodId: 1,
  staff: { id: 1, fullName: "Nguyễn Văn A", specialtyId: null, specialtyName: null },
  shiftType: { id: "L01", name: "Trực 24/24", isOvernight: true },
  workDate: "2026-06-15T00:00:00",
  notes: null,
  hasConflict: false,
  isPublished: false,
  ...overrides,
});

describe("filterSchedules", () => {
  const data = [
    baseSchedule({ id: 1, staff: { id: 1, fullName: "Nguyễn Văn A", specialtyId: null, specialtyName: null }, shiftType: { id: "L01", name: "Trực 24/24", isOvernight: true }, workDate: "2026-06-15T00:00:00" }),
    baseSchedule({ id: 2, staff: { id: 2, fullName: "Trần Thị B", specialtyId: null, specialtyName: null }, shiftType: { id: "L02", name: "Thông tầm", isOvernight: false }, workDate: "2026-06-16T00:00:00" }),
    baseSchedule({ id: 3, staff: { id: 1, fullName: "Nguyễn Văn A", specialtyId: null, specialtyName: null }, shiftType: { id: "L03", name: "Dịch vụ", isOvernight: false }, workDate: "2026-06-20T00:00:00", hasConflict: true }),
  ];

  it("search theo tên nhân sự", () => {
    const r = filterSchedules(data, { ...EMPTY_FILTERS, search: "Trần" });
    expect(r.length).toBe(1);
    expect(r[0]?.id).toBe(2);
  });

  it("search theo tên shiftType", () => {
    const r = filterSchedules(data, { ...EMPTY_FILTERS, search: "thông" });
    expect(r.length).toBe(1);
    expect(r[0]?.id).toBe(2);
  });

  it("search theo ID", () => {
    const r = filterSchedules(data, { ...EMPTY_FILTERS, search: "3" });
    expect(r.length).toBe(1);
    expect(r[0]?.id).toBe(3);
  });

  it("filter theo shiftType", () => {
    const r = filterSchedules(data, { ...EMPTY_FILTERS, filterType: "L01" });
    expect(r.length).toBe(1);
  });

  it("filter theo staffId", () => {
    const r = filterSchedules(data, { ...EMPTY_FILTERS, filterStaff: "1" });
    expect(r.length).toBe(2);
  });

  it("filter theo conflict", () => {
    const r1 = filterSchedules(data, { ...EMPTY_FILTERS, filterConflict: "conflict" });
    const r2 = filterSchedules(data, { ...EMPTY_FILTERS, filterConflict: "clean" });
    expect(r1.length).toBe(1);
    expect(r2.length).toBe(2);
  });

  it("filter theo date range", () => {
    const r = filterSchedules(data, { ...EMPTY_FILTERS, dateFrom: "2026-06-16T00:00:00", dateTo: "2026-06-16T23:59:59" });
    expect(r.length).toBe(1);
    expect(r[0]?.id).toBe(2);
  });

  it("kết hợp nhiều filter", () => {
    const r = filterSchedules(data, { ...EMPTY_FILTERS, filterStaff: "1", filterConflict: "conflict" });
    expect(r.length).toBe(1);
    expect(r[0]?.id).toBe(3);
  });
});

describe("sortSchedules", () => {
  const data = [
    baseSchedule({ id: 1, workDate: "2026-06-20T00:00:00" }),
    baseSchedule({ id: 2, workDate: "2026-06-10T00:00:00" }),
    baseSchedule({ id: 3, workDate: "2026-06-15T00:00:00" }),
  ];

  it("sort asc theo workDate", () => {
    const r = sortSchedules(data, "workDate", "asc").map((s) => s.id);
    expect(r).toEqual([2, 3, 1]);
  });

  it("sort desc theo workDate", () => {
    const r = sortSchedules(data, "workDate", "desc").map((s) => s.id);
    expect(r).toEqual([1, 3, 2]);
  });

  it("sort theo hasConflict (true xuống cuối khi asc)", () => {
    const data2 = [
      baseSchedule({ id: 1, hasConflict: true }),
      baseSchedule({ id: 2, hasConflict: false }),
    ];
    const r = sortSchedules(data2, "hasConflict", "asc").map((s) => s.id);
    expect(r).toEqual([2, 1]);
  });

  it("không mutate input", () => {
    const original = [...data];
    sortSchedules(data, "workDate", "desc");
    expect(data).toEqual(original);
  });
});

describe("paginate", () => {
  const arr = Array.from({ length: 25 }, (_, i) => i + 1);

  it("page 1 trả 20 phần tử đầu", () => {
    const { pageData, safePage, totalPages } = paginate(arr, 1);
    expect(pageData.length).toBe(20);
    expect(safePage).toBe(1);
    expect(totalPages).toBe(2);
  });

  it("page 2 trả 5 phần tử còn lại", () => {
    const { pageData, safePage } = paginate(arr, 2);
    expect(pageData.length).toBe(5);
    expect(safePage).toBe(2);
  });

  it("page out of range → snap về totalPages", () => {
    const { safePage } = paginate(arr, 99);
    expect(safePage).toBe(2);
  });

  it("empty array → totalPages = 1, pageData = []", () => {
    const { pageData, safePage, totalPages } = paginate<number>([], 1);
    expect(pageData).toEqual([]);
    expect(safePage).toBe(1);
    expect(totalPages).toBe(1);
  });
});

describe("applyTablePipeline", () => {
  it("kết hợp filter + sort + paginate", () => {
    const data = Array.from({ length: 30 }, (_, i) =>
      baseSchedule({
        id: i + 1,
        workDate: `2026-06-${String((i % 30) + 1).padStart(2, "0")}T00:00:00`,
        staff: { id: i % 5, fullName: `Staff ${i % 5}`, specialtyId: null, specialtyName: null },
      }),
    );
    const { pageData, totalPages, totalFiltered } = applyTablePipeline(data, { ...EMPTY_FILTERS, sortKey: "workDate", sortDir: "asc" }, 1);
    expect(pageData.length).toBe(20);
    expect(totalPages).toBe(2);
    expect(totalFiltered).toBe(30);
  });

  it("totalFiltered đếm sau filter, trước paginate", () => {
    const data = Array.from({ length: 10 }, (_, i) =>
      baseSchedule({
        id: i + 1,
        workDate: `2026-06-${String(i + 1).padStart(2, "0")}T00:00:00`,
        shiftType: { id: i < 3 ? "L01" : "L02", name: i < 3 ? "Trực 24/24" : "Thông tầm", isOvernight: i < 3 },
      }),
    );
    const { totalFiltered, pageData } = applyTablePipeline(data, { ...EMPTY_FILTERS, filterType: "L01" }, 1);
    expect(totalFiltered).toBe(3);
    expect(pageData.length).toBe(3);
  });
});

describe("getUniqueStaff", () => {
  it("dedupe theo staff id và sort theo tên", () => {
    const data = [
      baseSchedule({ staff: { id: 1, fullName: "B", specialtyId: null, specialtyName: null } }),
      baseSchedule({ id: 2, staff: { id: 2, fullName: "A", specialtyId: null, specialtyName: null } }),
      baseSchedule({ id: 3, staff: { id: 1, fullName: "B", specialtyId: null, specialtyName: null } }),
    ];
    const r = getUniqueStaff(data);
    expect(r).toEqual([
      [2, "A"],
      [1, "B"],
    ]);
  });
});

describe("countActiveFilters", () => {
  it("đếm đúng số filter khác default", () => {
    expect(countActiveFilters(EMPTY_FILTERS)).toBe(0);
    expect(countActiveFilters({ ...EMPTY_FILTERS, search: "a" })).toBe(1);
    expect(countActiveFilters({ ...EMPTY_FILTERS, filterType: "L01", filterStaff: "5", dateFrom: "2026-01-01" })).toBe(3);
  });
});

describe("getInitials", () => {
  it("lấy 2 ký tự đầu của từ cuối", () => {
    expect(getInitials("Nguyễn Văn An")).toBe("AN");
    expect(getInitials("Trần Thị Bích")).toBe("BÍ");
  });

  it("một từ", () => {
    expect(getInitials("Lan")).toBe("LA");
  });

  it("chuỗi rỗng", () => {
    expect(getInitials("")).toBe("");
  });
});

describe("isSameDay", () => {
  it("cùng ngày trả true", () => {
    const ref = new Date(2026, 5, 15, 10, 30);
    expect(isSameDay(new Date(2026, 5, 15, 23, 59), ref)).toBe(true);
  });

  it("khác ngày trả false", () => {
    const ref = new Date(2026, 5, 15);
    expect(isSameDay(new Date(2026, 5, 16), ref)).toBe(false);
  });

  it("mặc định so với hôm nay", () => {
    const today = new Date();
    expect(isSameDay(today)).toBe(true);
  });
});
