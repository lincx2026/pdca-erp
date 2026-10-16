package com.pcba.erp.sal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 销售业务看板 KPI（区别于 ArReceivable 的应收 KPI）
 *  - 销售订单交付率（已发货/部分出库/全部出库/已完成 ÷ 非取消总数）
 *  - 退货率（退货金额 ÷ 销售金额）
 *  - 客户 TOP 收款（按 received_amount 聚合）
 *  - 近 N 月销售/退货/收款金额趋势
 *  通过 JdbcTemplate 直查 sal_sales_order / sal_sales_return / sal_ar_receivable，零跨模块依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalKanbanService {

    private final JdbcTemplate jdbcTemplate;

    /** 销售业务 KPI 汇总 */
    public Map<String, Object> salesSummary(Long customerId) {
        Map<String, Object> r = new LinkedHashMap<>();
        String c = customerId == null ? "" : " AND customer_id = " + customerId;

        // 订单交付统计（非取消）
        Map<String, Object> o = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS \"totalOrders\", " +
                        "COALESCE(SUM(CASE WHEN status IN (3,4,5,6) THEN 1 ELSE 0 END),0) AS \"deliveredOrders\", " +
                        "COALESCE(SUM(total_amount),0) AS \"salesAmount\", " +
                        "COALESCE(SUM(CASE WHEN status IN (4,5,6) THEN 1 ELSE 0 END),0) AS \"completedOrders\" " +
                        "FROM sal_sales_order WHERE status <> 9" + c);

        // 退货统计（非取消）
        Map<String, Object> rt = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS \"returnOrders\", COALESCE(SUM(total_amount),0) AS \"returnAmount\" " +
                        "FROM sal_sales_return WHERE status <> 9" + c);

        long totalOrders = num(o.get("totalOrders"));
        long delivered = num(o.get("deliveredOrders"));
        BigDecimal salesAmount = dec(o.get("salesAmount"));
        BigDecimal returnAmount = dec(rt.get("returnAmount"));
        double deliveryRate = totalOrders == 0 ? 0 : (double) delivered / totalOrders;
        double returnRate = (salesAmount.signum() == 0) ? 0
                : returnAmount.divide(salesAmount, 4, java.math.RoundingMode.HALF_UP).doubleValue();

        r.put("totalOrders", totalOrders);
        r.put("deliveredOrders", delivered);
        r.put("completedOrders", num(o.get("completedOrders")));
        r.put("deliveryRate", deliveryRate);
        r.put("salesAmount", salesAmount);
        r.put("returnOrders", num(rt.get("returnOrders")));
        r.put("returnAmount", returnAmount);
        r.put("returnRate", returnRate);
        return r;
    }

    /** 客户 TOP 收款（按 received_amount 降序） */
    public List<Map<String, Object>> topReceivedCustomers(Long customerId, int limit) {
        String c = customerId == null ? "" : " AND customer_id = " + customerId;
        return jdbcTemplate.queryForList(
                "SELECT customer_id AS \"customerId\", MAX(customer_name) AS \"customerName\", " +
                        "COALESCE(SUM(received_amount),0) AS \"receivedAmount\", COUNT(*) AS \"receivableCount\" " +
                        "FROM sal_ar_receivable WHERE status <> 9 AND received_amount > 0" + c + " " +
                        "GROUP BY customer_id ORDER BY \"receivedAmount\" DESC LIMIT " + Math.max(1, limit));
    }

    /** 近 N 月销售/退货/收款金额趋势 */
    public List<Map<String, Object>> salesTrend(Long customerId, int months) {
        int n = Math.max(1, months);
        String c = customerId == null ? "" : " AND customer_id = " + customerId;
        LocalDate today = LocalDate.now();
        List<String> monthKeys = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate d = today.minusMonths(i);
            monthKeys.add(d.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        }
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (String k : monthKeys) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", k);
            row.put("salesAmount", BigDecimal.ZERO);
            row.put("returnAmount", BigDecimal.ZERO);
            row.put("receivedAmount", BigDecimal.ZERO);
            merged.put(k, row);
        }
        // 销售按 so_date 月份
        fillTrend(merged,
                "SELECT TO_CHAR(so_date,'YYYY-MM') AS m, COALESCE(SUM(total_amount),0) AS v " +
                        "FROM sal_sales_order WHERE status<>9 AND so_date >= (CURRENT_DATE - INTERVAL '1 month' * " + (n - 1) + ")" + c + " GROUP BY m",
                "salesAmount");
        // 退货按 return_date 月份
        fillTrend(merged,
                "SELECT TO_CHAR(return_date,'YYYY-MM') AS m, COALESCE(SUM(total_amount),0) AS v " +
                        "FROM sal_sales_return WHERE status<>9 AND return_date >= (CURRENT_DATE - INTERVAL '1 month' * " + (n - 1) + ")" + c + " GROUP BY m",
                "returnAmount");
        // 收款按 reconcile_time 月份（对账完成时间）
        fillTrend(merged,
                "SELECT TO_CHAR(reconcile_time,'YYYY-MM') AS m, COALESCE(SUM(received_amount),0) AS v " +
                        "FROM sal_ar_receivable WHERE status<>9 AND reconcile_time IS NOT NULL " +
                        "AND reconcile_time >= (CURRENT_DATE - INTERVAL '1 month' * " + (n - 1) + ")" + c + " GROUP BY m",
                "receivedAmount");
        return new ArrayList<>(merged.values());
    }

    private void fillTrend(Map<String, Map<String, Object>> merged, String sql, String field) {
        try {
            jdbcTemplate.query(sql, rs -> {
                String m = rs.getString("m");
                if (m == null) return;
                Map<String, Object> row = merged.get(m);
                if (row != null) row.put(field, dec(rs.getBigDecimal("v")));
            });
        } catch (Exception e) {
            log.warn("[salesTrend] 查询失败 field={} msg={}", field, e.getMessage());
        }
    }

    private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
    private static BigDecimal dec(Object o) { return o == null ? BigDecimal.ZERO
            : (o instanceof BigDecimal ? (BigDecimal) o : new BigDecimal(o.toString())); }
}
