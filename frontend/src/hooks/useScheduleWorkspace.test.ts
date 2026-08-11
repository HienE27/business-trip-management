import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { useScheduleWorkspace } from "@/hooks/useScheduleWorkspace";
import { api } from "@/lib/api";
import type {
  SchedulePeriod,
  Staff,
  Schedule,
  ConflictCheckResponse,
  CompensationDay,
} from "@/types/api";

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

let mockedPathname = "/monthly-schedule";
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

const fakeStaff: Staff = {
  id: 1,
  staffCode: "NV001",
  username: "nvana",
  fullName: "Nguyễn Văn A",
  email: "a@hospital.vn",
  phone: "0901",
  maxShiftsPerMonth: 15,
  isActive: true,
  status: "ACTIVE",
  specialty: undefined,
  roles: ["STAFF"],
  createdAt: "2026-01-01",
  updatedAt: "2026-01-01",
};

const fakeSchedule: Schedule = {
  id: 1,
  periodId: 1,
  staff: { id: 1, staffCode: "NV001", fullName: "Nguyễn Văn A", specialtyName: null },
  shiftType: { id: "L01", name: "Trực 24/24", isOvernight: true },
  workDate: "2026-06-15T00:00:00",
  notes: null,
  hasConflict: false,
  createdAt: "",
  updatedAt: "",
};

const fakeCompensation: CompensationDay = {
  id: 1,
  staffId: 1,
  staffName: "BS. Nguyễn Văn A",
  shiftDate: "2026-06-15T00:00:00",
  compensationDate: "2026-06-16T00:00:00",
};

const fakeConflictClean: ConflictCheckResponse = {
  periodId: 1,
  hasConflicts: false,
  totalConflicts: 0,
  conflicts: [],
  coverageGaps: [],
  hasCoverageGaps: false,
  totalCoverageGaps: 0,
};

const fakeConflictWith: ConflictCheckResponse = {
  ...fakeConflictClean,
  hasConflicts: true,
  totalConflicts: 3,
  conflicts: [
    { type: "COMPENSATION_CONFLICT", staffId: 1, workDate: "2026-06-15", description: "test" },
  ] as never,
};

function setupPeriodDataMocks(opts: {
  conflictData?: ConflictCheckResponse;
  schedules?: Schedule[];
  compensationDays?: CompensationDay[];
  activeStaff?: Staff[];
} = {}) {
  mockedApi.get.mockImplementation((url: string) => {
    if (url === "/periods") return Promise.resolve([fakePeriod]);
    if (url === "/staff/active") return Promise.resolve(opts.activeStaff ?? [fakeStaff]);
    if (url === "/specialties") return Promise.resolve([]);
    if (url.startsWith("/schedules/period/")) return Promise.resolve(opts.schedules ?? [fakeSchedule]);
    if (url.startsWith("/schedules/conflicts/check/"))
      return Promise.resolve(opts.conflictData ?? fakeConflictClean);
    if (url.startsWith("/schedules/compensation-days/"))
      return Promise.resolve(opts.compensationDays ?? []);
    if (url.startsWith("/shift-requirements/period/")) return Promise.resolve([]);
    return Promise.reject(new Error("Unexpected URL: " + url));
  });
}

describe("useScheduleWorkspace", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedPathname = "/monthly-schedule";
  });

  it("exposes state from useSchedulePeriodData + 3 action callbacks", async () => {
    setupPeriodDataMocks();
    const { result } = renderHook(() => useScheduleWorkspace());
    await waitFor(() => expect(result.current[0].loading).toBe(false));

    // State surface
    expect(result.current[0].periods).toEqual([fakePeriod]);
    expect(result.current[0].schedules).toEqual([fakeSchedule]);
    expect(result.current[0].activeStaff).toEqual([fakeStaff]);

    // Actions
    expect(typeof result.current[1].checkConflicts).toBe("function");
    expect(typeof result.current[1].publishPeriod).toBe("function");
    expect(typeof result.current[1].sendNotifications).toBe("function");
  });

  it("checkConflicts: clean period → success message", async () => {
    setupPeriodDataMocks({ conflictData: fakeConflictClean });
    const { result } = renderHook(() => useScheduleWorkspace());
    await waitFor(() => expect(result.current[0].loading).toBe(false));

    await act(async () => {
      await result.current[1].checkConflicts();
    });

    expect(result.current[0].message).toMatch(/không phát hiện xung đột/i);
  });

  it("checkConflicts: conflict period → warning message with count", async () => {
    setupPeriodDataMocks({ conflictData: fakeConflictWith });
    const { result } = renderHook(() => useScheduleWorkspace());
    await waitFor(() => expect(result.current[0].loading).toBe(false));

    await act(async () => {
      await result.current[1].checkConflicts();
    });

    expect(result.current[0].message).toMatch(/3 xung đột/i);
  });

  it("checkConflicts: API error → error message", async () => {
    mockedApi.get.mockImplementation((url: string) => {
      if (url === "/periods") return Promise.resolve([fakePeriod]);
      if (url === "/staff/active") return Promise.resolve([fakeStaff]);
      if (url === "/specialties") return Promise.resolve([]);
      if (url.startsWith("/schedules/period/")) return Promise.resolve([fakeSchedule]);
      if (url.startsWith("/schedules/conflicts/check/")) return Promise.reject(new Error("boom"));
      if (url.startsWith("/schedules/compensation-days/")) return Promise.resolve([]);
      if (url.startsWith("/shift-requirements/period/")) return Promise.resolve([]);
      return Promise.reject(new Error("Unexpected: " + url));
    });

    const { result } = renderHook(() => useScheduleWorkspace());
    await waitFor(() => expect(result.current[0].loading).toBe(false));

    await act(async () => {
      await result.current[1].checkConflicts();
    });

    expect(result.current[0].message).toMatch(/boom|Không thể kiểm tra/i);
  });

  it("publishPeriod: blocked when conflicts exist", async () => {
    setupPeriodDataMocks({ conflictData: fakeConflictWith });
    const { result } = renderHook(() => useScheduleWorkspace());
    await waitFor(() => expect(result.current[0].loading).toBe(false));

    await act(async () => {
      await result.current[1].publishPeriod();
    });

    expect(result.current[0].message).toMatch(/không thể publish/i);
    expect(mockedApi.post).not.toHaveBeenCalledWith(
      "/periods/1/publish",
      expect.anything(),
    );
  });

  it("publishPeriod: clean period → POSTs and refreshes", async () => {
    setupPeriodDataMocks({ conflictData: fakeConflictClean });
    mockedApi.post.mockResolvedValue({});
    const { result } = renderHook(() => useScheduleWorkspace());
    await waitFor(() => expect(result.current[0].loading).toBe(false));

    await act(async () => {
      await result.current[1].publishPeriod();
    });

    expect(mockedApi.post).toHaveBeenCalledWith("/periods/1/publish", {});
    // After publishPeriod completes, the workspace refreshes which
    // internally calls setMessage(null) before reloading data. So the
    // observable state is "loading then idle again with message=null".
    expect(result.current[0].loading).toBe(false);
  });

  it("sendNotifications: posts one notification per active staff", async () => {
    setupPeriodDataMocks({
      schedules: [fakeSchedule],
      compensationDays: [fakeCompensation],
      activeStaff: [fakeStaff, { ...fakeStaff, id: 2, fullName: "Trần B" }],
    });
    mockedApi.post.mockResolvedValue({});
    const { result } = renderHook(() => useScheduleWorkspace());
    await waitFor(() => expect(result.current[0].loading).toBe(false));

    await act(async () => {
      await result.current[1].sendNotifications();
    });

    expect(mockedApi.post).toHaveBeenCalledTimes(2);
    expect(mockedApi.post).toHaveBeenCalledWith(
      "/notifications",
      expect.objectContaining({ recipientId: 1, title: expect.stringContaining("Tháng 6/2026") }),
    );
    expect(mockedApi.post).toHaveBeenCalledWith(
      "/notifications",
      expect.objectContaining({ recipientId: 2 }),
    );
    expect(result.current[0].message).toMatch(/2 nhân sự/i);
  });

  it("sendNotifications: skips when no active staff", async () => {
    setupPeriodDataMocks({ activeStaff: [] });
    const { result } = renderHook(() => useScheduleWorkspace());
    await waitFor(() => expect(result.current[0].loading).toBe(false));

    await act(async () => {
      await result.current[1].sendNotifications();
    });

    expect(mockedApi.post).not.toHaveBeenCalled();
  });

  it("sendNotifications: API error surfaces as message", async () => {
    setupPeriodDataMocks({ activeStaff: [fakeStaff] });
    mockedApi.post.mockRejectedValue(new Error("network"));
    const { result } = renderHook(() => useScheduleWorkspace());
    await waitFor(() => expect(result.current[0].loading).toBe(false));

    let thrown: unknown;
    await act(async () => {
      try {
        await result.current[1].sendNotifications();
      } catch (e) {
        thrown = e;
      }
    });

    expect(result.current[0].message).toMatch(/network|Không thể gửi/i);
    expect(thrown).toBeInstanceOf(Error);
  });
});
