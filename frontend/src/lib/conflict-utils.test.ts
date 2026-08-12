import { describe, it, expect } from "vitest";
import {
  getConflictType,
  getConflictSeverityColor,
  getConflictSeverityBadgeTone,
  filterConflictsBySeverity,
  sortConflictsBySeverity,
  groupConflictsByStaff,
  normalizeConflictReasons,
} from "./conflict-utils";
import type { ConflictItem } from "@/types/schedule";

const createConflict = (overrides: Partial<ConflictItem>): ConflictItem => ({
  id: "1",
  type: "L01",
  staffName: "Test Staff",
  date: "2024-01-15",
  severity: "Chặn lưu",
  ...overrides,
});

describe("getConflictType", () => {
  it("returns correct type for trực 24/24 conflict", () => {
    const result = getConflictType("Trùng lịch trực 24/24");
    expect(result.type).toBe("Lịch trực");
    expect(result.icon).toBe("emergency");
  });

  it("returns correct type for L01 code", () => {
    const result = getConflictType("L01 conflict");
    expect(result.type).toBe("Lịch trực");
  });

  it("returns correct type for nghỉ phép", () => {
    const result = getConflictType("Trùng với nghỉ phép");
    expect(result.type).toBe("Nghỉ phép");
    expect(result.icon).toBe("event_busy");
  });

  it("returns correct type for Leave keyword", () => {
    const result = getConflictType("Leave request conflict");
    expect(result.type).toBe("Nghỉ phép");
  });

  it("returns correct type for nghỉ bù", () => {
    const result = getConflictType("Trùng ngày nghỉ bù");
    expect(result.type).toBe("Ngày nghỉ bù");
    expect(result.icon).toBe("calendar_month");
  });

  it("returns correct type for compensation keyword", () => {
    const result = getConflictType("compensation day conflict");
    expect(result.type).toBe("Ngày nghỉ bù");
  });

  it("returns correct type for ca liền kề", () => {
    const result = getConflictType("Ca làm việc liền kề");
    expect(result.type).toBe("Ca liền kề");
    expect(result.icon).toBe("schedule");
  });

  it("returns correct type for back-to-back", () => {
    const result = getConflictType("back-to-back shift conflict");
    expect(result.type).toBe("Ca liền kề");
  });

  it("returns default type for unknown conflict", () => {
    const result = getConflictType("Some random conflict");
    expect(result.type).toBe("Khác");
    expect(result.icon).toBe("warning");
  });

  it("returns default type for undefined detail", () => {
    const result = getConflictType(undefined);
    expect(result.type).toBe("Khác");
  });

  it("returns default type for empty string", () => {
    const result = getConflictType("");
    expect(result.type).toBe("Khác");
  });

  it("returns first matching type for multiple keywords", () => {
    // "trực 24/24" comes first in the map
    const result = getConflictType("Trùng lịch trực 24/24 với nghỉ bù");
    expect(result.type).toBe("Lịch trực");
  });
});

describe("getConflictSeverityColor", () => {
  it("returns error color for blocking severity", () => {
    expect(getConflictSeverityColor("Chặn lưu")).toBe("text-red-800");
  });

  it("returns tertiary color for warning severity", () => {
    expect(getConflictSeverityColor("Cảnh báo")).toBe("text-tertiary");
  });

  it("returns default color for unknown severity", () => {
    expect(getConflictSeverityColor("Unknown")).toBe("text-on-surface-variant");
  });
});

describe("getConflictSeverityBadgeTone", () => {
  it("returns error tone for blocking severity", () => {
    expect(getConflictSeverityBadgeTone("Chặn lưu")).toBe("error");
  });

  it("returns warning tone for warning severity", () => {
    expect(getConflictSeverityBadgeTone("Cảnh báo")).toBe("warning");
  });
});

describe("filterConflictsBySeverity", () => {
  const conflicts: ConflictItem[] = [
    createConflict({ id: "1", severity: "Chặn lưu" }),
    createConflict({ id: "2", severity: "Cảnh báo" }),
    createConflict({ id: "3", severity: "Chặn lưu" }),
  ];

  it("filters blocking conflicts", () => {
    const result = filterConflictsBySeverity(conflicts, "Chặn lưu");
    expect(result).toHaveLength(2);
    expect(result.every((c) => c.severity === "Chặn lưu")).toBe(true);
  });

  it("filters warning conflicts", () => {
    const result = filterConflictsBySeverity(conflicts, "Cảnh báo");
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe("2");
  });

  it("returns empty array when no matches", () => {
    const result = filterConflictsBySeverity([], "Chặn lưu");
    expect(result).toHaveLength(0);
  });
});

describe("sortConflictsBySeverity", () => {
  const conflicts: ConflictItem[] = [
    createConflict({ id: "1", severity: "Cảnh báo" }),
    createConflict({ id: "2", severity: "Chặn lưu" }),
    createConflict({ id: "3", severity: "Cảnh báo" }),
    createConflict({ id: "4", severity: "Chặn lưu" }),
  ];

  it("sorts blocking conflicts first", () => {
    const result = sortConflictsBySeverity(conflicts);
    expect(result[0].severity).toBe("Chặn lưu");
    expect(result[1].severity).toBe("Chặn lưu");
    expect(result[2].severity).toBe("Cảnh báo");
    expect(result[3].severity).toBe("Cảnh báo");
  });

  it("does not mutate original array", () => {
    const original = [...conflicts];
    sortConflictsBySeverity(conflicts);
    expect(conflicts).toEqual(original);
  });

  it("handles empty array", () => {
    const result = sortConflictsBySeverity([]);
    expect(result).toEqual([]);
  });
});

describe("groupConflictsByStaff", () => {
  const conflicts: ConflictItem[] = [
    createConflict({ id: "1", staffName: "Bs. A" }),
    createConflict({ id: "2", staffName: "Bs. B" }),
    createConflict({ id: "3", staffName: "Bs. A" }),
    createConflict({ id: "4", staffName: "Bs. C" }),
  ];

  it("groups conflicts by staff name", () => {
    const result = groupConflictsByStaff(conflicts);
    expect(result.get("Bs. A")).toHaveLength(2);
    expect(result.get("Bs. B")).toHaveLength(1);
    expect(result.get("Bs. C")).toHaveLength(1);
  });

  it("handles unknown staff name", () => {
    const result = groupConflictsByStaff([createConflict({ staffName: undefined })]);
    expect(result.get("Unknown")).toHaveLength(1);
  });

  it("handles empty array", () => {
    const result = groupConflictsByStaff([]);
    expect(result.size).toBe(0);
  });

  it("creates map with correct keys", () => {
    const result = groupConflictsByStaff(conflicts);
    expect(result.has("Bs. A")).toBe(true);
    expect(result.has("Bs. B")).toBe(true);
    expect(result.has("Bs. C")).toBe(true);
    expect(result.size).toBe(3);
  });
});

// REPORTS-CONFLICT-001: normalizeConflictReasons must sort + deduplicate so
// the same conflict set collapses into one bucket regardless of order.
describe("normalizeConflictReasons", () => {
  it("sorts reasons alphabetically using Vietnamese collation", () => {
    expect(normalizeConflictReasons(["B", "A", "C"])).toEqual(["A", "B", "C"]);
  });

  it("deduplicates repeated reasons", () => {
    expect(normalizeConflictReasons(["A", "A", "B"])).toEqual(["A", "B"]);
  });

  it("trims whitespace and drops empty strings", () => {
    expect(normalizeConflictReasons(["  A  ", "", "  ", "B"])).toEqual(["A", "B"]);
  });

  it("returns identical arrays for the same set in different orders (REPORTS-CONFLICT-001)", () => {
    const a = normalizeConflictReasons(["Trực 24/24", "Nghỉ phép", "Back-to-back"]);
    const b = normalizeConflictReasons(["Back-to-back", "Trực 24/24", "Nghỉ phép"]);
    expect(a).toEqual(b);
    expect(a.join(" + ")).toBe(b.join(" + "));
  });

  it("returns empty array for empty / undefined input", () => {
    expect(normalizeConflictReasons(undefined)).toEqual([]);
    expect(normalizeConflictReasons([])).toEqual([]);
  });
});
