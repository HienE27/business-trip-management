# TASK-01: Dead Runtime Configuration

> **Status:** ✅ Completed
> **Date:** 2026-07-24
> **Audit ref:** `docs/M07_CONFIGURATION_AUDIT.md` §4.2 (Dead config) + R1, R2, R5
> **Principle:** No field shall be UI→API→DB→(never read by scheduler).

---

## Summary

Removed or wired all runtime config fields that were "accepted but not used":
- **Removed:** `minShiftsPerStaff` (from `AlgorithmRuntimeConfig` DTO/DB/API/UI), `autoAssign` and `holidayMode` (from `AutoScheduleRequestDTO` + frontend API type), dead `bestScore`/`bestSchedules` block.
- **Wired:** `beamWidth` (added DB persistence — previously accepted by PUT but silently dropped).
- **Kept (logging-only):** `balanceScoreMin` — field IS read at runtime (log check), not dead. The dead `bestScore/bestSchedules` reassignment was removed; field is now an explicit logging-only parameter.
- **Kept (not dead):** `l0XMinPerWeek` (used by recommendation engine), `l0XTargetPerMonth` (used by recommendation engine).

---

## Files modified

### Backend — Java

| File | Change |
|---|---|
| `service/AlgorithmConfigService.java` | Added `BEAM_WIDTH` constant. Wired `beamWidth` → `getRuntimeConfig()` + `saveRuntimeConfig()` (reads from DB, default 5). Removed `MIN_SHIFTS_PER_STAFF` constant. Removed `minShiftsPerStaff` from `getRuntimeConfig()`, `saveRuntimeConfig()`, `syncDescriptions()`, `AlgorithmRuntimeConfig` class. |
| `service/AutoSchedulingService.java` | Removed dead `bestScore`/`bestSchedules` reassignment block (`:813-822`). Kept `calculateBalanceScore` + `balanceScoreMin` log check. |
| `dto/request/AutoScheduleRequestDTO.java` | Removed `autoAssign` field (line 22). Removed `holidayMode` field (line 33). |

### Backend — Tests

| File | Change |
|---|---|
| `algorithm/BenchmarkSchedulers.java:47-51` | Removed `0` (minShiftsPerStaff) from `AlgorithmRuntimeConfig` constructor arguments. |
| `algorithm/FairnessBenchmarkTest.java:174-187` | Same. |
| `algorithm/MaxShiftsPerDayHardCapTest.java:95-108` | Same. |
| `algorithm/MaxShiftsPerStaffHardCapTest.java:96-109` | Same. |
| `algorithm/MetaheuristicSchedulersSmokeTest.java:88-101` | Same. |
| `algorithm/OvernightRecoveryHoursTest.java:100-113` | Same. |
| `algorithm/RuntimeConfigBehaviorTest.java:112-125` | Same. |
| `controller/AutoSchedulingControllerWebMvcTest.java:104-111` | Removed `.autoAssign(true)` from `validRequest()` builder call. Also removed `0` minShiftsPerStaff from `sampleRuntimeConfig()` constructor (line 138-151). |

### Frontend — TypeScript

| File | Change |
|---|---|
| `algorithm-config/types.ts` | Removed `minShiftsPerStaff` from `RuntimeConfig` type. Removed `min_shifts_per_staff` from `PARAM_KEY_TO_CFG` map. |
| `algorithm-config/presets.ts` | Removed `minShiftsPerStaff: 0` from `baseConfig`. Removed `cfg.minShiftsPerStaff === p.minShiftsPerStaff` from `detectPreset()`. |
| `lib/validation/algorithmConfig.ts` | Removed `min_shifts_per_staff` validation rule (lines 66-71). |
| `types/api.ts` | Removed `autoAssign` and `holidayMode` from `AutoScheduleRequest` interface. |

---

## Per-field root cause, solution & rationale

### 1. `beamWidth`
- **Root cause:** Field existed on `AlgorithmRuntimeConfig` DTO with `@Builder.Default = 5`. `saveRuntimeConfig()` never persisted it. `BeamSearchScheduler` and `SimulatedAnnealingScheduler` both read it but always got the default `5`.
- **Solution:** Added `BEAM_WIDTH = "beam_width"` constant. `getRuntimeConfig()` reads from DB via `.beamWidth(getIntValue(BEAM_WIDTH, 5, cache))`. `saveRuntimeConfig()` writes it via `upsert(BEAM_WIDTH, ...)`.
- **Rationale:** Field is used by 2 schedulers — just missing DB persistence. This is the fix, not a removal.

### 2. `balanceScoreMin` (logging-only — not dead)

- **Root cause:** The only consumer (`AutoSchedulingService.java:813-822`) had a dead `bestScore = greedyBalanceScore; bestSchedules = createdSchedules; createdSchedules = bestSchedules` reassignment — always a no-op. However, `balanceScoreMin` itself was genuinely read for the log check at line 816.
- **Solution:** Removed the dead reassignment block. Kept `calculateBalanceScore` + `balanceScoreMin` log check. The field remains live as a **logging-only parameter**: it is persisted, loaded, and compared at runtime — but the comparison only produces a log line, not a behavioural change.
- **Rationale:** The field is not dead — it is read every scheduling run. It is simply a visibility/monitoring parameter by design. Making it functional (acceptance-gating) is a separate feature task.

### 3. `minShiftsPerStaff`
- **Root cause:** Persisted via `MIN_SHIFTS_PER_STAFF = "min_shifts_per_staff"`. Read by no scheduler — zero references outside `AlgorithmConfigService`.
- **Solution:** Removed constant, DB read/write, field from `AlgorithmRuntimeConfig`, frontend type/preset/validation.
- **Rationale:** Truly dead — no scheduler implements min-shifts guarantees. Adding it would require algorithm changes. Removed.

### 4. `autoAssign` (in `AutoScheduleRequestDTO`)
- **Root cause:** Declared on `AutoScheduleRequestDTO.java:22`. No service code ever called `getAutoAssign()`. Dead since inception.
- **Solution:** Removed field from DTO. Removed from frontend `AutoScheduleRequest` interface.
- **Rationale:** Truly dead — no usage anywhere. Removed.

### 5. `holidayMode` (in `AutoScheduleRequestDTO`)
- **Root cause:** Declared on `AutoScheduleRequestDTO.java:33`. No service code ever called `getHolidayMode()` — the holiday mode is always read from `AutoGenConfig.holidayMode()` via DB.
- **Solution:** Removed field from DTO. Removed from frontend `AutoScheduleRequest` interface.
- **Rationale:** Truly dead — the DB-stored `AutoGenConfig.holidayMode` is authoritative. Removed.

### 6. `l0XMinPerWeek` — kept as-is
- **Root cause:** Used by `recommendAutoGenConfig()` in `AlgorithmConfigService.java` to compute recommendations. Persisted but not read by schedulers.
- **Decision:** Kept. These serve a legitimate purpose in the recommendation pipeline (input → recommendation → user applies recommended config). Not dead — just not scheduler-direct.

### 7. `l0XTargetPerMonth` — kept as-is
- **Root cause:** Same as above — used exclusively by `recommendAutoGenConfig()`.
- **Decision:** Kept. Documented in code comments as "recommend-only". Same rationale as l0XMinPerWeek.

---

## Test results

| Test group | Count | Status |
|---|---|---|
| `RuntimeConfigBehaviorTest` | 8 | ✅ Pass |
| `AutoGenConfigSaveTest` | 2 | ✅ Pass |
| `AutoSchedulingControllerWebMvcTest` | 40 | ✅ Pass |
| `MaxShiftsPerDayHardCapTest` | 6 | ✅ Pass |
| `MaxShiftsPerStaffHardCapTest` | 3 | ✅ Pass |
| `OvernightRecoveryHoursTest` | 6 | ✅ Pass |
| `BenchmarkSchedulers` | 2 | ✅ Pass |
| `MetaheuristicSchedulersSmokeTest` | 15 | ✅ Pass |
| `FairnessBenchmarkTest` | 1 | ✅ Pass |
| **Total** | **83** | **✅ All pass** |

(26 pre-existing test failures unrelated to this task — all `holidayValidationService` NPE in test setup.)

---

## Build

```
Build: ./mvnw compile -q → success (Lombok deprecation warnings only)
```

---

## Remaining issues

| Issue | Priority | Detail |
|---|---|---|
| BEAM_WIDTH DB migration | **Medium** | Add a V13 flyway migration to seed `beam_width = 5` into `algorithm_config` table so new DBs don't need manual insert. |
| `balanceScoreMin` logging-only — acceptance not wired | **Low** | Field is read at runtime (log line) — not dead. Currently a logging-only parameter; if acceptance-gating is needed, implement actual `bestScores` retention across algorithm runs (separate feature, not fix). |
| `minShiftsPerStaff` DB row orphan | **Low** | Existing DB rows for `min_shifts_per_staff` are harmless but orphaned. Optional cleanup migration. |
| `l0XMinPerWeek` and `l0XTargetPerMonth` persist but no scheduler reads them | **Low** | These are used by `recommendAutoGenConfig()`; documented as recommend-only. Not dead by strict definition. |
| Pre-existing test failures (26 failures, 6 errors) | **High (separate)** | All are NPE from missing `holidayValidationService` mock in `ScheduleServiceBulkL01Test` / `ShiftRequirementServiceHolidayTest` and unrelated assertion failures. Not caused by this task. |

---

## Commit sequence recommendation

When committing, split into 3 logical commits for clean revert:

1. **`chore: wire beamWidth to DB persistence, remove minShiftsPerStaff`** — AlgorithmConfigService + frontend types/presets/validation + all test constructor updates
2. **`chore: remove dead bestScore/bestSchedules block`** — AutoSchedulingService.java only
3. **`chore: remove dead autoAssign, holidayMode from AutoScheduleRequestDTO`** — DTO + frontend api.ts + controller test builder
