import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { useReplacementSuggestions } from "@/hooks/monthly-schedule/useReplacementSuggestions";
import { api } from "@/lib/api";
import * as autoScheduleApi from "@/lib/api/autoScheduleApi";
import type { ReplacementSuggestion } from "@/types/api";

vi.mock("@/lib/api", () => ({
  // The api instance is forwarded to suggestReplacements; only the latter is spied.
  api: { _noop: true },
}));

const suggestSpy = vi.spyOn(autoScheduleApi, "suggestReplacements");

const fakeSuggestion: ReplacementSuggestion = {
  originalScheduleId: 42,
  originalStaffId: 7,
  originalStaffName: "Nguyễn Văn An",
  workDate: "2026-09-01",
  shiftTypeId: "L01",
  shiftTypeName: "Trực 24/24",
  totalCandidates: 5,
  availableCount: 3,
  suggestions: [
    {
      staffId: 11,
      staffName: "Trần Thị Bình",
      specialty: "Ngoại",
      currentWorkload: 4,
      conflicts: [],
      isAvailable: true,
      reason: "Không có xung đột",
    },
  ],
};

describe("useReplacementSuggestions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not fetch when scheduleId is null", () => {
    const { result } = renderHook(() => useReplacementSuggestions(null, true));
    expect(suggestSpy).not.toHaveBeenCalled();
    expect(result.current.data).toBeNull();
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("does not fetch when trigger is false even with a valid scheduleId", () => {
    const { result } = renderHook(() => useReplacementSuggestions(42, false));
    expect(suggestSpy).not.toHaveBeenCalled();
    expect(result.current.data).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("fetches when triggered and exposes data on success", async () => {
    suggestSpy.mockResolvedValue(fakeSuggestion);

    const { result } = renderHook(() => useReplacementSuggestions(42, true));

    expect(result.current.loading).toBe(true);
    expect(suggestSpy).toHaveBeenCalledWith(api, 42);

    await waitFor(() => {
      expect(result.current.data).toEqual(fakeSuggestion);
    });
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("sets error message on fetch failure", async () => {
    suggestSpy.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => useReplacementSuggestions(42, true));

    await waitFor(() => {
      expect(result.current.error).toBe("Không thể tải đề xuất thay thế.");
    });
    expect(result.current.data).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("reset() clears data / error / loading without re-fetching", async () => {
    suggestSpy.mockResolvedValue(fakeSuggestion);

    const { result } = renderHook(() => useReplacementSuggestions(42, true));

    await waitFor(() => expect(result.current.data).toEqual(fakeSuggestion));

    act(() => {
      result.current.reset();
    });

    expect(result.current.data).toBeNull();
    expect(result.current.error).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("re-fetches when scheduleId changes (trigger stays true)", async () => {
    suggestSpy.mockImplementation((_client, id) =>
      Promise.resolve({ ...fakeSuggestion, originalScheduleId: id }),
    );

    const { result, rerender } = renderHook(
      ({ id }: { id: number | null }) => useReplacementSuggestions(id, true),
      { initialProps: { id: 1 as number | null } },
    );

    await waitFor(() => expect(result.current.data?.originalScheduleId).toBe(1));
    rerender({ id: 2 });
    await waitFor(() => expect(result.current.data?.originalScheduleId).toBe(2));

    expect(suggestSpy).toHaveBeenCalledWith(api, 1);
    expect(suggestSpy).toHaveBeenCalledWith(api, 2);
  });
});
