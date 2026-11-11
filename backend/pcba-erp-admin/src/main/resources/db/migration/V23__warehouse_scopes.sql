-- 不推断历史仓库归属，管理员在仓库档案中确认分组。
ALTER TABLE wms_warehouse ADD COLUMN warehouse_scope VARCHAR(20);
ALTER TABLE wms_warehouse ADD CONSTRAINT wms_warehouse_scope_check CHECK (warehouse_scope IS NULL OR warehouse_scope IN ('', 'ELECTRONIC', 'ACCESSORY', 'FINISHED'));
UPDATE sys_menu SET parent_id=185,order_num=0 WHERE path IN ('/wms/warehouse','wms/warehouse') AND menu_type='C';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon,create_by)
SELECT '仓库档案',185,0,'/wms/warehouse','wms/warehouse/index','C',0,0,'wms:warehouse:list','House','migration'
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=185) AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path IN ('/wms/warehouse','wms/warehouse') AND menu_type='C');
UPDATE sys_menu SET menu_name='历史配件台账' WHERE path='/fas/acc-stock' AND menu_type='C';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon,create_by)
SELECT name,1853,ord,path,component,'C',0,0,permission,icon,'migration'
FROM (VALUES
  ('配件库存',2,'/wms/accessory-inventory','wms/inventory/index','wms:inventory:list','Box'),
  ('配件入库',3,'/wms/accessory-in','wms/stock-in/index','wms:stockin:list','Download'),
  ('配件出库',4,'/wms/accessory-out','wms/stock-out/index','wms:stockout:list','Upload')
) AS x(name,ord,path,component,permission,icon)
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1853) AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.path=x.path AND m.menu_type='C');
-- 仅给已拥有相应 WMS 权限的角色添加菜单，不授予新的库存操作权限。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id, target.menu_id FROM sys_menu target
JOIN sys_menu existing ON existing.perms=target.perms AND existing.menu_id<>target.menu_id
JOIN sys_role_menu rm ON rm.menu_id=existing.menu_id
WHERE target.path IN ('/wms/accessory-inventory','/wms/accessory-in','/wms/accessory-out')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id,1853 FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE m.path IN ('/wms/accessory-inventory','/wms/accessory-in','/wms/accessory-out') AND EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=1853)
ON CONFLICT DO NOTHING;
