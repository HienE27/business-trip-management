import type {
  ApiResponse,
  AuthResponse,
  LoginRequest,
  Staff,
  StaffSearchParams,
  Schedule,
  ScheduleRequest,
  SchedulePeriod,
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
  ShiftRequirement,
  Specialty,
  Notification,
  Holiday,
  ScheduleTemplate,
  TemplatePreviewItem,
  AuditHistory,
  ConflictCheckResponse,
  ShiftType,
  LeaveRequestStatistics,
  ReplacementSuggestion,
} from "@/types/api";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const LOGIN_PATH = "/login";
const TOKEN_STORAGE_KEY = "medschedule.token";

function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_STORAGE_KEY);
}

class ApiClient {
  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      ...(options.headers as Record<string, string>),
    };

    const token = getStoredToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    const response = await fetch(`${API_BASE}${endpoint}`, {
      ...options,
      headers,
      credentials: "include",
    });

    if (!response.ok) {
      if (response.status === 401 && typeof window !== "undefined") {
        window.localStorage.removeItem("medschedule.user");
        const currentPath = window.location.pathname;
        if (currentPath !== LOGIN_PATH) {
          window.location.replace(LOGIN_PATH);
        }
      }
      const errorData = await response.json().catch(() => ({}));
      throw new Error(errorData.message || `HTTP ${response.status}`);
    }

    return response.json().catch(() => ({ success: true, data: null, message: "Thành công" }));
  }

  // Generic HTTP methods
  async get<T>(endpoint: string, params?: Record<string, string | number | boolean>, requestInit?: Omit<RequestInit, "method" | "body">): Promise<T> {
    let url = endpoint;
    if (params) {
      const qs = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        qs.set(k, String(v));
      }
      url += (url.includes("?") ? "&" : "?") + qs.toString();
    }
    const res = await this.request<T>(url, { method: "GET", ...requestInit });
    return res.data;
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

  async searchStaff(params: StaffSearchParams): Promise<ApiResponse<Staff[]>> {
    const query = new URLSearchParams();
    if (params.keyword) query.set("keyword", params.keyword);
    if (params.specialtyId) query.set("specialtyId", String(params.specialtyId));
    if (params.status) query.set("status", params.status);
    return this.request<Staff[]>(`/staff/search?${query.toString()}`);
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

  async checkConflicts(periodId: number): Promise<ApiResponse<ConflictCheckResponse>> {
    return this.request<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`);
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

  async archivePeriod(id: number): Promise<ApiResponse<SchedulePeriod>> {
    return this.request<SchedulePeriod>(`/periods/${id}/archive`, { method: "POST" });
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

  async exportScheduleExcel(periodId: number): Promise<Blob> {
    const token = getStoredToken();
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    const response = await fetch(`${API_BASE}/dashboard/export/schedule/${periodId}`, {
      headers,
      credentials: "include",
    });
    if (!response.ok) throw new Error("Export failed");
    return response.blob();
  }

  async exportSchedulePdf(periodId: number): Promise<Blob> {
    const token = getStoredToken();
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    const response = await fetch(`${API_BASE}/dashboard/export/schedule/${periodId}/pdf`, {
      headers,
      credentials: "include",
    });
    if (!response.ok) throw new Error("Export PDF failed");
    return response.blob();
  }

  // Leave Requests
  async getAllLeaveRequests(): Promise<ApiResponse<LeaveRequest[]>> {
    return this.request<LeaveRequest[]>("/leave-requests");
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
  async previewAutoSchedule(data: AutoScheduleRequest): Promise<ApiResponse<AutoScheduleResult>> {
    return this.request<AutoScheduleResult>("/auto-schedule/preview", {
      method: "POST",
      body: JSON.stringify(data),
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

  async getWorkloadChartData(periodId: number): Promise<{
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
  }> {
    return this.get(`/auto-schedule/workload-chart/${periodId}`);
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

  async suggestReplacements(scheduleId: number): Promise<ReplacementSuggestion> {
    return this.get<ReplacementSuggestion>(`/auto-schedule/suggest-replacements/${scheduleId}`);
  }

  async getAllMetrics(): Promise<ApiResponse<AlgorithmMetrics[]>> {
    return this.request<AlgorithmMetrics[]>("/auto-schedule/metrics");
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

  // Shift Requirements
  async getAllRequirements(): Promise<ApiResponse<ShiftRequirement[]>> {
    return this.request<ShiftRequirement[]>("/shift-requirements");
  }

  async getRequirementsByPeriod(periodId: number): Promise<ApiResponse<ShiftRequirement[]>> {
    return this.request<ShiftRequirement[]>(`/shift-requirements/period/${periodId}`);
  }

  async getRequirementsByPeriodAndDate(periodId: number, date: string): Promise<ApiResponse<ShiftRequirement[]>> {
    return this.request<ShiftRequirement[]>(`/shift-requirements/period/${periodId}/date/${date}`);
  }

  async createRequirement(data: Partial<ShiftRequirement>): Promise<ApiResponse<ShiftRequirement>> {
    return this.request<ShiftRequirement>("/shift-requirements", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async updateRequirement(id: number, data: Partial<ShiftRequirement>): Promise<ApiResponse<ShiftRequirement>> {
    return this.request<ShiftRequirement>(`/shift-requirements/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  async deleteRequirement(id: number): Promise<ApiResponse<void>> {
    return this.request<void>(`/shift-requirements/${id}`, { method: "DELETE" });
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

  // Holidays
  async getAllHolidays(): Promise<ApiResponse<Holiday[]>> {
    return this.request<Holiday[]>("/holidays");
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

  async getUnreadNotifications(staffId: number): Promise<ApiResponse<Notification[]>> {
    return this.request<Notification[]>(`/notifications/staff/${staffId}/unread`);
  }

  async countUnreadNotifications(staffId: number): Promise<ApiResponse<{ count: number }>> {
    return this.request<{ count: number }>(`/notifications/staff/${staffId}/unread/count`);
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

  // Audit History
  async getAllAuditHistory(): Promise<ApiResponse<AuditHistory[]>> {
    return this.request<AuditHistory[]>("/audit-history");
  }

  async getAuditHistoryByTableAndRecord(tableName: string, recordId: number): Promise<ApiResponse<AuditHistory[]>> {
    return this.request<AuditHistory[]>(`/audit-history/table/${tableName}/record/${recordId}`);
  }

  async getAuditHistoryByUser(userId: number): Promise<ApiResponse<AuditHistory[]>> {
    return this.request<AuditHistory[]>(`/audit-history/user/${userId}`);
  }

  async getAuditHistoryByDateRange(startDate: string, endDate: string): Promise<ApiResponse<AuditHistory[]>> {
    const params = new URLSearchParams({ startDate, endDate });
    return this.request<AuditHistory[]>(`/audit-history/date-range?${params.toString()}`);
  }
}

export const api = new ApiClient();
export default api;
