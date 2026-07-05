-- =====================================================
-- V8: Expand audit_history.action_type ENUM
-- Java AuditHistory.ActionType includes APPROVE, REJECT,
-- CANCEL, PUBLISH in addition to the original INSERT,
-- UPDATE, DELETE. The base DDL only declared 3 values, so
-- inserting an APPROVE/REJECT/CANCEL row triggers
--   Data truncated for column 'action_type'
-- Fix: extend the ENUM to cover all Java enum values.
-- =====================================================
ALTER TABLE audit_history
    MODIFY COLUMN action_type ENUM(
        'INSERT', 'UPDATE', 'DELETE',
        'APPROVE', 'REJECT', 'CANCEL', 'PUBLISH'
    ) NOT NULL;
