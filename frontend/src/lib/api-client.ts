import type {
  ApiResponse,
  AuthResponse,
  LoginRequest,
  Staff,
  StaffSearchParams,
  Schedule,
  ScheduleRequest,
  SchedulePeriod,
  BulkPeriodResult,
  DashboardData,
  ShiftStatistics,
  StaffWorkloadStatistics,
  PeriodSummary,
  LeaveRequest,
  LeaveRequestCreate,
  ScheduleExchangeCreate,
  ScheduleExchangeResponse,
  AutoScheduleRequest,
  AutoScheduleResult,
  AlgorithmMetrics,
  Specialty,
  Notification,
  Holiday,
  ScheduleTemplate,
  TemplatePreviewItem,
  AuditHistory,
  AuditHistoryPage,
  AuditHistorySummary,
  ConflictCheckResponse,
  ShiftType,
  LeaveRequestStatistics,
  ReplacementSuggestion,
  BulkScheduleResponse,
  PublishDryRunResponse,
  StaffShiftStatistics,
  Page,
} from "@/types/api";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const LOGIN_PATH = "/login";
const TOKEN_STORAGE_KEY = "medschedule.token";

function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_STORAGE_KEY);
}

/**
 * Filter set shared by every export endpoint.
 *
 *   shiftTypeId  — limits the report to a single schedule type (L01..L04).
 *   staffId      — narrows the report to one staff member.
 *   startDate    — ISO yyyy-MM-dd lower bound (inclusive).
 *   endDate      — ISO yyyy-MM-dd upper bound (inclusive).
 *
 * Backend accepts these as optional query params; if all are omitted,
 * the export covers the entire period for the whole department —
 * matching §M06-F04 of the requirements doc.
 */
export interface ScheduleExportFilters {
  shiftTypeId?: string;
  staffId?: number;
  startDate?: string;
  endDate?: string;
}

function buildScheduleExportQuery(filters: ScheduleExportFilters): string {
  const parts: string[] = [];
  if (filters.shiftTypeId) parts.push(`shiftTypeId=${encodeURIComponent(filters.shiftTypeId)}`);
  if (filters.staffId !== undefined && filters.staffId !== null) {
    parts.push(`staffId=${filters.staffId}`);
  }
  if (filters.startDate) parts.push(`startDate=${encodeURIComponent(filters.startDate)}`);
  if (filters.endDate) parts.push(`endDate=${encodeURIComponent(filters.endDate)}`);
  return parts.length === 0 ? "" : `?${parts.join("&")}`;
}

class ApiClient {
  private async request<T>(
    endpoint: string,
    options: RequestInit & { timeout?: number } = {}
  ): Promise<ApiResponse<T>> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      ...(options.headers as Record<string, string>),
    };

    const token = getStoredToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    // Support AbortController timeout if specified
    const timeout = options.timeout ?? 60000;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    try {
      const response = await fetch(`${API_BASE}${endpoint}`, {
        ...options,
        headers,
        credentials: "include",
        signal: controller.signal,
      });
      clearTimeout(timeoutId);

      if (!response.ok) {
        if (response.status === 401 && typeof window !== "undefined") {
          window.localStorage.removeItem("medschedule.user");
          const currentPath = window.location.pathname;
          if (currentPath !== LOGIN_PATH) {
            window.location.replace(LOGIN_PATH);
          } else {
            throw new Error(`HTTP 401 — Phiên đăng nhập hết hạn`);
          }
        }
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || `HTTP ${response.status}`);
      }

      return response.json().catch(() => ({ success: true, data: null, message: "Thành công" }));
    } catch (error) {
      clearTimeout(timeoutId);
      if (error instanceof Error && error.name === "AbortError") {
        throw new Error(`Yêu cầu hết thời gian chờ (${Math.round(timeout / 1000)}s). Thuật toán có thể đang chạy quá lâu.`);
      }
      throw error;
    }
  }

  // Generic HTTP methods
  async get<T>(endpoint: string, params?: Record<string, string | number | boolean>, requestInit?: Omit<RequestInit, "method" | "body">): Promise<T> {
    let url = endpoint;
    if (params) {
      const qs = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        if (v === undefined || v === null || v === "") continue;
        qs.set(k, String(v));
      }
      url += (url.includes("?") ? "&" : "?") + qs.toString();
    }
    const res = await this.request<T>(url, { method: "GET", ...requestInit });
    // Handle both ApiResponse wrapper and direct array/object responses
    if (res.data !== undefined && res.data !== null) {
      // Check if it's a Page object (Spring Data Page structure with content array)
      if (Array.isArray(res.data)) {
        return res.data;
      }
      // Handle Page object - extract content array
      if (typeof res.data === 'object' && res.data !== null && 'content' in res.data) {
        return (res.data as { content: T[] }).content as T;
      }
      return res.data;
    }
    // If backend returns array directly (without ApiResponse wrapper)
    if (Array.isArray(res) || typeof res === 'object') {
      return res as T;
    }
    return res as T;
  }

  async post<T>(endpoint: string, body: unknown): Promise<T> {
    const res = await this.request<T>(endpoint, {
      method: "POST",
      body: JSON.stringify(body),
    });
    return res.data;
  }

  async put<T>(endpoint: string, body: unknown, params?: Record<string, string | number | boolean>): Promise<T> {
    let url = endpoint;
    if (params) {
      const qs = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        if (v === undefined || v === null || v === "") continue;
        qs.set(k, String(v));
      }
      url += `?${qs.toString()}`;
    }
    const res = await this.request<T>(url, {
      method: "PUT",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return res.data;
  }

  async delete<T>(endpoint: string): Promise<T> {
    const res = await this.request<T>(endpoint, { method: "DELETE" });
    return res.data;
  }

  /**
   * Returns a Spring {@link Page} envelope with full pagination metadata
   * (totalElements, totalPages, number, size, first, last, empty, content).
   *
   * Use this on /paginated endpoints. The generic {@link ApiClient#get} drops
   * the metadata by extracting `.content` (so callers don't get page count),
   * which is the opposite of what `<Pagination>` needs.
   */
  async getPage<T>(
    endpoint: string,
    params?: Record<string, string | number | boolean>,
    requestInit?: Omit<RequestInit, "method" | "body">,
  ): Promise<Page<T>> {
    let url = endpoint;
    if (params) {
      const qs = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        if (v === undefined || v === null || v === "") continue;
        qs.set(k, String(v));
      }
      url += (url.includes("?") ? "&" : "?") + qs.toString();
    }
    const res = await this.request<Page<T>>(url, { method: "GET", ...requestInit });
    if (res?.data && typeof res.data === "object" && "content" in res.data) {
      return res.data as Page<T>;
    }
    // Backward-compat: backend returning a bare `Page<T>` without the
    // ApiResponse wrapper (shouldn't happen with current controllers, but be safe).
    if (res && typeof res === "object" && "content" in (res as object)) {
      return res as unknown as Page<T>;
    }
    return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 0, first: true, last: true, empty: true };
  }

  // Auth
  async login(data: LoginRequest): Promise<ApiResponse<AuthResponse>> {
    return this.request<AuthResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async logout(): Promise<ApiResponse<void>> {
    return this.request<void>("/auth/logout", {
      method: "POST",
    });
  }

  // Staff
  async getAllStaff(): Promise<ApiResponse<Staff[]>> {
    return this.request<Staff[]>("/staff");
  }

  async getActiveStaff(): Promise<ApiResponse<Staff[]>> {
    return this.request<Staff[]>("/staff/active");
  }

  /**
   * Aggregate counts grouped by StaffStatus (entire DB, no pagination).
   * Returns `{ total, ACTIVE, ON_LEAVE, INACTIVE }` for dashboard summary cards.
   */
  async getStaffStatusCounts(): Promise<ApiResponse<Record<string, number>>> {
    return this.request<Record<string, number>>("/staff/status-counts");
  }

  async searchStaff(params: StaffSearchParams): Promise<ApiResponse<Staff[]>> {
    const query = new URLSearchParams();
    if (params.keyword) query.set("keyword", params.keyword);
    if (params.specialtyId) query.set("specialtyId", String(params.specialtyId));
    if (params.status) query.set("status", params.status);
    return this.request<Staff[]>(`/staff/search?${query.toString()}`);
  }

  /**
   * Server-paginated staff search — drives &lt;Pagination&gt; in StaffCrudPanel.
   * Mirrors {@link searchStaff} on the same filters but on `/staff/search/paginated`.
   */
  async searchStaffsPage(
    params: StaffSearchParams & { page: number; size: number },
  ): Promise<Page<Staff>> {
    return this.getPage<Staff>("/staff/search-page", { ...params });
  }

  async getStaffById(id: number): Promise<ApiResponse<Staff>> {
    return this.request<Staff>(`/staff/${id}`);
  }

  async getCurrentStaff(): Promise<ApiResponse<Staff>> {
    return this.request<Staff>("/staff/me");
  }

  async createStaff(data: Partial<Staff> & { roles?: string[] }): Promise<ApiResponse<Staff>> {
    return this.request<Staff>("/staff", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async updateStaff(id: number, data: Partial<Staff>): Promise<ApiResponse<Staff>> {
    return this.request<Staff>(`/staff/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  async deleteStaff(id: number): Promise<ApiResponse<void>> {
    return this.request<void>(`/staff/${id}`, { method: "DELETE" });
  }

  async importStaff(file: File): Promise<{ imported: number; errors: string[] }> {
    const token = getStoredToken();
    const formData = new FormData();
    formData.append("file", file);

    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;

    const response = await fetch(`${API_BASE}/staff/import`, {
      method: "POST",
      headers,
      body: formData,
    });

    if (!response.ok) {
      const text = await response.text().catch(() => "Unknown error");
      throw new Error(`Import failed (${response.status}): ${text}`);
    }

    return response.json();
  }

  // Schedule
  async getSchedulesByPeriod(periodId: number): Promise<ApiResponse<Schedule[]>> {
    return this.request<Schedule[]>(`/schedules/period/${periodId}`);
  }

  async getSchedulesByPeriodAndDate(
    periodId: number,
    date: string
  ): Promise<ApiResponse<Schedule[]>> {
    return this.request<Schedule[]>(`/schedules/period/${periodId}/date/${date}`);
  }

  async getSchedulesByStaff(staffId: number): Promise<ApiResponse<Schedule[]>> {
    return this.request<Schedule[]>(`/schedules/staff/${staffId}`);
  }

  async getExpertClinicSchedules(periodId: number, specialtyId?: number): Promise<ApiResponse<Schedule[]>> {
    const params = new URLSearchParams({ periodId: String(periodId) });
    if (specialtyId) params.set("specialtyId", String(specialtyId));
    return this.request<Schedule[]>(`/schedules/expert-clinic?${params.toString()}`);
  }

  async getScheduleById(id: number): Promise<ApiResponse<Schedule>> {
    return this.request<Schedule>(`/schedules/${id}`);
  }

  async createSchedule(data: ScheduleRequest): Promise<ApiResponse<Schedule>> {
    return this.request<Schedule>("/schedules", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async updateSchedule(id: number, data: ScheduleRequest): Promise<ApiResponse<Schedule>> {
    return this.request<Schedule>(`/schedules/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  async deleteSchedule(id: number): Promise<ApiResponse<void>> {
    return this.request<void>(`/schedules/${id}`, { method: "DELETE" });
  }

  async overrideScheduleConflict(id: number, reason: string): Promise<ApiResponse<unknown>> {
    return this.request<unknown>(`/schedules/${id}/override`, {
      method: "PUT",
      body: JSON.stringify({ reason }),
    });
  }

  async checkConflicts(periodId: number): Promise<ApiResponse<ConflictCheckResponse>> {
    return this.request<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`);
  }

  async bulkCreateSchedules(
    request: { periodId: number; entries: Array<{ workDate: string; staffId: number; requirementId?: number }> },
    shiftTypeId: string
  ): Promise<BulkScheduleResponse> {
    return this.post<BulkScheduleResponse>(
      `/schedules/bulk?shiftTypeId=${encodeURIComponent(shiftTypeId)}`,
      request
    );
  }

  async dryRunPublish(periodId: number): Promise<PublishDryRunResponse> {
    return this.get<PublishDryRunResponse>(`/periods/${periodId}/publish/dry-run`);
  }

  async findReplacements(
    periodId: number,
    workDate: string,
    shiftTypeId: string,
    originalStaffId: number,
    requiredCount = 1
  ): Promise<Staff[]> {
    const params = new URLSearchParams({
      workDate,
      shiftTypeId,
      originalStaffId: String(originalStaffId),
      requiredCount: String(requiredCount),
    });
    return this.get<Staff[]>(`/schedules/replacements/${periodId}?${params.toString()}`);
  }

  // Schedule Period
  async getAllPeriods(): Promise<ApiResponse<SchedulePeriod[]>> {
    return this.request<SchedulePeriod[]>("/periods");
  }

  /** Server-paginated variant — newest startDate first. */
  async getPeriodsPage(page: number, size: number): Promise<Page<SchedulePeriod>> {
    return this.getPage<SchedulePeriod>("/periods/page", { page, size });
  }

  async getPeriodsByStatus(status: string): Promise<ApiResponse<SchedulePeriod[]>> {
    return this.request<SchedulePeriod[]>(`/periods/status/${status}`);
  }

  async getPeriodById(id: number): Promise<ApiResponse<SchedulePeriod>> {
    return this.request<SchedulePeriod>(`/periods/${id}`);
  }

  async createPeriod(data: Partial<SchedulePeriod>): Promise<ApiResponse<SchedulePeriod>> {
    return this.request<SchedulePeriod>("/periods", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async updatePeriod(id: number, data: Partial<SchedulePeriod>): Promise<ApiResponse<SchedulePeriod>> {
    return this.request<SchedulePeriod>(`/periods/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  async publishPeriod(id: number): Promise<ApiResponse<SchedulePeriod>> {
    return this.request<SchedulePeriod>(`/periods/${id}/publish`, { method: "POST" });
  }

  async bulkPublishPeriods(ids: number[]): Promise<ApiResponse<BulkPeriodResult>> {
    return this.request<BulkPeriodResult>("/periods/bulk/publish", {
      method: "POST",
      body: JSON.stringify({ periodIds: ids }),
    });
  }

  async bulkArchivePeriods(ids: number[]): Promise<ApiResponse<BulkPeriodResult>> {
    return this.request<BulkPeriodResult>("/periods/bulk/archive", {
      method: "POST",
      body: JSON.stringify({ periodIds: ids }),
    });
  }

  async archivePeriod(id: number): Promise<ApiResponse<SchedulePeriod>> {
    return this.request<SchedulePeriod>(`/periods/${id}/archive`, { method: "POST" });
  }

  async deletePeriod(id: number): Promise<ApiResponse<void>> {
    return this.request<void>(`/periods/${id}`, { method: "DELETE" });
  }

  // Dashboard
  async getDashboard(): Promise<ApiResponse<DashboardData>> {
    return this.request<DashboardData>("/dashboard");
  }

  async getShiftStatistics(): Promise<ApiResponse<ShiftStatistics>> {
    return this.request<ShiftStatistics>("/dashboard/shifts");
  }

  async getLeaveRequestStatistics(): Promise<ApiResponse<LeaveRequestStatistics>> {
    return this.request<LeaveRequestStatistics>("/dashboard/leave-requests");
  }

  async getStaffWorkload(periodId: number): Promise<ApiResponse<StaffWorkloadStatistics[]>> {
    return this.request<StaffWorkloadStatistics[]>(`/dashboard/workload/period/${periodId}`);
  }

  async getPeriodSummaries(): Promise<ApiResponse<PeriodSummary[]>> {
    return this.request<PeriodSummary[]>("/dashboard/periods");
  }

  async getHeatmapData(periodId: number): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request<Record<string, unknown>>(`/dashboard/heatmap/period/${periodId}`);
  }

  async exportScheduleExcel(
    periodId: number,
    filters: ScheduleExportFilters = {},
  ): Promise<Blob> {
    const params = buildScheduleExportQuery(filters);
    const token = getStoredToken();
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    const response = await fetch(
      `${API_BASE}/dashboard/export/schedule/${periodId}${params}`,
      { headers, credentials: "include" },
    );
    if (!response.ok) {
      const text = await response.text().catch(() => "Unknown error");
      throw new Error(`Export failed (${response.status}): ${text}`);
    }
    return response.blob();
  }

  async exportSchedulePdf(
    periodId: number,
    filters: ScheduleExportFilters = {},
  ): Promise<Blob> {
    const params = buildScheduleExportQuery(filters);
    const token = getStoredToken();
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    const response = await fetch(
      `${API_BASE}/dashboard/export/schedule/${periodId}/pdf${params}`,
      { headers, credentials: "include" },
    );
    if (!response.ok) {
      const text = await response.text().catch(() => "Unknown error");
      throw new Error(`Export PDF failed (${response.status}): ${text}`);
    }
    return response.blob();
  }

  async exportWorkloadExcel(
    periodId: number,
    filters: ScheduleExportFilters = {},
  ): Promise<Blob> {
    const params = buildScheduleExportQuery(filters);
    const token = getStoredToken();
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    const response = await fetch(
      `${API_BASE}/dashboard/export/workload/${periodId}${params}`,
      { headers, credentials: "include" },
    );
    if (!response.ok) {
      const text = await response.text().catch(() => "Unknown error");
      throw new Error(`Export workload failed (${response.status}): ${text}`);
    }
    return response.blob();
  }

  // Leave Requests
  async getAllLeaveRequests(): Promise<ApiResponse<LeaveRequest[]>> {
    return this.request<LeaveRequest[]>("/leave-requests");
  }

  /**
   * Server-paginated variant — drives the shared &lt;Pagination&gt; widget.
   * @param page 0-indexed page number (Spring convention)
   * @param size items per page
   */
  async getLeaveRequestsPage(page: number, size: number): Promise<Page<LeaveRequest>> {
    return this.getPage<LeaveRequest>("/leave-requests/page", { page, size });
  }

  /**
   * Aggregate counts grouped by LeaveStatus (entire DB, no pagination).
   * Returns `{ total, PENDING, APPROVED, REJECTED, CANCELLED }` for dashboard summary cards.
   */
  async getLeaveRequestStatusCounts(): Promise<ApiResponse<Record<string, number>>> {
    return this.request<Record<string, number>>("/leave-requests/status-counts");
  }

  async getPendingLeaveRequests(): Promise<ApiResponse<LeaveRequest[]>> {
    return this.request<LeaveRequest[]>("/leave-requests/pending");
  }

  async getLeaveRequestsByStatus(status: string): Promise<ApiResponse<LeaveRequest[]>> {
    return this.request<LeaveRequest[]>(`/leave-requests/status/${status}`);
  }

  async getLeaveRequestsByStaff(staffId: number): Promise<ApiResponse<LeaveRequest[]>> {
    return this.request<LeaveRequest[]>(`/leave-requests/staff/${staffId}`);
  }

  async getLeaveRequestById(id: number): Promise<ApiResponse<LeaveRequest>> {
    return this.request<LeaveRequest>(`/leave-requests/${id}`);
  }

  async createLeaveRequest(staffId: number, data: LeaveRequestCreate): Promise<ApiResponse<LeaveRequest>> {
    return this.request<LeaveRequest>(`/leave-requests/staff/${staffId}`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async approveLeaveRequest(id: number, reviewerId: number, reviewNote?: string): Promise<ApiResponse<LeaveRequest>> {
    const params = new URLSearchParams({ reviewerId: String(reviewerId) });
    if (reviewNote) params.set("reviewNote", reviewNote);
    return this.request<LeaveRequest>(`/leave-requests/${id}/approve?${params.toString()}`, { method: "PUT" });
  }

  async rejectLeaveRequest(id: number, reviewerId: number, reviewNote?: string): Promise<ApiResponse<LeaveRequest>> {
    const params = new URLSearchParams({ reviewerId: String(reviewerId) });
    if (reviewNote) params.set("reviewNote", reviewNote);
    return this.request<LeaveRequest>(`/leave-requests/${id}/reject?${params.toString()}`, { method: "PUT" });
  }

  async cancelLeaveRequest(id: number): Promise<ApiResponse<LeaveRequest>> {
    return this.request<LeaveRequest>(`/leave-requests/${id}/cancel`, { method: "PUT" });
  }

  // Schedule Exchanges
  async getAllExchanges(): Promise<ApiResponse<ScheduleExchangeResponse[]>> {
    return this.request<ScheduleExchangeResponse[]>("/schedule-exchanges");
  }

  /** Server-paginated variant — newest first. */
  async getExchangesPage(page: number, size: number): Promise<Page<ScheduleExchangeResponse>> {
    return this.getPage<ScheduleExchangeResponse>("/schedule-exchanges/page", { page, size });
  }

  /**
   * Aggregate counts grouped by ExchangeStatus (entire DB, no pagination).
   * Returns `{ total, PENDING, APPROVED, REJECTED, CANCELLED }` for dashboard cards.
   */
  async getExchangeStatusCounts(): Promise<ApiResponse<Record<string, number>>> {
    return this.request<Record<string, number>>("/schedule-exchanges/status-counts");
  }

  async getPendingExchanges(): Promise<ApiResponse<ScheduleExchangeResponse[]>> {
    return this.request<ScheduleExchangeResponse[]>("/schedule-exchanges/pending");
  }

  async getExchangesByStatus(status: string): Promise<ApiResponse<ScheduleExchangeResponse[]>> {
    return this.request<ScheduleExchangeResponse[]>(`/schedule-exchanges/status/${status}`);
  }

  async getExchangesForUser(userId: number): Promise<ApiResponse<ScheduleExchangeResponse[]>> {
    return this.request<ScheduleExchangeResponse[]>(`/schedule-exchanges/user/${userId}`);
  }

  async getExchangeById(id: number): Promise<ApiResponse<ScheduleExchangeResponse>> {
    return this.request<ScheduleExchangeResponse>(`/schedule-exchanges/${id}`);
  }

  async createExchange(requesterId: number, data: Omit<ScheduleExchangeCreate, "targetStaffId"> & { periodId: number; reason?: string }): Promise<ApiResponse<ScheduleExchangeResponse>> {
    return this.request<ScheduleExchangeResponse>(`/schedule-exchanges/requester/${requesterId}`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async approveExchange(id: number, reviewerId: number, reviewNote?: string): Promise<ApiResponse<ScheduleExchangeResponse>> {
    const params = new URLSearchParams({ reviewerId: String(reviewerId) });
    if (reviewNote) params.set("reviewNote", reviewNote);
    return this.request<ScheduleExchangeResponse>(`/schedule-exchanges/${id}/approve?${params.toString()}`, { method: "PUT" });
  }

  async rejectExchange(id: number, reviewerId: number, reviewNote?: string): Promise<ApiResponse<ScheduleExchangeResponse>> {
    const params = new URLSearchParams({ reviewerId: String(reviewerId) });
    if (reviewNote) params.set("reviewNote", reviewNote);
    return this.request<ScheduleExchangeResponse>(`/schedule-exchanges/${id}/reject?${params.toString()}`, { method: "PUT" });
  }

  async cancelExchange(id: number): Promise<ApiResponse<ScheduleExchangeResponse>> {
    return this.request<ScheduleExchangeResponse>(`/schedule-exchanges/${id}/cancel`, { method: "PUT" });
  }

  // Auto Schedule
  async previewAutoSchedule(data: AutoScheduleRequest, options?: { timeout?: number }): Promise<ApiResponse<AutoScheduleResult>> {
    return this.request<AutoScheduleResult>("/auto-schedule/preview", {
      method: "POST",
      body: JSON.stringify(data),
      timeout: options?.timeout ?? 60000, // Default 60s, configurable for long-running algorithms
    });
  }

  async applyPreview(data: {
    periodId: number;
    algorithmType: string;
    schedules: Array<{ workDate: string; shiftTypeId: string; staffId: number }>;
  }): Promise<ApiResponse<void>> {
    return this.request<void>("/auto-schedule/apply-preview", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async saveScheduleTemplate(data: {
    periodId: number;
    templateName: string;
    description: string;
    algorithmType: string;
    scheduleIds: number[];
  }): Promise<ApiResponse<ScheduleTemplate>> {
    return this.request<ScheduleTemplate>("/auto-schedule/save-template", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async getWorkloadChartData(periodId: number, shiftTypeId?: string): Promise<{
    staffWorkloadData: Array<{
      staffId: number;
      staffName: string;
      specialty: string | null;
      totalShifts: number;
      L01: number;
      L02: number;
      L03: number;
      L04: number;
      workloadPercentage: number;
    }>;
    totalSchedules: number;
    totalStaff: number;
    averageWorkload: number;
    minWorkload: number;
    maxWorkload: number;
    shiftTypeId?: string;
  }> {
    const params = new URLSearchParams();
    if (shiftTypeId) params.set("shiftTypeId", shiftTypeId);
    const qs = params.toString();
    return this.get(`/auto-schedule/workload-chart/${periodId}${qs ? `?${qs}` : ""}`);
  }

  async getUnassignedDaysReport(periodId: number): Promise<{
    totalUnassignedDays: number;
    unassignedDays: Array<{
      workDate: string;
      dayOfWeek: string;
      shiftTypeId: string;
      shiftTypeName: string;
      requiredStaffCount: number;
      assignedStaffCount: number;
      missingCount: number;
    }>;
  }> {
    return this.get(`/auto-schedule/unassigned/${periodId}`);
  }

  async getMetricsByPeriod(periodId: number): Promise<AlgorithmMetrics[]> {
    return this.get<AlgorithmMetrics[]>(`/auto-schedule/metrics/period/${periodId}`);
  }

  /** Server-paginated variant of {@link getAllMetrics}. */
  async getMetricsPage(
    page: number,
    size: number,
    periodId?: number,
  ): Promise<Page<AlgorithmMetrics>> {
    return this.getPage<AlgorithmMetrics>(
      "/auto-schedule/metrics/page",
      periodId ? { page, size, periodId } : { page, size },
    );
  }

  async suggestReplacements(scheduleId: number): Promise<ReplacementSuggestion> {
    return this.get<ReplacementSuggestion>(`/auto-schedule/suggest-replacements/${scheduleId}`);
  }

  async getAllMetrics(): Promise<ApiResponse<AlgorithmMetrics[]>> {
    return this.request<AlgorithmMetrics[]>("/auto-schedule/metrics");
  }

  async getAlgorithmProgress(periodId: number): Promise<{
    status: "IDLE" | "RUNNING" | "COMPLETED" | "FAILED";
    periodId: number;
    step?: string;
    percent?: number;
    message?: string;
    startedAt?: string;
    updatedAt?: string;
    resultJson?: string;
  }> {
    return this.get(`/auto-schedule/progress/${periodId}`);
  }

  // AlgorithmConfig
  async getAllAlgorithmConfigs(): Promise<ApiResponse<Array<{
    paramKey: string;
    paramValue: string;
    valueType: string;
    description: string;
    updatedBy: string;
    createdAt: string;
    updatedAt: string;
  }>>> {
    return this.request<Array<{
      paramKey: string;
      paramValue: string;
      valueType: string;
      description: string;
      updatedBy: string;
      createdAt: string;
      updatedAt: string;
    }>>("/auto-schedule/config");
  }

  async getRolePermissionMatrix(): Promise<ApiResponse<import("@/types/api").RolePermissionMatrix>> {
    return this.request<import("@/types/api").RolePermissionMatrix>("/roles/permissions/matrix");
  }

  async toggleRolePermission(data: { roleId: number; permissionId: number; granted: boolean }): Promise<ApiResponse<null>> {
    return this.request<null>("/roles/permissions/toggle", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async getAlgorithmConfigAudit(paramKey?: string, page = 0, size = 50): Promise<{
    content: Array<{
      id: number;
      paramKey: string;
      oldValue: string | null;
      newValue: string;
      action: string;
      changedByUsername: string | null;
      createdAt: string;
    }>;
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  }> {
    const params = new URLSearchParams();
    if (paramKey) params.set("paramKey", paramKey);
    params.set("page", String(page));
    params.set("size", String(size));
    return this.get(`/auto-schedule/config/audit?${params.toString()}`);
  }

  async createAlgorithmConfig(data: { paramKey: string; paramValue: string; valueType: string; description?: string }): Promise<ApiResponse<{
    paramKey: string;
    paramValue: string;
    valueType: string;
    description: string;
  }>> {
    return this.request<{
      paramKey: string;
      paramValue: string;
      valueType: string;
      description: string;
    }>("/auto-schedule/config", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async updateAlgorithmConfig(paramKey: string, data: { paramValue: string; description?: string }): Promise<ApiResponse<{
    paramKey: string;
    paramValue: string;
    valueType: string;
    description: string;
  }>> {
    return this.request<{
      paramKey: string;
      paramValue: string;
      valueType: string;
      description: string;
    }>(`/auto-schedule/config/${encodeURIComponent(paramKey)}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  async deleteAlgorithmConfig(paramKey: string): Promise<ApiResponse<void>> {
    return this.request<void>(`/auto-schedule/config/${encodeURIComponent(paramKey)}`, {
      method: "DELETE",
    });
  }

  async syncAlgorithmConfigDescriptions(): Promise<ApiResponse<Record<string, string>>> {
    return this.request<Record<string, string>>("/auto-schedule/config/sync-descriptions", {
      method: "POST",
    });
  }

  // Runtime Config (all algorithm parameters in one call)
  async getRuntimeConfig(): Promise<ApiResponse<{
    maxIterations: number;
    weekendWeight: number;
    overnightRecoveryHours: number;
    greedyCoverageThreshold: number;
    balanceScoreMin: number;
    autoCompensationEnabled: boolean;
    backtrackTimeLimitSeconds: number;
  }>> {
    return this.request<{
      maxIterations: number;
      weekendWeight: number;
      overnightRecoveryHours: number;
      greedyCoverageThreshold: number;
      balanceScoreMin: number;
      autoCompensationEnabled: boolean;
      backtrackTimeLimitSeconds: number;
    }>("/auto-schedule/runtime-config");
  }

  async updateRuntimeConfig(data: {
    maxIterations: number;
    weekendWeight: number;
    overnightRecoveryHours: number;
    greedyCoverageThreshold: number;
    balanceScoreMin: number;
    autoCompensationEnabled: boolean;
    backtrackTimeLimitSeconds: number;
  }): Promise<ApiResponse<{
    maxIterations: number;
    weekendWeight: number;
    overnightRecoveryHours: number;
    greedyCoverageThreshold: number;
    balanceScoreMin: number;
    autoCompensationEnabled: boolean;
    backtrackTimeLimitSeconds: number;
  }>> {
    return this.request<{
      maxIterations: number;
      weekendWeight: number;
      overnightRecoveryHours: number;
      greedyCoverageThreshold: number;
      balanceScoreMin: number;
      autoCompensationEnabled: boolean;
      backtrackTimeLimitSeconds: number;
    }>("/auto-schedule/runtime-config", {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  async getAutoGenConfig(): Promise<ApiResponse<{
    enabled: boolean;
    l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
    l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
    l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
    l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
    holidayMode: string;
    removedShiftTypes: string[];
  }>> {
    return this.request<{
      enabled: boolean;
      l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
      l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
      l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
      l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
      holidayMode: string;
      removedShiftTypes: string[];
    }>("/auto-schedule/auto-gen-config");
  }

  async updateAutoGenConfig(data: {
    enabled: boolean;
    l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
    l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
    l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
    l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
    holidayMode: string;
    removedShiftTypes: string[];
    l04CrossSpecialty?: boolean;
    l04CrossSpecialtyRatio?: number;
  }): Promise<ApiResponse<{
    enabled: boolean;
    l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
    l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
    l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
    l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
    holidayMode: string;
    removedShiftTypes: string[];
    l04CrossSpecialty?: boolean;
    l04CrossSpecialtyRatio?: number;
  }>> {
    return this.request<{
      enabled: boolean;
      l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
      l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
      l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
      l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
      holidayMode: string;
      removedShiftTypes: string[];
      l04CrossSpecialty?: boolean;
      l04CrossSpecialtyRatio?: number;
    }>("/auto-schedule/auto-gen-config", {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  // Shift Types
  async getAllShiftTypes(): Promise<ApiResponse<ShiftType[]>> {
    return this.request<ShiftType[]>("/shift-types");
  }

  async getActiveShiftTypes(): Promise<ApiResponse<ShiftType[]>> {
    return this.request<ShiftType[]>("/shift-types/active");
  }

  async getShiftTypeById(id: string): Promise<ApiResponse<ShiftType>> {
    return this.request<ShiftType>(`/shift-types/${id}`);
  }

  // Specialties
  async getAllSpecialties(): Promise<ApiResponse<Specialty[]>> {
    return this.request<Specialty[]>("/specialties");
  }

  async getActiveSpecialties(): Promise<ApiResponse<Specialty[]>> {
    return this.request<Specialty[]>("/specialties/active");
  }

  async getSpecialtyById(id: number): Promise<ApiResponse<Specialty>> {
    return this.request<Specialty>(`/specialties/${id}`);
  }

  async createSpecialty(data: { name: string; description?: string }): Promise<ApiResponse<Specialty>> {
    return this.request<Specialty>("/specialties", { method: "POST", body: JSON.stringify(data) });
  }

  async updateSpecialty(id: number, data: { name: string; description?: string; isActive?: boolean }): Promise<ApiResponse<Specialty>> {
    return this.request<Specialty>(`/specialties/${id}`, { method: "PUT", body: JSON.stringify(data) });
  }

  async deleteSpecialty(id: number): Promise<ApiResponse<void>> {
    return this.request<void>(`/specialties/${id}`, { method: "DELETE" });
  }

  // Holidays
  async getAllHolidays(): Promise<ApiResponse<Holiday[]>> {
    return this.request<Holiday[]>("/holidays");
  }

  /** Server-paginated variant — newest holidayDate first. */
  async getHolidaysPage(page: number, size: number): Promise<Page<Holiday>> {
    return this.getPage<Holiday>("/holidays/page", { page, size });
  }

  async getActiveHolidays(): Promise<ApiResponse<Holiday[]>> {
    return this.request<Holiday[]>("/holidays/active");
  }

  async getHolidaysByYear(year: number): Promise<ApiResponse<Holiday[]>> {
    return this.request<Holiday[]>(`/holidays/year/${year}`);
  }

  async getHolidayById(id: number): Promise<ApiResponse<Holiday>> {
    return this.request<Holiday>(`/holidays/${id}`);
  }

  async createHoliday(data: { name: string; holidayDate: string; isNationalHoliday?: boolean; description?: string }): Promise<ApiResponse<Holiday>> {
    return this.request<Holiday>("/holidays", { method: "POST", body: JSON.stringify(data) });
  }

  async updateHoliday(id: number, data: { name: string; holidayDate: string; isNationalHoliday?: boolean; description?: string }): Promise<ApiResponse<Holiday>> {
    return this.request<Holiday>(`/holidays/${id}`, { method: "PUT", body: JSON.stringify(data) });
  }

  async deleteHoliday(id: number): Promise<ApiResponse<void>> {
    return this.request<void>(`/holidays/${id}`, { method: "DELETE" });
  }

  // Notifications
  async getNotificationsByStaff(staffId: number): Promise<ApiResponse<Notification[]>> {
    return this.request<Notification[]>(`/notifications/staff/${staffId}`);
  }

  /** Server-paginated variant for the current caller. */
  async getNotificationsPage(page: number, size: number): Promise<Page<Notification>> {
    return this.getPage<Notification>("/notifications/me/page", { page, size });
  }

  async getUnreadNotifications(staffId: number): Promise<ApiResponse<Notification[]>> {
    return this.request<Notification[]>(`/notifications/staff/${staffId}/unread`);
  }

  async countUnreadNotifications(staffId: number): Promise<ApiResponse<{ count: number }>> {
    return this.request<{ count: number }>(`/notifications/staff/${staffId}/unread/count`);
  }

  /**
   * Unread count for the currently authenticated staff. Resolves the staff id
   * server-side from the security context, so the frontend doesn't need to
   * know its own staff id. This avoids the page-slice counting bug where the
   * visible "chưa đọc" count was computed from the current paginated slice.
   */
  async countMyUnreadNotifications(): Promise<ApiResponse<{ count: number }>> {
    return this.request<{ count: number }>("/notifications/me/unread/count");
  }

  async markNotificationAsRead(id: number): Promise<ApiResponse<Notification>> {
    return this.request<Notification>(`/notifications/${id}/read`, { method: "PUT" });
  }

  async markAllNotificationsAsRead(staffId: number): Promise<ApiResponse<{ status: string }>> {
    return this.request<{ status: string }>(`/notifications/staff/${staffId}/read-all`, { method: "PUT" });
  }

  async deleteNotification(id: number): Promise<ApiResponse<void>> {
    return this.request<void>(`/notifications/${id}`, { method: "DELETE" });
  }

  async createNotification(staffId: number, data: { title: string; message: string }): Promise<ApiResponse<Notification>> {
    return this.request<Notification>(`/notifications/staff/${staffId}`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async broadcastNotification(data: { title: string; message: string }): Promise<ApiResponse<{ status: string }>> {
    return this.request<{ status: string }>("/notifications/broadcast", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  // Schedule Templates
  async getAllTemplates(): Promise<ApiResponse<ScheduleTemplate[]>> {
    return this.request<ScheduleTemplate[]>("/schedule-templates");
  }

  async getActiveTemplates(): Promise<ApiResponse<ScheduleTemplate[]>> {
    return this.request<ScheduleTemplate[]>("/schedule-templates/active");
  }

  async getTemplateById(id: number): Promise<ApiResponse<ScheduleTemplate>> {
    return this.request<ScheduleTemplate>(`/schedule-templates/${id}`);
  }

  async createTemplate(data: Partial<ScheduleTemplate>): Promise<ApiResponse<ScheduleTemplate>> {
    return this.request<ScheduleTemplate>("/schedule-templates", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async updateTemplate(id: number, data: Partial<ScheduleTemplate>): Promise<ApiResponse<ScheduleTemplate>> {
    return this.request<ScheduleTemplate>(`/schedule-templates/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  async deleteTemplate(id: number): Promise<ApiResponse<void>> {
    return this.request<void>(`/schedule-templates/${id}`, { method: "DELETE" });
  }

  async applyTemplate(templateId: number, periodId: number): Promise<ApiResponse<{ templateId: number; periodId: number; appliedCount: number }>> {
    return this.request<{ templateId: number; periodId: number; appliedCount: number }>(
      `/schedule-templates/${templateId}/apply/${periodId}`,
      { method: "POST" }
    );
  }

  async previewTemplate(templateId: number, periodId: number): Promise<ApiResponse<TemplatePreviewItem[]>> {
    return this.request<TemplatePreviewItem[]>(`/schedule-templates/${templateId}/preview/${periodId}`);
  }

  async applyTemplateWithEdits(
    templateId: number,
    periodId: number,
    edits: { slotId: number; assignedStaffId: number }[]
  ): Promise<ApiResponse<{ templateId: number; periodId: number; appliedCount: number }>> {
    return this.request<{ templateId: number; periodId: number; appliedCount: number }>(
      `/schedule-templates/${templateId}/apply/${periodId}/with-edits`,
      { method: "POST", body: JSON.stringify({ edits }) }
    );
  }

  // Audit History
  async getAuditHistory(page = 0, size = 50): Promise<AuditHistoryPage> {
    // Backend returns ApiResponse<Page<AuditHistoryResponse>> with Spring Data Page structure:
    // { success: true, data: { content: [], totalElements, totalPages, number, size, first, last, empty }, timestamp }
    // The `request` method already returns the parsed ApiResponse wrapper, so res.data is the Page object.
    const res = await this.request<{ content: AuditHistory[]; totalElements: number; totalPages: number; number: number; size: number; first: boolean; last: boolean; empty: boolean }>(
      `/audit-history?page=${page}&size=${size}`
    );
    return res.data as unknown as AuditHistoryPage;
  }

  /**
   * Fetch the global KPI summary (total / CREATE / UPDATE / DELETE counts).
   * Counts every row in audit_history so the cards reflect the DB,
   * not just the slice returned for the current page.
   */
  async getAuditHistorySummary(): Promise<AuditHistorySummary> {
    const res = await this.request<AuditHistorySummary>(`/audit-history/summary`);
    return res.data as unknown as AuditHistorySummary;
  }

  /**
   * Filtered KPI summary that mirrors every filter on the audit list page.
   * All params are optional — pass null/empty to skip a filter.
   */
  async getAuditHistorySummaryFiltered(params: {
    startDate?: string;
    endDate?: string;
    module?: string;
    action?: string;
    search?: string;
  }): Promise<AuditHistorySummary> {
    const search = new URLSearchParams();
    if (params.startDate) search.set("startDate", params.startDate);
    if (params.endDate)   search.set("endDate", params.endDate);
    if (params.module)    search.set("module", params.module);
    if (params.action)    search.set("action", params.action);
    if (params.search)    search.set("search", params.search);
    const qs = search.toString();
    const res = await this.request<AuditHistorySummary>(
      `/audit-history/summary/filter${qs ? `?${qs}` : ""}`
    );
    return res.data as unknown as AuditHistorySummary;
  }

  async deleteAuditHistory(id: number): Promise<void> {
    await this.request<void>(`/audit-history/${id}`, { method: "DELETE" });
  }

  async deleteMultipleAuditHistory(ids: number[]): Promise<number> {
    const res = await this.request<{ data: number }>(`/audit-history`, {
      method: "DELETE",
      body: JSON.stringify(ids),
    });
    const response = res as unknown as { data: number };
    return response.data ?? ids.length;
  }

  async deleteAuditHistoryByDateRange(startDate: string, endDate: string): Promise<number> {
    const res = await this.request<{ data: number }>(
      `/audit-history/date-range?startDate=${startDate}&endDate=${endDate}`,
      { method: "DELETE" }
    );
    const response = res as unknown as { data: number };
    return response.data ?? 0;
  }

  /**
   * Wipe the entire audit_history table. Requires ADMIN role + typed
   * confirmation on the UI side. Returns the number of rows deleted.
   */
  async deleteAllAuditHistory(): Promise<number> {
    const res = await this.request<{ data: number }>(`/audit-history/all`, {
      method: "DELETE",
    });
    const response = res as unknown as { data: number };
    return response.data ?? 0;
  }

  // Statistics (M02-F05, M04-F05, M05-F05)
  async getStaffStatistics(periodId: number, shiftTypeId?: string): Promise<StaffShiftStatistics[]> {
    const params = new URLSearchParams({ periodId: String(periodId) });
    if (shiftTypeId) params.set("shiftTypeId", shiftTypeId);
    return this.get<StaffShiftStatistics[]>(`/statistics/staff?${params.toString()}`);
  }
}

export const api = new ApiClient();
export default api;
