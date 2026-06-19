# Business Domain Thinking Guide (Hospital Scheduler)

> **Mở rộng tư duy** cho các tác vụ liên quan đến nghiệp vụ **quản lý lịch công tác** y tế.

---

## Vì sao cần guide riêng?

Hệ thống có các quy tắc nghiệp vụ **phức tạp và dễ sai sót** (xung đột lịch, nghỉ bù, workflow kỳ lịch). Guide này giúp AI agent **đặt câu hỏi đúng** trước khi code, tránh các bug thường gặp.

---

## Triggers — Khi nào đọc guide này

Đọc guide này khi tác vụ liên quan đến:

- [ ] Tạo / sửa / xóa `Schedule`
- [ ] Tính toán `CompensationDay`
- [ ] Duyệt `LeaveRequest` hoặc `ScheduleExchange`
- [ ] Chạy `AutoScheduling`
- [ ] Thay đổi `SchedulePeriod` (status: DRAFT → PUBLISHED → ARCHIVED)
- [ ] Thay đổi mapping L01–L04 trên UI
- [ ] Sửa `ConflictDetectionService`
- [ ] Thay đổi logic gợi ý phân bổ nhân sự

---

## Câu hỏi bắt buộc trước khi code

### 1. Loại lịch nào đang thay đổi?

| Loại | Có nghỉ bù? | Có thể trùng L02? | Có thể trùng L04? |
|---|---|---|---|
| L01 | **Có** | ❌ | ✅ (L01 vs L03/L04 OK) |
| L02 | Không | ❌ | ✅ |
| L03 | Không | ✅ | ❌ (L03 vs L04 conflict) |
| L04 | Không | ✅ | ❌ |

### 2. Nhân sự có rơi vào ngoại lệ nào không?

- [ ] Có `LeaveRequest` APPROVED overlap ngày này?
- [ ] Có `CompensationDay` đã được tạo cho ngày này?
- [ ] Staff có status = INACTIVE?
- [ ] Staff có specialty phù hợp với L04 không?
- [ ] Staff đã quá tải (vd: > 12 ca L01 / tháng)?

### 3. Kỳ lịch (SchedulePeriod) đang ở trạng thái nào?

| Trạng thái | Cho phép edit schedule? |
|---|---|
| DRAFT | ✅ Có |
| PUBLISHED | ❌ Không (read-only) |
| ARCHIVED | ❌ Không |

→ Nếu cần edit schedule của kỳ đã PUBLISHED, phải tạo period mới (workflow của team).

### 4. Audit log có được ghi không?

- [ ] Mọi CREATE / UPDATE / DELETE qua `AuditHistoryService`?
- [ ] Có ghi actor (user hiện tại) không?
- [ ] Action type enum có đúng không?

### 5. Notification có cần gửi không?

| Trigger | Notification |
|---|---|
| Tạo schedule mới cho staff X | Gửi notification cho X |
| Approve leave request | Gửi cho staff submit |
| Approve schedule exchange | Gửi cho 2 bên liên quan |
| Period PUBLISHED | Gửi cho tất cả staff trong kỳ |
| Conflict detected | Gửi cho manager |

---

## Câu hỏi về tính nhất quán dữ liệu

### Compensation day có được tạo tự động không?

- Tạo L01 → **PHẢI** tạo `CompensationDay` tương ứng
- Tạo L02/L03/L04 → **KHÔNG** tạo `CompensationDay`
- Sửa schedule từ L02/L03/L04 thành L01 → **PHẢI** tạo `CompensationDay` (mới)
- Sửa schedule từ L01 thành L02 → **PHẢI xóa** `CompensationDay` cũ

### Conflict detection chạy ở đâu?

| Tầng | Có chạy không | Lý do |
|---|---|---|
| Service (`ScheduleService.create`) | **BẮT BUỘC** | Tránh duplicate trước khi save |
| Controller | ❌ | Service đã xử lý |
| Frontend (trước khi submit) | Optional (UX) | Hiển thị warning sớm, nhưng server vẫn check |

### Holiday có ảnh hưởng đến compensation day không?

→ CÓ. Xem `CompensationDateCalculator`:
- Trực T2-T5/CN rơi vào ngày lễ → lùi sang ngày làm tiếp
- Trực T6/T7 → vẫn tính T3 tuần sau, bỏ qua ngày lễ trong khoảng đó

→ Câu hỏi: holiday data có được load không? `HolidayRepository` đã được inject?

---

## Câu hỏi về UX/UI

### Hiển thị cho ai?

| User role | Thấy gì |
|---|---|
| ADMIN | Tất cả |
| MANAGER | Tất cả period DRAFT + schedule đã PUBLISHED + reports |
| STAFF | Chỉ lịch cá nhân của mình + lịch phòng khám công khai |

### Có cần real-time update không?

- Schedule của STAFF: cập nhật real-time khi MANAGER publish period
- Notification: polling mỗi 30s hoặc khi mở app (chưa có WebSocket)

### Conflict visualization

Khi `hasConflict = true`:
- Hiển thị icon `warning` màu đỏ
- Tooltip: loại conflict (L01 vs L02, trùng compensation, …)
- Click → mở modal giải thích + gợi ý action

---

## Edge cases thường gặp

| Case | Xử lý |
|---|---|
| Tạo schedule ngày hôm qua | ❌ Reject (validation) |
| Sửa L01 thành L02 khi đã có compensation_day | Xóa compensation_day, return warning |
| Tạo L01 cho ngày đã có L01 của staff khác | ✅ OK (cho phép) |
| Tạo L01 cho ngày đã có L01 của cùng staff | ❌ Conflict (unique constraint) |
| Period endDate < startDate | ❌ Reject (validation) |
| Publish period khi còn DRAFT conflict | ❌ Block publish (cần xử lý conflict trước) |
| Approve leave request overlap schedule hiện có | Set `has_conflict = true`, không tự xóa schedule |

---

## Khi nào update guide này

- Phát hiện case mới chưa cover
- Team thay đổi workflow (vd: thêm trạng thái DRAFT_REVIEW)
- Thêm module mới (VD: thêm L05, thêm loại leave mới)
- Bug production do chưa nghĩ trước → ghi lại lesson learned

---

## Quick checklist cho mọi task nghiệp vụ

- [ ] Đã đọc `.trellis/spec/backend/business-rules.md`?
- [ ] Đã đọc `.trellis/spec/frontend/business-rules-fe.md`?
- [ ] Đã xác định role ảnh hưởng (ADMIN/MANAGER/STAFF)?
- [ ] Đã kiểm tra conflict detection có chạy?
- [ ] Đã kiểm tra audit log có ghi?
- [ ] Đã kiểm tra notification có gửi (nếu cần)?
- [ ] Đã kiểm tra compensation day có tạo/xóa đúng?
- [ ] Test happy path + conflict path + edge case (cuối tuần, ngày lễ, leap year)