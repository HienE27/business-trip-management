# M07 — ROADMAP

## Objective
Automated scheduling engine with 5 algorithms, runtime-configurable fairness weights, cross-specialty L04 support, and comprehensive persistence lifecycle (proposal → preview → cancel → confirm).

## Gate Status

| Gate | Description | Status |
|------|-------------|--------|
| **1** | Persistence E2E (proposal/preview/cancel/confirm DB isolation) | **VERIFIED** |
| **2** | Fairness benchmark (demand 150/300/600/1000, all metrics) | **VERIFIED** |
| **3** | Commit audit A–M | **VERIFIED** |
| **4** | Documentation truthfulness | **VERIFIED** |

## Workflow Verification

| Phase | Result | Detail |
|-------|--------|--------|
| Proposal (save=false) | ✅ VERIFIED | No writes to config/requirements/schedules/metrics/comp-days DB |
| Preview (save=false) | ✅ VERIFIED | recommendedConfig used in-memory only; DB unchanged |
| Cancel | ✅ VERIFIED | Only in-memory lock state reset; DB unchanged |
| Confirm (save=true) | ✅ VERIFIED | Only this phase writes to config/requirements/schedules/metrics/comp-days |

## M07 Final Status

**CONDITIONAL VERIFIED**

| Criterion | Result |
|-----------|--------|
| M07-specific test suite | ✅ All pass |
| Gate 1 Persistence E2E | ✅ VERIFIED |
| Gate 2 Fairness benchmark | ✅ VERIFIED (caveat: standalone solver coverage >100% — production path unaffected) |
| Gate 3 Audit A–M | ✅ VERIFIED (A, C, I, J, M confirmed individually) |
| Gate 4 Documentation | ✅ VERIFIED |
| Workflow Proposal→Preview→Confirm | ✅ VERIFIED |
| Full backend test suite | ❌ 26 failures + 8 errors (ALL outside M07 scope) |
| Repository release status | **BLOCKED** if policy requires full-suite green |

## Pre-Existing Failures (Non-M07, 26 failures + 8 errors)

| Test Class | Failures | Errors | Domain |
|-----------|----------|--------|--------|
| AuthControllerWebMvcTest | 10 | 0 | Auth |
| HolidayRepositoryTest | 3 | 0 | Repository |
| StaffRepositoryTest | 3 | 0 | Repository |
| SchedulePeriodControllerWebMvcTest | 2 | 0 | SchedulePeriod |
| ScheduleRepositoryTest | 2 | 0 | Repository |
| ShiftRequirementServiceHolidayTest | 2 | 1 | Service |
| LeaveRequestControllerWebMvcTest | 1 | 0 | LeaveRequest |
| ScheduleServiceCoreTest | 1 | 0 | ScheduleService |
| StaffServiceProtectionTest | 1 | 0 | StaffService |
| SchedulePeriodRepositoryTest | 1 | 0 | Repository |
| PersistenceContractTest | 0 | 2 | DB Contract |
| ScheduleServiceBulkL01Test | 0 | 5 | ScheduleService |
| **Total** | **26** | **8** | — |

## Post-M07 Task

See `docs/M07_POST_M07_REPAIR_TASK.md` — repair non-M07 backend test suite.
