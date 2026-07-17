-- =====================================================
-- V18: Remove legacy SQL seed rows from algorithm_config
-- =====================================================
-- Bug #11 (RC-003): Three rows shipped in the original seed file
-- (hospital_scheduler_business_final.sql, lines 605-607) but have no
-- Java reader and no functional effect:
--
--   * MAX_SHIFTS_PER_MONTH_DEFAULT
--   * AVOID_BACK_TO_BACK_SHIFT
--   * ENABLE_COMPENSATION_AFTER_L01
--
-- Replacement:
--   * MAX_SHIFTS_PER_MONTH_DEFAULT   -> l0XMaxPerMonth (per shift type)
--   * AVOID_BACK_TO_BACK_SHIFT       -> l0XMinRestHours (per shift type)
--   * ENABLE_COMPENSATION_AFTER_L01  -> pending RC-001 (PO decision pending)
--
-- Verified 0 Java consumer on 2026-07-17 (grep across backend/*.java).
--
-- Idempotent: DELETE on non-matching rows returns 0 affected rows and
-- succeeds. Safe on fresh DB (rows never inserted by Flyway).
-- =====================================================

DELETE FROM algorithm_config
WHERE param_key IN (
    'MAX_SHIFTS_PER_MONTH_DEFAULT',
    'AVOID_BACK_TO_BACK_SHIFT',
    'ENABLE_COMPENSATION_AFTER_L01'
);