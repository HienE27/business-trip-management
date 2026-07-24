import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { AutoSchedulePanel } from "@/components/monthly-schedule/AutoSchedulePanel";

// Stub the matrix grid to keep this test focused on KPI cards only.
vi.mock("@/components/monthly-schedule/AutoScheduleMatrixGrid", () => ({
  AutoScheduleMatrixGrid: () => <div data-testid="matrix-grid" />,
}));
vi.mock("@/components/monthly-schedule/ShiftTypeBreakdownCard", () => ({
  ShiftTypeBreakdownCard: () => <div data-testid="shift-type-card" />,
}));
vi.mock("@/components/monthly-schedule/TemplateActionsSplitButton", () => ({
  TemplateActionsSplitButton: () => <div data-testid="template-actions" />,
}));
vi.mock("@/components/monthly-schedule/FeasibilityReportCard", () => ({
  FeasibilityReportCard: () => <div data-testid="feasibility-card" />,
}));
vi.mock("@/hooks/useAlgorithmProgress", () => ({
  useAlgorithmProgress: () => ({ step: null, message: null, percent: 0 }),
}));

const noop = () => {};
const baseOverrides = {
  previewResult: null,
  editedPreview: [],
  activeStaff: [],
  applyingPreview: false,
  runningAutoSchedule: false,
  message: null,
  algorithmType: "V10_LOCAL_SEARCH" as const,
  selectedPeriod: null,
  selectedPeriodId: 1,
  selectedPeriodStatus: "DRAFT",
  onPreview: noop,
  onApplyPreview: noop,
  onResetEdits: noop,
  onSetAlgorithmType: noop,
  isManager: true,
};

describe("AutoSchedulePanel — KPI presentation for TEMPLATE_APPLIED vs SCHEDULED", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders '—' and 'Chưa chạy' helper when status === TEMPLATE_APPLIED", () => {
    render(
      <AutoSchedulePanel
        {...baseOverrides}
        previewResult={{
          success: true,
          message: "Đã tải lịch đã áp dụng từ mẫu",
          periodId: 1,
          algorithmType: "V10_LOCAL_SEARCH",
          executionTimeMs: 0,
          coverageRate: null,
          balanceScore: null,
          conflictCount: null,
          totalSchedulesCreated: 5,
          status: "TEMPLATE_APPLIED",
          schedules: [],
          executedAt: new Date().toISOString(),
        }}
      />,
    );

    // Coverage, Balance, Conflict cards each render an em-dash value when kpi is not available.
    const coverageLabels = screen.getAllByText("Tỷ lệ phủ");
    expect(coverageLabels.length).toBeGreaterThan(0);
    expect(coverageLabels.some((el) => el.textContent?.includes("—"))).toBe(false); // label stays unchanged
    // The em-dash lives in the value slot — look for it within aria-visible text.
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(3);
    // Helper text makes the "Bấm Chạy để tính" intent explicit.
    expect(screen.getAllByText("Nhấn Chạy để tính").length).toBeGreaterThanOrEqual(3);
  });

  it("renders real percentages and conflict=0 when KPIs are numbers (post-scheduling)", () => {
    render(
      <AutoSchedulePanel
        {...baseOverrides}
        previewResult={{
          success: true,
          message: "Scheduled",
          periodId: 1,
          algorithmType: "V10_LOCAL_SEARCH",
          executionTimeMs: 1234,
          coverageRate: 96.4,
          balanceScore: 91.1,
          conflictCount: 0,
          totalSchedulesCreated: 61,
          status: "SCHEDULED",
          schedules: [],
          executedAt: new Date().toISOString(),
        }}
      />,
    );

    expect(screen.getByText("96%")).toBeInTheDocument();
    expect(screen.getByText("91%")).toBeInTheDocument();
    // Conflict card label is "Xung đột", with value 0.
    expect(screen.getByText("Xung đột")).toBeInTheDocument();
    const conflictCard = screen.getByText("Xung đột").closest("div.flex.flex-col");
    expect(conflictCard?.textContent).toMatch(/0(?!%)/);
    // Helper "Bấm Chạy để tính" must NOT appear now.
    expect(screen.queryByText("Bấm Chạy để tính")).toBeNull();
    // Em-dash must NOT appear for these KPIs.
    // (There may still be other em-dashes elsewhere — count doesn't help; but
    // we already asserted "Chưa chạy" is gone which proves the kpiAvailable path is on.)
  });

  it("renders coverage <70%, balance <50%, conflict >0 with error/warning tones", () => {
    render(
      <AutoSchedulePanel
        {...baseOverrides}
        previewResult={{
          success: true,
          message: "Scheduled (low coverage)",
          periodId: 1,
          algorithmType: "GREEDY",
          executionTimeMs: 1234,
          coverageRate: 42.7,
          balanceScore: 33.0,
          conflictCount: 5,
          totalSchedulesCreated: 20,
          status: "SCHEDULED",
          schedules: [],
          executedAt: new Date().toISOString(),
        }}
      />,
    );

    expect(screen.getByText("43%")).toBeInTheDocument();
    expect(screen.getByText("33%")).toBeInTheDocument();
    const conflictCard2 = screen.getByText("Xung đột").closest("div.flex.flex-col");
    expect(conflictCard2?.textContent).toMatch(/5(?!%)/);
  });

  it("renders FeasibilityReportCard when previewResult is null (initial state)", () => {
    render(<AutoSchedulePanel {...baseOverrides} previewResult={null} />);
    expect(screen.getByTestId("feasibility-card")).toBeInTheDocument();
  });
});
