package com.pcba.erp;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.wms.service.IWmsInventoryService;
import com.pcba.erp.wms.service.IWmsStockOutService;
import com.pcba.erp.pmm.service.IPmmFasStockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.SqlSessionFactory;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/** Uses only the disposable PostgreSQL/Redis ports documented in README. */
@EnabledIfEnvironmentVariable(named = "PCBA_INTEGRATION_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/pcba_erp?stringtype=unspecified",
        "spring.datasource.username=postgres", "spring.datasource.password=postgres-test-only",
        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=56379",
        "spring.data.redis.password=redis-test-only",
        "pcba.jwt.secret=postgres-integration-test-secret-at-least-32-bytes",
        "pcba.bootstrap.admin-password=integration-admin-password-only"
})
class PostgresqlIntegrationTest {
    @Autowired IWmsInventoryService inventory;
    @Autowired IWmsStockOutService stockOut;
    @Autowired IPmmFasStockService finishedStock;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlSessionFactory sessions;
    @Autowired com.pcba.erp.system.mapper.SysRoleMapper roleMapper;
    @Autowired com.pcba.erp.system.controller.SysRoleController roleController;
    @Autowired com.pcba.erp.system.controller.SysUserController userController;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void disabledOrDeletedRolesGrantNeitherRoleKeysNorPermissions() {
        jdbc.execute("CREATE TEMP TABLE sys_role (LIKE public.sys_role INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE sys_user (LIKE public.sys_user INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE sys_user_role (LIKE public.sys_user_role INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE sys_role_menu (LIKE public.sys_role_menu INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE sys_menu (LIKE public.sys_menu INCLUDING ALL) ON COMMIT DROP");
        jdbc.update("INSERT INTO sys_user(user_id,user_name,nick_name,password,status,del_flag) VALUES (42,'role-status-test','Test','test-only',1,0)");
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag) VALUES "
                + "(10,'Enabled','enabled',1,1,0),(11,'Disabled','disabled',2,0,0),(12,'Deleted','deleted',3,1,2)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (42,10),(42,11),(42,12)");
        jdbc.update("INSERT INTO sys_menu(menu_id,menu_name,perms) VALUES (10,'Enabled','test:enabled'),(11,'Disabled','test:disabled'),(12,'Deleted','test:deleted')");
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (10,10),(11,11),(12,12)");
        assertEquals(List.of("test:enabled"),roleMapper.selectPermsByUserId(42L));
        assertEquals(List.of("enabled"),roleMapper.selectRolesByUserId(42L).stream().map(com.pcba.erp.system.entity.SysRole::getRoleKey).toList());
        var admin=new com.pcba.erp.common.security.LoginUser(1L,100L,"test-admin","","Test",1,Set.of("*:*:*"),Set.of("admin"),1);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(admin,null,List.of()));
        try {
            assertEquals(List.of(10L),roleController.optionSelect().getData().stream().map(com.pcba.erp.system.entity.SysRole::getRoleId).toList());
            assertEquals(List.of(10L),userController.optionSelect().getData().stream().map(r -> r.get("roleId")).toList());
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void legacyStatusConversionPreservesEnabledAndDisabledMeaningAndRoleBindings() throws Exception {
        statusMigrationTables();
        jdbc.execute("CREATE TEMP TABLE sys_user_role (LIKE public.sys_user_role INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE sys_role_menu (LIKE public.sys_role_menu INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE sys_role_dept (LIKE public.sys_role_dept INCLUDING ALL) ON COMMIT DROP");
        jdbc.update("INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,installed_by,execution_time,success) "
                + "VALUES (1,'7','Verified MySQL import','BASELINE','Verified MySQL import','test',0,true)");
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag) VALUES (10,'Enabled','enabled',1,0,0),(11,'Disabled','disabled',2,1,0)");
        jdbc.update("INSERT INTO sys_dept(dept_id,parent_id,dept_name,status,del_flag) VALUES (100,0,'Enabled',0,0),(101,100,'Disabled',1,0)");
        jdbc.update("INSERT INTO sys_user_role VALUES (42,10)");
        jdbc.update("INSERT INTO sys_role_menu VALUES (10,123)");
        jdbc.update("INSERT INTO sys_role_dept VALUES (10,100)");
        runMigration("V8__normalize_legacy_role_department_status.sql");
        assertEquals(List.of(1,0),jdbc.queryForList("SELECT status FROM sys_role ORDER BY role_id",Integer.class));
        assertEquals(List.of(1,0),jdbc.queryForList("SELECT status FROM sys_dept ORDER BY dept_id",Integer.class));
        assertEquals(10L,jdbc.queryForObject("SELECT role_id FROM sys_user_role WHERE user_id=42",Long.class));
        assertEquals(123L,jdbc.queryForObject("SELECT menu_id FROM sys_role_menu WHERE role_id=10",Long.class));
        assertEquals(100L,jdbc.queryForObject("SELECT dept_id FROM sys_role_dept WHERE role_id=10",Long.class));
        assertEquals("1",jdbc.queryForObject("SELECT column_default FROM information_schema.columns WHERE table_schema LIKE 'pg_temp_%' AND table_name='sys_role' AND column_name='status'",String.class));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void freshInstallationKeepsCurrentStatusMeaning() throws Exception {
        statusMigrationTables();
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag) VALUES (10,'Enabled','enabled',1,1,0),(11,'Disabled','disabled',2,0,0)");
        jdbc.update("INSERT INTO sys_dept(dept_id,parent_id,dept_name,status,del_flag) VALUES (100,0,'Enabled',1,0),(101,100,'Disabled',0,0)");
        runMigration("V8__normalize_legacy_role_department_status.sql");
        assertEquals(List.of(1,0),jdbc.queryForList("SELECT status FROM sys_role ORDER BY role_id",Integer.class));
        assertEquals(List.of(1,0),jdbc.queryForList("SELECT status FROM sys_dept ORDER BY dept_id",Integer.class));
    }

    private void statusMigrationTables() {
        jdbc.execute("CREATE TEMP TABLE sys_role (LIKE public.sys_role INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE sys_dept (LIKE public.sys_dept INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE flyway_schema_history (LIKE public.flyway_schema_history INCLUDING ALL) ON COMMIT DROP");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void salesMenuMigrationRepairsLegacyEncodingAndPreservesCustomLabels() {
        jdbc.execute("CREATE TEMP TABLE sys_menu (menu_id BIGINT PRIMARY KEY, menu_name TEXT, path TEXT, perms TEXT) ON COMMIT DROP");
        jdbc.update("INSERT INTO sys_menu VALUES (500, ?, 'sal', NULL), (501, ?, '/sal/customer', 'sal:customer:list'), "
                + "(502, '自定义销售订单', '/sal/order', 'sal:order:list'), (450, '财务应付', 'ap-fin', NULL)",
                "\u00e9\u201d\u20ac\u00e5\u201d\u00ae\u00e7\u00ae\u00a1\u00e7\u0090\u2020",
                "\u00e5\u00ae\u00a2\u00e6\u02c6\u00b7\u00e7\u00ae\u00a1\u00e7\u0090\u2020");
        var migration = new org.springframework.core.io.ClassPathResource("db/migration/V4__repair_sales_menu_labels.sql");
        for (int attempt = 0; attempt < 2; attempt++) {
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                        new org.springframework.core.io.support.EncodedResource(migration, java.nio.charset.StandardCharsets.UTF_8));
                return null;
            });
        }
        assertEquals("销售管理", jdbc.queryForObject("SELECT menu_name FROM sys_menu WHERE menu_id=500", String.class));
        assertEquals("客户管理", jdbc.queryForObject("SELECT menu_name FROM sys_menu WHERE menu_id=501", String.class));
        assertEquals("自定义销售订单", jdbc.queryForObject("SELECT menu_name FROM sys_menu WHERE menu_id=502", String.class));
        assertEquals("财务应付", jdbc.queryForObject("SELECT menu_name FROM sys_menu WHERE menu_id=450", String.class));
        assertEquals("sal:customer:list", jdbc.queryForObject("SELECT perms FROM sys_menu WHERE menu_id=501", String.class));
        assertEquals("/sal/customer", jdbc.queryForObject("SELECT path FROM sys_menu WHERE menu_id=501", String.class));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void partnerTextMigrationRepairsMasterAndHistoricalNamesWithoutChangingMoneyOrLinks() throws Exception {
        String brokenSupplier = new String(java.util.HexFormat.of().parseHex(
                "c3a6c2b5e280b9c3a8c2afe280a2c3a4c2bee280bac3a5c2bae2809dc3a5e280a2e280a0"), java.nio.charset.StandardCharsets.UTF_8);
        String brokenHistory = new String(java.util.HexFormat.of().parseHex(
                "c3a5c5bde280a0c3a5c28fc2b2c3a5c2aec2a2c3a6cb86c2b7"), java.nio.charset.StandardCharsets.UTF_8);
        String code = "ENC" + System.nanoTime();
        Long supplierId = jdbc.queryForObject("INSERT INTO scm_supplier(supplier_code, supplier_name, contact, address, payee_name, payee_account, credit_limit) "
                + "VALUES (?, ?, '正常联系人', NULL, ?, '0012300456', 12345.67) RETURNING supplier_id", Long.class, code, brokenSupplier, brokenSupplier);
        Long customerId = jdbc.queryForObject("INSERT INTO scm_customer(customer_code, customer_name, short_name) "
                + "VALUES (?, '正常客户é😀', '损坏�名称') RETURNING customer_id", Long.class, code);
        Long orderId = jdbc.queryForObject("INSERT INTO scm_purchase_order(po_no, supplier_id, supplier_name, po_date, total_amount) "
                + "VALUES (?, ?, ?, CURRENT_DATE, 7890.12) RETURNING po_id", Long.class, code, supplierId, brokenHistory);
        runMigration("V5__repair_partner_text_encoding.sql");
        runMigration("V5__repair_partner_text_encoding.sql");
        assertEquals("测试供应商", jdbc.queryForObject("SELECT supplier_name FROM scm_supplier WHERE supplier_id=?", String.class, supplierId));
        assertEquals("测试供应商", jdbc.queryForObject("SELECT payee_name FROM scm_supplier WHERE supplier_id=?", String.class, supplierId));
        assertEquals("正常联系人", jdbc.queryForObject("SELECT contact FROM scm_supplier WHERE supplier_id=?", String.class, supplierId));
        assertNull(jdbc.queryForObject("SELECT address FROM scm_supplier WHERE supplier_id=?", String.class, supplierId));
        assertEquals("0012300456", jdbc.queryForObject("SELECT payee_account FROM scm_supplier WHERE supplier_id=?", String.class, supplierId));
        assertEquals(0, new BigDecimal("12345.67").compareTo(jdbc.queryForObject("SELECT credit_limit FROM scm_supplier WHERE supplier_id=?", BigDecimal.class, supplierId)));
        assertEquals("正常客户é😀", jdbc.queryForObject("SELECT customer_name FROM scm_customer WHERE customer_id=?", String.class, customerId));
        assertEquals("损坏�名称", jdbc.queryForObject("SELECT short_name FROM scm_customer WHERE customer_id=?", String.class, customerId));
        assertEquals("历史客户", jdbc.queryForObject("SELECT supplier_name FROM scm_purchase_order WHERE po_id=?", String.class, orderId));
        assertEquals(supplierId, jdbc.queryForObject("SELECT supplier_id FROM scm_purchase_order WHERE po_id=?", Long.class, orderId));
        assertEquals(0, new BigDecimal("7890.12").compareTo(jdbc.queryForObject("SELECT total_amount FROM scm_purchase_order WHERE po_id=?", BigDecimal.class, orderId)));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void partnerMenuMigrationKeepsRoutesAndExistingRoleBoundaries() throws Exception {
        jdbc.execute("CREATE TEMP TABLE sys_menu (LIKE public.sys_menu INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE sys_role_menu (LIKE public.sys_role_menu INCLUDING ALL) ON COMMIT DROP");
        jdbc.update("INSERT INTO sys_menu(menu_id, menu_name, parent_id, path, menu_type, visible, status, perms) VALUES "
                + "(306, '供应商管理', 300, '/scm/supplier', 'C', 0, 0, 'scm:supplier:list'), "
                + "(501, '客户管理', 500, '/sal/customer', 'C', 0, 0, 'sal:customer:list')");
        jdbc.update("INSERT INTO sys_role_menu(role_id, menu_id) VALUES (1001, 306), (1002, 501), (1003, 999)");
        runMigration("V6__add_partner_master_data_menu.sql");
        runMigration("V6__add_partner_master_data_menu.sql");
        Long root = jdbc.queryForObject("SELECT menu_id FROM sys_menu WHERE path='base-data'", Long.class);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM sys_menu WHERE parent_id=?", Integer.class, root));
        assertEquals(List.of("/scm/supplier", "/sal/customer"), jdbc.queryForList("SELECT path FROM sys_menu WHERE parent_id=? ORDER BY order_num", String.class, root));
        assertEquals(List.of(1001L, 1002L), jdbc.queryForList("SELECT role_id FROM sys_role_menu WHERE menu_id=? ORDER BY role_id", Long.class, root));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sys_role_menu WHERE (role_id=1001 AND menu_id=501) OR (role_id=1002 AND menu_id=306)", Integer.class));
        assertEquals("scm:supplier:list", jdbc.queryForObject("SELECT perms FROM sys_menu WHERE menu_id=306", String.class));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void roleMenuMigrationRestoresPageAndPreservesPermissionsAndSeededDepartment() throws Exception {
        jdbc.execute("CREATE TEMP TABLE sys_menu (LIKE public.sys_menu INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE sys_role_menu (LIKE public.sys_role_menu INCLUDING ALL) ON COMMIT DROP");
        jdbc.update("INSERT INTO sys_menu(menu_id, menu_name, parent_id, path, component, menu_type, visible, status, perms) VALUES "
                + "(3, '角色管理', 1, '/system/role', 'system/role/index', 'C', 0, 0, 'system:role:list'), "
                + "(4, '菜单管理', 1, '/system/menu', 'system/menu/index', 'C', 0, 0, 'system:menu:list'), "
                + "(5, 'MenuMgmt', 3, 'menu', 'system/menu/index', 'C', 0, 0, 'system:menu:list'), "
                + "(143, '角色查询', 3, '', NULL, 'F', 0, 0, 'system:role:list'), "
                + "(151, '部门查询', 5, '', NULL, 'F', 0, 0, 'system:dept:list')");
        jdbc.update("INSERT INTO sys_role_menu(role_id, menu_id) VALUES (1001, 3), (1001, 143), (1002, 5)");
        var rights = jdbc.queryForList("SELECT role_id, menu_id FROM sys_role_menu ORDER BY role_id, menu_id");
        var pages = jdbc.queryForList("SELECT * FROM sys_menu WHERE menu_id IN (3,4,143,151) ORDER BY menu_id");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sys_menu WHERE parent_id=3 AND visible=0 AND status=0 AND menu_type IN ('M','C')", Integer.class));
        runMigration("V7__repair_role_menu_child.sql");
        runMigration("V7__repair_role_menu_child.sql");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sys_menu WHERE parent_id=3 AND visible=0 AND status=0 AND menu_type IN ('M','C')", Integer.class));
        assertEquals(pages, jdbc.queryForList("SELECT * FROM sys_menu WHERE menu_id IN (3,4,143,151) ORDER BY menu_id"));
        assertEquals(rights, jdbc.queryForList("SELECT role_id, menu_id FROM sys_role_menu ORDER BY role_id, menu_id"));
        assertEquals("system:menu:list", jdbc.queryForObject("SELECT perms FROM sys_menu WHERE menu_id=5", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT visible FROM sys_menu WHERE menu_id=5", Integer.class));
        // Fresh installations use ID 5 for department management; never rewrite it.
        jdbc.update("UPDATE sys_menu SET parent_id=1, menu_name='部门管理', path='dept', component='system/dept/index', "
                + "menu_type='C', visible=0, perms='system:dept:list' WHERE menu_id=5");
        var department = jdbc.queryForMap("SELECT * FROM sys_menu WHERE menu_id=5");
        runMigration("V7__repair_role_menu_child.sql");
        assertEquals(department, jdbc.queryForMap("SELECT * FROM sys_menu WHERE menu_id=5"));
    }

    private void runMigration(String file) throws Exception {
        var resource = new org.springframework.core.io.ClassPathResource("db/migration/" + file);
        try (var input = resource.getInputStream()) {
            jdbc.execute(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void handwrittenMapperSelectsCompileOnPostgresql() throws Exception {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        var configuration = sessions.getConfiguration();
        for (String id : configuration.getMappedStatementNames()) {
            if (!id.contains(".mapper.")) continue;
            var statement = configuration.getMappedStatement(id);
            if (statement.getSqlCommandType() != SqlCommandType.SELECT) continue;
            String namespace = id.substring(0, id.lastIndexOf('.'));
            String name = id.substring(id.lastIndexOf('.')+1);
            var method = java.util.Arrays.stream(Class.forName(namespace).getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).findFirst();
            if (method.isEmpty()) continue; // MyBatis Plus generated CRUD is checked through service tests.
            Map<String,Object> parameters = new HashMap<>();
            parameters.put("dataScope", "");
            var arguments = method.get().getParameters();
            for (int i=0; i<arguments.length; i++) {
                var argument = arguments[i];
                Param annotation = argument.getAnnotation(Param.class);
                String key = annotation == null ? argument.getName() : annotation.value();
                Class<?> type = argument.getType();
                Object value = null;
                if (type == Long.class || type == long.class) value = 1L;
                else if (type == Integer.class || type == int.class) value = key.equals("offset") ? 0 : 1;
                else if (type == String.class) value = key.toLowerCase().matches(".*(time|date|start|end).*") ? "2026-01-01" : "";
                else if (Page.class.isAssignableFrom(type)) value = new Page<>(1, 10);
                else if (List.class.isAssignableFrom(type)) value = List.of(1L);
                else if (Set.class.isAssignableFrom(type)) value = Set.of(1L);
                else if (Map.class.isAssignableFrom(type)) value = new HashMap<>();
                parameters.put(key, value);
                parameters.put("param"+(i+1),value);
            }
            try {
                var bound = statement.getBoundSql(parameters);
                jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                    try (var query = connection.prepareStatement("EXPLAIN " + bound.getSql())) {
                        for (int i=0; i<bound.getParameterMappings().size(); i++) {
                            String property = bound.getParameterMappings().get(i).getProperty();
                            Object value = bound.hasAdditionalParameter(property) ? bound.getAdditionalParameter(property) : parameters.get(property);
                            query.setObject(i+1,value);
                        }
                        query.execute();
                    }
                    return null;
                });
                checked++;
            } catch (Exception error) {
                Throwable cause = error;
                while (cause.getCause()!=null) cause=cause.getCause();
                failures.add(id+": "+cause.getMessage());
            }
        }
        System.out.println("PostgreSQL mapper SELECT plans checked: " + checked);
        assertTrue(failures.isEmpty(), String.join("\n",failures));
    }

    @Test
    void concurrentFirstInsertAndIncrementNeverLoseStock() throws Exception {
        long material = System.nanoTime();
        parallel(40, () -> inventory.increaseInventory(90001L, material, "", BigDecimal.ONE, "PCS"));
        assertEquals(40, quantity(material));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wms_inventory WHERE material_id=?", Integer.class, material));
        jdbc.update("DELETE FROM wms_inventory WHERE material_id=?", material);
    }

    @Test
    void concurrentDecrementsCannotOversell() throws Exception {
        long material = System.nanoTime();
        inventory.increaseInventory(90001L, material, "", BigDecimal.TEN, "PCS");
        List<Integer> results = parallel(30, () -> {
            try { return inventory.decreaseInventory(90001L, material, "", BigDecimal.ONE); }
            catch (ServiceException expected) { return 0; }
        });
        assertEquals(10, results.stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, quantity(material));
        jdbc.update("DELETE FROM wms_inventory WHERE material_id=?", material);
    }

    @Test
    void concurrentConfirmationAppliesInventoryExactlyOnce() throws Exception {
        long material = System.nanoTime();
        inventory.increaseInventory(90001L, material, "", BigDecimal.TEN, "PCS");
        Long id = jdbc.queryForObject("INSERT INTO wms_stock_out (order_no, warehouse_id, out_type, status) "
                + "VALUES (?, 90001, 'PRODUCTION', 2) RETURNING stock_out_id", Long.class, "TEST"+material);
        jdbc.update("INSERT INTO wms_stock_out_line(stock_out_id, material_id, quantity, batch_no, unit) VALUES (?, ?, 1, '', 'PCS')", id, material);
        List<Integer> results = parallel(8, () -> {
            try { return stockOut.confirmStockOut(id) ? 1 : 0; }
            catch (ServiceException expected) { return 0; }
        });
        assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
        assertEquals(9, quantity(material));
        jdbc.update("DELETE FROM wms_stock_out_line WHERE stock_out_id=?", id);
        jdbc.update("DELETE FROM wms_stock_out WHERE stock_out_id=?", id);
        jdbc.update("DELETE FROM wms_inventory WHERE material_id=?", material);
    }

    private int quantity(long material) {
        return jdbc.queryForObject("SELECT quantity FROM wms_inventory WHERE material_id=?", BigDecimal.class, material).intValueExact();
    }

    @Test
    void finishedStockConcurrentDecrementsCannotOversell() throws Exception {
        long product = System.nanoTime();
        Long id = jdbc.queryForObject("INSERT INTO fas_product_stock(product_id, product_name, warehouse, qty_in, qty_out, qty_available, stock_type) "
                + "VALUES (?, 'TEST', 'TEST', 10, 0, 10, 'FG') RETURNING stock_id", Long.class, product);
        List<Integer> results = parallel(30, () -> {
            try { finishedStock.stockOut(id, 1); return 1; }
            catch (ServiceException expected) { return 0; }
        });
        assertEquals(10, results.stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, jdbc.queryForObject("SELECT qty_available FROM fas_product_stock WHERE stock_id=?",Integer.class,id));
        jdbc.update("DELETE FROM fas_product_stock WHERE stock_id=?",id);
    }

    @Test
    void competingFinishedOutboundDocumentsCannotSpendTheSameStock() throws Exception {
        long product = System.nanoTime();
        jdbc.update("INSERT INTO fas_product_stock(product_id, product_name, warehouse, qty_in, qty_out, qty_available, stock_type, doc_type, doc_status) "
                + "VALUES (?, 'TEST', 'TEST', 10, 0, 10, 'FG', 'INBOUND', 3)", product);
        List<Long> documents = new ArrayList<>();
        for (int i=0; i<2; i++) {
            documents.add(jdbc.queryForObject("INSERT INTO fas_product_stock(product_id, product_name, warehouse, qty_in, qty_out, qty_available, stock_type, doc_type, doc_status, flow_qty) "
                    + "VALUES (?, 'TEST', 'TEST', 0, 0, 0, 'FG', 'OUTBOUND', 1, -7) RETURNING stock_id",Long.class,product));
        }
        var executor = Executors.newFixedThreadPool(2);
        int successes = 0;
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Long id : documents) futures.add(executor.submit(() -> {
                try {
                    var user = new com.pcba.erp.common.security.LoginUser(1L,100L,"test-admin","","Test",1,Set.of("*:*:*"),Set.of("admin"),1);
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user,null,List.of()));
                    finishedStock.outboundAuditPass(id); return 1;
                }
                catch (ServiceException expected) { return 0; }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            }));
            for (Future<Integer> future : futures) successes += future.get();
        } finally { executor.shutdownNow(); }
        assertEquals(1,successes);
        assertEquals(3,jdbc.queryForObject("SELECT SUM(qty_available) FROM fas_product_stock WHERE product_id=?",Integer.class,product));
        jdbc.update("DELETE FROM fas_product_stock WHERE product_id=?",product);
    }

    private <T> List<T> parallel(int count, Callable<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(12);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i=0; i<count; i++) futures.add(executor.submit(action));
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get());
            return results;
        } finally { executor.shutdownNow(); }
    }
}
