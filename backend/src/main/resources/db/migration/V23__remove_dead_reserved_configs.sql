-- =====================================================
-- V23: Remove DEAD/RESERVED algorithm_config keys
-- =====================================================
-- Background:
--   Two rows persisted in algorithm_config have no functional effect:
--
--     * overnight_recovery_hours
--     * greedy_coverage_threshold
--
--   CONFIRMED 0 runtime consumers (verification 2026-07-24):
--     * overnight_recovery_hours — loaded into ConfigDomain but never read
--       for any constraint decision. ADR-002 (CONFIG_ADMIN_DEEP_AUDIT_v2.md
--       line 1597) marked this as DEAD.
--     * greedy_coverage_threshold — only used by a log statement in
--       AutoSchedulingService with a comment that says explicitly
--       "logging purposes only - we always fill every requirement".
--       ADR-002 marked this as DEAD.
--
-- Properties cleanup (front-end support):
--   application.properties contains 8 keys under `scheduling.search.*` that
--   have NO @Value reader (grep 2026-07-24). These are duplicates of the
--   lowercase `scheduling_*` keys that ARE consumed via
--   AlgorithmConfigService.syncDescriptions().
--
-- Frontend cleanup:
--   * Removed from PARAM_GROUPS (internal group), validation, reference,
--     presets, and create-config modal.
--   * RuntimeConfig type still includes both fields to preserve DTO
--     compatibility with /api/v1/config/runtime — loaders return defaults
--     on absence. Cached/stale profiles deserialise without runtime error.
--
-- Profile JSON hygiene:
--   config_profile.config_json seeded by V14 contains 3 dead keys:
--     - overnightRecoveryHours
--     - greedyCoverageThreshold
--     - autoCompensationEnabled (already removed by V19 in some rows)
--   Strip them so subsequent GET /api/v1/config/profiles payloads do not
--   carry stale fields that the frontend no longer renders.
--
-- Idempotent: DELETE on non-matching rows returns 0 affected rows and
-- succeeds. JSON_REMOVE on a non-existent path is a no-op. Safe on fresh
-- DB (rows never inserted by Flyway).
-- =====================================================

-- 1. Strip the dead keys from every config_profile.config_json payload.
--    JSON_REMOVE on a non-existent path is a no-op, so this works whether
--    the column holds a V14-seeded profile or a user-edited one.
UPDATE config_profile
   SET config_json = JSON_REMOVE(
           config_json,
           '$.overnightRecoveryHours',
           '$.greedyCoverageThreshold',
           '$.autoCompensationEnabled'
       )
 WHERE JSON_CONTAINS_PATH(config_json, 'one', '$.overnightRecoveryHours') = 1
    OR JSON_CONTAINS_PATH(config_json, 'one', '$.greedyCoverageThreshold') = 1
    OR JSON_CONTAINS_PATH(config_json, 'one', '$.autoCompensationEnabled') = 1;

-- 2. Drop the algorithm_config rows.
DELETE FROM algorithm_config
WHERE param_key IN (
    'overnight_recovery_hours',
    'greedy_coverage_threshold'
);
