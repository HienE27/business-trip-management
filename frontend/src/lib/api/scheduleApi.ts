import { ApiClient } from "../api-client";
import type {
  ApiResponse,
  Schedule,
  ScheduleRequest,
  ConflictCheckResponse,
  BulkScheduleResponse,
  Staff,
} from "@/types/api";

/**
 * Filter set shared by every export endpoint.
 *
 *   shiftTypeId  — limits the report to a single schedule type (L01..L04).
 *   staffId      — narrows the report to one staff member.
 *   startDate    — ISO yyyy-MM-dd lower bound (inclusive).
 *   endDate      — ISO yyyy-MM-dd upper bound (inclusive).
 *
 * Backend accepts these as optional query params; if all are omitted,
 * the export covers the entire period for the whole department —
 * matching section M06-F04 of the requirements doc.
 */
export interface ScheduleExportFilters {
  shiftTypeId?: string;
  staffId?: number;
  startDate?: string;
  endDate?: string;
}

function buildScheduleExportQuery(filters: ScheduleExportFilters): string {
  const parts: string[] = [];
  if (filters.shiftTypeId) parts.push(`shiftTypeId=${encodeURIComponent(filters.shiftTypeId)}`);
  if (filters.staffId !== undefined && filters.staffId !== null) {
    parts.push(`staffId=${filters.staffId}`);
  }
  if (filters.startDate) parts.push(`startDate=${encodeURIComponent(filters.startDate)}`);
  if (filters.endDate) parts.push(`endDate=${encodeURIComponent(filters.endDate)}`);
  return parts.length === 0 ? "" : `?${parts.join("&")}`;
}

export async function getSchedulesByPeriod(client: ApiClient, periodId: number): Promise<ApiResponse<Schedule[]>> {
  return client.request<Schedule[]>(`/schedules/period/${periodId}`);
}

export async function getSchedulesByPeriodAndDate(
  client: ApiClient,
  periodId: number,
  date: string,
): Promise<ApiResponse<Schedule[]>> {
  return client.request<Schedule[]>(`/schedules/period/${periodId}/date/${date}`);
}

export async function getSchedulesByStaff(client: ApiClient, staffId: number): Promise<ApiResponse<Schedule[]>> {
  return client.request<Schedule[]>(`/schedules/staff/${staffId}`);
}

export async function getExpertClinicSchedules(client: ApiClient, periodId: number, specialtyId?: number): Promise<ApiResponse<Schedule[]>> {
  const params = new URLSearchParams({ periodId: String(periodId) });
  if (specialtyId) params.set("specialtyId", String(specialtyId));
  return client.request<Schedule[]>(`/schedules/expert-clinic?${params.toString()}`);
}

export async function getScheduleById(client: ApiClient, id: number): Promise<ApiResponse<Schedule>> {
  return client.request<Schedule>(`/schedules/${id}`);
}

export async function createSchedule(client: ApiClient, data: ScheduleRequest): Promise<ApiResponse<Schedule>> {
  return client.request<Schedule>("/schedules", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateSchedule(client: ApiClient, id: number, data: ScheduleRequest): Promise<ApiResponse<Schedule>> {
  return client.request<Schedule>(`/schedules/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteSchedule(client: ApiClient, id: number): Promise<ApiResponse<void>> {
  return client.request<void>(`/schedules/${id}`, { method: "DELETE" });
}

export async function overrideScheduleConflict(client: ApiClient, id: number, reason: string): Promise<ApiResponse<unknown>> {
  return client.request<unknown>(`/schedules/${id}/override`, {
    method: "PUT",
    body: JSON.stringify({ reason }),
  });
}

export async function checkConflicts(client: ApiClient, periodId: number): Promise<ApiResponse<ConflictCheckResponse>> {
  return client.request<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`);
}

export async function bulkCreateSchedules(
  client: ApiClient,
  request: { periodId: number; entries: Array<{ workDate: string; staffId: number; requirementId?: number }> },
  shiftTypeId: string,
): Promise<BulkScheduleResponse> {
  return client.post<BulkScheduleResponse>(
    `/schedules/bulk?shiftTypeId=${encodeURIComponent(shiftTypeId)}`,
    request,
  );
}

export async function findReplacements(
  client: ApiClient,
  periodId: number,
  workDate: string,
  shiftTypeId: string,
  originalStaffId: number,
  requiredCount = 1,
): Promise<Staff[]> {
  const params = new URLSearchParams({
    workDate,
    shiftTypeId,
    originalStaffId: String(originalStaffId),
    requiredCount: String(requiredCount),
  });
  return client.get<Staff[]>(`/schedules/replacements/${periodId}?${params.toString()}`);
}

export async function exportScheduleExcel(
  client: ApiClient,
  periodId: number,
  filters: ScheduleExportFilters = {},
): Promise<Blob> {
  const params = buildScheduleExportQuery(filters);
  const response = await client.fetchWithAuth(
    `/dashboard/export/schedule/${periodId}${params}`,
  );
  if (!response.ok) {
    const text = await response.text().catch(() => "Unknown error");
    throw new Error(`Export failed (${response.status}): ${text}`);
  }
  return response.blob();
}

export async function exportSchedulePdf(
  client: ApiClient,
  periodId: number,
  filters: ScheduleExportFilters = {},
): Promise<Blob> {
  const params = buildScheduleExportQuery(filters);
  const response = await client.fetchWithAuth(
    `/dashboard/export/schedule/${periodId}/pdf${params}`,
  );
  if (!response.ok) {
    const text = await response.text().catch(() => "Unknown error");
    throw new Error(`Export PDF failed (${response.status}): ${text}`);
  }
  return response.blob();
}

export async function exportWorkloadExcel(
  client: ApiClient,
  periodId: number,
  filters: ScheduleExportFilters = {},
): Promise<Blob> {
  const params = buildScheduleExportQuery(filters);
  const response = await client.fetchWithAuth(
    `/dashboard/export/workload/${periodId}${params}`,
  );
  if (!response.ok) {
    const text = await response.text().catch(() => "Unknown error");
    throw new Error(`Export workload failed (${response.status}): ${text}`);
  }
  return response.blob();
}
