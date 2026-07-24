--
-- V11: Migrate l04CrossSpecialty (boolean) → l04BalanceStrategy (string)
--
-- Converts the legacy boolean `auto_gen_l04_cross_specialty` to the
-- new string enum `auto_gen_l04_balance_strategy`:
--   true  → 'FAIR_DISTRIBUTE' (cross-specialty allowed at full ratio)
--   false → 'STRICT_MATCH_ONLY' (strict specialty match only)
--
-- Only sets the strategy row when it is missing or empty.
--

UPDATE algorithm_config target
JOIN algorithm_config old
  ON old.param_key = 'auto_gen_l04_cross_specialty'
SET target.param_value = CASE
    WHEN old.param_value = 'true' THEN 'FAIR_DISTRIBUTE'
    ELSE 'STRICT_MATCH_ONLY'
END
WHERE target.param_key = 'auto_gen_l04_balance_strategy'
  AND (target.param_value IS NULL OR target.param_value = '')
  AND old.param_value IS NOT NULL;
