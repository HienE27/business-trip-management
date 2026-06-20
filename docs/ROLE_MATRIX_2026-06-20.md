# Role Matrix — 2026-06-20

> Snapshot phân quyền UI của tất cả `frontend/src/app/**/page.tsx`. Đây là
> **client-side guard** (UI-only). Backend Spring Boot phải tự enforce
> authorization trên mọi endpoint. Component dùng: `RoleGuard` (16 routes)
> hoặc inline logic + canManage hook (4 routes).

## Quick Reference

| Route | Guard | Allowed Roles | Notes |
| --- | --- | --- | --- |
| `/` | (none) | All authenticated | Root redirect — bất kỳ role đã login |
| `/login` | (none — public) | All | Public auth page |
| `/dashboard` | (none) | All authenticated | KPI tổng quan cho mọi role |
| `/monthly-schedule` | inline `canManage` | All (read), ADMIN+MANAGER (write) | Calendar full-width + WorkflowShell |
| `/staff/profile` | (none) | STAFF self-service | Nhân viên xem lịch cá nhân |
| `/duty-24` | **RoleGuard** | ADMIN, MANAGER | Lịch trực 24/24 — qua `GuardedScheduleByTypePage` |
| `/all-day` | **RoleGuard** | ADMIN, MANAGER | Lịch thông tầm — qua `GuardedScheduleByTypePage` |
| `/service-clinic` | **RoleGuard** | ADMIN, MANAGER | Lịch PK dịch vụ |
| `/expert-clinic` | **RoleGuard** | ADMIN, MANAGER | Lịch PK chuyên gia |
| `/periods` | **RoleGuard** | ADMIN, MANAGER | Quản lý kỳ lịch |
| `/holidays` | **RoleGuard** | ADMIN, MANAGER | Quản lý ngày lễ |
| `/requirements` | **RoleGuard** | ADMIN, MANAGER | Yêu cầu nhân sự theo ca |
| `/audit-history` | **RoleGuard** | ADMIN, MANAGER | Nhật ký thao tác |
| `/staff` | **RoleGuard** | ADMIN, MANAGER | Danh sách nhân sự |
| `/staff/create` | **RoleGuard** | ADMIN, MANAGER | Tạo nhân sự mới |
| `/staff/[id]` | **RoleGuard** | ADMIN, MANAGER | Chi tiết nhân sự (view-only cho STAFF được thay bằng `/staff/profile`) |
| `/staff/[id]/edit` | **RoleGuard** | ADMIN, MANAGER | Sửa nhân sự |
| `/settings` | **RoleGuard** | ADMIN | Cài đặt hệ thống |
| `/settings/roles` | **RoleGuard** | ADMIN | Ma trận phân quyền |
| `/reports` | **RoleGuard** | ADMIN, MANAGER | Trung tâm báo cáo |
| `/reports/conflicts` | **RoleGuard** | ADMIN, MANAGER | Báo cáo xung đột |
| `/reports/staff` | **RoleGuard** | ADMIN, MANAGER | Khối lượng nhân sự |
| `/reports/monthly` | **RoleGuard** | ADMIN, MANAGER | Báo cáo kỳ lịch |
| `/auto-scheduling` | inline `canManage` | All (read), ADMIN+MANAGER (write) | WorkflowShell-based; manager-only controls (chạy thuật toán, apply) |
| `/auto-scheduling/algorithm-config` | **RoleGuard** | ADMIN | Cấu hình tham số thuật toán (admin-only) |
| `/auto-scheduling/history` | **RoleGuard** | ADMIN, MANAGER | Lịch sử chạy auto |
| `/leave-requests` | **RoleGuard** | ADMIN, MANAGER, STAFF | WorkflowShell — đã guard; STAFF xem/gửi yêu cầu của mình, ADMIN+MANAGER duyệt |
| `/swap-requests` | **RoleGuard** | ADMIN, MANAGER, STAFF | Self-service: tạo + theo dõi yêu cầu đổi ca |
| `/notifications` | **RoleGuard** | ADMIN, MANAGER, STAFF | Self-service: thông báo cá nhân |

**Tổng cộng**: 29 routes
- 19 dùng `RoleGuard` (16 `DashboardShell` + 3 `WorkflowShell` — `/leave-requests`, `/auto-scheduling/algorithm-config`, `/auto-scheduling/history`)
- 4 dùng `GuardedScheduleByTypePage` (mới — wrap `ScheduleByTypePage` với `RoleGuard`)
- 5 public/all-roles (`/`, `/login`, `/dashboard`, `/staff/profile`, `/monthly-schedule`)
- 1 inline (`/auto-scheduling` — có `canManage` checks cho write controls)
- **0 chưa guard** — toàn bộ routes production đã có ít nhất một guard

## Components & Helpers

| Tên | Vai trò |
| --- | --- |
| `RoleGuard` (`src/components/auth/RoleGuard.tsx`) | Wrap `DashboardShell` + `EmptyState` "không có quyền". Props: `activeSection`, `title`, `description`, `allow[]`, `children`, `deniedDescription?`. |
| `GuardedScheduleByTypePage` (`src/components/monthly-schedule/GuardedScheduleByTypePage.tsx`) | Helper bọc `ScheduleByTypePage` qua `RoleGuard`. Dùng cho 4 route `/duty-24`, `/all-day`, `/service-clinic`, `/expert-clinic`. |
| `useRole()` (`src/hooks/useRole.ts`) | Hook trả về `"ADMIN" \| "MANAGER" \| "STAFF"` dựa trên `user.roles` từ `AuthProvider`. |
| `canManage(role)` | Helper boolean: ADMIN hoặc MANAGER. |
| `canApprove(role)` / `canEditSchedule(role)` / `canDeleteSchedule(role)` / `canViewAuditLog(role)` | Capability matrix theo role. |

## Self-Service vs Manager Routes

**Self-service (STAFF allowed)**:
- `/swap-requests` — tạo yêu cầu đổi ca cá nhân
- `/notifications` — thông báo cá nhân
- `/staff/profile` — lịch cá nhân (KHÔNG dùng `/staff/[id]` — đó là manager view)
- `/dashboard` — KPI cá nhân
- `/monthly-schedule` — xem lịch (read-only, không có controls của manager)

**Manager-only (ADMIN + MANAGER)**:
- Mọi route quản lý lịch: `/periods`, `/holidays`, `/requirements`, `/duty-24`, `/all-day`, `/service-clinic`, `/expert-clinic`, `/auto-scheduling`, `/staff`, `/staff/create`, `/staff/[id]`, `/staff/[id]/edit`, `/reports`, `/reports/conflicts`, `/reports/staff`, `/reports/monthly`, `/audit-history`, `/leave-requests`

**Admin-only (ADMIN)**:
- `/settings` — system configuration
- `/settings/roles` — permission matrix

## Gaps & Follow-ups

1. ~~**`/leave-requests` chưa có guard**~~ — đã resolve (session 4, 2026-06-20). Refactor: default export wrap `<RoleGuard allow={["ADMIN", "MANAGER", "STAFF"]}>`; STAFF xem + gửi yêu cầu của mình, ADMIN/MANAGER duyệt.
2. **`/monthly-schedule` chỉ có inline guard** — STAFF thấy calendar read-only nhờ `canManage` ẩn controls, nhưng URL access không denied.
   - **Trade-off**: cho phép STAFF xem lịch tổng là feature; chỉ disable write controls. Không cần fix.
3. ~~**`/auto-scheduling/*` dùng `useRole` inline**~~ — đã resolve (session 4, 2026-06-20). Cả `/algorithm-config` (admin-only) và `/history` (admin+manager) giờ wrap trong `RoleGuard`. Page chính `/auto-scheduling` vẫn dùng inline `canManage` cho write controls (đổi thuật toán, apply) — chấp nhận được vì read-only với tất cả roles là design choice.

## Audit History

- **2026-06-20 session 1** (`7f63b37`): 6 routes đầu tiên có guard.
- **2026-06-20 session 2** (`32bbeff`): mở rộng thêm 8 routes.
- **2026-06-20 session 3** (`483775a` + this doc): thêm 4 `GuardedScheduleByTypePage` + audit + doc này.
- **2026-06-20 session 4** (`71893b4` + updates): thêm `RoleGuard` cho `/leave-requests` (3 roles), `/auto-scheduling/algorithm-config` (admin), `/auto-scheduling/history` (admin+manager); lazy-load `Modal` + `ConfirmDialog` trên `/periods`; doc gaps resolved.
- **Total coverage**: 23/24 protected routes có RoleGuard hoặc GuardedScheduleByTypePage; 1 route inline (`/auto-scheduling`); 4 routes public/all-roles; 0 gaps chưa biết.