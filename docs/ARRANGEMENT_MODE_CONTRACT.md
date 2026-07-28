# Arrangement Mode Contract

Source of truth for `arrangementMode` (`INTRA_TYPE` | `WITH_INTER_BALANCE`).
UI: `/auto-scheduling/algorithm-config` → `RuntimeConfigEditor`.
Persist key: `algorithm_config.arrangement_mode` (runtime).

## Semantics (exclusive)

| Mode | Assignment soft objective | Scorer | Auto targets L01/L02/L03/L04 |
|---|---|---|---|
| `INTRA_TYPE` | inter penalty **OFF** | fairness without inter blend | 0.30 / 0.25 / 0.30 / 0.15 |
| `WITH_INTER_BALANCE` | inter penalty **ON** (weight 5.0) | 15% inter fairness blend | 0.30 / 0.30 / 0.30 / 0.10 |

Hard rules always on: conflicts, maxShifts, L04 strategy, eligibility.
Mode = **soft only**. User choice is exclusive — recommend/planner must not flip it.

## Data flow

1. UI mode change → retarget L01–L04 ratios → auto-fill min/max (if Auto-fill ON).
2. Save: runtime PUT (mode/weights/caps) + auto-gen PUT (limits/targets/L04).
3. Recommend sends current UI `arrangementMode` + `targetPerStaffPerMonth`.
4. Preview/run loads mode from **DB runtime** (not per-run request).
5. All schedulers + `ScheduleQualityScorer` read mode.

## Algorithms that honor mode

- `EnhancedGreedyScheduler` — candidate inter-type penalty
- `BeamSearchScheduler` — beam state score soft term
- `SimulatedAnnealingScheduler` — objective soft term
- `RandomRestartHCScheduler` — score / rebalance soft term
- `CpSatScheduler` — soft objective pressure
- `ScheduleQualityScorer` — fairness blend when INTER

Post-process (rotation / gap-fill / local rebalance) is **variety/coverage**, not mode gate (v1).

## Recommend rules

1. Targets: request `targetPerStaff` (>0) → else DB target → else hist/default.
2. `fairnessType`:
   - user `WITH_INTER_BALANCE` → `INTRA_TYPE_WITH_INTER_BALANCE`
   - user `INTRA_TYPE` → `INTRA_TYPE`
   - null only → auto (ratio ≤ 2.5)
