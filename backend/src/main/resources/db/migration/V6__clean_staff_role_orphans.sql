-- V6: Clean orphan staff_role rows + add FK constraint
-- Lỗi "No row with the given identifier exists" xảy ra khi staff_role có role_id
-- không tồn tại trong app_role (FK chưa được khai báo trước đó).

-- Step 1: Xóa orphan rows (staff_role không có role tương ứng trong app_role)
DELETE sr FROM staff_role sr
LEFT JOIN app_role ar ON ar.id = sr.role_id
WHERE ar.id IS NULL;

-- Step 2: Xóa orphan rows (staff_role không có staff tương ứng trong staff)
DELETE sr FROM staff_role sr
LEFT JOIN staff s ON s.id = sr.staff_id
WHERE s.id IS NULL;

-- Step 3: Thêm FK constraint để chặn orphan rows phát sinh trong tương lai
-- Chỉ thêm nếu chưa có (kiểm tra qua information_schema)
SET @fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'staff_role'
      AND CONSTRAINT_NAME = 'fk_staff_role_role'
);

SET @sql := IF(@fk_exists = 0,
    'ALTER TABLE staff_role ADD CONSTRAINT fk_staff_role_role FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE CASCADE',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_staff_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'staff_role'
      AND CONSTRAINT_NAME = 'fk_staff_role_staff'
);

SET @sql := IF(@fk_staff_exists = 0,
    'ALTER TABLE staff_role ADD CONSTRAINT fk_staff_role_staff FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE CASCADE',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;