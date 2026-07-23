# Issue: V10 Local Search bỏ qua cấu hình `l04CrossSpecialty`

**Labels:** bug, m07-auto-scheduling, epic-1-blocker, v10-local-search
**Severity:** High (vi phạm nguyên tắc cốt lõi: cross-specialty phải là opt-in)
**Status:** Open
**Discovered:** 2026-07-23 (sau Epic 1 RC merge)

---

## 1. Tóm tắt (TL;DR)

Thuật toán **V10 Local Search** xếp lịch không tôn trọng config `l04CrossSpecialty`.
Cho dù config đặt `false`, V10 vẫn sinh ra ~70% assignments **cross-specialty** (L04 staff khác chuyên khoa requirement).

**Ảnh hưởng thực tế** (period 1, 17 staff thật, 30 ngày):

| Algorithm | l04CrossSpecialty=true | l04CrossSpecialty=false | DB L04 cross count |
|---|---|---|---|
| **CSP_MRV_FC** | 3 cross (Ngoại shortage) | **0 cross** ✅ | 0–3 |
| **V10_LOCAL_SEARCH** | 100 cross | **100 cross** ❌ | ~129/180 |
| **FAIR_GREEDY** | như CSP | như CSP | đúng |

→ Khi user tắt cross-specialty để đảm bảo chuyên khoa đúng, **V10 vẫn ép cross**.
→ Vi phạm expectation: "đặt config = false là tắt cross".

---

## 2. Root cause đã phân tích

### 2.1 Cấu trúc hiện tại

```
AutoSchedulingService.dispatchAlgorithm()
  ├─ CSP_MRV_FC  → CSPScheduler → StaffEligibilityFilter.shouldPreferCrossSpecialty() ✅
  ├─ FAIR_GREEDY → chung logic CSP                              ✅
  └─ V10_LOCAL_SEARCH → LocalSearchScheduler → SchedulingProblem.getEligibleStaff()
                                                              ❌ KHÔNG filter cross
```

### 2.2 Code vi phạm

**File:** `backend/src/main/java/com/hospital/scheduler/scheduling/domain/SchedulingProblem.java`

```java
public List<Integer> getEligibleStaff(int slotId) {
    ShiftRequirementInfo slot = requirementsById.get(slotId);
    if (slot == null) return Collections.emptyList();

    List<Integer> result = new ArrayList<>();
    for (StaffNode s : staffList) {
        if (isOnLeave(s.getId(), slot.date())) continue;
        if (isOnCompensation(s.getId(), slot.date())) continue;
        if (!s.isEligibleFor(slot.shiftTypeId())) continue;  // ← chỉ check shiftType
        result.add(s.getId());
    }
    return result;
    // ← KHÔNG check l04CrossSpecialty
}
```

**File:** `backend/src/main/java/com/hospital/scheduler/scheduling/domain/StaffNode.java`

```java
private static Set<String> determineEligibleTypes(Staff staff) {
    Set<String> types = new HashSet<>();
    if (Boolean.TRUE.equals(staff.getIsActive())) {
        types.add("L01");
        types.add("L02");
        types.add("L03");
        types.add("L04");  // ← tất cả active staff đều eligible L04
    }
    return types;
}
```

Comment trong code đã ghi nhận:
```
Specialty-based restrictions are layered on top via StaffEligibilityFilter
which the caller invokes when computing getEligibleStaff(slotId).
```
**Nhưng caller KHÔNG gọi filter** — TODO bị bỏ quên.

### 2.3 Sử dụng config

| Config key | Nơi đọc | Algorithm áp dụng |
|---|---|---|
| `l04CrossSpecialty` | `RequirementPreparationService.java:109,187` | Requirement gen — ✅ tất cả |
| `l04CrossSpecialty` | `StaffEligibilityFilter.java:97-99` | CSP/FairGreedy — ✅ |
| `l04CrossSpecialty` | `LocalSearchScheduler.java` | **V10 — ❌ KHÔNG đọc** |

---

## 3. Reproduction steps

### Bước 1 — Đặt config cross = false
```bash
curl -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"enabled":true,"holidayMode":"SKIP","l04MinPerDay":5,"l04MaxPerDay":6,
       "l01MinPerDay":5,"l01MaxPerDay":0,"l02MinPerDay":0,"l02MaxPerDay":0,
       "l03MinPerDay":0,"l03MaxPerDay":0,"l01MinPerWeek":0,"l02MinPerWeek":0,
       "l03MinPerWeek":0,"l04MinPerWeek":0,"l01MaxPerWeek":0,"l02MaxPerWeek":0,
       "l03MaxPerWeek":0,"l04MaxPerWeek":0,"removedShiftTypes":[],
       "l04CrossSpecialty":false,"l04CrossSpecialtyRatio":0.3,
       "l04AllowedSpecialties":[],"l04BalanceStrategy":"FAIR_DISTRIBUTE"}' \
  http://localhost:8080/api/v1/auto-schedule/auto-gen-config
```

### Bước 2 — Clear schedules period 1
```sql
DELETE cd FROM compensation_day cd JOIN schedule s ON cd.schedule_id = s.id WHERE s.period_id = 1;
DELETE FROM schedule WHERE period_id = 1;
```

### Bước 3 — Run V10
```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"periodId":1,"algorithmType":"V10_LOCAL_SEARCH","maxIterations":50,"timeoutMs":10000,"save":true}' \
  http://localhost:8080/api/v1/auto-schedule
```

### Bước 4 — Đếm cross assignments
```sql
SELECT sp_req.name AS req_spec,
       CASE WHEN sp_req.id = sp_staff.id OR sp_req.id IS NULL OR sp_staff.id IS NULL
            THEN 'SAME' ELSE 'CROSS' END AS match_type,
       COUNT(*) AS cnt
FROM schedule s
JOIN shift_requirement sr ON sr.id = s.requirement_id
LEFT JOIN specialty sp_req ON sp_req.id = sr.specialty_id
JOIN staff st ON st.id = s.staff_id
LEFT JOIN specialty sp_staff ON sp_staff.id = st.specialty_id
WHERE s.period_id = 1 AND s.is_preview = 0 AND s.shift_type_id = 'L04'
GROUP BY sp_req.name, match_type;
```

### Expected vs Actual

| Setup | Expected L04 cross | Actual L04 cross |
|---|---|---|
| `cross=true`, period 1 | 0–10 (chỉ khi shortage cao) | 129 ❌ |
| `cross=false`, period 1 | **0** ✅ | **129** ❌ |

→ Config `l04CrossSpecialty` **không ảnh hưởng** V10.

---

## 4. Đã thử — không work

### Attempt #1: Filter tại `LocalSearchScheduler.buildInitialSolution()`
- Inject `StaffEligibilityFilter` vào `LocalSearchScheduler`
- Thêm `applyL04SpecialtyFilter()` lọc pool sau `getEligibleStaff()`
- **Kết quả:** Chỉ work cho initial solution, KHÔNG work cho search loop moves.
- V10 vẫn pick staff cross ~129/180 sau search.

### Attempt #2: Filter tại `SchedulingProblem.getEligibleStaff()`
- Inject filter vào `SchedulingProblem` qua constructor overload mới
- Áp dụng filter cho mọi L04 call
- **Log xác nhận filter chạy đúng** (slot Ngoại: `before=25 → after=9`)
- **NHƯNG DB vẫn có 129 cross** — tổng Ngoại strict chỉ 46, không giải thích được 180-46=134 cross.

### Kết luận debug
- Filter apply đúng (verified bằng `System.out.println`)
- V10 vẫn pick staff ngoài 9 Ngoại strict cho 134 assignments
- Nghi vờ: có path code khác bypass filter — **cần investigation sâu hơn**

**Files đã thay đổi (đã revert):**
- `backend/src/main/java/com/hospital/scheduler/scheduling/LocalSearchScheduler.java`
- `backend/src/main/java/com/hospital/scheduler/scheduling/domain/SchedulingProblem.java`

---

## 5. Đề xuất hướng giải quyết

### Option A: Wire `StaffEligibilityFilter` vào `SchedulingProblem` (chuẩn nhất)
1. Thêm constructor `SchedulingProblem(..., StaffEligibilityFilter filter)`
2. `getEligibleStaff()` check `crossSpecialtyFilter != null && shiftType=L04 && spec != null` → áp dụng filter
3. `LocalSearchScheduler.solve()` truyền filter qua `withRequirements(... filter)`
4. `CSP/FairGreedy` paths KHÔNG cần đổi (đã dùng filter riêng)
5. Verify bằng: log filter + query DB cross count

### Option B: Refactor `StaffEligibilityFilter` thành strategy
1. Tạo `EligibilityFilter` interface với `getEligibleStaff(slot, pool) → List<Staff>`
2. CSP dùng `EligibilityFilter.fullFilter()`, V10 dùng `EligibilityFilter.crossOnly()`
3. Centralized logic, dễ test

### Option C: Hardcode V10 dùng filter cứng (quick fix)
1. Trong `LocalSearchScheduler.buildInitialSolution()`, thay vì gọi `problem.getEligibleStaff()`, gọi thẳng `staffEligibilityFilter.getEligibleStaff(slot)`
2. Tốn effort nhưng giữ logic cũ
3. Có thể miss các edge case (leave/compensation đã filter ở Problem)

**Recommendation:** Option A — clean, nhưng cần thêm debug session để hiểu tại sao Attempt #2 không work.

---

## 6. Acceptance criteria

Khi fix xong:

- [ ] V10 với `l04CrossSpecialty=false` → 0 cross assignments (DB verified)
- [ ] V10 với `l04CrossSpecialty=true` + ratio=0.5 → cross chỉ khi shortage ≥ 50%
- [ ] V10 với `l04CrossSpecialty=true` + ratio=1.0 → cross khi shortage > 0
- [ ] CSP path không regression (vẫn như cũ)
- [ ] Unit test `LocalSearchSchedulerTest.shouldRespectL04CrossSpecialtyConfig()` PASS
- [ ] E2E: config persistence (đã fix ở commit trước) + V10 fix → full flow OK
- [ ] `mvn test` PASS, không skip

---

## 7. Related

- Epic 1 RC merge commit: `f984f3c`
- Issue khác (đã fix): "Config persistence — card values lost on F5 reload"
- Spec reference: `PROJECT_CONTEXT.md` — CRITICAL Constraints #1, #2, #3
- Design note: `StaffEligibilityFilter.shouldPreferCrossSpecialty()` đã implement đúng ở CSP/FairGreedy — V10 cần delegate logic tương tự

---

## 8. Owner / Priority

- **Owner:** Backend (Java Spring Boot)
- **Priority:** P1 — Block release nếu user dùng V10 algorithm
- **Estimate:** 4-6 giờ (gồm debug session + fix + tests)
- **Suggested milestone:** Sprint tiếp theo sau Epic 1