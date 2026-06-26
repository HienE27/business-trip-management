import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useMonthlyScheduleUrlState } from "@/hooks/monthly-schedule/useMonthlyScheduleUrlState";

const mockRouter = {
  push: vi.fn(),
  replace: vi.fn(),
  refresh: vi.fn(),
};

let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useRouter: () => mockRouter,
  useSearchParams: () => mockSearchParams,
}));

describe("useMonthlyScheduleUrlState", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
  });

  it("returns default state when no URL params", () => {
    const { result } = renderHook(() => useMonthlyScheduleUrlState());
    expect(result.current.selectedTab).toBe("ALL");
    expect(result.current.selectedPanel).toBe("overview");
    expect(result.current.parsedScheduleId).toBeNull();
    expect(result.current.parsedStaffId).toBeNull();
    expect(result.current.parsedSpecialtyId).toBeNull();
    expect(result.current.periodId).toBeNull();
  });

  it("parses valid tab value", () => {
    mockSearchParams = new URLSearchParams("tab=L02");
    const { result } = renderHook(() => useMonthlyScheduleUrlState());
    expect(result.current.selectedTab).toBe("L02");
  });

  it("falls back to ALL for unknown tab", () => {
    mockSearchParams = new URLSearchParams("tab=BOGUS");
    const { result } = renderHook(() => useMonthlyScheduleUrlState());
    expect(result.current.selectedTab).toBe("ALL");
  });

  it("parses panel value (overview/conflicts/summary)", () => {
    mockSearchParams = new URLSearchParams("panel=conflicts");
    const { result } = renderHook(() => useMonthlyScheduleUrlState());
    expect(result.current.selectedPanel).toBe("conflicts");
  });

  it("falls back to overview for unknown panel", () => {
    mockSearchParams = new URLSearchParams("panel=garbage");
    const { result } = renderHook(() => useMonthlyScheduleUrlState());
    expect(result.current.selectedPanel).toBe("overview");
  });

  it("parses numeric IDs from query string", () => {
    mockSearchParams = new URLSearchParams(
      "scheduleId=42&staffId=7&specialtyId=3&periodId=1",
    );
    const { result } = renderHook(() => useMonthlyScheduleUrlState());
    expect(result.current.parsedScheduleId).toBe(42);
    expect(result.current.parsedStaffId).toBe(7);
    expect(result.current.parsedSpecialtyId).toBe(3);
    expect(result.current.periodId).toBe(1);
  });

  it("returns null for non-numeric IDs", () => {
    mockSearchParams = new URLSearchParams("scheduleId=abc");
    const { result } = renderHook(() => useMonthlyScheduleUrlState());
    expect(result.current.parsedScheduleId).toBeNull();
  });

  it("setQueryState: writes new tab and panel, replaces URL", () => {
    const { result } = renderHook(() => useMonthlyScheduleUrlState());

    act(() => {
      result.current.setQueryState({ tab: "L03", panel: "conflicts" });
    });

    expect(mockRouter.replace).toHaveBeenCalledWith(
      expect.stringContaining("tab=L03"),
      { scroll: false },
    );
    const url = mockRouter.replace.mock.calls[0][0] as string;
    expect(url).toContain("panel=conflicts");
  });

  it("setQueryState: null periodId removes the key", () => {
    mockSearchParams = new URLSearchParams("periodId=5");
    const { result } = renderHook(() => useMonthlyScheduleUrlState());

    act(() => {
      result.current.setQueryState({ periodId: null });
    });

    const url = mockRouter.replace.mock.calls[0][0] as string;
    expect(url).not.toContain("periodId");
  });

  it("setQueryState: undefined periodId is a no-op for the key", () => {
    mockSearchParams = new URLSearchParams("periodId=5");
    const { result } = renderHook(() => useMonthlyScheduleUrlState());

    act(() => {
      result.current.setQueryState({ tab: "L04" });
    });

    const url = mockRouter.replace.mock.calls[0][0] as string;
    expect(url).toContain("periodId=5");
    expect(url).toContain("tab=L04");
  });

  it("openScheduleDetail: pushes URL with scheduleId", () => {
    const { result } = renderHook(() => useMonthlyScheduleUrlState());

    act(() => {
      result.current.openScheduleDetail(123);
    });

    expect(mockRouter.push).toHaveBeenCalledWith(
      expect.stringContaining("scheduleId=123"),
      { scroll: false },
    );
  });

  it("closeScheduleDetail: removes only scheduleId key, preserves others", () => {
    mockSearchParams = new URLSearchParams("scheduleId=99&panel=summary&tab=L02");
    const { result } = renderHook(() => useMonthlyScheduleUrlState());

    act(() => {
      result.current.closeScheduleDetail();
    });

    const url = mockRouter.replace.mock.calls[0][0] as string;
    expect(url).not.toContain("scheduleId");
    expect(url).toContain("panel=summary");
    expect(url).toContain("tab=L02");
  });
});
