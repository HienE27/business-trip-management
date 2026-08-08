-- BUGFIX #53: leave_request.version was NULL for records created before the
-- @Version column was added. Hibernate's Versioning.increment() then throws
-- NPE on update whenever the leave request is modified (e.g. approve/reject).
--
-- This migration:
--   1. Backfills version=0 for any existing NULL rows.
--   2. Sets the column to NOT NULL DEFAULT 0 so future inserts without an
--      explicit version still satisfy the optimistic-lock contract.

UPDATE leave_request SET version = 0 WHERE version IS NULL;

ALTER TABLE leave_request
    MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;
