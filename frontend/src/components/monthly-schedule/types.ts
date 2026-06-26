import type { ConflictCheckResponse, ConflictDetail, PublishDryRunResponse, Schedule, SchedulePeriod, Staff } from "@/types/api";

export type ScheduleTab = "L01" | "L02" | "L03" | "L04" | "ALL";
export type ViewMode = "calendar" | "table" | "matrix";
export type MonthlyPanel = "overview" | "conflicts" | "summary" | "workload";
export type WorkflowStepId = "auto-schedule" | "conflicts" | "review" | "export" | "publish" | "notify";
export type WorkflowStatus = "pending" | "active" | "completed" | "error";
export type KpiTone = "success" | "warning" | "danger" | "info" | "neutral";

export type WorkflowDefinition = {
  id: WorkflowStepId;
  title: string;
  description: string;
};

export type WorkflowStepView = WorkflowDefinition & {
  status: WorkflowStatus;
  statusLabel: string;
};

export type OperationalKpi = {
  label: string;
  value: string | number;
  helper: string;
  tone: KpiTone;
  trend?: string;
  icon: string;
};

export type WorkloadRow = {
  staffId: number;
  staffName: string;
  shifts: number;
};

export type CalendarAnnotation = {
  date: string;
  label: string;
  tone?: "compLeave" | "warning" | "neutral" | "holiday";
  description?: string;
};

export type MonthlyDerivedData = {
  filteredSchedules: Schedule[];
  conflictList: ConflictDetail[];
  calendarAnnotations: CalendarAnnotation[];
  computedCoverages: Record<string, { required: number; assigned: number }>;
  kpis: OperationalKpi[];
  workloadSnapshot: WorkloadRow[];
  focusSchedules: Schedule[];
  conflictKeys: Set<string>;
};

export type MonthlyScheduleQueryState = {
  selectedTab: ScheduleTab;
  selectedPanel: MonthlyPanel;
  viewMode: ViewMode;
  parsedScheduleId: number | null;
  parsedStaffId: number | null;
  parsedSpecialtyId: number | null;
  periodId: number | null;
};

export type ScheduleHeaderPeriod = Pick<SchedulePeriod, "id" | "periodName" | "startDate" | "endDate" | "status">;

export type WorkflowContext = {
  selectedPanel: MonthlyPanel;
  selectedPeriod: SchedulePeriod | null;
  schedules: Schedule[];
  conflictData: ConflictCheckResponse | null;
  dryRunData?: PublishDryRunResponse | null;
  checkingConflicts: boolean;
  publishing: boolean;
  exporting: boolean;
  notifying: boolean;
  notified: boolean;
};

export type StaffOption = Pick<Staff, "id" | "fullName">;
