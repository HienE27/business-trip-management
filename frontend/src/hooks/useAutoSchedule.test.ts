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

  it("starts with no preview, no edits, no message, BEAM_SEARCH algorithm", () => {
    const { result } = renderHook(() => useAutoSchedule());
    const [state] = result.current;
    expect(state.previewResult).toBeNull();
    expect(state.editedPreview).toEqual([]);
    expect(state.removedShiftTypes.size).toBe(0);
    expect(state.applying).toBe(false);
    expect(state.running).toBe(false);
    expect(state.message).toBeNull();
    expect(state.algorithmType).toBe("BEAM_SEARCH");
  });
});

describe("useAutoSchedule — synchronous actions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("setAlgorithmType updates the algorithm choice", () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].setAlgorithmType("ENHANCED_GREEDY"));
    expect(result.current[0].algorithmType).toBe("ENHANCED_GREEDY");
    act(() => result.current[1].setAlgorithmType("CP_SAT"));
    expect(result.current[0].algorithmType).toBe("CP_SAT");
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
      algorithmType: "BEAM_SEARCH",
      schedules: [{ workDate: "2026-06-15", shiftTypeId: "L02", staffId: 7 }],
      removedSchedules: [{ workDate: "2026-06-15", shiftTypeId: "L01", staffId: 7 }],
    });
    expect(onSuccess).toHaveBeenCalledTimes(1);
  });
});

describe("useAutoSchedule — persistence contract (Commit B)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.previewAutoSchedule).mockResolvedValue({
      success: true,
      data: { success: true, message: "ok", periodId: 1, algorithmType: "BEAM_SEARCH",
        executionTimeMs: 100, coverageRate: 0, balanceScore: 0, conflictCount: 0,
        totalSchedulesCreated: 0, schedules: [], executedAt: "" },
      timestamp: "2026-07-07T00:00:00Z",
    });
  });

  it("runPreview calls previewAutoSchedule (not applyPreview)", async () => {
    const { result } = renderHook(() => useAutoSchedule());
    await act(async () => {
      await result.current[1].runPreview(1, undefined, false);
    });
    expect(api.previewAutoSchedule).toHaveBeenCalledTimes(1);
    expect(api.applyPreview).not.toHaveBeenCalled();
  });

  it("runPreview with recommendedConfig passes it through to previewAutoSchedule", async () => {
    const { result } = renderHook(() => useAutoSchedule());
    const recommendedConfig = { l01MinPerDay: 2, l02MinPerDay: 2, l04CrossSpecialty: true };
    await act(async () => {
      await result.current[1].runPreview(1, undefined, true, recommendedConfig);
    });
    expect(api.previewAutoSchedule).toHaveBeenCalledWith(
      expect.objectContaining({ recommendedConfig }),
      expect.any(Object)
    );
    expect(api.applyPreview).not.toHaveBeenCalled();
  });

  it("applyPreview calls applyPreview (not previewAutoSchedule)", async () => {
    vi.mocked(api.applyPreview).mockResolvedValue({
      success: true, data: undefined, timestamp: "2026-07-07T00:00:00Z",
    });
    const onSuccess = vi.fn();
    const { result } = renderHook(() => useAutoSchedule());
    await act(async () => {
      await result.current[1].applyPreview(1, [], onSuccess);
    });
    expect(api.applyPreview).toHaveBeenCalledTimes(1);
    expect(api.previewAutoSchedule).not.toHaveBeenCalled();
    expect(onSuccess).toHaveBeenCalledTimes(1);
  });

  it("resetEdits makes zero API calls", async () => {
    const { result } = renderHook(() => useAutoSchedule());
    act(() => result.current[1].editStaff("2026-06-15", "L01", 7));
    act(() => result.current[1].resetEdits());
    // resetEdits only updates local state — no API calls
    expect(api.previewAutoSchedule).not.toHaveBeenCalled();
    expect(api.applyPreview).not.toHaveBeenCalled();
    expect(result.current[0].editedPreview).toEqual([]);
  });

  it("runPreview does NOT persist config or requirements", async () => {
    // Verify that calling runPreview does not trigger any POST/PUT/DELETE that
    // would persist data — it is a read-only preview (save=false in request).
    const { result } = renderHook(() => useAutoSchedule());
    await act(async () => {
      await result.current[1].runPreview(1, undefined, false);
    });
    // previewAutoSchedule is a POST, but it is idempotent preview — the mock confirms
    // no applyPreview (save=true) was called
    expect(api.previewAutoSchedule).toHaveBeenCalled();
    expect(api.applyPreview).not.toHaveBeenCalled();
  });
});
