/**
 * Frontend types matching backend ConfigMetadata/ConfigService types.
 * These types are consumed from GET /api/v1/config/metadata.
 */

/** API response wrapper — matches Spring ApiResponse<T>. */
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}

/** Render type for UI component selection. */
export type RenderType = "number" | "slider" | "toggle" | "select" | "multiselect" | "chip_group";

/** Validation severity level. */
export type ValidationSeverity = "ERROR" | "WARNING" | "INFO";

/** Config category matching ConfigCategory enum. */
export type ConfigCategoryKey =
  | "GENERAL"
  | "ALGORITHM"
  | "ACCEPTANCE"
  | "FAIRNESS"
  | "COVERAGE"
  | "L04"
  | "CONSTRAINTS"
  | "PERFORMANCE";

/** A single allowed value option for select/chip_group fields. */
export interface ConfigOption {
  value: string;
  label: string;
}

/** Metadata for a single configuration field.
 * Matches ConfigService.FieldMetadataDto from backend.
 */
export interface FieldMetadata {
  fieldPath: string;
  label: string;
  description: string;
  category: ConfigCategoryKey;
  renderType: RenderType;
  min: number;
  max: number;
  step: number;
  defaultValue: string;
  required: boolean;
  visibleWhen: string | null;
  editableWhen: string | null;
  validationSeverity: ValidationSeverity;
  allowedValues: ConfigOption[];
}

/** Metadata for a category group.
 * Matches ConfigService.CategoryMetadata from backend.
 */
export interface CategoryMetadata {
  categoryKey: ConfigCategoryKey;
  labelVi: string;
  labelEn: string;
  sortOrder: number;
  fields: FieldMetadata[];
}

/** Violation from validation result. */
export interface ValidationViolation {
  fieldPath: string;
  message: string;
  severity: ValidationSeverity;
}

/** Full validation response from POST /api/v1/config/validate. */
export interface ValidationResponse {
  valid: boolean;
  errorCount: number;
  warningCount: number;
  infoCount: number;
  errors: ValidationViolation[];
  warnings: ValidationViolation[];
  infos: ValidationViolation[];
}

/** Preset configuration. */
export interface ConfigPreset {
  key: string;
  labelVi: string;
  labelEn: string;
  coverageTarget: number;
  balanceScoreMin: number;
  weekendWeight: number;
}

/** Diff entry between two configs. */
export interface ConfigDiff {
  fieldPath: string;
  oldValue: string;
  newValue: string;
}

/** Config domain — all fields.
 * Matches ConfigController.ConfigDto from backend.
 */
export interface ConfigDomain {
  enabled: boolean;
  holidayMode: string;
  removedShiftTypes: string[];
  maxIterations: number;
  neighborhoodSize: number;
  tabuTenureMin: number;
  tabuTenureMax: number;
  maxNoImproveIterations: number;
  relativeImprovementThreshold: number;
  diversifyAfterIterations: number;
  acceptanceStrategy: string;
  saInitialTemperature: number;
  saCoolingRate: number;
  saTemperatureMin: number;
  laMemorySize: number;
  gdInitialLevel: number;
  gdDecayRate: number;
  gdMinLevel: number;
  cvTarget: number;
  cvWorst: number;
  weekendWeight: number;
  l01MinPerDay: number;
  l01MaxPerDay: number;
  l01MinPerWeek: number;
  l01MaxPerWeek: number;
  l02MinPerDay: number;
  l02MaxPerDay: number;
  l02MinPerWeek: number;
  l02MaxPerWeek: number;
  l03MinPerDay: number;
  l03MaxPerDay: number;
  l03MinPerWeek: number;
  l03MaxPerWeek: number;
  l04MinPerDay: number;
  l04MaxPerDay: number;
  l04MinPerWeek: number;
  l04MaxPerWeek: number;
  l04CrossSpecialtyEnabled: boolean;
  l04CrossSpecialtyRatio: number;
  l04AllowedSpecialties: string[];
  l04BalanceStrategy: string;
  overnightRecoveryHours: number;
  greedyCoverageThreshold: number;
  minStaffPerShift: number;
  maxStaffPerShift: number;
  minShiftsPerStaff: number;
  maxShiftsPerStaff: number;
  timeLimitSeconds: number;
  candidateListSize: number;
}

/** Dirty state for a single field. */
export interface FieldDirtyState {
  original: unknown;
  current: unknown;
  isDirty: boolean;
}

/** Config context state. */
export interface ConfigContextState {
  metadata: CategoryMetadata[] | null;
  config: ConfigDomain | null;
  validation: ValidationResponse | null;
  dirtyFields: Map<string, FieldDirtyState>;
  isLoading: boolean;
  isSaving: boolean;
  error: string | null;
}
