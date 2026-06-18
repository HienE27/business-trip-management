# FE: 11 vấn đề frontend pages

## Goal

Fix #14 tách create/edit pages, #15 expert-clinic sidebar, #16 notifications pagination, #17-18 auth header, #19 ConfirmDialog, #20-21 hardcoded colors, #22-23 type checks, #24 skeleton loaders.

## Requirements

### #14 Tách create/edit pages
- `/staff/create/page.tsx` (1389 bytes hiện tại) chỉ dùng StaffCrudPanel với `?id=` hack → refactor thành create-only page.
- `/staff/[id]/edit/page.tsx` (15700 bytes) đã có sẵn dedicated form → giữ nguyên.

### #15 expert-clinic sidebar
- Thêm `expert-clinic` section vào `APP_SECTIONS` array trong `data/navigation.ts`.

### #16 notifications pagination
- `/notifications` hiện load toàn bộ → thêm state `page`, `pageSize`, gọi `api.getNotificationsByStaff(staffId, {page, size})` hoặc `getNotificationsByStaffPaginated`. Có thể giữ client-side slicing nếu backend chưa có endpoint phân trang.

### #17-18 Auth header / raw fetch
- `/dashboard/page.tsx` `handleExport` + `handleExportPdf` dùng raw `fetch()` với manual token → dùng `api.exportScheduleExcel()` (đã có sẵn trong api-client).
- `/reports/conflicts/page.tsx` `<a href="/api/v1/...">` thiếu auth → thay bằng button gọi `api.exportScheduleExcel()`.

### #19 ConfirmDialog
- 4 chỗ dùng `confirm()` native: algorithm-config, holidays, leave-requests, notifications → thay bằng shared `ConfirmDialog` component.

### #20-21 Hardcoded colors
- `/auto-scheduling/history/page.tsx` có `bg-blue-100`, `bg-green-100`, `bg-purple-100`, `bg-orange-400` → dùng design tokens (bg-primary-fixed, bg-secondary-container, bg-tertiary-container, etc.).

### #22-23 Type checks
- `/expert-clinic/page.tsx` dùng field `hasConflict` trên Schedule type → cần `api.getExpertClinicSchedules` trả về field này. Nếu không, fallback về computed check.
- `/holidays/page.tsx` dùng `isNationalHoliday` → đã match backend rồi (line 73-74).

### #24 Skeleton loaders
- Thay các `div animate-spin` (spinner-only) bằng `Skeleton` shared component trên các page list: holidays, leave-requests, audit-history, swap-requests, monthly-schedule, reports.

## Acceptance Criteria

- [ ] #14 `/staff/create` chỉ dùng cho create (không có `?id=` hack)
- [ ] #15 expert-clinic xuất hiện trong sidebar navigation
- [ ] #16 notifications page có phân trang (page/pageSize controls)
- [ ] #17 dashboard export buttons dùng `api.exportScheduleExcel()` (không raw fetch)
- [ ] #18 reports/conflicts export dùng `api.exportScheduleExcel()` (không raw anchor)
- [ ] #19 Tất cả 4 page dùng `ConfirmDialog` thay cho native `confirm()`
- [ ] #20-21 Không còn hardcoded Tailwind colors trong auto-scheduling/history
- [ ] #22 expert-clinic page handle missing `hasConflict` safely
- [ ] #23 holidays `isNationalHoliday` field verified match backend
- [ ] #24 Skeleton loaders thay cho spinner-only trên ít nhất 3 list pages

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
