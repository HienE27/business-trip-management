import { ApiClient } from "../api-client";
import type { ApiResponse, LeaveRequest, LeaveRequestCreate, LeaveRequestStatistics } from "@/types/api";
import type { Page } from "@/types/api";

export async function getAllLeaveRequests(client: ApiClient): Promise<ApiResponse<LeaveRequest[]>> {
  return client.request<LeaveRequest[]>("/leave-requests");
}

/**
 * Server-paginated variant — drives the shared &lt;Pagination&gt; widget.
 * @param page 0-indexed page number (Spring convention)
 * @param size items per page
 */
export async function getLeaveRequestsPage(client: ApiClient, page: number, size: number): Promise<Page<LeaveRequest>> {
  return client.getPage<LeaveRequest>("/leave-requests/page", { page, size });
}

export async function getLeaveRequestsByStatus(client: ApiClient, status: string): Promise<ApiResponse<LeaveRequest[]>> {
  return client.request<LeaveRequest[]>(`/leave-requests/status/${status}`);
}

export async function getLeaveRequestsByStaff(client: ApiClient, staffId: number): Promise<ApiResponse<LeaveRequest[]>> {
  return client.request<LeaveRequest[]>(`/leave-requests/staff/${staffId}`);
}

export async function getLeaveRequestById(client: ApiClient, id: number): Promise<ApiResponse<LeaveRequest>> {
  return client.request<LeaveRequest>(`/leave-requests/${id}`);
}

/**
 * Aggregate counts grouped by LeaveStatus (entire DB, no pagination).
 * Returns `{ total, PENDING, APPROVED, REJECTED, CANCELLED }` for dashboard summary cards.
 */
export async function getLeaveRequestStatusCounts(client: ApiClient): Promise<ApiResponse<Record<string, number>>> {
  return client.request<Record<string, number>>("/leave-requests/status-counts");
}

export async function getLeaveRequestStatistics(client: ApiClient): Promise<ApiResponse<LeaveRequestStatistics>> {
  return client.request<LeaveRequestStatistics>("/dashboard/leave-requests");
}

export async function getPendingLeaveRequests(client: ApiClient): Promise<ApiResponse<LeaveRequest[]>> {
  return client.request<LeaveRequest[]>("/leave-requests/pending");
}

export async function createLeaveRequest(client: ApiClient, staffId: number, data: LeaveRequestCreate): Promise<ApiResponse<LeaveRequest>> {
  return client.request<LeaveRequest>(`/leave-requests/staff/${staffId}`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function approveLeaveRequest(client: ApiClient, id: number, reviewerId: number, reviewNote?: string): Promise<ApiResponse<LeaveRequest>> {
  const params = new URLSearchParams({ reviewerId: String(reviewerId) });
  if (reviewNote) params.set("reviewNote", reviewNote);
  return client.request<LeaveRequest>(`/leave-requests/${id}/approve?${params.toString()}`, { method: "PUT" });
}

export async function rejectLeaveRequest(client: ApiClient, id: number, reviewerId: number, reviewNote?: string): Promise<ApiResponse<LeaveRequest>> {
  const params = new URLSearchParams({ reviewerId: String(reviewerId) });
  if (reviewNote) params.set("reviewNote", reviewNote);
  return client.request<LeaveRequest>(`/leave-requests/${id}/reject?${params.toString()}`, { method: "PUT" });
}

export async function cancelLeaveRequest(client: ApiClient, id: number): Promise<ApiResponse<LeaveRequest>> {
  return client.request<LeaveRequest>(`/leave-requests/${id}/cancel`, { method: "PUT" });
}
