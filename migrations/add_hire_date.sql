-- Add hire_date column to staff table
-- This column tracks when each staff member was hired (ngày vào làm)
ALTER TABLE staff
ADD COLUMN hire_date DATETIME NULL
COMMENT 'Ngày vào làm của nhân sự'
AFTER updated_at;
