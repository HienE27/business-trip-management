-- =====================================================
-- V24: Remove unused min_per_week config rows
-- =====================================================
-- Background:
--   l01MinPerWeek / l02MinPerWeek / l03MinPerWeek / l04MinPerWeek
--   are NOT consumed by any scheduling algorithm (GREEDY, CSP, V10).
--   They were display/recommendation values only.
--   UI already hid them: hiddenParams = ["l01MinPerWeek"] // Reserved.
--   Scheduler never reads them at runtime.
--
-- Verification (2026-07-28):
--   * git grep backend/       -> 0 runtime consumers
--   * git grep frontend/      -> 0 display consumers (hidden)
--   * All DB read paths have safe fallback defaults (1, 2, 1, 1)
--
-- Idempotent: DELETE on non-matching rows returns 0 affected rows.
-- Safe on fresh DB (rows never inserted by Flyway).
-- =====================================================

-- 1. Strip from config_profile.config_json (seeded by V14)
UPDATE config_profile
   SET config_json = JSON_REMOVE(
           config_json,
           '$.l01MinPerWeek',
           '$.l02MinPerWeek',
           '$.l03MinPerWeek',
           '$.l04MinPerWeek'
       )
 WHERE JSON_CONTAINS_PATH(config_json, 'one',
       '$.l01MinPerWeek', '$.l02MinPerWeek',
       '$.l03MinPerWeek', '$.l04MinPerWeek') = 1;

-- 2. Drop the algorithm_config rows
DELETE FROM algorithm_config
WHERE param_key IN (
    'auto_gen_l01_min_per_week',
    'auto_gen_l02_min_per_week',
    'auto_gen_l03_min_per_week',
    'auto_gen_l04_min_per_week'
);
