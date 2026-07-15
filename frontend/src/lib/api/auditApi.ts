import { ApiClient } from "../api-client";
import type { AuditHistory, AuditHistoryPage, AuditHistorySummary } from "@/types/api";

export async function getAuditHistory(client: ApiClient, page = 0, size = 50): Promise<AuditHistoryPage> {
  // Backend returns ApiResponse<Page<AuditHistoryResponse>> with Spring Data Page structure:
  // { success: true, data: { content: [], totalElements, totalPages, number, size, first, last, empty }, timestamp }
  // The `request` method already returns the parsed ApiResponse wrapper, so res.data is the Page object.
  const res = await client.request<{ content: AuditHistory[]; totalElements: number; totalPages: number; number: number; size: number; first: boolean; last: boolean; empty: boolean }>(
    `/audit-history?page=${page}&size=${size}`,
  );
  return res.data as unknown as AuditHistoryPage;
}

/**
 * Fetch the global KPI summary (total / CREATE / UPDATE / DELETE counts).
 * Counts every row in audit_history so the cards reflect the DB,
 * not just the slice returned for the current page.
 */
export async function getAuditHistorySummary(client: ApiClient): Promise<AuditHistorySummary> {
  const res = await client.request<AuditHistorySummary>(`/audit-history/summary`);
  return res.data as unknown as AuditHistorySummary;
}

/**
 * Filtered KPI summary that mirrors every filter on the audit list page.
 * All params are optional — pass null/empty to skip a filter.
 */
export async function getAuditHistorySummaryFiltered(
  client: ApiClient,
  params: {
    startDate?: string;
    endDate?: string;
    module?: string;
    action?: string;
    search?: string;
  },
): Promise<AuditHistorySummary> {
  const search = new URLSearchParams();
  if (params.startDate) search.set("startDate", params.startDate);
  if (params.endDate)   search.set("endDate", params.endDate);
  if (params.module)    search.set("module", params.module);
  if (params.action)    search.set("action", params.action);
  if (params.search)    search.set("search", params.search);
  const qs = search.toString();
  const res = await client.request<AuditHistorySummary>(
    `/audit-history/summary/filter${qs ? `?${qs}` : ""}`,
  );
  return res.data as unknown as AuditHistorySummary;
}

export async function deleteAuditHistory(client: ApiClient, id: number): Promise<void> {
  await client.request<void>(`/audit-history/${id}`, { method: "DELETE" });
}

export async function deleteMultipleAuditHistory(client: ApiClient, ids: number[]): Promise<number> {
  const res = await client.request<{ data: number }>(`/audit-history`, {
    method: "DELETE",
    body: JSON.stringify(ids),
  });
  const response = res as unknown as { data: number };
  return response.data ?? ids.length;
}

export async function deleteAuditHistoryByDateRange(client: ApiClient, startDate: string, endDate: string): Promise<number> {
  const res = await client.request<{ data: number }>(
    `/audit-history/date-range?startDate=${startDate}&endDate=${endDate}`,
    { method: "DELETE" },
  );
  const response = res as unknown as { data: number };
  return response.data ?? 0;
}

/**
 * Wipe the entire audit_history table. Requires ADMIN role + typed
 * confirmation on the UI side. Returns the number of rows deleted.
 */
export async function deleteAllAuditHistory(client: ApiClient): Promise<number> {
  const res = await client.request<{ data: number }>(`/audit-history/all`, {
    method: "DELETE",
  });
  const response = res as unknown as { data: number };
  return response.data ?? 0;
}
