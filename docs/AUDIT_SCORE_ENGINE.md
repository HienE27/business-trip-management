# Score / Fairness Engine Audit Report

**Date**: 2026-07-17
**Auditor**: AI Agent (Post-v10 Stabilization Audit)
**Scope**: All score computation components in the scheduling pipeline

---

## 1. Executive Summary

Two independent score systems exist:
1. **Preview/Metrics Score** (`ScheduleQualityScorer`) — used in REST API and DB metrics
2. **V10 Search Score** (`ScoreDirector` + `MutableScore`) — used in V10 LocalSearch

Both systems are active and serving their respective consumers. No blocking issues.

**Note**: `BalanceScoreCalculator` is deprecated. `ScheduleScorer` is a stub.

---

## 2. Score System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  PREVIEW PATH (Greedy/FairGreedy/CSP/V10)                  │
│                                                             │
│  Schedule list → ScheduleQualityScorer                      │
│                       ↓                                      │
│                 ScheduleQualityReport                        │
│                 (coverage + fairness + constraint)           │
│                       ↓                                      │
│                 AutoScheduleResponse.metrics                 │
│                       ↓                                      │
│                 algorithm_metrics DB                         │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  V10 SEARCH PATH                                            │
│                                                             │
│  WorkingSolution → ScoreDirector.recomputeFull()             │
│                       ↓                                      │
│                 MutableScore (incremental)                   │
│                       ↓                                      │
│                 LocalSearchAlgorithm (move selection)         │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  BENCHMARK PATH (isolated)                                  │
│                                                             │
│  Schedule → ScheduleScorer (STUB)                          │
│                       ↓                                      │
│                 ScoreResult (always 1000.0)                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. ScheduleQualityScorer (Preview/Metrics)

**Location**: `com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer`
**Consumer**: REST API preview responses, `algorithm_metrics` table

### 3.1 Score Formula

```
totalScore = w_cov × coverageScore
           + w_fair × fairnessScore
           + w_con × constraintScore
```

**Default Weights**: Coverage=0.40, Fairness=0.35, Constraint=0.25

### 3.2 Sub-Scores

| Sub-Score | Formula | Range | Notes |
|-----------|---------|-------|-------|
| `coverageScore` | assigned / required × 100 | 0–100 | |
| `fairnessScore` | Per-type CV scoring | 0–100 | L01/L02/L03 global; L04 per-specialty |
| `constraintScore` | 100 - (hardViolations × 25) - (softViolations × 5) | 0–100 | |

### 3.3 Per-Type Fairness (CV)

```
CV = σ / μ (coefficient of variation)

CV ≤ 10%  → 100 points
CV ≥ 50%  → 0 points
Linear interpolation between
```

L04 uses per-specialty pool (specialty-aware fairness per M05).

### 3.4 BR Coverage in ScheduleQualityScorer

| BR | Enforcement |
|----|-------------|
| BR-01 | Conflict detection (L01↔L02, L03↔L04) |
| BR-02 | Conflict detection |
| BR-03 | Consecutive day check |
| BR-04 | Adjacent L01 check |
| BR-05 | Leave conflict check |
| BR-06 | Max shifts check |

---

## 4. ScoreDirector + MutableScore (V10)

**Location**: `com.hospital.scheduler.scheduling.score.*`
**Consumer**: V10 LocalSearch search loop

### 4.1 MutableScore Fields

| Field | Source | Meaning |
|-------|--------|---------|
| `hardViolations` | Constraint deltas | Count of hard violations |
| `coverage` | `solution.getCoverage()` | Fraction of required slots filled |
| `cvTotal` | LoadStatistics | Coefficient of variation (all shifts) |
| `cvWeekend` | WeekendStatistics | Coefficient of variation (weekend only) |
| `weekendGap` | WeekendStatistics | Max - Min weekend shifts |
| `consecutiveGap` | RestDayConstraint | Max consecutive - 6 |
| `gap` | LoadStatistics | Max - Min total shifts |
| `gini` | FairnessStatistics | Gini coefficient |

### 4.2 Score Lexicographic Ordering

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

## 5. Statistics Components

| Component | Used By | Role |
|-----------|---------|------|
| `IncrementalStatisticsHub` | ScoreDirector | Central registry for incremental stats |
| `LoadStatistics` | ScoreDirector | Per-staff shift counts, total |
| `WeekendStatistics` | ScoreDirector | Per-staff weekend shift counts |
| `FairnessStatistics` | ScoreDirector | Gini coefficient |

All four are used in `ScoreDirector.recomputeFull()`.

---

## 6. Deprecated / Dead Components

### 6.1 BalanceScoreCalculator — DEPRECATED

**Location**: `com.hospital.scheduler.service.scheduling.BalanceScoreCalculator`

```java
@Deprecated
public BigDecimal calculateBalanceScore(List<Schedule> schedules, int totalStaff)
```

**Status**: Deprecated. Replaced by `ScheduleQualityScorer.fairnessScore`.
Retained for backward compatibility with `BalanceScoreCalculatorTest`.

### 6.2 ScheduleScorer — STUB

**Location**: `com.hospital.scheduler.scheduling.score.ScheduleScorer`

```java
@Component
public class ScheduleScorer {
    public ScoreResult calculateScore(Schedule schedule) {
        return ScoreResult.builder()
                .totalScore(1000.0)  // Always 1000
                .hardViolations(0)
                .softViolations(0)
                .coverageScore(400.0)
                .fairnessScore(350.0)
                .constraintScore(250.0)
                .build();
    }
}
```

**Status**: Stub. Only used by `BenchmarkService`. Production scheduler does not use this.

**Consumer**: `BenchmarkService.calculateScore()` — reads but ignores result fields.

**Action for v1.1**: Either implement properly or remove.

---

## 7. Internal Heuristic

**Location**: `AutoSchedulingService.calculateInternalHeuristicBalanceScore()`

```java
private BigDecimal calculateInternalHeuristicBalanceScore(List<Schedule> schedules, int totalStaff)
```

**Status**: Internal only. Used only for Greedy↔FairGreedy dispatch comparison.
NOT persisted. NOT exposed in REST API.

**Consumer**: `runScheduling()` — decides whether to fall back to FairGreedy.

---

## 8. Score Consistency

### 8.1 Preview vs Persisted Metrics

**Before**: `apply-preview` used internal heuristic, producing drift (e.g., 92.68 vs 55.02).

**After (FIXED)**: Both preview and persist use `ScheduleQualityScorer`:
```java
// In runScheduling():
applyQualityReport = ScheduleQualityScorer.computeQuality(
    ...,
    ScoringMeta.of(request.getAlgorithmType(), 0L),
    algorithmConfigService.getAutoGenConfig().orElse(null));
BigDecimal balanceScore = BigDecimal.valueOf(applyQualityReport.getFairnessScore());
```

This ensures `algorithm_metrics.balance_score` matches the REST response.

---

## 9. Files Audited

- `backend/src/main/java/com/hospital/scheduler/algorithm/scoring/ScheduleQualityScorer.java`
- `backend/src/main/java/com/hospital/scheduler/algorithm/scoring/ScheduleQualityReport.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/score/ScoreDirector.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/score/MutableScore.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/score/ScoreDelta.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/score/ScheduleScorer.java`
- `backend/src/main/java/com/hospital/scheduler/service/scheduling/BalanceScoreCalculator.java`
- `backend/src/main/java/com/hospital/scheduler/service/AutoSchedulingService.java` (score persistence)
