---
name: database-design
description: Thiết kế và tối ưu database cho dự án
---

# Skill: Database Design

## Mục tiêu
Hỗ trợ thiết kế và làm việc với database

## Các bảng chính

### Nhân sự
```sql
CREATE TABLE staff (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100) UNIQUE,
    specialty_id INT,
    max_shifts_per_month INT DEFAULT 5,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### Lịch
```sql
CREATE TABLE schedule (
    id INT PRIMARY KEY AUTO_INCREMENT,
    period_id INT NOT NULL,
    work_date DATE NOT NULL,
    staff_id INT NOT NULL,
    shift_type_id VARCHAR(10) NOT NULL, -- L01, L02, L03, L04
    requirement_id INT,
    has_conflict BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_schedule_unique (period_id, staff_id, shift_type_id, work_date)
);
```

### Ngày nghỉ bù
```sql
CREATE TABLE compensation_day (
    id INT PRIMARY KEY AUTO_INCREMENT,
    schedule_id INT NOT NULL,
    staff_id INT NOT NULL,
    period_id INT NOT NULL,
    shift_date DATE NOT NULL,
    compensation_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_compensation_staff_date (staff_id, compensation_date)
);
```

## Index Strategy

```sql
-- Cho tìm kiếm nhanh theo ngày
CREATE INDEX idx_schedule_date ON schedule(work_date);
CREATE INDEX idx_schedule_staff_date ON schedule(staff_id, work_date);

-- Cho xung đột
CREATE INDEX idx_schedule_conflict ON schedule(has_conflict);

-- Cho compensation
CREATE INDEX idx_compensation_date ON compensation_day(compensation_date);
CREATE INDEX idx_compensation_staff ON compensation_day(staff_id);
```

## Migration Checklist
- [ ] Tạo bảng với đúng charset utf8mb4
- [ ] Thêm tất cả foreign keys
- [ ] Thêm indexes cho các truy vấn thường dùng
- [ ] Seed data cho app_role, app_permission
- [ ] Test với sample data
