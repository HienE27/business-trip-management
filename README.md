# Hệ thống Quản lý Lịch Công Tác

Website quản lý lịch công tác cho phòng chuyên môn với 4 loại lịch (`L01`-`L04`), backend Spring Boot + MySQL và frontend Next.js.

## Tổng quan

- **Backend**: Spring Boot `4.0.6`, Java `17`, Spring Security, JPA, MySQL, SpringDoc OpenAPI
- **Frontend**: Next.js `16.2.6`, React `19`, TypeScript, Tailwind CSS `4`
- **Database**: MySQL schema `hospital_scheduler`
- **API base path**: `/api/v1`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

## Nghiệp vụ cốt lõi

Hệ thống quản lý 4 loại lịch:

- `L01` — Lịch trực `24/24`
- `L02` — Lịch thông tầm
- `L03` — Lịch phòng khám dịch vụ
- `L04` — Lịch phòng khám chuyên gia

Các ràng buộc đang được backend kiểm soát qua `ConflictDetectionService`:

- Cùng nhân sự, cùng ngày: `L01` và `L02` không được đồng thời tồn tại
- Cùng nhân sự, cùng ngày: `L03` và `L04` không được đồng thời tồn tại
- Ngày nghỉ bù sau `L01` không được xếp bất kỳ lịch nào khác

Quy tắc nghỉ bù hiện bám theo tài liệu nghiệp vụ:

- Trực `Thứ 2` → nghỉ bù `Thứ 3`
- Trực `Thứ 3` → nghỉ bù `Thứ 4`
- Trực `Thứ 4` → nghỉ bù `Thứ 5`
- Trực `Thứ 5` → nghỉ bù `Thứ 6`
- Trực `Thứ 6` hoặc `Thứ 7` → nghỉ bù `Thứ 3 tuần sau`
- Trực `Chủ Nhật` → nghỉ bù `Thứ 2 tuần sau`

## Cấu trúc thư mục

- `backend/` — API, business rules, seed data, export Excel/PDF
- `frontend/` — giao diện quản trị và dashboard
- `SPEC.md` — tài liệu nghiệp vụ và phạm vi hệ thống
- `QuanLyLichCongTac_v5.md` — mô tả chức năng gốc theo hướng product/spec

## Cách chạy local

### 1. Chuẩn bị

Cần cài sẵn:

- Java `17`
- Maven wrapper hoặc Maven compatible với Spring Boot `4`
- Node.js mới đủ chạy Next.js `16`
- `pnpm`
- MySQL `8.x`

Tạo database:

```sql
CREATE DATABASE hospital_scheduler CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Cấu hình mặc định hiện nằm trong `backend/src/main/resources/application.properties`:

- DB URL: `jdbc:mysql://localhost:3306/hospital_scheduler`
- Username: `root`
- Password: `123456`
- Backend port: `8080`

### 2. Chạy backend

```bash
cd backend
./mvnw spring-boot:run
```

Hoặc trên Windows:

```bash
cd backend
mvnw.cmd spring-boot:run
```

Backend sẽ chạy tại `http://localhost:8080`.

### 3. Chạy frontend

```bash
cd frontend
pnpm install
pnpm dev
```

Frontend sẽ chạy tại `http://localhost:3000`.

Nếu cần chỉ rõ API URL cho frontend, tạo file `.env.local` trong `frontend/`:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

Nếu không cấu hình biến này, frontend đang fallback về `http://localhost:8080/api/v1` trong code.

## Tài khoản seed mặc định

`DataSeeder` seed dữ liệu mẫu khi database còn trống. Tổng cộng **20 nhân sự**: 1 admin, 2 manager, 17 staff.

Tài khoản có sẵn:

| Username | Password | Role | Họ tên |
|---|---|---|---|
| `admin` | `admin123` | ADMIN + MANAGER | Nguyễn Văn An |
| `manager1` | `123456` | MANAGER | Trần Thị Bình |
| `manager2` | `123456` | MANAGER | Lê Hoàng Cường |
| `nvminh` | `123456` | STAFF | Nguyễn Văn Minh |
| `tthuhien` | `123456` | STAFF | Trần Thu Hiền |
| *(+ 15 staff khác)* | `123456` | STAFF | … |

Danh sách đầy đủ: xem `DataSeeder.java` method `seedAdminUser()`.

## Dữ liệu mẫu được seed

Khi database rỗng, hệ thống tự tạo:

- 4 `shift types`: `L01`, `L02`, `L03`, `L04`
- 4 nhóm chuyên môn mẫu
- 1 kỳ `PUBLISHED`: `Kỳ tháng 06/2026`
- 1 kỳ `DRAFT`: `Kỳ tháng 07/2026`
- Một số `shift requirements`
- Một số lịch mẫu, bao gồm cả dữ liệu có conflict để test UI và rule

## Màn hình frontend hiện có

Các route quan trọng trong `frontend/src/app`:

| Route | Mô tả |
|-------|-------|
| `/login` | Đăng nhập |
| `/dashboard` | Dashboard tổng quan |
| `/staff` | Danh sách nhân sự |
| `/staff/create` | Tạo nhân sự |
| `/staff/profile` | Hồ sơ cá nhân |
| `/duty-24` | Lịch trực `L01` |
| `/all-day` | Lịch thông tầm `L02` |
| `/service-clinic` | Lịch phòng khám dịch vụ `L03` |
| `/expert-clinic` | Lịch phòng khám chuyên gia `L04` |
| `/schedule-summary` | Tổng hợp lịch + export |
| `/monthly-schedule` | Bảng lịch tháng + conflicts + coverage |
| `/conflict-check` | Kiểm tra xung đột |
| `/swap-requests` | Yêu cầu đổi ca |
| `/leave-requests` | Đơn nghỉ phép |
| `/notifications` | Thông báo |
| `/reports` | Báo cáo |
| `/reports/staff` | Báo cáo theo nhân sự |
| `/reports/monthly` | Báo cáo theo tháng |
| `/reports/conflicts` | Báo cáo xung đột |
| `/audit-history` | Nhật ký thao tác |
| `/auto-scheduling` | Auto scheduling (M07) |
| `/auto-scheduling/algorithm-config` | Cấu hình thuật toán |
| `/auto-scheduling/history` | Lịch sử chạy |
| `/settings` | Cài đặt |
| `/settings/roles` | **Ma trận phân quyền** (M01-F05) |

## API chính hiện có

### Auth

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`

Auth hiện dùng JWT lưu trong cookie HTTP-only tên `medschedule_access_token`.

### Schedule

- `GET /api/v1/schedules/period/{periodId}`
- `GET /api/v1/schedules/period/{periodId}/date/{date}`
- `GET /api/v1/schedules/staff/{staffId}`
- `GET /api/v1/schedules/conflicts/check/{periodId}`
- `POST /api/v1/schedules`
- `PUT /api/v1/schedules/{id}`
- `DELETE /api/v1/schedules/{id}`
- `GET /api/v1/schedules/replacements/{periodId}`

### Period

- `GET /api/v1/periods`
- `GET /api/v1/periods/{id}`
- `POST /api/v1/periods`
- `PUT /api/v1/periods/{id}`
- `POST /api/v1/periods/{id}/publish`
- `POST /api/v1/periods/{id}/archive`

### Dashboard / Export

- `GET /api/v1/dashboard`
- `GET /api/v1/dashboard/shifts`
- `GET /api/v1/dashboard/periods`
- `GET /api/v1/dashboard/workload/period/{periodId}`
- `GET /api/v1/dashboard/export/schedule/{periodId}`
- `GET /api/v1/dashboard/export/schedule/{periodId}/pdf`
- `GET /api/v1/dashboard/export/workload/{periodId}`

### Auto scheduling

- `POST /api/v1/auto-schedule/preview`
- `POST /api/v1/auto-schedule`
- `GET /api/v1/auto-schedule/unassigned/{periodId}` (M07-F06)
- `GET /api/v1/auto-schedule/suggest-replacements/{scheduleId}`
- `GET /api/v1/auto-schedule/workload-chart/{periodId}`
- `GET /api/v1/auto-schedule/metrics/period/{periodId}`

### Roles & Permissions (M01-F05)

- `GET /api/v1/roles/permissions/matrix` — full role × permission matrix (ADMIN only)
- `POST /api/v1/roles/permissions/toggle` — grant or revoke a permission for a role

### Staff / exchange

- `GET /api/v1/staff`
- `GET /api/v1/staff/active`
- `GET /api/v1/staff/me`
- `POST /api/v1/staff/import`
- `GET /api/v1/schedule-exchanges`
- `GET /api/v1/schedule-exchanges/pending`
- `POST /api/v1/schedule-exchanges/requester/{requesterId}`
- `PUT /api/v1/schedule-exchanges/{id}/approve`
- `PUT /api/v1/schedule-exchanges/{id}/reject`

## Trạng thái triển khai hiện tại

Những phần đã thấy rõ trong code:

- CRUD lịch cơ bản cho `L01`-`L04`
- Kiểm tra conflict theo kỳ
- Tự tính và trả `compensationDate` từ backend
- Hiển thị ngày nghỉ bù trên bảng lịch tháng (`/monthly-schedule`), khóa thao tác trên ô nghỉ bù
- Export Excel cho lịch và workload
- Export PDF cho lịch tổng hợp nếu service PDF khả dụng trong môi trường chạy
- Auto scheduling với các luồng preview, run, báo cáo unassigned, workload chart, metrics
- Seed dữ liệu mẫu để demo nhanh (20 nhân sự, 2 kỳ lịch)

Các điểm cần hiểu đúng khi đọc tài liệu:

- `SPEC.md` và `QuanLyLichCongTac_v5.md` chứa nhiều mô tả theo hướng mục tiêu sản phẩm, không phải mục nào cũng đồng nghĩa UI hiện tại đã hoàn thiện 100%
- Một số flow giữa các module đang chưa đồng đều về UX dù backend endpoint đã có
- Quyền trên API không hoàn toàn giống mô tả product-level; ví dụ publish/archive period đang yêu cầu `ADMIN`

## Test hiện có

Backend đang có test ở các vùng chính (206 tests, 0 failures):

- `backend/src/test/java/com/hospital/scheduler/service/ConflictDetectionServiceTest.java`
- `backend/src/test/java/com/hospital/scheduler/service/ScheduleServiceBusinessRulesTest.java`
- `backend/src/test/java/com/hospital/scheduler/service/AutoSchedulingServiceTest.java`
- `backend/src/test/java/com/hospital/scheduler/service/LeaveRequestServiceTest.java`
- `backend/src/test/java/com/hospital/scheduler/service/ScheduleServiceTest.java`
- `backend/src/test/java/com/hospital/scheduler/service/RoleServiceTest.java`
- `frontend/src/lib/api-client.test.ts` (30 tests)
- `frontend/tests/e2e/*.spec.ts` (Playwright E2E)

Chạy test backend:

```bash
cd backend
./mvnw test
```

Chạy E2E (cần backend chạy trên port 8080 và frontend trên port 3000):

```bash
cd frontend
pnpm playwright test
```

## Tài liệu liên quan

- `SPEC.md` — scope nghiệp vụ và constraints
- `QuanLyLichCongTac_v5.md` — mô tả chức năng chi tiết
- `backend/HELP.md` — hướng dẫn backend current-state
- `frontend/README.md` — hướng dẫn frontend current-state
