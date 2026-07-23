# Sprint Summary — Config Admin Audit & Refactor

**Date:** 2026-07-19
**Scope:** Verify config-default behavior + refactor magic strings

---

## 1. Verification Results (Test Run)

| Test suite | Tests | Pass | Fail |
|---|---|---|---|
| `SchedulerDefaultInconsistencyTest` (H1) | 9 | 9 | 0 |
| `FairShareCalculatorTest` (M05 cross-specialty) | 7 | 7 | 0 |
| `GreedyAssignmentEngineTest` | 14 | 14 | 0 |
| `StaffEligibilityFilter` (A + Shortage Logic) | 24 | 24 | 0 |
| `StaffEligibilityFilterTest` | 4 | 4 | 0 |
| `BalanceScoreCalculatorTest` | 3 | 3 | 0 |
| `SchedulePersistenceServiceTest` | 8 | 8 | 0 |
| `SchedulingLockServiceTest` | 7 | 7 | 0 |
| `SchedulingStateAccessorTest` | 8 | 8 | 0 |
| `CspScheduler*` (CSP tests) | 11 | 11 | 0 |
| `CspBr06MultiSlotRegressionTest` | 3 | 3 | 0 |
| `CspScheduler90StaffPerfTest` (perf regression) | 1 | 1 | 0 |
| `CspConstraintsTest` | 5 | 5 | 0 |
| **TOTAL** | **104** | **104** | **0** |

**Build status:** `BUILD SUCCESS` (zero failures, zero errors).

---

## 2. Verified Fixes

### H1 — Scheduler / UI default inconsistency ✅

**Status:** Already fixed; tests verify all 9 paths return consistent defaults.

| Path | `enabled` | `ratio` | `strategy` |
|---|---|---|---|
| `AutoGenConfigService.buildAutoGenConfig()` (line 101-104) | `true` | `0.5f` | `"FAIR_DISTRIBUTE"` |
| `AlgorithmConfigService.getAutoGenConfig()` (line 268-271) | `true` | `0.5f` | `"FAIR_DISTRIBUTE"` |
| `StaffEligibilityFilter.CrossSpecialtyConfig.defaultEnabled()` | `true` | `0.5f` | `"FAIR_DISTRIBUTE"` |

All three paths return identical values when DB is empty.

### Dead code cleanup ✅

`StaffEligibilityFilter.filterAndSortEligibleStaffBatch(...)` is annotated `@Deprecated(forRemoval = true)` with Javadoc pointing to the production scheduler method. **No action needed.**

### Performance regression ✅

`CspScheduler90StaffPerfTest` passes in 0.244s — within budget.

---

## 3. False Positives (no action needed)

### RC3 — "L01/L02/L03 use global pool"

**Verdict:** This is intentional per spec. Quoting from `AutoGenConfig.java` line 8-13:

> "L01/L02/L03/L04 là 4 loại **ca trực**, phân biệt bởi thời gian/ca và chế độ nghỉ — **không phải bởi chuyên khoa**"

And `CspDataBuilder.java` line 290:

> "Theo tài liệu nghiệp vụ, L01/L02/L03 không bị giới hạn theo chuyên khoa."

Verified by 3 passing tests in `FairShareCalculatorTest.DefaultPool` ("L01/L02/L03 use full staff pool").

**If a future product decision is to make L01/L02/L03 per-specialty, that is a feature change, not a bug fix.**

---

## 4. Refactor Done This Sprint

### AutoGenConstants — Magic String Centralization

**Created:** `backend/src/main/java/com/hospital/scheduler/algorithm/AutoGenConstants.java`

Centralizes the 5 hardcoded string values that were previously scattered across 11 files:

| Constant | Value |
|---|---|
| `HOLIDAY_MODE_SKIP` | `"SKIP"` |
| `HOLIDAY_MODE_PARTIAL` | `"PARTIAL"` |
| `BALANCE_STRATEGY_STRICT_MATCH_ONLY` | `"STRICT_MATCH_ONLY"` |
| `BALANCE_STRATEGY_FAIR_DISTRIBUTE` | `"FAIR_DISTRIBUTE"` |
| `BALANCE_STRATEGY_WEIGHTED_FAIR` | `"WEIGHTED_FAIR"` |

**Files refactored (9):**
1. `algorithm/AutoGenConfig.java` — Builder defaults + Javadoc
2. `service/AutoGenConfigService.java` — 3 occurrences
3. `service/AlgorithmConfigService.java` — 5 occurrences
4. `service/AlgorithmConfigRecommendationService.java` — 1 occurrence
5. `service/AutoSchedulingService.java` — 3 occurrences
6. `service/ShiftRequirementSyncService.java` — 1 occurrence
7. `service/scheduling/RequirementPreparationService.java` — 3 occurrences
8. `service/scheduling/StaffEligibilityFilter.java` — 3 occurrences
9. `scheduling/config/ConfigMapper.java` — 2 occurrences
10. `scheduling/config/ConfigMetadataRegistry.java` — 5 occurrences
11. `command/DataSeeder.java` — 2 occurrences

**Verification:** After refactor, only literal occurrences remaining are:
- `AutoGenConstants.java` itself (definitions)
- `ConfigDefaults.java` (separate `ConfigDomain`, not `AutoGenConfig` — intentionally untouched)

**Test result:** 104/104 tests still pass. No behavior change.

---

## 5. Frontend Edge Case Detected (Backlog)

**File:** `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/types.ts` lines 30-49 and 73-92

**Issue:** TypeScript `RuntimeConfig` and `AutoGenConfigPayload` types declare cross-specialty fields for L01/L02/L03 (`l01CrossSpecialty`, `l02CrossSpecialty`, `l03CrossSpecialty`), but the backend `AutoGenConfig` record only supports `l04CrossSpecialty`.

**Impact:** No runtime bug (frontend sends, backend silently ignores unknown fields via Jackson). But:
- Misleading to frontend devs — they think they can configure L01/L02/L03 cross-specialty
- Type drift between frontend and backend
- Future backend support for these fields would require no frontend change, but frontend devs might assume the fields do nothing

**Recommendation:** Remove `l01*`/`l02*`/`l03*` cross-specialty fields from `RuntimeConfig` and `AutoGenConfigPayload` types and from `AUTO_GEN_OVERRIDE_KEYS` set. **Out of scope for this sprint.**

---

## 6. Open Items for Next Sprint

1. **Frontend types cleanup** — Remove unused L01/L02/L03 cross-specialty fields (see §5)
2. **`StaffEligibilityFilter.filterAndSortEligibleStaffBatch`** — Remove deprecated method (currently `@Deprecated(forRemoval = true)`)
3. **`ConfigDefaults.java`** — Decide whether to consolidate with `AutoGenConstants` (currently separate domains, intentional)
4. **Production data smoke test** — Manually verify the refactored constants work end-to-end via UI (after deploy)

---

## 7. Files Modified

| File | Change |
|---|---|
| `AutoGenConstants.java` | **Created** |
| `AutoGenConfig.java` | Builder defaults use constants |
| `AutoGenConfigService.java` | 3 literals → constants |
| `AlgorithmConfigService.java` | 5 literals → constants |
| `AlgorithmConfigRecommendationService.java` | 1 literal → constant |
| `AutoSchedulingService.java` | 3 literals → constants |
| `ShiftRequirementSyncService.java` | 1 literal → constant |
| `RequirementPreparationService.java` | 3 literals → constants |
| `StaffEligibilityFilter.java` | 3 literals → constants |
| `ConfigMapper.java` | 2 literals → constants |
| `ConfigMetadataRegistry.java` | 5 literals → constants |
| `DataSeeder.java` | 2 literals → constants |
| `docs/SPRINT_SUMMARY_2026-07-19.md` | **Created** (this file) |

**Total:** 11 files refactored + 2 files created = 13 files touched.
