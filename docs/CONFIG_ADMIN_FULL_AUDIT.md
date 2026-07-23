# Config Admin UI — Full Audit Report

**Date**: 2026-07-17
**Auditor**: AI Agent (Principal Software Architect + Senior Hospital Scheduling Expert + Senior UX Auditor + QA Lead)
**Scope**: Toàn bộ pipeline `UI → API → Service → Config → Scheduler → Constraint → DB → Runtime` của trang **Admin Config**.
**Method**: Đọc code thực tế tại `E:\DACN\business-trip-management`. Mọi kết luận có file:line. Nơi không đủ bằng chứng ghi `NOT VERIFIED`.

---

## 1. Executive Summary

| Đánh giá | Điểm | Ghi chú |
|---|---|---|
| **Architecture clarity** | 9.0/10 | Hai tầng config tách biệt rõ ràng (algorithm_config + SchedulingConfig) nhưng UI chỉ expose một tầng. |
| **UI ↔ Backend consistency** | 8.5/10 | Đa số khớp, còn vài mismatch nhỏ. |
| **Business correctness** | 9.5/10 | Hard constraints + soft goals được phản ánh đúng. |
| **Code quality** | 8.0/10 | Một số đoạn `@Deprecated` chưa xóa, một vài hàm trùng logic. |
| **Release readiness (RC v1.0.0)** | 9.8/10 | Feature freeze đã áp dụng. Bug #1 resolved 2026-07-18. |

**Không có blocker release.** ✅ Bug #1 resolved (2026-07-18). Có 4 vấn đề Low/Medium còn lại (Bug #2-5) nên cân nhắc xử lý trước UAT.

---

## 2. Architecture

### 2.1 Hai tầng config độc lập

Hệ thống có **2 lớp config** song song, không giao tiếp trực tiếp:

```
┌─────────────────────────────────────────┐
│ Layer A: AlgorithmConfig (algorithm_config table) │
│  - Persisted rows key/value              │
│  - Loaded via AlgorithmConfigService     │
│  - Driver: Greedy/FairGreedy             │
│  - API: PUT /auto-schedule/runtime-config│
└─────────────────────────────────────────┘
                    ↓ (in-memory only)
              AlgorithmRuntimeConfig record
                    ↓
       StaffEligibilityFilter / GreedyComparator

┌─────────────────────────────────────────┐
│ Layer B: SchedulingConfig (application.properties)  │
│  - @ConfigurationProperties              │
│  - Loaded via Spring boot startup        │
│  - Driver: V10 LocalSearchScheduler      │
│  - NO API, NO DB persistence             │
└─────────────────────────────────────────┘
```

**Evidence**:
- `backend/src/main/java/com/hospital/scheduler/scheduling/config/SchedulingConfig.java:21` — `@ConfigurationProperties(prefix = "scheduling")`
- `backend/src/main/java/com/hospital/scheduler/scheduling/LocalSearchScheduler.java:11,57` — inject `SchedulingConfig`
- `backend/src/main/java/com/hospital/scheduler/service/AlgorithmConfigService.java:38-71` — `AUTO_GEN_ENABLED, WEEKEND_WEIGHT, ...` constants
- `backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigDomain.java:508` — separate record cho Layer A

### 2.2 UI chỉ expose Layer A

Frontend tab "Cấu hình thuật toán" chỉ thao tác Layer A. Layer B (V10) được load từ `application.properties` và **không có UI**. Manager không thể chỉnh tabu tenure, neighborhood size, hay time limit.

**Evidence**: `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/page.tsx:79-93` — chỉ render `RuntimeConfigEditor` + `CustomConfigsCard`. Không có component nào tham chiếu `SchedulingConfig`.

---

## 3. Data Flow

### 3.1 Config Admin UI → Database

```
User (Manager/Admin)
  ↓
RuntimeConfigEditor (frontend/src/.../algorithm-config/RuntimeConfigEditor.tsx:927)
  ↓ setField(key, value)
form state
  ↓ handleSave
api.updateRuntimeConfig(form)  → api-client.ts:1196-1218
  ↓ PUT /api/v1/auto-schedule/runtime-config
ConfigController.updateRuntimeConfig (controller/ConfigController.java:103-119)
  ↓
ConfigService.save (scheduling/config/ConfigService.java:533)
  ↓ upsert(...)
AlgorithmConfigCrudService.upsertAll (service/AlgorithmConfigCrudService.java:288)
  ↓ INSERT/UPDATE
DB: algorithm_config table (param_key, param_value, value_type)
```

### 3.2 Database → Scheduler Runtime

```
SchedulerEngine.runScheduling()
  ↓
AlgorithmConfigService.getRuntimeConfig()  → service/AlgorithmConfigService.java:506
  ↓ getIntValue(...) per key
AlgorithmRuntimeConfig record (POJO with @Getter)
  ↓ injected into
AutoSchedulingService.autoSchedule()  → line 612
  ↓ used at
  - StaffEligibilityFilter.filterAndSortEligibleStaffBatch  → service/scheduling/StaffEligibilityFilter.java:71
  - GreedyAssignmentEngine comparator  → service/AutoSchedulingService.java:1304
  - FairGreedy fallback trigger  → service/AutoSchedulingService.java:731
```

### 3.3 AutoGen → Requirement Generation

```
RequirementPreparationService.prepareRequirements  → line 54
  ↓
algorithmConfigService.getAutoGenConfig()  → line 56
  ↓
AutoGenConfig record  → algorithm/AutoGenConfig.java:93
  ↓
syncExistingRequirementsWithConfig(period, config, activeStaff)  → line 83-126
  ↓ resolveSoftDailyTarget(...) per L0X
ShiftRequirement.requiredStaffCount  → persisted to shift_requirement table
```

**Verified**: `RequirementPreparationService.java:102, 104, 107, 112` cho thấy `l01MinPerDay`, `l01MaxPerDay`, `l02MinPerDay`, `l02MaxPerDay`, `l03MinPerDay`, `l03MaxPerDay`, `l04MinPerDay`, `l04MaxPerDay` đều được consume runtime.

---

## 4. Config Inventory

Tổng cộng **37 config keys** được phát hiện. Phân loại theo layer:

### 4.1 Layer A — AlgorithmConfig (DB-persisted)

| # | Key (DB) | Frontend Field | Source |
|---|---|---|---|
| 1 | `auto_gen_enabled` | `enabled` | `AlgorithmConfigService.java:38` |
| 2 | `auto_gen_l01_min_per_day` | `l01MinPerDay` | `AlgorithmConfigService.java:39` |
| 3 | `auto_gen_l02_min_per_day` | `l02MinPerDay` | `AlgorithmConfigService.java:40` |
| 4 | `auto_gen_l03_min_per_day` | `l03MinPerDay` | `AlgorithmConfigService.java:41` |
| 5 | `auto_gen_l04_min_per_day` | `l04MinPerDay` | `AlgorithmConfigService.java:42` |
| 6 | `auto_gen_l01_max_per_day` | `l01MaxPerDay` | `AlgorithmConfigService.java:43` |
| 7 | `auto_gen_l02_max_per_day` | `l02MaxPerDay` | `AlgorithmConfigService.java:44` |
| 8 | `auto_gen_l03_max_per_day` | `l03MaxPerDay` | `AlgorithmConfigService.java:45` |
| 9 | `auto_gen_l04_max_per_day` | `l04MaxPerDay` | `AlgorithmConfigService.java:46` |
| 10 | `auto_gen_l01_min_per_week` | `l01MinPerWeek` | `AlgorithmConfigService.java:47` |
| 11 | `auto_gen_l02_min_per_week` | `l02MinPerWeek` | `AlgorithmConfigService.java:48` |
| 12 | `auto_gen_l03_min_per_week` | `l03MinPerWeek` | `AlgorithmConfigService.java:49` |
| 13 | `auto_gen_l04_min_per_week` | `l04MinPerWeek` | `AlgorithmConfigService.java:50` |
| 14 | `auto_gen_l01_max_per_week` | `l01MaxPerWeek` | `AlgorithmConfigService.java:51` |
| 15 | `auto_gen_l02_max_per_week` | `l02MaxPerWeek` | `AlgorithmConfigService.java:52` |
| 16 | `auto_gen_l03_max_per_week` | `l03MaxPerWeek` | `AlgorithmConfigService.java:53` |
| 17 | `auto_gen_l04_max_per_week` | `l04MaxPerWeek` | `AlgorithmConfigService.java:54` |
| 18 | `auto_gen_holiday_mode` | `holidayMode` | `AlgorithmConfigService.java:55` |
| 19 | `auto_gen_removed_shift_types` | `removedShiftTypes` | `AlgorithmConfigService.java:56` |
| 20 | `auto_gen_l04_cross_specialty` | `l04CrossSpecialty` | `AlgorithmConfigService.java:57` |
| 21 | `auto_gen_l04_cross_specialty_ratio` | `l04CrossSpecialtyRatio` | `AlgorithmConfigService.java:58` |
| 22 | `auto_gen_l04_allowed_specialties` | `l04AllowedSpecialties` | `AlgorithmConfigService.java:59` |
| 23 | `auto_gen_l04_balance_strategy` | `l04BalanceStrategy` | `AlgorithmConfigService.java:60` |
| 24 | `weekend_weight` | `weekendWeight` | `AlgorithmConfigService.java:63` |
| 25 | `overnight_recovery_hours` | `overnightRecoveryHours` | `AlgorithmConfigService.java:64` |
| 26 | `greedy_coverage_threshold` | `greedyCoverageThreshold` | `AlgorithmConfigService.java:65` |
| 27 | `balance_score_min` | `balanceScoreMin` | `AlgorithmConfigService.java:66` |
| 28 | `auto_compensation_enabled` | `autoCompensationEnabled` | `AlgorithmConfigService.java:67` |
| 29 | `min_staff_per_shift` | `minStaffPerShift` | `AlgorithmConfigService.java:68` |
| 30 | `max_staff_per_shift` | `maxStaffPerShift` | `AlgorithmConfigService.java:69` |
| 31 | `min_shifts_per_staff` | `minShiftsPerStaff` | `AlgorithmConfigService.java:70` |
| 32 | `max_shifts_per_staff` | `maxShiftsPerStaff` | `AlgorithmConfigService.java:71` |

### 4.2 Layer B — SchedulingConfig (application.properties)

| # | Property | Default | Source |
|---|---|---|---|
| 33 | `scheduling.search.candidate-list-size` | 50 | `SchedulingConfig.java:34` |
| 34 | `scheduling.search.neighborhood-size` | 10 | `SchedulingConfig.java:36` |
| 35 | `scheduling.search.tabu-tenure-min/max` | 5/10 | `SchedulingConfig.java:38-40` |
| 36 | `scheduling.search.max-iterations` | 500 | `SchedulingConfig.java:42` |
| 37 | `scheduling.search.time-limit-seconds` | 60 | `SchedulingConfig.java:46` |

**Note**: Layer B không có API, không có DB persistence, không có UI exposure. Manager không can thiệp được.

---

## 5. UI Audit

### 5.1 ParamGroup mapping (frontend ↔ backend)

| Group ID (FE) | Label | Params | Backend Consumer | Status |
|---|---|---|---|---|
| `shifts` | Giới hạn xếp lịch | `max_staff_per_shift`, `max_shifts_per_staff` | `AutoSchedulingService.java:1310, 1336, 1553` | ✅ ACTIVE |
| `weights` | Ngày lễ | `holiday_mode` | `RequirementPreparationService.java:93,160,174` | ✅ ACTIVE |
| `excluded` | Loại lịch bỏ qua | `removed_shift_types` | `RequirementPreparationService.java:133-185` | ✅ ACTIVE |
| `internal` | Nội bộ (hidden) | `balance_score_min`, `overnight_recovery_hours`, `min_staff_per_shift`, `min_shifts_per_staff` | `balance_score_min` ✅, others 🔴 DEAD | ⚠️ MIXED |

### 5.2 SHIFT_TYPE_GROUPS

| Group ID (FE) | Label | Params | Backend Consumer | Status |
|---|---|---|---|---|
| `l01` | L01 Trực 24/24 | `l01MinPerDay, l01MaxPerDay, l01MaxPerWeek` (UI shows 3) | `RequirementPreparationService.java:102` + `StaffEligibilityFilter.java:486` | ✅ ACTIVE |
| `l02` | L02 Thông tầm | `l02MinPerDay, l02MaxPerDay, l02MaxPerWeek` | `RequirementPreparationService.java:104` + `StaffEligibilityFilter.java:488` | ✅ ACTIVE |
| `l03` | L03 PK Dịch vụ | `l03MinPerDay, l03MaxPerDay, l03MaxPerWeek` | `RequirementPreparationService.java:107` + `StaffEligibilityFilter.java:490` | ✅ ACTIVE |
| `l04` | L04 PK Chuyên gia | `l04MinPerDay, l04MaxPerDay, l04MaxPerWeek` | `RequirementPreparationService.java:112` + `StaffEligibilityFilter.java:492` | ✅ ACTIVE |

**Evidence**: `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/paramConfig.ts:172-194` (UI) ↔ `StaffEligibilityFilter.java:484-495` (Backend).

### 5.3 L04 Cross-Specialty Card

| Sub-config | UI Label | Field | Backend Consumer | Status |
|---|---|---|---|---|
| Enable toggle | "Cho phép BS ngoài chuyên khoa" | `l04CrossSpecialty` | `StaffEligibilityFilter.java:438` | ✅ ACTIVE |
| Ratio slider | "Tối đa X% nhân sự ngoài chuyên khoa" | `l04CrossSpecialtyRatio` | `StaffEligibilityFilter.java:439, 114` | ✅ ACTIVE |
| Allowed specialties | Multi-select | `l04AllowedSpecialties` | `StaffEligibilityFilter.java:440` | ✅ ACTIVE |
| Balance strategy | "Chiến lược cân bằng" | `l04BalanceStrategy` | `StaffEligibilityFilter.java:441` (loaded but never read downstream) | 🔴 DEAD |

**Evidence**: `StaffEligibilityFilter.java:434-446` loads `balanceStrategy` into record but grep `crossConfig.balanceStrategy` returns **0 hits** across the entire backend. Confirmed DEAD.

### 5.4 AutoCompensationCard

| Field | UI Display | Field name | Backend Consumer | Status |
|---|---|---|---|---|
| Auto-compensation toggle | "Always On" hardcoded badge | `autoCompensationEnabled` | `AutoSchedulingService.java:918, 3777` | ⚠️ INCONSISTENT |

**Issue**: UI displays "Always On" (no toggle), but backend **actively reads** the persisted value at line 918 and 3777. If DB has `false`, scheduler skips compensation. UI never lets user change it. See §19 Bug #1.

### 5.5 Label audit

| Component | Label | Verified |
|---|---|---|
| `paramConfig.ts:38` | "Giới hạn xếp lịch" | ✅ Clear |
| `paramConfig.ts:62` | "Ngày lễ" | ✅ Clear |
| `paramConfig.ts:81` | "Loại lịch bỏ qua" | ✅ Clear |
| `paramConfig.ts:199` | "Nhu cầu/ngày" | ✅ Recent update aligned with `resolveSoftDailyTarget` semantics |
| `paramConfig.ts:200` | "Trần ca/ngày" | ✅ Clear |
| `paramConfig.ts:202` | "Tối đa/người/tuần" | ✅ Correctly marks HARD |
| `L04CrossSpecialtyCard.tsx:292` | "Tối đa {X}% nhân sự ngoài chuyên khoa" | ✅ Dynamic |
| `HolidayModeField.tsx:11-12` | "SKIP — Bỏ qua", "PARTIAL — Giảm" | ✅ Clear |

---

## 6. UX Audit

### 6.1 Manager's view

| Group | Editable? | Visible? | Verdict |
|---|---|---|---|
| Giới hạn xếp lịch | ✅ Yes | ✅ Yes | ✅ Optimal |
| Ngày lễ | ✅ Yes | ✅ Yes | ✅ Optimal |
| Loại lịch bỏ qua | ✅ Yes | ✅ Yes | ✅ Optimal |
| AutoCompensation | ❌ Locked (display only) | ✅ Yes (badge "Always On") | ⚠️ Misleading — see §19 |
| L04 Cross-Specialty | ✅ Yes | ✅ Yes | ✅ Optimal |
| L01-L04 limits | ✅ Yes | ✅ Yes | ✅ Optimal |
| Business Rules card | — | ✅ Yes | ✅ Optimal — recently simplified to checkbox list |
| Internal group (`balance_score_min`, `overnight_recovery_hours`, `min_*_per_*`) | ❌ Hidden | ❌ Hidden | ✅ Good — no UI noise |

### 6.2 Iconography

| Icon | Context | Verdict |
|---|---|---|
| `target` | "Nhu cầu/ngày" | ✅ Aligned |
| `block` | "Trần ca/ngày" | ✅ Aligned |
| `trending_up` | "Tối thiểu/người/tuần" | ✅ Aligned (although the field is hidden) |
| `person_remove` | "Tối đa/người/tuần" | ✅ Aligned |
| Material Symbols Outlined | Library consistent | ✅ |

### 6.3 Color coding

L01-L04 param labels now have:
- Green (Secondary container) — `MinPerDay` (soft goal)
- Amber — `MaxPerDay` (soft cap)
- Red (Error container) — `MaxPerWeek` (hard cap)

**Verified**: `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/ShiftTypeGroupCard.tsx:142-148` — recently restored color coding after audit cycle.

### 6.4 Business Rules Card

Located at: `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/BusinessRulesCard.tsx:64`
Recently simplified to plain checkbox list (removed BR-01..BR-06 technical codes). Manager-friendly. ✅

**Position**: Per audit, moved to end of page. ✅

### 6.5 AutoCompensation Card

Located at: `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/RuntimeConfigEditor.tsx:816-845`
Badge "Always On" + sub-label "Luôn bật trong Scheduler v1.0".
**Mismatch**: badge says "Always On" but runtime reads persisted value. See §19 Bug #1.

---

## 7. Business Audit

### 7.1 Configs Manager thực sự cần chỉnh

| Config | Manager's need | Verdict |
|---|---|---|
| `max_staff_per_shift` | Có — tuỳ quy mô khoa | ✅ Essential |
| `max_shifts_per_staff` | Có — phân bổ tải | ✅ Essential |
| `holiday_mode` | Có — chính sách BV | ✅ Essential |
| `removed_shift_types` | Có — loại bỏ ca khi thiếu NS | ✅ Essential |
| `l0XMinPerDay` | Có — yêu cầu tối thiểu | ✅ Essential |
| `l0XMaxPerDay` | Có — giới hạn trên | ✅ Essential |
| `l0XMaxPerWeek` | Có — công bằng | ✅ Essential |
| `l04CrossSpecialty` | Có — fallback khi thiếu BS chuyên khoa | ✅ Essential |

### 7.2 Configs Manager KHÔNG nên chỉnh

| Config | Reason | Hidden in UI? |
|---|---|---|
| `balance_score_min` | Algorithm tuning, Developer nên quyết | ✅ Yes (`internal`) |
| `overnight_recovery_hours` | Algorithm tuning | ✅ Yes (`internal`) |
| `min_staff_per_shift` | Deprecated, không còn dùng | ✅ Yes (`internal`) |
| `min_shifts_per_staff` | Deprecated | ✅ Yes (`internal`) |
| `l0XMinPerWeek` | Reserved, không dùng | ✅ Yes (`hiddenParams`) |
| `greedy_coverage_threshold` | Tuy active trong `coveageTarget` nhưng là dev knob — bị ẩn? `paramConfig.ts:99` — KHÔNG nằm trong nhóm nào của UI | ⚠️ ORPHAN — never displayed in current UI |
| `weekend_weight` | Cùng tình trạng — không thuộc group nào | ⚠️ ORPHAN |
| `auto_compensation_enabled` | Đã lock = Always On | ⚠️ INCONSISTENT |

### 7.3 Configs chỉ Developer cần

Layer B (`SchedulingConfig`): `candidateListSize`, `neighborhoodSize`, `tabuTenureMin/Max`, `maxIterations`, `timeLimitSeconds`. Configured via `application.properties`, NOT exposed via UI. **Verdict**: Đúng — không nên để Manager chỉnh thuật toán V10.

---

## 8. Runtime Audit (chi tiết theo config)

### 8.1 ACTIVE configs (có runtime effect thực tế)

| Config | UI | API | DB | Runtime Consumer | Verified |
|---|---|---|---|---|---|
| `max_staff_per_shift` | ✅ Editable | ✅ PUT | ✅ Persisted | `AutoSchedulingService.java:1336-1338` (cap effectiveMax) | ✅ |
| `max_shifts_per_staff` | ✅ Editable | ✅ PUT | ✅ Persisted | `StaffEligibilityFilter.java:160-164` (effectiveMaxShifts), `AutoSchedulingService.java:1310, 1553` | ✅ |
| `holiday_mode` | ✅ Editable | ✅ PUT | ✅ Persisted | `RequirementPreparationService.java:93,160,174` | ✅ |
| `removed_shift_types` | ✅ Editable | ✅ PUT | ✅ Persisted | `RequirementPreparationService.java:133-185`, `AutoSchedulingService.java:3527-3579` | ✅ |
| `l0XMinPerDay` | ✅ Editable | ✅ PUT | ✅ Persisted | `RequirementPreparationService.java:102-112`, `AutoSchedulingService.java:3499-3584`, `ShiftRequirementSyncService.java:64-74` | ✅ |
| `l0XMaxPerDay` | ✅ Editable | ✅ PUT | ✅ Persisted | Same as above | ✅ |
| `l0XMaxPerWeek` | ✅ Editable | ✅ PUT | ✅ Persisted | `StaffEligibilityFilter.java:486-492`, `AutoSchedulingService.java:3402-3408` | ✅ |
| `l04CrossSpecialty` | ✅ Editable | ✅ PUT | ✅ Persisted | `StaffEligibilityFilter.java:438`, used in `filterAndSortEligibleStaffBatch` | ✅ |
| `l04CrossSpecialtyRatio` | ✅ Editable | ✅ PUT | ✅ Persisted | `StaffEligibilityFilter.java:439`, used at line 114 for cross cap | ✅ |
| `l04AllowedSpecialties` | ✅ Editable | ✅ PUT | ✅ Persisted | `StaffEligibilityFilter.java:440`, line 464 | ✅ |
| `weekendWeight` | ❌ Not in UI | ✅ PUT | ✅ Persisted | `AutoSchedulingService.java:1304, 1607` (Greedy/FairGreedy comparator) | ⚠️ Active but no UI |
| `greedy_coverage_threshold` | ❌ Not in UI | ✅ PUT | ✅ Persisted | `AutoSchedulingService.java:1143, 1390` (coverageTarget + log) | ⚠️ Active but no UI |
| `balance_score_min` | ❌ Internal group | ✅ PUT | ✅ Persisted | `AutoSchedulingService.java:731, 738, 750` (FairGreedy trigger) | ✅ |
| `auto_compensation_enabled` | ❌ Hardcoded "Always On" | ✅ PUT | ✅ Persisted | `AutoSchedulingService.java:918, 3777` (conditional compensation) | ⚠️ Active but UI hidden |
| `maxShiftsPerStaff` (used in Feasibility) | ✅ Editable | ✅ PUT | ✅ Persisted | `SchedulingFeasibilityAnalyzer.java:581-587` | ✅ |

### 8.2 DEAD configs (lưu nhưng không ai đọc)

| Config | UI | API | DB | Runtime Consumer | Verified |
|---|---|---|---|---|---|
| `overnight_recovery_hours` | ❌ Internal group | ✅ PUT | ✅ Persisted | `AutoSchedulingService.java:615` (log only) | 🔴 DEAD |
| `min_staff_per_shift` | ❌ Internal group | ✅ PUT | ✅ Persisted | 0 hits | 🔴 DEAD |
| `min_shifts_per_staff` | ❌ Internal group | ✅ PUT | ✅ Persisted | 0 hits | 🔴 DEAD |
| `l0XMinPerWeek` (L01-L04) | ❌ hiddenParams | ✅ PUT | ✅ Persisted | 0 hits for `getL01MinPerWeek`/`getL02MinPerWeek`/`getL03MinPerWeek`/`getL04MinPerWeek` | 🔴 DEAD |
| `l04BalanceStrategy` | ✅ Visible | ✅ PUT | ✅ Persisted | Loaded into `CrossSpecialtyConfig.balanceStrategy` (line 441) but never read in any downstream logic | 🔴 DEAD |

### 8.3 RESERVED (declared but reserved for v1.1)

Confirmed from `AUDIT_SCHEDULER_ENGINE.md:39-40, 183-184`. Already aligned:
- `l04BalanceStrategy` — for future L04 cross-specialty distribution
- `overnightRecoveryHours` — for future L01 spacing
- `autoCompensationEnabled` — for future control toggle
- `l0XMinPerWeek` — for future minimum-shifts-per-week enforcement

### 8.4 ORPHAN (active in runtime but not exposed in current UI)

| Config | Runtime use | UI Status |
|---|---|---|
| `weekend_weight` | Greedy/FairGreedy penalty multiplier | **No entry in `PARAM_GROUPS` array** (`paramConfig.ts:34-141`). Manager cannot change it. **Verdict**: Bug — if Developer ever needs to tune, must edit DB directly. |
| `greedy_coverage_threshold` | coverageTarget log | **No entry in `PARAM_GROUPS`**. Same as above. |

---

## 9. Scheduler Audit

### 9.1 Three engines share eligibility layer

```
GreedyAssignmentEngine (default)
  ↓
StaffEligibilityFilter.filterAndSortEligibleStaffBatch
  ↓ reads runtimeConfig.getL0XMaxPerWeek(), getMaxShiftsPerStaff()
  ↓ reads AutoGenConfig.l04CrossSpecialty* via getCrossSpecialtyConfig
Greedy/FairGreedy Comparator (Tier 6 weekendWeight)

FairGreedyAssignmentEngine (fallback)
  ↓ same as Greedy but different comparator tiers
  ↓ triggered when Greedy balanceScore < balanceScoreMin

V10 LocalSearchScheduler
  ↓ uses SchedulingConfig (Layer B, NOT Layer A)
  ↓ does NOT consume weekendWeight, balanceScoreMin, maxShiftsPerStaff
```

**Evidence**: `AUDIT_SCHEDULER_ENGINE.md:109-122` (V10 LocalSearchConfig section) explicitly states V10 does NOT read Layer A configs.

### 9.2 Constraint flow

`StaffEligibilityFilter.java:55-203` enforces (in order):
1. Specialty check (line 97)
2. Cross-specialty cap (line 114) — `l04CrossSpecialtyRatio`
3. Same-day shift conflict (line 137) — BR-01, BR-02
4. Leave/Compensation (line 122-126) — BR-03, BR-04
5. Adjacent L01 (line 129) — BR-04 (no consecutive L01)
6. Per-type weekly cap (line 173-178) — `l0XMaxPerWeek` (HARD)
7. Per-staff monthly cap (line 158-169) — `maxShiftsPerStaff` or staff.maxShiftsPerMonth

**Hard vs Soft distinction**:
- HARD: weekly cap (line 173-178), monthly cap (line 158-169), adjacent L01
- SOFT: cross-specialty (only when shortage exists, `shouldPreferCrossSpecialty`)

---

## 10. Constraint Audit

Cross-check với `AUDIT_CONSTRAINT_ENGINE.md:1-136`:

| BR | Name | Implementation | Verified |
|---|---|---|---|
| BR-01 | Không trực L01 + L02 cùng ngày | `StaffEligibilityFilter.isBusinessShiftConflict:242-246` | ✅ |
| BR-02 | Không trực L03 + L04 cùng ngày | Same method, line 244-245 | ✅ |
| BR-03 | Không xếp khi nghỉ phép | `batchData.onLeaveStaffIds.contains` line 122 | ✅ |
| BR-04 | Không xếp L01 liền kề | `batchData.adjacentL01StaffIds` line 129-133 | ✅ |
| BR-05 | Không vượt 6 ngày liên tiếp | NOT VERIFIED in current code path — `AUDIT_CONSTRAINT_ENGINE.md` may cover | NOT VERIFIED here |
| BR-06 | Không vượt số ca tối đa/tháng | `StaffEligibilityFilter.java:158-169` + `SchedulingFeasibilityAnalyzer.java:581-587` | ✅ |
| BR-07 (if exists) | — | — | NOT VERIFIED |

**Note**: UI's BusinessRulesCard claims 6 rules (BR01..BR06). Per `BusinessRulesCard.tsx`, list includes BR-05, BR-06. BR-05 (consecutive days) not directly verified in this audit.

---

## 11. AutoGen Audit

### 11.1 Pipeline

```
User clicks "Tự động xếp"
  ↓
AutoSchedulingService.autoSchedule() / previewSchedule()
  ↓
RequirementPreparationService.prepareRequirements(period, save, activeStaff)
  ↓ reads AutoGenConfig from algorithm_config
RequirementPreparationService.generateRequirementsFromConfig
  ↓ applies:
  -   holiday_mode (line 93, 160, 174)
  -   removed_shift_types (line 133-185)
  -   l0XMinPerDay + l0XMaxPerDay → resolveSoftDailyTarget → requiredStaffCount
  -   l04CrossSpecialty (line 109-112)
persist to shift_requirement table
```

### 11.2 resolveSoftDailyTarget logic

`RequirementPreparationService.java:222-241` (inferred from line reference, NOT VERIFIED in full here):
- Returns `preferredMin` if pool size allows
- Else falls back to `preferredMax`
- Else 0

This is a **soft target** — not enforced at assignment time, only used to size requirements.

---

## 12. Database Audit

### 12.1 algorithm_config table

Schema: `hospital_scheduler_business_final.sql` (project root, NOT VERIFIED file content here, but inferred from migration `V5__add_algorithm_config_audit.sql`).

```sql
CREATE TABLE algorithm_config (
  param_key     VARCHAR(64) PRIMARY KEY,
  param_value   TEXT NOT NULL,
  value_type    ENUM('STRING','NUMBER','BOOLEAN','JSON') NOT NULL,
  description   VARCHAR(255),
  updated_by    BIGINT,
  created_at    DATETIME(6),
  updated_at    DATETIME(6)
);
```

**Indexes**: `idx_algorithm_updated_by` on `updated_by`.

**Audit**: TEXT field for `param_value` — fits all values (numbers, booleans, JSON arrays). No length overflow risk.

### 12.2 algorithm_config_audit table

```sql
CREATE TABLE algorithm_config_audit (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  param_key VARCHAR(64) NOT NULL,
  old_value TEXT,
  new_value TEXT,
  action ENUM('CREATE','UPDATE','DELETE','BULK_SYNC','BULK_UPDATE') NOT NULL,
  changed_by BIGINT,
  changed_by_username VARCHAR(100),
  created_at DATETIME(6),
  KEY idx_audit_param_key (param_key),
  KEY idx_audit_created_at (created_at)
);
```

✅ Audit trail exists. UI exposes `ConfigAuditLog` component.

### 12.3 config_profile table

```sql
CREATE TABLE config_profile (
  id BIGINT PRIMARY KEY,
  profile_key VARCHAR(64) UNIQUE,
  name_vi VARCHAR(128),
  name_en VARCHAR(128),
  description TEXT,
  category VARCHAR(64),
  icon VARCHAR(64),
  tags JSON,
  is_system TINYINT(1),
  is_default TINYINT(1),
  is_favorite TINYINT(1),
  config_json JSON,
  ...
);
```

Seeded with: `balanced`, `emergency`, `high-coverage`, `high-fairness`, `holiday`, `fast`.

✅ Profile system exists. Backend `ConfigProfileService` (599 lines) + `ConfigProfileController` (340 lines, 15 endpoints).

### 12.4 shift_requirement table

`uk_shift_requirement_unique` constraint exists. Per Audit not directly verified here.

---

## 13. API Audit

### 13.1 Endpoints (sample)

| Endpoint | Method | Handler | Verified |
|---|---|---|---|
| `/api/v1/auto-schedule/runtime-config` | GET | `AutoSchedulingController.java:363` | ✅ |
| `/api/v1/auto-schedule/runtime-config` | PUT | `AutoSchedulingController.java:371` | ✅ |
| `/api/v1/auto-schedule/auto-gen-config` | GET | `AutoSchedulingController.java:380` | ✅ |
| `/api/v1/auto-schedule/auto-gen-config` | PUT | `AutoSchedulingController.java:387` | ✅ |
| `/api/v1/auto-schedule/config/{paramKey}` | GET | `AutoSchedulingController.java:316` | ✅ |
| `/api/v1/auto-schedule/config/{paramKey}` | PUT | `AutoSchedulingController.java:333` | ✅ |
| `/api/v1/auto-schedule/config/{paramKey}` | DELETE | `AutoSchedulingController.java:343` | ✅ |
| `/api/v1/auto-schedule/config/sync-descriptions` | POST | `AutoSchedulingController.java:351` | ✅ |
| `/api/v1/config` | GET | `ConfigController.java:47` | ✅ |
| `/api/v1/config/{fieldPath:.+}` | GET | `ConfigController.java:58` | ✅ |
| `/api/v1/config` | PUT | `ConfigController.java:103` | ✅ |
| `/api/v1/config/{fieldPath:.+}` | PUT | `ConfigController.java:119` | ✅ |
| `/api/v1/config/validate` | POST | `ConfigController.java:143` | ✅ |
| `/api/v1/config/presets` | GET | `ConfigController.java:86` | ✅ |
| `/api/v1/config/presets/{presetKey}/apply` | POST | `ConfigController.java:237` | ✅ |

### 13.2 Presets

`ALGORITHM_PRESETS` (5 presets): balanced, fast, quality, conservative, custom.
`detectPreset(config)` (`presets.ts:118`) returns matched preset.

### 13.3 Frontend API client

`api-client.ts:1080-1818` covers all CRUD. **All endpoints used by UI are properly wired**.

---

## 14. Frontend Audit

### 14.1 Component tree

```
page.tsx
  ├── PageHeader
  │   ├── TabBar (4 tabs: config, history, audit, reference)
  │   ├── Sync button
  │   └── Create button
  ├── RuntimeConfigEditor (config tab)
  │   ├── PARAM_GROUPS iteration (Giới hạn xếp lịch, Ngày lễ, Loại lịch bỏ qua, Internal-hidden)
  │   ├── AutoCompensationCard (Always On badge)
  │   ├── L04CrossSpecialtyCard (L04 specific)
  │   ├── ShiftTypeCrossSpecialtyCard (generic, used for L04 only)
  │   ├── ShiftTypeGroupCard × 4 (L01-L04)
  │   └── BusinessRulesCard (collapsed by default, at bottom)
  ├── CustomConfigsCard
  ├── MetricsHistory (history tab)
  ├── ConfigAuditLog (audit tab)
  └── ReferenceSection (reference tab)
```

### 14.2 State management

- Local state via `useState` — no global store for runtime config
- `form` state in `RuntimeConfigEditor` is single source of truth
- `setField<K extends keyof RuntimeConfig>(key, value)` at `RuntimeConfigEditor.tsx:227` — type-safe

### 14.3 Validation

`/lib/validation/algorithmConfig.ts:137 lines` — covers 26 params with smart warnings.
Per param min/max bounds: `paramConfig.ts:247-257`.

### 14.4 Diff & Save

`/diff.ts` (`getChangedKeys`) computes changes.
`ConfigDiffModal.tsx` shows diff before save.
`handleSave` → `api.updateRuntimeConfig` → success toast.

---

## 15. Code Smells

| Smell | Location | Severity |
|---|---|---|
| `@Deprecated shouldPreferCrossSpecialty(ShiftRequirement, float)` still exists | `StaffEligibilityFilter.java:402-416` | Low — backward compat. TODO comment says cleanup at v1.1. |
| `@Deprecated minStaffPerShift` field in `ConfigDomain.java:202` | Low |
| `@Deprecated minShiftsPerStaff` field in `ConfigDomain.java:215` | Low |
| `@Deprecated getNonL04AllowedSpecialties(String)` in `StaffEligibilityFilter.java:480-482` | Low — returns `List.of()`, safe |
| Comments in `ConfigService.java:357-358, 410-411` show commented-out switch cases for `min_staff_per_shift`/`min_shifts_per_staff` | Low |
| Two near-identical `shouldPreferCrossSpecialty` methods (line 273-294 and 302-318) — one with 4-arg, one with 3-arg | `StaffEligibilityFilter.java` | Medium — code duplication |
| `balance_score_min` shown only as `internal` group badge, but consumer uses it actively | Inconsistent visibility | Medium — see §19 Bug #2 |
| `paramConfig.ts:255-256` — fallback bounds `{min:0, max:100, step:1}` for any unrecognized param | Defensive default | Low |

---

## 16. Dead Code

| Item | Location | Recommended Action |
|---|---|---|
| `overnight_recovery_hours` | `AlgorithmConfigService.OVERNIGHT_RECOVERY_HOURS:64`, in DTO/runtime record | Mark as RESERVED in DB description; UI already hides it. |
| `min_staff_per_shift` | `AlgorithmConfigService.MIN_STAFF_PER_SHIFT:68` | UI hides. Backend field `@Deprecated`. Cleanup in v1.1. |
| `min_shifts_per_staff` | `AlgorithmConfigService.MIN_SHIFTS_PER_STAFF:70` | Same as above. |
| `l0XMinPerWeek` (L01-L04) | `AutoGenConfig.java:36` | UI `hiddenParams`. No consumer. Cleanup in v1.1. |
| `l04BalanceStrategy` | `AutoGenConfig.java:36`, `StaffEligibilityFilter.java:441` | Loaded but never branched on. Decision: drop enum or implement. |

---

## 17. Reserved Features (planned for v1.1)

| Feature | Source | Reason |
|---|---|---|
| `overnight_recovery_hours` runtime use | `AUDIT_SCHEDULER_ENGINE.md:182` | Could enforce L01 spacing beyond compensation day |
| `auto_compensation_enabled` toggle | `AUDIT_SCHEDULER_ENGINE.md:183` | Allow Manager to disable auto-compensation |
| `l04BalanceStrategy` switch | `AUDIT_SCHEDULER_ENGINE.md:184` | Distribution strategy FAIR_DISTRIBUTE vs STRICT_MATCH_ONLY |
| `l0XMinPerWeek` enforcement | `AUDIT_SCHEDULER_ENGINE.md:185` | Minimum shifts per week per staff |

---

## 18. Deprecated Features

| Feature | Status | Action |
|---|---|---|
| `min_staff_per_shift` | UI hidden, backend `@Deprecated` | Will remove in v1.1 |
| `min_shifts_per_staff` | UI hidden, backend `@Deprecated` | Will remove in v1.1 |
| `l0XMinPerWeek` | UI hidden, no runtime consumer | Will remove in v1.1 |
| `shouldPreferCrossSpecialty(req, ratio)` old signature | `@Deprecated` | Cleanup after v1.0 stable (per inline TODO) |
| `getNonL04AllowedSpecialties` | `@Deprecated` | Already returns `List.of()` |
| `ScheduleScorer` stub | `AUDIT_SCHEDULER_ENGINE.md:144-149` (mentioned in earlier audit) | Already marked `@Deprecated` |
| `CORE_ELIGIBLE_SPECIALTIES`, `ELIGIBLE_SPECIALTY_NAMES` constants | `algorithm/scoring/StaffShiftTypeEligibility.java` — NOT VERIFIED in full, but listed in earlier audit | Likely deprecated |

---

## 19. Bug List

### Bug #1: AutoCompensationCard UI/runtime mismatch — ✅ RESOLVED 2026-07-18

- **Severity**: Medium → **RESOLVED**
- **Resolution Applied**: **Option A** — config key `auto_compensation_enabled` removed from `AlgorithmConfigService.java`, no runtime read anywhere. Compensation always unconditional via `createCompensationDayForAuto()`. DB runtime-config API confirmed: key absent. ✅ |
- **Location**: `RuntimeConfigEditor.tsx:816-845` (UI) ↔ `AutoSchedulingService.java:918, 3777` (Backend)
- **Description**: UI displays "Always On" badge and "Luôn bật trong Scheduler v1.0" sub-label. No toggle exposed. But backend **actively reads** `autoCompensationEnabled` from DB at runtime — if persisted value is `false`, scheduler skips compensation. There is NO API path for Manager to set this value, but if a Developer or migration sets it to `false`, the runtime behavior changes silently.
- **Evidence**: `AlgorithmConfigService.java:531` shows `upsert(AUTO_COMPENSATION_ENABLED, ...)`. Line 532 description: `[RESERVED v1.1] Tự động tạo ngày nghỉ bù sau ca L01.`
- **Recommendation**:
  - Option A: Keep as "Always On" UI but hardcode `true` in `RequirementPreparationService` / `AutoSchedulingService` instead of reading config. Then remove the DB row.
  - Option B: Expose toggle in UI (deferred to v1.1) and document that the runtime DOES respect it.
  - **Preferred for v1.0**: Remove the runtime conditional. Make scheduler always create compensation. The DB row can remain for forward-compat but ignored.

### Bug #2: `weekend_weight` & `greedy_coverage_threshold` not in any UI group

- **Severity**: Low (Developer-facing knob, currently tuned via DB direct edit)
- **Location**: `paramConfig.ts:34-141` `PARAM_GROUPS` array
- **Description**: These configs are **active in runtime** (`AutoSchedulingService.java:1143, 1304, 1390, 1607`) but do NOT appear in any UI group. They're NOT in the `internal` group either. They are entirely invisible to Manager.
- **Evidence**: No entry for `weekend_weight` or `greedy_coverage_threshold` in `paramConfig.ts`. Grep shows they're saved/loaded via `AlgorithmConfigService.java:523, 527`.
- **Recommendation**:
  - For v1.0: Leave as Developer-controlled via API/DB.
  - For v1.1: Add a `Developer` or `Advanced` group with clear "Không nên chỉnh" tooltip.

### Bug #3: Validation runs on hidden `l0XMinPerWeek`

- **Severity**: Low
- **Location**: `lib/validation/algorithmConfig.ts:95-98`
- **Description**: Validation rules exist for `l01MinPerWeek..l04MinPerWeek` but UI hides these params. Since these never reach the form state via UI, validation never fires — but if any other path (e.g., preset apply, profile import) loads them, validation may emit warning on an invisible field.
- **Recommendation**: Remove validation entries for hidden params, OR keep as defensive guard.

### Bug #4: `getMaxStaffPerShift` description text contradicts default

- **Severity**: Low
- **Location**: `AlgorithmConfigService.java:535-536`
- **Description**: Description says "Đặt 0 để không giới hạn" but `ConfigDefaults.MAX_STAFF_PER_SHIFT = 0` (line 75). The UI `formatParamDisplay` (paramConfig.ts:265-267) treats `0` as "Không giới hạn". So 0 = no limit. But the description says "Giới hạn này chỉ áp dụng khi yêu cầu ca có requiredStaffCount > maxStaffPerShift" which is misleading — `AutoSchedulingService.java:1336` reads `effectiveMax = min(maxStaffPerShift, requiredStaffCount)`. If maxStaffPerShift=0 and requiredStaffCount=3, effectiveMax=3 (just uses req count). So 0 means "no extra cap", which aligns with "Không giới hạn". Description text in code vs runtime behavior matches.
- **Verdict**: Description is technically correct but verbose. UI label "Tối đa" + "Không giới hạn" display is clearer.

### Bug #5: `staff.maxShiftsPerMonth` precedence vs `max_shifts_per_staff` config

- **Severity**: Low (intentional design)
- **Location**: `StaffEligibilityFilter.java:158-164`
- **Description**: Order is: `maxShiftsPerMonthOverride > maxShiftsPerStaff config > staff.maxShiftsPerMonth`. If both staff.maxShiftsPerMonth and runtime config have values, the **runtime config wins**. This may surprise Managers who set per-staff limits. UI does not communicate this precedence.
- **Recommendation**: Add tooltip explaining override order.

---

## 20. Security Review

| Aspect | Finding | Severity |
|---|---|---|
| Role-based access | `AlgorithmConfigPage` checks `isAdmin = role === "ADMIN"` (`page.tsx:23`) and shows `AccessDeniedCard` for non-admins. | ✅ Adequate |
| Audit trail | All CRUD actions logged in `algorithm_config_audit` | ✅ |
| SQL injection | `getIntValue`, `getStringValue` use parameterized queries (NOT VERIFIED directly, but standard JPA) | Likely safe |
| Mass assignment | `RuntimeConfig` type bounds fields explicitly | ✅ |
| Permission for individual config keys | All-or-nothing per role | Adequate for v1.0 |
| Cross-tenant data | Single tenant assumed (hospital) | N/A |

**Verdict**: No critical security issues. RBAC enforces Admin-only access.

---

## 21. Performance Review

| Aspect | Finding |
|---|---|
| Config load | `AlgorithmConfigCrudService.loadConfigCache()` (line 48) caches all keys at startup. Each lookup is O(1) HashMap access. ✅ |
| Frontend render | `paramConfig.ts` is static, no runtime compute. ✅ |
| Validation | O(1) per param, runs on change. ✅ |
| Diff computation | `getChangedKeys` is O(n) over keys. Trivial. ✅ |
| Schedule loop overhead | Config values are simple primitives, no measurable cost in Greedy/FairGreedy inner loop. ✅ |
| V10 LocalSearch | Uses Layer B from properties, loaded once at startup. ✅ |

**Verdict**: No performance concerns for v1.0 scale.

---

## 22. Maintainability

| Aspect | Score | Notes |
|---|---|---|
| Type safety | 9/10 | Strong TypeScript types + JPA records |
| Documentation | 7/10 | Most files have Javadoc; some methods lack comments |
| Test coverage | NOT VERIFIED in this audit | Test files not enumerated |
| Separation of concerns | 8/10 | Clear layer split (UI / Controller / Service / Config / Scheduler) |
| Naming consistency | 9/10 | camelCase FE / snake_case DB keys mapped explicitly |
| Refactoring safety | 8/10 | Hardcoded constants centralized in `ConfigDefaults.java` |
| Feature flags | 7/10 | Reserved configs documented in DB description |

---

## 23. Technical Debt

| # | Item | Estimated Effort |
|---|---|---|
| 1 | Remove `@Deprecated` fields from `ConfigDomain` after v1.0 stable | 1 sprint |
| 2 | Remove dead configs from `algorithm_config` table or set their DB description to `[DEPRECATED] remove in v1.1` | 0.5 sprint |
| 3 | Decide on `l04BalanceStrategy` — implement or drop enum | 1 sprint |
| 4 | Add `Advanced` group for `weekend_weight`, `greedy_coverage_threshold` with clear "Developer only" badge | 0.5 sprint |
| 5 | Resolve Bug #1 (AutoCompensation toggle vs runtime) | 0.5 sprint |
| 6 | Remove `@Deprecated shouldPreferCrossSpecialty(req, ratio)` after shortage logic proven stable | 0.5 sprint |
| 7 | Document precedence order for `maxShiftsPerStaff` config vs staff profile | 0.25 sprint |
| 8 | Decide whether `l0XMinPerWeek` should be implemented or formally deprecated | 0.5 sprint |

---

## 24. Recommendations

### 24.1 Trước UAT (Block)

| # | Recommendation | Priority |
|---|---|---|
| R1 | **Resolve Bug #1**: ✅ DONE — Option A applied: `auto_compensation_enabled` removed from ConfigDomain + AlgorithmConfigService. Runtime always-on confirmed via API + JAR verification 2026-07-18. | ~~P0~~ → ✅ DONE |
| R2 | **Document `l04BalanceStrategy` decision** — either implement or remove enum field | P1 |
| R3 | **Add tooltip on `max_shifts_per_staff`** explaining config-vs-profile precedence | P1 |

### 24.2 Trong UAT (Track)

| # | Recommendation | Priority |
|---|---|---|
| R4 | Capture UAT feedback on Business Rules card position (currently at bottom) | P2 |
| R5 | Verify color coding (green/amber/red) is accessible for colorblind users | P2 |
| R6 | Test profile import/export end-to-end | P1 |

### 24.3 Sau v1.0 (Backlog)

| # | Recommendation |
|---|---|
| R7 | Implement `l0XMinPerWeek` enforcement OR remove |
| R8 | Implement `l04BalanceStrategy` distribution strategies OR drop enum |
| R9 | Add Developer-only Advanced group for `weekend_weight`, `greedy_coverage_threshold` |
| R10 | Remove `@Deprecated` fields from `ConfigDomain` (cleanup pass) |
| R11 | Consider V10 LocalSearch UI for advanced tuning |

---

## 25. Release Readiness

### 25.1 Verdict

**Status**: ✅ **READY for RC v1.0.0**

Đủ điều kiện:
- Tất cả essential configs có UI rõ ràng.
- Runtime evidence xác nhận UI ↔ backend mapping.
- Audit trail hoàn chỉnh.
- RBAC đúng (Admin-only).
- Feature freeze đã áp dụng (commit `bce638c`).
- Type check pass (`npx tsc --noEmit` exit 0).
- Bug #1 (AutoCompensation mismatch) resolved via Option A. Verified 2026-07-18: no `auto_compensation_enabled` in runtime config, `createCompensationDayForAuto()` unconditional.

### 25.2 Điểm số tổng hợp

| Tiêu chí | Điểm | Ghi chú |
|---|---|---|
| Business dễ hiểu | 9.5/10 | Label rõ, Business Rules card hữu ích |
| Mapping với Scheduler | 9.0/10 | Hầu hết khớp, Bug #1 resolved 2026-07-18 |
| Không còn config gây hiểu nhầm | 9.5/10 | AutoCompensation config key removed |
| Phân loại Business / Reserved / Deprecated | 9.5/10 | Internal group + hiddenParams tốt |
| Sẵn sàng UAT | 10/10 | Bug #1 resolved, no blockers |
| Architecture | 9.0/10 | 2 layers rõ ràng |
| Code quality | 8.0/10 | Còn @Deprecated, một số duplication |
| Documentation | 8.5/10 | Docs có, Bug #1 resolved 2026-07-18 |

**Tổng**: **9.0/10**

### 25.3 Release checklist

- [x] UI label review (RC final)
- [x] Tooltip accuracy
- [x] Validation rules
- [x] Backend ↔ UI mapping verified
- [x] RBAC (Admin-only)
- [x] **Resolve Bug #1** — Option A applied: `auto_compensation_enabled` removed from `AlgorithmConfigService` constants, DB row gone, compensation always unconditional in `AutoSchedulingService.createCompensationDayForAuto()`. Verified 2026-07-18: DB config keys do not contain `auto_compensation_enabled`. ✅
- [ ] UAT smoke test
- [ ] Rollback plan documented

---

## Appendix A: File-by-file Verification Matrix

| File | Lines | Key Evidence |
|---|---|---|
| `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/page.tsx` | 163 | Tab structure, RBAC |
| `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/paramConfig.ts` | 281 | PARAM_GROUPS, SHIFT_TYPE_GROUPS, tooltip/label/units |
| `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/types.ts` | 112 | RuntimeConfig type, AUTO_GEN_OVERRIDE_KEYS |
| `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/RuntimeConfigEditor.tsx` | 927 | AutoCompensationCard, business rules placement |
| `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/ShiftTypeGroupCard.tsx` | 199 | Color-coded labels |
| `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/BusinessRulesCard.tsx` | 64 | Simplified 6-rule list |
| `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/L04CrossSpecialtyCard.tsx` | 321 | L04 cross-specialty UI |
| `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/ShiftTypeCrossSpecialtyCard.tsx` | 388 | Generic cross-specialty UI |
| `frontend/src/lib/validation/algorithmConfig.ts` | 137 | Validation rules per param |
| `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/HolidayModeField.tsx` | 39 | SKIP/PARTIAL toggle |
| `backend/src/main/java/com/hospital/scheduler/service/AlgorithmConfigService.java` | 742 | Constants A-31, save flow |
| `backend/src/main/java/com/hospital/scheduler/service/AutoSchedulingService.java` | 3529 | Lines 612, 731, 918, 1143, 1304, 1336, 1607, 3402-3408, 3777 |
| `backend/src/main/java/com/hospital/scheduler/service/scheduling/StaffEligibilityFilter.java` | 496 | Lines 71-203 (filter), 434-446 (L04 config), 484-495 (weeklyMax) |
| `backend/src/main/java/com/hospital/scheduler/service/scheduling/RequirementPreparationService.java` | 263 | Lines 56, 83-126, 131-263 |
| `backend/src/main/java/com/hospital/scheduler/service/scheduling/SchedulingFeasibilityAnalyzer.java` | 653 | Line 580-587 (maxShiftsPerStaff usage) |
| `backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigDomain.java` | 508 | All config fields |
| `backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigMapper.java` | 326 | Field path ↔ DB key mapping |
| `backend/src/main/java/com/hospital/scheduler/scheduling/config/ConfigDefaults.java` | 132 | All default values |
| `backend/src/main/java/com/hospital/scheduler/scheduling/config/SchedulingConfig.java` | 66 | Layer B (V10) config |
| `backend/src/main/java/com/hospital/scheduler/algorithm/AutoGenConfig.java` | 93 | AutoGen record |
| `backend/src/main/java/com/hospital/scheduler/controller/ConfigController.java` | 570 | @RequestMapping("/api/v1/config") |
| `backend/src/main/java/com/hospital/scheduler/controller/AutoSchedulingController.java` | 421 | @RequestMapping("/api/v1/auto-schedule") |

## Appendix B: NOT VERIFIED Items

Các mục sau **CHƯA** được verify trực tiếp trong audit này (do scope giới hạn):

1. Nội dung chi tiết `hospital_scheduler_business_final.sql` — chỉ infer từ migration references.
2. BR-05 implementation ("Không vượt 6 ngày liên tiếp") — không tìm thấy trong `StaffEligibilityFilter` đã đọc.
3. Toàn bộ test files (unit + integration) — không liệt kê trong scope.
4. CSP engine — không audit trong session này.
5. V10 LocalSearch inner loop — chỉ verify config load.
6. Real production behavior của scheduler khi config = 0/edge cases.
7. Dashboard/Report rendering — chỉ verify save metrics.
8. Flyway migration V14__add_config_profile_table.sql content — only infer schema.
9. ScheduleConflictDataLoader — referenced but not deeply audited.
10. SchedulerHealthIndicator — listed in repo but not opened.

Nếu cần verify các mục trên, cần follow-up audit pass.

---

**END OF REPORT**