-- ============================================================
-- Migration: Add staff_code column to staff table (SQL Server)
-- ============================================================

-- 1. Thêm column
ALTER TABLE staff ADD staff_code VARCHAR(20) NULL;

-- 2. Update dữ liệu cũ: set staff_code = username cho các record hiện có
UPDATE staff SET staff_code = username WHERE staff_code IS NULL;

-- 3. Set NOT NULL sau khi đã có dữ liệu
ALTER TABLE staff ALTER COLUMN staff_code VARCHAR(20) NOT NULL;

-- ============================================================
-- Verify
-- ============================================================
-- SELECT id, staff_code, username, full_name FROM staff LIMIT 10;
