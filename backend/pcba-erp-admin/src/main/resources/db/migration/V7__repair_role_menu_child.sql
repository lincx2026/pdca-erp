-- The migrated MenuMgmt duplicate turns the role page into a submenu targeting
-- /system/role/menu, which is not a frontend route. Hide only this obsolete
-- duplicate; retain its ID, permission grants and historical button children.
UPDATE sys_menu legacy
SET visible = 1
WHERE legacy.menu_id = 5 AND legacy.parent_id = 3
  AND legacy.menu_name = 'MenuMgmt' AND legacy.menu_type = 'C'
  AND legacy.path = 'menu' AND legacy.component = 'system/menu/index'
  AND legacy.perms = 'system:menu:list'
  AND legacy.visible = 0
  AND EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3
              AND menu_type = 'C' AND path = '/system/role')
  AND EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 4
              AND menu_type = 'C' AND path = '/system/menu'
              AND visible = 0 AND status = 0);
