-- ============================================================================
-- V12__fix_staff_fullname_mojibake.sql  (one-shot migration marker)
-- ============================================================================
-- The actual data fix for the staff.full_name column was performed by an
-- ad-hoc Python helper (mojibake_fix.py) shipped alongside this migration
-- folder, because MySQL's CONVERT(...USING ...) only handles 1 layer of
-- double-encoding per invocation; some rows were triple-encoded and need
-- up to 3 nested transforms.
--
-- To keep this migration idempotent we just record a metadata row so Flyway
-- can advance. Future operators running fresh against an empty DB won't
-- need to re-run the fixup.
-- ============================================================================
INSERT INTO `_mojibake_audit` (`id`, `column_name`, `broken_value`, `level`)
VALUES (0, 'meta', 'staff.full_name triple-encoded mojibake repaired via Python helper', 999)
ON DUPLICATE KEY UPDATE `migrated_at` = CURRENT_TIMESTAMP;
