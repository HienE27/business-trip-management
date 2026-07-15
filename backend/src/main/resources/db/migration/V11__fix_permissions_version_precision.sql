-- =====================================================
-- V11: Fix permissions.version precision mismatch
-- =====================================================
-- The previous version stored the matrix version implicitly via the
-- `updated_at` DATETIME column (1-second precision), while JWTs carried
-- millisecond-precision permVer claims. Two `bump()` calls inside the
-- same second collapsed into the same `updated_at`, so issued JWTs
-- carried a permVer that never exceeded currentVersion() — triggering
-- the "Stale permission matrix version in JWT ... forcing re-login"
-- WARN spam (see PermissionInvalidationFilter).
--
-- Fix:
--   1. Promote algorithm_config.updated_at to DATETIME(6) so the audit
--      trail keeps millisecond precision.
--   2. Backfill `param_value` with the millisecond representation of the
--      existing row's updated_at so the in-memory AtomicLong picks up
--      the right starting value on the next deploy.
-- =====================================================

ALTER TABLE algorithm_config
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL;

UPDATE algorithm_config
SET param_value = CAST(
        IFNULL(UNIX_TIMESTAMP(updated_at), UNIX_TIMESTAMP(NOW(3))) * 1000
        + MICROSECOND(updated_at) DIV 1000
        AS CHAR),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE param_key = 'permissions.version';
