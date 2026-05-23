USE hospital_scheduler;

-- =====================================================
-- MOCK DATA - Hospital Scheduler
-- Version: 1.1 (RBAC) - OPTIMIZED
-- 17 bảng theo tài liệu yêu cầu v1.1
-- =====================================================

-- =====================================================
-- 1. SPECIALTY (Chuyên khoa)
-- =====================================================
INSERT INTO specialty (name, description) VALUES
('SAN_NHI', 'Sản - Nhi'),
('NOI_TIET', 'Nội tiết'),
('NGOAI', 'Ngoại'),
('MATT', 'Mắt'),
('RANG', 'Răng - Hàm'),
('TIM_MACH', 'Tim mạch'),
('THAN_TIEU_HOA', 'Thận - Tiêu hóa'),
('CHUYEN_GIA', 'Chuyên gia tổng hợp');

-- =====================================================
-- 2. PERMISSION (Quyền hạn) - M01-F05
-- =====================================================
INSERT INTO permission (name, description) VALUES
-- Staff permissions
('view_own_schedule', 'Xem lịch cá nhân'),
('view_all_schedule', 'Xem lịch toàn phòng'),
('request_leave', 'Đăng ký nghỉ phép'),
('request_exchange', 'Yêu cầu đổi ca trực'),

-- Manager permissions
('approve_leave', 'Phê duyệt nghỉ phép'),
('approve_exchange', 'Phê duyệt đổi ca'),
('view_reports', 'Xem báo cáo thống kê'),

-- Schedule management
('create_schedule_24', 'Xếp lịch trực 24/24'),
('create_schedule_thongtam', 'Xếp lịch thông tầm'),
('create_schedule_dichvu', 'Xếp lịch phòng khám dịch vụ'),
('create_schedule_chuyengia', 'Xếp lịch phòng khám chuyên gia'),
('edit_schedule', 'Chỉnh sửa lịch đã xếp'),
('delete_schedule', 'Xóa lịch đã xếp'),
('publish_schedule', 'Công bố lịch tháng'),

-- Staff management
('manage_staff', 'Quản lý nhân sự'),
('view_staff', 'Xem danh sách nhân sự'),

-- System
('run_auto_schedule', 'Chạy xếp lịch tự động'),
('configure_algorithm', 'Cấu hình thuật toán'),
('system_config', 'Cấu hình hệ thống');

-- =====================================================
-- 3. ROLE (Vai trò) - M01-F05
-- =====================================================
INSERT INTO role (name, description) VALUES
('ADMIN', 'Quản lý lịch - Toàn quyền quản lý lịch trực'),
('MANAGER', 'Trưởng phòng - Xem và phê duyệt'),
('STAFF', 'Nhân viên - Xem lịch cá nhân và đăng ký nghỉ');

-- =====================================================
-- 4. ROLE_PERMISSION
-- =====================================================
-- ADMIN: Full permissions
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p WHERE r.name = 'ADMIN';

-- MANAGER: View + Approve + Reports
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'MANAGER'
AND p.name IN (
    'view_own_schedule', 'view_all_schedule',
    'approve_leave', 'approve_exchange', 'view_reports',
    'view_staff'
);

-- STAFF: Basic permissions only
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r, permission p
WHERE r.name = 'STAFF'
AND p.name IN (
    'view_own_schedule',
    'request_leave', 'request_exchange',
    'view_staff'
);

-- =====================================================
-- 5. SHIFT_TYPE (Loại lịch L01-L04) - 1.2
-- =====================================================
INSERT INTO shift_type (id, name, description, start_time, end_time, fatigue_score) VALUES
('L01', 'Lịch trực 24/24',
 'Nhân sự trực liên tục từ 7h30 ngày N đến 7h30 ngày N+1. Sau ngày trực được nghỉ bù theo quy tắc.',
 '07:30:00', '07:30:00', 5),
('L02', 'Lịch thông tầm',
 'Ca làm việc liên tục không nghỉ trưa trong ngày.',
 '08:00:00', '17:00:00', 3),
('L03', 'Lịch phòng khám dịch vụ',
 'Phụ trách ca khám dịch vụ trong ngày.',
 '08:00:00', '17:00:00', 2),
('L04', 'Lịch phòng khám chuyên gia',
 'Phụ trách ca khám chuyên sâu trong ngày.',
 '08:00:00', '17:00:00', 4);

-- =====================================================
-- 6. STAFF (Nhân sự) - 20 người
-- =====================================================
INSERT INTO staff (username, password_hash, full_name, phone, email, specialty_id, max_shifts_per_month) VALUES
-- Admin (ID: 1)
('admin001', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Nguyễn Văn Quản', '0900100001', 'admin@hospital.vn', NULL, 0),

-- Managers (ID: 2, 3)
('manager001', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Trần Thị Trưởng', '0900200001', 'truongphong1@hospital.vn', 1, 0),
('manager002', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Lê Văn Phó', '0900200002', 'truongphong2@hospital.vn', 2, 0),

-- Doctors - Sản Nhi (ID: 4, 5, 6)
('dr_sannhi_01', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Phạm Thị Hương', '0901100001', 'dr_huong@hospital.vn', 1, 5),
('dr_sannhi_02', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Hoàng Văn Minh', '0901100002', 'dr_minh@hospital.vn', 1, 5),
('dr_sannhi_03', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Ngô Thị Lan', '0901100003', 'dr_lan@hospital.vn', 1, 5),

-- Doctors - Nội tiết (ID: 7, 8)
('dr_noitiet_01', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Đặng Văn Hùng', '0901200001', 'dr_hung@hospital.vn', 2, 5),
('dr_noitiet_02', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Vũ Thị Mai', '0901200002', 'dr_mai@hospital.vn', 2, 5),

-- Doctors - Ngoại (ID: 9, 10)
('dr_ngoai_01', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Bùi Văn Đức', '0901300001', 'dr_duc@hospital.vn', 3, 5),
('dr_ngoai_02', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Đỗ Thị Hà', '0901300002', 'dr_ha@hospital.vn', 3, 5),

-- Doctors - Mắt (ID: 11, 12)
('dr_mat_01', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Cao Văn Tài', '0901400001', 'dr_tai@hospital.vn', 4, 5),
('dr_mat_02', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Trịnh Thị Ngọc', '0901400002', 'dr_ngoc@hospital.vn', 4, 5),

-- Doctors - Răng (ID: 13, 14)
('dr_rang_01', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Phan Văn Nam', '0901500001', 'dr_nam@hospital.vn', 5, 5),
('dr_rang_02', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Lý Thị Loan', '0901500002', 'dr_loan@hospital.vn', 5, 5),

-- Doctors - Tim mạch (ID: 15, 16)
('dr_timmach_01', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Trần Văn Toàn', '0901600001', 'dr_toan@hospital.vn', 6, 5),
('dr_timmach_02', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Nguyễn Thị Oanh', '0901600002', 'dr_oanh@hospital.vn', 6, 5),

-- Doctors - Thận tiêu hóa (ID: 17, 18)
('dr_thantieu_01', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Lưu Văn Sơn', '0901700001', 'dr_son@hospital.vn', 7, 5),
('dr_thantieu_02', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Hồ Thị Hương', '0901700002', 'dr_huong2@hospital.vn', 7, 5),

-- Chuyên gia (ID: 19, 20)
('dr_chuyengia_01', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Võ Văn Thắng', '0901800001', 'dr_thang@hospital.vn', 8, 5),
('dr_chuyengia_02', '$2b$10$abcdefghijklmnopqrstuvwxyz', 'Trương Thị Thảo', '0901800002', 'dr_thao@hospital.vn', 8, 5);

-- =====================================================
-- 7. STAFF_ROLE (Phân vai trò)
-- =====================================================
INSERT INTO staff_role (staff_id, role_id)
SELECT s.id, r.id FROM staff s, role r WHERE s.username = 'admin001' AND r.name = 'ADMIN';

INSERT INTO staff_role (staff_id, role_id)
SELECT s.id, r.id FROM staff s, role r WHERE s.username IN ('manager001', 'manager002') AND r.name = 'MANAGER';

INSERT INTO staff_role (staff_id, role_id)
SELECT s.id, r.id FROM staff s, role r WHERE s.username NOT IN ('admin001', 'manager001', 'manager002') AND r.name = 'STAFF';

-- =====================================================
-- 8. SCHEDULE_PERIOD (Kỳ trực)
-- =====================================================
INSERT INTO schedule_period (period_name, status, generated_by) VALUES
('2026-05', 'PUBLISHED', 1),
('2026-06', 'DRAFT', 1);

-- =====================================================
-- 9. SAMPLE SCHEDULES (Lịch mẫu tháng 6/2026)
-- =====================================================

-- Lịch trực 24/24 (L01)
INSERT INTO schedule (period_id, work_date, staff_id, shift_type_id, created_by) VALUES
(2, '2026-06-01', 4, 'L01', 1),   -- Thứ 2
(2, '2026-06-01', 5, 'L01', 1),
(2, '2026-06-02', 6, 'L01', 1),   -- Thứ 3
(2, '2026-06-02', 7, 'L01', 1),
(2, '2026-06-03', 8, 'L01', 1),   -- Thứ 4
(2, '2026-06-03', 9, 'L01', 1),
(2, '2026-06-04', 10, 'L01', 1),  -- Thứ 5
(2, '2026-06-04', 11, 'L01', 1),
(2, '2026-06-05', 12, 'L01', 1),  -- Thứ 6
(2, '2026-06-05', 13, 'L01', 1),
(2, '2026-06-06', 14, 'L01', 1),  -- Thứ 7
(2, '2026-06-06', 15, 'L01', 1),
(2, '2026-06-07', 16, 'L01', 1);  -- Chủ Nhật

-- Lịch thông tầm (L02)
INSERT INTO schedule (period_id, work_date, staff_id, shift_type_id, created_by) VALUES
(2, '2026-06-08', 17, 'L02', 1),
(2, '2026-06-09', 18, 'L02', 1),
(2, '2026-06-10', 19, 'L02', 1),
(2, '2026-06-11', 20, 'L02', 1);

-- Lịch phòng khám dịch vụ (L03)
INSERT INTO schedule (period_id, work_date, staff_id, shift_type_id, created_by) VALUES
(2, '2026-06-08', 4, 'L03', 1),
(2, '2026-06-09', 5, 'L03', 1),
(2, '2026-06-10', 6, 'L03', 1),
(2, '2026-06-11', 7, 'L03', 1);

-- Lịch phòng khám chuyên gia (L04)
INSERT INTO schedule (period_id, work_date, staff_id, shift_type_id, created_by) VALUES
(2, '2026-06-08', 19, 'L04', 1),
(2, '2026-06-09', 20, 'L04', 1),
(2, '2026-06-10', 8, 'L04', 1),
(2, '2026-06-11', 9, 'L04', 1);

-- =====================================================
-- 10. COMPENSATION_DAY (Ngày nghỉ bù) - 1.4
-- =====================================================

-- Trực Thứ 2 -> Nghỉ bù Thứ 3
INSERT INTO compensation_day (staff_id, shift_date, compensation_date, period_id) VALUES
(4, '2026-06-01', '2026-06-02', 2),
(5, '2026-06-01', '2026-06-02', 2);

-- Trực Thứ 3 -> Nghỉ bù Thứ 4
INSERT INTO compensation_day (staff_id, shift_date, compensation_date, period_id) VALUES
(6, '2026-06-02', '2026-06-03', 2),
(7, '2026-06-02', '2026-06-03', 2);

-- Trực Thứ 4 -> Nghỉ bù Thứ 5
INSERT INTO compensation_day (staff_id, shift_date, compensation_date, period_id) VALUES
(8, '2026-06-03', '2026-06-04', 2),
(9, '2026-06-03', '2026-06-04', 2);

-- Trực Thứ 5 -> Nghỉ bù Thứ 6
INSERT INTO compensation_day (staff_id, shift_date, compensation_date, period_id) VALUES
(10, '2026-06-04', '2026-06-05', 2),
(11, '2026-06-04', '2026-06-05', 2);

-- Trực Thứ 6 -> Nghỉ bù T3 tuần sau
INSERT INTO compensation_day (staff_id, shift_date, compensation_date, period_id) VALUES
(12, '2026-06-05', '2026-06-09', 2),
(13, '2026-06-05', '2026-06-09', 2);

-- Trực Thứ 7 -> Nghỉ bù T3 tuần sau
INSERT INTO compensation_day (staff_id, shift_date, compensation_date, period_id) VALUES
(14, '2026-06-06', '2026-06-09', 2),
(15, '2026-06-06', '2026-06-09', 2);

-- Trực Chủ Nhật -> Nghỉ bù T2
INSERT INTO compensation_day (staff_id, shift_date, compensation_date, period_id) VALUES
(16, '2026-06-07', '2026-06-08', 2);

-- =====================================================
-- 11. LEAVE_REQUEST (Đơn nghỉ phép)
-- =====================================================
INSERT INTO leave_request (staff_id, request_date, end_date, reason, status, reviewed_by) VALUES
(4, '2026-06-10', '2026-06-12', 'Nghỉ phép gia đình', 'PENDING', NULL),
(5, '2026-06-15', '2026-06-15', 'Khám sức khỏe định kỳ', 'APPROVED', 2),
(6, '2026-06-20', '2026-06-22', 'Du lịch', 'PENDING', NULL);

-- =====================================================
-- 12. SCHEDULE_EXCHANGE (Đổi ca trực) - M02-F04
-- =====================================================
INSERT INTO schedule_exchange (period_id, requester_id, target_id, requester_shift_date, target_shift_date, reason, status) VALUES
(2, 4, 5, '2026-06-08', '2026-06-09', 'Cần đổi ngày nghỉ bù', 'PENDING');

-- =====================================================
-- 13. NOTIFICATIONS (Thông báo)
-- =====================================================
INSERT INTO notification (staff_id, title, message, notification_type) VALUES
(4, 'Lịch trực tháng 6/2026', 'Bạn được phân công trực 24/24 ngày 01/06/2026. Ngày nghỉ bù: 02/06/2026', 'INFO'),
(5, 'Lịch trực tháng 6/2026', 'Bạn được phân công trực 24/24 ngày 01/06/2026. Ngày nghỉ bù: 02/06/2026', 'INFO'),
(6, 'Lịch trực tháng 6/2026', 'Bạn được phân công trực 24/24 ngày 02/06/2026. Ngày nghỉ bù: 03/06/2026', 'INFO'),
(2, 'Yêu cầu đổi ca', 'Dr Phạm Thị Hương yêu cầu đổi ca trực ngày 08/06', 'INFO');

-- =====================================================
-- 14. ALGORITHM_CONFIG (Cấu hình thuật toán) - M07-F01
-- =====================================================
INSERT INTO algorithm_config (param_key, param_value, description, updated_by) VALUES
('weight_fairness', 0.4, 'Trọng số công bằng phân bổ lịch', 1),
('weight_fatigue', 0.3, 'Trọng số mệt mỏi (fatigue)', 1),
('weight_specialty', 0.2, 'Trọng số chuyên khoa phù hợp', 1),
('min_rest_hours', 12, 'Số giờ nghỉ tối thiểu giữa 2 ca', 1),
('max_consecutive_days', 6, 'Số ngày trực liên tiếp tối đa', 1),
('compensation_week_offset', 1, 'Số tuần dời nghỉ bù (T6/T7)', 1);

-- =====================================================
-- 15. SYSTEM_LOG (Nhật ký hệ thống) - M06-F05
-- =====================================================
INSERT INTO system_log (staff_id, action_type, entity_type, entity_id, description) VALUES
(1, 'CREATE', 'schedule_period', 2, 'Tạo kỳ trực tháng 6/2026'),
(1, 'CREATE', 'schedule', 1, 'Xếp lịch trực 24/24 ngày 01/06'),
(1, 'CREATE', 'schedule', 2, 'Xếp lịch trực 24/24 ngày 02/06'),
(1, 'CREATE', 'compensation_day', 1, 'Tạo ngày nghỉ bù cho ngày 01/06'),
(1, 'RUN_ALGORITHM', 'algorithm', NULL, 'Chạy thuật toán xếp lịch tự động tháng 6/2026', NULL, NULL, '192.168.1.1'),
(2, 'APPROVE', 'leave_request', 2, 'Phê duyệt đơn nghỉ phép của Vũ Thị Mai');

-- =====================================================
-- 16. STAFF_EXCLUSION (Nhân sự ngoại lệ) - M07-F01
-- =====================================================
INSERT INTO staff_exclusion (staff_id, period_id, reason, exclusion_type, created_by) VALUES
(4, 2, 'Nghỉ phép gia đình', 'LEAVE', 1),
(6, 2, 'Du lịch cuối tháng', 'LEAVE', 1);
