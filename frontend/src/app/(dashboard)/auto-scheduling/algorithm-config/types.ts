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

export type BalanceStrategy = "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR";

export type RuntimeConfig = {
  /** Từ AutoGenConfig response — có cho phép auto-gen không */
  enabled?: boolean;
  weekendWeight: number;
  overnightRecoveryHours: number;
  greedyCoverageThreshold: number;
  balanceScoreMin: number;
  minStaffPerShift: number;
  maxStaffPerShift: number;
  minShiftsPerStaff: number;
  maxShiftsPerStaff: number;
  l01MinPerDay?: number; l02MinPerDay?: number; l03MinPerDay?: number; l04MinPerDay?: number;
  l01MaxPerDay?: number; l02MaxPerDay?: number; l03MaxPerDay?: number; l04MaxPerDay?: number;
  l01MaxPerWeek?: number; l02MaxPerWeek?: number; l03MaxPerWeek?: number; l04MaxPerWeek?: number;
  holidayMode?: string;
  removedShiftTypes?: string[];
  // Cross-specialty cho L01
  l01CrossSpecialty?: boolean;
  l01CrossSpecialtyRatio?: number;
  l01AllowedSpecialties?: string[];
  l01BalanceStrategy?: BalanceStrategy;
  // Cross-specialty cho L02
  l02CrossSpecialty?: boolean;
  l02CrossSpecialtyRatio?: number;
  l02AllowedSpecialties?: string[];
  l02BalanceStrategy?: BalanceStrategy;
  // Cross-specialty cho L03
  l03CrossSpecialty?: boolean;
  l03CrossSpecialtyRatio?: number;
  l03AllowedSpecialties?: string[];
  l03BalanceStrategy?: BalanceStrategy;
  // Cross-specialty cho L04
  l04CrossSpecialty?: boolean;
  l04CrossSpecialtyRatio?: number;
  l04AllowedSpecialties?: string[];
  l04BalanceStrategy?: BalanceStrategy;
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
  removedShiftTypes: string[];
  // Cross-specialty cho L01
  l01CrossSpecialty?: boolean;
  l01CrossSpecialtyRatio?: number;
  l01AllowedSpecialties?: string[];
  l01BalanceStrategy?: BalanceStrategy;
  // Cross-specialty cho L02
  l02CrossSpecialty?: boolean;
  l02CrossSpecialtyRatio?: number;
  l02AllowedSpecialties?: string[];
  l02BalanceStrategy?: BalanceStrategy;
  // Cross-specialty cho L03
  l03CrossSpecialty?: boolean;
  l03CrossSpecialtyRatio?: number;
  l03AllowedSpecialties?: string[];
  l03BalanceStrategy?: BalanceStrategy;
  // Cross-specialty cho L04
  l04CrossSpecialty?: boolean;
  l04CrossSpecialtyRatio?: number;
  l04AllowedSpecialties?: string[];
  l04BalanceStrategy?: BalanceStrategy;
};

export type TabKey = "config" | "history" | "audit" | "reference";

/** Keys mà auto-gen payload ghi đè runtime config khi load */
export const AUTO_GEN_OVERRIDE_KEYS = new Set<string>([
  "enabled",
  "holidayMode",
  "removedShiftTypes",
	  "l01MinPerDay", "l01MaxPerDay", "l01MaxPerWeek",
	  "l02MinPerDay", "l02MaxPerDay", "l02MaxPerWeek",
	  "l03MinPerDay", "l03MaxPerDay", "l03MaxPerWeek",
	  "l04MinPerDay", "l04MaxPerDay", "l04MaxPerWeek",
  // L01 cross-specialty
  "l01CrossSpecialty",
  "l01CrossSpecialtyRatio",
  "l01AllowedSpecialties",
  "l01BalanceStrategy",
  // L02 cross-specialty
  "l02CrossSpecialty",
  "l02CrossSpecialtyRatio",
  "l02AllowedSpecialties",
  "l02BalanceStrategy",
  // L03 cross-specialty
  "l03CrossSpecialty",
  "l03CrossSpecialtyRatio",
  "l03AllowedSpecialties",
  "l03BalanceStrategy",
  // L04 cross-specialty
  "l04CrossSpecialty",
  "l04CrossSpecialtyRatio",
  "l04AllowedSpecialties",
  "l04BalanceStrategy",
]);

/** Map snake_case param key (URL/draft) sang camelCase RuntimeConfig field */
export const PARAM_KEY_TO_CFG: Record<string, keyof RuntimeConfig> = {
  greedy_coverage_threshold: "greedyCoverageThreshold",
  balance_score_min: "balanceScoreMin",
  weekend_weight: "weekendWeight",
  overnight_recovery_hours: "overnightRecoveryHours",
  min_staff_per_shift: "minStaffPerShift",
  max_staff_per_shift: "maxStaffPerShift",
  min_shifts_per_staff: "minShiftsPerStaff",
  max_shifts_per_staff: "maxShiftsPerStaff",
  holiday_mode: "holidayMode",
};

export const LEGACY_AUTO_GEN_KEYS = new Set<string>([
  "auto_compensation_enabled",
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