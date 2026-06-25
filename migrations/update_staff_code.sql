-- ============================================================
-- Migration: Update existing staff records with staff_code
-- Database: MySQL 8.0
-- Run this AFTER add_staff_code.sql
-- ============================================================

UPDATE staff
SET staff_code = CONCAT('NV', LPAD(id, 3, '0'))
WHERE staff_code IS NULL OR staff_code = '';
