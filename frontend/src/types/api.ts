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
  workDate: string;
  staff: { id: number; fullName: string };
  shiftType: { id: string; name: string; isOvernight: boolean };
  requirementId?: number;
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

export interface LeaveRequest {
  id: number;
  staffId: number;
  staffName?: string;
  startDate: string;
  endDate: string;
  reason?: string;
  status: LeaveStatus;
  reviewedBy?: number;
  reviewerName?: string;
  reviewNote?: string;
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

export interface AutoSchedulePreview {
  proposedSchedules: Schedule[];
  unassignedDays: string[];
  conflictWarnings: string[];
  coverageRate: number;
  balanceScore: number;
}

export interface AutoScheduleResponse {
  scheduleCount: number;
  unassignedDays: string[];
  conflictCount: number;
  executionTimeMs: number;
  coverageRate: number;
  balanceScore: number;
  schedules?: Schedule[];
}

export interface AlgorithmMetrics {
  id: number;
  algorithmType: string;
  executionTimeMs: number;
  coverageRate: number;
  balanceScore: number;
  conflictCount: number;
  createdAt: string;
}

// ============================================================
// Shift Requirement Types
// ============================================================
export interface ShiftRequirement {
  id: number;
  periodId: number;
  shiftTypeId: string;
  requiredStaffCount: number;
  requiredDate: string;
  createdAt: string;
  updatedAt: string;
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
  templateName: string;
  description?: string;
  isActive: boolean;
  scheduleCount: number;
  createdAt: string;
  updatedAt: string;
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
  oldValues?: string;
  newValues?: string;
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
}

export interface ConflictDetail {
  scheduleId: number;
  staffName: string;
  workDate: string;
  shiftTypeId: string;
  shiftTypeName: string;
  conflictReasons: string[];
}
