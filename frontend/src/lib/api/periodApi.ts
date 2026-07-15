import { ApiClient } from "../api-client";
import type { ApiResponse, SchedulePeriod, BulkPeriodResult, PublishDryRunResponse, PeriodSummary } from "@/types/api";
import type { Page } from "@/types/api";

export async function getAllPeriods(client: ApiClient): Promise<ApiResponse<SchedulePeriod[]>> {
  return client.request<SchedulePeriod[]>("/periods");
}

/** Server-paginated variant — newest startDate first. */
export async function getPeriodsPage(client: ApiClient, page: number, size: number): Promise<Page<SchedulePeriod>> {
  return client.getPage<SchedulePeriod>("/periods/page", { page, size });
}

export async function getPeriodsByStatus(client: ApiClient, status: string): Promise<ApiResponse<SchedulePeriod[]>> {
  return client.request<SchedulePeriod[]>(`/periods/status/${status}`);
}

export async function getPeriodById(client: ApiClient, id: number): Promise<ApiResponse<SchedulePeriod>> {
  return client.request<SchedulePeriod>(`/periods/${id}`);
}

export async function createPeriod(client: ApiClient, data: Partial<SchedulePeriod>): Promise<ApiResponse<SchedulePeriod>> {
  return client.request<SchedulePeriod>("/periods", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updatePeriod(client: ApiClient, id: number, data: Partial<SchedulePeriod>): Promise<ApiResponse<SchedulePeriod>> {
  return client.request<SchedulePeriod>(`/periods/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function publishPeriod(client: ApiClient, id: number): Promise<ApiResponse<SchedulePeriod>> {
  return client.request<SchedulePeriod>(`/periods/${id}/publish`, { method: "POST" });
}

export async function archivePeriod(client: ApiClient, id: number): Promise<ApiResponse<SchedulePeriod>> {
  return client.request<SchedulePeriod>(`/periods/${id}/archive`, { method: "POST" });
}

export async function bulkPublishPeriods(client: ApiClient, ids: number[]): Promise<ApiResponse<BulkPeriodResult>> {
  return client.request<BulkPeriodResult>("/periods/bulk/publish", {
    method: "POST",
    body: JSON.stringify({ periodIds: ids }),
  });
}

export async function bulkArchivePeriods(client: ApiClient, ids: number[]): Promise<ApiResponse<BulkPeriodResult>> {
  return client.request<BulkPeriodResult>("/periods/bulk/archive", {
    method: "POST",
    body: JSON.stringify({ periodIds: ids }),
  });
}

export async function deletePeriod(client: ApiClient, id: number): Promise<ApiResponse<void>> {
  return client.request<void>(`/periods/${id}`, { method: "DELETE" });
}

export async function getPeriodSummaries(client: ApiClient): Promise<ApiResponse<PeriodSummary[]>> {
  return client.request<PeriodSummary[]>("/dashboard/periods");
}

export async function dryRunPublish(client: ApiClient, periodId: number): Promise<PublishDryRunResponse> {
  return client.get<PublishDryRunResponse>(`/periods/${periodId}/publish/dry-run`);
}
