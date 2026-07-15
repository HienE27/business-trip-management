import { ApiClient } from "../api-client";
import type { ApiResponse, ScheduleExchangeCreate, ScheduleExchangeResponse } from "@/types/api";
import type { Page } from "@/types/api";

export async function getAllExchanges(client: ApiClient): Promise<ApiResponse<ScheduleExchangeResponse[]>> {
  return client.request<ScheduleExchangeResponse[]>("/schedule-exchanges");
}

/** Server-paginated variant — newest first. */
export async function getExchangesPage(client: ApiClient, page: number, size: number): Promise<Page<ScheduleExchangeResponse>> {
  return client.getPage<ScheduleExchangeResponse>("/schedule-exchanges/page", { page, size });
}

/**
 * Aggregate counts grouped by ExchangeStatus (entire DB, no pagination).
 * Returns `{ total, PENDING, APPROVED, REJECTED, CANCELLED }` for dashboard cards.
 */
export async function getExchangeStatusCounts(client: ApiClient): Promise<ApiResponse<Record<string, number>>> {
  return client.request<Record<string, number>>("/schedule-exchanges/status-counts");
}

export async function getPendingExchanges(client: ApiClient): Promise<ApiResponse<ScheduleExchangeResponse[]>> {
  return client.request<ScheduleExchangeResponse[]>("/schedule-exchanges/pending");
}

export async function getExchangesByStatus(client: ApiClient, status: string): Promise<ApiResponse<ScheduleExchangeResponse[]>> {
  return client.request<ScheduleExchangeResponse[]>(`/schedule-exchanges/status/${status}`);
}

export async function getExchangesForUser(client: ApiClient, userId: number): Promise<ApiResponse<ScheduleExchangeResponse[]>> {
  return client.request<ScheduleExchangeResponse[]>(`/schedule-exchanges/user/${userId}`);
}

export async function getExchangeById(client: ApiClient, id: number): Promise<ApiResponse<ScheduleExchangeResponse>> {
  return client.request<ScheduleExchangeResponse>(`/schedule-exchanges/${id}`);
}

export async function createExchange(
  client: ApiClient,
  requesterId: number,
  data: Omit<ScheduleExchangeCreate, "targetStaffId"> & { periodId: number; reason?: string },
): Promise<ApiResponse<ScheduleExchangeResponse>> {
  return client.request<ScheduleExchangeResponse>(`/schedule-exchanges/requester/${requesterId}`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function approveExchange(client: ApiClient, id: number, reviewerId: number, reviewNote?: string): Promise<ApiResponse<ScheduleExchangeResponse>> {
  const params = new URLSearchParams({ reviewerId: String(reviewerId) });
  if (reviewNote) params.set("reviewNote", reviewNote);
  return client.request<ScheduleExchangeResponse>(`/schedule-exchanges/${id}/approve?${params.toString()}`, { method: "PUT" });
}

export async function rejectExchange(client: ApiClient, id: number, reviewerId: number, reviewNote?: string): Promise<ApiResponse<ScheduleExchangeResponse>> {
  const params = new URLSearchParams({ reviewerId: String(reviewerId) });
  if (reviewNote) params.set("reviewNote", reviewNote);
  return client.request<ScheduleExchangeResponse>(`/schedule-exchanges/${id}/reject?${params.toString()}`, { method: "PUT" });
}

export async function cancelExchange(client: ApiClient, id: number): Promise<ApiResponse<ScheduleExchangeResponse>> {
  return client.request<ScheduleExchangeResponse>(`/schedule-exchanges/${id}/cancel`, { method: "PUT" });
}
