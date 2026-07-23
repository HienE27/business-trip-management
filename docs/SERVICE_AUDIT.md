# Service Audit — Technical Debt Backlog

> **Date**: 2026-07-14
> **Scope**: All files in `backend/src/main/java/com/hospital/scheduler/service/`
> **Methodology**: LOC ranking + long-method scan + parameter-list scan + repository-call density
> **Status**: Audit only — refactors proposed, not yet executed

---

## 1. Inventory & LOC Ranking

| Rank | File | LOC | Public methods | Notes |
|---|---|---|---|---|
| 1 | `AutoSchedulingService.java` | 3249 | 14 | **Already refactored** (Nov 2026) — 3712 → 3249 |
| 2 | `StaffService.java` | 1087 | 11 | CRUD + soft-delete + import wrapper |
| 3 | `ScheduleTemplateService.java` | 885 | 10 | Template apply/preview/save-from-gen |
| 4 | `ScheduleService.java` | 808 | 14 | CRUD + bulk + conflict-check entry |
| 5 | `ConflictDetectionService.java` | 753 | 23 | Conflict scan, coverage check, replacement |
| 6 | `AlgorithmConfigService.java` | 677 | 12 | Config CRUD + recommendation |
| 7 | `SchedulePeriodService.java` | 492 | 13 | Period lifecycle + bulk publish |
| 8 | `ScheduleExchangeService.java` | 491 | 12 | Swap approval flow |
| 9 | `LeaveRequestService.java` | 442 | 10 | Leave approval flow |
| 10 | `DashboardService.java` | 317 | – | Read-only aggregations |
| 11–47 | (others) | <300 each | – | Mostly fine |

**Sub-services under `service.scheduling/`**: 11 files, all <300 lines, all single-responsibility — **healthy**.

---

## 2. Red-flag Scan (Top 8 Services)

### 2.1 StaffService (1087 LOC, 11 methods)

**Red flags**:

| # | Method | Lines | Issue |
|---|---|---|---|
| 1 | `updateStaff(Integer id, StaffRequest)` L285 | **140** | Mixed: role + permission + status + specialty update. Many branches, hard to test exhaustively. |
| 2 | `createStaff(StaffRequest, List<String> roles)` L211 | 74 | Similar pattern to updateStaff — could share a "staff mutation pipeline". |
| 3 | `searchStaffsPage` L147 | 64 | Long JPQL/Spec chain — acceptable but could move to `StaffQueryBuilder`. |
| 4 | Repository call count | 26 | **Slight over-fetch risk** — each call site may need verify. |

**Refactor proposal**:
- Extract `StaffMutationService` (create/update/reactivate) — 74+140 = 214 lines, single-responsibility
- Keep `StaffService` thin: read-only queries + delegate mutation
- Effort: **M** · Risk: **Med** (touches auth/user-impacting code)

### 2.2 ScheduleTemplateService (885 LOC, 10 methods)

**Red flags**:

| # | Method | Lines | Issue |
|---|---|---|---|
| 1 | `saveTemplateFromGenerated` L436 | **296** | **Very long**. Parses AutoScheduleResponse + saves as template — likely mixes parsing, validation, and persistence. |
| 2 | `applyTemplateWithEdits` L732 | **271** | Long apply flow with edits. |
| 3 | `applyTemplateToPeriod` L158 | 188 | Apply algorithm — could be a sub-service. |
| 4 | `previewTemplate` L346 | 90 | Preview logic — could share code with applyTemplateToPeriod. |

**Refactor proposal**:
- Extract `TemplateApplyService` (apply + apply-with-edits + preview) — ~550 lines
- Keep `ScheduleTemplateService` thin: CRUD + save-from-generated
- Effort: **L** · Risk: **Med** (template→period flow is business-critical)

### 2.3 ScheduleService (808 LOC, 14 methods)

**Red flags**:

| # | Method | Lines | Issue |
|---|---|---|---|
| 1 | `createBulkL01` L477 | **175** | Bulk L01 has compensation-day cascade — long but well-tested |
| 2 | `bulkCreateSchedules` L652 | **157** | Bulk generic — long but well-tested |
| 3 | `updateSchedule` L211 | 131 | Single-row update with conflict re-check — long but straightforward |
| 4 | `getExpertClinicWeeklyView` L809 | 126 | Read-only — fine for a query, but build many sub-objects |
| 5 | `overrideConflict` L410 | 67 | Force-save with audit — fine |

**Refactor proposal**:
- Extract `BulkScheduleService` (createBulkL01 + bulkCreateSchedules) — ~330 lines
- Keep `ScheduleService` for single-row + reads
- Effort: **M** · Risk: **Low** (already 2 dedicated bulk tests pass)

### 2.4 ConflictDetectionService (753 LOC, 23 methods)

**Red flags**:

| # | Method | Lines | Issue |
|---|---|---|---|
| 1 | `validateAndThrow(...)` L169 | 1 line | **7 parameters** — explosion. Other overloads too. Should take a `ConflictCheckRequest` parameter object. |
| 2 | `checkPeriodConflicts` L299 | **161** | Period-wide conflict scan — long, but the meat of the system. Acceptable for a single-responsibility method. |
| 3 | `validateStaffingCoverage` L737 | 131 | Coverage math — acceptable. |
| 4 | `resolveConflict` L487 | 118 | Long but mostly DB calls — acceptable. |
| 5 | `findReplacements` overloads L605/L611 | 90 each | Two near-duplicate overloads — could merge. |

**Refactor proposal**:
- Introduce `record ConflictCheckRequest(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, Integer periodId, boolean skipCompensationDay, boolean skipShiftTypeConflict)` — replaces all `validateAndThrow` overloads
- Extract `ConflictScanEngine` (the two `checkPeriodConflicts*` methods + `validateStaffingCoverage`) — ~290 lines
- Effort: **M** · Risk: **High** (conflict detection is the most heavily tested module; refactor must preserve semantics)

### 2.5 AlgorithmConfigService (677 LOC, 12 methods)

**Red flags**:

| # | Method | Lines | Issue |
|---|---|---|---|
| 1 | `saveAutoGenConfig` L281 | **219** | **Longest single method in this service**. Persists AutoGen config — likely mixes validation, calculation, and storage. |
| 2 | `recommendAutoGenConfig` L604 | 99 | AI recommendation logic — acceptable. |
| 3 | `saveRuntimeConfig` L529 | 75 | OK. |
| 4 | Mixes CRUD + templates + recommendations + runtime | – | **Mixed responsibility** — a config service that also does recommendation is a 4-in-1. |

**Refactor proposal**:
- Extract `AutoGenConfigService` (the 4 `AutoGen`-named methods) — ~350 lines
- Extract `AlgorithmConfigRecommendationService` (the `recommendAutoGenConfig` method) — ~150 lines
- Keep `AlgorithmConfigService` for the standard CRUD + runtime config
- Effort: **L** · Risk: **Med** (recommendation has external scoring dependencies)

### 2.6 SchedulePeriodService (492 LOC, 13 methods)

**Red flags**:

| # | Method | Lines | Issue |
|---|---|---|---|
| 1 | `publishPeriod` L147 | 81 | State transition + bulk pre-validation. Acceptable. |
| 2 | `deletePeriod` L415 | 88 | Cascade delete — acceptable but needs integration test verification. |
| 3 | `bulkPublish`/`bulkArchive` | 67/57 | Two near-duplicates — could share helper. |

**Refactor proposal**: Minor only. Extract `PeriodBulkOperations` helper. **Effort: S · Risk: Low.**

### 2.7 ScheduleExchangeService (491 LOC, 12 methods)

**Red flags**:

| # | Method | Lines | Issue |
|---|---|---|---|
| 1 | `approveExchange` L199 | **188** | **State machine**: validate → swap → notify → audit. Long but represents one transaction. |
| 2 | `cancelExchange` L428 | 140 | Cancellation flow — acceptable. |
| 3 | `createExchange` L111 | 88 | OK. |

**Refactor proposal**: Acceptable as-is. `approveExchange` could split into `validateSwapConstraints()` + `executeSwap()` for testability. **Effort: S · Risk: Low.**

### 2.8 LeaveRequestService (442 LOC, 10 methods)

**Red flags**:

| # | Method | Lines | Issue |
|---|---|---|---|
| 1 | `findReplacementsForLeave` L373 | **137** | Combines eligibility filter + ranking + fallback — close to `ReplacementSuggestionService` (M07). Could **delegate** to it. |
| 2 | `approveLeaveRequest` L170 | **134** | Approve → remove conflicting schedules → notify → audit. Long but well-bounded. |

**Refactor proposal**:
- Make `ReplacementSuggestionService` (M07) callable from LeaveRequestService — eliminates ~100 lines of duplicate logic.
- Effort: **S** · Risk: **Low** (the two services would need a small shared contract).
- **This is a cross-module refactor candidate.**

---

## 3. Cross-cutting Issues

### 3.1 Long-parameter methods

Only one offender:

| Method | Params |
|---|---|
| `ConflictDetectionService.validateAndThrow(...)` | **7 params** |

Recommendation: introduce `ConflictCheckRequest` record (see §2.4).

### 3.2 Mixed responsibility services

| Service | Responsibilities mixed | Severity |
|---|---|---|
| `AlgorithmConfigService` | CRUD + template + AutoGen + recommendation + runtime | High |
| `ScheduleTemplateService` | CRUD + apply + preview + save-from-gen | Medium |
| `StaffService` | CRUD + soft-delete + reactivate + import | Medium |

### 3.3 Duplication candidates

| Pattern | Files with duplicates |
|---|---|
| Eligibility filter + ranking | `LeaveRequestService.findReplacementsForLeave` vs `ReplacementSuggestionService` |
| Period bulk operation | `bulkPublish` vs `bulkArchive` |
| Conflict `findReplacements` overloads | L605 + L611 in `ConflictDetectionService` |

### 3.4 Dead-code scan

- No `private` method appears to be unreferenced (manual spot-check on top 5 services).
- Most static helpers are used at least once.

---

## 4. Refactor Backlog (Ranked)

Top 5 candidates, sorted by **value ÷ effort ÷ risk**.

| Priority | Service | Refactor | Effort | Risk | Value |
|---|---|---|---|---|---|
| **P1** | `LeaveRequestService` | Delegate `findReplacementsForLeave` to `ReplacementSuggestionService` | **S** | Low | High (eliminates cross-module duplication) |
| **P2** | `ConflictDetectionService` | Introduce `ConflictCheckRequest` record; consolidate overloads | **M** | High | High (tames parameter explosion) |
| **P3** | `ScheduleService` | Extract `BulkScheduleService` (createBulkL01 + bulkCreateSchedules) | **M** | Low | Medium (clean separation) |
| **P4** | `StaffService` | Extract `StaffMutationService` (create/update/reactivate) | **M** | Med | Medium (clarifies auth-critical paths) |
| **P5** | `AlgorithmConfigService` | Split into 3 services (CRUD / AutoGen / Recommendation) | **L** | Med | Medium (large refactor, modest gain) |
| P6 | `ScheduleTemplateService` | Extract `TemplateApplyService` | L | Med | Medium |
| P7 | `SchedulePeriodService` | Extract `PeriodBulkOperations` helper | S | Low | Low |
| P8 | `ScheduleExchangeService` | Split `approveExchange` for testability | S | Low | Low |

**Total estimated effort**: ~6–8 working days for P1–P5.

---

## 5. Recommended Action Plan

**Sprint 1 (1 day)**:
- P1: LeaveRequestService → ReplacementSuggestionService delegation (+ new unit test)

**Sprint 2 (2 days)**:
- P2: ConflictDetectionService parameter object refactor (+ run all conflict tests)

**Sprint 3 (2 days)**:
- P3: Extract BulkScheduleService
- P4: Extract StaffMutationService

**Sprint 4 (2–3 days, optional)**:
- P5: AlgorithmConfigService split

---

## 6. Health Indicators (overall)

| Metric | Value | Verdict |
|---|---|---|
| Services > 1000 LOC | 1 (StaffService) | ⚠️ Watch |
| Methods > 200 lines | 4 (one is M07 facade, accepted) | ⚠️ Watch |
| Methods > 100 lines | ~15 | ⚠️ Spread across services |
| Methods with > 5 params | 1 | ✅ Mostly OK |
| Circular service deps | 0 detected | ✅ Healthy |
| N+1 query patterns | 0 detected | ✅ Healthy |
| Sub-services inside M07 | 11, all <300 LOC | ✅ Excellent |

The overall codebase is in **good shape** post-refactor — the main residual debt is concentrated in 2-3 services (AlgorithmConfigService, ScheduleTemplateService, StaffService), and none of it is urgent.