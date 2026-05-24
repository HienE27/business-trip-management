-- =====================================================
-- DATABASE: Hospital Scheduler
-- =====================================================
CREATE DATABASE IF NOT EXISTS hospital_scheduler;
USE hospital_scheduler;

-- =====================================================
-- 1. SPECIALTY
-- =====================================================
CREATE TABLE specialty (
 id INT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(50) UNIQUE NOT NULL,
 description TEXT
);

-- =====================================================
-- 2. STAFF
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
 FOREIGN KEY (specialty_id) REFERENCES specialty(id)
);

-- =====================================================
-- 3. ROLE
-- =====================================================
CREATE TABLE role (
 id INT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(50) UNIQUE NOT NULL,
 description TEXT
);

-- =====================================================
-- 4. PERMISSION
-- =====================================================
CREATE TABLE permission (
 id INT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(50) UNIQUE NOT NULL,
 description TEXT
);

-- =====================================================
-- 5. ROLE_PERMISSION
-- =====================================================
CREATE TABLE role_permission (
 role_id INT NOT NULL,
 permission_id INT NOT NULL,
 PRIMARY KEY (role_id, permission_id),
 FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
 FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
);

-- =====================================================
-- 6. STAFF_ROLE
-- =====================================================
CREATE TABLE staff_role (
 staff_id INT NOT NULL,
 role_id INT NOT NULL,
 PRIMARY KEY (staff_id, role_id),
 FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
 FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

-- =====================================================
-- 7. SHIFT TYPE
-- =====================================================
CREATE TABLE shift_type (
 id VARCHAR(10) PRIMARY KEY,
 name VARCHAR(50) NOT NULL,
 description TEXT,
 start_time TIME,
 end_time TIME,
 fatigue_score INT DEFAULT 1
);

-- =====================================================
-- 8. LEAVE REQUEST
-- =====================================================
CREATE TABLE leave_request (
 id INT AUTO_INCREMENT PRIMARY KEY,
 staff_id INT NOT NULL,
 request_date DATE NOT NULL,
 reason TEXT,
 status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE
);

-- =====================================================
-- 9. SCHEDULE PERIOD
-- =====================================================
CREATE TABLE schedule_period (
 id INT AUTO_INCREMENT PRIMARY KEY,
 period_name VARCHAR(20) NOT NULL,
 status ENUM('DRAFT', 'PUBLISHED') DEFAULT 'DRAFT',
 generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 generated_by INT NULL,
 FOREIGN KEY (generated_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 10. SCHEDULE
-- =====================================================
CREATE TABLE schedule (
 id INT AUTO_INCREMENT PRIMARY KEY,
 period_id INT NOT NULL,
 work_date DATE NOT NULL,
 staff_id INT NOT NULL,
 shift_type_id VARCHAR(10) NOT NULL,
 has_conflict BOOLEAN DEFAULT FALSE,
 conflict_note TEXT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE CASCADE,
 FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
 FOREIGN KEY (shift_type_id) REFERENCES shift_type(id),
 UNIQUE KEY uk_sched_unique (period_id, staff_id, shift_type_id, work_date)
);

-- =====================================================
-- 11. COMPENSATION DAY
-- =====================================================
CREATE TABLE compensation_day (
 id INT AUTO_INCREMENT PRIMARY KEY,
 staff_id INT NOT NULL,
 shift_date DATE NOT NULL,
 compensation_date DATE NOT NULL,
 period_id INT NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE,
 FOREIGN KEY (period_id) REFERENCES schedule_period(id) ON DELETE CASCADE,
 UNIQUE KEY unique_compensation (staff_id, compensation_date)
);

-- =====================================================
-- 12. SCHEDULE EXCHANGE
-- =====================================================
CREATE TABLE schedule_exchange (
 id INT AUTO_INCREMENT PRIMARY KEY,
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
 FOREIGN KEY (requester_id) REFERENCES staff(id) ON DELETE CASCADE,
 FOREIGN KEY (target_id) REFERENCES staff(id) ON DELETE CASCADE,
 FOREIGN KEY (reviewed_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 13. ALGORITHM CONFIG (FIX: VARCHAR thay vì DOUBLE)
-- =====================================================
CREATE TABLE algorithm_config (
 param_key VARCHAR(50) PRIMARY KEY,
 param_value VARCHAR(500) NOT NULL,
 description VARCHAR(255),
 updated_by INT,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY (updated_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 14. SYSTEM LOG
-- =====================================================
CREATE TABLE system_log (
 id INT AUTO_INCREMENT PRIMARY KEY,
 staff_id INT,
 action_type VARCHAR(50) NOT NULL,
 description TEXT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 15. NOTIFICATION
-- =====================================================
CREATE TABLE notification (
 id INT AUTO_INCREMENT PRIMARY KEY,
 staff_id INT NOT NULL,
 title VARCHAR(100) NOT NULL,
 message TEXT NOT NULL,
 is_read BOOLEAN DEFAULT FALSE,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE
);

-- =====================================================
-- 16. SCHEDULE CONFLICT
-- =====================================================
CREATE TABLE schedule_conflict (
 id INT AUTO_INCREMENT PRIMARY KEY,
 schedule_id INT NOT NULL,
 conflict_type VARCHAR(50),
 description TEXT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (schedule_id) REFERENCES schedule(id) ON DELETE CASCADE
);

-- =====================================================
-- 17. FILE ATTACHMENT
-- =====================================================
CREATE TABLE file_attachment (
 id INT AUTO_INCREMENT PRIMARY KEY,
 ref_table VARCHAR(50) NOT NULL,
 ref_id INT NOT NULL,
 filename VARCHAR(255) NOT NULL,
 filepath VARCHAR(255) NOT NULL,
 uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 uploaded_by INT,
 FOREIGN KEY (uploaded_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 18. AUDIT HISTORY
-- =====================================================
CREATE TABLE audit_history (
 id INT AUTO_INCREMENT PRIMARY KEY,
 table_name VARCHAR(50) NOT NULL,
 record_id INT NOT NULL,
 action_type ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
 changed_by INT,
 old_data JSON,
 new_data JSON,
 action_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (changed_by) REFERENCES staff(id) ON DELETE SET NULL
);

-- =====================================================
-- 19. ALGORITHM METRICS
-- =====================================================
CREATE TABLE algorithm_metrics (
 id INT AUTO_INCREMENT PRIMARY KEY,
 algorithm_type VARCHAR(20),
 execution_time_ms INT,
 coverage_rate DECIMAL(5,2),
 balance_score DECIMAL(5,2),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- Indexes
-- =====================================================
CREATE INDEX idx_schedule_date ON schedule(work_date);
CREATE INDEX idx_schedule_staff ON schedule(staff_id);
CREATE INDEX idx_schedule_period ON schedule(period_id);
CREATE INDEX idx_compensation_date ON compensation_day(compensation_date);
CREATE INDEX idx_compensation_staff ON compensation_day(staff_id);
CREATE INDEX idx_exchange_status ON schedule_exchange(status);
CREATE INDEX idx_audit_table ON audit_history(table_name);
CREATE INDEX idx_audit_record ON audit_history(record_id);
CREATE INDEX idx_leave_staff_date ON leave_request(staff_id, request_date);
CREATE INDEX idx_conflict_schedule ON schedule_conflict(schedule_id);
CREATE INDEX idx_file_ref ON file_attachment(ref_table, ref_id);
