# Post-M07: Repair Non-M07 Backend Test Suite

> **Created:** 2026-07-25  
> **Status:** 📝 Task created — not yet implemented  
> **Scope:** Non-M07 test failures only. Do NOT modify M07 code, auto-scheduling, scheduler, algorithm, scoring, or related persistence logic.

---

## Objective

Restore full backend test suite to green (0 failures, 0 errors) by repairing pre-existing test failures that are outside M07 scope.

## Failure Inventory

### 26 Failures

#### AuthControllerWebMvcTest — 10 failures
- Root cause: Auth security layer test mismatch (likely missing `@WithMockUser` configuration, permission string drift, or security config change)
- File: `backend/src/test/java/.../controller/AuthControllerWebMvcTest.java`

#### HolidayRepositoryTest — 3 failures
- Root cause: DB seed data mismatch between `init.sql` and test expectations
- Files: `backend/src/test/java/.../repository/HolidayRepositoryTest.java`

#### StaffRepositoryTest — 3 failures
- Root cause: DB seed data mismatch
- File: `backend/src/test/java/.../repository/StaffRepositoryTest.java`

#### SchedulePeriodControllerWebMvcTest — 2 failures
- Root cause: Controller routing or permission annotation mismatch
- File: `backend/src/test/java/.../controller/SchedulePeriodControllerWebMvcTest.java`

#### ScheduleRepositoryTest — 2 failures
- Root cause: DB seed data mismatch
- File: `backend/src/test/java/.../repository/ScheduleRepositoryTest.java`

#### ShiftRequirementServiceHolidayTest — 2 failures
- Root cause: Holiday logic edge case
- File: `backend/src/test/java/.../service/ShiftRequirementServiceHolidayTest.java`

#### LeaveRequestControllerWebMvcTest — 1 failure
- Root cause: Permission or routing mismatch
- File: `backend/src/test/java/.../controller/LeaveRequestControllerWebMvcTest.java`

#### ScheduleServiceCoreTest — 1 failure
- Root cause: Service contract mismatch
- File: `backend/src/test/java/.../service/ScheduleServiceCoreTest.java`

#### StaffServiceProtectionTest — 1 failure
- Root cause: Staff service protection logic
- File: `backend/src/test/java/.../service/StaffServiceProtectionTest.java`

#### SchedulePeriodRepositoryTest — 1 failure
- Root cause: Repository query mismatch
- File: `backend/src/test/java/.../repository/SchedulePeriodRepositoryTest.java`

### 8 Errors

#### ScheduleServiceBulkL01Test — 5 errors
- Root cause: Bulk L01 schedule creation — likely DB constraint or transaction issue
- File: `backend/src/test/java/.../service/ScheduleServiceBulkL01Test.java`

#### PersistenceContractTest — 2 errors
- Root cause: DB contract violations (FK, unique constraint, or data integrity)
- File: `backend/src/test/java/.../service/PersistenceContractTest.java`

#### ShiftRequirementServiceHolidayTest — 1 error
- Root cause: Holiday logic exception
- File: `backend/src/test/java/.../service/ShiftRequirementServiceHolidayTest.java`

## Repair Scope

| Domain | Files to Touch | Expected Change |
|--------|---------------|-----------------|
| Auth | AuthControllerWebMvcTest | Permission config or mock setup |
| Repository (Holiday, Staff, Schedule, SchedulePeriod) | 4 test files | Seed data alignment with init.sql |
| Controller (SchedulePeriod, LeaveRequest) | 2 test files | Routing/permission annotation alignment |
| Service (ScheduleCore, StaffProtection, Holiday, BulkL01, PersistenceContract) | 5 test files | Test expectations alignment with current service behaviour |
| DB schema/migration | None unless PersistenceContract errors indicate drift | — |

## Non-Scope (M07 — do NOT touch)

- `AutoSchedulingService` and its tests
- All 5 schedulers (`EnhancedGreedy`, `BeamSearch`, `RandomRestartHC`, `SimulatedAnnealing`, `CpSat`)
- `ScheduleQualityScorer` and `ScheduleQualityReport`
- `AlgorithmConfigService` and `RequirementAutoGenService`
- `AutoSchedulingReportingService`
- `BenchmarkSchedulers`
- Frontend files
- M07 documentation files
