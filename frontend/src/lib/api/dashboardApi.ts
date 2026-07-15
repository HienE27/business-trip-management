import { ApiClient } from "../api-client";
import type { ApiResponse, DashboardData, ShiftStatistics, StaffWorkloadStatistics, StaffShiftStatistics } from "@/types/api";

export async function getDashboard(client: ApiClient): Promise<ApiResponse<DashboardData>> {
  return client.request<DashboardData>("/dashboard");
}

export async function getShiftStatistics(client: ApiClient): Promise<ApiResponse<ShiftStatistics>> {
  return client.request<ShiftStatistics>("/dashboard/shifts");
}

export async function getStaffWorkload(client: ApiClient, periodId: number): Promise<ApiResponse<StaffWorkloadStatistics[]>> {
  return client.request<StaffWorkloadStatistics[]>(`/dashboard/workload/period/${periodId}`);
}

export async function getHeatmapData(client: ApiClient, periodId: number): Promise<ApiResponse<Record<string, unknown>>> {
  return client.request<Record<string, unknown>>(`/dashboard/heatmap/period/${periodId}`);
}

// Statistics (M02-F05, M04-F05, M05-F05)
export async function getStaffStatistics(client: ApiClient, periodId: number, shiftTypeId?: string): Promise<StaffShiftStatistics[]> {
  const params = new URLSearchParams({ periodId: String(periodId) });
  if (shiftTypeId) params.set("shiftTypeId", shiftTypeId);
  return client.get<StaffShiftStatistics[]>(`/statistics/staff?${params.toString()}`);
}
