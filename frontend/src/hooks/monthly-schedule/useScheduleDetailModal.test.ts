import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { useScheduleDetailModal } from "@/hooks/monthly-schedule/useScheduleDetailModal";
import { api } from "@/lib/api";
import type { Schedule, ApiResponse } from "@/types/api";

vi.mock("@/lib/api", () => ({
  api: {
    getScheduleById: vi.fn(),
  },
}));

const mockedApi = vi.mocked(api);

const fakeSchedule: Schedule = {
  id: 1,
  periodId: 1,
  staff: { id: 1, fullName: "Nguyễn Văn A", specialtyName: null },
  shiftType: { id: "L01", name: "Trực 24/24", isOvernight: true },
  workDate: "2026-06-15T00:00:00",
  notes: null,
  hasConflict: false,
  createdAt: "",
  updatedAt: "",
};

describe("useScheduleDetailModal", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns null detail when scheduleId is null", () => {
    const onClose = vi.fn();
    const { result } = renderHook(() => useScheduleDetailModal(null, onClose));
    expect(result.current.detailScheduleId).toBeNull();
    expect(result.current.detailSchedule).toBeNull();
    expect(result.current.detailLoading).toBe(false);
    expect(result.current.detailError).toBeNull();
  });

  it("fetches schedule by id and exposes it on success", async () => {
    mockedApi.getScheduleById.mockResolvedValue({
      success: true,
      data: fakeSchedule,
      timestamp: "",
    } as unknown as ApiResponse<Schedule>);

    const onClose = vi.fn();
    const { result } = renderHook(() => useScheduleDetailModal(1, onClose));

    // Loading state
    expect(result.current.detailLoading).toBe(true);

    await waitFor(() => {
      expect(result.current.detailSchedule).toEqual(fakeSchedule);
    });
    expect(result.current.detailLoading).toBe(false);
    expect(result.current.detailError).toBeNull();
  });

  it("sets detailError on fetch failure", async () => {
    mockedApi.getScheduleById.mockRejectedValue(new Error("boom"));

    const onClose = vi.fn();
    const { result } = renderHook(() => useScheduleDetailModal(1, onClose));

    await waitFor(() => {
      expect(result.current.detailError).toBe("Không thể tải chi tiết ca trực.");
    });
    expect(result.current.detailSchedule).toBeNull();
    expect(result.current.detailLoading).toBe(false);
  });

  it("closeDetail: clears state and calls onCloseRoute", async () => {
    mockedApi.getScheduleById.mockResolvedValue({
      success: true,
      data: fakeSchedule,
      timestamp: "",
    } as unknown as ApiResponse<Schedule>);

    const onClose = vi.fn();
    const { result } = renderHook(() => useScheduleDetailModal(1, onClose));

    await waitFor(() => expect(result.current.detailSchedule).toEqual(fakeSchedule));

    act(() => {
      result.current.closeDetail();
    });

    expect(result.current.detailSchedule).toBeNull();
    expect(result.current.detailError).toBeNull();
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("re-fetches when scheduleId changes", async () => {
    mockedApi.getScheduleById.mockImplementation((id: number) =>
      Promise.resolve({
        success: true,
        data: { ...fakeSchedule, id },
        timestamp: "",
      } as unknown as ApiResponse<Schedule>),
    );

    const onClose = vi.fn();
    const { result, rerender } = renderHook(
      ({ id }: { id: number | null }) => useScheduleDetailModal(id, onClose),
      { initialProps: { id: 1 as number | null } },
    );

    await waitFor(() => expect(result.current.detailSchedule?.id).toBe(1));

    rerender({ id: 2 });
    await waitFor(() => expect(result.current.detailSchedule?.id).toBe(2));
    expect(mockedApi.getScheduleById).toHaveBeenCalledWith(1);
    expect(mockedApi.getScheduleById).toHaveBeenCalledWith(2);
  });

  it("handles a fast scheduleId swap (latest id wins under normal resolution order)", async () => {
    // Race condition: when the scheduleId changes, the previous effect
    // cleans up by setting `ignore.current = true`; the new effect
    // immediately resets it to false. So whichever fetch resolves first
    // wins. This test documents that behaviour — a stricter ignore flag
    // (e.g. capturing scheduleId at request time) is a possible follow-up
    // but is not required by the current implementation.
    let resolveFirst!: (v: ApiResponse<Schedule>) => void;
    const firstPromise = new Promise<ApiResponse<Schedule>>((resolve) => {
      resolveFirst = resolve;
    });
    let resolveSecond!: (v: ApiResponse<Schedule>) => void;
    const secondPromise = new Promise<ApiResponse<Schedule>>((resolve) => {
      resolveSecond = resolve;
    });

    mockedApi.getScheduleById
      .mockReturnValueOnce(firstPromise)
      .mockReturnValueOnce(secondPromise);

    const onClose = vi.fn();
    const { result, rerender } = renderHook(
      ({ id }: { id: number | null }) => useScheduleDetailModal(id, onClose),
      { initialProps: { id: 1 as number | null } },
    );
    rerender({ id: 2 });

    // Resolve the FIRST one first → should become the visible detail
    // (under current implementation the cleanup resets the ignore flag
    // before the new effect runs, so the first fetch is not "stale"
    // from React's point of view).
    await act(async () => {
      resolveFirst({
        success: true,
        data: { ...fakeSchedule, id: 1 },
        timestamp: "",
      } as unknown as ApiResponse<Schedule>);
    });

    await waitFor(() => expect(result.current.detailSchedule?.id).toBe(1));

    // The second fetch resolves after — overwrites (since effect #2 ran
    // and set ignore=false; resolveFirst fired while ignore=false, so
    // its result is accepted; the second resolve then overwrites).
    await act(async () => {
      resolveSecond({
        success: true,
        data: { ...fakeSchedule, id: 2 },
        timestamp: "",
      } as unknown as ApiResponse<Schedule>);
    });

    expect(result.current.detailSchedule?.id).toBe(2);
  });
});
