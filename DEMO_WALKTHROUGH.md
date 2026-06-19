# Demo Walkthrough — Hospital Scheduler

> Script minh họa toàn bộ luồng nghiệp vụ cho buổi bảo vệ / demo.

**Thời gian ước tính:** 15-20 phút
**Người demo:** Quản lý (MANAGER) hoặc Quản trị viên (ADMIN)

---

## Phần 0: Chuẩn bị (2 phút)

### 0.1 Thông tin đăng nhập (từ seed data)

| Vai trò | Username | Mật khẩu | Ghi chú |
|--------|----------|----------|---------|
| ADMIN | `admin` | `admin123` | Toàn quyền |
| MANAGER | `manager01` | `password123` | Xếp lịch, duyệt đổi ca |
| STAFF | `staff01` | `password123` | Xem lịch cá nhân, gửi đổi ca |

### 0.2 URL truy cập

- **Frontend:** `http://localhost:3000`
- **Swagger API:** `http://localhost:8080/swagger-ui.html`
- **Swagger JSON:** `http://localhost:8080/v3/api-docs`

### 0.3 Khởi động (nếu chạy local)

```bash
# Backend
cd backend
./mvnw spring-boot:run

# Frontend (terminal khác)
cd frontend
pnpm dev

# Hoặc dùng Docker
docker compose up -d
```

---

## Phần 1: Đăng nhập & Tổng quan Dashboard (2 phút)

### 1.1 Đăng nhập với MANAGER

1. Mở trình duyệt → `http://localhost:3000`
2. Nhập username: `manager01`, password: `password123`
3. Nhấn **Đăng nhập**
4. → Chuyển hướng đến Dashboard

### 1.2 Dashboard Overview

**Điểm trình diễn:**
- KPI cards: Tổng nhân sự, ca trực hôm nay, yêu cầu chờ duyệt
- Lịch trực hôm nay (nếu có)
- Thống kê số ca L01-L04
- Notification bell (top-right) — click xem thông báo mới

**Chuyển sang giao diện tiếng Việt / English toggle (Settings)**

---

## Phần 2: Quản lý Nhân sự — M01 (2 phút)

### 2.1 Xem danh sách nhân sự

1. Sidebar → **Nhân sự**
2. → Bảng 20 nhân sự với: Họ tên, Chuyên khoa, Số điện thoại, Email, Trạng thái, Vai trò
3. Filter: Tìm theo tên, lọc theo chuyên khoa, vai trò, trạng thái

### 2.2 Tạo nhân sự mới

1. Nhấn **+ Thêm nhân sự**
2. Điền form: Họ tên, username, email, số điện thoại, chuyên khoa, vai trò
3. Nhấn **Lưu** → Toast thông báo thành công

### 2.3 Ma trận phân quyền

1. Sidebar → **Cài đặt** → **Phân quyền hệ thống**
2. → Bảng toggle: ADMIN / MANAGER / STAFF × quyền
3. Bật/tắt toggle → Lưu → Cập nhật ngay lập tức

---

## Phần 3: Xếp lịch — M02-M05 (6 phút)

### 3.1 Tổng hợp lịch tháng (Monthly Schedule)

1. Sidebar → **Lịch công tác** (biểu tượng calendar_month)
2. **Tab selector:** Lịch trực 24/24 | Thông tầm | Phòng khám dịch vụ | Phòng khám chuyên gia
3. **View toggle:** Lịch (calendar) | Bảng (table)
4. **Kỳ lịch selector:** Chọn kỳ tháng cần xem
5. Click vào ngày → Quick Add Modal mở sẵn ngày

### 3.2 Thêm ca trực nhanh (Quick Add)

1. Click **+** ở góc phải-trên calendar
2. Điền:
   - **Nhân sự:** Chọn từ dropdown
   - **Loại lịch:** Lịch trực 24/24
   - **Ngày:** 2026-06-22
3. Nhấn **Lưu**
4. → Toast "Tạo lịch thành công"
5. → Lịch xuất hiện trên calendar
6. → **Compensation Day tự động được tạo** (thứ 3 tuần sau)

### 3.3 Inline Quick-Edit (tính năng mới)

1. Click vào ngày có nhiều ca trực → **OverflowPopover** mở ra
2. Hover dòng → icon **edit** (bút chì)
3. Click **edit** → Thay đổi trực tiếp **Nhân sự** và **Loại lịch** trong popover
4. Nhấn **check** → Lưu ngay, calendar tự cập nhật
5. Không cần mở modal riêng

### 3.4 Chi tiết ca trực + Conflict Detection

1. Click vào 1 lịch trên calendar → **ShiftDetailModal** mở
2. Xem: Nhân sự, loại lịch, ngày, ghi chú, trạng thái xung đột
3. **Chỉnh sửa:** Đổi nhân sự / loại lịch → Lưu
4. Nếu vi phạm quy tắc (L01 + L02 cùng ngày) → **Cảnh báo xung đột** hiển thị ngay

### 3.5 Bulk Publish (tính năng mới)

1. Auto-Scheduling page → Nhấn **"Công bố hàng loạt"**
2. Checkbox chọn nhiều kỳ lịch DRAFT
3. Nhấn **Công bố** → Xem kết quả: bao nhiêu thành công, bao nhiêu thất bại (kỳ có xung đột)
4. → Tất cả staff trong kỳ nhận **notification + email**

---

## Phần 4: Auto Scheduling — M07 (4 phút)

### 4.1 Chạy thuật toán tự động

1. Sidebar → **Tự động xếp lịch**
2. Chọn **Kỳ lịch** (DRAFT)
3. Chọn **Thuật toán:**
   - Tham lam (GREEDY) — Nhanh
   - Luân phiên (ROUND_ROBIN) — Cân bằng
   - Backtracking — Tối ưu nhưng chậm
4. Nhấn **Chạy Auto Schedule**
5. → Preview hiển thị: Danh sách ca được gợi ý, biểu đồ phân bổ tải, báo cáo ca chưa xếp

### 4.2 Chỉnh sửa Preview

- **Đổi nhân sự:** Click vào staff → Chọn người khác
- **Xóa ca:** Nhấn **×** trên dòng
- **Thêm ca:** Nhấn **+ Thêm ca**

### 4.3 Áp dụng

1. Nhấn **Áp dụng lịch**
2. Confirm → Ca trực được tạo hàng loạt
3. Quay lại Monthly Schedule → Kiểm tra

### 4.4 Lưu thành Template

1. Sau khi chạy thuật toán → Nhấn **Lưu thành mẫu**
2. Đặt tên: "Mẫu L01 Tháng 6"
3. Nhấn **Lưu** → Template được lưu

### 4.5 Áp dụng Template

1. Chọn kỳ mới → **Mẫu lịch** → Chọn template
2. Xem preview → **Áp dụng**
3. → Lịch từ template được tạo cho kỳ mới

---

## Phần 5: Đổi ca — Swap Requests (2 phút)

### 5.1 Staff gửi yêu cầu đổi ca

1. **Đăng nhập với STAFF** (`staff01`)
2. Xem lịch cá nhân
3. Click vào ca muốn đổi → ShiftDetailModal
4. Nhấn **Yêu cầu đổi ca**
5. Chọn nhân sự muốn đổi → Gửi

### 5.2 Manager duyệt đổi ca

1. **Đăng nhập với MANAGER** (`manager01`)
2. Sidebar → **Đổi trực** → Danh sách yêu cầu chờ duyệt
3. Xem chi tiết: Ai muốn đổi, đổi với ai, ngày nào
4. **Duyệt** hoặc **Từ chối** (kèm lý do)
5. → Cả hai staff nhận notification

---

## Phần 6: Báo cáo & Thống kê — M06 (2 phút)

### 6.1 Báo cáo xung đột

1. Sidebar → **Báo cáo** → **Xung đột lịch**
2. → Danh sách xung đột: Nhân sự, ngày, loại xung đột
3. Click → Mở ConflictResolutionModal → Chọn giải pháp

### 6.2 Báo cáo tháng

1. Sidebar → **Báo cáo** → **Thống kê tháng**
2. Bộ lọc: Theo kỳ, theo loại lịch, theo nhân sự
3. → Biểu đồ cột: Số ca L01-L04 theo ngày
4. → Biểu đồ tròn: Phân bổ theo chuyên khoa
5. **Xuất Excel** — Tải file `.xlsx` đầy đủ

---

## Phần 7: Cài đặt & Thông báo (1 phút)

### 7.1 Cấu hình Email

1. Sidebar → **Cài đặt** → **Thông báo Email**
2. Toggle **Bật thông báo Email**
3. Toggle **Thông báo xung đột lịch**
4. Nhấn **Lưu cấu hình**

### 7.2 Lịch sử thao tác (Audit History)

1. Sidebar → **Nhật ký thao tác**
2. → Bảng: Ai làm gì, lúc nào, resource nào
3. Filter: Theo user, action type, ngày

---

## Kết thúc Demo

- **Điểm nổi bật đã show:**
  - Quy tắc nghiệp vụ L01-L04 tự động áp dụng
  - Compensation Day tự động tính
  - Conflict Detection real-time
  - Inline Quick-Edit không cần modal
  - Bulk Publish hàng loạt kỳ lịch
  - Auto Scheduling với preview + chỉnh sửa
  - Template reuse cho kỳ mới
  - Email alert khi publish / có xung đột

---

## Troubleshooting

| Vấn đề | Giải pháp |
|--------|-----------|
| Backend không khởi động | Kiểm tra MySQL đang chạy port 3306 |
| Frontend lỗi build | `cd frontend && pnpm install && pnpm build` |
| Login lỗi 401 | Verify JWT secret trong `application.properties` |
| Email không gửi được | Bật `APP_EMAIL_ENABLED=true` trong `.env` |
| Xung đột không hiển thị | Kiểm tra `ConflictDetectionService` logs |

---

## Thông tin kỹ thuật

| Thành phần | Công nghệ |
|-----------|-----------|
| Backend | Java 17, Spring Boot 4.0.6, JPA/Hibernate, Spring Security + JWT |
| Frontend | Next.js 16, React 19, Tailwind CSS 4, pnpm |
| Database | MySQL 8.0 (utf8mb4) |
| API Docs | SpringDoc OpenAPI 3 (`/swagger-ui.html`) |
| Container | Docker + Docker Compose |
| CI/CD | GitHub Actions |
