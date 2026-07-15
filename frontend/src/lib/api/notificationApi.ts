import { ApiClient } from "../api-client";
import type { ApiResponse, Notification } from "@/types/api";
import type { Page } from "@/types/api";

export async function getNotificationsByStaff(client: ApiClient, staffId: number): Promise<ApiResponse<Notification[]>> {
  return client.request<Notification[]>(`/notifications/staff/${staffId}`);
}

/** Server-paginated variant for the current caller. */
export async function getNotificationsPage(client: ApiClient, page: number, size: number): Promise<Page<Notification>> {
  return client.getPage<Notification>("/notifications/me/page", { page, size });
}

export async function getUnreadNotifications(client: ApiClient, staffId: number): Promise<ApiResponse<Notification[]>> {
  return client.request<Notification[]>(`/notifications/staff/${staffId}/unread`);
}

export async function countUnreadNotifications(client: ApiClient, staffId: number): Promise<ApiResponse<{ count: number }>> {
  return client.request<{ count: number }>(`/notifications/staff/${staffId}/unread/count`);
}

/**
 * Unread count for the currently authenticated staff. Resolves the staff id
 * server-side from the security context, so the frontend doesn't need to
 * know its own staff id. This avoids the page-slice counting bug where the
 * visible "chưa đọc" count was computed from the current paginated slice.
 */
export async function countMyUnreadNotifications(client: ApiClient): Promise<ApiResponse<{ count: number }>> {
  return client.request<{ count: number }>("/notifications/me/unread/count");
}

export async function markNotificationAsRead(client: ApiClient, id: number): Promise<ApiResponse<Notification>> {
  return client.request<Notification>(`/notifications/${id}/read`, { method: "PUT" });
}

export async function markAllNotificationsAsRead(client: ApiClient, staffId: number): Promise<ApiResponse<{ status: string }>> {
  return client.request<{ status: string }>(`/notifications/staff/${staffId}/read-all`, { method: "PUT" });
}

export async function deleteNotification(client: ApiClient, id: number): Promise<ApiResponse<void>> {
  return client.request<void>(`/notifications/${id}`, { method: "DELETE" });
}

export async function createNotification(client: ApiClient, staffId: number, data: { title: string; message: string }): Promise<ApiResponse<Notification>> {
  return client.request<Notification>(`/notifications/staff/${staffId}`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function broadcastNotification(client: ApiClient, data: { title: string; message: string }): Promise<ApiResponse<{ status: string }>> {
  return client.request<{ status: string }>("/notifications/broadcast", {
    method: "POST",
    body: JSON.stringify(data),
  });
}
