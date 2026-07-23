-- =====================================================
-- V20: Sync algorithm_config L01-L04 bounds with ConfigDefaults
-- =====================================================
-- Bug #001 (discovered in Gate 2 Runtime Evidence):
--   POST /api/v1/config/profiles/{id}/apply returns 422 because the profile's
--   config snapshot inherits invalid values (0) from `algorithm_config`.
--
-- ConfigDefaults specifies:
--   L0x_MIN_PER_DAY = 1, L0x_MAX_PER_DAY = 10
--   L0x_MIN_PER_WEEK = 1, L0x_MAX_PER_WEEK = 3
--
-- But the legacy seeded rows have value '0', violating
-- ConfigMetadataRegistry bounds (min ≥ 1).
--
-- This migration normalises the rows to match ConfigDefaults, restoring
-- the "snapshot → apply" round-trip.
--
-- Idempotent: only updates rows whose current value is invalid (0 or blank)
-- for the affected keys. Rows with non-default valid values are preserved.
-- =====================================================

-- L01 — Trực 24/24
UPDATE algorithm_config
   SET param_value = '1'
 WHERE param_key = 'auto_gen_l01_min_per_day'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '10'
 WHERE param_key = 'auto_gen_l01_max_per_day'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '1'
 WHERE param_key = 'auto_gen_l01_min_per_week'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '3'
 WHERE param_key = 'auto_gen_l01_max_per_week'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

-- L02 — Thông tầm
UPDATE algorithm_config
   SET param_value = '1'
 WHERE param_key = 'auto_gen_l02_min_per_day'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '10'
 WHERE param_key = 'auto_gen_l02_max_per_day'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '1'
 WHERE param_key = 'auto_gen_l02_min_per_week'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '3'
 WHERE param_key = 'auto_gen_l02_max_per_week'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

-- L03 — Phòng khám dịch vụ
UPDATE algorithm_config
   SET param_value = '1'
 WHERE param_key = 'auto_gen_l03_min_per_day'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '10'
 WHERE param_key = 'auto_gen_l03_max_per_day'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '1'
 WHERE param_key = 'auto_gen_l03_min_per_week'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '3'
 WHERE param_key = 'auto_gen_l03_max_per_week'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

-- L04 — Phòng khám chuyên gia
UPDATE algorithm_config
   SET param_value = '1'
 WHERE param_key = 'auto_gen_l04_min_per_day'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '10'
 WHERE param_key = 'auto_gen_l04_max_per_day'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '1'
 WHERE param_key = 'auto_gen_l04_min_per_week'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');

UPDATE algorithm_config
   SET param_value = '3'
 WHERE param_key = 'auto_gen_l04_max_per_week'
   AND (param_value IS NULL OR param_value = '' OR param_value = '0');