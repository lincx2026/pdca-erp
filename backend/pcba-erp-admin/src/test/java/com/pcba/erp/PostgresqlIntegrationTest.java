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
