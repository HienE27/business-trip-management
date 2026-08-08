-- BUGFIX #47: Staff.status was set to ACTIVE while staff.is_active=0 for some
-- records (notably the "Test Manager" id=49). This leaves the two flags out
-- of sync and confuses the UI which checks both columns to decide whether a
-- staff member is selectable.
--
-- This migration:
--   1. Backfills status=INACTIVE for any staff row where is_active=0 but
--      status='ACTIVE' (handles pre-existing drift).
--   2. Adds a stored function + trigger so any future UPDATE that flips
--      is_active=0 also flips status='INACTIVE' (and vice-versa). Keeping
--      both columns consistent prevents the same drift from recurring.

-- 1. Backfill existing drift
UPDATE staff SET status = 'INACTIVE'
WHERE is_active = 0 AND status = 'ACTIVE';

-- 2. Trigger to keep is_active <-> status in sync
DROP TRIGGER IF EXISTS staff_sync_status_after_update;

DELIMITER //
CREATE TRIGGER staff_sync_status_after_update
AFTER UPDATE ON staff
FOR EACH ROW
BEGIN
    -- If is_active was set to 0 but status is still ACTIVE/ON_LEAVE, snap status.
    IF NEW.is_active = 0 AND NEW.status <> 'INACTIVE' THEN
        UPDATE staff SET status = 'INACTIVE' WHERE id = NEW.id;
    END IF;
    -- If status was set to INACTIVE but is_active=1, snap is_active.
    IF NEW.status = 'INACTIVE' AND NEW.is_active = 1 THEN
        UPDATE staff SET is_active = 0 WHERE id = NEW.id;
    END IF;
END//
DELIMITER ;