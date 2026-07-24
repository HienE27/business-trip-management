--
-- V12: Remove dead config `min_staff_per_shift`
-- Sprint 1A / Commit 1 — minStaffPerShift cleanup
--
-- `minStaffPerShift` đã bị xoá khỏi Java backend, frontend, và
-- không được scheduler nào đọc. `requiredStaffCount` đã bao quát
-- nghiệp vụ này. Row cũ trong DB không gây lỗi nhưng gây nhiễu.
--
-- Idempotent: nếu row không tồn tại thì không ảnh hưởng.
--

DELETE FROM algorithm_config
WHERE param_key = 'min_staff_per_shift';
