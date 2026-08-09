-- V27: Clean orphan schedule_exchange rows + add FK constraints
--
-- Symptom on startup:
--   WARN  o.h.t.s.i.ExceptionHandlerLoggedImpl - GenerationTarget encountered
--   exception accepting command : Error executing DDL
--     "alter table schedule_exchange add constraint FKrhe76fvm025j19835u8brjala
--      foreign key (requester_schedule_id) references schedule (id)"
--   [Cannot add or update a child row: a foreign key constraint fails
--    (`hospital_scheduler`.`#sql-XXXX_X`,
--     CONSTRAINT `FKrhe76fvm025j19835u8brjala` FOREIGN KEY (`requester_schedule_id`)
--     REFERENCES `schedule` (`id`))]
--
-- Root cause: schedule_exchange.requester_schedule_id and
-- target_schedule_id reference schedule(id) but some historical rows
-- point to schedules that have since been deleted (cascading purge
-- of test periods, etc.). JPA's ddl-auto=update then fails to backfill
-- the FK because the orphan rows block constraint creation.
--
-- Fix:
--   1. Drop orphan rows where (requester|target)_schedule_id no longer
--      resolves to schedule.id.
--   2. Drop the FK constraints if they already exist (from a partial
--      previous attempt), then re-add them as proper ON DELETE CASCADE
--      so future schedule deletes clean up exchanges automatically.

-- ── Step 1: delete orphan exchange rows ───────────────────────────────────
DELETE se
FROM schedule_exchange se
LEFT JOIN schedule s ON s.id = se.requester_schedule_id
WHERE se.requester_schedule_id IS NOT NULL
  AND s.id IS NULL;

DELETE se
FROM schedule_exchange se
LEFT JOIN schedule s ON s.id = se.target_schedule_id
WHERE se.target_schedule_id IS NOT NULL
  AND s.id IS NULL;

-- ── Step 2: drop FKs if they exist (idempotent re-run) ────────────────────
SET @fk_req_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'schedule_exchange'
      AND CONSTRAINT_NAME = 'fk_schedule_exchange_requester_schedule'
);
SET @sql := IF(@fk_req_exists > 0,
    'ALTER TABLE schedule_exchange DROP FOREIGN KEY fk_schedule_exchange_requester_schedule',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_tgt_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'schedule_exchange'
      AND CONSTRAINT_NAME = 'fk_schedule_exchange_target_schedule'
);
SET @sql := IF(@fk_tgt_exists > 0,
    'ALTER TABLE schedule_exchange DROP FOREIGN KEY fk_schedule_exchange_target_schedule',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── Step 3: add FKs with ON DELETE CASCADE so future purges stay clean ───
ALTER TABLE schedule_exchange
    ADD CONSTRAINT fk_schedule_exchange_requester_schedule
    FOREIGN KEY (requester_schedule_id) REFERENCES schedule(id) ON DELETE CASCADE;

ALTER TABLE schedule_exchange
    ADD CONSTRAINT fk_schedule_exchange_target_schedule
    FOREIGN KEY (target_schedule_id) REFERENCES schedule(id) ON DELETE CASCADE;