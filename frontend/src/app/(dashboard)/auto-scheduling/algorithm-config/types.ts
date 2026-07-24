export type ConfigEntry = {
  paramKey: string;
  paramValue: string;
  valueType: "STRING" | "NUMBER" | "BOOLEAN" | "JSON";
  description: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
};

export type EditingConfig = Partial<Pick<ConfigEntry, "paramValue" | "description">>;

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

export type TabKey = "config" | "history" | "audit" | "reference";

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

/** Map snake_case param key (URL/draft) sang camelCase RuntimeConfig field */
export const PARAM_KEY_TO_CFG: Record<string, keyof RuntimeConfig> = {
  greedy_coverage_threshold: "greedyCoverageThreshold",
  balance_score_min: "balanceScoreMin",
  weekend_weight: "weekendWeight",
  overnight_recovery_hours: "overnightRecoveryHours",
  max_staff_per_shift: "maxStaffPerShift",
  max_shifts_per_staff: "maxShiftsPerStaff",
  max_shifts_per_day: "maxShiftsPerDay",
  auto_adjust_config: "autoAdjustConfig",
  holiday_mode: "holidayMode",
  scorer_coverage_weight: "coverageWeight",
  scorer_fairness_weight: "fairnessWeight",
  scorer_constraint_weight: "constraintWeight",
  scorer_pass_threshold: "passThreshold",
  scorer_hard_violation_penalty: "hardViolationPenalty",
  scorer_soft_violation_penalty: "softViolationPenalty",
  scorer_target_cv: "targetCv",
  scorer_worst_cv: "worstCv",
  rebalance_rounds_total: "rebalanceRoundsTotal",
  rebalance_rounds_per_type: "rebalanceRoundsPerType",
  rebalance_rounds_eg: "rebalanceRoundsEg",
	  rebalance_rounds_post_save: "rebalanceRoundsPostSave",
	  beam_width: "beamWidth",
	};

export const LEGACY_AUTO_GEN_KEYS = new Set<string>([
  "auto_generate_requirements",
  "auto_gen_holiday_mode",
  "auto_gen_l01_per_day",
  "auto_gen_l02_per_day",
  "auto_gen_l03_per_day",
  "auto_gen_l04_per_day",
  "auto_gen_l01_per_week",
  "auto_gen_l02_per_week",
  "auto_gen_l03_per_week",
  "auto_gen_l04_per_week",
]);