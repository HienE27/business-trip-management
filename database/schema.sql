CREATE DATABASE IF NOT EXISTS hospital_scheduler;

USE hospital_scheduler;

-- =====================================================
-- 1. STAFF
-- =====================================================

CREATE TABLE staff (

    id INT AUTO_INCREMENT PRIMARY KEY,

    username VARCHAR(50)
    NOT NULL UNIQUE,

    password_hash VARCHAR(255)
    NOT NULL,

    full_name VARCHAR(100)
    NOT NULL,

    role ENUM(
        'ADMIN',
        'STAFF'
    ) DEFAULT 'STAFF',

    specialty VARCHAR(50)
    NOT NULL,

    max_shifts_per_month INT
    DEFAULT 5,

    phone VARCHAR(20),

    email VARCHAR(100),

    is_active BOOLEAN
    DEFAULT TRUE,

    created_at TIMESTAMP
    DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 2. SHIFT TYPE
-- =====================================================

CREATE TABLE shift_type (

    id VARCHAR(10)
    PRIMARY KEY,

    name VARCHAR(50)
    NOT NULL,

    description TEXT,

    start_time TIME,

    end_time TIME,

    fatigue_score INT
    DEFAULT 1
);

-- =====================================================
-- 3. LEAVE REQUEST
-- =====================================================

CREATE TABLE leave_request (

    id INT AUTO_INCREMENT PRIMARY KEY,

    staff_id INT NOT NULL,

    request_date DATE NOT NULL,

    reason TEXT,

    status ENUM(
        'PENDING',
        'APPROVED',
        'REJECTED'
    ) DEFAULT 'PENDING',

    created_at TIMESTAMP
    DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (staff_id)
    REFERENCES staff(id)
    ON DELETE CASCADE
);

-- =====================================================
-- 4. SCHEDULE PERIOD
-- =====================================================

CREATE TABLE schedule_period (

    id INT AUTO_INCREMENT PRIMARY KEY,

    period_name VARCHAR(20)
    NOT NULL,

    status ENUM(
        'DRAFT',
        'PUBLISHED'
    ) DEFAULT 'DRAFT',

    generated_at TIMESTAMP
    DEFAULT CURRENT_TIMESTAMP,

    generated_by VARCHAR(50)
);

-- =====================================================
-- 5. SCHEDULE
-- =====================================================

CREATE TABLE schedule (

    id INT AUTO_INCREMENT PRIMARY KEY,

    period_id INT NOT NULL,

    work_date DATE NOT NULL,

    staff_id INT,

    shift_type_id VARCHAR(10)
    NOT NULL,

    has_conflict BOOLEAN
    DEFAULT FALSE,

    conflict_note TEXT,

    created_at TIMESTAMP
    DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (period_id)
    REFERENCES schedule_period(id)
    ON DELETE CASCADE,

    FOREIGN KEY (staff_id)
    REFERENCES staff(id)
    ON DELETE CASCADE,

    FOREIGN KEY (shift_type_id)
    REFERENCES shift_type(id)

    -- KHÔNG dùng UNIQUE KEY
    -- để cho phép lưu draft schedule lỗi
);

-- =====================================================
-- 6. ALGORITHM CONFIG
-- =====================================================

CREATE TABLE algorithm_config (

    param_key VARCHAR(50)
    PRIMARY KEY,

    param_value DOUBLE
    NOT NULL,

    description VARCHAR(255)
);

-- =====================================================
-- 7. SYSTEM LOG
-- =====================================================

CREATE TABLE system_log (

    id INT AUTO_INCREMENT PRIMARY KEY,

    action_user VARCHAR(50)
    NOT NULL,

    action_type VARCHAR(50)
    NOT NULL,

    description TEXT,

    created_at TIMESTAMP
    DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 8. NOTIFICATION
-- =====================================================

CREATE TABLE notification (

    id INT AUTO_INCREMENT PRIMARY KEY,

    staff_id INT NOT NULL,

    title VARCHAR(100)
    NOT NULL,

    message TEXT
    NOT NULL,

    is_read BOOLEAN
    DEFAULT FALSE,

    created_at TIMESTAMP
    DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (staff_id)
    REFERENCES staff(id)
    ON DELETE CASCADE
);