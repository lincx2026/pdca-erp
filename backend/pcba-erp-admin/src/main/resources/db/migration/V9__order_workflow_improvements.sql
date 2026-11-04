CREATE SEQUENCE IF NOT EXISTS pmm_work_order_number_seq;
CREATE SEQUENCE IF NOT EXISTS scm_requisition_number_seq;
CREATE SEQUENCE IF NOT EXISTS wms_stock_out_number_seq;
CREATE TABLE IF NOT EXISTS document_number_counter (
    document_type varchar(20) NOT NULL,
    number_date date NOT NULL,
    last_number bigint NOT NULL,
    PRIMARY KEY (document_type, number_date)
);
ALTER TABLE sal_sales_order ADD COLUMN IF NOT EXISTS customer_order_no varchar(100);
ALTER TABLE pmm_work_order ADD COLUMN IF NOT EXISTS sale_order_id bigint;
ALTER TABLE pmm_work_order ADD COLUMN IF NOT EXISTS sale_order_line_id bigint;
ALTER TABLE pmm_work_order ADD COLUMN IF NOT EXISTS customer_order_no varchar(100);
ALTER TABLE pmm_work_order ADD COLUMN IF NOT EXISTS factory_id bigint;
ALTER TABLE pmm_work_order ADD COLUMN IF NOT EXISTS factory_name varchar(200);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, create_by)
SELECT '销售出库', menu_id, 6, '/sal/stock-out', 'wms/stock-out/index', 'C', 0, 0, 'wms:stockout:list', 'Upload', 'migration'
FROM sys_menu parent WHERE parent.parent_id=0 AND parent.path='sal' AND parent.menu_type='M'
AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/sal/stock-out' AND menu_type='C');
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, target.menu_id FROM sys_role_menu rm
JOIN sys_menu source ON source.menu_id=rm.menu_id AND source.perms='wms:stockout:list'
JOIN sys_menu target ON target.path='/sal/stock-out' AND target.menu_type='C'
ON CONFLICT DO NOTHING;
