# FIX_LOG.md — ARRANGEMENT_MODE_CONTRACT Violations

Date: 2026-07-25
Contract: `docs/ARRANGEMENT_MODE_CONTRACT.md`
Session: continuation context preserved

---

## Summary of Violations Fixed

| # | Violation | File | Severity |
|---|---|---|---|
| 1 | Fallback target ratios not mode-specific | `AlgorithmConfigService.java` | HIGH |
| 2 | BeamSearchScheduler ignores `arrangementMode` | `BeamSearchScheduler.java` | HIGH |
| 3 | SimulatedAnnealingScheduler ignores `arrangementMode` | `SimulatedAnnealingScheduler.java` | HIGH |
| 4 | RandomRestartHCScheduler ignores `arrangementMode` | `RandomRestartHCScheduler.java` | HIGH |
| 5 | CpSatScheduler ignores `arrangementMode` | `CpSatScheduler.java` | HIGH |

---

## Fix 1 — AlgorithmConfigService: mode-specific fallback ratios

**File:** `backend/src/main/java/com/hospital/scheduler/service/AlgorithmConfigService.java`

**Problem:** `recommendAutoGenConfig()` used identical fallback ratios (0.30/0.25/0.30/0.15) for
both `INTRA_TYPE` and `WITH_INTER_BALANCE` modes. The `arrangementMode` parameter was passed in
but only used for `fairnessType` computation — not for ratio selection.

**Contract specifies:**
- `INTRA_TYPE` fallback: L01=0.30, L02=0.25, L03=0.30, L04=0.15
- `WITH_INTER_BALANCE` fallback: L01=0.30, L02=0.30, L03=0.30, L04=0.10

**Change (around line 820):**
```java
// BEFORE:
int l01Target = resolveTarget(targetPerStaff, "L01", current.l01TargetPerMonth(), capacityPerPerson,
        histRatios, 0.30, 2);
int l02Target = resolveTarget(targetPerStaff, "L02", current.l02TargetPerMonth(), capacityPerPerson,
        histRatios, 0.25, 2);   // ← same for both modes
int l03Target = resolveTarget(targetPerStaff, "L03", current.l03TargetPerMonth(), capacityPerPerson,
        histRatios, 0.30, 2);
int l04Target = resolveTarget(targetPerStaff, "L04", current.l04TargetPerMonth(), capacityPerPerson,
        histRatios, 0.15, 5);   // ← same for both modes

// AFTER:
double l01Def = 0.30;
double l02Def = "WITH_INTER_BALANCE".equals(arrangementMode) ? 0.30 : 0.25;
double l03Def = 0.30;
double l04Def = "WITH_INTER_BALANCE".equals(arrangementMode) ? 0.10 : 0.15;
int l01Target = resolveTarget(targetPerStaff, "L01", current.l01TargetPerMonth(), capacityPerPerson,
        histRatios, l01Def, 2);
int l02Target = resolveTarget(targetPerStaff, "L02", current.l02TargetPerMonth(), capacityPerPerson,
        histRatios, l02Def, 2);
int l03Target = resolveTarget(targetPerStaff, "L03", current.l03TargetPerMonth(), capacityPerPerson,
        histRatios, l03Def, 2);
int l04Target = resolveTarget(targetPerStaff, "L04", current.l04TargetPerMonth(), capacityPerPerson,
        histRatios, l04Def, 5);
```

---

## Fix 2 — BeamSearchScheduler: add inter penalty to scoreStateFast

**File:** `backend/src/main/java/com/hospital/scheduler/algorithm/BeamSearchScheduler.java`

**Problem:** `scoreStateFast()` — the hot-path scoring function used during beam expansion —
had no inter-type penalty term. `arrangementMode` was completely ignored.

**Contract:** `WITH_INTER_BALANCE` → inter penalty ON (weight 5.0), 15% inter fairness blend.

**Changes:**

1. Added import:
```java
import static com.hospital.scheduler.algorithm.ArrangementModeSupport.*;
```

2. Updated method signature:
```java
// BEFORE:
private double scoreStateFast(Map<String, String> assignments,
                               Map<Integer, Set<String>> staffTypes,
                               Map<Integer, Integer> staffCount,
                               int totalRequired, int numStaff)

// AFTER:
private double scoreStateFast(Map<String, String> assignments,
                               Map<Integer, Set<String>> staffTypes,
                               Map<Integer, Integer> staffCount,
                               int totalRequired, int numStaff,
                               AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig)
```

3. Updated call site in beam expansion loop to pass `runtimeConfig`.

4. Added inter penalty computation in `scoreStateFast()`:
```java
// Soft inter-type penalty: WITH_INTER_BALANCE only (ARRANGEMENT_MODE_CONTRACT)
double interPenalty = 0;
if (interEnabled(runtimeConfig)) {
    Map<Integer, Map<String, Integer>> byStaff = new HashMap<>();
    for (Map.Entry<Integer, Set<String>> e : staffTypes.entrySet()) {
        Map<String, Integer> m = byStaff.computeIfAbsent(e.getKey(), k -> new HashMap<>());
        for (String t : e.getValue()) {
            if ("L01".equals(t) || "L02".equals(t) || "L03".equals(t)) {
                m.merge(t, 1, Integer::sum);
            }
        }
    }
    double meanSpan = meanInterSpan(byStaff);
    interPenalty = DEFAULT_INTER_WEIGHT * meanSpan * OBJECTIVE_INTER_SCALE; // 5.0 × span × 0.02
}

// Return updated:
return COVERAGE_WEIGHT * coverage
        + (FAIRNESS_WEIGHT + BALANCE_WEIGHT) * fairness
        + VARIETY_WEIGHT * variety
        - interPenalty;
```

---

## Fix 3 — SimulatedAnnealingScheduler: add inter penalty to score()

**File:** `backend/src/main/java/com/hospital/scheduler/algorithm/SimulatedAnnealingScheduler.java`

**Problem:** `score()` — used for SA acceptance criterion and move/swap evaluation —
had no inter-type penalty. All 5 call sites used the old 4-argument signature.

**Changes:**

1. Added import:
```java
import static com.hospital.scheduler.algorithm.ArrangementModeSupport.*;
```

2. Updated method signature:
```java
// BEFORE:
private double score(List<Schedule> schedules, List<ShiftRequirement> reqs,
                     Map<Integer, Staff> staffMap, int totalRequired, int l01Window)

// AFTER:
private double score(List<Schedule> schedules, List<ShiftRequirement> reqs,
                     Map<Integer, Staff> staffMap, int totalRequired, int l01Window,
                     AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig)
```

3. Updated all 5 call sites: `currentScore` init, move mutation, swap mutation, log statement.

4. Added inter penalty in `score()` return:
```java
double interPenalty = 0;
if (interEnabled(runtimeConfig)) {
    Map<Integer, Map<String, Integer>> byStaff = typeCountsFromSchedules(schedules);
    double meanSpan = meanInterSpan(byStaff);
    interPenalty = DEFAULT_INTER_WEIGHT * meanSpan * OBJECTIVE_INTER_SCALE;
}
return COVERAGE_WEIGHT * coverage + FAIRNESS_WEIGHT * fairness
        - conflicts * CONFLICT_PENALTY - interPenalty;
```

---

## Fix 4 — RandomRestartHCScheduler: add inter penalty to score()

**File:** `backend/src/main/java/com/hospital/scheduler/algorithm/RandomRestartHCScheduler.java`

**Problem:** `score()` had no inter-type penalty. `tryRandomMove` and `tryRandomSwap`
— which call `score()` for acceptance — had no `l01Window` or `runtimeConfig` params.

**Changes:**

1. Added import:
```java
import static com.hospital.scheduler.algorithm.ArrangementModeSupport.*;
```

2. Updated `score()` signature (same as SA above) and added inter penalty to return.

3. Updated `tryRandomMove` signature: added `int l01Window` parameter (was computed inside
   the method but needed externally).

4. Updated `tryRandomSwap` signature: added `runtimeConfig` parameter.

5. Updated all call sites in `solve()`, `totalCountRebalanceRRHC()`,
   `tryRandomMove()`, `tryRandomSwap()` to pass both `runtimeConfig` and `l01Window`
   to `score()`.

---

## Fix 5 — CpSatScheduler: add inter penalty to objective

**File:** `backend/src/main/java/com/hospital/scheduler/algorithm/CpSatScheduler.java`

**Problem:** The CP-SAT objective (OR-Tools `CpModel`) had no inter-type balance term.
The solver was unaware of `arrangementMode`.

**Contract:** `WITH_INTER_BALANCE` → inter penalty weight 5.0 in soft objective.

**Approach:** In CP-SAT, inter-type balance is expressed as a soft objective term
penalising the span `max(L01max, L02max, L03max) − min(L01max, L02max, L03max)`.
This is implemented using OR-Tools auxiliary IntVars and `addMaxEquality`/`addMinEquality`.

**Changes:**

1. Added import:
```java
import static com.hospital.scheduler.algorithm.ArrangementModeSupport.*;
```

2. Added inter-balance objective term before `model.minimize()`:
```java
// Soft inter-type penalty: WITH_INTER_BALANCE only (ARRANGEMENT_MODE_CONTRACT).
// Penalises (max(L01max, L02max, L03max) − min(L01max, L02max, L03max))
// to push the solver toward even inter-type distribution across staff.
if (interEnabled(runtimeConfig)) {
    IntVar interSpan = model.newIntVar(0, numDays, "inter_type_span");
    IntVar maxL012 = model.newIntVar(0, numDays, "max_l012");
    IntVar minL012 = model.newIntVar(0, numDays, "min_l012");
    model.addMaxEquality(maxL012, new IntVar[]{maxPerType[0], maxPerType[1], maxPerType[2]});
    model.addMinEquality(minL012, new IntVar[]{maxPerType[0], maxPerType[1], maxPerType[2]});
    model.addEquality(interSpan, LinearExpr.newBuilder().add(maxL012).addCoefficient(minL012, -1).build());
    // Weight 5.0 matches ArrangementModeSupport.DEFAULT_INTER_WEIGHT
    objective.addTerm(interSpan, 5.0);
}
```

---

## Items NOT Changed (Already Correct)

| Item | Status |
|---|---|
| `EnhancedGreedyScheduler` inter penalty (weight 5.0) | Already correct |
| `ScheduleQualityScorer` 15% inter blend | Already correct |
| `ArrangementModeSupport.interEnabled()` | Already correct |
| `fairnessType` mapping: `WITH_INTER_BALANCE` → `INTRA_TYPE_WITH_INTER_BALANCE` | Already correct |
| Recommend sends `arrangementMode` to backend | Already correct |
| Preview/Run loads mode from DB runtime config | Already correct |
| Storage key `algorithm_config.arrangement_mode` | Already correct |
| Default value `"INTRA_TYPE"` | Already correct |

---

## Test Verification Plan

To verify these fixes, run the following scenarios:

1. **Mode = `INTRA_TYPE`, fallback ratios:**
   - Call `recommendAutoGenConfig` with `arrangementMode="INTRA_TYPE"`, no targetPerStaff
   - Verify fallback ratios: L01=0.30, L02=0.25, L03=0.30, L04=0.15

2. **Mode = `WITH_INTER_BALANCE`, fallback ratios:**
   - Call `recommendAutoGenConfig` with `arrangementMode="WITH_INTER_BALANCE"`, no targetPerStaff
   - Verify fallback ratios: L01=0.30, L02=0.30, L03=0.30, L04=0.10

3. **All 6 schedulers with `WITH_INTER_BALANCE`:**
   - Run each scheduler with `arrangementMode="WITH_INTER_BALANCE"`
   - Verify inter-type balance in output (stddev of L01/L02/L03 per staff should be low)

4. **All 6 schedulers with `INTRA_TYPE`:**
   - Run each scheduler with `arrangementMode="INTRA_TYPE"`
   - Verify no inter penalty applied (output may have higher inter-type variance)

---

## Skipped / Technical Debt

- **L04 in inter penalty:** `ArrangementModeSupport.meanInterSpan()` and the inter penalty
  formula explicitly exclude L04 (only L01/L02/L03), matching the contract.
  `CpSatScheduler` uses `maxPerType[0..2]` (L01/L02/L03) for the same reason.

- **Post-process (rotation/gap-fill/local rebalance):** Contract §Post-process states
  these are "variety/coverage, not mode gate (v1)" — no changes needed.

- **CP-SAT interSpan auxiliary var bound:** `interSpan` is bounded to `[0, numDays]`.
  In pathological cases (span > numDays) the solver may not perfectly satisfy the
  equality — if this becomes an issue, bound `interSpan` to `[0, numStaff * numDays]`
  and add a redundant `model.addGreaterOrEqual(interSpan, LinearExpr.newBuilder()
  .add(maxL012).addCoefficient(minL012, -1).build())` as an extra constraint.
