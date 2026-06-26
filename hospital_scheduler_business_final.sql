-- =====================================================
-- DATABASE: Hospital Scheduler - Final Business Schema
-- Purpose: Backend Java Spring Boot + MySQL + REST API
-- Charset: utf8mb4 for Vietnamese text
-- =====================================================

CREATE DATABASE IF NOT EXISTS hospital_scheduler
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE hospital_scheduler;

-- =====================================================
-- Re-runnable setup: drop tables in dependency order
-- =====================================================
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS algorithm_metrics;
DROP TABLE IF EXISTS audit_history;
DROP TABLE IF EXISTS file_attachment;
DROP TABLE IF EXISTS schedule_conflict;
DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS system_log;
DROP TABLE IF EXISTS algorithm_config;
DROP TABLE IF EXISTS schedule_exchange;
DROP TABLE IF EXISTS compensation_day;
DROP TABLE IF EXISTS schedule;
DROP TABLE IF EXISTS leave_request;
DROP TABLE IF EXISTS shift_requirement;
DROP TABLE IF EXISTS schedule_template;
DROP TABLE IF EXISTS schedule_period;
DROP TABLE IF EXISTS shift_type;
DROP TABLE IF EXISTS holiday;
DROP TABLE IF EXISTS staff_role;
DROP TABLE IF EXISTS role_permission;
DROP TABLE IF EXISTS app_permission;
DROP TABLE IF EXISTS app_role;
DROP TABLE IF EXISTS staff;
DROP TABLE IF EXISTS specialty;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 1. SPECIALTY
-- Quản lý chuyên môn chuẩn hóa: Bác sĩ, Điều dưỡng, Kỹ thuật viên...
-- =====================================================
CREATE TABLE specialty (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =====================================================
-- 2. STAFF
-- Nhân sự tham gia hệ thống. Không lưu role trực tiếp tại đây.
-- Phân quyền đi qua staff_role.
-- =====================================================
CREATE TABLE staff (
    id INT AUTO_INCREMENT PRIMARY KEY,
    staff_code VARCHAR(20) NOT NULL UNIQUE COMMENT 'Mã nhân viên duy nhất (VD: NV001)',
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100) UNIQUE,
    specialty_id INT NULL,
    max_shifts_per_month INT NOT NULL DEFAULT 5,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    hire_date DATETIME NULL COMMENT 'Ngày vào làm',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_staff_specialty
        FOREIGN KEY (specialty_id) REFERENCES specialty(id) ON DELETE SET NULL,
    CONSTRAINT chk_staff_max_shifts
        CHECK (max_shifts_per_month >= 0)
) ENGINE=InnoDB;

-- =====================================================
-- 3. APP_ROLE
-- Tránh đặt tên bảng là role vì dễ gây nhầm với cơ chế ROLE của MySQL/Spring Security.
-- =====================================================
CREATE TABLE app_role (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =====================================================
-- 4. APP_PERMISSION
-- Quyền chi tiết theo chức năng/API.
-- =====================================================
CREATE TABLE app_permission (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =====================================================
-- 5. ROLE_PERMISSION
-- 1 role có nhiều permission, 1 permission có thể thuộc nhiều role.
-- =====================================================
CREATE TABLE role_permission (
    role_id INT NOT NULL,
    permission_id INT NOT NULL,
    PRIMARY KEY (role_id, permission_id),

    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES app_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================
-- 6. STAFF_ROLE
-- 1 nhân sự có thể có nhiều role.
-- =====================================================
CREATE TABLE staff_role (
    staff_id INT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (staff_id, role_id),

    CONSTRAINT fk_staff_role_staff
        FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_role_role
        FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================
-- 7. SHIFT_TYPE
-- Danh mục loại ca trực. is_overnight xử lý ca qua ngày, ví dụ 17:00 - 08:00 hôm sau.
-- =====================================================
CREATE TABLE shift_type (
    id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    start_time TIME NULL,
    end_time TIME NULL,
    is_overnight BOOLEAN NOT NULL DEFAULT FALSE,
    fatigue_score INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_shift_fatigue_score
        CHECK (fatigue_score >= 0)
) ENGINE=InnoDB;

-- =====================================================
-- 8. SCHEDULE_PERIOD
-- Kỳ lập lịch: tháng/tuần/khoảng ngày.
-- Quy tắc trạng thái xử lý ở backend: DRAFT -> PUBLISHED -> ARCHIVED.
-- =====================================================
CREATE TABLE schedule_period (
    id INT AUTO_INCREMENT PRIMARY KEY,
    period_name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status ENUM('DRAFT', 'PUBLISHED', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    generated_by INT NULL,
    generated_at TIMESTAMP NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_schedule_period_generated_by
        FOREIGN KEY (generated_by) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_schedule_period_range
        CHECK (start_date <= end_date),
    UNIQUE KEY uk_schedule_period_range (start_date, end_date)
) ENGINE=InnoDB;

-- =====================================================
-- 9. SHIFT_REQUIREMENT
-- Nhu cầu nhân sự cho từng ngày/ca/chuyên môn.
-- Rất quan trọng cho thuật toán lập lịch tự động.
-- Ví dụ: ngày X, ca đêm, cần 2 bác sĩ và 3 điều dưỡng.
-- =====================================================
CREATE TABLE shift_requirement (
    id INT AUTO_INCREMENT PRIMARY KEY,
    period_id INT NOT NULL,
    work_date DATE NOT NULL,
    shift_type_id VARCHAR(10) NOT NULL,
    specialty_id INT NOT NULL,
    required_staff_count INT NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_requirement_period
        FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE CASCADE,
    CONSTRAINT fk_requirement_shift_type
        FOREIGN KEY (shift_type_id) REFERENCES shift_type(id),
    CONSTRAINT fk_requirement_specialty
        FOREIGN KEY (specialty_id) REFERENCES specialty(id),
    CONSTRAINT chk_requirement_staff_count
        CHECK (required_staff_count > 0),
    UNIQUE KEY uk_requirement_unique (period_id, work_date, shift_type_id, specialty_id),
    UNIQUE KEY uk_requirement_id_period_date_shift (id, period_id, work_date, shift_type_id)
) ENGINE=InnoDB;

-- =====================================================
-- 10. LEAVE_REQUEST
-- Xin nghỉ theo khoảng ngày thay vì chỉ 1 ngày.
-- Nếu nghỉ 1 ngày: start_date = end_date.
-- =====================================================
CREATE TABLE leave_request (
    id INT AUTO_INCREMENT PRIMARY KEY,
    staff_id INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason TEXT,
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    reviewed_by INT NULL,
    reviewed_at TIMESTAMP NULL,
    review_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_leave_staff
        FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_leave_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_leave_date_range
        CHECK (start_date <= end_date)
) ENGINE=InnoDB;

-- =====================================================
-- 11. SCHEDULE
-- Lịch phân công thực tế.
-- requirement_id cho biết dòng lịch này đang đáp ứng nhu cầu nào.
-- has_conflict dùng để lọc nhanh; chi tiết conflict nằm ở schedule_conflict.
-- =====================================================
CREATE TABLE schedule (
    id INT AUTO_INCREMENT PRIMARY KEY,
    period_id INT NOT NULL,
    work_date DATE NOT NULL,
    staff_id INT NOT NULL,
    shift_type_id VARCHAR(10) NOT NULL,
    requirement_id INT NULL,
    has_conflict BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_schedule_period
        FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_staff
        FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_shift_type
        FOREIGN KEY (shift_type_id) REFERENCES shift_type(id),
    CONSTRAINT fk_schedule_requirement
        FOREIGN KEY (requirement_id, period_id, work_date, shift_type_id)
        REFERENCES shift_requirement(id, period_id, work_date, shift_type_id),

    UNIQUE KEY uk_schedule_unique (period_id, staff_id, shift_type_id, work_date)
) ENGINE=InnoDB;

-- =====================================================
-- 12. COMPENSATION_DAY
-- Ngày nghỉ bù sau ca trực đặc biệt, ví dụ L01.
-- Composite FK đảm bảo staff_id/period_id/shift_date khớp với schedule_id.
-- =====================================================
CREATE TABLE compensation_day (
    id INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id INT NOT NULL,
    staff_id INT NOT NULL,
    period_id INT NOT NULL,
    shift_date DATE NOT NULL,
    compensation_date DATE NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_compensation_staff
        FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_compensation_schedule_consistency
        FOREIGN KEY (schedule_id, staff_id, period_id, shift_date)
        REFERENCES schedule(id, staff_id, period_id, work_date)
        ON DELETE CASCADE,
    CONSTRAINT chk_compensation_not_same_day
        CHECK (compensation_date <> shift_date),
    UNIQUE KEY uk_compensation_schedule (schedule_id),
    UNIQUE KEY uk_compensation_staff_date (staff_id, compensation_date)
) ENGINE=InnoDB;

-- =====================================================
-- 13. SCHEDULE_EXCHANGE
-- Đổi ca giữa 2 nhân sự.
-- period_id + composite FK đảm bảo 2 ca đổi thuộc cùng kỳ và đúng người sở hữu ca.
-- =====================================================
CREATE TABLE schedule_exchange (
    id INT AUTO_INCREMENT PRIMARY KEY,
    period_id INT NOT NULL,
    requester_id INT NOT NULL,
    target_id INT NOT NULL,
    requester_schedule_id INT NOT NULL,
    target_schedule_id INT NOT NULL,
    reason TEXT,
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    reviewed_by INT NULL,
    reviewed_at TIMESTAMP NULL,
    review_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_exchange_period
        FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE CASCADE,
    CONSTRAINT fk_exchange_requester
        FOREIGN KEY (requester_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_exchange_target
        FOREIGN KEY (target_id) REFERENCES staff(id) ON DELETE CASCADE,
    CONSTRAINT fk_exchange_requester_schedule_consistency
        FOREIGN KEY (requester_schedule_id, requester_id, period_id)
        REFERENCES schedule(id, staff_id, period_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_exchange_target_schedule_consistency
        FOREIGN KEY (target_schedule_id, target_id, period_id)
        REFERENCES schedule(id, staff_id, period_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_exchange_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_exchange_different_staff
        CHECK (requester_id <> target_id),
    CONSTRAINT chk_exchange_different_schedule
        CHECK (requester_schedule_id <> target_schedule_id)
) ENGINE=InnoDB;

-- =====================================================
-- 14. ALGORITHM_CONFIG
-- Cấu hình thuật toán. value_type giúp backend validate param_value.
-- =====================================================
CREATE TABLE algorithm_config (
    param_key VARCHAR(50) PRIMARY KEY,
    param_value VARCHAR(500) NOT NULL,
    value_type ENUM('STRING', 'NUMBER', 'BOOLEAN', 'JSON') NOT NULL DEFAULT 'STRING',
    description VARCHAR(255),
    updated_by INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_algorithm_config_updated_by
        FOREIGN KEY (updated_by) REFERENCES staff(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- =====================================================
-- 15. SYSTEM_LOG
-- Log hành động hệ thống ở mức tổng quát.
-- =====================================================
CREATE TABLE system_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    staff_id INT NULL,
    action_type VARCHAR(50) NOT NULL,
    description TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_system_log_staff
        FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- =====================================================
-- 16. NOTIFICATION
-- Thông báo cho nhân sự.
-- read_at lưu thời điểm đọc.
-- =====================================================
CREATE TABLE notification (
    id INT AUTO_INCREMENT PRIMARY KEY,
    staff_id INT NOT NULL,
    title VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_staff
        FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================
-- 17. SCHEDULE_CONFLICT
-- Chi tiết xung đột lịch: trùng ca, vượt giới hạn, nghỉ phép, thiếu chuyên môn...
-- Khi tất cả conflict được resolved, backend cần cập nhật schedule.has_conflict = FALSE.
-- =====================================================
CREATE TABLE schedule_conflict (
    id INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id INT NOT NULL,
    conflict_type ENUM(
        'LEAVE_CONFLICT',
        'MAX_SHIFT_EXCEEDED',
        'BACK_TO_BACK_SHIFT',
        'SPECIALTY_MISMATCH',
        'REQUIREMENT_NOT_MET',
        'DUPLICATE_ASSIGNMENT',
        'COMPENSATION_CONFLICT',
        'OTHER'
    ) NOT NULL DEFAULT 'OTHER',
    description TEXT,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by INT NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_conflict_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule(id) ON DELETE CASCADE,
    CONSTRAINT fk_conflict_resolved_by
        FOREIGN KEY (resolved_by) REFERENCES staff(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- =====================================================
-- 18. FILE_ATTACHMENT
-- Lưu file đính kèm theo nghiệp vụ. ref_id vẫn cần backend kiểm tra tồn tại theo ref_type.
-- =====================================================
CREATE TABLE file_attachment (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ref_type ENUM('STAFF', 'LEAVE_REQUEST', 'SCHEDULE', 'SCHEDULE_EXCHANGE', 'SCHEDULE_CONFLICT') NOT NULL,
    ref_id INT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    filepath VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT NULL,
    uploaded_by INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_file_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT chk_file_size
        CHECK (file_size IS NULL OR file_size >= 0)
) ENGINE=InnoDB;

-- =====================================================
-- 19. AUDIT_HISTORY
-- Lưu lịch sử thay đổi dữ liệu quan trọng.
-- table_name dùng VARCHAR để linh hoạt cho nhiều bảng.
-- =====================================================
CREATE TABLE audit_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(50) NOT NULL,
    record_id INT NOT NULL,
    action_type ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    changed_by INT NULL,
    old_data JSON,
    new_data JSON,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_changed_by
        FOREIGN KEY (changed_by) REFERENCES staff(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- =====================================================
-- 20. ALGORITHM_METRICS
-- Kết quả chạy thuật toán theo kỳ lịch.
-- =====================================================
CREATE TABLE algorithm_metrics (
    id INT AUTO_INCREMENT PRIMARY KEY,
    period_id INT NULL,
    algorithm_type VARCHAR(50) NOT NULL,
    execution_time_ms INT NULL,
    coverage_rate DECIMAL(5,2) NULL,
    balance_score DECIMAL(5,2) NULL,
    conflict_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_algorithm_metrics_period
        FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE SET NULL,
    CONSTRAINT chk_algorithm_execution_time
        CHECK (execution_time_ms IS NULL OR execution_time_ms >= 0),
    CONSTRAINT chk_algorithm_coverage_rate
        CHECK (coverage_rate IS NULL OR (coverage_rate >= 0 AND coverage_rate <= 100)),
    CONSTRAINT chk_algorithm_balance_score
        CHECK (balance_score IS NULL OR (balance_score >= 0 AND balance_score <= 100)),
    CONSTRAINT chk_algorithm_conflict_count
        CHECK (conflict_count >= 0)
) ENGINE=InnoDB;

-- =====================================================
-- SCHEDULE TEMPLATE (M07-F10: Luu & tai su dung mau lich)
-- =====================================================

CREATE TABLE schedule_template (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    day_of_week INT NOT NULL COMMENT '1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun',
    shift_type_id VARCHAR(10) NOT NULL,
    specialty_id INT NULL,
    required_staff_count INT NOT NULL DEFAULT 1,
    is_active TINYINT(1) NOT NULL DEFAULT 1,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_schedule_template_shift_type
        FOREIGN KEY (shift_type_id) REFERENCES shift_type(id),
    CONSTRAINT fk_schedule_template_specialty
        FOREIGN KEY (specialty_id) REFERENCES specialty(id) ON DELETE SET NULL,
    CONSTRAINT chk_template_day_of_week
        CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_template_required_staff
        CHECK (required_staff_count >= 1)
) ENGINE=InnoDB;

-- =====================================================
-- HOLIDAY (M01-F04: Quan ly ngay le)
-- =====================================================

CREATE TABLE holiday (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    holiday_date DATE NOT NULL UNIQUE,
    year INT NOT NULL,
    is_national_holiday TINYINT(1) NULL,
    description TEXT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_holiday_date (holiday_date),
    INDEX idx_holiday_year (year),
    INDEX idx_holiday_active (is_active)
) ENGINE=InnoDB;

-- =====================================================
-- INDEXES
-- =====================================================
CREATE INDEX idx_staff_specialty ON staff(specialty_id);
CREATE INDEX idx_staff_active ON staff(is_active);
CREATE INDEX idx_staff_full_name ON staff(full_name);

CREATE INDEX idx_shift_type_active ON shift_type(is_active);

CREATE INDEX idx_period_status ON schedule_period(status);
CREATE INDEX idx_period_range ON schedule_period(start_date, end_date);

CREATE INDEX idx_requirement_period_date ON shift_requirement(period_id, work_date);
CREATE INDEX idx_requirement_shift_specialty ON shift_requirement(shift_type_id, specialty_id);

CREATE INDEX idx_leave_status ON leave_request(status);
CREATE INDEX idx_leave_staff_range ON leave_request(staff_id, start_date, end_date);
CREATE INDEX idx_leave_reviewed_by ON leave_request(reviewed_by);

CREATE INDEX idx_schedule_date ON schedule(work_date);
CREATE INDEX idx_schedule_staff ON schedule(staff_id);
CREATE INDEX idx_schedule_period ON schedule(period_id);
CREATE INDEX idx_schedule_period_date ON schedule(period_id, work_date);
CREATE INDEX idx_schedule_staff_date ON schedule(staff_id, work_date);
CREATE INDEX idx_schedule_requirement ON schedule(requirement_id);
CREATE INDEX idx_schedule_conflict_flag ON schedule(has_conflict);

CREATE INDEX idx_compensation_date ON compensation_day(compensation_date);
CREATE INDEX idx_compensation_staff ON compensation_day(staff_id);
CREATE INDEX idx_compensation_period ON compensation_day(period_id);
CREATE INDEX idx_compensation_schedule ON compensation_day(schedule_id);

CREATE INDEX idx_exchange_status ON schedule_exchange(status);
CREATE INDEX idx_exchange_period_status ON schedule_exchange(period_id, status);
CREATE INDEX idx_exchange_requester_status ON schedule_exchange(requester_id, status);
CREATE INDEX idx_exchange_target_status ON schedule_exchange(target_id, status);
CREATE INDEX idx_exchange_reviewed_by ON schedule_exchange(reviewed_by);

CREATE INDEX idx_algorithm_config_updated_by ON algorithm_config(updated_by);

CREATE INDEX idx_template_active ON schedule_template(is_active);
CREATE INDEX idx_template_day ON schedule_template(day_of_week);
CREATE INDEX idx_template_specialty ON schedule_template(specialty_id);

CREATE INDEX idx_system_log_staff ON system_log(staff_id);
CREATE INDEX idx_system_log_created ON system_log(created_at);
CREATE INDEX idx_system_log_action ON system_log(action_type);

CREATE INDEX idx_notification_staff_read ON notification(staff_id, is_read);
CREATE INDEX idx_notification_created ON notification(created_at);

CREATE INDEX idx_conflict_schedule ON schedule_conflict(schedule_id);
CREATE INDEX idx_conflict_type ON schedule_conflict(conflict_type);
CREATE INDEX idx_conflict_resolved ON schedule_conflict(is_resolved);
CREATE INDEX idx_conflict_resolved_by ON schedule_conflict(resolved_by);

CREATE INDEX idx_file_ref ON file_attachment(ref_type, ref_id);
CREATE INDEX idx_file_uploaded_by ON file_attachment(uploaded_by);

CREATE INDEX idx_audit_table_record ON audit_history(table_name, record_id);
CREATE INDEX idx_audit_changed_by ON audit_history(changed_by);
CREATE INDEX idx_audit_created ON audit_history(created_at);

CREATE INDEX idx_algorithm_metrics_period ON algorithm_metrics(period_id);
CREATE INDEX idx_algorithm_metrics_type ON algorithm_metrics(algorithm_type);

-- =====================================================
-- BASIC SEED DATA
-- Có thể sửa/xóa theo nhu cầu thực tế.
-- =====================================================
INSERT INTO app_role (name, description) VALUES
('ADMIN', 'Quản trị hệ thống'),
('MANAGER', 'Quản lý và duyệt lịch'),
('STAFF', 'Nhân viên sử dụng hệ thống');

INSERT INTO app_permission (name, description) VALUES
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

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p
WHERE r.name = 'ADMIN';

INSERT INTO role_permission (role_id, permission_id)
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

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.name IN (
    'SCHEDULE_READ',
    'LEAVE_REQUEST_CREATE',
    'SCHEDULE_EXCHANGE_CREATE'
)
WHERE r.name = 'STAFF';

INSERT INTO algorithm_config (param_key, param_value, value_type, description) VALUES
('MAX_SHIFTS_PER_MONTH_DEFAULT', '5', 'NUMBER', 'Số ca tối đa mặc định mỗi tháng'),
('AVOID_BACK_TO_BACK_SHIFT', 'true', 'BOOLEAN', 'Hạn chế phân công ca liên tiếp'),
('ENABLE_COMPENSATION_AFTER_L01', 'true', 'BOOLEAN', 'Tự động tính nghỉ bù sau ca L01');

-- =====================================================
-- BACKEND BUSINESS RULES THAT MUST BE VALIDATED IN SERVICE LAYER
-- =====================================================
-- 1. schedule.work_date phải nằm trong schedule_period.start_date/end_date.
-- 2. shift_requirement.work_date phải nằm trong schedule_period.start_date/end_date.
-- 3. schedule.staff_id phải có specialty phù hợp với shift_requirement.specialty_id nếu requirement_id khác NULL.
-- 4. Khi duyệt leave_request, cần kiểm tra lịch đã phân công trùng khoảng nghỉ.
-- 5. Khi duyệt schedule_exchange, cần swap staff_id hoặc swap schedule assignment theo quy tắc nghiệp vụ và ghi audit_history.
-- 6. Khi tất cả schedule_conflict của một schedule đã resolved, cập nhật schedule.has_conflict = FALSE.
-- 7. file_attachment.ref_id phải được backend kiểm tra tồn tại theo ref_type trước khi lưu.
