import { ApiClient } from "../api-client";
import type { ApiResponse, Staff, StaffSearchParams } from "@/types/api";
import type { Page } from "@/types/api";

export async function getAllStaff(client: ApiClient): Promise<ApiResponse<Staff[]>> {
  return client.request<Staff[]>("/staff");
}

export async function getActiveStaff(client: ApiClient): Promise<ApiResponse<Staff[]>> {
  return client.request<Staff[]>("/staff/active");
}

/**
 * Aggregate counts grouped by StaffStatus (entire DB, no pagination).
 * Returns `{ total, ACTIVE, ON_LEAVE, INACTIVE }` for dashboard summary cards.
 */
export async function getStaffStatusCounts(client: ApiClient): Promise<ApiResponse<Record<string, number>>> {
  return client.request<Record<string, number>>("/staff/status-counts");
}

export async function searchStaff(client: ApiClient, params: StaffSearchParams): Promise<ApiResponse<Staff[]>> {
  const query = new URLSearchParams();
  if (params.keyword) query.set("keyword", params.keyword);
  if (params.specialtyId) query.set("specialtyId", String(params.specialtyId));
  if (params.status) query.set("status", params.status);
  return client.request<Staff[]>(`/staff/search?${query.toString()}`);
}

/**
 * Server-paginated staff search — drives &lt;Pagination&gt; in StaffCrudPanel.
 * Mirrors {@link searchStaff} on the same filters but on `/staff/search/paginated`.
 */
export async function searchStaffsPage(
  client: ApiClient,
  params: StaffSearchParams & { page: number; size: number },
): Promise<Page<Staff>> {
  return client.getPage<Staff>("/staff/search-page", { ...params });
}

export async function getStaffById(client: ApiClient, id: number): Promise<ApiResponse<Staff>> {
  return client.request<Staff>(`/staff/${id}`);
}

export async function getCurrentStaff(client: ApiClient): Promise<ApiResponse<Staff>> {
  return client.request<Staff>("/staff/me");
}

export async function createStaff(client: ApiClient, data: Partial<Staff> & { roles?: string[] }): Promise<ApiResponse<Staff>> {
  return client.request<Staff>("/staff", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateStaff(client: ApiClient, id: number, data: Partial<Staff>): Promise<ApiResponse<Staff>> {
  return client.request<Staff>(`/staff/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteStaff(client: ApiClient, id: number): Promise<ApiResponse<void>> {
  return client.request<void>(`/staff/${id}`, { method: "DELETE" });
}

export async function importStaff(client: ApiClient, file: File): Promise<{ imported: number; errors: string[] }> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await client.fetchWithAuth(`/staff/import`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const text = await response.text().catch(() => "Unknown error");
    throw new Error(`Import failed (${response.status}): ${text}`);
  }

  return response.json();
}
