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
  crossSpecialty?: boolean;
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
// Explain / AI Explanation Types
// ============================================================
export interface ExplainQueryRequest {
  slotId: number;
  staffId?: number;
  question?: string;
}

export interface AssignmentExplanation {
  slotId: number;
  workDate: string;
  shiftTypeId: string;
  chosenStaffId: number;
  chosenStaffName: string;
  staffName?: string;
  score: number;
  ranking: number;
  totalScore?: number;
  scoreBreakdown?: {
    coverageScore?: number;
    fairnessScore?: number;
    preferenceScore?: number;
    coverage?: number;
    fairness?: number;
    total?: number;
  };
  selectionReasons?: Array<{ reason: string } | string>;
  hardConstraints?: Array<{ id: string; name: string; constraintName?: string; satisfied?: boolean }>;
  alternatives: Array<{ staffId: number; staffName: string; score: number; reason: string }>;
  naturalLanguageExplanation?: string;
}

export interface WhyNotExplanation {
  staffId: number;
  staffName: string;
  slotId: number;
  workDate: string;
  shiftTypeId: string;
  reasons: string[];
  blockedBy: Array<{ rule: string; detail: string }>;
  rejectionReasons?: Array<{
    constraintId: string;
    constraintName?: string;
    description?: string;
    isBlocking: boolean;
    penalty?: number;
    detail?: string;
  }>;
  score?: number;
  rank?: number;
  scoreImpact?: number;
  constraintChain?: Array<{
    constraintId: string;
    description: string;
    weight?: number;
    detail?: string;
  }>;
  selectedAlternative?: { staffId: number; staffName: string; score?: number };
  naturalLanguageExplanation?: string;
}

export interface CandidateRankingExplanation {
  slotId: number;
  workDate: string;
  shiftTypeId: string;
  totalCandidates?: number;
  acceptedCount?: number;
  rejectedCount?: number;
  rankings?: Array<{
    staffId: number;
    staffName: string;
    rank: number;
    score: number;
    reasons: string[];
    selected?: boolean;
    rejected?: boolean;
    primaryConstraint?: string;
  }>;
  candidates: Array<{
    staffId: number;
    staffName: string;
    rank: number;
    score: number;
    reasons: string[];
    selected?: boolean;
    rejected?: boolean;
    primaryConstraint?: string;
  }>;
  summary?: {
    bestStaffId?: number;
    bestStaffName?: string;
    averageScore?: number;
    coverage?: number;
    highestScore?: number;
    lowestScore?: number;
    averageBranchingFactor?: number;
  };
}

export interface ReplayExplanation {
  iteration: number;
  moveType: string;
  beforeScore: number;
  afterScore: number;
  changes: Array<{ slotId: number; oldStaffId: number; newStaffId: number; date: string }>;
  constraintChanges?: Array<{
    rule: string;
    constraintId?: string;
    constraintName?: string;
    improved: boolean;
    delta: number;
  }>;
  accepted: boolean;
  reason: string;
  rejectionReason?: string;
  acceptanceReason?: string;
  naturalLanguageExplanation?: string;
  staffName?: string;
  targetStaffName?: string;
  scoreBreakdown?: {
    coverageDelta?: number;
    fairnessDelta?: number;
    totalDelta?: number;
  };
}

export interface ExplainQueryResponse {
  query: ExplainQueryRequest;
  explanations: AssignmentExplanation[];
  whyNot: WhyNotExplanation[];
  candidateRanking: CandidateRankingExplanation[];
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

// ============================================================
// Governance Types
// ============================================================
export interface GovernancePolicy {
  id: number;
  name: string;
  description: string;
  policyType: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ApprovalRequest {
  id: number;
  entityType: string;
  entityId: number;
  requestedBy: string;
  submittedByName?: string;
  requestedAt: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  description?: string;
  createdAt?: string;
  dueDate?: string;
  title?: string;
  priority?: string;
  ApprovalStatus?: string;
}

export type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED" | "DRAFT" | "SUBMITTED" | "UNDER_REVIEW" | "CHANGES_REQUESTED" | "CANCELLED" | "EXPIRED" | "APPLIED";

export interface AuditEvent {
  id: number;
  eventType: string;
  entityType: string;
  entityId: number;
  performedBy: string;
  performedAt: string;
  details: Record<string, unknown>;
  timestamp?: string;
  userName?: string;
  userRole?: string;
  action?: string;
  previousValue?: string;
  newValue?: string;
  reason?: string;
}

export interface AuditSummary {
  totalEvents: number;
  todayEvents: number;
  weekEvents?: number;
  monthEvents?: number;
  byEntityType: Record<string, number>;
  byAction: Record<string, number>;
  recentEvents?: AuditEvent[];
}

export interface AuditTimelineEvent {
  id: number;
  timestamp: string;
  userName: string;
  userRole: string;
  action: string;
  entityType: string;
  entityId: number;
  description: string;
  previousValue?: string;
  newValue?: string;
  reason?: string;
}

export interface ConfigVersion {
  id: number;
  configKey: string;
  version: number;
  versionNumber?: number;
  configJson: Record<string, unknown>;
  changedBy: string;
  changedByName?: string;
  changedAt: string;
  createdAt?: string;
  createdBy?: string;
  createdByName?: string;
  changeNote: string;
  changeComment?: string;
  source?: string;
  checksum?: string;
  active?: boolean;
  locked?: boolean;
  configSnapshot?: Record<string, unknown>;
}

export interface ConfigVersionDiff {
  key: string;
  changeType: "ADDED" | "REMOVED" | "MODIFIED";
  oldValue?: string | null;
  newValue?: string | null;
}

// ============================================================
// Benchmark Types
// ============================================================
export interface BenchmarkScenario {
  id: number;
  name: string;
  description: string;
  staffCount: number;
  requirementCount: number;
  conflictCount: number;
  coverageTarget: number;
  createdAt: string;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
}

export interface BenchmarkResult {
  scenarioId: number;
  algorithmType: string;
  executionTimeMs: number;
  coverageRate: number;
  balanceScore: number;
  hardViolations: number;
  softViolations: number;
  totalScore: number;
  grade: string;
}

// ============================================================
// Digital Twin / Sandbox Types
// ============================================================
// Sandbox / Digital Twin Types
export interface SandboxSession {
  id: number;
  sessionKey: string;
  name: string;
  status: SandboxStatus;
  configKey?: string;
  sourcePeriodId?: number;
  initialScore?: number;
  bestScore?: number;
  createdAt: string;
  updatedAt?: string;
  completedAt?: string | null;
  coverageRate?: number;
  fairnessCv?: number;
  violations?: number;
  runtimeSeconds?: number;
  iterations?: number;
}

export interface SandboxSnapshot {
  id: number;
  sessionKey: string;
  iteration: number;
  iterations?: number;
  scoreSnapshot: Record<string, unknown>;
  score?: number;
  coverageRate?: number;
  fairnessCv?: number;
  violations?: number;
  createdAt: string;
}

// What-If Scenario Types
export type ScenarioStatus = "DRAFT" | "PENDING" | "READY" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";

export interface ScenarioResponse {
  id: number;
  name: string;
  description: string;
  status: ScenarioStatus;
  createdAt: string;
  completedAt?: string;
  metrics?: Record<string, unknown>;
  sessionKey?: string;
  baseline?: boolean;
  results?: {
    coverage?: number;
    balance?: number;
    fairness?: number;
    violations?: number;
    score?: number;
    executionTime?: number;
  };
  simulationDurationMs?: number;
}

export interface ScenarioComparison {
  scenario1Id: number;
  scenario2Id: number;
  scenario1Name: string;
  scenario2Name: string;
  differences: Array<{ key: string; value1: unknown; value2: unknown }>;
  scenario1?: Record<string, unknown>;
  scenario2?: Record<string, unknown>;
  metrics?: ScenarioMetrics;
  changes?: ScenarioMetrics;
  recommendation?: string;
}

// ============================================================
// Sandbox / Digital Twin Types
// ============================================================
export interface ReplayScoreSummary {
  iterations: number;
  scores: Array<{ iteration: number; score: number }>;
  hardViolations: number[];
  softViolations: number[];
}

export interface ScenarioMetrics {
  baselineScore?: number;
  comparedScore?: number;
  scoreDelta?: number;
  baselineViolations?: number;
  comparedViolations?: number;
  violationsDelta?: number;
  baselineRuntime?: number;
  comparedRuntime?: number;
  runtimeDelta?: number;
  baselineCoverage?: number;
  comparedCoverage?: number;
  coverageDelta?: number;
  baselineFairness?: number;
  comparedFairness?: number;
  fairnessDelta?: number;
  violations?: { impact: string };
  coverage?: { impact: string };
  fairness?: { impact: string };
  [key: string]: unknown;
}

// ============================================================
// Digital Twin - Compare Types
// ============================================================
export interface SandboxTimeline {
  events: TimelineEvent[];
  iterations?: Array<{
    iteration: number;
    score: number;
    accepted: boolean;
    moveType?: string;
  }>;
}

export interface SandboxPromotionDiff {
  addedSchedules: Array<{ staffName: string; shiftTypeName: string; date: string }>;
  removedSchedules: Array<{ staffName: string; shiftTypeName: string; date: string }>;
  scoreDelta: number;
  coverageDelta: number;
  fairnessDelta: number;
  totalChanges?: number;
  added?: Array<{ staffName: string; shiftTypeName: string; date: string }>;
  modified?: Array<{ staffName: string; shiftTypeName: string; date: string }>;
  removed?: Array<{ staffName: string; shiftTypeName: string; date: string }>;
}

export interface TimelineIterationPoint {
  iteration: number;
  score: number;
  hardViolations?: number;
  softViolations?: number;
  moveType?: string;
  accepted?: boolean;
}

// ============================================================
// Digital Twin - Decision Types
// ============================================================
export interface DecisionNode {
  id: string;
  iteration: number;
  nodeType: "ROOT" | "ACCEPT" | "REJECT" | "BRANCH";
  label: string;
  score: number;
  hardViolations: number;
  softViolations: number;
  moveDescription?: string;
  x?: number;
  y?: number;
  status?: string;
  candidateStaffName?: string;
  slotId?: number;
  violatedConstraint?: string;
  candidateStaffId?: number;
  rejectionReason?: string;
  scoreDelta?: number;
  coverageDelta?: number;
  fairnessDelta?: number;
  depth?: number;
}

export interface DecisionEdge {
  id: string;
  source: string;
  target: string;
  label: string;
  accepted: boolean;
  scoreDelta: number;
  fromId?: string;
  toId?: string;
  type?: string;
}

export interface DecisionGraph {
  nodes: DecisionNode[];
  edges: DecisionEdge[];
  totalIterations: number;
  finalScore: number;
  statistics?: GraphStatistics;
}

export interface GraphStatistics {
  totalIterations: number;
  totalAccepts: number;
  totalRejects: number;
  acceptanceRate: number;
  avgScoreGain: number;
  bestIteration: number;
  worstIteration: number;
  totalNodes?: number;
  totalEdges?: number;
  totalCandidates?: number;
  totalAccepted?: number;
  totalRejected?: number;
  averageBranchingFactor?: number;
  maxDepth?: number;
  maxCandidatesPerIteration?: number;
  rejectionReasons?: Record<string, number>;
}

// ============================================================
// Digital Twin - Live Types
// ============================================================
export interface TimelineEvent {
  id: string;
  timestamp: string;
  eventType: TimelineEventType;
  message: string;
  iteration?: number;
  score?: number;
  hardViolations?: number;
  softViolations?: number;
  accepted?: boolean;
  coverage?: number;
  fairnessCv?: number;
  moveType?: string;
  staffName?: string;
  scoreDelta?: number;
  rejectionReason?: string;
  details?: Record<string, unknown>;
}

export type TimelineEventType =
  | "STARTED"
  | "ITERATION_START"
  | "ITERATION_END"
  | "MOVE_PROPOSED"
  | "MOVE_EVALUATING"
  | "MOVE_ACCEPTED"
  | "MOVE_REJECTED"
  | "SCORE_IMPROVED"
  | "NO_IMPROVEMENT"
  | "BEST_UPDATED"
  | "TABU_HIT"
  | "DIVERSIFIED"
  | "EARLY_STOP"
  | "COMPLETED"
  | "FAILED"
  | "PAUSED"
  | "RESUMED"
  | "SNAPSHOT";

// ============================================================
// Digital Twin - Replay Types
// ============================================================
export interface SandboxReplayFrame {
  iteration: number;
  score: number;
  stepType?: string;
  moveType?: string;
  slotId?: number;
  staffId?: number;
  staffName?: string;
  targetStaffId?: number;
  targetStaff?: string;
  timestamp: string;
  accepted: boolean;
  reason?: string;
  scoreDelta?: number;
  coverage?: number;
  fairnessCv?: number;
  hardViolations?: number;
  softViolations?: number;
  durationMs?: number;
  staff?: { name: string; staffCode: string };
}

export interface ReplayScoreSummary {
  iterations: number;
  scores: Array<{ iteration: number; score: number }>;
  hardViolations: number[];
  softViolations: number[];
}

// ============================================================
// Sandbox Status with all variants
// ============================================================
export type SandboxStatus =
  | "CREATED"
  | "CLONING"
  | "READY"
  | "RUNNING"
  | "PAUSED"
  | "COMPLETED"
  | "FAILED"
  | "PROMOTED"
  | "CANCELLED"
  | "EXPIRED"
  | "DELETED";

export type SimulationMode = "STEPPING" | "FULL" | "FAST_FORWARD" | "SINGLE_RUN" | "COMPARE" | "SENSITIVITY" | "WHAT_IF";

// ── Configuration Calculator ─────────────────────────────────────────

export interface ConfigCalculatorResponse {
  mode: number;
  feasible: boolean;
  message?: string;
  periodId?: number;
  periodName?: string;
  periodDays: number;
  totalStaff: number;
  totalRequirement: number;
  totalCapacity: number;
  totalAssigned: number;
  perShiftType?: ShiftTypeCapacity[];
  bottlenecks?: Bottleneck[];
  holidayImpact?: HolidayImpact;
  algorithmInfo?: AlgorithmInfo;
  recommendedConfig?: Record<string, unknown>;
  configChanges?: ConfigChange[];
  recommendedAlgorithm?: string;
  expectedCoverage?: number;
  expectedFairness?: number;
}

export interface ShiftTypeCapacity {
  shiftType: string;
  requirement: number;
  maxPossible: number;
  assigned: number;
  eligibleStaffCount: number;
  avgDomainSize: number;
  minDomainSize: number;
  bottleneckCount: number;
  perSpecialty?: Record<string, number>;
}

export interface Bottleneck {
  type: string;
  shiftType: string;
  specialty?: string;
  severity: string;
  message: string;
  suggestion?: string;
}

export interface HolidayImpact {
  holidayDaysCount: number;
  skippedShifts?: Record<string, number>;
  mode: string;
}

export interface AlgorithmInfo {
  type: string;
  executionTimeMs: number;
  terminatedBy: string;
  varsExplored: number;
  assignmentsMade: number;
}

export interface ConfigChange {
  field: string;
  fromValue: unknown;
  toValue: unknown;
  reason: string;
}
