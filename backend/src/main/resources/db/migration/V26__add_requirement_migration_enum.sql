-- =====================================================
-- V26: Add REQUIREMENT_MIGRATION_NULL_TO_ANY to audit_history.action_type ENUM.
-- Java AuditHistory.ActionType now includes this value (matches existing orphan
-- rows that previously crashed Hibernate Enum.valueOf on read).
-- =====================================================
ALTER TABLE audit_history
    MODIFY COLUMN action_type ENUM(
        'INSERT', 'UPDATE', 'DELETE',
        'APPROVE', 'REJECT', 'CANCEL', 'PUBLISH',
        'BULK_DELETE', 'BULK_UPDATE',
        'REQUIREMENT_MIGRATION_NULL_TO_ANY'
    ) NOT NULL;
