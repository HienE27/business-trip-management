import { ApiClient } from "../api-client";
import type { ApiResponse, ScheduleTemplate, TemplatePreviewItem } from "@/types/api";

export async function getAllTemplates(client: ApiClient): Promise<ApiResponse<ScheduleTemplate[]>> {
  return client.request<ScheduleTemplate[]>("/schedule-templates");
}

export async function getActiveTemplates(client: ApiClient): Promise<ApiResponse<ScheduleTemplate[]>> {
  return client.request<ScheduleTemplate[]>("/schedule-templates/active");
}

export async function getTemplateById(client: ApiClient, id: number): Promise<ApiResponse<ScheduleTemplate>> {
  return client.request<ScheduleTemplate>(`/schedule-templates/${id}`);
}

export async function createTemplate(client: ApiClient, data: Partial<ScheduleTemplate>): Promise<ApiResponse<ScheduleTemplate>> {
  return client.request<ScheduleTemplate>("/schedule-templates", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateTemplate(client: ApiClient, id: number, data: Partial<ScheduleTemplate>): Promise<ApiResponse<ScheduleTemplate>> {
  return client.request<ScheduleTemplate>(`/schedule-templates/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteTemplate(client: ApiClient, id: number): Promise<ApiResponse<void>> {
  return client.request<void>(`/schedule-templates/${id}`, { method: "DELETE" });
}

export async function previewTemplate(client: ApiClient, templateId: number, periodId: number): Promise<ApiResponse<TemplatePreviewItem[]>> {
  return client.request<TemplatePreviewItem[]>(`/schedule-templates/${templateId}/preview/${periodId}`);
}

export async function applyTemplate(client: ApiClient, templateId: number, periodId: number): Promise<ApiResponse<{ templateId: number; periodId: number; appliedCount: number }>> {
  return client.request<{ templateId: number; periodId: number; appliedCount: number }>(
    `/schedule-templates/${templateId}/apply/${periodId}`,
    { method: "POST" },
  );
}

export async function applyTemplateWithEdits(
  client: ApiClient,
  templateId: number,
  periodId: number,
  edits: { slotId: number; assignedStaffId: number }[],
): Promise<ApiResponse<{ templateId: number; periodId: number; appliedCount: number }>> {
  return client.request<{ templateId: number; periodId: number; appliedCount: number }>(
    `/schedule-templates/${templateId}/apply/${periodId}/with-edits`,
    { method: "POST", body: JSON.stringify({ edits }) },
  );
}
