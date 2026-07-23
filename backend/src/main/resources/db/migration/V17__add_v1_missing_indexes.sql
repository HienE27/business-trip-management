-- =====================================================
-- V17: Add performance indexes originally promised by V1
-- =====================================================
-- V1 (the file is named V1 but it shipped as comments-only) left
-- performance indexes un-created. Three migrations later (V3) added
-- indexes for leave/compensation/staff/etc., but the four hot-path
-- indexes below were still missing — every query that filters by
-- (entity_type, action_type), (period, work_date), (staff, is_read)
-- or (staff, work_date) was a full-table scan.
--
-- This migration closes that gap. It is idempotent for MySQL 8+
-- (CREATE INDEX IF NOT EXISTS) and harmless if the index already
-- exists from a manual fix.
-- =====================================================

-- Audit history: filter by entity/action + date range.
-- Column names are the JPA snake_case form (table_name, action_type, created_at).
CREATE INDEX IF NOT EXISTS idx_audit_entity_action_date
    ON audit_history (table_name, action_type, created_at);

-- Shift requirement: filter by period and date
CREATE INDEX IF NOT EXISTS idx_shift_req_period_date
    ON shift_requirement (period_id, work_date);

-- Notification: filter by staff + unread
CREATE INDEX IF NOT EXISTS idx_notification_staff_read
    ON notification (staff_id, is_read);

-- Schedule: filter by staff + date range (calendar view, conflict
-- detection, fairness calculations all touch this shape)
CREATE INDEX IF NOT EXISTS idx_schedule_staff_date_range
    ON schedule (staff_id, work_date);