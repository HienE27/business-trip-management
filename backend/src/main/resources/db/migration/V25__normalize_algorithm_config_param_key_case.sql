-- =====================================================
-- V25: Normalize algorithm_config.param_key to lowercase
-- =====================================================
-- Problem: Legacy seed rows and manually created rows may
-- have UPPERCASE param_key values (e.g. 'MAX_STAFF_PER_SHIFT')
-- while ConfigMapper and all Java constants use lowercase
-- snake_case (e.g. 'max_staff_per_shift'). JPA findByParamKey()
-- does exact match, causing cache miss → silent fallback to
-- default values.
--
-- Fix: Lowercase all existing param_key values so they match
-- the Java constants used everywhere in the new config system.
--
-- Idempotent: LOWER of lowercase string = same string.
-- LOWER is deterministic — re-running is safe.
-- =====================================================

UPDATE algorithm_config SET param_key = LOWER(param_key);
