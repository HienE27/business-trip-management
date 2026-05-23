CREATE DATABASE IF NOT EXISTS hospital_scheduler;
USE hospital_scheduler;

-- =====================================================
-- 1. SPECIALTY (Chuyên khoa)
-- =====================================================
CREATE TABLE specialty (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =====================================================
-- 2. STAFF (Nhân sự) - M01
-- =====================================================
CREATE TABLE staff (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    specialty_id INT,
    max_shifts_per_month INT DEFAULT 5,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (specialty_id) REFERENCES specialty(id) ON DELETE SET NULL
);

-- =====================================================
-- 3. ROLE (Vai trò) - M01-F05
-- =====================================================
CREATE TABLE role (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =====================================================
-- 4. PERMISSION (Quyền hạn) - M01-F05
-- =====================================================
CREATE TABLE permission (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 5. ROLE_PERMISSION (Liên kết Role - Permission)
-- =====================================================
CREATE TABLE role_permission (
    role_id INT NOT NULL,
    permission_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
);

-- =====================================================
-- 6. STAFF_ROLE (Nhân sự - Vai trò)
-- =====================================================
CREATE TABLE staff_role (
    staff_id INT NOT NULL,
    role_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (staff_id, role_id),
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

-- =====================================================
-- 7. SHIFT_TYPE (Loại lịch L01-L04) - 1.2
-- =====================================================
CREATE TABLE shift_type (
    id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    start_time TIME,
    end_time TIME,
    fatigue_score INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =====================================================
-- 8. LEAVE_REQUEST (Đơn xin nghỉ phép)
-- =====================================================
CREATE TABLE leave_request (
    id INT AUTO_INCREMENT PRIMARY KEY,
    staff_id INT NOT NULL,
    request_date DATE NOT NULL,
    end_date DATE,
    reason TEXT,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    reviewed_by INT,
    reviewed_at TIMESTAMP,
    review_note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewed_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 9. SCHEDULE_PERIOD (Kỳ trực/tháng) - M02-M05
-- =====================================================
CREATE TABLE schedule_period (
    id INT AUTO_INCREMENT PRIMARY KEY,
    period_name VARCHAR(20) NOT NULL,
    status ENUM('DRAFT', 'PUBLISHED') DEFAULT 'DRAFT',
    generated_by INT,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (generated_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 10. SCHEDULE (Lịch trực chính) - M02-M05
-- =====================================================
CREATE TABLE schedule (
    id INT AUTO_INCREMENT PRIMARY KEY,
    period_id INT NOT NULL,
    work_date DATE NOT NULL,
    staff_id INT NOT NULL,
    shift_type_id VARCHAR(10) NOT NULL,
    has_conflict BOOLEAN DEFAULT FALSE,
    conflict_note TEXT,
    notes TEXT,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE CASCADE,
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    FOREIGN KEY (shift_type_id) REFERENCES shift_type(id),
    FOREIGN KEY (created_by) REFERENCES staff(id) ON DELETE SET NULL,
    UNIQUE KEY unique_schedule (period_id, work_date, staff_id, shift_type_id)
);

-- =====================================================
-- 11. COMPENSATION_DAY (Ngày nghỉ bù) - M02-F06, 1.4
-- =====================================================
CREATE TABLE compensation_day (
    id INT AUTO_INCREMENT PRIMARY KEY,
    staff_id INT NOT NULL,
    shift_date DATE NOT NULL,
    compensation_date DATE NOT NULL,
    period_id INT NOT NULL,
    is_locked BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE CASCADE,
    UNIQUE KEY unique_compensation (staff_id, compensation_date)
);

-- =====================================================
-- 12. SCHEDULE_EXCHANGE (Đổi ngày trực) - M02-F04
-- =====================================================
CREATE TABLE schedule_exchange (
    id INT AUTO_INCREMENT PRIMARY KEY,
    period_id INT NOT NULL,
    requester_id INT NOT NULL,
    target_id INT NOT NULL,
    requester_shift_date DATE NOT NULL,
    target_shift_date DATE NOT NULL,
    reason TEXT,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    reviewed_by INT,
    reviewed_at TIMESTAMP,
    review_note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE CASCADE,
    FOREIGN KEY (requester_id) REFERENCES staff(id) ON DELETE CASCADE,
    FOREIGN KEY (target_id) REFERENCES staff(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewed_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 13. SCHEDULE_CONFLICT (Xung đột lịch) - M02-F02, M03-F02
-- =====================================================
CREATE TABLE schedule_conflict (
    id INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id INT NOT NULL,
    conflict_type VARCHAR(50) NOT NULL,
    description TEXT,
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_by INT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (schedule_id) REFERENCES schedule(id) ON DELETE CASCADE,
    FOREIGN KEY (resolved_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 14. ALGORITHM_CONFIG (Cấu hình thuật toán) - M07-F01
-- =====================================================
CREATE TABLE algorithm_config (
    param_key VARCHAR(50) PRIMARY KEY,
    param_value DOUBLE NOT NULL,
    description VARCHAR(255),
    updated_by INT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (updated_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 15. SYSTEM_LOG (Nhật ký hệ thống) - M06-F05
-- =====================================================
CREATE TABLE system_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    staff_id INT,
    action_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id INT,
    description TEXT,
    old_data JSON,
    new_data JSON,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 16. NOTIFICATION (Thông báo) - M02-F01, M03-F01
-- =====================================================
CREATE TABLE notification (
    id INT AUTO_INCREMENT PRIMARY KEY,
    staff_id INT NOT NULL,
    title VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    notification_type ENUM('INFO', 'WARNING', 'CONFLICT', 'SUCCESS') DEFAULT 'INFO',
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE
);

-- =====================================================
-- 17. STAFF_EXCLUSION (Nhân sự ngoại lệ) - M07-F01
-- =====================================================
CREATE TABLE staff_exclusion (
    id INT AUTO_INCREMENT PRIMARY KEY,
    staff_id INT NOT NULL,
    period_id INT NOT NULL,
    reason VARCHAR(255),
    exclusion_type ENUM('LEAVE', 'HOLIDAY', 'MANUAL') DEFAULT 'MANUAL',
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
    FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES staff(id) ON DELETE SET NULL,
    UNIQUE KEY unique_exclusion (staff_id, period_id)
);

-- =====================================================
-- INDEXES for Performance
-- =====================================================

-- Schedule indexes
CREATE INDEX idx_schedule_date ON schedule(work_date);
CREATE INDEX idx_schedule_staff ON schedule(staff_id);
CREATE INDEX idx_schedule_period ON schedule(period_id);
CREATE INDEX idx_schedule_shift_type ON schedule(shift_type_id);
CREATE INDEX idx_schedule_conflict ON schedule(has_conflict);

-- Compensation indexes
CREATE INDEX idx_compensation_date ON compensation_day(compensation_date);
CREATE INDEX idx_compensation_staff ON compensation_day(staff_id);
CREATE INDEX idx_compensation_shift ON compensation_day(shift_date);

-- Exchange indexes
CREATE INDEX idx_exchange_status ON schedule_exchange(status);
CREATE INDEX idx_exchange_requester ON schedule_exchange(requester_id);
CREATE INDEX idx_exchange_target ON schedule_exchange(target_id);

-- Conflict indexes
CREATE INDEX idx_conflict_schedule ON schedule_conflict(schedule_id);
CREATE INDEX idx_conflict_type ON schedule_conflict(conflict_type);
CREATE INDEX idx_conflict_unresolved ON schedule_conflict(is_resolved);

-- Leave request indexes
CREATE INDEX idx_leave_staff ON leave_request(staff_id);
CREATE INDEX idx_leave_status ON leave_request(status);
CREATE INDEX idx_leave_date ON leave_request(request_date);

-- Staff & Role indexes
CREATE INDEX idx_staff_active ON staff(is_active);
CREATE INDEX idx_staff_specialty ON staff(specialty_id);
CREATE INDEX idx_role_perm_role ON role_permission(role_id);
CREATE INDEX idx_role_perm_perm ON role_permission(permission_id);
CREATE INDEX idx_staff_role_staff ON staff_role(staff_id);
CREATE INDEX idx_staff_role_role ON staff_role(role_id);

-- Notification indexes
CREATE INDEX idx_notification_staff ON notification(staff_id);
CREATE INDEX idx_notification_unread ON notification(is_read);
CREATE INDEX idx_notification_created ON notification(created_at);

-- Exclusion indexes
CREATE INDEX idx_exclusion_staff ON staff_exclusion(staff_id);
CREATE INDEX idx_exclusion_period ON staff_exclusion(period_id);

-- System log indexes
CREATE INDEX idx_log_staff ON system_log(staff_id);
CREATE INDEX idx_log_action ON system_log(action_type);
CREATE INDEX idx_log_entity ON system_log(entity_type, entity_id);
CREATE INDEX idx_log_timestamp ON system_log(created_at);
