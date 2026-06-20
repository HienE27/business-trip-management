import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { useSchedulePeriodData } from "@/hooks/useSchedulePeriodData";
import { api } from "@/lib/api";
import type { SchedulePeriod, Staff, Schedule, ConflictCheckResponse } from "@/types/api";

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

// Mock next/navigation so we can simulate pathname changes during tests.
let mockedPathname = "/dashboard";
vi.mock("next/navigation", () => ({
  usePathname: () => mockedPathname,
}));

const mockedApi = vi.mocked(api);

const fakePeriod: SchedulePeriod = {
  id: 1,
  periodName: "Tháng 6/2026",
  startDate: "2026-06-01",
  endDate: "2026-06-30",
  status: "DRAFT",
  createdAt: "2026-05-01",
  updatedAt: "2026-05-01",
};

const fakeStaff = {
  id: 1,
  username: "nvana",
  fullName: "Nguyễn Văn A",
  email: "a@hospital.vn",
  phone: "0901",
  maxShiftsPerMonth: 15,
  isActive: true,
  specialty: undefined,
  roles: ["STAFF"],
  createdAt: "2026-01-01",
  updatedAt: "2026-01-01",
};

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

const fakeConflict: ConflictCheckResponse = {
  periodId: 1,
  hasConflicts: false,
  totalConflicts: 0,
  conflicts: [],
  coverageGaps: [],
  hasCoverageGaps: false,
  totalCoverageGaps: 0,
};

describe("useSchedulePeriodData", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedPathname = "/dashboard";
  });

  it("tự fetch periods, staff, specialties khi mount", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url === "/periods") return Promise.resolve([fakePeriod]);
      if (url === "/staff/active") return Promise.resolve([fakeStaff]);
      if (url === "/specialties") return Promise.resolve([]);
      if (url.startsWith("/schedules/period/")) return Promise.resolve([fakeSchedule]);
      if (url.startsWith("/schedules/conflicts/check/")) return Promise.resolve(fakeConflict);
      if (url.startsWith("/schedules/compensation-days/")) return Promise.resolve([]);
      if (url.startsWith("/shift-requirements/period/")) return Promise.resolve([]);
      return Promise.reject(new Error("Unexpected URL: " + url));
    });

    const { result } = renderHook(() => useSchedulePeriodData());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.periods).toEqual([fakePeriod]);
    expect(result.current.activeStaff).toEqual([fakeStaff]);
    expect(result.current.schedules).toEqual([fakeSchedule]);
    expect(result.current.conflictData).toEqual(fakeConflict);
  });

  it("tự chọn period DRAFT đầu tiên khi autoSelectPeriod = true (default)", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url === "/periods") return Promise.resolve([fakePeriod]);
      if (url === "/staff/active") return Promise.resolve([]);
      if (url === "/specialties") return Promise.resolve([]);
      return Promise.resolve([]);
    });

    const { result } = renderHook(() => useSchedulePeriodData());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.selectedPeriodId).toBe(1);
    expect(result.current.selectedPeriod?.id).toBe(1);
  });

  it("không tự chọn period khi autoSelectPeriod = false", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url === "/periods") return Promise.resolve([fakePeriod]);
      if (url === "/staff/active") return Promise.resolve([]);
      if (url === "/specialties") return Promise.resolve([]);
      return Promise.resolve([]);
    });

    const { result } = renderHook(() => useSchedulePeriodData({ autoSelectPeriod: false }));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.selectedPeriodId).toBeNull();
  });

  it("setSelectedPeriodId trigger reload period-specific data", async () => {
    let callCount = 0;
    mockedApi.get.mockImplementation((url: string) => {
      if (url === "/periods") {
        return Promise.resolve([
          fakePeriod,
          { ...fakePeriod, id: 2, periodName: "Tháng 7/2026", status: "DRAFT" as const },
        ]);
      }
      if (url === "/staff/active") return Promise.resolve([]);
      if (url === "/specialties") return Promise.resolve([]);
      if (url.startsWith("/schedules/period/")) {
        callCount++;
        return Promise.resolve([fakeSchedule]);
      }
      return Promise.resolve([]);
    });

    const { result } = renderHook(() => useSchedulePeriodData());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    const initialCalls = callCount;
    act(() => {
      result.current.setSelectedPeriodId(2);
    });

    await waitFor(() => {
      expect(callCount).toBeGreaterThan(initialCalls);
    });
    expect(result.current.selectedPeriodId).toBe(2);
  });

  it("xử lý lỗi fetch mà không crash", async () => {
    mockedApi.get.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => useSchedulePeriodData());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.periods).toEqual([]);
    expect(result.current.activeStaff).toEqual([]);
    expect(result.current.message).toBeTruthy();
  });

  it("không poll conflict khi conflictPollMs = 0 (default)", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url === "/periods") return Promise.resolve([fakePeriod]);
      if (url === "/staff/active") return Promise.resolve([]);
      if (url === "/specialties") return Promise.resolve([]);
      if (url.startsWith("/schedules/conflicts/check/")) return Promise.resolve(fakeConflict);
      return Promise.resolve([]);
    });

    const { result } = renderHook(() => useSchedulePeriodData());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    const before = mockedApi.get.mock.calls.filter(([url]) =>
      String(url).includes("/schedules/conflicts/check/")
    ).length;

    // Chờ 100ms — đủ để phát hiện polling nếu có
    await new Promise((r) => setTimeout(r, 100));

    const after = mockedApi.get.mock.calls.filter(([url]) =>
      String(url).includes("/schedules/conflicts/check/")
    ).length;

    expect(after).toBe(before);
  });

  describe("banner / message", () => {
    it("clear message sạch khi pathname đổi", async () => {
      mockedApi.get.mockImplementation((url: string) => {
        if (url === "/periods") return Promise.resolve([fakePeriod]);
        if (url === "/staff/active") return Promise.resolve([]);
        if (url === "/specialties") return Promise.resolve([]);
        return Promise.resolve([]);
      });

      mockedPathname = "/dashboard";
      const { result, rerender } = renderHook(() => useSchedulePeriodData());

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      act(() => {
        result.current.setMessage("Banner cũ từ trang khác");
      });
      expect(result.current.message).toBe("Banner cũ từ trang khác");

      // Navigate sang page khác và rerender hook để trigger useEffect[pathname]
      mockedPathname = "/periods";
      rerender();

      await waitFor(() => {
        expect(result.current.message).toBeNull();
      });
    });

    it("auto-dismiss message sau 5s", async () => {
      vi.useFakeTimers();
      try {
        mockedApi.get.mockImplementation((url: string) => {
          if (url === "/periods") return Promise.resolve([fakePeriod]);
          if (url === "/staff/active") return Promise.resolve([]);
          if (url === "/specialties") return Promise.resolve([]);
          return Promise.resolve([]);
        });

        const { result } = renderHook(() => useSchedulePeriodData());

        // Đợi fetch ban đầu hoàn tất (real timers cần thiết cho async).
        // Nhưng vì đang fake, hãy chạy với real trước rồi switch.
        vi.useRealTimers();
        await waitFor(() => {
          expect(result.current.loading).toBe(false);
        });
        vi.useFakeTimers();

        act(() => {
          result.current.setMessage("Đã lưu");
        });
        expect(result.current.message).toBe("Đã lưu");

        act(() => {
          vi.advanceTimersByTime(4_999);
        });
        expect(result.current.message).toBe("Đã lưu");

        act(() => {
          vi.advanceTimersByTime(2);
        });
        expect(result.current.message).toBeNull();
      } finally {
        vi.useRealTimers();
      }
    });
  });
});
