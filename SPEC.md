# SPEC.md - Hệ thống Quản lý Lịch Công Tác

## Thông tin dự án

| Trường | Giá trị |
|--------|---------|
| Phiên bản | 1.1 |
| Ngày lập | 05/2026 |
| Người hướng dẫn | ThS. Văn Minh Hoàng Quân |
| Nhóm thực hiện | Nhóm 4 |
| Công nghệ | Java Spring Boot + MySQL + REST API |
| Database | hospital_scheduler (utf8mb4) |

## 1. Tổng quan hệ thống

### 1.1 Mục tiêu
Xây dựng website quản lý lịch công tác cho phòng gồm 20 nhân sự. Hệ thống hỗ trợ xếp lịch 4 loại, kiểm tra xung đột tự động và tự động phân công lịch theo thuật toán.

### 1.2 Các loại lịch trong hệ thống

| Mã | Tên | Mô tả | Ưu tiên |
|----|-----|--------|---------|
| L01 | Lịch trực 24/24 | Nhân sự trực liên tục từ 7h30 ngày N đến 7h30 ngày N+1. Sau ngày trực, nhân sự được nghỉ bù ngày kế tiếp. | Cốt lõi |
| L02 | Lịch thông tầm | Nhân sự làm ca liên tục không nghỉ trưa trong ngày được chọn. | Cốt lõi |
| L03 | Lịch phòng khám dịch vụ | Nhân sự phụ trách ca khám dịch vụ trong ngày được chọn. | Cốt lõi |
| L04 | Lịch phòng khám chuyên gia | Chuyên gia phụ trách ca khám chuyên sâu trong ngày được chọn. | Cốt lõi |

### 1.3 Quy tắc chọn ngày

- Tất cả 4 loại lịch đều chỉ yêu cầu chọn **NGÀY**, không cần nhập giờ hoặc chọn ca.
- Lịch trực 24/24: chọn ngày N => hệ thống tự hiểu ca trực từ 7h30 ngày N đến 7h30 ngày N+1.
- Lịch thông tầm, phòng khám dịch vụ, phòng khám chuyên gia: chọn ngày N => ghi nhận lịch làm việc trong ngày N.

### 1.4 Quy định nghỉ bù sau trực 24/24

| Trực | Nghỉ bù |
|------|---------|
| Thứ 2 | Thứ 3 (tuần này) |
| Thứ 3 | Thứ 4 (tuần này) |
| Thứ 4 | Thứ 5 (tuần này) |
| Thứ 5 | Thứ 6 (tuần này) |
| Thứ 6 | Thứ 3 tuần sau |
| Thứ 7 | Thứ 3 tuần sau |
| Chủ Nhật | Thứ 2 (tuần sau) |

**Lưu ý:** Ngày nghỉ bù được dời sang tuần sau KHÔNG được rơi vào Thứ 2 hoặc Thứ 6.

### 1.5 Ràng buộc nghiệp vụ cốt lõi

1. **Lịch trực 24/24 và Lịch thông tầm**: Cùng nhân sự, cùng ngày => KHÔNG được đồng thời có cả 2 loại lịch.

2. **Ngày nghỉ bù**: Cùng nhân sự => KHÔNG được xếp bất kỳ loại lịch nào vào ngày nghỉ bù.

3. **Lịch phòng khám dịch vụ và Lịch phòng khám chuyên gia**: Cùng nhân sự, cùng ngày => KHÔNG được đồng thời có cả 2 loại lịch.

---

## 2. Các Module chức năng

### Module M01 — Quản lý nhân sự

| Mã | Chức năng | Mô tả | Ưu tiên |
|----|-----------|-------|---------|
| M01-F01 | Thêm nhân sự | Nhập: họ tên, mã nhân viên, chức vụ, chuyên khoa, SĐT, email. Kiểm tra trùng mã NV. | Trung bình |
| M01-F02 | Sửa thông tin nhân sự | Chỉnh sửa thông tin; lưu lịch sử thay đổi. | Trung bình |
| M01-F03 | Ngừng hoạt động | Đánh dấu nghỉ việc (soft delete). | Trung bình |
| M01-F04 | Tìm kiếm & lọc | Tìm theo tên, mã NV, chức vụ, chuyên khoa, trạng thái. | Trung bình |
| M01-F05 | Phân quyền hệ thống | 3 vai trò: Quản lý lịch, Trưởng phòng, Nhân viên. | Trung bình |

### Module M02 — Lịch trực 24/24

| Mã | Chức năng | Mô tả | Ưu tiên |
|----|-----------|-------|---------|
| M02-F01 | Xếp lịch trực 24/24 theo tháng | Gán ngày trực cho từng nhân sự trên bảng lịch tháng. | Cao |
| M02-F02 | Kiểm tra xung đột hàng loạt | Quét toàn bộ, phát hiện lịch trực 24/24 trùng thông tầm. | Cao |
| M02-F03 | Chỉnh sửa lịch trong tháng | Sửa từng ô ngày trên bảng lịch tháng. | Cao |
| M02-F04 | Đăng ký đổi ngày trực | Nhân viên gửi yêu cầu đổi; quản lý duyệt/từ chối. | Trung bình |
| M02-F05 | Thống kê số ngày trực | Báo cáo tổng số ngày trực của từng nhân sự. | Trung bình |
| M02-F06 | Tự động tính ngày nghỉ bù | Tự tính ngày nghỉ bù theo quy định. | Cao |
| M02-F07 | Cảnh báo lịch trùng ngày nghỉ bù | Phát hiện lịch khác xếp trùng ngày nghỉ bù. | Cao |

### Module M03 — Lịch thông tầm

| Mã | Chức năng | Mô tả | Ưu tiên |
|----|-----------|-------|---------|
| M03-F01 | Tạo lịch thông tầm | Chọn nhân sự và ngày làm thông tầm. | Cao |
| M03-F02 | Kiểm tra xung đột lịch trực 24/24 | Ngăn lưu nếu nhân sự đã có lịch trực 24/24 cùng ngày. | Cao |
| M03-F03 | Sửa / huỷ lịch thông tầm | Chỉnh sửa hoặc huỷ lịch; ghi nhật ký. | Cao |
| M03-F04 | Xem lịch theo tuần / tháng | Hiển thị dạng bảng lịch; bộ lọc theo nhân sự. | Trung bình |

### Module M04 — Lịch phòng khám dịch vụ

| Mã | Chức năng | Mô tả | Ưu tiên |
|----|-----------|-------|---------|
| M04-F01 | Tạo lịch phòng khám dịch vụ | Chọn nhân sự và ngày phụ trách. | Cao |
| M04-F02 | Kiểm tra xung đột lịch chuyên gia | Ngăn lưu nếu nhân sự đã có lịch chuyên gia cùng ngày. | Cao |
| M04-F03 | Sửa / huỷ lịch dịch vụ | Chỉnh sửa hoặc huỷ lịch. | Cao |
| M04-F04 | Xem lịch theo tuần / tháng | Hiển thị bảng lịch; bộ lọc theo nhân sự. | Trung bình |
| M04-F05 | Thống kê ca khám dịch vụ | Báo cáo số ngày theo tuần/tháng. | Thấp |

### Module M05 — Lịch phòng khám chuyên gia

| Mã | Chức năng | Mô tả | Ưu tiên |
|----|-----------|-------|---------|
| M05-F01 | Tạo lịch phòng khám chuyên gia | Chọn chuyên gia và ngày phụ trách. | Cao |
| M05-F02 | Kiểm tra xung đột lịch dịch vụ | Ngăn lưu nếu đã có lịch dịch vụ cùng ngày. | Cao |
| M05-F03 | Sửa / huỷ lịch chuyên gia | Chỉnh sửa hoặc huỷ lịch. | Cao |
| M05-F04 | Lọc lịch theo chuyên khoa | Xem lịch theo Ngoại, Nội, Sản, Nhi, Mắt, Răng... | Trung bình |
| M05-F05 | Thống kê ca khám chuyên gia | Báo cáo số ngày theo tuần/tháng. | Thấp |

### Module M06 — Tổng hợp & Hiển thị lịch

| Mã | Chức năng | Mô tả | Ưu tiên |
|----|-----------|-------|---------|
| M06-F01 | Xem lịch theo ngày / tuần / tháng | Lưới lịch 4 loại với màu phân biệt. | Cao |
| M06-F02 | Xem lịch theo nhân sự | Xem toàn bộ lịch của 1 người. | Cao |
| M06-F03 | Cảnh báo xung đột thời gian thực | Thông báo tức thời khi phát hiện vi phạm. | Cao |
| M06-F04 | Xuất báo cáo lịch | Xuất Excel / PDF theo tháng hoặc loại lịch. | Trung bình |
| M06-F05 | Nhật ký thao tác | Ghi lại toàn bộ hành động. | Thấp |

### Module M07 — Tự động sắp xếp lịch

| Mã | Chức năng | Mô tả | Ưu tiên |
|----|-----------|-------|---------|
| M07-F01 | Cấu hình tham số đầu vào | Chọn tháng, danh sách nhân sự ngoại lệ. | Cao |
| M07-F02 | Tự động xếp lịch trực 24/24 | Tự chọn ngày và phân công. | Cao |
| M07-F03 | Tự động xếp lịch thông tầm | Tự chọn ngày và phân công. | Cao |
| M07-F04 | Tự động xếp lịch phòng khám dịch vụ | Tự phân công nhân sự. | Cao |
| M07-F05 | Tự động xếp lịch phòng khám chuyên gia | Tự gán chuyên gia phù hợp. | Cao |
| M07-F06 | Báo cáo ngày chưa phân công được | Liệt kê ngày chưa đủ nhân sự. | Cao |
| M07-F07 | Xem trước lịch trước khi xác nhận | Hiển thị bản nháp để chỉnh sửa. | Cao |
| M07-F08 | Sắp xếp lại khi có thay đổi đột xuất | Tự đề xuất người thay thế hợp lệ. | Trung bình |
| M07-F09 | Thống kê cân bằng tải | Biểu đồ số ngày trực của từng nhân sự. | Trung bình |
| M07-F10 | Lưu & tái sử dụng mẫu lịch | Lưu cấu hình thành template. | Thấp |

### Gợi ý thuật toán

- **Round Robin**: Luân phiên xoay vòng theo thứ tự danh sách nhân sự.
- **Greedy**: Mỗi ngày chọn nhân sự có ít ngày công nhất và không vi phạm ràng buộc.
- **Backtracking**: Thử từng phương án, quay lui nếu vi phạm ràng buộc.

---

## 3. Database Schema

Database file: `hospital_scheduler_business_final.sql`

### 3.1 Tổng quan các bảng

| Bảng | Mục đích | Mapping Module |
|------|----------|----------------|
| `specialty` | Chuyên môn (Bác sĩ, Điều dưỡng...) | M01 |
| `staff` | Nhân sự (20 người) | M01 |
| `app_role` | Vai trò (ADMIN, MANAGER, STAFF) | M01-F05 |
| `app_permission` | Quyền chi tiết | M01-F05 |
| `role_permission` | Mapping role ↔ permission | M01-F05 |
| `staff_role` | Mapping staff ↔ role | M01-F05 |
| `shift_type` | Loại ca (L01, L02, L03, L04) | M02-M05 |
| `schedule_period` | Kỳ lập lịch (DRAFT→PUBLISHED→ARCHIVED) | M02-M07 |
| `shift_requirement` | Nhu cầu nhân sự cho từng ngày/ca | M07 |
| `leave_request` | Xin nghỉ phép | M01 |
| `schedule` | Lịch phân công thực tế | M02-M05 |
| `compensation_day` | Ngày nghỉ bù sau L01 | M02 |
| `schedule_exchange` | Đổi ca giữa nhân sự | M02-F04 |
| `algorithm_config` | Cấu hình thuật toán | M07 |
| `algorithm_metrics` | Kết quả chạy thuật toán | M07 |
| `schedule_conflict` | Chi tiết xung đột lịch | M02-M05 |
| `notification` | Thông báo cho nhân sự | M06 |
| `audit_history` | Lịch sử thay đổi dữ liệu | M01, M06-F05 |
| `system_log` | Log hành động hệ thống | M06-F05 |
| `file_attachment` | File đính kèm | M01, M02 |

### 3.2 Mapping Shift Type (Loại ca)

| shift_type_id | Tên | is_overnight | Mapping |
|---------------|-----|--------------|---------|
| L01 | Lịch trực 24/24 | TRUE | M02 |
| L02 | Lịch thông tầm | FALSE | M03 |
| L03 | Lịch phòng khám dịch vụ | FALSE | M04 |
| L04 | Lịch phòng khám chuyên gia | FALSE | M05 |

### 3.3 Ràng buộc Database quan trọng

1. **UNIQUE**: `schedule(period_id, staff_id, shift_type_id, work_date)` - Mỗi nhân sự chỉ 1 lịch/ngày/loại
2. **UNIQUE**: `compensation_day(staff_id, compensation_date)` - 1 ngày nghỉ bù chỉ cho 1 nhân sự
3. **Composite FK**: `compensation_day` references `schedule` để đảm bảo consistency
4. **schedule_period.status**: DRAFT → PUBLISHED → ARCHIVED (workflow bắt buộc)

### 3.4 Conflict Types trong schedule_conflict

| conflict_type | Mô tả |
|---------------|-------|
| LEAVE_CONFLICT | Trùng ngày nghỉ phép |
| MAX_SHIFT_EXCEEDED | Vượt số ca tối đa/tháng |
| BACK_TO_BACK_SHIFT | Ca liên tiếp không nghỉ |
| SPECIALTY_MISMATCH | Chuyên môn không phù hợp |
| REQUIREMENT_NOT_MET | Không đủ nhân sự |
| DUPLICATE_ASSIGNMENT | Trùng phân công |
| COMPENSATION_CONFLICT | Trùng ngày nghỉ bù |
| OTHER | Khác |

---

## 4. Lưu ý quan trọng

1. **Logic kiểm tra ràng buộc phải được tách thành hàm/service dùng chung** cho toàn hệ thống (sử dụng cho cả thủ công và tự động).

2. **Tất cả ngày nghỉ bù** phải được hiển thị và khoá trên bảng lịch tháng.

3. **Ngày nghỉ bù** được kiểm tra trong bước kiểm tra xung đột hàng loạt của tất cả 3 module M03, M04, M05.

---

## Tài liệu gốc

Chi tiết luồng xử lý từng bước, xem file: `QuanLyLichCongTac_v5.txt`
