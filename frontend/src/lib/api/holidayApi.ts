import { ApiClient } from "../api-client";
import type { ApiResponse, Holiday } from "@/types/api";
import type { Page } from "@/types/api";

export async function getAllHolidays(client: ApiClient): Promise<ApiResponse<Holiday[]>> {
  return client.request<Holiday[]>("/holidays");
}

/** Server-paginated variant — newest holidayDate first. */
export async function getHolidaysPage(client: ApiClient, page: number, size: number): Promise<Page<Holiday>> {
  return client.getPage<Holiday>("/holidays/page", { page, size });
}

export async function getHolidaysByYear(client: ApiClient, year: number): Promise<ApiResponse<Holiday[]>> {
  return client.request<Holiday[]>(`/holidays/year/${year}`);
}

export async function getHolidayById(client: ApiClient, id: number): Promise<ApiResponse<Holiday>> {
  return client.request<Holiday>(`/holidays/${id}`);
}

export async function getActiveHolidays(client: ApiClient): Promise<ApiResponse<Holiday[]>> {
  return client.request<Holiday[]>("/holidays/active");
}

export async function createHoliday(client: ApiClient, data: { name: string; holidayDate: string; isNationalHoliday?: boolean; description?: string }): Promise<ApiResponse<Holiday>> {
  return client.request<Holiday>("/holidays", { method: "POST", body: JSON.stringify(data) });
}

export async function updateHoliday(client: ApiClient, id: number, data: { name: string; holidayDate: string; isNationalHoliday?: boolean; description?: string }): Promise<ApiResponse<Holiday>> {
  return client.request<Holiday>(`/holidays/${id}`, { method: "PUT", body: JSON.stringify(data) });
}

export async function deleteHoliday(client: ApiClient, id: number): Promise<ApiResponse<void>> {
  return client.request<void>(`/holidays/${id}`, { method: "DELETE" });
}
