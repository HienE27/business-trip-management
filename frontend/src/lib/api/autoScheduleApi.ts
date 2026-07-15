import { ApiClient } from "../api-client";
import type {
  ApiResponse,
  AutoScheduleRequest,
  AutoScheduleResult,
  AlgorithmMetrics,
  ReplacementSuggestion,
  ScheduleTemplate,
} from "@/types/api";
import type { Page } from "@/types/api";

export async function previewAutoSchedule(
  client: ApiClient,
  data: AutoScheduleRequest,
  options?: { timeout?: number; cancelSignal?: AbortSignal },
): Promise<ApiResponse<AutoScheduleResult>> {
  return client.request<AutoScheduleResult>("/auto-schedule/preview", {
    method: "POST",
    body: JSON.stringify(data),
    timeout: options?.timeout ?? 60000, // Default 60s, configurable for long-running algorithms
    cancelSignal: options?.cancelSignal,
  });
}

export async function applyPreview(
  client: ApiClient,
  data: {
    periodId: number;
    algorithmType: string;
    // BUGFIX (was M07 #8): requirementId is forwarded by the wizard when the
    // auto-schedule preview carries one; the backend uses it to resolve
    // multi-specialty L04 slots deterministically.
    schedules: Array<{
      workDate: string;
      shiftTypeId: string;
      staffId: number;
      requirementId?: number | null;
    }>;
    removedSchedules?: Array<{
      workDate: string;
      shiftTypeId: string;
      staffId: number;
    }>;
  },
): Promise<ApiResponse<void>> {
  return client.request<void>("/auto-schedule/apply-preview", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function saveScheduleTemplate(
  client: ApiClient,
  data: {
    periodId: number;
    templateName: string;
    description: string;
    algorithmType: string;
    scheduleIds: number[];
  },
): Promise<ApiResponse<ScheduleTemplate>> {
  return client.request<ScheduleTemplate>("/auto-schedule/save-template", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function getWorkloadChartData(
  client: ApiClient,
  periodId: number,
  shiftTypeId?: string,
): Promise<{
  staffWorkloadData: Array<{
    staffId: number;
    staffName: string;
    specialty: string | null;
    totalShifts: number;
    L01: number;
    L02: number;
    L03: number;
    L04: number;
    workloadPercentage: number;
  }>;
  totalSchedules: number;
  totalStaff: number;
  averageWorkload: number;
  minWorkload: number;
  maxWorkload: number;
  shiftTypeId?: string;
}> {
  const params = new URLSearchParams();
  if (shiftTypeId) params.set("shiftTypeId", shiftTypeId);
  const qs = params.toString();
  return client.get(`/auto-schedule/workload-chart/${periodId}${qs ? `?${qs}` : ""}`);
}

export async function getUnassignedDaysReport(client: ApiClient, periodId: number): Promise<{
  totalUnassignedDays: number;
  unassignedDays: Array<{
    workDate: string;
    dayOfWeek: string;
    shiftTypeId: string;
    shiftTypeName: string;
    requiredStaffCount: number;
    assignedStaffCount: number;
    missingCount: number;
  }>;
}> {
  return client.get(`/auto-schedule/unassigned/${periodId}`);
}

export async function getMetricsByPeriod(client: ApiClient, periodId: number): Promise<AlgorithmMetrics[]> {
  return client.get<AlgorithmMetrics[]>(`/auto-schedule/metrics/period/${periodId}`);
}

/** Server-paginated variant of {@link getAllMetrics}. */
export async function getMetricsPage(
  client: ApiClient,
  page: number,
  size: number,
  periodId?: number,
): Promise<Page<AlgorithmMetrics>> {
  return client.getPage<AlgorithmMetrics>(
    "/auto-schedule/metrics/page",
    periodId ? { page, size, periodId } : { page, size },
  );
}

export async function suggestReplacements(client: ApiClient, scheduleId: number): Promise<ReplacementSuggestion> {
  return client.get<ReplacementSuggestion>(`/auto-schedule/suggest-replacements/${scheduleId}`);
}

export async function getAllMetrics(client: ApiClient): Promise<ApiResponse<AlgorithmMetrics[]>> {
  return client.request<AlgorithmMetrics[]>("/auto-schedule/metrics");
}

export async function getAlgorithmProgress(client: ApiClient, periodId: number): Promise<{
  status: "IDLE" | "RUNNING" | "COMPLETED" | "FAILED";
  periodId: number;
  step?: string;
  percent?: number;
  message?: string;
  startedAt?: string;
  updatedAt?: string;
  resultJson?: string;
}> {
  return client.get(`/auto-schedule/progress/${periodId}`);
}

// Runtime Config (all algorithm parameters in one call)
export async function getRuntimeConfig(client: ApiClient): Promise<ApiResponse<{
  weekendWeight: number;
  overnightRecoveryHours: number;
  greedyCoverageThreshold: number;
  balanceScoreMin: number;
  autoCompensationEnabled: boolean;
}>> {
  return client.request<{
    weekendWeight: number;
    overnightRecoveryHours: number;
    greedyCoverageThreshold: number;
    balanceScoreMin: number;
    autoCompensationEnabled: boolean;
  }>("/auto-schedule/runtime-config");
}

export async function updateRuntimeConfig(
  client: ApiClient,
  data: {
    weekendWeight: number;
    overnightRecoveryHours: number;
    greedyCoverageThreshold: number;
    balanceScoreMin: number;
    autoCompensationEnabled: boolean;
  },
): Promise<ApiResponse<{
  weekendWeight: number;
  overnightRecoveryHours: number;
  greedyCoverageThreshold: number;
  balanceScoreMin: number;
  autoCompensationEnabled: boolean;
}>> {
  return client.request<{
    weekendWeight: number;
    overnightRecoveryHours: number;
    greedyCoverageThreshold: number;
    balanceScoreMin: number;
    autoCompensationEnabled: boolean;
  }>("/auto-schedule/runtime-config", {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function getAutoGenConfig(client: ApiClient): Promise<ApiResponse<{
  enabled: boolean;
  l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
  l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
  l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
  l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
  holidayMode: string;
  removedShiftTypes: string[];
  l01AllowedSpecialties?: string[] | null;
  l02AllowedSpecialties?: string[] | null;
  l03AllowedSpecialties?: string[] | null;
  l04AllowedSpecialties?: string[] | null;
}>> {
  return client.request<{
    enabled: boolean;
    l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
    l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
    l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
    l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
    holidayMode: string;
    removedShiftTypes: string[];
    l01AllowedSpecialties?: string[] | null;
    l02AllowedSpecialties?: string[] | null;
    l03AllowedSpecialties?: string[] | null;
    l04AllowedSpecialties?: string[] | null;
  }>("/auto-schedule/auto-gen-config");
}

export async function updateAutoGenConfig(
  client: ApiClient,
  data: {
    enabled: boolean;
    l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
    l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
    l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
    l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
    holidayMode: string;
    removedShiftTypes: string[];
    l04CrossSpecialty?: boolean;
    l04CrossSpecialtyRatio?: number;
    l04BalanceStrategy?: "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR";
  },
): Promise<ApiResponse<{
  enabled: boolean;
  l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
  l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
  l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
  l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
  holidayMode: string;
  removedShiftTypes: string[];
  l04CrossSpecialty?: boolean;
  l04CrossSpecialtyRatio?: number;
  l04BalanceStrategy?: "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR";
}>> {
  return client.request<{
    enabled: boolean;
    l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
    l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
    l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
    l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
    holidayMode: string;
    removedShiftTypes: string[];
    l04CrossSpecialty?: boolean;
    l04CrossSpecialtyRatio?: number;
    l04BalanceStrategy?: "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR";
  }>("/auto-schedule/auto-gen-config", {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function recommendAutoGenConfig(
  client: ApiClient,
  data: {
    periodDays: number;
    periodWeeks: number;
    totalStaff: number;
    eligibleStaff: Record<string, number>;
    targetPerStaffPerMonth: Record<string, number>;
    expandNonL04Eligibility?: boolean;
    expandedSpecialties?: string[];
  },
): Promise<ApiResponse<{
  recommendedConfig: {
    enabled: boolean;
    l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
    l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
    l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
    l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
    holidayMode: string;
    removedShiftTypes: string[];
    l04CrossSpecialty: boolean;
    l04CrossSpecialtyRatio: number;
    l04AllowedSpecialties: string[];
    l01AllowedSpecialties: string[];
    l02AllowedSpecialties: string[];
    l03AllowedSpecialties: string[];
  };
  totalShiftsExpected: number;
  rationale: string;
}>> {
  return client.request<{
    recommendedConfig: {
      enabled: boolean;
      l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
      l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
      l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
      l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
      holidayMode: string;
      removedShiftTypes: string[];
      l04CrossSpecialty: boolean;
      l04CrossSpecialtyRatio: number;
      l04AllowedSpecialties: string[];
      l01AllowedSpecialties: string[];
      l02AllowedSpecialties: string[];
      l03AllowedSpecialties: string[];
    };
    totalShiftsExpected: number;
    rationale: string;
  }>("/auto-schedule/auto-gen-config/recommend", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

// AlgorithmConfig
export async function getAllAlgorithmConfigs(client: ApiClient): Promise<ApiResponse<Array<{
  paramKey: string;
  paramValue: string;
  valueType: string;
  description: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}>>> {
  return client.request<Array<{
    paramKey: string;
    paramValue: string;
    valueType: string;
    description: string;
    updatedBy: string;
    createdAt: string;
    updatedAt: string;
  }>>("/auto-schedule/config");
}

export async function createAlgorithmConfig(
  client: ApiClient,
  data: { paramKey: string; paramValue: string; valueType: string; description?: string },
): Promise<ApiResponse<{
  paramKey: string;
  paramValue: string;
  valueType: string;
  description: string;
}>> {
  return client.request<{
    paramKey: string;
    paramValue: string;
    valueType: string;
    description: string;
  }>("/auto-schedule/config", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateAlgorithmConfig(
  client: ApiClient,
  paramKey: string,
  data: { paramValue: string; description?: string },
): Promise<ApiResponse<{
  paramKey: string;
  paramValue: string;
  valueType: string;
  description: string;
}>> {
  return client.request<{
    paramKey: string;
    paramValue: string;
    valueType: string;
    description: string;
  }>(`/auto-schedule/config/${encodeURIComponent(paramKey)}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteAlgorithmConfig(client: ApiClient, paramKey: string): Promise<ApiResponse<void>> {
  return client.request<void>(`/auto-schedule/config/${encodeURIComponent(paramKey)}`, {
    method: "DELETE",
  });
}

export async function getAlgorithmConfigAudit(
  client: ApiClient,
  paramKey?: string,
  page = 0,
  size = 50,
): Promise<{
  content: Array<{
    id: number;
    paramKey: string;
    oldValue: string | null;
    newValue: string;
    action: string;
    changedByUsername: string | null;
    createdAt: string;
  }>;
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}> {
  const params = new URLSearchParams();
  if (paramKey) params.set("paramKey", paramKey);
  params.set("page", String(page));
  params.set("size", String(size));
  return client.get(`/auto-schedule/config/audit?${params.toString()}`);
}

export async function syncAlgorithmConfigDescriptions(client: ApiClient): Promise<ApiResponse<Record<string, string>>> {
  return client.request<Record<string, string>>("/auto-schedule/config/sync-descriptions", {
    method: "POST",
  });
}
