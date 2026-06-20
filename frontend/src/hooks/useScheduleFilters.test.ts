import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useScheduleFilters } from "@/hooks/useScheduleFilters";

// Mock next/navigation with controllable searchParams and router.
let currentParams = new URLSearchParams();
let lastPushedUrl: string | null = null;
let lastPushedPush: boolean | null = null;
const mockRouter = {
  push: (url: string) => {
    lastPushedUrl = url;
    lastPushedPush = true;
    // Update the simulated URL.
    const qIndex = url.indexOf("?");
    currentParams = qIndex === -1 ? new URLSearchParams() : new URLSearchParams(url.slice(qIndex + 1));
  },
  replace: (url: string) => {
    lastPushedUrl = url;
    lastPushedPush = false;
    const qIndex = url.indexOf("?");
    currentParams = qIndex === -1 ? new URLSearchParams() : new URLSearchParams(url.slice(qIndex + 1));
  },
};

vi.mock("next/navigation", () => ({
  useRouter: () => mockRouter,
  useSearchParams: () => currentParams,
}));

describe("useScheduleFilters", () => {
  beforeEach(() => {
    currentParams = new URLSearchParams();
    lastPushedUrl = null;
    lastPushedPush = null;
  });

  it("defaults to ALL tab, no staff, no date", () => {
    const { result } = renderHook(() => useScheduleFilters());
    expect(result.current.selectedTab).toBe("ALL");
    expect(result.current.selectedStaffId).toBeNull();
    expect(result.current.selectedDate).toBeNull();
  });

  it("parses valid tab from URL", () => {
    currentParams = new URLSearchParams("tab=L02");
    const { result } = renderHook(() => useScheduleFilters());
    expect(result.current.selectedTab).toBe("L02");
  });

  it("falls back to ALL for invalid tab", () => {
    currentParams = new URLSearchParams("tab=NOT_A_TAB");
    const { result } = renderHook(() => useScheduleFilters());
    expect(result.current.selectedTab).toBe("ALL");
  });

  it("parses valid staffId from URL", () => {
    currentParams = new URLSearchParams("staffId=42");
    const { result } = renderHook(() => useScheduleFilters());
    expect(result.current.selectedStaffId).toBe(42);
  });

  it("parses valid date from URL", () => {
    currentParams = new URLSearchParams("date=2026-06-15");
    const { result } = renderHook(() => useScheduleFilters());
    expect(result.current.selectedDate).toBe("2026-06-15");
  });

  it("rejects malformed date", () => {
    currentParams = new URLSearchParams("date=15-06-2026");
    const { result } = renderHook(() => useScheduleFilters());
    expect(result.current.selectedDate).toBeNull();
  });

  it("rejects non-numeric staffId", () => {
    currentParams = new URLSearchParams("staffId=not-a-number");
    const { result } = renderHook(() => useScheduleFilters());
    expect(result.current.selectedStaffId).toBeNull();
  });

  it("setTab writes to URL via router.replace by default", () => {
    const { result } = renderHook(() => useScheduleFilters());
    act(() => result.current.setTab("L03"));
    expect(lastPushedUrl).toContain("tab=L03");
    expect(lastPushedPush).toBe(false);
  });

  it("setTab('ALL') removes tab param from URL", () => {
    currentParams = new URLSearchParams("tab=L04");
    const { result } = renderHook(() => useScheduleFilters());
    act(() => result.current.setTab("ALL"));
    expect(lastPushedUrl).not.toContain("tab=");
  });

  it("setStaffId(null) removes staffId param", () => {
    currentParams = new URLSearchParams("staffId=5");
    const { result } = renderHook(() => useScheduleFilters());
    act(() => result.current.setStaffId(null));
    expect(lastPushedUrl).not.toContain("staffId=");
  });

  it("setDate accepts null and clears the param", () => {
    currentParams = new URLSearchParams("date=2026-06-01");
    const { result } = renderHook(() => useScheduleFilters());
    act(() => result.current.setDate(null));
    expect(lastPushedUrl).not.toContain("date=");
  });

  it("applyFilters updates multiple filters at once", () => {
    const { result } = renderHook(() => useScheduleFilters());
    act(() => result.current.applyFilters({
      selectedTab: "L01",
      selectedStaffId: 7,
      selectedDate: "2026-06-20",
    }));
    expect(lastPushedUrl).toContain("tab=L01");
    expect(lastPushedUrl).toContain("staffId=7");
    expect(lastPushedUrl).toContain("date=2026-06-20");
  });

  it("applyFilters with empty object is a no-op (URL unchanged)", () => {
    currentParams = new URLSearchParams("tab=L02");
    const { result } = renderHook(() => useScheduleFilters());
    act(() => result.current.applyFilters({}));
    // Should still produce a URL, but without new params added
    expect(lastPushedUrl).toContain("tab=L02");
    expect(lastPushedUrl).not.toContain("staffId=");
  });

  it("uses basePath from options to keep the user on the configured route", () => {
    const { result } = renderHook(() => useScheduleFilters({ basePath: "/monthly-schedule" }));
    act(() => result.current.setTab("L02"));
    expect(lastPushedUrl?.startsWith("/monthly-schedule?")).toBe(true);
  });

  it("router.push is used when push:true", () => {
    const { result } = renderHook(() => useScheduleFilters({ push: true }));
    act(() => result.current.setTab("L02"));
    expect(lastPushedPush).toBe(true);
  });
});