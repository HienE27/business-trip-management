# Frontend - Hospital Scheduler

Frontend quản trị cho hệ thống quản lý lịch công tác, viết bằng Next.js App Router.

## Công nghệ

- Next.js `16.2.6`
- React `19.2.4`
- TypeScript `5`
- Tailwind CSS `4`
- ESLint `9`

## Chạy local

### Cài dependencies

```bash
pnpm install
```

### Cấu hình API

Frontend gọi backend qua biến môi trường:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

Tạo file `.env.local` trong thư mục `frontend/` nếu cần override.

Nếu không có biến này, code hiện đang fallback về `http://localhost:8080/api/v1`.

### Start dev server

```bash
pnpm dev
```

App sẽ chạy tại `http://localhost:3000`.

## Scripts

- `pnpm dev` — chạy dev server
- `pnpm build` — build production
- `pnpm start` — chạy production build
- `pnpm lint` — chạy ESLint

## Các màn hình chính

App Router hiện có các trang chính:

- `/login` — đăng nhập
- `/` — dashboard tổng quan
- `/staff` — danh sách nhân sự
- `/staff/create` — tạo nhân sự
- `/staff/profile` — hồ sơ cá nhân
- `/duty-24` — quản lý lịch `L01`
- `/all-day` — quản lý lịch `L02`
- `/service-clinic` — quản lý lịch `L03`
- `/expert-clinic` — quản lý lịch `L04`
- `/schedule-summary` — tổng hợp lịch và export
- `/conflict-check` — kiểm tra xung đột
- `/swap-requests` — yêu cầu đổi ca
- `/notifications` — thông báo
- `/reports` — workload report
- `/audit-history` — nhật ký thao tác
- `/auto-scheduling` — auto scheduling
- `/settings` — cài đặt giao diện

## Luồng dữ liệu chính

- Auth dùng cookie HTTP-only do backend set sau login
- Phần lớn API yêu cầu user có role `ADMIN` hoặc `MANAGER`
- Frontend đọc dữ liệu kỳ lịch (`periods`) rồi tải dữ liệu theo `periodId`
- Các màn hình lịch sử dụng endpoint conflict check theo kỳ để hiển thị cảnh báo
- Với lịch `L01`, frontend ưu tiên dùng `compensationDate` trả từ backend thay vì tự tính ở client

## Tình trạng triển khai đáng chú ý

- `schedule-summary` hỗ trợ export Excel và PDF theo `periodId`
- `reports` đang tập trung vào workload report theo kỳ
- `auto-scheduling` đã có preview, run, metrics, unassigned report và apply template flow trong UI
- Một số khác biệt UX giữa các module vẫn có thể tồn tại dù backend rule đã thống nhất

## Tài khoản dùng thử

Nếu backend đang dùng seed mặc định, có thể đăng nhập bằng:

- `admin / admin123`
- `staff1 / 123456`

Thực tế các màn quản trị sẽ phù hợp nhất khi dùng `admin` vì nhiều API yêu cầu `ADMIN` hoặc `MANAGER`.

## Ghi chú

- File này thay cho README mặc định của `create-next-app`
- Thư mục `.next/` là build artifact, không phải source code
- Nếu frontend báo lỗi xác thực hoặc không tải được dữ liệu, kiểm tra backend đang chạy ở `:8080` và cookie auth đã được set thành công
