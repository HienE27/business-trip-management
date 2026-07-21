-- =====================================================
-- V19: Remove autoCompensationEnabled (RC-001)
-- =====================================================
-- Compensation day creation is now always-on (mandatory per PROJECT_CONTEXT.mdc).
-- The `autoCompensation_enabled` config knob has been removed from:
--   * ConfigDomain record + Builder (backend)
--   * AlgorithmConfigService + RuntimeConfigService
--   * ConfigDefaults + ConfigMapper + ConfigService
--   * ConfigMetadataRegistry toggle entry
--   * ConfigController GET/PUT + DTO
--   * FE: types.ts, presets.ts, RuntimeParamsChips.tsx, api-client.ts
--
-- This migration cleans up the persisted state:
--   1. Remove `autoCompensationEnabled` from each `config_profile.config_json`
--      so the new code path (which does not read this key) does not return stale data.
--   2. Delete the row from `algorithm_config` if present.
--
-- Idempotent: rows/keys that do not exist are no-ops.
-- =====================================================

-- 1. Strip the key from each profile's JSON payload.
--    MySQL 8 + JSON functions: JSON_REMOVE is the idiomatic way.
UPDATE config_profile
   SET config_json = JSON_REMOVE(
           config_json,
           '$.autoCompensationEnabled'
       )
 WHERE JSON_CONTAINS_PATH(config_json, 'one', '$.autoCompensationEnabled') = 1;

-- 2. Delete the algorithm_config row if it exists.
DELETE FROM algorithm_config
 WHERE param_key = 'auto_compensation_enabled';