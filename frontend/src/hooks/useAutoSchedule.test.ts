import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";

// Mock @/lib/api so async functions don't actually call out.
vi.mock("@/lib/api", () => ({
  api: {
    previewAutoSchedule: vi.fn(),
    applyPreview: vi.fn().mockResolvedValue({ data: undefined }),
    saveScheduleTemplate: vi.fn(),
    applyTemplate: vi.fn(),
  },
  getErrorMessage: (_e: unknown, fallback: string) => fallback,
}));

import { api } from "@/lib/api";
import { useAutoSchedule } from "@/hooks/useAutoSchedule";

describe("useAutoSchedule — state defaults", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("starts with no preview, no edits, no message, GREEDY algorithm", () => {
    const { result } = renderHook(() => useAutoSchedule());
    const [state] = result.current;
    expect(state.previewResult).toBeNull();
    expect(state.editedPreview).toEqual([]);
    expect(state.removedShiftTypes.size).toBe(0);
    expect(state.applying).toBe(false);
    expect(state.running).toBe(false);
    expect(state.message).toBeNull();
    expect(state.algorithmType).toBe("GREEDY");
  });
});

describe("useAutoSchedule — synchronous actions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("setAlgorithmType updates the algorithm choice", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].setAlgorithmType("BACKTRACKING"));
    expect(result.current[0].algorithmType).toBe("BACKTRACKING");
    act(() => result.current[1].setAlgorithmType("FAIR_GREEDY"));
    expect(result.current[0].algorithmType).toBe("FAIR_GREEDY");
  });

  it("setMessage writes a transient banner message", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].setMessage("Đã lưu"));
    expect(result.current[0].message).toBe("Đã lưu");
  });

  it("clearMessage clears a transient banner message", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].setMessage("Lỗi"));
    act(() => result.current[1].clearMessage());
    expect(result.current[0].message).toBeNull();
  });

  it("clearPreview resets the preview result and all edits", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].editStaff("2026-06-15", "L01", 7));
    act(() => result.current[1].editStaff("2026-06-15", "L02", 8));
    expect(result.current[0].editedPreview).toHaveLength(2);

    act(() => result.current[1].clearPreview());
    expect(result.current[0].editedPreview).toEqual([]);
    expect(result.current[0].removedShiftTypes.size).toBe(0);
  });

  it("resetEdits clears only the user edits and writes a confirmation banner", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].setMessage("Banner cũ"));
    act(() => result.current[1].editStaff("2026-06-15", "L01", 7));

    act(() => result.current[1].resetEdits());

    expect(result.current[0].editedPreview).toEqual([]);
    // resetEdits is intentionally chatty — it replaces the previous message
    // with a confirmation. This is the contract callers (the auto-scheduling
    // page) rely on for user feedback.
    expect(result.current[0].message).toBe("Đã hủy thay đổi.");
  });
});

describe("useAutoSchedule — editStaff (inline edits)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("adds a new edit when no entry exists for (date, shiftType)", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].editStaff("2026-06-15", "L01", 7));
    expect(result.current[0].editedPreview).toEqual([
      { workDate: "2026-06-15", shiftTypeId: "L01", staffId: 7 },
    ]);
  });

  it("replaces the staffId when an entry already exists for (date, shiftType)", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].editStaff("2026-06-15", "L01", 7));
    act(() => result.current[1].editStaff("2026-06-15", "L01", 9));
    expect(result.current[0].editedPreview).toEqual([
      { workDate: "2026-06-15", shiftTypeId: "L01", staffId: 9 },
    ]);
    expect(result.current[0].editedPreview).toHaveLength(1);
  });

  it("keeps separate entries for different shiftTypes on the same date", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].editStaff("2026-06-15", "L01", 7));
    act(() => result.current[1].editStaff("2026-06-15", "L02", 8));
    expect(result.current[0].editedPreview).toEqual([
      { workDate: "2026-06-15", shiftTypeId: "L01", staffId: 7 },
      { workDate: "2026-06-15", shiftTypeId: "L02", staffId: 8 },
    ]);
  });
});

describe("useAutoSchedule — editShiftType (rewire shift)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("adds a new edit when changing shift type on a (date, staff) row", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].editShiftType("2026-06-15", "L01", "L02", 7));
    expect(result.current[0].editedPreview).toEqual([
      { workDate: "2026-06-15", shiftTypeId: "L02", staffId: 7 },
    ]);
    // removedShiftTypes carries the (date, oldType, staff) tuple so the
    // backend knows to delete the original row.
    expect([...result.current[0].removedShiftTypes]).toContain("2026-06-15_L01_7");
  });

  it("removes the entry entirely when newShiftTypeId is empty", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].editShiftType("2026-06-15", "L01", "", 7));
    expect(result.current[0].editedPreview).toEqual([]);
    expect([...result.current[0].removedShiftTypes]).toContain("2026-06-15_L01_7");
  });

  it("no-op when newShiftTypeId equals oldShiftTypeId (mark removed, no replacement)", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].editShiftType("2026-06-15", "L01", "L01", 7));
    // editedPreview stays empty because the old and new shift types match —
    // we only mark it removed; the backend sees no replacement row to add.
    expect(result.current[0].editedPreview).toEqual([]);
    expect([...result.current[0].removedShiftTypes]).toContain("2026-06-15_L01_7");
  });
});

describe("useAutoSchedule — applyPreview", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.applyPreview).mockResolvedValue({
      success: true,
      data: undefined,
      timestamp: "2026-07-07T00:00:00Z",
    });
  });

  it("sends removed schedule tuples when applying shift-type edits", async () => {
    const onSuccess = vi.fn();
    const { result } = renderHook(() => useAutoSchedule());

    act(() => result.current[1].editShiftType("2026-06-15", "L01", "L02", 7));

    await act(async () => {
      await result.current[1].applyPreview(1, result.current[0].editedPreview, onSuccess);
    });

    expect(api.applyPreview).toHaveBeenCalledWith({
      periodId: 1,
      algorithmType: "GREEDY",
      schedules: [{ workDate: "2026-06-15", shiftTypeId: "L02", staffId: 7 }],
      removedSchedules: [{ workDate: "2026-06-15", shiftTypeId: "L01", staffId: 7 }],
    });
    expect(onSuccess).toHaveBeenCalledTimes(1);
  });
});
