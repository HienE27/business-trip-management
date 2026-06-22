# Business Rules (CRITICAL)

> **QUY TẮC NGHIỆP VỤ CỐT LÕI** của Hospital Scheduler. Mọi thay đổi code phải tuân thủ.

---

## 1. Bốn loại lịch (Schedule Types)

| ID | Tên | Đặc điểm | Có nghỉ bù? | Ca |
|---|---|---|---|---|
| `L01` | Lịch trực 24/24 | 7h30 ngày N → 7h30 ngày N+1 | **Có** | Qua đêm |
| `L02` | Lịch thông tầm | Ca ngày, không nghỉ trưa | Không | Ban ngày |
| `L03` | Lịch phòng khám dịch vụ | Ca khám dịch vụ | Không | Ban ngày |
| `L04` | Lịch phòng khám chuyên gia | Ca khám chuyên sâu (theo specialty) | Không | Ban ngày |

---

## 2. RÀNG BUỘC CỨNG (HARD CONSTRAINTS) — BẮT BUỘC

### 2.1 L01 vs L02: Cùng nhân sự, cùng ngày → ❌ KHÔNG ĐƯỢC

Cùng một nhân sự **không thể** vừa trực 24/24 (L01) vừa thông tầm (L02) trong cùng một ngày. Lý do: L01 đã bao trùm ca trực ban ngày + đêm.

```java
// Pseudo-code trong ConflictDetectionService
if (shiftTypeA == L01 && shiftTypeB == L02 && sameStaff && sameDate) {
    throw new ConflictException("L01 và L02 không thể cùng ngày cho cùng nhân sự");
}
```

### 2.2 L03 vs L04: Cùng nhân sự, cùng ngày → ❌ KHÔNG ĐƯỢC

Cùng một nhân sự **không thể** vừa khám dịch vụ (L03) vừa khám chuyên gia (L04) trong cùng một ngày. Lý do: hai ca khám trùng thời gian, không thể phục vụ cả hai.

### 2.3 Ngày nghỉ bù → ❌ KHÔNG xếp bất kỳ lịch nào

Nếu ngày D là `compensation_day` của nhân sự X, ngày D+1 hoặc ngày bất kỳ được tính là nghỉ bù → X **không thể** có schedule nào (L01/L02/L03/L04) vào ngày đó.

```java
// Trước khi save schedule
if (compensationDayRepository.existsByStaffIdAndCompensationDate(staffId, workDate)) {
    throw new ConflictException("Ngày " + workDate + " là ngày nghỉ bù của nhân sự " + staffId);
}
```

### 2.4 Unique constraint: 1 staff/1 ngày/1 loại

```sql
UNIQUE KEY uk_schedule_unique (period_id, staff_id, shift_type_id, work_date)
```

→ Một nhân sự chỉ có **đúng 1 lịch** cho mỗi (period, date, shiftType). Hệ thống reject duplicate.

---

## 3. Quy tắc nghỉ bù (Compensation Day)

Khi tạo L01 (trực 24/24), PHẢI tự động tính và lưu `compensation_day`:

| Trực ngày | Nghỉ bù |
|---|---|
| Thứ 2 (Monday) | Thứ 3 (tuần này) |
| Thứ 3 (Tuesday) | Thứ 4 (tuần này) |
| Thứ 4 (Wednesday) | Thứ 5 (tuần này) |
| Thứ 5 (Thursday) | Thứ 6 (tuần này) |
| Thứ 6 (Friday) | **Thứ 3 tuần sau** (bỏ T2, T6) |
| Thứ 7 (Saturday) | **Thứ 3 tuần sau** (bỏ T2, T6) |
| Chủ Nhật (Sunday) | **Thứ 2 tuần sau** |

Xem code: `util/CompensationDateCalculator.java`.

### Holiday avoidance (tránh ngày lễ)

Nếu ngày nghỉ bù rơi vào ngày lễ:

| Trực | Hành vi |
|---|---|
| Thứ 2–Thứ 5, Chủ Nhật | Lùi sang ngày làm việc tiếp theo (Thứ 2 hoặc Thứ 6 được phép) |
| Thứ 6, Thứ 7 | Vẫn là **Thứ 3 tuần sau**, bỏ qua T2 + T6 + các ngày lễ trong khoảng đó |

Holidays được quản lý trong bảng `holiday`.

---

## 4. Workflow kỳ lịch (Schedule Period)

```
DRAFT  →  PUBLISHED  →  ARCHIVED
```

| Status | Ý nghĩa | Cho phép edit? | Hiển thị với staff? |
|---|---|---|---|
| `DRAFT` | Đang soạn, chưa công bố | ✅ Có | ❌ Không |
| `PUBLISHED` | Đã công bố chính thức | ❌ Không (phải tạo period mới) | ✅ Có |
| `ARCHIVED` | Lưu trữ, kỳ đã qua | ❌ Không | Chỉ xem lịch sử |

Mỗi period có `startDate` + `endDate` (thường 1 tháng). Schedule chỉ thuộc 1 period.

---

## 5. Roles & Permissions

| Role | Permissions |
|---|---|
| `ADMIN` | Toàn quyền: CRUD tất cả resource, quản lý user, audit log |
| `MANAGER` | Xếp lịch (create/update/delete schedule), duyệt đổi ca, xem báo cáo, **không** quản lý user |
| `STAFF` | Xem lịch cá nhân, gửi yêu cầu nghỉ phép, yêu cầu đổi ca |

Annotation: `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")` (xem `StaffController.java`).

---

## 6. Leave Request (Yêu cầu nghỉ phép)

- Staff gửi `LeaveRequest` với `startDate`, `endDate`, `reason`, `type` (ANNUAL, SICK, UNPAID, OTHER).
- Manager approve/reject.
- Khi approved, **KHÔNG tự động xóa schedule** đã có — chỉ set `has_conflict = true` và notify manager.

---

## 7. Schedule Exchange (Đổi ca)

- Staff A muốn đổi ca với Staff B → tạo `ScheduleExchange` với `requesterScheduleId` + `targetScheduleId`.
- Trạng thái: `PENDING` → `APPROVED` (manager duyệt) → swap schedule owners.
- Hoặc `REJECTED` / `CANCELLED`.

### 7.1 Chỉ ca L01 (24/24) mới được đổi — HARD CONSTRAINT

**Hai ca trong `createExchange` ÍT NHẤT MỘT phải là L01.** Lý do: L01 là ca qua đêm 24/24, có nghỉ bù kèm theo — chỉ loại ca này mới có ý nghĩa khi đổi giữa hai nhân sự (tránh staff ôm đồm ca L02/L03/L04 của người khác).

```java
// Trong ScheduleExchangeService.createExchange
boolean requesterIsL01 = "L01".equals(requesterSchedule.getShiftType().getId());
boolean targetIsL01    = "L01".equals(targetSchedule.getShiftType().getId());
if (!requesterIsL01 && !targetIsL01) {
    throw new BadRequestException("Chỉ có ca trực L01 (24/24) mới có thể yêu cầu đổi ca");
}
```

→ Enforced sau các check `cùng period` + `cùng status PUBLISHED`, trước khi save `ScheduleExchange`.

---

## 8. Auto Scheduling (M07)

Algorithm module dùng để gợi ý xếp lịch tự động. Ràng buộc:

| Hard | Soft (ưu tiên) |
|---|---|
| Tất cả ràng buộc ở §2 | Phân bổ đều giữa các nhân sự |
| Mỗi ngày đủ `shift_requirement` | Cân bằng chuyên khoa (L04 cần đúng specialty) |
| Tránh xếp 2 ca liên tiếp cho 1 người | Ưu tiên người ít ca trong tháng |

Xem `algorithm-specialist.md` và `service/AlgorithmConfigService.java`.

---

## 9. Validation checklist cho mọi Schedule create/update

```java
public void validateSchedule(ScheduleRequest req) {
    // 1. Period tồn tại và status = DRAFT
    // 2. Staff tồn tại và status = ACTIVE
    // 3. ShiftType tồn tại
    // 4. workDate nằm trong period range
    // 5. KHÔNG trùng schedule khác của cùng staff/cùng date/cùng type
    // 6. KHÔNG có compensation_day của staff vào workDate
    // 7. Nếu shiftType = L01: KHÔNG trùng L02 của staff cùng ngày
    // 8. Nếu shiftType = L02: KHÔNG trùng L01 của staff cùng ngày
    // 9. Nếu shiftType = L03: KHÔNG trùng L04 của staff cùng ngày
    // 10. Nếu shiftType = L04: KHÔNG trùng L03 của staff cùng ngày
    // 11. Nếu staff có LeaveRequest APPROVED overlap workDate → WARN
    // 12. Nếu shiftType = L04: staff phải có specialty phù hợp
}
```

Đặt logic này trong `ConflictDetectionService.validate()`.

---

## 10. Test cases bắt buộc (cho `trellis-check` agent)

| Case | Expected |
|---|---|
| Tạo L01 cho staff X ngày D | OK, auto tạo compensation_day D+1 (hoặc theo rule) |
| Tạo L02 cho staff X ngày D, đã có L01 ngày D | ❌ ConflictException |
| Tạo L03 cho staff X ngày D, đã có L04 ngày D | ❌ ConflictException |
| Tạo bất kỳ lịch nào cho staff X vào ngày compensation_day | ❌ ConflictException |
| Tạo L01 ngày thứ 6 → compensation_day | Thứ 3 tuần sau |
| Tạo L01 ngày thứ 7 → compensation_day | Thứ 3 tuần sau |
| Tạo L01 ngày Chủ Nhật → compensation_day | Thứ 2 tuần sau |
| Tạo L01 ngày thường rơi vào ngày lễ | Lùi sang ngày làm tiếp |
| Đổi ca: cả 2 schedules đều không phải L01 | ❌ `BadRequestException` "Chỉ có ca trực L01..." |
| Đổi ca: 1 trong 2 là L01 | ✅ OK, tạo `ScheduleExchange` PENDING |

---

## 11. Khi nào update file này

| Trigger | Hành động |
|---|---|
| Thay đổi cách tính nghỉ bù | Update §3 + update `CompensationDateCalculator` + update test |
| Thêm loại lịch mới (L05, …) | Update §1, §2, §9 |
| Thay đổi permission role | Update §5 + update `SecurityConfig` + update test |
| Thay đổi period workflow | Update §4 + update `SchedulePeriod` entity + update `SchedulePeriodService` |
| Thay đổi conflict rule | Update §2, §9 + update `ConflictDetectionService` + update test |
| Thay đổi exchange eligibility (vd: cho phép L02 đổi) | Update §7.1 + update `ScheduleExchangeService` + update test |

**Mọi thay đổi business rule PHẢI được review bởi team lead và ghi vào CHANGELOG.**