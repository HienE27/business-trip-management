// ============================================================
// API Response Wrapper
// ============================================================
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
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
  username: string;
  fullName: string;
  phone?: string;
  email?: string;
  position?: string;
  specialty?: StaffSpecialty;
  maxShiftsPerMonth: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  roles: string[];
}

export interface StaffSearchParams {
  keyword?: string;
  specialtyId?: number;
  status?: string;
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
  createdAt: string;
  updatedAt: string;
}

export interface ScheduleRequest {
  periodId: number;
  workDate: string;
  staffId: number;
  shiftTypeId: string;
  requirementId?: number;
}

export interface ShiftRequirement {
  id: number;
  periodId: number;
  workDate: string;
  shiftType: { id: string; name: string };
  specialty: { id: number; name: string };
  requiredStaffCount: number;
  assignedStaffCount: number;
  note?: string;
  createdAt: string;
  updatedAt: string;
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
  maxIterations?: number;
  autoAssign?: boolean;
}

export interface AutoScheduleSummary {
  scheduleId: number | null;
  staffId: number;
  staffName: string;
  workDate: string;
  shiftTypeId: string;
  shiftTypeName: string;
}

export interface AutoScheduleResult {
  success: boolean;
  message: string;
  periodId: number;
  algorithmType: string;
  executionTimeMs: number;
  coverageRate: number;
  balanceScore: number;
  conflictCount: number;
  totalSchedulesCreated: number;
  schedules: AutoScheduleSummary[];
  executedAt: string;
  excludedStaffIds?: number[];
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
  periodId?: number;
  periodName?: string;
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
  dayOfWeek: number;
  shiftTypeId: string;
  shiftTypeName?: string;
  specialtyId?: number | null;
  specialtyName?: string | null;
  requiredStaffCount: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
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
  dayOfWeek: number;
  shiftTypeId: string;
  specialtyId?: number | null;
  requiredStaffCount: number;
}

// ============================================================
// Audit History Types
// ============================================================
export interface AuditHistory {
  id: number;
  userId: number;
  userName?: string;
  action: string;
  tableName: string;
  recordId: number;
  oldData?: string;
  newData?: string;
  ipAddress?: string;
  createdAt: string;
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
}

export interface CompensationDay {
  id: number;
  staffId: number;
  staffName: string;
  shiftDate: string;
  compensationDate: string;
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
