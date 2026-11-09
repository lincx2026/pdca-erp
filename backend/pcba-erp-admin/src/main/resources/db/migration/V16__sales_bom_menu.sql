INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES (159000,'销售BOM',500,7,'sales-bom','pdm/bom/index',1,0,'C',0,0,'pdm:bom:list','Grid','system',CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id,159000 FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE m.perms='pdm:bom:list' AND rm.menu_id<>159000
ON CONFLICT DO NOTHING;
