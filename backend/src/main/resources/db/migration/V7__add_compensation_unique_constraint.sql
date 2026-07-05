-- V7: Add UNIQUE constraint uk_compensation_staff_date
-- Lỗi: JPA entity CompensationDay thiếu @UniqueConstraint ở @Table annotation
-- trong khi spec nghiệp vụ yêu cầu mỗi nhân sự chỉ có 1 ngày nghỉ bù.
-- File SQL gốc `hospital_scheduler_business_final.sql` đã có constraint
-- nhưng migration Flyway chưa apply cho các DB đã tồn tại.

-- Step 1: Xóa duplicate (giữ lại bản ghi cũ nhất) trước khi tạo UNIQUE
-- Đây là bảo vệ data-integrity; nếu DB đã clean, query này không ảnh hưởng.
DELETE cd1 FROM compensation_day cd1
INNER JOIN compensation_day cd2
    ON cd1.staff_id = cd2.staff_id
    AND cd1.compensation_date = cd2.compensation_date
    AND cd1.id > cd2.id;

-- Step 2: Thêm UNIQUE constraint nếu chưa có (idempotent)
SET @uq_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'compensation_day'
      AND CONSTRAINT_NAME = 'uk_compensation_staff_date'
);

SET @sql := IF(@uq_exists = 0,
    'ALTER TABLE compensation_day ADD CONSTRAINT uk_compensation_staff_date UNIQUE (staff_id, compensation_date)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
