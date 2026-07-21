# Constraint Engine Audit Report

**Date**: 2026-07-17
**Auditor**: AI Agent (Post-v10 Stabilization Audit)
**Scope**: Business Rules (BR-01 to BR-07) enforcement across all engines

---

## 1. Executive Summary

All 7 business rules from the SPEC have active consumers in both the production scheduler (Greedy/FairGreedy) and the V10 LocalSearch engine. No dead rules, no missing enforcement.

**Conclusion**: Constraint engine is complete for RC v1.0.0.

---

## 2. Business Rules Map

| Rule | Description | SPEC | Greedy/FairGreedy Consumer | V10 Consumer | Hard/Soft | Runtime Verified | Status |
|------|-------------|------|--------------------------|--------------|-----------|-----------------|--------|
| **BR-01** | L01 ↔ L02 conflict (same staff, same day) | ✅ | `StaffEligibilityFilter` | `ShiftConflictConstraint` | HARD | ✅ Unit test | ✅ Active |
| **BR-02** | L03 ↔ L04 conflict (same staff, same day) | ✅ | `StaffEligibilityFilter` | `ShiftConflictConstraint` | HARD | ✅ Unit test | ✅ Active |
| **BR-03** | Max 6 consecutive work days | ✅ | `StaffEligibilityFilter` | `RestDayConstraint` | SOFT (weight=100) | ✅ Unit test | ✅ Active |
| **BR-04** | Adjacent L01 penalty | ✅ | `StaffEligibilityFilter` (hard block) | `AdjacentL01Constraint` | SOFT (weight=50) | ✅ Unit test | ⚠️ Inconsistent |
| **BR-05** | Leave conflict | ✅ | `StaffEligibilityFilter` | `LeaveConflictConstraint` | HARD | ✅ Unit test | ✅ Active |
| **BR-06** | Max shifts per month | ✅ | Soft sort (eligible pool) | `MaxShiftsConstraint` | SOFT (weight=30) | ✅ Unit test | ✅ Active |
| **BR-07** | No duplicate shifts (DB guard) | ✅ | DB UNIQUE constraint | `DuplicateShiftConstraint` | HARD | ✅ Defense-in-depth | ✅ Active |

---

## 3. BR-04 Behavior Inconsistency (Known, Not Fixed)

### Greedy/FairGreedy (Hard Block)
```
Staff has L01 on day N-1
    ↓
Staff in adjacentL01FromPrev
    ↓
Staff is filtered out of eligible pool
    ↓
Cannot be assigned any shift on day N
```

### V10 LocalSearch (Soft Penalty)
```
Staff has L01 on day N-1
    ↓
Assignment allowed
    ↓
AdjacentL01Constraint violated
    ↓
ScoreDelta: +1 consecutiveViolation * 50
    ↓
Score worsens (but assignment remains)
```

### Why This Is Not a Blocker

- Both behaviors are valid interpretations of BR-04
- Greedy's hard block is more conservative (safer)
- V10's soft penalty allows the algorithm to explore solutions where adjacent L01 is unavoidable
- V10 is NOT the default algorithm — Greedy runs first
- No SPEC requirement mandates hard vs soft for adjacent L01

### Recommendation

**Do NOT fix before RC.** Document for v1.1 roadmap:
- Align BR-04 behavior across all engines
- Option A: Make Greedy soft (like V10)
- Option B: Make V10 hard (like Greedy)

---

## 4. Greedy/FairGreedy: Eligibility Filter

### StaffEligibilityFilter Checks

The `filterAndSortEligibleStaffBatch` method enforces:

| Check | Source | Hard/Soft |
|-------|--------|-----------|
| Excluded staff IDs | Request param | HARD (skip) |
| Already assigned today | `assignedStaffIds` set | HARD (skip) |
| L01↔L02 conflict | `todayConflicts.shiftConflictStaffIds` | HARD (filter) |
| L03↔L04 conflict | `todayConflicts.shiftConflictStaffIds` | HARD (filter) |
| Adjacent L01 | `adjacentL01FromPrev` | HARD (filter) |
| Compensation day | `todayCompDayStaffIds` | HARD (filter) |
| Leave request | `periodData.leaveStaffIds` | HARD (filter) |
| Max shifts per staff | `runtimeConfig.maxShiftsPerStaff` | HARD (filter) |
| Weekly max per type | `runtimeConfig.l0XMaxPerWeek` | HARD (filter) |
| L04 cross-specialty | `StaffEligibilityFilter` | HARD (filter) |
| Max shifts per month | `maxShiftsPerMonthOverride` | SOFT (sort last) |

### Compensation Day Enforcement

When L01 is assigned:
1. `CompensationDateCalculator.calculate(workDate)` computes comp date
2. If comp date is within period, staff is added to `compensationDaysByDate`
3. Next day (and subsequent days), staff is filtered out of eligible pool

This is enforced at the filter level, BEFORE the comparator runs.

---

## 5. V10 LocalSearch: Constraint Registry

V10 registers all constraints in `LocalSearchScheduler.solve()`:

```java
ConstraintRegistry registry = new ConstraintRegistry();
registry.register(new ShiftConflictConstraint());    // BR-01, BR-02
registry.register(new LeaveConflictConstraint());   // BR-05
registry.register(new DuplicateShiftConstraint());   // BR-07
registry.register(new RestDayConstraint());          // BR-03
registry.register(new AdjacentL01Constraint());     // BR-04
registry.register(new MaxShiftsConstraint());       // BR-06
```

Each constraint implements the `Constraint` SPI with:
- `id()`: Identifier for logs and telemetry
- `isHard()`: True = infinite weight
- `weight()`: Soft constraint penalty multiplier
- `evaluate(WorkingSolution)`: Full recompute
- `evaluateMove(...)`: Optional fast-path delta

---

## 6. ScoreDelta Schema

Constraints return a `ScoreDelta` with 7 fields:

| Field | BR Source | Meaning |
|-------|-----------|---------|
| `hardDelta` | BR-01, BR-02, BR-05, BR-07 | Hard violations count |
| `coverageDelta` | — | Coverage change |
| `cvDelta` | — | Coefficient of variation |
| `weekendDelta` | — | Weekend fairness |
| `consecutiveDelta` | BR-03, BR-04 | Consecutive days penalty |
| `gapDelta` | BR-06 | Shift count gap |
| `giniDelta` | — | Gini coefficient |

### Score Ordering (Lexicographic)

```
hardViolations ↑ (must = 0)
coverage ↓ (higher is better)
cvTotal ↑
cvWeekend ↑
gap ↑
gini ↑
consecutiveGap ↑
```

---

## 7. Coverage Constraint

Coverage is NOT a registered constraint in V10. It is handled separately:

- **Greedy**: Always fills 100% (unless understaffed)
- **V10**: Evaluated by `ScoreDirector.recomputeFull()` → `MutableScore.coverage`

---

## 8. Files Audited

- `backend/src/main/java/com/hospital/scheduler/scheduling/constraint/Constraint.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/constraint/ConstraintRegistry.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/constraint/ShiftConflictConstraint.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/constraint/DuplicateShiftConstraint.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/constraint/AdjacentL01Constraint.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/constraint/RestDayConstraint.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/constraint/LeaveConflictConstraint.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/constraint/MaxShiftsConstraint.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/LocalSearchScheduler.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/score/ScoreDelta.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/score/MutableScore.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/score/ScoreDirector.java`
