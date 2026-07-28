# TASK: Fix Phase C per-type rebalance — RRHC objective (PA2')

## Trạng thái: **RRHC PART DONE** — BeamSearch part deferred

## Mục tiêu (RRHC part)

Sửa `perTypeRebalanceRRHC` dùng objective riêng (`perTypeMoveObjective`) thay vì `score()` chung — để per-type moves không bị reject bởi global CV.

## Kết quả

### Files modified

`RandomRestartHCScheduler.java`:

| Location | Change |
|----------|--------|
| `PER_TYPE_WEIGHT=0.8`, `TOTAL_FAIRNESS_WEIGHT=0.2` (lines 54-55) | New constants |
| `perTypeRebalanceRRHC()` (lines 273, 278) | `score()` → `perTypeMoveObjective()` |
| `perTypeMoveObjective()` (lines 301-369) | **New method** — isolated per-type objective |

### Design achieved

- ✅ Objective hoàn toàn độc lập với `score()`
- ✅ Không ảnh hưởng `tryRandomMove/swap/totalCountRebalance`
- ✅ Per-type CV + total fairness guardrail + conflict penalty
- ✅ `objWorsen=0` (đã verify bằng instrumentation) — objective không block moves

### Benchmark

| Demand | L02 (before) | L02 (after) | L03 (before) | L03 (after) |
|--------|-------------|-------------|-------------|-------------|
| 150 | 0-4 (4) | **1-3 (2)** | 0-4 (4) | **1-2 (1)** |
| 300 | 1-7 (6) | **3-4 (1)** | 1-8 (7) | **3-4 (1)** |
| 600 | 4-11 (7) | **6-7 (1)** | 5-11 (6) | **7-8 (1)** |
| 1000 | 3-7 (4) | **4-7 (3)** | 10-13 (3) | **10-11 (1)** |

Move counts: 21 (demand=150), 29 (300), 34 (600), **8 (1000)** — từ 0 lên.

### Instrumentation findings (demand=1000)

| Type | Rounds | Moved | canTakeFail | objWorsen |
|------|--------|-------|-------------|-----------|
| L02 | 11 | 10 | 30/40 (75%) | **0** |
| L03 | 11 | 11 | 9/20 (45%) | **0** |
| L01 | 8 | 7 | 32/39 (82%) | **0** |

**Kết luận:** Objective không còn là bottleneck. `canTake` (conflict check) là nguồn reject duy nhất (45-82%). Dưới single-move operator hiện tại, fairness improvement ở saturated capacity bị giới hạn bởi feasibility constraints, không phải objective.

### Tests

- `FairnessBenchmarkTest` ✅
- `MetaheuristicSchedulersSmokeTest` ✅
- `SchedulingResultTest` ✅
- `MaxShiftsPerStaffHardCapTest` ✅
- `MaxShiftsPerDayHardCapTest` ✅
- `OvernightRecoveryHoursTest` ✅
- `RuntimeConfigBehaviorTest` ✅

### BeamSearch part

Deferred. BeamSearch `perTypeRebalance` cũng bị giới hạn bởi `canTakeBeam`. Cần swap operator hoặc multi-move local search để cải thiện — đó là nghiên cứu thuật toán mới, không phải sửa tiếp PA2'.
