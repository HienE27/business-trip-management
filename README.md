# Hệ thống Quản lý Lịch Công Tác — MedSchedule Pro

**Nhóm 4 DACN** | Giảng viên: ThS. Văn Minh Hoàng Quân

Website quản lý lịch công tác cho phòng chuyên môn với 4 loại lịch (`L01`-`L04`), backend Spring Boot + MySQL và frontend Next.js.

---

## Mục lục

- [Tổng quan](#tổng-quan)
- [Nghiệp vụ cốt lõi](#nghiệp-vụ-cốt-lõi)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Cách chạy local](#cách-chạy-local)
- [Tài khoản mặc định](#tài-khoản-mặc-định)
- [Dữ liệu mẫu được seed](#dữ-liệu-mẫu-được-seed)
- [Màn hình frontend](#màn-hình-frontend)
- [API chính](#api-chính)
- [Module M07 — Auto Scheduling](#module-m07--auto-scheduling)
- [Test](#test)
- [Tài liệu liên quan](#tài-liệu-liên-quan)
- [Changelog](#changelog)

---

## Tổng quan

| Thành phần | Công nghệ |
|---|---|
| **Backend** | Spring Boot `3.5.5`, Java `17`, Spring Security, JPA, MySQL |
| **Frontend** | Next.js `16.2.6`, React `19`, TypeScript, Tailwind CSS `4` |
| **Database** | MySQL `8.x` (schema `hospital_scheduler`) |
| **API base path** | `/api/v1` |
| **Swagger UI** | `http://localhost:8080/swagger-ui.html` |
| **Package manager** | pnpm (frontend) |

---

## Nghiệp vụ cốt lõi

### 4 loại lịch

| ID | Tên | Mô tả | Overnight |
|---|---|---|---|
| `L01` | Lịch trực `24/24` | 7h30 ngày N → 7h30 ngày N+1, có nghỉ bù | ✅ |
| `L02` | Lịch thông tầm | Ca ngày, không nghỉ trưa | ❌ |
| `L03` | Lịch phòng khám dịch vụ | Ca khám dịch vụ | ❌ |
| `L04` | Lịch phòng khám chuyên gia | Ca khám chuyên sâu | ❌ |

### Ràng buộc (CRITICAL)

```java
// 1. L01 vs L02: Cùng nhân sự, cùng ngày → KHÔNG ĐƯỢC
// 2. L03 vs L04: Cùng nhân sự, cùng ngày → KHÔNG ĐƯỢC
// 3. Compensation Day: Ngày nghỉ bù → KHÔNG ĐƯỢC xếp bất kỳ lịch nào
```

Backend kiểm soát qua `ConflictDetectionService` với 8 loại conflict:
`DUPLICATE_SHIFT`, `L01_L02_CONFLICT`, `L03_L04_CONFLICT`, `COMPENSATION_CONFLICT`, `MAX_SHIFTS_PER_MONTH`, `BACK_TO_BACK_SHIFT`, `INVALID_SHIFT_TYPE`, `UNAUTHORIZED_SHIFT`

### Quy tắc nghỉ bù

| Trực ngày | Nghỉ bù |
|---|---|
| Thứ 2 (Monday) | Thứ 3 (tuần này) |
| Thứ 3 (Tuesday) | Thứ 4 (tuần này) |
| Thứ 4 (Wednesday) | Thứ 5 (tuần này) |
| Thứ 5 (Thursday) | Thứ 6 (tuần này) |
| Thứ 6 (Friday) | **Thứ 3 tuần sau** (bỏ T2, T6) |
| Thứ 7 (Saturday) | **Thứ 3 tuần sau** (bỏ T2, T6) |
| Chủ Nhật (Sunday) | **Thứ 2 tuần sau** |

---

## Cấu trúc thư mục

```
business-trip-management/
├── backend/                    # Spring Boot API
│   └── src/
│       ├── main/java/com/hospital/scheduler/
│       │   ├── config/         # Security, OpenAPI
│       │   ├── controller/     # REST Controllers
│       │   ├── dto/            # Request/Response DTOs
│       │   ├── entity/         # JPA Entities
│       │   ├── exception/      # Custom exceptions
│       │   ├── repository/     # JPA Repositories
│       │   ├── service/        # Business logic
│       │   └── util/           # Utilities
│       └── test/               # Backend tests
├── frontend/                   # Next.js app
│   └── src/
│       ├── app/                # Next.js App Router pages
│       ├── components/         # React components
│       ├── hooks/              # Custom React hooks
│       ├── lib/                # Utilities, API clients
│       └── types/              # TypeScript types
├── SPEC.md                     # Tài liệu nghiệp vụ
├── QuanLyLichCongTac_v5.md     # Mô tả chức năng gốc
└── README.md                   # (file này)
```

---

## Cách chạy local

### 1. Chuẩn bị

Cần cài sẵn:

- Java `17`
- Maven `3.9+`
- Node.js `20+`
- `pnpm`
- MySQL `8.x`

### 2. Tạo database

```sql
CREATE DATABASE hospital_scheduler CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. Cấu hình backend

File cấu hình: `backend/src/main/resources/application.properties`

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/hospital_scheduler
spring.datasource.username=root
spring.datasource.password=123456
server.port=8080
```

### 4. Chạy backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend chạy tại `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`

### 5. Chạy frontend

```bash
cd frontend
pnpm install
pnpm dev
```

Frontend chạy tại `http://localhost:3000`.

**Lưu ý:** Frontend mặc định gọi API tại `http://localhost:8080/api/v1`. Nếu cần thay đổi:

```bash
# Tạo file .env.local trong frontend/
echo "NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1" > frontend/.env.local
```

---

## Tài khoản mặc định

`DataSeeder` seed dữ liệu khi database còn trống. **20 nhân sự**: 1 admin, 2 manager, 17 staff.

| Username | Password | Role | Họ tên |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | Nguyễn Văn An |
| `manager1` | `123456` | MANAGER | Trần Thị Bình |
| `manager2` | `123456` | MANAGER | Lê Hoàng Cường |
| `nvminh` | `123456` | STAFF | Nguyễn Văn Minh |
| `tthuhien` | `123456` | STAFF | Trần Thu Hiền |
| *(+ 15 staff khác)* | `123456` | STAFF | … |

---

## Dữ liệu mẫu được seed

Khi database rỗng, hệ thống tự tạo:

- **4 shift types**: `L01`, `L02`, `L03`, `L04`
- **4 nhóm chuyên môn** mẫu
- **1 kỳ PUBLISHED**: `Kỳ tháng 06/2026`
- **1 kỳ DRAFT**: `Kỳ tháng 07/2026`
- **Shift requirements** cho các loại lịch
- **Lịch mẫu** bao gồm cả dữ liệu có conflict để test

---

## Màn hình frontend

### Dashboard & Lịch

| Route | Mô tả |
|-------|-------|
| `/login` | Đăng nhập (hỗ trợ demo quick-login) |
| `/dashboard` | Dashboard tổng quan + ma trận lịch |
| `/monthly-schedule` | Bảng lịch tháng + conflicts + coverage |
| `/staff` | Danh sách nhân sự |
| `/staff/create` | Tạo nhân sự mới |
| `/staff/[id]` | Chi tiết hồ sơ nhân sự |
| `/staff/profile` | Hồ sơ cá nhân đang đăng nhập |

### Các module lịch (M02-M05)

| Route | Mô tả |
|-------|-------|
| `/duty-24` | Lịch trực `L01` |
| `/all-day` | Lịch thông tầm `L02` |
| `/service-clinic` | Lịch phòng khám dịch vụ `L03` |
| `/expert-clinic` | Lịch phòng khám chuyên gia `L04` |

### Workflow & Phê duyệt

| Route | Mô tả |
|-------|-------|
| `/swap-requests` | Yêu cầu đổi ca |
| `/leave-requests` | Đơn nghỉ phép |

### Báo cáo & Thống kê

| Route | Mô tả |
|-------|-------|
| `/reports` | Trang tổng hợp báo cáo |
| `/reports/staff` | Báo cáo theo nhân sự |
| `/reports/monthly` | Báo cáo theo tháng |
| `/reports/statistics` | Thống kê |
| `/reports/conflicts` | Báo cáo xung đột |

### Auto Scheduling (M07)

| Route | Mô tả |
|-------|-------|
| `/auto-scheduling` | Giao diện xếp lịch tự động |
| `/auto-scheduling/algorithm-config` | Cấu hình thuật toán (17 params) |
| `/auto-scheduling/history` | Lịch sử chạy auto scheduling |

### Quản lý hệ thống

| Route | Mô tả |
|-------|-------|
| `/periods` | Quản lý kỳ lịch (CRUD + publish/archive) |
| `/holidays` | Quản lý ngày lễ + ngày nghỉ bù |
| `/compensation-days` | Danh sách ngày nghỉ bù |
| `/notifications` | Thông báo |
| `/audit-history` | Nhật ký thao tác |
| `/settings` | Cài đặt hệ thống |
| `/settings/roles` | Ma trận phân quyền (ADMIN only) |

---

## API chính

### Auth

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| `POST` | `/api/v1/auth/login` | Đăng nhập |
| `POST` | `/api/v1/auth/logout` | Đăng xuất |

JWT token lưu trong cookie HTTP-only tên `medschedule_access_token`.

### Schedule

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| `GET` | `/api/v1/schedules/period/{periodId}` | Lấy lịch theo kỳ |
| `GET` | `/api/v1/schedules/period/{periodId}/date/{date}` | Lấy lịch theo ngày |
| `GET` | `/api/v1/schedules/staff/{staffId}` | Lấy lịch theo nhân sự |
| `GET` | `/api/v1/schedules/conflicts/check/{periodId}` | Kiểm tra xung đột |
| `POST` | `/api/v1/schedules` | Tạo lịch mới |
| `PUT` | `/api/v1/schedules/{id}` | Cập nhật lịch |
| `DELETE` | `/api/v1/schedules/{id}` | Xóa lịch |
| `GET` | `/api/v1/schedules/replacements/{periodId}` | Danh sách thay thế |

### Period

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| `GET` | `/api/v1/periods` | Lấy tất cả kỳ lịch |
| `GET` | `/api/v1/periods/{id}` | Lấy kỳ lịch theo ID |
| `POST` | `/api/v1/periods` | Tạo kỳ lịch mới |
| `PUT` | `/api/v1/periods/{id}` | Cập nhật kỳ lịch |
| `POST` | `/api/v1/periods/{id}/publish` | Công bố kỳ lịch |
| `POST` | `/api/v1/periods/{id}/archive` | Lưu trữ kỳ lịch |

### Dashboard & Export

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| `GET` | `/api/v1/dashboard` | Dashboard data |
| `GET` | `/api/v1/dashboard/shifts` | Lịch trực dashboard |
| `GET` | `/api/v1/dashboard/periods` | Danh sách kỳ |
| `GET` | `/api/v1/dashboard/workload/period/{periodId}` | Workload theo kỳ |
| `GET` | `/api/v1/dashboard/export/schedule/{periodId}` | Export Excel lịch |
| `GET` | `/api/v1/dashboard/export/schedule/{periodId}/pdf` | Export PDF lịch |
| `GET` | `/api/v1/dashboard/export/workload/{periodId}` | Export Excel workload |

### Auto Scheduling (M07)

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| `POST` | `/api/v1/auto-scheduling/preview` | Xem trước lịch |
| `POST` | `/api/v1/auto-scheduling/apply` | Áp dụng lịch |
| `GET` | `/api/v1/auto-scheduling/templates` | Liệt kê templates |
| `GET` | `/api/v1/auto-scheduling/templates/{id}` | Chi tiết template |
| `POST` | `/api/v1/auto-scheduling/templates` | Tạo template |
| `POST` | `/api/v1/auto-scheduling/apply-template` | Áp dụng template |
| `GET` | `/api/v1/auto-scheduling/unassigned/{periodId}` | Ngày chưa đủ nhân sự |
| `GET` | `/api/v1/auto-scheduling/suggest-replacements/{scheduleId}` | Đề xuất thay thế |
| `GET` | `/api/v1/algorithm-config` | Lấy cấu hình thuật toán |
| `PUT` | `/api/v1/algorithm-config` | Cập nhật cấu hình |
| `GET` | `/api/v1/algorithm-config/runtime` | Runtime config |
| `PUT` | `/api/v1/algorithm-config/runtime` | Cập nhật runtime config |

### Roles & Permissions (M01-F05)

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| `GET` | `/api/v1/roles/permissions/matrix` | Ma trận quyền (ADMIN) |
| `POST` | `/api/v1/roles/permissions/toggle` | Toggle permission |

### Staff & Exchange

| Method | Endpoint | Mô tả |
|--------|----------|--------|
| `GET` | `/api/v1/staff` | Danh sách nhân sự |
| `GET` | `/api/v1/staff/active` | Nhân sự đang hoạt động |
| `GET` | `/api/v1/staff/me` | Thông tin user hiện tại |
| `POST` | `/api/v1/staff/import` | Import nhân sự (Excel) |
| `GET` | `/api/v1/schedule-exchanges` | Danh sách đổi ca |
| `GET` | `/api/v1/schedule-exchanges/pending` | Đổi ca chờ duyệt |
| `POST` | `/api/v1/schedule-exchanges/requester/{requesterId}` | Tạo yêu cầu đổi ca |
| `PUT` | `/api/v1/schedule-exchanges/{id}/approve` | Duyệt đổi ca |
| `PUT` | `/api/v1/schedule-exchanges/{id}/reject` | Từ chối đổi ca |

---

## Module M07 — Auto Scheduling

### 3 thuật toán

| Thuật toán | Mô tả |
|------------|--------|
| `GREEDY` | Mỗi ngày chọn nhân sự có ít ngày công nhất |
| `ROUND_ROBIN` | Luân phiên xoay vòng theo thứ tự danh sách |
| `BACKTRACKING` | Thử từng phương án, quay lui nếu vi phạm ràng buộc |

### Cấu hình thuật toán (17 params)

**Auto-generation (10 params)** — tự tạo `shift_requirement` khi mở kỳ lịch mới:

| param_key | Mô tả | Mặc định |
|-----------|--------|-----------|
| `auto_gen_enabled` | Bật/tắt auto-gen | `true` |
| `auto_gen_l01_per_day` | Số nhân sự L01/ngày | `2` |
| `auto_gen_l02_per_day` | Số nhân sự L02/ngày | `2` |
| `auto_gen_l03_per_day` | Số nhân sự L03/ngày | `2` |
| `auto_gen_l04_per_day` | Số nhân sự L04/ngày | `2` |
| `auto_gen_l01_per_week` | Số L01 tối thiểu/tuần/người | `1` |
| `auto_gen_l02_per_week` | Số L02 tối thiểu/tuần/người | `3` |
| `auto_gen_l03_per_week` | Số L03 tối thiểu/tuần/người | `2` |
| `auto_gen_l04_per_week` | Số L04 tối thiểu/tuần/người | `1` |
| `auto_gen_holiday_mode` | Xử lý ngày lễ | `SKIP` |

**Runtime algorithm (7 params)** — ảnh hưởng cách thuật toán chạy:

| param_key | Mô tả | Mặc định |
|-----------|--------|-----------|
| `max_iterations` | Số vòng lặp tối đa backtracking | `1000` |
| `weekend_weight` | Hệ số phạt cuối tuần | `2` |
| `overnight_recovery_hours` | Khoảng cách nghỉ L01-L01 | `24` |
| `greedy_coverage_threshold` | Ngưỡng phủ lịch để Greedy dừng sớm | `0.85` |
| `balance_score_min` | Ngưỡng cân bằng tải | `0.70` |
| `backtrack_time_limit_seconds` | Timeout backtracking (giây) | `60` |

Cấu hình tại `/auto-scheduling/algorithm-config`.

---

## Test

### Backend tests

```bash
cd backend
./mvnw test
```

**Các test chính:**
- `ConflictDetectionServiceTest` — 8 loại conflict
- `ScheduleServiceBusinessRulesTest` — Business rules
- `AutoSchedulingServiceTest` — Thuật toán auto scheduling
- `LeaveRequestServiceTest` — Đơn nghỉ phép
- `RoleServiceTest` — Phân quyền

### Frontend tests

```bash
cd frontend
pnpm test          # Unit tests (vitest)
pnpm test:e2e     # E2E tests (Playwright)
```

**Lưu ý:** E2E tests cần backend chạy trên port `8080` và frontend trên port `3000`.

---

## Tài liệu liên quan

| File | Mô tả |
|------|--------|
| `SPEC.md` | Scope nghiệp vụ và constraints |
| `QuanLyLichCongTac_v5.md` | Mô tả chức năng chi tiết |
| `DEMO_WALKTHROUGH.md` | Kịch bản demo đầy đủ |
| `backend/HELP.md` | Hướng dẫn backend |
| `frontend/README.md` | Hướng dẫn frontend |
| `screenshots/` | Ảnh chụp màn hình phục vụ demo |

---

## Changelog

### v1.1 (08/2026)

**Bug Fixes:**
- Fix API endpoint mismatch: `/schedule-periods` → `/periods`
- Fix null specialty reference trong requirements page
- Fix scroll-behavior warning trong browser console
- Fix oversize notification message (>1000 chars)
- Fix WorkflowStepper visual redesign

**Features:**
- WorkflowStepper 6 bước cho M02-M05
- ConflictSection và ExportReportPanel đồng bộ
- Preview + manual edit + undo cho Auto Scheduling
- GENERATED/PATTERN template support
- CoverageInspector full-coverage state redesign (check_circle icon)

### v1.0 (05/2026)

- Initial release với 4 loại lịch (L01-L04)
- Backend Spring Boot + MySQL
- Frontend Next.js
