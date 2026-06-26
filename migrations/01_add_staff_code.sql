-- ============================================================
-- Migration: Add staff_code column to staff table (MySQL 8.0)
-- Database: MySQL 8.0
-- Description: Adds unique staff_code column for employee ID tracking (M01-F01)
-- ============================================================

-- 1. Add column (nullable first, then update existing data)
ALTER TABLE staff
  ADD COLUMN staff_code VARCHAR(20) NULL
  COMMENT 'Mã nhân viên duy nhất (VD: NV001)' AFTER status;

-- 2. Backfill existing records: set staff_code = CONCAT('NV', LPAD(id, 3, '0'))
-- This ensures every existing staff member gets a predictable, non-null code
UPDATE staff
   SET staff_code = CONCAT('NV', LPAD(id, 3, '0'))
 WHERE staff_code IS NULL;

-- 3. Set NOT NULL after backfill is complete
ALTER TABLE staff
  MODIFY COLUMN staff_code VARCHAR(20) NOT NULL;

-- 4. Add UNIQUE constraint (MySQL syntax)
ALTER TABLE staff
  ADD UNIQUE KEY uk_staff_code (staff_code);

-- ============================================================
-- Verify
-- ============================================================
-- SELECT id, staff_code, username, full_name FROM staff LIMIT 10;
-- Expected: all rows have non-null, unique staff_code like NV001, NV002, ...
