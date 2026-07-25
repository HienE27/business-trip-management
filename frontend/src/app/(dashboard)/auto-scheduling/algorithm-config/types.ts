export type RuntimeConfig = {
  weekendWeight: number;
  overnightRecoveryHours: number;
  greedyCoverageThreshold: number;
  balanceScoreMin: number;
  autoCompensationEnabled: boolean;
  maxStaffPerShift: number;
  maxShiftsPerStaff: number;
  maxShiftsPerDay?: number;
  autoAdjustConfig?: boolean;
  beamWidth?: number;
  coverageWeight?: number;
  fairnessWeight?: number;
  constraintWeight?: number;
  passThreshold?: number;
  hardViolationPenalty?: number;
  softViolationPenalty?: number;
  targetCv?: number;
  worstCv?: number;
  rebalanceRoundsTotal?: number;
  rebalanceRoundsPerType?: number;
  rebalanceRoundsEg?: number;
  rebalanceRoundsPostSave?: number;
  l01MinPerDay?: number; l02MinPerDay?: number; l03MinPerDay?: number; l04MinPerDay?: number;
  l01MaxPerDay?: number; l02MaxPerDay?: number; l03MaxPerDay?: number; l04MaxPerDay?: number;
  l01MinPerWeek?: number; l02MinPerWeek?: number; l03MinPerWeek?: number; l04MinPerWeek?: number;
  l01MaxPerWeek?: number; l02MaxPerWeek?: number; l03MaxPerWeek?: number; l04MaxPerWeek?: number;
  holidayMode?: string;
  removedShiftTypes?: string[];
  l04CrossSpecialty?: boolean;
  l04CrossSpecialtyRatio?: number;
  l04AllowedSpecialties?: string[];
  l01AllowedSpecialties?: string[];
  l02AllowedSpecialties?: string[];
  l03AllowedSpecialties?: string[];
  l04BalanceStrategy?: "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR";
  /** Chế độ sắp xếp: INTRA_TYPE (mặc định) hoặc WITH_INTER_BALANCE */
  arrangementMode?: "INTRA_TYPE" | "WITH_INTER_BALANCE";
  /** Inter-type balance weight (chỉ áp dụng khi arrangementMode = WITH_INTER_BALANCE). Default: 5.0. Cao hơn → mạnh hơn ép cân bằng L01/L02/L03. */
  interTypeWeight?: number;
  /** Target ca/người/tháng — input editable cho recommend. Persist vào DB. */
  l01TargetPerMonth?: number;
  l02TargetPerMonth?: number;
  l03TargetPerMonth?: number;
  l04TargetPerMonth?: number;
};

export type AlgorithmMetrics = {
  id: number;
  algorithmType: string;
  executionTimeMs: number;
  coverageRate: number;
  balanceScore: number;
  conflictCount: number;
  totalSchedulesCreated?: number;
  periodId?: number;
  periodName?: string;
  createdAt: string;
};

export type AutoGenConfigPayload = {
  enabled: boolean;
  holidayMode: string;
  l01MinPerDay: number; l02MinPerDay: number; l03MinPerDay: number; l04MinPerDay: number;
  l01MaxPerDay: number; l02MaxPerDay: number; l03MaxPerDay: number; l04MaxPerDay: number;
  l01MinPerWeek: number; l02MinPerWeek: number; l03MinPerWeek: number; l04MinPerWeek: number;
  l01MaxPerWeek: number; l02MaxPerWeek: number; l03MaxPerWeek: number; l04MaxPerWeek: number;
  removedShiftTypes: string[];
  l04CrossSpecialty?: boolean;
  l04CrossSpecialtyRatio?: number;
  l04AllowedSpecialties?: string[];
  l01AllowedSpecialties?: string[];
  l02AllowedSpecialties?: string[];
  l03AllowedSpecialties?: string[];
  l04BalanceStrategy?: "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR";
  /** Target ca/người/tháng — input editable cho recommend. Persist vào DB. */
  l01TargetPerMonth?: number;
  l02TargetPerMonth?: number;
  l03TargetPerMonth?: number;
  l04TargetPerMonth?: number;
};

export type TabKey = "config" | "history";

/** Keys mà auto-gen payload ghi đè runtime config khi load */
export const AUTO_GEN_OVERRIDE_KEYS = new Set<string>([
  "holidayMode",
  "removedShiftTypes",
  "l01MinPerDay", "l01MaxPerDay", "l01MinPerWeek", "l01MaxPerWeek",
  "l02MinPerDay", "l02MaxPerDay", "l02MinPerWeek", "l02MaxPerWeek",
  "l03MinPerDay", "l03MaxPerDay", "l03MinPerWeek", "l03MaxPerWeek",
  "l04MinPerDay", "l04MaxPerDay", "l04MinPerWeek", "l04MaxPerWeek",
  "l04CrossSpecialty",
  "l04CrossSpecialtyRatio",
  "l04AllowedSpecialties",
  "l04BalanceStrategy",
  "l01TargetPerMonth", "l02TargetPerMonth", "l03TargetPerMonth", "l04TargetPerMonth",
]);

// ── PlanningReport types (Phase 2) ──────────────────────────

export type CapacityAnalysis = {
  totalStaff: number;
  periodDays: number;
  totalDemand: number;
  maxCapacity: number;
  coverageCeiling: number;
};

export type ConstraintAnalysis = {
  leaveDensity: number;
  l01AdjacencyImpact: number;
  weeklyCapTightness: number;
  overallFeasibility: number;
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
};

export type FairnessAnalysis = {
  type: "INTRA_TYPE" | "INTER_TYPE" | "CROSS_SPECIALTY";
  label: string;
  feasibility: number;
  expectedFairness: number;
  coverageImpact: number;
  constraintRisk: string;
  description: string;
  starRating: number;
};

export type AlgorithmRecommendation = {
  algorithm: string;
  rationale: string;
  alternatives: string[];
};

export type ParameterRecommendation = {
  beamWidth: number;
  rebalanceRounds: number;
  weekendWeight: number;
  coverageWeight: number;
  fairnessWeight: number;
  constraintWeight: number;
  maxShiftsPerStaff: number;
  arrangementMode: string;
  /** Global config param key → relevant (true) or ignored (false) for the recommended algorithm */
  paramRelevance: Record<string, boolean>;
};

export type ExpectedResult = {
  coverage: number;
  constraintScore: number;
  fairnessScore: number;
  qualityScore: number;
};

export type PlanningReport = {
  capacity: CapacityAnalysis;
  constraint: ConstraintAnalysis;
  fairnessOptions: FairnessAnalysis[];
  algorithm: AlgorithmRecommendation;
  parameters: ParameterRecommendation;
  expected: ExpectedResult;
  warnings: string[];
};