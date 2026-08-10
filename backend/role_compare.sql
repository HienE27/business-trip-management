-- 1. Role count summary
SELECT r.name AS role, COUNT(*) AS perm_count
FROM role_permission rp
JOIN app_role r ON rp.role_id = r.id
GROUP BY r.name
ORDER BY r.name;

-- 2. Permissions ONLY ADMIN has
SELECT p.name AS admin_only
FROM app_permission p
WHERE p.id IN (
    SELECT permission_id FROM role_permission rp
    JOIN app_role r ON rp.role_id=r.id WHERE r.name='ADMIN'
) AND p.id NOT IN (
    SELECT permission_id FROM role_permission rp
    JOIN app_role r ON rp.role_id=r.id WHERE r.name IN ('MANAGER','STAFF')
)
ORDER BY p.name;

-- 3. Permissions ONLY MANAGER has (not in STAFF)
SELECT p.name AS manager_only
FROM app_permission p
WHERE p.id IN (
    SELECT permission_id FROM role_permission rp
    JOIN app_role r ON rp.role_id=r.id WHERE r.name='MANAGER'
) AND p.id NOT IN (
    SELECT permission_id FROM role_permission rp
    JOIN app_role r ON rp.role_id=r.id WHERE r.name='STAFF'
)
ORDER BY p.name;

-- 4. Permissions ONLY STAFF has (unusual - typically none)
SELECT p.name AS staff_only
FROM app_permission p
WHERE p.id IN (
    SELECT permission_id FROM role_permission rp
    JOIN app_role r ON rp.role_id=r.id WHERE r.name='STAFF'
) AND p.id NOT IN (
    SELECT permission_id FROM role_permission rp
    JOIN app_role r ON rp.role_id=r.id WHERE r.name IN ('MANAGER','ADMIN')
)
ORDER BY p.name;

-- 5. ALL permissions (for reference)
SELECT id, name, description FROM app_permission ORDER BY name;
