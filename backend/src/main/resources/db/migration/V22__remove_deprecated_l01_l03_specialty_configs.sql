-- =====================================================
-- V22: Remove deprecated L01/L02/L03 specialty configs
-- =====================================================
-- Background:
--   Three uppercase rows were originally seeded by the legacy SQL seed
--   (hospital_scheduler_business_final.sql) but have no Java reader:
--
--     * AUTO_GEN_L01_ALLOWED_SPECIALTIES
--     * AUTO_GEN_L02_ALLOWED_SPECIALTIES
--     * AUTO_GEN_L03_ALLOWED_SPECIALTIES
--
--   Only AUTO_GEN_L04_ALLOWED_SPECIALTIES is consumed by
--   AutoGenConfigService and AlgorithmConfigService. L01/L02/L03
--   eligibility is fixed to ALL_ELIGIBLE_SPECIALTIES (6 khoa) and does
--   not require any DB-level configuration.
--
-- Verification (2026-07-24):
--   * git grep across backend/  -> 0 matches
--   * git grep across frontend/ -> 0 matches
--   * git grep across tests/    -> 0 matches
--   * git grep tracked files    -> only docs/evidence/gate2/smoke/*.json
--     (historical snapshots — preserved unchanged to keep gate2 evidence
--      reproducible)
--
-- Idempotent: DELETE on non-matching rows returns 0 affected rows and
-- succeeds. Safe on fresh DB (rows never inserted by Flyway).
-- =====================================================

DELETE FROM algorithm_config
WHERE param_key IN (
    'AUTO_GEN_L01_ALLOWED_SPECIALTIES',
    'AUTO_GEN_L02_ALLOWED_SPECIALTIES',
    'AUTO_GEN_L03_ALLOWED_SPECIALTIES'
);
