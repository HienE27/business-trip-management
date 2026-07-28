# TASK-02: L04 cross-specialty parity across all 5 schedulers

## Trạng thái: DONE

## Mục tiêu

Toàn bộ 5 schedulers (EnhancedGreedy, BeamSearch, RRHC, SA, CP-SAT) có hành vi giống nhau khi bật `l04CrossSpecialty`, `l04CrossSpecialtyRatio`, `l04AllowedSpecialties`.

Không thay đổi scoring, fairness, objective.

## Design

### Config record: `L04CrossSpecialtyConfig`

Đã tồn tại ở `algorithm/L04CrossSpecialtyConfig.java`:
- `enabled()` — master switch
- `ratio()` — max cross-specialty ratio per requirement (0.0–1.0)
- `allowedSpecialties()` — specialty names cho phép cross (empty = all)
- `isPermittedFor(specName)` — kiểm tra permission
- `crossCap(requiredStaffCount)` → `max(1, ceil(required * ratio))`, tối thiểu 1 khi enabled

### Pass `l04CrossConfig` tới tất cả schedulers

`AutoSchedulingService.java:778-810` pass `L04CrossSpecialtyConfig` tới cả 5 schedulers.

### Cross-cap tracking

Mỗi scheduler dùng `Map<String, Integer> crossAssignmentCount` với key `date|shiftType|specId`:
- Check trước khi assign: nếu cross-specialty và count ≥ `crossCap(required)` → skip
- Increment sau khi assign cross-specialty

CP-SAT dùng constraint `crossSum ≤ crossCap(minReq)` thay vì counter.

## Thay đổi

### 1. `EnhancedGreedyScheduler.java`

**Gap-fill pass (line 454 cũ):** Strict match → cross-config support:
- Dùng `l04CrossConfig.isPermittedFor(reqSpecName)` thay vì strict `specId.equals()` 
- Check cross-cap trước khi assign cross-specialty
- Increment `crossAssignmentCount` khi assign cross-specialty

### 2. `BeamSearchScheduler.java`

**Field:** thêm `l04CrossConfig` field (pattern đồng bộ với RRHC/SA).

**`findEligible()`:** Thêm `crossCapacity` parameter:
- Pre-compute staff specialty map `staffLookup` O(N)
- Khi staff là cross-specialty: đếm cross count từ state assignments → kiểm tra `< crossCapacity`

**Rebalance methods (`totalCountRebalance`, `perTypeRebalance`):** L04 specialty check dùng `l04CrossConfig.isPermittedFor()` thay vì strict match.

### 3. `RandomRestartHCScheduler.java`

**`randomSolution()`:** Strict filter `specId.equals()` → cross-config:
- `l04CrossConfig.isPermittedFor(req.getSpecialty().getName())` 
- Thêm `crossAssignmentCount` tracking
- Check cross-cap trước khi assign cross

### 4. `SimulatedAnnealingScheduler.java`

**`greedyInitial()`:** Bổ sung cross-cap check:
- Thêm `crossAssignmentCount` tracking
- Check `crossAssignmentCount.getOrDefault(crossKey, 0) < crossCap` cho cross-specialty candidates

### 5. `CpSatScheduler.java`

**Model constraint:** Tách `matchSum` và `crossSum` cho L04:
- `crossSum` = tổng BoolVar của cross-specialty staff
- Constraint: `crossSum ≤ l04CrossConfig.crossCap(minReq)` (chỉ khi crossCap < minReq)
- `assigned = matchSum + crossSum`, giữ nguyên constraint coverage + shortfall

### 6. `AutoSchedulingService.java`

**Bug fix:** Xoá duplicate code block (lines 816-819) — `runGreedy` log + call lặp ngoài if-else chain.

## Benchmark (no cross-specialty — strict mode, `L04CrossSpecialtyConfig.DISABLED`)

20 staff, 30 ngày, maxShiftsPerStaff=30 (capacity=600), beamWidth=5.

| Algorithm | L01 (mm) | L02 (mm) | L03 (mm) | L04 (mm) | Total |
|---|---|---|---|---|---|
| **ENHANCED_GREEDY** | 12-13 | 4-6 | 11-12 | 1-1 | 600 |
| BEAM_SEARCH | 12-13 | 2-7 | 10-11 | 0-3 | 600 |
| RANDOM_RESTART_HC | 13-14 | 4-7 | 10-11 | 0-2 | 600 |
| SIMULATED_ANNEALING | 13-14 | 4-7 | 9-12 | 0-3 | 600 |
| CP_SAT | 13-14 | 4-5 | 4-5 | 2-3 | 520 |

Benchmark identical to pre-change baseline (no regression).

## Test results

| Test suite | Tests | Result |
|---|---|---|
| L04BalanceStrategyTest | 10 | ✅ PASS |
| MetaheuristicSchedulersSmokeTest | 5 | ✅ PASS |
| SchedulingResultTest | 3 | ✅ PASS |
| FairnessBenchmarkTest | 5 | ✅ PASS |
| MaxShiftsPerStaffHardCapTest | 5 | ✅ PASS |
| MaxShiftsPerDayHardCapTest | 16 | ✅ PASS |
| OvernightRecoveryHoursTest | 10 | ✅ PASS |
| RuntimeConfigBehaviorTest | 4 | ✅ PASS |
| **Total** | **58** | **✅ ALL PASS** |

## Files modified

- `backend/src/main/java/.../EnhancedGreedyScheduler.java` — gap-fill cross-specialty
- `backend/src/main/java/.../BeamSearchScheduler.java` — cross-cap + rebalance
- `backend/src/main/java/.../RandomRestartHCScheduler.java` — randomSolution cross-cap
- `backend/src/main/java/.../SimulatedAnnealingScheduler.java` — greedyInitial cross-cap
- `backend/src/main/java/.../CpSatScheduler.java` — cross-cap constraint
- `backend/src/main/java/.../AutoSchedulingService.java` — fix duplicate code
- `backend/src/test/java/.../MetaheuristicSchedulersSmokeTest.java` — add missing param
- `docs/tasks/TASK-L04-CROSS-SPECIALTY.md` — this file
