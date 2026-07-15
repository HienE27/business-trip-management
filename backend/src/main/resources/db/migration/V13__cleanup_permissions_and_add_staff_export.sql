-- Migration: Permission matrix cleanup + STAFF_EXPORT
-- Date: 2026-07-14
--
-- Changes:
--   1. Remove 3 orphan permissions that no role uses:
--      PERIOD_MANAGE, SCHEDULE_EDIT, STAFF_MANAGE
--      (Replaced by fine-grained PERIOD_*, SCHEDULE_*, STAFF_* permissions)
--   2. Add STAFF_EXPORT permission (Excel/CSV export of staff list)
--   3. Grant STAFF_EXPORT to ADMIN + MANAGER (not STAFF)

-- =====================================================
-- 1. Remove orphan permissions (no FK to role_permission)
-- =====================================================
DELETE FROM app_permission
WHERE name IN ('PERIOD_MANAGE', 'SCHEDULE_EDIT', 'STAFF_MANAGE');

-- =====================================================
-- 2. Add STAFF_EXPORT permission
-- =====================================================
INSERT IGNORE INTO app_permission (name, description)
VALUES ('STAFF_EXPORT', 'Xuất danh sách nhân sự ra Excel/CSV');

-- =====================================================
-- 3. Grant STAFF_EXPORT to ADMIN and MANAGER
-- =====================================================
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
CROSS JOIN app_permission p
WHERE r.name IN ('ADMIN', 'MANAGER')
  AND p.name = 'STAFF_EXPORT';