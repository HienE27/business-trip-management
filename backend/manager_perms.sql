SELECT p.name AS permission_name, p.description
FROM app_permission p
JOIN role_permission rp ON p.id = rp.permission_id
JOIN app_role r ON rp.role_id = r.id
WHERE r.name = 'MANAGER'
ORDER BY p.name;
