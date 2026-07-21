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
  minStaffPerShift: number;
  maxStaffPerShift: number;
  minShiftsPerStaff: number;
  maxShiftsPerStaff: number;
  maxShiftsPerDay?: number;
  autoAdjustConfig?: boolean;
  l01MinPerDay?: number; l02MinPerDay?: number; l03MinPerDay?: number; l04MinPerDay?: number;
  l01MaxPerDay?: number; l02MaxPerDay?: number; l03MaxPerDay?: number; l04MaxPerDay?: number;
  l01MinPerWeek?: number; l02MinPerWeek?: number; l03MinPerWeek?: number; l04MinPerWeek?: number;
  l01MaxPerWeek?: number; l02MaxPerWeek?: number; l03MaxPerWeek?: number; l04MaxPerWeek?: number;
  holidayMode?: string;
  removedShiftTypes?: string[];
  l04CrossSpecialty?: boolean;
  l04CrossSpecialtyRatio?: number;
  l04AllowedSpecialties?: string[];
  l04BalanceStrategy?: "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR";
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
  l04BalanceStrategy?: "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR";
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