package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalArReceivable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SalArReceivableMapper extends BaseMapper<SalArReceivable> {

    @Select("<script>" +
            "SELECT * FROM sal_ar_receivable <where>" +
            "  <if test='receivableNo != null and receivableNo != \"\"'>AND receivable_no LIKE CONCAT('%',CAST(#{receivableNo} AS TEXT),'%')</if>" +
            "  <if test='customerId != null'>AND customer_id = #{customerId}</if>" +
            "  <if test='customerName != null and customerName != \"\"'>AND customer_name LIKE CONCAT('%',CAST(#{customerName} AS TEXT),'%')</if>" +
            "  <if test='status != null'>AND status = #{status}</if>" +
            "  <if test='bizDate != null and bizDate != \"\"'>AND biz_date = #{bizDate}</if>" +
            "  <if test='dueDate != null and dueDate != \"\"'>AND due_date = #{dueDate}</if>" +
            "  <if test='isOverdue != null'>AND is_overdue = #{isOverdue}</if>" +
            "</where> ORDER BY receivable_id DESC</script>")
    IPage<SalArReceivable> selectPage(IPage<SalArReceivable> page,
                                      @Param("receivableNo") String receivableNo,
                                      @Param("customerId") Long customerId,
                                      @Param("customerName") String customerName,
                                      @Param("status") Integer status,
                                      @Param("bizDate") String bizDate,
                                      @Param("dueDate") String dueDate,
                                      @Param("isOverdue") Integer isOverdue);

    @Select("SELECT COUNT(*) FROM sal_ar_receivable WHERE receivable_no = #{no} AND (#{id} IS NULL OR receivable_id <> #{id})")
    long countDuplicate(@Param("no") String no, @Param("id") Long id);

    // --- 看板统计 ---
    /** 已收总额 */
    @Select("SELECT COALESCE(SUM(CASE WHEN r.status IN (3,4) THEN COALESCE(r.received_amount,0) ELSE 0 END),0) AS val FROM sal_ar_receivable r WHERE r.status <> 9 AND (CAST(#{customerId} AS BIGINT) IS NULL OR r.customer_id = #{customerId})")
    java.math.BigDecimal sumReceived(@Param("customerId") Long customerId);

    /** 未收总额 = Σ(cny_total_amount - received_amount) */
    @Select("SELECT COALESCE(SUM(CASE WHEN r.status <> 9 THEN GREATEST(COALESCE(r.cny_total_amount,0) - COALESCE(r.received_amount,0), 0) ELSE 0 END),0) AS val FROM sal_ar_receivable r WHERE r.status <> 9 AND (CAST(#{customerId} AS BIGINT) IS NULL OR r.customer_id = #{customerId})")
    java.math.BigDecimal sumUnreceived(@Param("customerId") Long customerId);

    /** 逾期未收总额 */
    @Select("SELECT COALESCE(SUM(CASE WHEN r.status<>9 AND r.is_overdue=1 THEN GREATEST(COALESCE(r.cny_total_amount,0)-COALESCE(r.received_amount,0),0) ELSE 0 END),0) AS val FROM sal_ar_receivable r WHERE (CAST(#{customerId} AS BIGINT) IS NULL OR r.customer_id = #{customerId})")
    java.math.BigDecimal sumOverdue(@Param("customerId") Long customerId);

    /** 本月到期未收 */
    @Select("SELECT COALESCE(SUM(CASE WHEN r.status<>9 AND EXTRACT(YEAR FROM r.due_date)=EXTRACT(YEAR FROM CURRENT_DATE) AND EXTRACT(MONTH FROM r.due_date)=EXTRACT(MONTH FROM CURRENT_DATE) THEN GREATEST(COALESCE(r.cny_total_amount,0)-COALESCE(r.received_amount,0),0) ELSE 0 END),0) AS val FROM sal_ar_receivable r WHERE (CAST(#{customerId} AS BIGINT) IS NULL OR r.customer_id=#{customerId})")
    java.math.BigDecimal sumDueThisMonth(@Param("customerId") Long customerId);

    /** 新增30天 / 今日新增 */
    @Select("SELECT COALESCE(SUM(CASE WHEN r.status<>9 AND DATE(r.create_time)=CURRENT_DATE THEN r.cny_total_amount ELSE 0 END),0) AS \"newToday\", " +
            "COALESCE(SUM(CASE WHEN r.status<>9 AND DATE(r.create_time) BETWEEN (CURRENT_DATE - INTERVAL '30 days') AND CURRENT_DATE THEN r.cny_total_amount ELSE 0 END),0) AS new30 " +
            "FROM sal_ar_receivable r WHERE (CAST(#{customerId} AS BIGINT) IS NULL OR r.customer_id=#{customerId})")
    Map<String,Object> sumMore(@Param("customerId") Long customerId);

    /** 客户TOP未收 */
    @Select("SELECT r.customer_id AS \"customerId\", MAX(r.customer_name) AS \"customerName\", " +
            "GREATEST(SUM(COALESCE(r.cny_total_amount,0)-COALESCE(r.received_amount,0)),0) AS \"unreceivedAmount\", " +
            "COALESCE(SUM(CASE WHEN r.is_overdue=1 THEN GREATEST(COALESCE(r.cny_total_amount,0)-COALESCE(r.received_amount,0),0) ELSE 0 END),0) AS \"overdueAmount\" " +
            "FROM sal_ar_receivable r WHERE r.status <> 9 AND (CAST(#{customerId} AS BIGINT) IS NULL OR r.customer_id=#{customerId}) GROUP BY r.customer_id ORDER BY \"unreceivedAmount\" DESC LIMIT #{limit}")
    List<Map<String,Object>> topCustomers(@Param("customerId") Long customerId, @Param("limit") int limit);

    /** 账龄分桶 */
    @Select("SELECT CASE WHEN (CURRENT_DATE - CAST(r.due_date AS DATE)) < 0 THEN '未到期' " +
            " WHEN (CURRENT_DATE - CAST(r.due_date AS DATE)) BETWEEN 0 AND 30 THEN '0-30天' " +
            " WHEN (CURRENT_DATE - CAST(r.due_date AS DATE)) BETWEEN 31 AND 60 THEN '31-60天' " +
            " WHEN (CURRENT_DATE - CAST(r.due_date AS DATE)) BETWEEN 61 AND 90 THEN '61-90天' " +
            " ELSE '90天以上' END AS bucket, " +
            "GREATEST(SUM(COALESCE(r.cny_total_amount,0)-COALESCE(r.received_amount,0)),0) AS amount " +
            "FROM sal_ar_receivable r WHERE r.status <> 9 AND GREATEST(COALESCE(r.cny_total_amount,0)-COALESCE(r.received_amount,0),0) > 0 " +
            " AND (CAST(#{customerId} AS BIGINT) IS NULL OR r.customer_id=#{customerId}) GROUP BY bucket")
    List<Map<String,Object>> agingBuckets(@Param("customerId") Long customerId);

    // --- 对账单(客户+周期) 汇总 + 明细 ---
    /**
     * 客户对账单汇总：期初未收 / 本期蓝字发生 / 本期红字冲销 / 本期收款 / 期末未收
     * 期末未收 = 期初 + 本期蓝字 + 本期红字(已为负) - 本期收款
     */
    @Select("SELECT " +
            " COALESCE(SUM(CASE WHEN r.biz_date < #{dateFrom} THEN GREATEST(COALESCE(r.cny_total_amount,0)-COALESCE(r.received_amount,0),0) ELSE 0 END),0) AS \"beforeUnreceived\"," +
            " COALESCE(SUM(CASE WHEN r.biz_date BETWEEN #{dateFrom} AND #{dateTo} AND r.biz_type<>'SALES_RETURN' THEN COALESCE(r.cny_total_amount,0) ELSE 0 END),0) AS \"occurredBlue\"," +
            " COALESCE(SUM(CASE WHEN r.biz_date BETWEEN #{dateFrom} AND #{dateTo} AND r.biz_type='SALES_RETURN' THEN COALESCE(r.cny_total_amount,0) ELSE 0 END),0) AS \"occurredRed\"," +
            " COALESCE(SUM(CASE WHEN r.biz_date BETWEEN #{dateFrom} AND #{dateTo} THEN COALESCE(r.received_amount,0) ELSE 0 END),0) AS \"receivedInPeriod\"" +
            " FROM sal_ar_receivable r" +
            " WHERE r.customer_id=#{customerId} AND r.status<>9")
    Map<String,Object> arStatementSummary(@Param("customerId") Long customerId,
                                          @Param("dateFrom") java.time.LocalDate dateFrom,
                                          @Param("dateTo") java.time.LocalDate dateTo);

    /** 客户对账单明细：本期发生(biz_date 在区间内)的应收单列表 */
    @Select("SELECT * FROM sal_ar_receivable " +
            "WHERE customer_id=#{customerId} AND status<>9 " +
            "  AND biz_date BETWEEN #{dateFrom} AND #{dateTo} " +
            "ORDER BY biz_date ASC, receivable_id ASC")
    List<SalArReceivable> arStatementLines(@Param("customerId") Long customerId,
                                           @Param("dateFrom") java.time.LocalDate dateFrom,
                                           @Param("dateTo") java.time.LocalDate dateTo);
}
