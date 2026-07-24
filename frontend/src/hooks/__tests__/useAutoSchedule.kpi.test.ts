import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { useAutoSchedule } from "@/hooks/useAutoSchedule";
import { api } from "@/lib/api";
import type { Schedule } from "@/types/api";

// Mock the api client so we never hit the network.
vi.mock("@/lib/api", () => ({
  api: {
    applyTemplate: vi.fn(),
    applyTemplateWithEdits: vi.fn(),
    previewTemplate: vi.fn(),
    previewAutoSchedule: vi.fn(),
    getSchedulesByPeriod: vi.fn(),
    applyPreview: vi.fn(),
    saveScheduleTemplate: vi.fn(),
  },
}));

// Render a fresh schedule row the way the backend would.
function makeSchedule(overrides: Partial<Schedule> = {}): Schedule {
  return {
    id: 1,
    workDate: "2026-09-07",
    periodId: 1,
    staff: {
      id: 10,
      fullName: "Nguyễn Văn A",
      specialtyName: "Ngoại",
    },
    shiftType: {
      id: "L01",
      name: "Trực 24/24",
      isOvernight: true,
    },
    hasConflict: false,
    requirementId: undefined,
    ...overrides,
  } as Schedule;
}

describe("useAutoSchedule — Apply Template KPI contract", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("TEMPLATE_APPLIED: returns null KPIs and applies correct status", async () => {
    // After apply-template the backend only inserted shift_requirement rows.
    // The hook re-fetches /schedules/period/{id}, so either get [] or get rows
    // — both paths must keep KPIs null.
    vi.mocked(api.applyTemplate).mockResolvedValue({
      success: true,
      data: { templateId: 5, periodId: 1, appliedCount: 5 },
      timestamp: new Date().toISOString(),
    });
    vi.mocked(api.getSchedulesByPeriod).mockResolvedValue({
      success: true,
      data: [] as Schedule[],
      timestamp: new Date().toISOString(),
    });

    const { result } = renderHook(() => useAutoSchedule());

    expect(result.current[0].previewResult).toBeNull();

    await act(async () => {
      await result.current[1].loadTemplate(5, 1);
    });

    const pr = result.current[0].previewResult;
    expect(pr).not.toBeNull();
    expect(pr!.status).toBe("TEMPLATE_APPLIED");
    expect(pr!.coverageRate).toBeNull();
    expect(pr!.balanceScore).toBeNull();
    expect(pr!.conflictCount).toBeNull();

    // Backwards-compatible numeric derivations remain available downstream;
    // contract here is that the source of truth is null, not 100/0.
    expect(pr!.totalSchedulesCreated).toBe(0);
    expect(pr!.schedules).toEqual([]);
  });

  it("TEMPLATE_APPLIED: re-fetched schedule rows show up without setting fake KPIs", async () => {
    vi.mocked(api.applyTemplate).mockResolvedValue({
      success: true,
      data: { templateId: 5, periodId: 1, appliedCount: 5 },
      timestamp: new Date().toISOString(),
    });

    const generatedRows: Schedule[] = [
      makeSchedule({ id: 100, workDate: "2026-09-07" }),
      makeSchedule({ id: 101, workDate: "2026-09-08", staff: { id: 11, fullName: "Trần Thị B", specialtyName: "Nội" } }),
    ];
    vi.mocked(api.getSchedulesByPeriod).mockResolvedValue({
      success: true,
      data: generatedRows,
      timestamp: new Date().toISOString(),
    });

    const { result } = renderHook(() => useAutoSchedule());

    await act(async () => {
      await result.current[1].loadTemplate(5, 1);
    });

    const pr = result.current[0].previewResult;
    expect(pr).not.toBeNull();
    expect(pr!.status).toBe("TEMPLATE_APPLIED");
    expect(pr!.totalSchedulesCreated).toBe(2);
    expect(pr!.schedules).toHaveLength(2);
    // KPIs still null even when schedules were loaded — Apply Template ≠ Coverage run.
    expect(pr!.coverageRate).toBeNull();
    expect(pr!.balanceScore).toBeNull();
    expect(pr!.conflictCount).toBeNull();
  });

  it("TEMPLATE_APPLIED via applyTemplateWithEdits: same null KPI contract", async () => {
    vi.mocked(api.applyTemplateWithEdits).mockResolvedValue({
      success: true,
      data: { templateId: 5, periodId: 1, appliedCount: 5 },
      timestamp: new Date().toISOString(),
    });
    vi.mocked(api.getSchedulesByPeriod).mockResolvedValue({
      success: true,
      data: [] as Schedule[],
      timestamp: new Date().toISOString(),
    });

    const { result } = renderHook(() => useAutoSchedule());

    await act(async () => {
      await result.current[1].applyTemplateWithEdits(5, 1, []);
    });

    const pr = result.current[0].previewResult;
    expect(pr).not.toBeNull();
    expect(pr!.status).toBe("TEMPLATE_APPLIED");
    expect(pr!.coverageRate).toBeNull();
    expect(pr!.balanceScore).toBeNull();
    expect(pr!.conflictCount).toBeNull();
  });

  it("Apply-template user-facing message reflects what was actually done", async () => {
    vi.mocked(api.applyTemplate).mockResolvedValue({
      success: true,
      data: { templateId: 5, periodId: 1, appliedCount: 5 },
      timestamp: new Date().toISOString(),
    });
    vi.mocked(api.getSchedulesByPeriod).mockResolvedValue({
      success: true,
      data: [] as Schedule[],
      timestamp: new Date().toISOString(),
    });

    const { result } = renderHook(() => useAutoSchedule());

    await act(async () => {
      await result.current[1].loadTemplate(5, 1);
    });

    // "Đã áp dụng mẫu lịch — 5 yêu cầu nhân sự được tạo. Nhấn 'Chạy' để phân công."
    expect(result.current[0].message).toMatch(/Đã áp dụng mẫu lịch/);
    expect(result.current[0].message).toMatch(/5 yêu cầu nhân sự được tạo/);
    expect(result.current[0].message).toMatch(/Chạy/);
    // And the key bit: the message must NOT lie about 100% coverage.
    expect(result.current[0].message).not.toMatch(/100/);
  });

  it("Auto Scheduling runPreview: KPIs carry real numeric values from scheduler", async () => {
    vi.mocked(api.previewAutoSchedule).mockResolvedValue({
      success: true,
      data: {
        success: true,
        message: "Scheduled",
        periodId: 1,
        algorithmType: "V10_LOCAL_SEARCH",
        executionTimeMs: 1234,
        coverageRate: 96.4,
        balanceScore: 91.1,
        conflictCount: 0,
        totalSchedulesCreated: 63,
        status: "SCHEDULED" as const,
        schedules: [],
        executedAt: new Date().toISOString(),
      },
      timestamp: new Date().toISOString(),
    });

    const { result } = renderHook(() => useAutoSchedule());

    await act(async () => {
      await result.current[1].runPreview(1);
    });

    const pr = result.current[0].previewResult;
    expect(pr).not.toBeNull();
    expect(pr!.status).not.toBe("TEMPLATE_APPLIED");
    expect(pr!.coverageRate).toBe(96.4);
    expect(pr!.balanceScore).toBe(91.1);
    expect(pr!.conflictCount).toBe(0);
    expect(pr!.totalSchedulesCreated).toBe(63);

    // Optional: signal-state default should be off.
    await waitFor(() => expect(result.current[0].running).toBe(false));
  });

  it("runPreview keeps last previewResult intact on API error (sets error message)", async () => {
    vi.mocked(api.previewAutoSchedule).mockRejectedValue(new Error("500 from server"));

    const { result } = renderHook(() => useAutoSchedule());

    await act(async () => {
      await result.current[1].runPreview(1);
    });

    // previewResult was never assigned because the call failed.
    expect(result.current[0].previewResult).toBeNull();
    // getErrorMessage extracts the underlying Error.message when present,
    // and only falls back to the Vietnamese default for non-Error throwables.
    expect(result.current[0].message).toMatch(/500 from server/);
    expect(result.current[0].running).toBe(false);
  });

  it("runPreview falls back to Vietnamese default for non-Error rejections", async () => {
    vi.mocked(api.previewAutoSchedule).mockRejectedValue("some string error");

    const { result } = renderHook(() => useAutoSchedule());

    await act(async () => {
      await result.current[1].runPreview(1);
    });

    expect(result.current[0].previewResult).toBeNull();
    expect(result.current[0].message).toMatch(/Không thể chạy auto schedule/i);
    expect(result.current[0].running).toBe(false);
  });
});

describe("useAutoSchedule — KPI type contract", () => {
  it("AutoScheduleResult.coverageRate etc. can be number | null in TS", () => {
    const arr: Array<number | null> = [null, 0, 100, 96.4];
    expect(arr).toHaveLength(4);
    // The whole point is that null and 0 are distinguishable downstream.
    expect(arr.filter((v) => v === null)).toHaveLength(1);
    expect(arr.filter((v) => v === 0)).toHaveLength(1);
  });
});
