-- Migration: Seed app_permission and role_permission data
-- Run this to populate the permission matrix for M01-F05

-- =====================================================
-- 1. Seed app_permission (INSERT IGNORE to skip existing)
-- =====================================================
INSERT IGNORE INTO app_permission (name, description) VALUES
('STAFF_READ', 'Xem danh sách nhân sự'),
('STAFF_WRITE', 'Thêm/sửa/xóa nhân sự'),
('SCHEDULE_READ', 'Xem lịch trực'),
('SCHEDULE_WRITE', 'Tạo/sửa/xóa lịch trực'),
('SCHEDULE_PUBLISH', 'Công bố lịch trực'),
('LEAVE_REQUEST_CREATE', 'Tạo yêu cầu nghỉ'),
('LEAVE_REQUEST_REVIEW', 'Duyệt/từ chối yêu cầu nghỉ'),
('SCHEDULE_EXCHANGE_CREATE', 'Tạo yêu cầu đổi ca'),
('SCHEDULE_EXCHANGE_REVIEW', 'Duyệt/từ chối yêu cầu đổi ca'),
('CONFIG_MANAGE', 'Quản lý cấu hình thuật toán'),
('AUDIT_READ', 'Xem lịch sử thay đổi');

-- =====================================================
-- 2. Seed role_permission for ADMIN (all permissions)
-- =====================================================
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
CROSS JOIN app_permission p
WHERE r.name = 'ADMIN';

-- =====================================================
-- 3. Seed role_permission for MANAGER
-- =====================================================
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.name IN (
    'STAFF_READ',
    'SCHEDULE_READ',
    'SCHEDULE_WRITE',
    'SCHEDULE_PUBLISH',
    'LEAVE_REQUEST_REVIEW',
    'SCHEDULE_EXCHANGE_REVIEW',
    'CONFIG_MANAGE'
)
WHERE r.name = 'MANAGER';

-- =====================================================
-- 4. Seed role_permission for STAFF
-- =====================================================
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.name IN (
    'STAFF_READ',
    'SCHEDULE_READ',
    'LEAVE_REQUEST_CREATE',
    'SCHEDULE_EXCHANGE_CREATE'
)
WHERE r.name = 'STAFF';
