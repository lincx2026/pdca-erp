-- Earlier submissions only updated the header. Recover unconverted lines on approved requests.
UPDATE scm_purchase_requisition_line l SET status = 2
FROM scm_purchase_requisition r
WHERE r.requisition_id = l.requisition_id AND r.status = 2
  AND l.status IN (0, 1) AND l.converted_po_id IS NULL AND COALESCE(l.converted_qty, 0) = 0;

-- A role that already has warehouse出库 access also needs the sales parent to see the new shortcut.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, parent.menu_id
FROM sys_role_menu rm
JOIN sys_menu child ON child.menu_id = rm.menu_id AND child.path = '/sal/stock-out'
JOIN sys_menu parent ON parent.menu_id = child.parent_id AND parent.path = 'sal'
ON CONFLICT DO NOTHING;
