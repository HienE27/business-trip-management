-- V16: Add run_token column to algorithm_metrics for log/DB correlation
-- Companion to the [M07-RUN] log format and AutoScheduleResponse.runToken echo —
-- every algorithm_metrics row now carries the UUID minted at the controller boundary
-- so dashboards, audit history and replay tooling can cross-reference one execution.

ALTER TABLE algorithm_metrics
    ADD COLUMN IF NOT EXISTS run_token VARCHAR(40) DEFAULT NULL AFTER total_schedules_created;

-- Lookup indexes: by-token for trace correlation, by period for newest-first listing.
CREATE INDEX IF NOT EXISTS idx_algorithm_metrics_run_token
    ON algorithm_metrics (run_token);

CREATE INDEX IF NOT EXISTS idx_algorithm_metrics_period_created
    ON algorithm_metrics (period_id, created_at);
