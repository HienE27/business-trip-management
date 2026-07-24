# V24 Audit — `scheduling.parallelism` + 3 sibling flags

## Scope

Audit four `scheduling.*` keys in `application.properties` to determine
whether they are wired into runtime or are dead config (zero consumers).

## Method

For each flag, find every Java class that mentions it and verify there
is an actual runtime path that reads the value. Grep on:

- `@Value("scheduling.<key>")` — direct property injection
- `@ConfigurationProperties` binding
- Direct `getProperty(...)` lookup

Plus a code-graph check for any consumer of the class that would read
the flag.

## Findings (audit 2026-07-25)

### 1. `scheduling.parallelism` — DEAD

| Where | What | Status |
|---|---|---|
| `application.properties` line 155 | `scheduling.parallelism=1` | Defined |
| `scheduling/parallel/ParallelSearchEngine.java` | Uses `parallelism` for `Executors.newFixedThreadPool(...)` | Class exists |
| `scheduling/parallel/BestSolutionHolder.java` | Helper used only by `ParallelSearchEngine` | Class exists |
| **Caller of `ParallelSearchEngine`** | — | **NONE** |

The class is defined but never instantiated. `parallelism=1` therefore
disables nothing — the flag is unreachable from any code path.

### 2. `scheduling.adaptive` — DEAD

| Where | What | Status |
|---|---|---|
| `application.properties` line 156 | `scheduling.adaptive=false` | Defined |
| `scheduling/adaptive/AdaptiveController.java` | Controller class | Exists |
| `@Value("scheduling.adaptive")` or `isAdaptive()` lookup | — | **NONE** |

`AdaptiveController` exists but is never constructed via Spring DI, and
no `@Value` reader pulls the flag into a runtime predicate.

### 3. `scheduling.auto-tune` — DEAD

| Where | What | Status |
|---|---|---|
| `application.properties` line 157 | `scheduling.auto-tune=false` | Defined |
| `scheduling/tune/AutoTuner.java` | Tuner class | Exists |
| `scheduling/tune/LatinHypercube.java` | Helper | Exists |
| `@Value("scheduling.auto-tune")` or `isAutoTune()` lookup | — | **NONE** |

Same shape — class exists, flag has no consumer.

### 4. `scheduling.ml-warm-start` — DEAD

| Where | What | Status |
|---|---|---|
| `application.properties` line 158 | `scheduling.ml-warm-start=false` | Defined |
| `scheduling/ml/MlWarmStart.java` | Warm-start helper | Exists |
| `@Value("scheduling.ml-warm-start")` or `isWarmStart()` lookup | — | **NONE** |

## Pattern observed

This is the **same shape as V23**: feature classes were scaffolded
(`ParallelSearchEngine`, `AdaptiveController`, `AutoTuner`, `MlWarmStart`)
but never wired to a runtime path. The `application.properties` defaults
exist as a contract but the contract is unfulfilled.

Unlike V23 (which cleaned DB rows and JSON profile payloads), these
flags live only in `application.properties` — there is no DB cleanup to
perform.

## Recommendation (V24 scope)

1. **Remove the four dead property keys** from `application.properties`:
   - `scheduling.parallelism`
   - `scheduling.adaptive`
   - `scheduling.auto-tune`
   - `scheduling.ml-warm-start`
2. **Keep the scaffold classes** (`ParallelSearchEngine`, `AdaptiveController`,
   `AutoTuner`, `MlWarmStart`, plus their helpers) — they document the
   intended design and may be wired up later. Removing them is out of
   scope for this audit.
3. **Do not** change runtime defaults until the classes are wired up.
   Removing the flags simply means Spring will no longer carry unused
   configuration; it does not affect any active code path.
4. Verify no test references the flags (TBD before merge).
5. Re-run Preview Period 1 and confirm the result is unchanged
   (coverageRate, schedule count, conflict count, execution time should
   all match baseline within measurement noise).

## Out of scope

- Wiring `ParallelSearchEngine` into the auto-scheduling pipeline.
- Wiring `AdaptiveController` / `AutoTuner` / `MlWarmStart` into a runtime
  caller.
- Removing the scaffold classes themselves.

These are **feature work**, not cleanup. They belong in a follow-up
epic.

## Runtime verification (post-cleanup, TBD)

- Preview Period 1 expected: `coverageRate=45.05%`, `totalSchedulesCreated=805`,
  `conflictCount=0`, `executionTimeMs` within ±10% of `50799`.
- Backend boot time should not regress (no class loading changes).
- SpringDoc endpoints (`/v3/api-docs`, `/swagger-ui.html`) unaffected.
