// ============================================================
// API Response Wrapper
// ============================================================
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

/**
 * Spring Data Page envelope exposed at /paginated endpoints.
 * Mirrors the JSON shape returned by Spring's `Page<T>` serializer — every
 * field maps 1:1 to the underlying type so the shared {@code <Pagination>}
 * component can read totalElements / totalPages / number / size directly.
 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// ============================================================
// Auth Types
// ============================================================
export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  username: string;
  roles: string[];
}

// ============================================================
// Staff Types
// ============================================================
export interface StaffSpecialty {
  id: number;
  name: string;
}

export interface Staff {
  id: number;
  staffCode: string;
  username: string;
  fullName: string;
  phone?: string;
  email?: string;
  position?: string;
  specialty?: StaffSpecialty;
  maxShiftsPerMonth: number;
  isActive: boolean;
  status: string;
  createdAt: string;
  updatedAt: string;
  roles: string[];
}

export interface StaffSearchParams {
  keyword?: string;
  specialtyId?: number;
  status?: string;
  role?: string;
  position?: string;
}

// ============================================================
// Schedule Types
// ============================================================
export interface ShiftType {
  id: string;
  name: string;
  description?: string;
  startTime?: string;
  endTime?: string;
  isOvernight: boolean;
  fatigueScore: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Schedule {
  id: number;
  periodId: number;
  period?: {
    id: number;
    periodName: string;
    startDate: string;
    endDate: string;
    status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  };
  workDate: string;
  staff: {
    id: number;
    username?: string;
    staffCode?: string;
    fullName: string;
    specialtyName?: string | null;
    roles?: string[];
  };
  shiftType: {
    id: string;
    name: string;
    description?: string | null;
    startTime?: string | null;
    endTime?: string | null;
    isOvernight: boolean;
    fatigueScore?: number;
  };
  requirementId?: number;
  compensationDate?: string | null;
  conflictReasons?: string[];
  notes?: string | null;
  hasConflict: boolean;
  conflictCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface ScheduleRequest {
  periodId: number;
  workDate: string;
  staffId: number;
  shiftTypeId: string;
  requirementId?: number;
  notes?: string;
}

export interface DayCoverage {
  date: string;
  shiftTypeId: string;
  required: number;
  assigned: number;
}

// ============================================================
// Schedule Period Types
// ============================================================
export interface SchedulePeriod {
  id: number;
  periodName: string;
  startDate: string;
  endDate: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  generatedBy?: { id: number; fullName: string };
  generatedAt?: string;
  publishedBy?: string;
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;
}

// ============================================================
// Bulk Period Operations
// ============================================================
export interface BulkPeriodResult {
  totalRequested: number;
  successCount: number;
  failureCount: number;
  results: BulkPeriodItem[];
}

export interface BulkPeriodItem {
  id: number;
  periodName?: string;
  success: boolean;
  message: string;
  data?: SchedulePeriod;
  processedAt: string;
}

// ============================================================
// Dashboard Types
// ============================================================
export interface DashboardSummary {
  totalStaff: number;
  activeStaff: number;
  totalSchedules: number;
  totalPeriods: number;
  pendingLeaveRequests: number;
  pendingScheduleExchanges: number;
}

export interface ShiftStatistics {
  L01Count: number;
  L02Count: number;
  L03Count: number;
  L04Count: number;
}

export interface StaffWorkloadStatistics {
  staffId: number;
  staffName: string;
  scheduleCount: number;
  L01Count: number;
  L02Count: number;
  L03Count: number;
  L04Count: number;
  leaveDays: number;
}

export interface PeriodSummary {
  periodId: number;
  periodName: string;
  startDate: string;
  endDate: string;
  status: string;
  scheduleCount: number;
  staffCount: number;
}

export interface LeaveRequestStatistics {
  total: number;
  pending: number;
  approved: number;
  rejected: number;
}

export interface DashboardData {
  summary: DashboardSummary;
  shiftStatistics: ShiftStatistics;
  leaveRequestStatistics: LeaveRequestStatistics;
  staffWorkloadStatistics: StaffWorkloadStatistics;
}

// ============================================================
// Leave Request Types
// ============================================================
export type LeaveStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export interface LeaveRequestStaffSummary {
  id: number;
  fullName: string;
}

export interface LeaveRequest {
  id: number;
  staffId?: number;
  staff?: LeaveRequestStaffSummary;
  staffName?: string;
  startDate: string;
  endDate: string;
  reason?: string;
  status: LeaveStatus;
  reviewedBy?: LeaveRequestStaffSummary;
  reviewerName?: string;
  reviewNote?: string;
  reviewedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface LeaveRequestCreate {
  startDate: string;
  endDate: string;
  reason?: string;
}

// ============================================================
// Schedule Exchange Types
// ============================================================
export type ExchangeStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export interface ScheduleExchange {
  id: number;
  requesterId: number;
  requesterName?: string;
  targetStaffId: number;
  targetStaffName?: string;
  requesterScheduleId: number;
  targetScheduleId: number;
  reason?: string;
  status: ExchangeStatus;
  reviewedBy?: number;
  reviewerName?: string;
  reviewNote?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ScheduleExchangeResponse {
  id: number;
  periodId: number;
  requester: { id: number; fullName: string };
  target: { id: number; fullName: string };
  requesterSchedule: {
    id: number;
    workDate: string;
    shiftType: { id: string; name: string };
  };
  targetSchedule: {
    id: number;
    workDate: string;
    shiftType: { id: string; name: string };
  };
  reason?: string;
  status: ExchangeStatus;
  reviewedBy?: { id: number; fullName: string };
  reviewedAt?: string;
  reviewNote?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ScheduleExchangeCreate {
  targetStaffId: number;
  requesterScheduleId: number;
  targetScheduleId: number;
  reason?: string;
}

// ============================================================
// Auto Schedule Types
// ============================================================
export interface AutoScheduleRequest {
  periodId: number;
  algorithmType?: string;
  autoAssign?: boolean;
  excludedStaffIds?: number[];
  holidayMode?: "SKIP" | "PARTIAL";
  /**
   * Runtime override for max_shifts_per_month cap.
   * null = use DB per-staff caps; positive int = force all staff to this cap; 0 = disable cap.
   * Tip: start with 8 (realistic hospital workload), 25-40 (relaxed sim), 0 (disable).
   */
  maxShiftsPerMonthOverride?: number | null;
}

export interface AutoScheduleSummary {
  scheduleId: number | null;
  staffId: number;
  staffName: string;
  workDate: string;
  shiftTypeId: string;
  shiftTypeName: string;
  staffSpecialtyName?: string | null;
  requiredSpecialtyName?: string | null;
  /**
   * Backend requirement id (ShiftRequirement). Populated by the auto-schedule
   * previews so the apply-preview round-trip can pin the right ShiftRequirement
   * even when L04 has multiple specialties on the same date+shiftType.
   */
  requirementId?: number | null;
}

export interface AutoScheduleResult {
  success: boolean;
  message: string;
  periodId: number;
  algorithmType: string;
  executionTimeMs: number;
  /**
   * KPI only meaningful after Auto Scheduling runs.
   * `null` indicates templates were applied but the algorithm has not run yet
   * (no Schedule rows => no coverage to measure). Avoid hardcoding 100 here.
   */
  coverageRate: number | null;
  balanceScore: number | null;
  conflictCount: number | null;
  totalSchedulesCreated: number;
  status?: "SCHEDULED" | "TEMPLATE_APPLIED" | "PREVIEW";
  schedules: AutoScheduleSummary[];
  executedAt: string;
  excludedStaffIds?: number[];
  unassignedDays?: Array<{
    workDate: string;
    dayOfWeek: string;
    shiftTypeId: string;
    shiftTypeName: string;
    requiredStaffCount: number;
    assignedStaffCount: number;
    missingCount: number;
    reason?: string;
    reasonCode?: "NO_SPECIALTY_STAFF" | "NO_ELIGIBLE_STAFF" | "PARTIAL_COVERAGE" | string;
    severity?: "critical" | "warning" | "info" | string;
  }>;
  /** Chi tiết phân bổ theo từng loại lịch (L01/L02/L03/L04) */
  byShiftType?: Record<string, ShiftTypeBreakdown>;
}

export interface ShiftTypeBreakdown {
  shiftTypeId: string;
  shiftTypeName: string;
  totalAssigned: number;
  totalRequired: number;
  coverageRate: number;
  unassignedDates: string[];
  distinctStaffAssigned: number;
}

export interface UnassignedDayItem {
  workDate: string;
  dayOfWeek: string;
  shiftTypeId: string;
  shiftTypeName: string;
  specialty: string | null;
  requiredStaffCount: number;
  assignedStaffCount: number;
  missingCount: number;
}

export interface UnassignedDayReport {
  periodId: number;
  periodName: string;
  startDate: string;
  endDate: string;
  totalUnassignedDays: number;
  unassignedDays: UnassignedDayItem[];
}

export interface ReplacementCandidate {
  staffId: number;
  staffName: string;
  specialty: string | null;
  currentWorkload: number;
  conflicts: string[];
  isAvailable: boolean;
  reason: string;
}

export interface ReplacementSuggestion {
  originalScheduleId: number;
  originalStaffId: number;
  originalStaffName: string;
  workDate: string;
  shiftTypeId: string;
  shiftTypeName: string;
  totalCandidates: number;
  availableCount: number;
  suggestions: ReplacementCandidate[];
}

export interface AlgorithmMetrics {
  id: number;
  algorithmType: string;
  executionTimeMs: number;
  coverageRate: number;
  balanceScore: number;
  conflictCount: number;
  totalSchedulesCreated?: number;
  periodId?: number;
  periodName?: string;
  runToken?: string;
  createdAt: string;
}

// ============================================================
// Specialty Types
// ============================================================
export interface Specialty {
  id: number;
  name: string;
  description?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

// ============================================================
// Holiday Types
// ============================================================
export interface Holiday {
  id: number;
  name: string;
  holidayDate: string;
  year: number;
  isNationalHoliday?: boolean;
  description?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

// ============================================================
// Notification Types
// ============================================================
export interface Notification {
  id: number;
  staffId: number;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

// ============================================================
// Schedule Template Types
// ============================================================
export interface ScheduleTemplate {
  id: number;
  name: string;
  description?: string;
  dayOfWeek?: number | null;
  shiftTypeId?: string | null;
  shiftTypeName?: string;
  specialtyId?: number | null;
  specialtyName?: string | null;
  requiredStaffCount: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  templateType?: string; // "PATTERN" | "GENERATED"
  generatedScheduleIds?: string;
  sourcePeriodId?: number;
  sourcePeriodName?: string;
  algorithmType?: string;
  algorithmConfig?: string;
}

export interface TemplatePreviewItem {
  id: number;
  workDate: string;
  dayOfWeek: string;
  shiftTypeId: string;
  shiftTypeName: string;
  specialtyName: string | null;
  requiredStaffCount: number;
  assignedStaffId: number | null;
  assignedStaffName: string | null;
}

export interface ScheduleTemplateRequest {
  name: string;
  description?: string;
  dayOfWeek?: number | null;
  shiftTypeId?: string | null;
  specialtyId?: number | null;
  requiredStaffCount: number;
}

// ============================================================
// Audit History Types
// ============================================================
export interface AuditHistory {
  id: number;
  userId?: number;
  userName?: string;
  action: string;
  actionType?: "INSERT" | "UPDATE" | "DELETE" | "CREATE" | "UPDATE" | "DELETE";
  tableName: string;
  recordId: number;
  oldData?: string;
  newData?: string;
  ipAddress?: string;
  userAgent?: string;
  createdAt: string;
}

export interface AuditHistoryPage {
  content: AuditHistory[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface AuditHistorySummary {
  total: number;
  create: number;
  update: number;
  delete: number;
}

// ============================================================
// Conflict Types
// ============================================================
export interface ConflictCheckResponse {
  periodId: number;
  hasConflicts: boolean;
  totalConflicts: number;
  conflicts: ConflictDetail[];
  coverageGaps: string[];
  hasCoverageGaps: boolean;
  totalCoverageGaps: number;
}

export interface ConflictDetail {
  scheduleId: number;
  staffName: string;
  workDate: string;
  shiftTypeId: string;
  shiftTypeName: string;
  conflictReasons: string[];
  /** Period ID — required for replacement suggestion in ConflictResolutionModal */
  periodId?: number;
  /** Original staff ID — used to exclude current assignee from replacement candidates */
  originalStaffId?: number;
  /** Legacy shape compatibility for ConflictItem consumers */
  id?: string;
  type?: string;
  severity?: "Chặn lưu" | "Cảnh báo";
}

export interface CompensationDay {
  id: number;
  staffId: number;
  staffName: string;
  staffCode?: string;
  scheduleId?: number;
  shiftDate: string;
  compensationDate: string;
  note?: string;
}

// ============================================================
// Bulk Schedule Types
// ============================================================
export interface BulkScheduleResultEntry {
  workDate: string;
  staffId: number;
  staffName?: string;
  scheduleId: number | null;
  error: string | null;
}

export interface BulkScheduleResponse {
  totalRequested: number;
  successCount: number;
  failureCount: number;
  results: BulkScheduleResultEntry[];
}

// ============================================================
// Publish Dry-Run Types
// ============================================================
export interface PublishDryRunConflictDetail {
  scheduleId: number;
  staffName: string;
  workDate: string;
  shiftTypeId: string;
  shiftTypeName: string;
  conflictReasons: string[];
}

export interface CoverageReportEntry {
  date: string;
  shiftTypeId: string;
  shiftTypeName: string;
  required: number;
  assigned: number;
  coverageRate: number;
}

export interface PublishDryRunResponse {
  periodId: number;
  periodName: string;
  hasConflicts: boolean;
  conflictCount: number;
  conflicts: PublishDryRunConflictDetail[];
  hasCoverageGaps: boolean;
  coverageGaps: string[];
  staffingCoverage: {
    totalRequired: number;
    totalAssigned: number;
    overallCoverageRate: number;
    entries: CoverageReportEntry[];
  };
  canPublish: boolean;
}

export interface RoleMatrixRole {
  id: number;
  name: string;
  description: string | null;
  isActive: boolean;
}

export interface RoleMatrixPermission {
  id: number;
  name: string;
  description: string | null;
}

export interface RoleMatrixEntry {
  roleId: number;
  roleName: string;
  permissionId: number;
  permissionName: string;
  granted: boolean;
}

export interface RolePermissionMatrix {
  roles: RoleMatrixRole[];
  permissions: RoleMatrixPermission[];
  matrix: RoleMatrixEntry[];
}

// ============================================================
// Staff Shift Statistics Types (M02-F05, M04-F05, M05-F05)
// ============================================================
export interface StaffShiftStatistics {
  staffId: number;
  staffName: string;
  staffCode: string;
  specialtyName: string | null;
  totalShifts: number;
  L01Count: number;
  L02Count: number;
  L03Count: number;
  L04Count: number;
  totalHours: number;
  workloadPercentage: number;
}

// ============================================================
// Config Profile Types (stubs — backend ConfigController CRUD pending)
// ============================================================
export type ConfigProfileCategory =
  | "GENERAL"
  | "ALGORITHM"
  | "FAIRNESS"
  | "COVERAGE"
  | "EMERGENCY"
  | "HOLIDAY"
  | "TESTING"
  | "L04";

export interface ConfigProfile {
  id: number;
  profileKey?: string;
  nameVi?: string;
  nameEn?: string;
  name?: string; // fallback
  category: ConfigProfileCategory;
  description?: string;
  icon?: string;
  tags?: string[];
  enabled: boolean;
  configJson?: Record<string, unknown>;
  config?: Record<string, unknown>; // alternate field used by ProfileHealthBadge
  createdAt: string;
  updatedAt: string;
  isFavorite?: boolean;
  isHealthy?: boolean;
  healthIssues?: string[];
}

export interface CreateProfileRequest {
  nameVi: string;
  nameEn?: string;
  description?: string;
  category: ConfigProfileCategory;
  icon?: string;
  tags?: string[];
  enabled?: boolean;
  configJson?: Record<string, unknown>;
}
