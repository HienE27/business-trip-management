import { ApiClient } from "../api-client";
import type { ApiResponse, Specialty, ShiftType } from "@/types/api";

// Specialties
export async function getAllSpecialties(client: ApiClient): Promise<ApiResponse<Specialty[]>> {
  return client.request<Specialty[]>("/specialties");
}

export async function getActiveSpecialties(client: ApiClient): Promise<ApiResponse<Specialty[]>> {
  return client.request<Specialty[]>("/specialties/active");
}

export async function getSpecialtyById(client: ApiClient, id: number): Promise<ApiResponse<Specialty>> {
  return client.request<Specialty>(`/specialties/${id}`);
}

export async function createSpecialty(client: ApiClient, data: { name: string; description?: string }): Promise<ApiResponse<Specialty>> {
  return client.request<Specialty>("/specialties", { method: "POST", body: JSON.stringify(data) });
}

export async function updateSpecialty(client: ApiClient, id: number, data: { name: string; description?: string; isActive?: boolean }): Promise<ApiResponse<Specialty>> {
  return client.request<Specialty>(`/specialties/${id}`, { method: "PUT", body: JSON.stringify(data) });
}

export async function deleteSpecialty(client: ApiClient, id: number): Promise<ApiResponse<void>> {
  return client.request<void>(`/specialties/${id}`, { method: "DELETE" });
}

// Shift Types
export async function getAllShiftTypes(client: ApiClient): Promise<ApiResponse<ShiftType[]>> {
  return client.request<ShiftType[]>("/shift-types");
}

export async function getActiveShiftTypes(client: ApiClient): Promise<ApiResponse<ShiftType[]>> {
  return client.request<ShiftType[]>("/shift-types/active");
}

export async function getShiftTypeById(client: ApiClient, id: string): Promise<ApiResponse<ShiftType>> {
  return client.request<ShiftType>(`/shift-types/${id}`);
}

// Role Permissions
export async function getRolePermissionMatrix(client: ApiClient): Promise<ApiResponse<import("@/types/api").RolePermissionMatrix>> {
  return client.request<import("@/types/api").RolePermissionMatrix>("/roles/permissions/matrix");
}

export async function toggleRolePermission(client: ApiClient, data: { roleId: number; permissionId: number; granted: boolean }): Promise<ApiResponse<null>> {
  return client.request<null>("/roles/permissions/toggle", {
    method: "POST",
    body: JSON.stringify(data),
  });
}
