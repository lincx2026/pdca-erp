package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmApPayable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ScmApPayableMapper extends BaseMapper<ScmApPayable> {

    @Select("<script>" +
            "SELECT * FROM scm_ap_payable <where>" +
            "  <if test='payableNo != null and payableNo != \"\"'>AND payable_no LIKE CONCAT('%',CAST(#{payableNo} AS TEXT),'%')</if>" +
            "  <if test='supplierId != null'>AND supplier_id = #{supplierId}</if>" +
            "  <if test='supplierName != null and supplierName != \"\"'>AND supplier_name LIKE CONCAT('%',CAST(#{supplierName} AS TEXT),'%')</if>" +
            "  <if test='status != null'>AND status = #{status}</if>" +
            "  <if test='bizDate != null and bizDate != \"\"'>AND biz_date = #{bizDate}</if>" +
            "  <if test='dueDate != null and dueDate != \"\"'>AND due_date = #{dueDate}</if>" +
            "  <if test='isOverdue != null'>AND is_overdue = #{isOverdue}</if>" +
            "</where> ORDER BY payable_id DESC</script>")
    IPage<ScmApPayable> selectPage(IPage<ScmApPayable> page,
                                    @Param("payableNo") String payableNo,
                                    @Param("supplierId") Long supplierId,
                                    @Param("supplierName") String supplierName,
                                    @Param("status") Integer status,
                                    @Param("bizDate") String bizDate,
                                    @Param("dueDate") String dueDate,
                                    @Param("isOverdue") Integer isOverdue);

    @Select("SELECT COUNT(*) FROM scm_ap_payable WHERE payable_no = #{no} AND (#{id} IS NULL OR payable_id <> #{id})")
    long countDuplicate(@Param("no") String no, @Param("id") Long id);

    // --- 看板统计 ---
    @Select("SELECT COALESCE(SUM(CASE WHEN p.status <> 9 THEN GREATEST(COALESCE(p.cny_total_amount,0) - COALESCE(p.paid_amount,0), 0) ELSE 0 END),0) AS val FROM scm_ap_payable p WHERE p.status <> 9 AND (CAST(#{supplierId} AS BIGINT) IS NULL OR p.supplier_id = #{supplierId})")
    java.math.BigDecimal sumUnpaid(@Param("supplierId") Long supplierId);

    @Select("SELECT COALESCE(SUM(CASE WHEN p.status<>9 AND p.is_overdue=1 THEN GREATEST(COALESCE(p.cny_total_amount,0)-COALESCE(p.paid_amount,0),0) ELSE 0 END),0) AS val FROM scm_ap_payable p WHERE (CAST(#{supplierId} AS BIGINT) IS NULL OR p.supplier_id = #{supplierId})")
    java.math.BigDecimal sumOverdue(@Param("supplierId") Long supplierId);

    @Select("SELECT COALESCE(SUM(CASE WHEN p.status<>9 AND EXTRACT(YEAR FROM p.due_date)=EXTRACT(YEAR FROM CURRENT_DATE) AND EXTRACT(MONTH FROM p.due_date)=EXTRACT(MONTH FROM CURRENT_DATE) THEN GREATEST(COALESCE(p.cny_total_amount,0)-COALESCE(p.paid_amount,0),0) ELSE 0 END),0) AS val FROM scm_ap_payable p WHERE (CAST(#{supplierId} AS BIGINT) IS NULL OR p.supplier_id=#{supplierId})")
    java.math.BigDecimal sumDueThisMonth(@Param("supplierId") Long supplierId);

    @Select("SELECT COALESCE(SUM(CASE WHEN p.status<>9 AND DATE(p.create_time)=CURRENT_DATE THEN p.cny_total_amount ELSE 0 END),0) AS \"newToday\", " +
            "COALESCE(SUM(CASE WHEN p.status<>9 AND DATE(p.create_time) BETWEEN (CURRENT_DATE - INTERVAL '30 days') AND CURRENT_DATE THEN p.cny_total_amount ELSE 0 END),0) AS new30, " +
            "COALESCE(SUM(CASE WHEN p.status IN (3,4) THEN COALESCE(p.paid_amount,0) ELSE 0 END),0) AS \"paidTotal\" " +
            "FROM scm_ap_payable p WHERE (CAST(#{supplierId} AS BIGINT) IS NULL OR p.supplier_id=#{supplierId})")
    Map<String,Object> sumMore(@Param("supplierId") Long supplierId);

    @Select("SELECT p.supplier_id AS \"supplierId\", MAX(p.supplier_name) AS \"supplierName\", " +
            "GREATEST(SUM(COALESCE(p.cny_total_amount,0)-COALESCE(p.paid_amount,0)),0) AS \"unpaidAmount\", " +
            "COALESCE(SUM(CASE WHEN p.is_overdue=1 THEN GREATEST(COALESCE(p.cny_total_amount,0)-COALESCE(p.paid_amount,0),0) ELSE 0 END),0) AS \"overdueAmount\" " +
            "FROM scm_ap_payable p WHERE p.status <> 9 AND (CAST(#{supplierId} AS BIGINT) IS NULL OR p.supplier_id=#{supplierId}) GROUP BY p.supplier_id ORDER BY \"unpaidAmount\" DESC LIMIT #{limit}")
    List<Map<String,Object>> topSuppliers(@Param("supplierId") Long supplierId, @Param("limit") int limit);

    @Select("SELECT CASE WHEN (CURRENT_DATE - CAST(p.due_date AS DATE)) < 0 THEN '未到期' " +
            " WHEN (CURRENT_DATE - CAST(p.due_date AS DATE)) BETWEEN 0 AND 30 THEN '0-30天' " +
            " WHEN (CURRENT_DATE - CAST(p.due_date AS DATE)) BETWEEN 31 AND 60 THEN '31-60天' " +
            " WHEN (CURRENT_DATE - CAST(p.due_date AS DATE)) BETWEEN 61 AND 90 THEN '61-90天' " +
            " ELSE '90天以上' END AS bucket, " +
            "GREATEST(SUM(COALESCE(p.cny_total_amount,0)-COALESCE(p.paid_amount,0)),0) AS amount " +
            "FROM scm_ap_payable p WHERE p.status <> 9 AND GREATEST(COALESCE(p.cny_total_amount,0)-COALESCE(p.paid_amount,0),0) > 0 " +
            " AND (CAST(#{supplierId} AS BIGINT) IS NULL OR p.supplier_id=#{supplierId}) GROUP BY bucket")
    List<Map<String,Object>> agingBuckets(@Param("supplierId") Long supplierId);
}
