-- Reuse the existing maintenance pages and their button permissions, without duplicate routes.
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, menu_type, visible, status, icon, create_by)
SELECT '基础资料', 0, 3, 'base-data', 'M', 0, 0, 'Collection', 'migration'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'base-data' AND menu_type = 'M'
);

UPDATE sys_menu
SET parent_id = (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id = 0 AND path = 'base-data' AND menu_type = 'M'),
    order_num = CASE WHEN path = '/scm/supplier' THEN 1 ELSE 2 END
WHERE menu_type = 'C' AND path IN ('/scm/supplier', '/sal/customer')
  AND (parent_id, order_num) IS DISTINCT FROM (
      (SELECT MIN(menu_id) FROM sys_menu WHERE parent_id = 0 AND path = 'base-data' AND menu_type = 'M'),
      CASE WHEN path = '/scm/supplier' THEN 1 ELSE 2 END
  );

-- Grant only the new parent directory to roles that already have either child page.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rights.role_id, root.menu_id
FROM sys_role_menu rights
JOIN sys_menu child ON child.menu_id = rights.menu_id
CROSS JOIN sys_menu root
WHERE child.menu_type = 'C' AND child.path IN ('/scm/supplier', '/sal/customer')
  AND root.parent_id = 0 AND root.path = 'base-data' AND root.menu_type = 'M'
ON CONFLICT DO NOTHING;
