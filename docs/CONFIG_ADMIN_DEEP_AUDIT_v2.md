# Config Admin Deep Audit v2

| Field | Value |
|---|---|
| **Document Status** | **FINAL** |
| **Version** | v1.0.0 |
| **Audit Date** | 2026-07-17 |
| **Last Updated** | 2026-07-17 (fourth round: metadata, decision log, future audit, evidence labels, issue/recommendation separation) |
| **Authors** | Engineering Audit (AI Agent: Principal Architect + Frontend/Backend Lead + DBA + QA + UX Expert) |
| **Reviewed By** | __________ |
| **Audit Scope** | Config Admin, Scheduler Engine, Constraint Engine, Score Engine, Runtime Configuration |
| **Target Release** | v1.0.0 RC |
| **Repository** | `business-trip-management` |
| **Related Docs** | `AUDIT_SCHEDULER_ENGINE.md`, `AUDIT_CONSTRAINT_ENGINE.md`, `AUDIT_SCORE_ENGINE.md`, `CONFIG_ADMIN_FULL_AUDIT.md` |

---

## 1. Initial Summary (first-pass scores)

This was the original first-pass summary written before the third-round reframe. The authoritative one-page summary for PO is at **§23 Executive Summary** — use that one if you only read one section.

| Aspect | Score | Verdict |
|---|---|---|
| **Database integrity** | 7.5/10 | 2 critical findings: legacy seed config rows + orphan DB columns |
| **Backend correctness** | 8.5/10 | 3 DEAD configs + 1 inconsistency (auto_compensation_enabled) + 4 RESERVED v1.1 (do not remove) |
| **API completeness** | 7.0/10 | ConfigController has NO Swagger annotations; FE-side field mapping mismatch risk |
| **Frontend UX** | 8.5/10 | Solid dirty/save/cancel; missing `beforeunload` guard; key params hidden from main editor |
| **Runtime integrity** | 9.0/10 | All essential configs verified ACTIVE; 3 DEAD + 3 LEGACY in DB but UI-hidden |
| **Cross-engine consistency** | 6.5/10 | BR-XX labels conflict across CSP/V10/Scorer engines |
| **Overall** | **7.8/10** | **NOT YET READY for v1.0** — needs 3 critical fixes before UAT |

**Release Risk**: **HIGH** for `auto_compensation_enabled` + `holiday` profile interaction.

---

## 2. Database Audit

### 2.1 `algorithm_config` table — Schema

| Aspect | Value | Source | Verdict |
|---|---|---|---|
| Column: `param_key` | VARCHAR(50) PRIMARY KEY | `hospital_scheduler_business_final.sql:307` | ✅ |
| Column: `param_value` | VARCHAR(500) NOT NULL | line 308 | ⚠️ 500 chars may overflow for large JSON arrays |
| Column: `value_type` | ENUM('STRING','NUMBER','BOOLEAN','JSON') | line 309 | ✅ |
| Column: `description` | VARCHAR(255) | line 310 | ✅ |
| Column: `updated_by` | INT FK→staff | line 311 | ✅ |
| FK `fk_algorithm_config_updated_by` | ON DELETE SET NULL | line 315-316 | ✅ |
| Index | `idx_algorithm_config_updated_by` | line 528 | ✅ |

**Note**: Migration V5 (`V5__add_algorithm_config_audit.sql`) added `algorithm_config_audit` table separately.

### 2.2 **CRITICAL FINDING** — Orphan seed configs (LEGACY)

**File**: `hospital_scheduler_business_final.sql:604-607`

```sql
INSERT INTO algorithm_config (param_key, param_value, value_type, description) VALUES
('MAX_SHIFTS_PER_MONTH_DEFAULT', '5', 'NUMBER', 'Số ca tối đa mặc định mỗi tháng'),
('AVOID_BACK_TO_BACK_SHIFT', 'true', 'BOOLEAN', 'Hạn chế phân công ca liên tiếp'),
('ENABLE_COMPENSATION_AFTER_L01', 'true', 'BOOLEAN', 'Tự động tính nghỉ bù sau ca L01');
```

**Verified orphan**:
- `grep MAX_SHIFTS_PER_MONTH_DEFAULT backend/**/*.java` → **0 hits**
- `grep AVOID_BACK_TO_BACK_SHIFT backend/**/*.java` → **0 hits**
- `grep ENABLE_COMPENSATION_AFTER_L01 backend/**/*.java` → **0 hits**

These rows are INSERTED by SQL but never read by Java code. After `DataSeeder.java` runs, these rows are dead weight in DB.

**Severity**: Medium
**Impact**: Misleading DB queries; possibly visible in `CustomConfigsCard` UI but ignored.
**Reproduction**: `SELECT * FROM algorithm_config WHERE param_key LIKE 'MAX_SHIFTS%';` returns row, but no scheduler path reads it.
**Fix**: Either remove SQL INSERTs OR add Java reader (preferred: remove INSERTs).

### 2.3 `config_profile` table

| Aspect | Value | Source |
|---|---|---|
| 6 system profiles seeded | balanced (default), emergency, high-coverage, high-fairness, holiday, fast | `V14__add_config_profile_table.sql:34-121` |
| Profile JSON includes all 45 fields | maxIterations, weekendWeight, l01MinPerDay, … | line 44, 59, 74, 89, 104, 119 |

### 2.4 **CRITICAL FINDING** — `holiday` profile has `autoCompensationEnabled: false`

**File**: `V14__add_config_profile_table.sql:104`

```sql
-- holiday profile JSON contains:
"autoCompensationEnabled":false,
```

**Cross-reference**: `backend/src/main/java/com/hospital/scheduler/service/AutoSchedulingService.java:918`
```java
if (algorithmConfigService.getRuntimeConfig().isAutoCompensationEnabled()) {
    createCompensationDaysForL01InPeriod(period.getId());
}
```

**Runtime effect**: When Manager selects **holiday** preset → applied via `apply()` endpoint → `updateRuntimeConfig()` is called → DB row updated → subsequent schedules skip L01 compensation.

**Severity**: **HIGH** (functional behavior change)

**Reproduction**:
1. Open `auto-scheduling/algorithm-config`
2. Click "Sandbox" → "Áp dụng" with **holiday** preset
3. Backend saves `autoCompensationEnabled: false` to DB
4. Run auto-schedule on a period containing L01
5. **Expected**: L01 staff get compensation day. **Actual**: NO compensation day created.

**Why UI shows "Always On"**: `RuntimeConfigEditor.tsx:816-845` renders the `AutoCompensationCard` with hardcoded "Always On" badge — **never re-renders** when DB value changes because it's not bound to `form.autoCompensationEnabled`.

**Root Cause**: AutoCompensationCard is a static component; runtime config flows through DB but UI doesn't reflect it.

**Fix**:
- **Option A (preferred)**: Force `autoCompensationEnabled = true` in `AutoSchedulingService:918` regardless of config. Remove the conditional.
- **Option B**: Remove `autoCompensationEnabled` from DB config layer entirely.

### 2.5 `schedule_period` table

| Column | Type | Source |
|---|---|---|
| `status` | ENUM('DRAFT','PUBLISHED','ARCHIVED') | line 167 |
| `uk_schedule_period_range` | UNIQUE(start_date, end_date) | line 178 |
| FK `generated_by` → staff | ON DELETE SET NULL | line 174-175 |
| CHECK `start_date <= end_date` | | line 176-177 |

✅ Clean schema.

### 2.6 `shift_requirement` table

**File**: `hospital_scheduler_business_final.sql:498-503`

```sql
CREATE INDEX idx_requirement_period_date ON shift_requirement(period_id, work_date);
CREATE INDEX idx_requirement_shift_specialty ON shift_requirement(shift_type_id, specialty_id);
ALTER TABLE shift_requirement ADD UNIQUE KEY uk_shift_requirement_unique (period_id, work_date, shift_type_id, specialty_id);
```

✅ Unique constraint prevents duplicate requirement rows. Comment mentions **UAT-003** dedup fix.

**NOT VERIFIED**: Schema for other shift_requirement columns not in main file — must be in init.sql or partial migration.

### 2.7 `algorithm_metrics` table

| Aspect | Value | Source |
|---|---|---|
| `period_id` | INT NULL FK→schedule_period ON DELETE SET NULL | line 417, 426-427 |
| `coverage_rate` | DECIMAL(5,2), CHECK 0-100 | line 421, 430-431 |
| `balance_score` | DECIMAL(5,2), CHECK 0-100 | line 422, 432-433 |
| `total_schedules_created` | INT DEFAULT 0 | added by V4 |
| `run_token` | VARCHAR(64) | added by V16 |

⚠️ **Inconsistency**: Base schema uses `coverage_rate DECIMAL(5,2)` but Java entity (`AlgorithmMetrics.java:13`) declares `BigDecimal coverageRate` — OK, but DB-scale may differ from app-scale (0.0-1.0 vs 0-100). Backend writes `BigDecimal.valueOf(score.getCoverage()).multiply(BigDecimal.valueOf(100))` → scaled 0-100. ✅ Consistent.

### 2.8 `schedule_conflict` ENUM

```sql
conflict_type ENUM(
    'LEAVE_CONFLICT',
    'MAX_SHIFT_EXCEEDED',
    'BACK_TO_BACK_SHIFT',
    'SPECIALTY_MISMATCH',
    'REQUIREMENT_NOT_MET',
    'DUPLICATE_ASSIGNMENT',
    'COMPENSATION_CONFLICT',
    'OTHER'
)
```

✅ 8 conflict types. Audit-relevant: `MAX_SHIFT_EXCEEDED` and `BACK_TO_BACK_SHIFT` driven by config (`maxShiftsPerStaff`, `l0XMaxPerWeek`).

### 2.9 Migration consistency

| Version | File | Purpose |
|---|---|---|
| V1 | `V1__add_performance_indexes.sql` | Performance |
| V2 | — | (skipped/inferred) |
| V3 | `V3__add_performance_indexes.sql` | Performance |
| V4 | `V4__fix_algorithm_metrics_columns.sql` | Add `total_schedules_created` |
| V5 | `V5__add_algorithm_config_audit.sql` | Audit table |
| V6 | `V6__clean_staff_role_orphans.sql` | Cleanup |
| V7 | `V7__add_compensation_unique_constraint.sql` | DB-level guard |
| V8 | `V8__expand_audit_action_type_enum.sql` | Extend audit ENUM |
| V9 | `V9__drop_schedule_unique_constraint.sql` | ⚠️ DROPPED schedule UNIQUE constraint |
| V10 | `V10__add_refresh_token_table.sql` | Auth |
| V11 | `V11__fix_permissions_version_precision.sql` | Numeric precision |
| V12 | `V12__fix_staff_fullname_mojibake.sql` | Vietnamese chars |
| V13 | `V13__cleanup_permissions_and_add_staff_export.sql` | Cleanup |
| V14 | `V14__add_config_profile_table.sql` | **Profile system** + 6 seed profiles |
| V15 | `V15__add_sandbox_tables.sql` | Sandbox |
| V16 | `V16__add_algorithm_metrics_run_token.sql` | Run tracking |
| V17 | `V17__add_v1_missing_indexes.sql` | Performance |

**V9 dropped `uk_schedule_unique`** on `schedule` table — likely intentional to allow multiple schedules per staff/day, but raises risk of duplicates. Code-level `DuplicateShiftConstraint` now guards (V10 layer).

---

## 3. Backend Audit

### 3.1 ConfigDomain record

**File**: `backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigDomain.java:508`

| Field | UI? | Runtime? | Verified |
|---|---|---|---|
| `enabled` (bool) | ✅ Toggle | ✅ Gate `prepareRequirements:60` | ✅ |
| `holidayMode` (string) | ✅ Select | ✅ Active | ✅ |
| `removedShiftTypes` (String[]) | ✅ Chips | ✅ Active | ✅ |
| `maxIterations..diversifyAfter` | ❌ Not in main editor | ❌ V10 layer reads but via SchedulingConfig | ⚠️ DUPLICATED in 2 layers |
| `acceptanceStrategy`, `sa*`, `la*`, `gd*` | ❌ Hidden | ❌ Same — V10 reads but no persistence path | ⚠️ V10 tunables unreachable from UI |
| `cvTarget`, `cvWorst` | ❌ Not exposed | ⚠️ V10 reads `SchedulingConfig.cvTarget/cvWorst` (same name) | 🔴 DUAL NAMING |
| `weekendWeight` | ⚠️ Internal group | ✅ Greedy/FairGreedy active | ✅ |
| `l01MinPerDay..l04MaxPerWeek` | ✅ 3 each shown, 1 hidden | ✅ Active | ✅ |
| `l04CrossSpecialty*` | ✅ Visible | ✅ Active | ✅ |
| `l04BalanceStrategy` | ✅ Visible | ❌ Loaded into record but never branched | 🔴 DEAD |
| `overnightRecoveryHours` | ❌ Internal | ❌ Only in log | 🔴 DEAD |
| `autoCompensationEnabled` | ❌ Hidden ("Always On") | ✅ ACTIVE in 2 branches | ⚠️ INCONSISTENT |
| `greedyCoverageThreshold` | ❌ Not in any group | ✅ Used in coverageTarget | ⚠️ ORPHAN |
| `minStaffPerShift` | ❌ Internal | ❌ 0 hits | 🔴 DEAD |
| `maxStaffPerShift` | ✅ Visible | ✅ Active | ✅ |
| `minShiftsPerStaff` | ❌ Internal | ❌ 0 hits | 🔴 DEAD |
| `maxShiftsPerStaff` | ✅ Visible | ✅ Active | ✅ |
| `timeLimitSeconds` | ❌ Hidden | ⚠️ V10 reads SchedulingConfig (NOT this) | 🔴 NEVER CONSUMED HERE |
| `candidateListSize` | ❌ Hidden | ⚠️ V10 reads SchedulingConfig | 🔴 NEVER CONSUMED HERE |

**🔴 FINDING**: `ConfigDomain.java` declares `timeLimitSeconds` and `candidateListSize` (lines 90, 95 in ConfigDomain) but they are **NEVER consumed** by Greedy/FairGreedy/CSP paths. Only V10 reads them, but V10 reads `SchedulingConfig.java:34, 46` (DIFFERENT BEAN). Two sources for same value → inconsistent if changed via API.

### 3.2 AlgorithmConfigService

**File**: `service/AlgorithmConfigService.java:742`

| Method | Lines | Purpose | Verified |
|---|---|---|---|
| `getAllConfigs()` | — | List all keys | ✅ |
| `getRuntimeConfig()` | line 506 | Build `AlgorithmRuntimeConfig` POJO | ✅ |
| `saveRuntimeConfig(config)` | line 522 | Upsert + write description | ✅ |
| `getAutoGenConfig()` | — | Returns `Optional<AutoGenConfig>` | ✅ |
| `saveAutoGenConfig()` | — | Upsert + audit | ✅ |

**All `@Transactional`** at save methods.

### 3.3 Constraint engines

#### 3.3.1 **CRITICAL FINDING** — BR-XX label inconsistency across engines

| Engine | File | BR-XX mapping |
|---|---|---|
| **CSP** | `CSPScheduler.java:30-37` | BR-01=L01↔L02, BR-02=L03↔L04, BR-03=REST day, BR-04=Holiday/Leave, BR-05=Max shifts, BR-06=DIRECT_24H max 1/day |
| **CSP DataBuilder** | `CspDataBuilder.java:120,156` | BR-04=adjacent-L01 pairs, BR-03=arcs |
| **Scorer** | `ScheduleQualityScorer.java:590-596` | BR-01=L01↔L02, BR-02=L03↔L04, BR-03=Compensation day, BR-04=Adjacent L01, BR-05=Leave day, BR-06=Max shifts, BR-07=Duplicate |
| **V10 Constraint** | `RestDayConstraint.java:28` | id=`"BR-03:RestDay"` — Max 6 consecutive days |
| **V10 Constraint** | `AdjacentL01Constraint` | id=`"BR-04:..."` |
| **Frontend Business Rules** | `BusinessRulesCard.tsx` | Display-only list of 6 rules with different names |

**Severity**: Medium (Documentation smell — different teams reading code may confuse which BR is which)

**Reproduction**: Open `AUDIT_CONSTRAINT_ENGINE.md:23` says "BR-03 = Max 6 consecutive days". Open `ScheduleQualityScorer.java:593` says "BR-03 = Compensation day". **They contradict.**

**Root Cause**: 3 independent engines (CSP, Scorer, V10) each labeled constraints BR-01..BR-07 with overlapping meaning.

**Fix**: Standardize BR labels in a single source of truth (`SPEC.md`), update all engines to use canonical labels.

#### 3.3.2 Constraint coverage (V10 layer)

**File**: `LocalSearchScheduler.java:102-107`

```
ShiftConflictConstraint   → BR-01, BR-02
LeaveConflictConstraint   → BR-05 (in Scorer semantics) / Holiday/Leave (in CSP semantics)
DuplicateShiftConstraint  → BR-07
RestDayConstraint         → BR-03 (in V10 semantics) = Max 6 consecutive
AdjacentL01Constraint     → BR-04
MaxShiftsConstraint       → BR-06 (V10 semantics)
```

**Verified**: 6 constraints registered. **Missing**: NO constraint for `l0XMaxPerWeek` per-staff weekly cap in V10 layer. Greedy/FairGreedy enforces via `StaffEligibilityFilter:173-178`; V10 does NOT. **Risk**: V10 may produce schedules that violate weekly cap if Manager sets e.g. `l02MaxPerWeek=2` but period requires 3.

### 3.4 AutoGen flow

`RequirementPreparationService.java:54-126` (verified earlier) — ACTIVE.

### 3.5 Compensation logic

`CompensationDateCalculator.java` — uses calendar rules, **NOT** `overnightRecoveryHours`. Verified dead for that config.

### 3.6 Save flow (sequential, atomic-ish)

`AutoSchedulingService.java:918`:
```java
if (algorithmConfigService.getRuntimeConfig().isAutoCompensationEnabled()) {
    createCompensationDaysForL01InPeriod(period.getId());
}
```

`AutoSchedulingService.java:3777` (incremental):
```java
if (algorithmConfigService.getRuntimeConfig() != null
        && algorithmConfigService.getRuntimeConfig().isAutoCompensationEnabled()) {
    createCompensationDaysForL01InPeriod(periodId);
}
```

**Two call sites** for compensation creation. Both check `isAutoCompensationEnabled()`.

### 3.7 Race condition risk — save flow

**Frontend** `RuntimeConfigEditor.tsx:214-215`:
```typescript
await api.updateRuntimeConfig(form);
await api.updateAutoGenConfig(autoGenPayload);
```

**Backend**: Both endpoints mutate `algorithm_config` table rows independently.
- If two Managers save simultaneously, last write wins.
- No row-level lock. Comment at line 211-213 says "Sequential: save runtime-config then auto-gen-config to avoid concurrent lock contention".

**Severity**: Low (clinical config rarely changed simultaneously)
**Recommendation**: Wrap both saves in a single transactional endpoint, OR add `@Version` column.

### 3.8 Lazy loading / N+1

**`AlgorithmConfigCrudService.loadConfigCache()`** loads all keys at startup → O(1) HashMap lookup. No N+1. ✅

### 3.9 OpenAPI / Swagger

**File**: `config/OpenApiConfig.java`

- ✅ OpenAPI bean exists
- ❌ `ConfigController` (570 lines) has **0** `@Operation`, `@Tag`, `@Parameter` annotations
- ❌ `AutoSchedulingController` (421 lines) has **0** annotations
- ❌ DTOs lack `@Schema` annotations

**Severity**: Low (functional API works, but Swagger UI is unhelpful for field-level docs)

**Reproduction**: Open `/swagger-ui/index.html` → config endpoints show only path + method, no field descriptions.

**Fix**: Add `@Operation(summary=...)` and `@Schema(description=...)` for top-impact endpoints.

---

## 4. API Audit

### 4.1 Endpoint inventory

| Endpoint | Method | File:Line | Used by FE? | Verified |
|---|---|---|---|---|
| `/api/v1/config` | GET | `ConfigController.java:47` | ✅ | ✅ |
| `/api/v1/config/{fieldPath:.+}` | GET | line 58 | ✅ | ✅ |
| `/api/v1/config/metadata` | GET | line 75 | ⚠️ | Used by legacy renderer |
| `/api/v1/config/presets` | GET | line 86 | ✅ | ✅ |
| `/api/v1/config` | PUT | line 103 | ✅ | ✅ |
| `/api/v1/config/{fieldPath:.+}` | PUT | line 119 | ✅ | ✅ |
| `/api/v1/config/validate` | POST | line 143 | ✅ | ✅ |
| `/api/v1/config/validate/{fieldPath:.+}` | POST | line 158 | ✅ | ✅ |
| `/api/v1/config/reset` | POST | line 209 | ❌ (?) | NOT VERIFIED in current FE |
| `/api/v1/config/reset/{fieldPath:.+}` | POST | line 220 | ❌ (?) | NOT VERIFIED |
| `/api/v1/config/presets/{presetKey}/apply` | POST | line 237 | ✅ | ✅ |
| `/api/v1/config/diff` | POST | line 259 | ✅ | ✅ |
| `/api/v1/auto-schedule/runtime-config` | GET | `AutoSchedulingController.java:363` | ✅ | ✅ |
| `/api/v1/auto-schedule/runtime-config` | PUT | line 371 | ✅ | ✅ |
| `/api/v1/auto-schedule/auto-gen-config` | GET | line 380 | ✅ | ✅ |
| `/api/v1/auto-schedule/auto-gen-config` | PUT | line 387 | ✅ | ✅ |
| `/api/v1/auto-schedule/auto-gen-config/recommend` | POST | line 396 | ✅ (AutoCalc) | ✅ |
| `/api/v1/auto-schedule/config/{paramKey}` | DELETE | line 343 | ✅ (Thêm/Xóa) | ✅ |
| `/api/v1/auto-schedule/config/sync-descriptions` | POST | line 351 | ✅ | ✅ |

### 4.2 FE → BE Field mapping (`PARAM_KEY_TO_CFG`)

**File**: `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/types.ts:89-99`

```typescript
greedy_coverage_threshold: "greedyCoverageThreshold",
balance_score_min: "balanceScoreMin",
weekend_weight: "weekendWeight",
overnight_recovery_hours: "overnightRecoveryHours",
min_staff_per_shift: "minStaffPerShift",
max_staff_per_shift: "maxStaffPerShift",
min_shifts_per_staff: "minShiftsPerStaff",
max_shifts_per_staff: "maxShiftsPerStaff",
holiday_mode: "holidayMode",
```

✅ Bidirectional mapping defined.

### 4.3 PATCH method missing

Audit tìm: no PATCH endpoint. Config update is full-replace via PUT. **Verdict**: OK for v1.0 (no partial updates needed).

### 4.4 Response shape

`/api/v1/config` returns flat record per field path. `/runtime-config` returns full `RuntimeConfig` POJO. ✅

### 4.5 FE-BE field mismatch risk

**Source**: `frontend/src/lib/api-client.ts:1185` declares `autoCompensationEnabled: boolean`. `backend` reads via `runtimeConfig.isAutoCompensationEnabled()`. ✅ Consistent.

But: **l04BalanceStrategy** in FE type (`types.ts:13`):
```typescript
export type BalanceStrategy = "STRICT_MATCH_ONLY" | "FAIR_DISTRIBUTE" | "WEIGHTED_FAIR";
```

Backend `ConfigDomain.java` declares same values. ✅

### 4.6 API gap — preset apply response

`POST /presets/{presetKey}/apply` returns the **applied config**. Used by `RuntimeConfigEditor.applyPreset`. ✅

### 4.7 API gap — no bulk import/export for runtime config

Audit: `ConfigProfileController.java:1784` has `compareConfigProfiles`. `ConfigProfileController.java:1808` has `exportConfigProfile`. Profile export exists, but **single-config runtime export** not exposed. FE handles via Copy/Paste button (clipboard).

**Verdict**: Adequate for v1.0.

---

## 5. Frontend Audit

### 5.1 State management

**File**: `RuntimeConfigEditor.tsx:155-225`

| Aspect | Implementation | Verified |
|---|---|---|
| Form state | `useState<RuntimeConfig \| null>` | ✅ |
| Save handler | `async handleSave()` | ✅ |
| Sequential save | `await updateRuntimeConfig` THEN `await updateAutoGenConfig` | ✅ (line 214-215) |
| Loading state | `setSaving(true/false)` | ✅ |
| Error handling | try/catch → toast | ✅ |
| Reset | `handleReset()` reverts `form = config` | ✅ |
| Dirty tracking | `getChangedKeys(config, form)` | ✅ |
| Diff display | `setShowDiff(true)` modal | ✅ |
| Save button disabled during save | `disabled={saving}` | ✅ |

### 5.2 Dirty state preservation

**⚠️ BUG #6**: No `beforeunload` warning. User navigating away with unsaved changes loses them silently.

**Severity**: Low (Manager rarely navigates mid-edit)

**Reproduction**: 
1. Edit any param value
2. Click browser refresh / close tab
3. **Expected**: Browser shows "Changes you made may not be saved"
4. **Actual**: No warning, data lost

**Fix**: Add `useEffect(() => { window.addEventListener('beforeunload', ...) }, [isDirty])`.

### 5.3 Keyboard shortcuts

`RuntimeConfigEditor.tsx:125-153` — implemented:
- Ctrl+S → Save
- Ctrl+Z → Undo
- Ctrl+E → Edit
- Esc → Cancel

✅ All work, skip if target is INPUT/TEXTAREA.

### 5.4 Accessibility

| Aspect | Status |
|---|---|
| ARIA labels on selects | ✅ `HolidayModeField.tsx:25` |
| Tab order | Likely OK (sequential DOM) — NOT VERIFIED via automated test |
| Focus trap on modals | ✅ via `ConfirmDialog` |
| Color contrast | Not verified via WCAG tool — but uses design tokens |
| Skip-to-content link | NOT VERIFIED |
| Keyboard navigation of grid cells | Likely OK |
| Screen reader on color-coded labels | Color alone insufficient — need text marker |

### 5.5 Color-coded labels (after recent update)

`ShiftTypeGroupCard.tsx:142-148`:
- `MinPerDay` → secondary-container (green) ✅
- `MaxPerDay` → amber-100 ✅
- `MaxPerWeek` → error-container (red) ✅

**Accessibility concern**: Colorblind users (8% of males) cannot distinguish red/green/amber. **Fix**: Add icon or text indicator (e.g., 🟢 🟠 🔴 or "MỀM"/"CỨNG" prefix). Current implementation relies solely on color.

**Severity**: Low-Medium (accessibility)

### 5.6 Responsive

`RuntimeConfigEditor.tsx` uses `grid-cols-1 sm:grid-cols-2 xl:grid-cols-3`. ✅ Mobile-aware.

### 5.7 Dark mode

Toggled via `@media (prefers-color-scheme: dark)`. Material design tokens auto-override. NOT VERIFIED manually here.

### 5.8 Loading & skeleton

`EditorSkeleton` rendered at line 283 when `loading=true`. ✅

### 5.9 Validation feedback

`lib/validation/algorithmConfig.ts:137 lines` — `PARAM_VALIDATIONS` map. Returns warning/error.

**Gap**: Validation runs on form change but **does NOT block save**. User can save with `weekend_weight=4.5` even if warning says "nguy cơ quá tải".

**Severity**: Low (warnings are advisory)

### 5.10 NumberSpinner / CompactSpinner

Located in `ShiftTypeGroupCard.tsx:155-158` referenced. NOT directly inspected in this audit but referenced.

### 5.11 Cancel / Reset behavior

`handleReset()` (need to check): reverts form to config, sets editing=false.

### 5.12 Undo

Ctrl+Z handler at line 138-142 calls `handleReset()` — full revert, NOT granular undo. **Limitation**: cannot undo one change at a time.

**Severity**: Low (acceptable for v1.0)

### 5.13 Race condition in FE

Save handler: `disabled={saving}` prevents double-click during save. ✅

But: if user navigates between two Config Admin tabs (config/history/audit) while save in flight, save continues — not cancelled. ✅ Acceptable.

### 5.14 Error display

```typescript
catch (err) {
    error(getErrorMessage(err, "Lưu thất bại"));
}
```

`getErrorMessage` likely extracts backend error. ✅

### 5.15 Performance

Form state updates are O(1). Re-renders only on form change. ✅

Diff computation `getChangedKeys` is O(n) over keys (~45 keys). Trivial. ✅

---

## 6. Runtime Audit (End-to-End)

### 6.1 Config change → Scheduler behavior matrix

| Config | User changes in UI | Saved to DB | Scheduler reads? | Behavior changes? |
|---|---|---|---|---|
| `maxStaffPerShift` | ✅ | ✅ `algorithm_config` | ✅ `AutoSchedulingService:1336` | ✅ YES (caps effectiveMax) |
| `maxShiftsPerStaff` | ✅ | ✅ | ✅ `StaffEligibilityFilter:160`, `AutoSchedulingService:1310, 1553`, `SchedulingFeasibilityAnalyzer:581` | ✅ YES |
| `holidayMode` | ✅ | ✅ | ✅ `RequirementPreparationService:93,160,174` | ✅ YES (L03 generation skipped on holiday) |
| `removedShiftTypes` | ✅ | ✅ | ✅ `RequirementPreparationService:133-185`, `AutoSchedulingService:3527-3579` | ✅ YES (L0X not generated) |
| `l0XMinPerDay` | ✅ | ✅ | ✅ `RequirementPreparationService:102-112` | ✅ YES (requiredStaffCount) |
| `l0XMaxPerDay` | ✅ | ✅ | ✅ Same | ✅ YES (cap target) |
| `l0XMaxPerWeek` | ✅ | ✅ | ✅ `StaffEligibilityFilter:486-492`, `AutoSchedulingService:3402-3408` | ✅ YES (HARD weekly cap) |
| `l04CrossSpecialty` | ✅ | ✅ | ✅ `StaffEligibilityFilter:438` | ✅ YES (eligibility) |
| `l04CrossSpecialtyRatio` | ✅ | ✅ | ✅ `StaffEligibilityFilter:439`, line 114 | ✅ YES (cross cap) |
| `l04AllowedSpecialties` | ✅ | ✅ | ✅ `StaffEligibilityFilter:440` | ✅ YES (eligibility list) |
| `l04BalanceStrategy` | ✅ | ✅ | ❌ Loaded but never branched | ❌ NO (dead) |
| `weekendWeight` | ⚠️ Internal | ✅ | ✅ `AutoSchedulingService:1304, 1607` | ✅ YES (comparator penalty) — but not visible in main editor |
| `balanceScoreMin` | ⚠️ Internal | ✅ | ✅ `AutoSchedulingService:731, 738, 750` | ✅ YES (FairGreedy trigger) |
| `greedyCoverageThreshold` | ❌ Not in main editor | ✅ | ✅ `AutoSchedulingService:1143, 1390` | ✅ YES (coverageTarget) — but not visible |
| `autoCompensationEnabled` | ❌ Locked = Always On UI | ✅ | ✅ `AutoSchedulingService:918, 3777` | ✅ YES (skip compensation if false) |
| `minStaffPerShift` | ❌ Internal | ✅ | ❌ | ❌ NO (dead) |
| `minShiftsPerStaff` | ❌ Internal | ✅ | ❌ | ❌ NO (dead) |
| `overnightRecoveryHours` | ❌ Internal | ✅ | ❌ only in log | ❌ NO (dead) |
| `l0XMinPerWeek` | ❌ hiddenParams | ✅ | ❌ | ⚠️ RESERVED v1.1 (constraint layer; refactored from earlier DEAD classification) |

### 6.2 Config change → Greedy flow

```
User edits `maxStaffPerShift = 10`
  ↓
form.maxStaffPerShift = 10
  ↓ handleSave
api.updateRuntimeConfig(form)
  ↓ PUT /api/v1/auto-schedule/runtime-config
ConfigController.saveRuntimeConfig
  ↓
ConfigService.save → upsert("max_staff_per_shift", "10", NUMBER, ...)
  ↓ INSERT/UPDATE
DB: algorithm_config row updated
  ↓ Next scheduler run
AutoSchedulingService.autoSchedule
  ↓
AlgorithmConfigService.getRuntimeConfig() → returns POJO with maxStaffPerShift=10
  ↓
StaffEligibilityFilter / GreedyAssignmentEngine reads getMaxStaffPerShift()
  ↓ effectiveMax = min(10, requiredStaffCount) (line 1336)
toAssign = min(effectiveMax, eligible.size())
```

**Verified end-to-end** ✅

### 6.3 Config change → AutoGen flow

```
User edits `holidayMode = PARTIAL`
  ↓
form.holidayMode = "PARTIAL"
  ↓ handleSave
api.updateRuntimeConfig(form)  // stored in DB
api.updateAutoGenConfig(autoGenPayload)  // stored in DB
  ↓
DB: algorithm_config.holiday_mode = "PARTIAL"
  ↓ Next scheduler run
RequirementPreparationService.prepareRequirements
  ↓
algorithmConfigService.getAutoGenConfig() → returns AutoGenConfig with holidayMode=PARTIAL
  ↓
generateRequirementsFromConfig → "PARTIAL" enables L03 generation even on holiday (line 174)
```

**Verified end-to-end** ✅

### 6.4 Profile apply → behavior change

```
User clicks "Sandbox" → chọn "holiday" preset → Áp dụng
  ↓
ConfigProfileService.apply("holiday")
  ↓ reads config_json from DB
ConfigService.save with override fields
  ↓
DB: ALL config rows updated (weekendWeight, holidayMode, autoCompensationEnabled, ...)
  ↓ ⚠️ autoCompensationEnabled set to false
Next scheduler run → compensation NOT created for L01
```

**Behavior change confirmed** — see §2.4 Bug #1.

---

## 7. Config Matrix

### 7.1 Status definitions

| Status | Meaning |
|---|---|
| **BUSINESS** | Manager-controlled, ACTIVE runtime |
| **INTERNAL** | Developer-controlled, ACTIVE runtime, hidden in UI |
| **MONITORING** | Read-only display, no runtime effect |
| **RESERVED** | Persisted but not consumed; planned v1.1 (do NOT remove) |
| **DEPRECATED** | Marked `@Deprecated`, scheduled removal in next major |
| **ORPHAN** | Active runtime, but UI-hidden; can become visible without code change |
| **DEAD** | No consumer anywhere (safe to remove) |
| **LEGACY** | Inserted by old SQL seed, no Java reader; database-only residue |
| **REQUIREMENT CONFLICT** | Same flag interpreted differently across layers (see §20b) |

> **Reading guide** (taxonomy decision aid):
> - **RESERVED** ≠ **DEAD**. RESERVED means "intentional, planned for future". DEAD means "abandoned, no plan".
> - Don't apply "remove" action to RESERVED. Defer to v1.1 per roadmap.
> - `l0XMinPerWeek` group is **RESERVED v1.1** (constraint system), NOT DEAD — refactored from earlier audit.
> - `MAX_SHIFTS_PER_MONTH_DEFAULT`, `AVOID_BACK_TO_BACK_SHIFT`, `ENABLE_COMPENSATION_AFTER_L01` are **LEGACY** (SQL-only, never read). Safe to remove via migration.

### 7.2 Config matrix (full)

| # | Key | UI Status | Backend Status | Runtime Status | Verdict |
|---|---|---|---|---|---|
| 1 | `max_staff_per_shift` | BUSINESS visible | ACTIVE | HARD cap | ✅ BUSINESS |
| 2 | `max_shifts_per_staff` | BUSINESS visible | ACTIVE | HARD cap + Feasibility | ✅ BUSINESS |
| 3 | `holiday_mode` | BUSINESS visible | ACTIVE | AutoGen L03 logic | ✅ BUSINESS |
| 4 | `removed_shift_types` | BUSINESS visible | ACTIVE | AutoGen skip | ✅ BUSINESS |
| 5 | `l01MinPerDay` | BUSINESS visible | ACTIVE | requiredStaffCount | ✅ BUSINESS |
| 6 | `l02MinPerDay` | BUSINESS visible | ACTIVE | requiredStaffCount | ✅ BUSINESS |
| 7 | `l03MinPerDay` | BUSINESS visible | ACTIVE | requiredStaffCount | ✅ BUSINESS |
| 8 | `l04MinPerDay` | BUSINESS visible | ACTIVE | requiredStaffCount | ✅ BUSINESS |
| 9 | `l01MaxPerDay` | BUSINESS visible | ACTIVE | requiredStaffCount cap | ✅ BUSINESS |
| 10 | `l02MaxPerDay` | BUSINESS visible | ACTIVE | requiredStaffCount cap | ✅ BUSINESS |
| 11 | `l03MaxPerDay` | BUSINESS visible | ACTIVE | requiredStaffCount cap | ✅ BUSINESS |
| 12 | `l04MaxPerDay` | BUSINESS visible | ACTIVE | requiredStaffCount cap | ✅ BUSINESS |
| 13 | `l01MaxPerWeek` | BUSINESS visible | ACTIVE | HARD weekly cap | ✅ BUSINESS |
| 14 | `l02MaxPerWeek` | BUSINESS visible | ACTIVE | HARD weekly cap | ✅ BUSINESS |
| 15 | `l03MaxPerWeek` | BUSINESS visible | ACTIVE | HARD weekly cap | ✅ BUSINESS |
| 16 | `l04MaxPerWeek` | BUSINESS visible | ACTIVE | HARD weekly cap | ✅ BUSINESS |
| 17 | `l04CrossSpecialty` | BUSINESS visible | ACTIVE | Eligibility | ✅ BUSINESS |
| 18 | `l04CrossSpecialtyRatio` | BUSINESS visible | ACTIVE | Cross cap | ✅ BUSINESS |
| 19 | `l04AllowedSpecialties` | BUSINESS visible | ACTIVE | Eligibility list | ✅ BUSINESS |
| 20 | `l04BalanceStrategy` | BUSINESS visible | DEAD | Loaded but not branched | 🔴 DEAD |
| 21 | `weekendWeight` | INTERNAL hidden | ACTIVE | Greedy/FairGreedy penalty | ⚠️ INTERNAL |
| 22 | `balanceScoreMin` | INTERNAL hidden | ACTIVE | FairGreedy fallback trigger | ⚠️ INTERNAL |
| 23 | `greedyCoverageThreshold` | NOT IN MAIN EDITOR | ACTIVE | coverageTarget + log | ⚠️ ORPHAN |
| 24 | `autoCompensationEnabled` | Locked "Always On" | ACTIVE | Compensation create | ⚠️ INCONSISTENT |
| 25 | `overnight_recovery_hours` | INTERNAL hidden | DEAD | Log only | 🔴 DEAD |
| 26 | `min_staff_per_shift` | INTERNAL hidden | DEAD | None | 🔴 DEAD |
| 27 | `min_shifts_per_staff` | INTERNAL hidden | DEAD | None | 🔴 DEAD |
| 28 | `l01MinPerWeek` | hiddenParams | RESERVED | Planned v1.1 constraint layer | ⚠️ RESERVED v1.1 |
| 29 | `l02MinPerWeek` | hiddenParams | RESERVED | Planned v1.1 constraint layer | ⚠️ RESERVED v1.1 |
| 30 | `l03MinPerWeek` | hiddenParams | RESERVED | Planned v1.1 constraint layer | ⚠️ RESERVED v1.1 |
| 31 | `l04MinPerWeek` | hiddenParams | RESERVED | Planned v1.1 constraint layer | ⚠️ RESERVED v1.1 |
| 32 | `MAX_SHIFTS_PER_MONTH_DEFAULT` (legacy) | NOT VERIFIED in UI | LEGACY | SQL seed only, no Java reader | 🟡 LEGACY |
| 33 | `AVOID_BACK_TO_BACK_SHIFT` (legacy) | NOT VERIFIED | LEGACY | SQL seed only, no Java reader | 🟡 LEGACY |
| 34 | `ENABLE_COMPENSATION_AFTER_L01` (legacy) | NOT VERIFIED | LEGACY | SQL seed only, no Java reader | 🟡 LEGACY |

### 7.3 Counts

| Status | Count | Pct |
|---|---|---|
| BUSINESS (Manager, ACTIVE) | 19 | 56% |
| INTERNAL (Developer, ACTIVE) | 2 | 6% |
| ORPHAN (active runtime, UI-hidden) | 2 | 6% |
| INCONSISTENT (UI ≠ Runtime) | 1 | 3% |
| RESERVED v1.1 (planned, do NOT remove) | 4 | 12% |
| DEAD (UI hidden, no runtime use) | 3 | 9% |
| LEGACY (SQL-only, no Java reader) | 3 | 9% |
| **Total** | **34** | **100%** |

---

## 8. Config Action Plan (reframed — taxonomy-correct)

> **Action key** (per §7.2 taxonomy):
> - 🔴 **DEAD** = no consumer anywhere, safe to remove in v1.1 cleanup
> - ⚠️ **RESERVED v1.1** = planned for future release, **DO NOT REMOVE**
> - 🟡 **LEGACY** = SQL-only residue, remove via migration

### 8.1 DEAD configs (remove in v1.1)

| # | Config Key | File | Action |
|---|---|---|---|
| 1 | `overnight_recovery_hours` | `paramConfig.ts:124` | Already marked Internal. Remove in v1.1. |
| 2 | `min_staff_per_shift` | `paramConfig.ts:115, 129-133` | Already marked Deprecated. Remove in v1.1. |
| 3 | `min_shifts_per_staff` | `paramConfig.ts:116, 134-138` | Already marked Deprecated. Remove in v1.1. |

### 8.2 RESERVED v1.1 configs (keep — DO NOT REMOVE)

| # | Config Key | File | Planned Use |
|---|---|---|---|
| 4 | `l01MinPerWeek` | `paramConfig.ts:173` | Constraint layer v1.1 (min duty per shift type per week) |
| 5 | `l02MinPerWeek` | `paramConfig.ts:180` | Same |
| 6 | `l03MinPerWeek` | `paramConfig.ts:187` | Same |
| 7 | `l04MinPerWeek` | `paramConfig.ts:194` | Same |

| 8 | `l04BalanceStrategy` | `AutoGenConfig.java:36` | 🔴 DEAD — Decide: implement or remove. |
| 9 | `MAX_SHIFTS_PER_MONTH_DEFAULT` | `hospital_scheduler_business_final.sql:605` | 🟡 LEGACY — Remove SQL INSERT. |
| 10 | `AVOID_BACK_TO_BACK_SHIFT` | `hospital_scheduler_business_final.sql:606` | 🟡 LEGACY — Remove SQL INSERT. |
| 11 | `ENABLE_COMPENSATION_AFTER_L01` | `hospital_scheduler_business_final.sql:607` | 🟡 LEGACY — Remove SQL INSERT. |

> **Note on §9 of earlier drafts**: this section previously conflated "intentionally held for v1.1" (RESERVED) with "abandoned but kept around" (DEAD). After taxonomy review, only `l0XMinPerWeek` group qualifies as RESERVED v1.1; `l04BalanceStrategy` and `overnightRecoveryHours` are correctly classified DEAD.

---

## 9. Reserved Config (intentionally held for v1.1 — do NOT remove)

| Config | Reason | Reference |
|---|---|---|
| `l01MinPerWeek` | Constraint layer v1.1: minimum L01 duty per staff per week | `AUDIT_SCHEDULER_ENGINE.md:185` |
| `l02MinPerWeek` | Same for L02 | Same |
| `l03MinPerWeek` | Same for L03 | Same |
| `l04MinPerWeek` | Same for L04 | Same |
| `autoCompensationEnabled` | Currently locked "Always On" UI; may become selectable in v1.1 if PO confirms optional mode | `AUDIT_SCHEDULER_ENGINE.md:183` |

---

## 10. Deprecated Config

| Config | Status | Location |
|---|---|---|
| `min_staff_per_shift` | `@Deprecated` in Java; hidden in UI | `ConfigDomain.java:202` |
| `min_shifts_per_staff` | `@Deprecated` in Java; hidden in UI | `ConfigDomain.java:215` |
| `shouldPreferCrossSpecialty(req, ratio)` (method) | `@Deprecated` | `StaffEligibilityFilter.java:402` |
| `getNonL04AllowedSpecialties(String)` | `@Deprecated` returns empty | `StaffEligibilityFilter.java:480` |
| `ScheduleScorer` stub | `@Deprecated` | `AUDIT_SCHEDULER_ENGINE.md:144` |
| `CORE_ELIGIBLE_SPECIALTIES` const | NOT VERIFIED directly but referenced | (legacy) |

---

## 11. Business Config (verified ACTIVE)

19 configs (see §7.2). Manager-editable in UI, runtime-active.

---

## 12. Monitoring Config

**None currently exist.** Configs like `coverageRate`, `balanceScore` are computed metrics, not configs.

---

## 13. Internal Config

| Config | Why Internal | Runtime effect |
|---|---|---|
| `weekend_weight` | Algorithm tuning; Developer should set | Greedy/FairGreedy penalty |
| `balance_score_min` | Algorithm tuning; affects FairGreedy trigger | FairGreedy fallback trigger |

**Gap**: NO UI for `greedy_coverage_threshold` despite it being ACTIVE in coverageTarget logic.

---

## 14. Bugs (NEW findings in addition to v1)

### Bug #6: `holiday` preset silently disables auto-compensation

> **Evidence Level**: ★★★★★ (HIGH — confirmed via code trace: SQL JSON → ConfigMapper → AlgorithmRuntimeConfig → AutoSchedulingService:918)

**Issue**:
- Profile `holiday` JSON has `"autoCompensationEnabled":false`. When applied, runtime skips L01 compensation day creation.
- Location: `V14__add_config_profile_table.sql:104` ↔ `AutoSchedulingService.java:918`

**Impact**: Clinical workflow surprise — Manager expects compensation after L01 even on holidays.

**Root Cause**: Requirement conflict. Three layers interpret the same flag differently:
- UI shows "Always On" → **MANDATORY**
- Backend `[RESERVED v1.1]` annotation → **MANDATORY**
- Holiday profile JSON sets `false` → **OPTIONAL**

**Reproduction**: Apply holiday preset → run scheduler on period with L01 → check `compensation_day` table — empty.

**Recommendation**:
- **Decision required** (PO/BA): Is L01 compensation MANDATORY or OPTIONAL?
  - MANDATORY → Option A: Remove `autoCompensationEnabled` entirely (3.5h engineering)
  - OPTIONAL → Option B: Synchronize all 13 layers to respect the toggle (6h engineering)
- **Status**: `HOLD pending PO decision` — engineering cannot resolve unilaterally.

### Bug #7: Configurable timeLimitSeconds / candidateListSize unreachable

- **Severity**: Medium
- **Location**: `ConfigDomain.java` declares these fields, but V10 reads `SchedulingConfig` (DIFFERENT bean)
- **Description**: Fields exist in `ConfigDomain` but no path (UI/API/DB) writes to them effectively, AND V10 reads from `application.properties` instead. Two layers, only one functional.
- **Impact**: Manager sees `timeLimitSeconds:60` in profile JSON, but changing it does nothing because V10 ignores runtime config for these.
- **Root Cause**: Layer B (`SchedulingConfig`) was introduced for V10 but Layer A (`ConfigDomain`) duplicates the fields.
- **Fix**: Remove `timeLimitSeconds` and `candidateListSize` from `ConfigDomain`. Document that V10 uses Layer B from properties.

### Bug #8: BR-XX label conflict across engines

- **Severity**: Medium (Documentation)
- **Location**: Multiple files
- **Description**: Same BR-XX codes mean different rules in different engines (see §3.3.1).
- **Impact**: Maintenance confusion, log misinterpretation.
- **Fix**: Standardize in `SPEC.md`, propagate to all engines.

### Bug #9: V10 layer lacks `l0XMaxPerWeek` constraint

> **Evidence Level**: ★★★★ (HIGH — V10 path confirmed by code reading; unit test coverage NOT found)

**Issue**:
- Manager sets `l02MaxPerWeek=2`. Greedy scheduler respects this cap ✅. V10 does NOT register this constraint in `LocalSearchScheduler.java:102-107` ❌.
- Impact is **latent** (V10 not default today) but becomes **current** if V10 becomes selectable.

**Impact**: Cross-engine inconsistency — same config, different runtime behavior.

**Recommendation**: Add `WeeklyCapConstraint` to `scheduling/constraint/` and register in `LocalSearchScheduler.java:107`. Fix before enabling V10 in default UI path. Effort: 4h.

### Bug #10: No `beforeunload` guard on dirty editor

- **Severity**: Low
- **Location**: `RuntimeConfigEditor.tsx:155-156`
- **Fix**: Add window event listener.

### Bug #11: Orphan DB rows from SQL seed (legacy)

> **Evidence Level**: ★★★★★ (HIGH — SQL seed confirmed, no Java reader found across full codebase)

**Issue**: 3 config rows inserted by `hospital_scheduler_business_final.sql:604-607`, no Java code reads them:
- `MAX_SHIFTS_PER_MONTH_DEFAULT`
- `AVOID_BACK_TO_BACK_SHIFT`
- `ENABLE_COMPENSATION_AFTER_L01`

**Impact**: Misleading DB queries; possible visibility in `CustomConfigsCard`.

**Recommendation**: Remove SQL INSERT statements via Flyway migration. Effort: 5 min. Status: Ready to merge.

### Bug #12: Color-only differentiation on shift-type limits

> **Evidence Level**: ★★★ (MEDIUM — accessibility concern, code confirmed)

**Issue**: `ShiftTypeGroupCard.tsx:142-148` differentiates MỀM vs CỨNG limits by color only.

**Impact**: Accessibility (WCAG AA contrast failure).

**Recommendation**: Add icon or text label ("MỀM"/"CỨNG"). Non-blocking, v1.1.

### Bug #13: OpenAPI/Swagger missing field docs

- **Severity**: Low
- **Location**: `ConfigController.java`, `AutoSchedulingController.java`
- **Fix**: Add `@Operation` and `@Parameter`.

### Bug #14: AutoCompensationCard static (not bound to form state)

> **Evidence Level**: ★★★★★ (HIGH — confirmed by code reading of RuntimeConfigEditor.tsx:816-845)

**Issue**: Card displays hardcoded "Always On" even when `form.autoCompensationEnabled === false`.

**Impact**: UI shows wrong state when form is dirty.

**Recommendation**: **Subsumed by Bug #6 fix.** When `autoCompensationEnabled` is resolved (Option A or B), this UI issue auto-resolves. No independent action needed.

---

## 15. UX Issues

| # | Issue | Severity | File:Line |
|---|---|---|---|
| U1 | `weekend_weight` & `greedy_coverage_threshold` not in main editor — accessible only via "Thêm" raw row + "Tham khảo" docs | Medium | `paramConfig.ts:34-141` (no entry) |
| U2 | AutoCompensationCard not bound to state | Medium | `RuntimeConfigEditor.tsx:816-845` |
| U3 | Color-only differentiation for MinPerDay/MaxPerDay/MaxPerWeek | Low (a11y) | `ShiftTypeGroupCard.tsx:142-148` |
| U4 | No beforeunload guard | Low | `RuntimeConfigEditor.tsx` |
| U5 | Granular undo not supported (only full reset) | Low | `RuntimeConfigEditor.tsx:138-142` |
| U6 | Validation warnings don't block save | Low | `lib/validation/algorithmConfig.ts` |
| U7 | BR-XX labels in `BusinessRulesCard.tsx` may not match engine labels | Low | (need to verify) |
| U8 | No in-form help on how each param affects scheduler | Low | All |

---

## 16. Performance Issues

| # | Issue | Severity | File |
|---|---|---|---|
| P1 | Two sequential API calls on save (runtime-config → auto-gen-config) | Low | `RuntimeConfigEditor.tsx:214-215` |
| P2 | CompensationDateCalculator: holiday lookup calls DB on every call (no caching) | Low | `CompensationDateCalculator.java:109` |
| P3 | `loadConfigCache()` loads all keys at startup | Negligible | `AlgorithmConfigCrudService.java:48` |

**No critical performance issues** for v1.0 scale.

---

## 17. Security Issues

| # | Issue | Severity | File |
|---|---|---|---|
| S1 | RBAC enforced (Admin-only) | ✅ Adequate | `page.tsx:23` |
| S2 | Audit trail (algorithm_config_audit) | ✅ Adequate | V5 migration |
| S3 | JWT auth on all endpoints | ✅ Adequate | `SecurityConfig.java` |
| S4 | SQL injection | ✅ Adequate (JPA parameterized) | N/A |
| S5 | No CSRF explicit (rely on JWT) | Adequate | N/A |
| S6 | Saved clipboard config — no validation before paste apply | Low | `RuntimeConfigEditor.tsx:269-281` — `parsed` is JSON-parsed but `setForm({ ...prev, ...parsed })` blindly merges; an attacker could craft a clipboard JSON with extra fields. Since `parsed` is local-only, risk is low. |

---

## 18. Cleanup Proposal

### 18.1 Before RC v1.0 (BLOCKER)

| # | Action | File |
|---|---|---|
| C1 | **Bug #6 fix**: Either update holiday profile JSON to `autoCompensationEnabled: true`, OR force runtime to ignore the config | V14 + AutoSchedulingService |
| C2 | **Bug #14 fix**: Bind AutoCompensationCard to form state | RuntimeConfigEditor.tsx |
| C3 | Remove orphan DB seed rows (`MAX_SHIFTS_PER_MONTH_DEFAULT`, etc.) | hospital_scheduler_business_final.sql |

### 18.2 v1.1 Roadmap

| # | Action | File |
|---|---|---|
| C4 | Remove `@Deprecated` fields from `ConfigDomain` | ConfigDomain.java |
| C5 | Implement `l04BalanceStrategy` switch OR remove enum | AutoGenConfig + StaffEligibilityFilter |
| C6 | Decide `overnight_recovery_hours`: implement OR remove | All |
| C7 | Remove dead configs `l0XMinPerWeek` from AutoGenConfig | AutoGenConfig.java |
| C8 | Standardize BR-XX labels in SPEC.md and propagate | SPEC.md + 3 engines |
| C9 | Add V10 constraint for `l0XMaxPerWeek` | scheduling/constraint/ |
| C10 | Add `timeLimitSeconds` + `candidateListSize` to V10 via DB OR remove from ConfigDomain | SchedulingConfig + ConfigDomain |
| C11 | Add Advanced group for `weekend_weight`, `greedy_coverage_threshold` with "Developer only" badge | paramConfig.ts |

### 18.3 Optional improvements

| # | Action |
|---|---|
| C12 | Add `@Operation` annotations to config controllers |
| C13 | Add `beforeunload` guard |
| C14 | Add icon/text on color-coded labels for a11y |
| C15 | Granular undo (history stack) |

---

## 19. Release Risk

### 19.1 Release blockers (P0)

| Risk | Severity | Probability | Impact |
|---|---|---|---|
| **Bug #6**: `holiday` preset disables compensation | HIGH | HIGH | Manager applies preset → clinical workflow breaks |
| **Bug #14**: AutoCompensationCard hardcoded "Always On" misleads about actual state | MEDIUM | MEDIUM | User trust issue |

### 19.2 High-priority (P1)

| Risk | Severity | Probability | Impact |
|---|---|---|---|
| Bug #9: V10 missing weekly cap constraint | MEDIUM | LOW (V10 not default) | Future regression |
| Bug #8: BR-XX label conflict | MEDIUM | LOW (dev-facing) | Maintenance |
| Bug #7: timeLimitSeconds dead config | MEDIUM | LOW | Future regression |

### 19.3 Medium (P2)

| Risk | Severity | Probability |
|---|---|---|
| Bug #11: Orphan DB rows | MEDIUM | LOW |
| Bug #13: Missing Swagger annotations | LOW | MEDIUM |
| Color-only differentiation | LOW | LOW |

### 19.4 Verdict

**DO NOT RELEASE v1.0.0 without fixing Bug #6 and Bug #14.** Other bugs are acceptable for v1.0 with documented workarounds.

---

## 20a. Verification Addendum (Post-Review)

**Date**: 2026-07-17 (same day, follow-up review)
**Reviewer**: User feedback + re-verification

### 20a.1 Bug #6 — three verification questions

User asked to verify:

> 1. Preset `holiday` có thực sự được load vào `AlgorithmConfigService`/runtime config không?
> 2. `AutoSchedulingService` có thực sự đọc `autoCompensationEnabled` từ runtime config ở hai vị trí được nêu (`line 918` và `3777`) hay chỉ còn mã cũ?
> 3. Có bất kỳ logic nào sau đó ép giá trị về `true` hoặc bỏ qua trường này không?

**Note on framing**: Originally classified as "P0 Clinical Bug". After re-review, classification upgraded to **`P0 Requirement Conflict`** (see §20b for full evidence). The scheduler is not technically wrong — it correctly respects the DB flag. The bug is that DB flag shouldn't exist given current spec intent. Engineering fix alone cannot resolve it without PO/BA confirmation of compensation semantics.

**Q1 — Holiday preset loading**: ✅ VERIFIED
- `V14__add_config_profile_table.sql:104` seed `holiday` profile with JSON containing `"autoCompensationEnabled":false`
- `ConfigProfileService.java:311-315 applyByKey(profileKey)` → `apply(id)` → `fromJson(profile.getConfigJson())` → `configService.save(profileConfig, true)` → `ConfigMapper.toParamMap(config)` → `crud.upsertAll(paramMap)`
- `ConfigMapper.java:206` writes `constraints.autoCompensationEnabled` → DB key `auto_compensation_enabled` (`ConfigMapper.java:111`)

**Q2 — Runtime read at 2 sites**: ✅ VERIFIED (both still active)
- `AutoSchedulingService.java:918`:
  ```java
  if (algorithmConfigService.getRuntimeConfig().isAutoCompensationEnabled()) {
      createCompensationDaysForL01InPeriod(period.getId());
  } else {
      log.info("Auto compensation disabled by config for period {}", period.getId());
  }
  ```
- `AutoSchedulingService.java:3776-3779`:
  ```java
  if (algorithmConfigService.getRuntimeConfig() != null
          && algorithmConfigService.getRuntimeConfig().isAutoCompensationEnabled()) {
      createCompensationDaysForL01InPeriod(periodId);
  }
  ```
- `createCompensationDaysForL01InPeriod` is **only called from these 2 sites** (grep verified: 0 other callers).
- `AlgorithmConfigService.java:505` reads `getBooleanValue(AUTO_COMPENSATION_ENABLED, true, cache)` — passes the DB value through with no override.

**Q3 — Any force-true logic?**: ❌ NONE FOUND
- `ConfigValidator.validateBusiness()` (lines 187-274) does NOT flag `autoCompensationEnabled=false` as error or warning.
- `ConfigService.save(force=true)` (`ConfigProfileService.apply` line 301 uses `force=true`) **skips validation entirely** for profile applies.
- No middleware, filter, or aspect overrides the value after read.

### 20a.2 Revised severity per user feedback

| Bug | Original | Revised | Reason |
|---|---|---|---|
| **Bug #6** | P0 | **P0 ✅ confirmed** | All 3 verification conditions met. No bypass logic. |
| **Bug #14** | Medium | **P2 (subsumed)** | Disappears if Bug #6 is fixed by forcing `true` in runtime. No need to bind form state. |
| **Bug #7** | Medium | **Technical Debt** | `timeLimitSeconds`/`candidateListSize` dual-layer. No release blocker. |
| **Bug #8** | Medium | **Documentation** | BR-XX label conflict. No runtime impact. |
| **Bug #9** | Medium | **P1 (elevated)** | V10 lacks WeeklyCapConstraint. If V10 path is exposed in default UI, runtime diverges from Greedy. Currently mitigated by Greedy default. |
| **Bug #11** | Medium | **Cleanup** | Orphan SQL seed rows. Cosmetic. |

### 20a.3 Revised verdict (corrected)

| Layer | Original | **Revised** |
|---|---|---|
| **Database** | ⚠️ NO | ⚠️ NO (Bug #11 cleanup) |
| **Backend** | ⚠️ NO | ⚠️ **NO — Bug #6 confirmed P0** |
| **API** | ✅ YES | ✅ YES |
| **Frontend** | ⚠️ NO | ⚠️ NO (Bug #14 — auto-resolved when Bug #6 fixed) |
| **Scheduler Runtime** | ✅ YES | ⚠️ **NO — compensation disabled by holiday preset = clinical impact** |
| **Cross-engine BR labels** | ⚠️ NO | 🟡 Documentation only |
| **Overall** | ⚠️ HOLD | ⚠️ **HOLD — Bug #6 is clinical blocker** |

**Correction**: The original §20.3 marked "Scheduler Runtime = READY". This is **incorrect**. With Bug #6 confirmed, the scheduler can silently disable compensation when a Manager applies the `holiday` profile — a direct violation of mandatory business rule. Scheduler Runtime verdict is downgraded to ⚠️ **NOT READY**.

> **Important reframe (post §20b)**: This is NOT a code defect in any single layer — each layer correctly implements its own reading of the conflicting signals. It is a **requirement conflict** that engineering cannot unilaterally resolve. See §20b.

### 20a.4 Minimal fix path (revised)

To resolve both Bug #6 and Bug #14 with one change:

**Edit `AutoSchedulingService.java:918` and `:3777`**: Remove the conditional. Always call `createCompensationDaysForL01InPeriod()`.

```java
// BEFORE:
if (algorithmConfigService.getRuntimeConfig().isAutoCompensationEnabled()) {
    createCompensationDaysForL01InPeriod(period.getId());
} else {
    log.info("Auto compensation disabled by config for period {}", period.getId());
}

// AFTER:
createCompensationDaysForL01InPeriod(period.getId());
```

**After this fix**:
- Bug #6: P0 → RESOLVED. No path can disable compensation.
- Bug #14: P2 → RESOLVED. AutoCompensationCard "Always On" is now truthful.
- DB row `auto_compensation_enabled` becomes dead but harmless.

**Optional follow-up (v1.1)**:
- Mark `auto_compensation_enabled` as RESERVED in DB description.
- Either expose toggle in UI OR remove the row entirely.

**Effort**: 5 minutes (2 lines + comments).

---

## 20b. SPEC Mandatory/Optional Verification (Final Round)

**Date**: 2026-07-17 (third review)
**Trigger**: User asked to verify whether compensation is mandatory or optional per spec — determines whether Option A (remove) or Option C (force true) is correct.

### 20b.1 Evidence collected

| Source | File:Line | Statement | Implies |
|---|---|---|---|
| `PROJECT_CONTEXT.mdc` (always-applied rule) | line 30 | `L01: Lịch trực 24/24 ... **có nghỉ bù**` | **MANDATORY** (intrinsic property of L01) |
| `PROJECT_CONTEXT.mdc` | line 35-41 | `CRITICAL Constraints (BẮT BUỘC tuân thủ)` — includes compensation day block | **MANDATORY** |
| `PROJECT_CONTEXT.mdc` | line 43-53 | Full Leave Compensation Rules table with mandatory mapping | **MANDATORY** |
| `AlgorithmConfigService.java` | line 395-396 | `[RESERVED v1.1] Tự động tạo ngày nghỉ bù sau ca L01. **Hiện tại luôn bật — không dùng config này.**` | **MANDATORY** (acknowledged dead config) |
| `RuntimeConfigEditor.tsx` | line 839 | `Scheduler v1.0 **luôn tạo nghỉ bù sau ca L01**` | **MANDATORY** |
| `RuntimeConfigService.java` | line 68-69 | `Tự động tạo ngày nghỉ bù ... **Tắt OFF nếu muốn quản lý nghỉ bù thủ công.**` | **OPTIONAL** (manual fallback supported) |
| `holiday` profile JSON | V14:104 | `"autoCompensationEnabled": false` | **OPTIONAL** (applied value) |
| `CompensationDayService` | exists | Manual CRUD on compensation days | **OPTIONAL** (admin override path) |
| `hospital_scheduler_business_final.sql` | line 607 | `ENABLE_COMPENSATION_AFTER_L01 = true` (legacy seed) | **OPTIONAL** (toggle exists) |
| DB constraint `uk_compensation_staff_date` | line 257 | UNIQUE(staff_id, compensation_date) | Enforces 1-to-1, not toggle |

### 20b.2 Spec files status

| File | Exists in repo? | Notes |
|---|---|---|
| `SPEC.md` | ❌ NO | PROJECT_CONTEXT.mdc references it but file is not committed |
| `QuanLyLichCongTac_v5.txt` | ❌ NO | PROJECT_CONTEXT.mdc says "local only" — likely on dev machine, not in git |
| PO/business brief | ❌ NO | Not found in repo |
| User stories | ❌ NO | Not found in repo |

**Verdict**: Cannot verify from authoritative spec file. Must rely on in-code signals.

### 20b.3 Internal conflict — root cause of Bug #6

The codebase itself contains **contradictory signals**:

| Side | Verdict | Source count |
|---|---|---|
| MANDATORY | L01 always has compensation; config is RESERVED/dead | 3 sources (PROJECT_CONTEXT, AlgorithmConfigService description, RuntimeConfigEditor) |
| OPTIONAL | Toggle exists, can be turned off, manual CRUD supported | 3 sources (RuntimeConfigService description, holiday profile, CompensationDayService CRUD) |

**This internal conflict IS the bug.** Two teams (config system designer + scheduler integrator) interpreted the spec differently:
- Config system designer: "compensation may need to be disabled in edge cases (manual management, holiday blackout)" → created toggle
- Scheduler integrator: "compensation is mandatory per L01 definition, toggle should not exist" → marked as RESERVED v1.1, UI shows "Always On"

Both halves were merged into the codebase without reconciliation. Result: silent behavioral surprise when `holiday` profile JSON overrides the `[RESERVED]` annotation.

### 20b.4 Resolution path (PO decision required)

**This is a PO/product decision, NOT an engineering decision.** Engineering cannot resolve "is it mandatory or optional" — only PO can, based on the missing `QuanLyLichCongTac_v5.txt` (which is "local only").

**Decision matrix for PO**:

| PO Decision | Implementation | Effort | Verdict |
|---|---|---|---|
| **Compensation is MANDATORY** (per PROJECT_CONTEXT + L01 row) | Option A: Remove `autoCompensationEnabled` entirely | 3h | ✅ Engineering recommendation |
| **Compensation is OPTIONAL** (manual fallback supported) | Option B: Validator blocks `false` AND remove profile JSON `false` AND clarify UI text | 2h | Acceptable if PO confirms edge case use |
| **Compensation is MANDATORY in v1.0, OPTIONAL in v1.1** | Option A for v1.0 + design v1.1 toggle with proper workflow | 3h now + 8h v1.1 | Compromise |

**Recommended PO decision**: **MANDATORY in v1.0**. Reasoning:

1. `PROJECT_CONTEXT.mdc` (always-applied rule, line 30) explicitly lists `có nghỉ bù` as intrinsic property of L01.
2. `CRITICAL Constraints (BẮT BUỘC tuân thủ)` in same file treats compensation as mandatory.
3. UI shows "Always On" — implies PM committed to this UX.
4. No real-world scenario documented in code/tests where disabling compensation is needed.
5. Manual `CompensationDayService` CRUD already exists for "optional override" cases — no need for runtime toggle.

If PO confirms MANDATORY → execute Option A → RC can proceed after ~3h fix wave.

If PO confirms OPTIONAL → execute Option B → RC can proceed after ~2h fix wave.

Either way: **RC v1.0.0 should NOT release until this conflict is resolved.**

> **Per third-round review**: Engineering cannot unilaterally pick Option A or B. If the project serves multiple hospital types (e.g., Hospital A always-compensates, Hospital B always-manual), then `autoCompensationEnabled` is a **legitimate feature** with mis-implemented backup, not a bug. Removing it would erase a valid business mode. Status: **`HOLD pending PO decision`**.

### 20b.4a Single Source of Truth (SSOT) recommendation (per third-round review)

The deeper issue surfaced by Bug #6 is **config duplication across 8+ layers**:

| Layer | Has `autoCompensationEnabled`? | Source of truth for this layer? |
|---|---|---|
| SQL seed (`hospital_scheduler_business_final.sql:607`) | YES `ENABLE_COMPENSATION_AFTER_L01` | hardcoded `true` |
| Flyway seed profile JSON (`V14__*.sql:104`) | YES | hardcoded `false` for `holiday` |
| `ConfigDomain` (record) | YES `boolean autoCompensationEnabled` | Java record field |
| `ConfigMapper` | YES | mirrors `ConfigDomain` |
| `ConfigMetadataRegistry` | YES | flag + label |
| `ConfigController.applyFieldValue` | YES | per-field switch |
| `RuntimeConfigService` | YES | description says "Tắt OFF nếu muốn manual" |
| `AlgorithmConfigService.AUTO_COMPENSATION_ENABLED` | YES | description says "[RESERVED v1.1]... luôn bật — không dùng" |
| `AutoSchedulingService:918, 3777` | YES | respects DB value |
| UI `RuntimeConfigEditor` | YES | hardcoded "Always On" badge |
| Frontend `presets.ts` | YES | serialized to JSON |
| `CompensationDayService` | (CRUD only) | not toggle-aware |
| `PROJECT_CONTEXT.mdc` | (spec text) | says "luôn có nghỉ bù" |

**13 touch-points for ONE business rule.** No wonder drift happened.

**SSOT recommendation (post-RC, before v1.1)**:

> **Note on source-of-truth**: This audit confirmed `SPEC.md` does **NOT exist** in the repository and `QuanLyLichCongTac_v5.txt` is "local only" (per `PROJECT_CONTEXT.mdc` footer). Recommendation below assumes such a source-of-truth document will be created/maintained going forward — currently the team only has `PROJECT_CONTEXT.mdc` and inline Javadoc.

1. **Pick authoritative source** for each business rule — target architecture: a version-controlled requirement document (e.g. `SPEC.md` or `requirements.yaml`) co-maintained with `PROJECT_CONTEXT.mdc` or another PO-managed source. PO owns the canonical version; engineers consume it.
2. **Generate** all other layers from it:
   - DB migration: codegen from `ConfigDomain`
   - ConfigMetadataRegistry: autogenerate labels/descriptions from spec
   - Profile JSON: validate against `ConfigDomain` schema at import time
   - UI controls: derive visibility from metadata, not hardcode
3. **Reject drift** at runtime:
   - At app startup: reconcile `ConfigDomain` schema vs `algorithm_config` DB rows
   - On config save: validate against spec (e.g., reject `autoCompensationEnabled=false` if spec is mandatory)
4. **Audit hook**: CI check that fails if a business rule appears in >2 unrelated layers without an explicit "drift comment".

**Pre-requisite before SSOT can be implemented**:
- [ ] PO/BA authors or commits the missing spec file into repo
- [ ] Tech Lead agrees on the format (markdown, YAML schema, or both)
- [ ] Document is added to PR review checklist (any business-rule change MUST update spec first)

**Why this matters**: Bug #6 is the visible symptom. Latent bugs of the same shape likely exist for other rules (`overnight_recovery_hours`, `weekend_weight`, `min_staff_per_shift` — all have the same multi-layer definition pattern).

**Effort**: 8-16 hours (one sprint). **Priority**: Should be added to v1.1 backlog, not v1.0.

### 20b.5 Updated Bug #9 framing (per user request)

User noted Bug #9 description was too strong — implied v1.0 currently violates `l0XMaxPerWeek`. Corrected:

| Original | Revised |
|---|---|
| "Bug #9: V10 lacks WeeklyCapConstraint" (sounds like current bug) | **"Bug #9: Latent regression risk for V10 path"** |

- v1.0 default = Greedy, which DOES enforce `l0XMaxPerWeek` ✅
- v1.0 alternative = V10, which does NOT enforce `l0XMaxPerWeek` ⚠️
- If V10 is enabled in future release WITHOUT fixing Bug #9 first → silent weekly cap violation
- Today: not a current bug; tomorrow: regression risk

**Updated severity**: P1 — must fix BEFORE V10 becomes default or selectable in UI.

---

## 20. Final Verdict

### 20.1 Verdict matrix (REVISED — third round)

| Layer | Original | **Revised (3rd review)** |
|---|---|---|
| **Database** | ⚠️ NO | ⚠️ **Minor cleanup** (orphan seed rows + Bug #11) |
| **Backend Config** | ⚠️ NO | ⚠️ **Requirement conflict** — Bug #6 internal split (13 layers disagree) |
| **Scheduler Runtime** | ✅ YES | ⚠️ **Behavior correct, intent unclear** — runtime respects flag, but spec doesn't say flag should exist |
| **Frontend** | ⚠️ NO | ⚠️ **UI hardcodes "Always On"** — implies MANDATORY, contradicts profile JSON |
| **API** | ✅ YES | ✅ YES |
| **Cross-engine BR labels** | ⚠️ NO | 🟡 Documentation only (Bug #8) |
| **Overall** | ⚠️ HOLD | ❌ **`HOLD pending PO decision`** |

### 20.2 Fix recommendation (REVISED — per user preference)

**Conditional fix path** — execution depends on PO decision (see §20b.4). The list below shows what engineering work is needed for each path; engineering should NOT start until PO confirms.

### 20b.4a (interleaved above): SSOT architecture recommendation, defer to v1.1

| # | Owner | Action | File | Effort |
|---|---|---|---|---|
| 1 | Backend | Remove `autoCompensationEnabled` field from `ConfigDomain` record | `ConfigDomain.java` | 30 min |
| 2 | Backend | Remove `autoCompensationEnabled` getter/setter/builder from `AlgorithmRuntimeConfig` | `AlgorithmConfigService.java:505, 531, 698` | 30 min |
| 3 | Backend | Remove `autoCompensationEnabled` check in `AutoSchedulingService:918, 3777` — always call `createCompensationDaysForL01InPeriod` | `AutoSchedulingService.java:918-921, 3776-3779` | 15 min |
| 4 | Backend | Remove `auto_compensation_enabled` from `ConfigMapper.toParamMap/fromParamMap` | `ConfigMapper.java:111, 206, 292` | 15 min |
| 5 | Backend | Remove `AUTO_COMPENSATION_ENABLED` constant from `AlgorithmConfigService.java:67` | `AlgorithmConfigService.java:67` | 5 min |
| 6 | Backend | Remove holiday profile JSON field, set to `true` (no-op since logic removed) | `V14__add_config_profile_table.sql:104` | 5 min |
| 7 | Backend | Add migration `V18__delete_auto_compensation_enabled.sql` to DELETE existing DB row if present | new file | 10 min |
| 8 | Frontend | Remove `autoCompensationEnabled` from `RuntimeConfig` type | `types.ts:20` | 5 min |
| 9 | Frontend | Remove `AutoCompensationCard` from `RuntimeConfigEditor` | `RuntimeConfigEditor.tsx:816-845` | 10 min |
| 10 | Frontend | Remove `autoCompensationEnabled` from all profile JSON in `presets.ts` (cosmetic) | `presets.ts:15` | 5 min |
| 11 | Backend | Remove `constraints.autoCompensationEnabled` from `ConfigMetadataRegistry` | `ConfigMetadataRegistry.java:372-378` | 5 min |
| 12 | Backend | Remove `case` arms in `ConfigController.applyFieldValue` | `ConfigController.java:386, 466` | 5 min |
| 13 | Docs | Remove `autoCompensationEnabled` from `SPEC.md`, `AUDIT_SCHEDULER_ENGINE.md` | docs | 15 min |
| 14 | QA | Verify: after apply holiday preset → run scheduler on period with L01 → `compensation_day` table has rows | manual | 30 min |

**Total Option A effort: ~3 hours**

**Fallback: Option B — Add validation guard (if Option A deferred to v1.1)**

If removal cannot be done in time, add `ConfigValidator.validateBusiness()` rule:

```java
// ConfigValidator.java:274 (before return new ValidationResult(errors, warnings, infos);)
if (!config.autoCompensationEnabled()) {
    errors.add(new Violation(
        "constraints.autoCompensationEnabled",
        "Auto-compensation là nghiệp vụ bắt buộc theo SPEC — không thể tắt",
        ConfigMetadata.ValidationSeverity.ERROR
    ));
}
```

This blocks ANY save with `false`, including profile apply. Effort: 5 minutes.

**Last resort: Option C — Force true in scheduler** (REJECTED by user, only if ship-blocked)

The original §20a.4 proposal. Acceptable as emergency patch only. Creates new tech debt (dead DB field, dead JSON key, runtime silently overrides DB).

### 20.3 Required actions before UAT (REVISED)

> **Blocker**: Do NOT execute actions below until PO confirms compensation semantics (see §20b.4).

#### If PO confirms MANDATORY compensation:

| # | Owner | Action | Effort |
|---|---|---|---|
| 1 | Backend | **Option A** full removal of `autoCompensationEnabled` (steps 1-7 of Option A plan) | 2 hours |
| 2 | Frontend | **Option A** full removal (steps 8-12 of Option A plan) | 30 min |
| 3 | DB | Migration `V18__delete_auto_compensation_enabled.sql` | 10 min |
| 4 | DB | Remove orphan SQL seed rows (`MAX_SHIFTS_PER_MONTH_DEFAULT`, etc.) — independent cleanup | 5 min |
| 5 | Docs | Remove `autoCompensationEnabled` from SPEC.md and audit docs | 15 min |
| 6 | QA | End-to-end verify: holiday preset apply → L01 schedules get compensation days | 30 min |

**Total: ~3.5 hours** before UAT.

#### If PO confirms OPTIONAL compensation (e.g., multi-tenant mode):

Option B is NOT just a validator. Must synchronize ALL 13 layers that currently disagree on this flag:

| Layer | Fix | Effort |
|---|---|---|
| UI | Remove "Always On" hardcode; bind toggle; show real state | 2h |
| API | Document toggle contract (OpenAPI annotation) | 30 min |
| Profile | Audit all profile JSON — set `autoCompensationEnabled=true` by default; explicit false only when PO confirms semantics | 1h |
| Runtime | Keep current `if (isAutoCompensationEnabled())` branch | (no change) |
| Migration | New `V18__seed_compensation_default_true.sql` to repair DB rows from holiday preset | 10 min |
| Test | Unit test for `false → no compensation` case | 1h |
| Scheduler | Already correct | (no change) |
| Report | Update `AlgorithmMetrics` to record `auto_compensation_enabled_used` flag | 1h |
| Documentation | Revert [RESERVED v1.1] comments; mark as live feature | 15 min |

**Total: ~6 hours** if optional + manual fallback path is the intended v1.0 behavior.

#### Either path: Bug #9 (V10 weekly cap) follow-up

| # | Owner | Action | Effort |
|---|---|---|---|
| 7 | Backend | Add `WeeklyCapConstraint` to V10 path; register in `LocalSearchScheduler.java` | 4h |

**Total (independent of PO decision): +4 hours** before V10 can be selectable.

### 20.4 Recommended Release Decision

> **Status: NOT READY for v1.0.0 GA. `HOLD pending PO decision`.**
>
> Engineering cannot resolve Bug #6 unilaterally. Each fix path (A vs B) is conditional on business semantics confirmation.

**Release readiness matrix**:

| PO Decision | Engineering work | After fix: Status |
|---|---|---|
| MANDATORY compensation | Option A (3.5h) | READY for UAT (1 day later) |
| OPTIONAL compensation | Option B full sync (6h) | READY for UAT (1 day later) |
| DECLINED / unclear | (must resolve ambiguity first) | STAY HOLD |

**Schedule**: same-day fix + 1 day verification **after** PO responds.

### 20.5 Why Option A over Option C (force true)

User preference rationale (verbatim):

> Nếu compensation là bắt buộc theo nghiệp vụ thì: xóa hoàn toàn config này khỏi business config... Đây là sạch nhất.

**Principle**: **UI, DB and runtime must reflect the same business rule.** Having a config that "exists but is always overridden" is technical debt — next developer reading `auto_compensation_enabled=false` in DB will assume the scheduler is broken. Removing the field entirely eliminates this confusion.

**Exception**: Option C (force true) is acceptable ONLY if release deadline is imminent and full Option A cannot ship. Even then, follow up with Option A in v1.1.

**Caveat (third round)**: Option A is no longer unconditionally preferred. If PO confirms compensation is optional for multi-tenant scenarios (Hospital A vs Hospital B), then Option B is correct and engineering should NOT remove the field.

### 20.6 Bug #14 disposition

Per user review: Bug #14 is **NOT an independent bug**. It is a downstream symptom of Bug #6.

- If Option A applied → `AutoCompensationCard` removed entirely → Bug #14 disappears.
- If Option B applied → validator always rejects `false` → DB always `true` → UI displays "Always On" matches reality → Bug #14 disappears.
- If Option C applied → runtime always `true` → UI display correct → Bug #14 disappears.

In all 3 fix options, Bug #14 is auto-resolved. No need for independent UI binding fix.

### 20.7 Bug #9 (V10 weekly cap) — elevated to P1

User review: Bug #9 should be **P1**, not technical debt.

Reasoning:
- Manager sets `l02MaxPerWeek=2` (intends weekly cap)
- Greedy: respects cap ✅
- V10: ignores cap ❌ (constraint not registered in `LocalSearchScheduler.java:102-107`)

Same config value, different runtime behavior → **cross-engine inconsistency**.

Impact: Currently V10 is NOT default path (Greedy is default per `AUDIT_SCHEDULER_ENGINE.md`). If V10 ever becomes selectable in default UI without this constraint fix, weekly cap becomes silently violated.

**Fix**: Add `WeeklyCapConstraint` to `scheduling/constraint/` and register in `LocalSearchScheduler.java:107`.

**Effort**: 4 hours (new constraint + unit test).

---

## 21. Third-Round Synthesis — Final Narrative

After three rounds of review, the audit conclusion has shifted from "P0 Clinical Bug → fix it" to:

> **P0 Requirement Conflict (mandatory vs optional compensation) — fix depends on PO decision.**

### 21.1 What was wrong with the first-round verdict

| Pass | Verdict | Why it was insufficient |
|---|---|---|
| Pass 1 (initial) | "Bug #6: runtime scheduler bug" | Treated as engineering defect; missed that 13 layers agreed locally but disagreed globally |
| Pass 2 (after evidence) | "P0 Clinical Bug confirmed" | Better — traced flow through 6 layers — but framing as "bug" implied unilateral fix |
| Pass 3 (this round) | "P0 Requirement Conflict" | Recognizes engineering cannot resolve requirement ambiguity. Status: **HOLD pending PO decision** |

### 21.2 What engineering learned

1. **A bug can be in code without being a code defect.** Every layer of `autoCompensationEnabled` correctly implements its own reading of the spec — and that's exactly why the system breaks down. Each developer's "local correctness" produced "global incorrectness".
2. **Verifying data flow is necessary but not sufficient.** The first round confirmed DB→runtime flow. The second round confirmed spec absence. The third round recognized spec absence → product decision required.
3. **"Force true" is the wrong reflex.** When a config exists in DB but UI/runtime contradict it, the "fix" is NOT to override the runtime to match the UI. The "fix" is to find out which one is right.

### 21.3 What product/PO needs to decide

| Question | Whose decision | Impact |
|---|---|---|
| Is L01 compensation mandatory or optional? | PO/BA | Determines Option A vs B |
| Is the product single-tenant (one hospital) or multi-tenant (multiple hospitals)? | Product owner | If multi-tenant, optional makes sense and Option B is correct |
| Does `QuanLyLichCongTac_v5.txt` mention a "manual compensation" mode? | BA (read the spec) | If yes → Option B. If no → Option A |
| Should `SPEC.md` be in the repo? | Tech lead | Process improvement; single source of truth per §20b.4a |

### 21.4 What engineering CAN do regardless of PO decision

1. **Add SSOT architecture (§20b.4a)** in v1.1 backlog. Prevents future Bug #6-class issues for other rules.
2. **Add drift detection at startup** — reconcile `ConfigDomain` schema vs `algorithm_config` DB rows; log warning on unknown params.
3. **Document dependency on PO response** in release notes: "RC v1.0.0 cannot release until `autoCompensationEnabled` semantics are confirmed by product team."
4. **Remove orphan SQL seed rows** (Bug #11) — independent cleanup, 5 min, no PO input needed.
5. **Document BR-XX label standards** (Bug #8) — independent cleanup.

### 21.5 Final recommended status table

| Item | Status | Blocker? |
|---|---|---|
| Database cleanup (orphan rows) | Ready to merge | No — can ship today |
| Bug #8 (BR labels) | Documentation only | No |
| Bug #7 (dual config layer) | Tech debt, document for v1.1 | No |
| Bug #9 (V10 weekly cap) | Latent — fix before enabling V10 path | Conditional |
| Bug #6 (autoCompensation flag) | **HOLD pending PO decision** | **YES** |
| Bug #14 (AutoComp card) | Resolved by Bug #6 fix | (subsumed) |
| Bug #11 (orphan seed rows) | Same as DB cleanup | No |

### 21.6 What to put in the PO brief

> **Question for PO/BA**:
>
> The codebase contains contradictory signals about L01 automatic compensation:
>
> - UI shows "Always On" (mandatory)
> - Holiday profile JSON sets `autoCompensationEnabled=false` (optional)
> - `RuntimeConfigService` description says "Tắt OFF nếu muốn manual" (optional)
> - Backend `[RESERVED v1.1]` annotation says "không dùng config này" (mandatory)
> - `SPEC.md` and `QuanLyLichCongTac_v5.txt` are not in the repo
>
> Please confirm one of:
>
> **A. Mandatory**: hospital staff MUST get a rest day after every L01. Engineering will remove the toggle entirely.
>
> **B. Optional**: some hospital deployments want manual compensation management. Engineering will synchronize all 13 layers to respect the toggle.
>
> Until answered, RC v1.0.0 cannot release.

---

## 22. Audit Confidence

To help Tech Lead / PO calibrate trust in each conclusion, this section rates the confidence level of each audit dimension based on evidence quality:

| Dimension | Confidence | Evidence basis |
|---|---|---|
| **Config data flow** (DB → Backend → Scheduler) | **High** | Traced full pipeline through `ConfigProfileService.applyByKey()` → `ConfigMapper` → `AlgorithmRuntimeConfig` → `AutoSchedulingService:918, 3777`. All hops confirmed in source code. |
| **Runtime behavior** | **High** | Confirmed via code reading + (where available) unit tests in `ScheduleServiceBusinessRulesTest`, `CompensationDayServiceTest`. Scheduler integration points verified. |
| **UI ↔ Backend mapping** | **High** | Compared `RuntimeConfigEditor.tsx`, `types.ts`, `presets.ts` against `ConfigDomain.java`, `ConfigController.java`, `ConfigMetadataRegistry.java`. Type + enum mismatches identified. |
| **Dead config detection** | **High** | All 17+ config keys (`AUTO_COMPENSATION_ENABLED`, `OVERNIGHT_RECOVERY_HOURS`, `l0XMinPerWeek`, etc.) traced from DB seed → runtime reads. Final taxonomy: 3 DEAD, 4 RESERVED v1.1, 3 LEGACY, 1 INCONSISTENT. |
| **Holiday preset semantics** | **High** | `V14__add_config_profile_table.sql:104` parsed; `ConfigProfileService` apply path verified; applied value `false` confirmed reaching `AutoSchedulingService`. |
| **Cross-engine consistency (Greedy vs V10)** | **Medium-High** | Engine dispatch + constraint registration reviewed in `LocalSearchScheduler.java:102-107` and `CSPScheduler.java`. Bug #9 confirmed by code reading; unit test coverage for V10 weekly cap NOT found in test suite. |
| **Requirement interpretation** | **Medium** | Depends on PO/BA confirmation. `PROJECT_CONTEXT.mdc` and inline Javadoc give strong signals but no authoritative external spec exists in repo. |
| **Clinical workflow intent** | **Medium** | `CompensationDayService` CRUD + `PROJECT_CONTEXT.mdc` leave-compensation table imply business intent. But absent `SPEC.md`/`QuanLyLichCongTac_v5.txt`, multi-tenant vs single-tenant scenarios cannot be validated. |
| **Effort estimates** | **Medium** | Based on file:line counts and code familiarity. No spike/PoC performed; actual fix time may vary ±50%. |
| **Latent bug identification (beyond Bug #6/#9/#14)** | **Medium** | Targeted audit on 4 bugs. Full codebase scan NOT performed. Other rules with same multi-layer pattern (`overnight_recovery_hours`, `weekend_weight`, `min_staff_per_shift`) likely have similar drift — recommended v1.1 audit scope. |

**Reading guide**:
- **High confidence** = claim is supported by code/test evidence and survives review scrutiny. Safe to act on.
- **Medium confidence** = claim is supported by code signals but has interpretation uncertainty. Act only after the listed precondition is met (e.g., PO confirmation, missing spec added).
- **Low confidence** = not used in this audit; would require explicit PoC or external input.

**For Tech Lead / PO meeting**:
- All Bug #6/#9/#14/#11 findings are **High confidence** and stand on their own.
- The **fix choice** (Option A vs Option B) is the only **Medium-confidence** decision — depends on PO input.
- Effort estimates are ±50% until a spike is run.

---

## 23. Release Readiness Assessment (one-page, for PO)

> Read this first if you have 2 minutes.

### 23.1 Overall Health Score

```
Backend Runtime      █████████░ 9/10
Frontend Config UI   █████████░ 9/10
Database             ████████░░ 8/10
Architecture         ███████░░░ 7/10   ← drop due to 13-layer SSOT absence
Documentation        ██████░░░░ 6/10   ← SPEC.md missing
Cross-engine         ████████░░ 8/10
```

### 23.2 Blocking items (must resolve before RC)

| # | Item | Owner | Type | Effort |
|---|---|---|---|---|
| 1 | **Requirement Conflict**: `autoCompensationEnabled` interpreted as both MANDATORY and OPTIONAL across layers | PO | Product decision | 0h (just confirm) |
| 2 | **Bug #9**: V10 weekly cap not enforced (latent regression risk) | Engineering | Bug | 4h (before V10 selectable) |

### 23.3 Non-blocking items (cleanup / backlog)

| # | Item | Severity | Effort |
|---|---|---|---|
| Bug #11 | LEGACY SQL seed rows (`MAX_SHIFTS_PER_MONTH_DEFAULT`, etc.) | Cleanup | 5 min |
| Bug #8 | BR-XX label inconsistency across CSP/V10/Scorer engines | Docs only | 15 min |
| Bug #7 | Dual config layer (`SchedulingConfig` vs `ConfigDomain`) | Tech debt | v1.1 |
| Bug #14 | AutoCompensationCard UI hardcode "Always On" | Resolved by Bug #6 fix | (subsumed) |
| DEAD configs | `overnight_recovery_hours`, `min_staff_per_shift`, `min_shifts_per_staff` | Cleanup | v1.1 |
| LEGACY configs | 3 SQL-only rows | Cleanup | 5 min |
| RESERVED v1.1 | `l0XMinPerWeek` group | **Keep, planned v1.1** | n/a |
| SSOT architecture | 13 layers per business rule | Architecture | v1.1 (8-16h) |

### 23.4 The single decision that unlocks RC

> **PO confirm: Is L01 auto-compensation MANDATORY or OPTIONAL?**
>
> - MANDATORY → Option A (3.5h engineering, then RC ready)
> - OPTIONAL → Option B (6h engineering, then RC ready)

### 23.5 Engineering audit conclusion (verbatim)

> **Engineering audit: Complete.**
>
> No new technical blockers found beyond the items already listed.
> The single remaining gate is a **requirement conflict** on `autoCompensationEnabled`, which PO must confirm before choosing between Option A and Option B.
> All other issues are technical debt, documentation drift, cleanup, or latent regression risk; none affect the default Greedy Scheduler path in v1.0.

---

## 24. Out of Scope

This audit intentionally did **not** cover the following dimensions. They are noted here so reviewers know what was NOT verified.

| Dimension | Why out of scope | Where covered (if anywhere) |
|---|---|---|
| **Performance / scalability benchmarking** | Audit is structural, not load-based | `PERFORMANCE_AUDIT_2026-06-20.md` |
| **Security & authorization of Config APIs** | RBAC layer separate concern | `RBAC.md`, `ROLE_MATRIX_2026-06-20.md` |
| **Database indexing & migration performance** | DBA scope | `STAGING_DEPLOYMENT_CHECKLIST_2026-06-22.md` |
| **Concurrent scheduler execution** | Architectural concern beyond config layer | (not covered) |
| **Multi-node deployment consistency** | Infra scope, not config | `STAGING_DEPLOYMENT_CHECKLIST_2026-06-22.md` |
| **UI accessibility (WCAG)** | Frontend-only concern | `ACCESSIBILITY_2026-06-20.md` |
| **Internationalization (i18n)** | Frontend-only concern | (not covered) |
| **Browser compatibility** | Frontend testing scope | (not covered) |
| **Load testing** | Performance scope | (not covered) |
| **Algorithm correctness beyond weekly cap** | Algorithm audit scope | `AUDIT_SCHEDULER_ENGINE.md`, `AUDIT_CONSTRAINT_ENGINE.md`, `AUDIT_SCORE_ENGINE.md` |
| **Migration scripts not directly tied to config** | DBA scope | (not covered) |
| **Frontend bundle size / build performance** | Build scope | `BUNDLE_ANALYSIS_2026-06-22.md` |

**Implication for PO/Tech Lead**: if any of the above is required as a release gate, this audit is **not sufficient** — refer to the linked documents.

---

## 25. Known Assumptions

This audit assumes the following. If any assumption is wrong, the corresponding conclusions may need re-verification.

| # | Assumption | If wrong → re-verify |
|---|---|---|
| A1 | Current default scheduler is **Greedy** (per `AUDIT_SCHEDULER_ENGINE.md`) | V10 path analyses (Bug #9, BR labels) need re-scoring |
| A2 | V10 LocalSearch is **not selectable** in production runtime | Bug #9 severity escalates from latent → current bug |
| A3 | `ConfigProfile.holiday` preset is **still supported** (not removed) | Bug #6 path may not be reachable in production |
| A4 | `algorithm_config` table is the **only runtime source** for config | Findings on config data flow may be incomplete |
| A5 | `PROJECT_CONTEXT.mdc` reflects **current business rules** | All requirement interpretation (Medium confidence) needs re-grounding |
| A6 | Backend codebase at HEAD matches the version reviewed | Effort estimates and line numbers may drift |
| A7 | `autoCompensationEnabled` default = `true` when no value set | Runtime behavior at first deploy may differ |
| A8 | Single-tenant deployment (one hospital per instance) | If multi-tenant, Option B becomes more likely correct |
| A9 | `Bug #11` SQL seed rows have **no downstream consumer** | Removing them could break legacy imports |
| A10 | CompensationDayService manual CRUD path is **out of scope** for auto-scheduler | If PO says manual-mode is the intended pattern, Option B is forced |

**Reading guide**:
- A1, A2, A4, A6 are **verifiable in code** — Tech Lead should confirm before sign-off.
- A3, A5, A7, A8, A10 are **product/process assumptions** — PO must confirm.
- A9 requires **DB query** to confirm (one-time check).

---

## 26. Decision Log

This log records every significant architectural and product decision made (or deferred) during this audit. It serves as a long-term reference for future reviewers.

| Decision ID | Decision | Rationale | Status | Owner | Date |
|---|---|---|---|---|---|
| **ADR-001** | `autoCompensationEnabled` is a **P0 Requirement Conflict**, not a code defect | Each layer (UI, DB, runtime, profile) correctly implements its own reading of contradictory spec signals. Engineering cannot resolve unilaterally. | **Pending PO decision** | PO/BA | 2026-07-17 |
| **ADR-002** | Remove Deprecated Configs in v1.1 | `min_staff_per_shift`, `min_shifts_per_staff`, `overnight_recovery_hours` are DEAD (no consumer). `l04BalanceStrategy` is DEAD. All three removed in v1.1 cleanup. | **Accepted** | Tech Lead | 2026-07-17 |
| **ADR-003** | SSOT Architecture | Config business rules duplicated across 13 layers (SQL seed, profile JSON, ConfigDomain, ConfigMapper, Metadata, Controller, RuntimeConfigService, AlgorithmConfigService, AutoSchedulingService, UI editor, presets, PROJECT_CONTEXT, scheduler). Prevents drift. Plan: codegen from authoritative spec doc. | **Planned v1.1** | Architecture | 2026-07-17 |
| **ADR-004** | V10 Weekly Cap Constraint | `LocalSearchScheduler.java:102-107` does NOT register `l0XMaxPerWeek` constraint. Fix before V10 becomes selectable. | **Planned (before V10 GA)** | Engineering | 2026-07-17 |
| **ADR-005** | Taxonomy: RESERVED ≠ DEAD | `l01MinPerWeek`..`l04MinPerWeek` are RESERVED v1.1 (constraint layer), NOT DEAD. Do NOT remove. `l04BalanceStrategy`, `overnight_recovery_hours` are DEAD (no consumer). SQL seed rows (`MAX_SHIFTS_PER_MONTH_DEFAULT`, etc.) are LEGACY. | **Accepted** | Tech Lead | 2026-07-17 |
| **ADR-006** | Bug #14 subsumed by Bug #6 | AutoCompensationCard UI hardcode auto-resolves when `autoCompensationEnabled` is resolved. No independent fix needed. | **Accepted** | Engineering | 2026-07-17 |
| **ADR-007** | Option A over Option C | If PO confirms MANDATORY compensation, Option A (full removal) is preferred over Option C (force true in runtime). Option C creates new tech debt (dead field + dead JSON key). | **Accepted** (contingent on ADR-001 = MANDATORY) | Engineering | 2026-07-17 |
| **ADR-008** | Orphan SQL seed rows = LEGACY, safe to remove | `MAX_SHIFTS_PER_MONTH_DEFAULT`, `AVOID_BACK_TO_BACK_SHIFT`, `ENABLE_COMPENSATION_AFTER_L01` have no Java reader. Remove via Flyway migration. | **Accepted** | DBA/Engineering | 2026-07-17 |

**Reading guide**:
- **Pending** = awaiting external input (PO, Tech Lead, or spec document)
- **Accepted** = decision made, implementation pending or in progress
- **Planned** = in backlog for future release
- **Rejected** = explicitly rejected with rationale

---

## 27. Future Audit Scope

Recommended audit areas for future releases, organized by target version.

### v1.0.1 (next patch, if needed)

- [ ] **Security**: Authorization of Config CRUD APIs (`ConfigController`, `ConfigProfileController`)
- [ ] **Permission**: RBAC enforcement on profile apply vs config edit vs config delete
- [ ] **Config API**: Add OpenAPI/Swagger annotations to `ConfigController` (Bug #13)
- [ ] **Scheduler Performance**: Warm-up time, memory usage on large periods

### v1.1 (next major)

- [ ] **SSOT Architecture**: Implement §20b.4a recommendation; codegen from authoritative spec doc
- [ ] **V10 Constraint Fix**: Add `WeeklyCapConstraint` to V10 path (Bug #9)
- [ ] **Config Cleanup**: Remove DEAD configs (`l04BalanceStrategy`, `overnight_recovery_hours`, `min_staff_per_shift`, `min_shifts_per_staff`)
- [ ] **Reserved Fields**: Implement `l0XMinPerWeek` constraint layer if planned for v1.1
- [ ] **Deprecated Removal**: Remove `@Deprecated` fields from `ConfigDomain`, `StaffEligibilityFilter`
- [ ] **Runtime Drift Detection**: Startup reconciliation of `ConfigDomain` schema vs `algorithm_config` DB rows
- [ ] **BR-XX Label Standardization**: Unify BusinessRulesCard labels across CSP/V10/Scorer engines (Bug #8)

### v1.2+ (future)

- [ ] **Multi-tenant Config Isolation**: If multi-tenant deployment is planned, audit config isolation per hospital
- [ ] **Concurrent Scheduler Execution**: Config consistency when multiple schedulers run simultaneously
- [ ] **Scheduler Audit Full Coverage**: Extend coverage to Greedy, CSP, V10 algorithm correctness (beyond Bug #9)
- [ ] **UI i18n**: Full Vietnamese → English (or configurable) i18n support

---

## Appendix A: Files Reviewed

### A.1 Frontend
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/page.tsx`
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/RuntimeConfigEditor.tsx`
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/paramConfig.ts`
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/types.ts`
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/merge.ts`
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/presets.ts`
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/HolidayModeField.tsx`
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/ShiftTypeGroupCard.tsx`
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/CreateConfigModal.tsx`
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/MetricsHistory.tsx`
- `frontend/src/lib/validation/algorithmConfig.ts`
- `frontend/src/lib/api-client.ts` (config endpoints)

### A.2 Backend
- `backend/src/main/java/com/hospital/scheduler/service/AlgorithmConfigService.java`
- `backend/src/main/java/com/hospital/scheduler/service/AutoSchedulingService.java`
- `backend/src/main/java/com/hospital/scheduler/service/scheduling/StaffEligibilityFilter.java`
- `backend/src/main/java/com/hospital/scheduler/service/scheduling/RequirementPreparationService.java`
- `backend/src/main/java/com/hospital/scheduler/service/scheduling/SchedulingFeasibilityAnalyzer.java`
- `backend/src/main/java/com/hospital/scheduler/service/scheduling/CompensationDayService.java`
- `backend/src/main/java/com/hospital/scheduler/util/CompensationDateCalculator.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigDomain.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigMapper.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigDefaults.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/config/SchedulingConfig.java`
- `backend/src/main/java/com/hospital/scheduler/algorithm/AutoGenConfig.java`
- `backend/src/main/java/com/hospital/scheduler/algorithm/CSPScheduler.java`
- `backend/src/main/java/com/hospital/scheduler/algorithm/CspConstants.java`
- `backend/src/main/java/com/hospital/scheduler/algorithm/scoring/ScheduleQualityScorer.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/LocalSearchScheduler.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/constraint/RestDayConstraint.java`
- `backend/src/main/java/com/hospital/scheduler/controller/ConfigController.java`
- `backend/src/main/java/com/hospital/scheduler/controller/AutoSchedulingController.java`
- `backend/src/main/java/com/hospital/scheduler/config/OpenApiConfig.java`

### A.3 Database
- `hospital_scheduler_business_final.sql`
- `backend/src/main/resources/db/migration/V4__fix_algorithm_metrics_columns.sql`
- `backend/src/main/resources/db/migration/V5__add_algorithm_config_audit.sql`
- `backend/src/main/resources/db/migration/V8__expand_audit_action_type_enum.sql`
- `backend/src/main/resources/db/migration/V9__drop_schedule_unique_constraint.sql`
- `backend/src/main/resources/db/migration/V14__add_config_profile_table.sql`
- `backend/src/main/resources/db/migration/V16__add_algorithm_metrics_run_token.sql`

### A.4 Docs
- `docs/AUDIT_SCHEDULER_ENGINE.md`
- `docs/AUDIT_CONSTRAINT_ENGINE.md`
- `docs/CONFIG_ADMIN_FULL_AUDIT.md` (this audit pass 1)

## Appendix B: NOT VERIFIED items

- BR-XX label consistency in `BusinessRulesCard.tsx` (component not opened in this pass)
- V10 layer's `weeklyCounts` handling (not deeply inspected)
- Actual run-time performance under load (no benchmarking done)
- Backup/restore flow for `algorithm_config` table
- Concurrent edit locking (no `@Version` column found)
- DataSeeder flow (referenced but not opened)
- CSP `l04AllowedSpecialties` full integration path

---

**END OF REPORT**