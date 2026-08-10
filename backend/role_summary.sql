SELECT r.name AS role, COUNT(*) AS perm_count
FROM role_permission rp
JOIN app_role r ON rp.role_id = r.id
GROUP BY r.name
ORDER BY r.name;
