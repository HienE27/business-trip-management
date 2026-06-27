-- Performance optimization: Add indexes for slow queries
-- Run this script manually or via Flyway/Liquibase migration

-- Index for audit_history: commonly filtered by entity_type, action_type, and date range
-- CREATE INDEX idx_audit_entity_action_date ON audit_history(entity_type, action_type, changed_at DESC);

-- Index for shift_requirement: commonly filtered by period and work_date
-- CREATE INDEX idx_shift_req_period_date ON shift_requirement(period_id, work_date);

-- Index for notification: commonly filtered by staff_id and is_read
-- CREATE INDEX idx_notification_staff_read ON notification(staff_id, is_read);

-- Index for schedule: commonly filtered by staff and date range
-- CREATE INDEX idx_schedule_staff_date_range ON schedule(staff_id, work_date);

-- Note: Since JPA ddl-auto=update is used, these indexes should be created via Hibernate @Index annotation
-- or manually via this SQL file
