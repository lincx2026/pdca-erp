package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalArReceiptPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalArReceiptPlanMapper extends BaseMapper<SalArReceiptPlan> {

    @Select("<script>" +
            "SELECT * FROM sal_ar_receipt_plan <where>" +
            "  <if test='planNo != null and planNo != \"\"'>AND plan_no LIKE CONCAT('%',CAST(#{planNo} AS TEXT),'%')</if>" +
            "  <if test='receivableId != null'>AND receivable_id = #{receivableId}</if>" +
            "  <if test='customerId != null'>AND customer_id = #{customerId}</if>" +
            "  <if test='customerName != null and customerName != \"\"'>AND customer_name LIKE CONCAT('%',CAST(#{customerName} AS TEXT),'%')</if>" +
            "  <if test='status != null'>AND status = #{status}</if>" +
            "  <if test='planDate != null and planDate != \"\"'>AND plan_date = #{planDate}</if>" +
            "  <if test='phaseNo != null'>AND phase_no = #{phaseNo}</if>" +
            "</where> ORDER BY plan_date ASC, phase_no ASC, plan_id DESC</script>")
    IPage<SalArReceiptPlan> selectPage(IPage<SalArReceiptPlan> page,
                                       @Param("planNo") String planNo,
                                       @Param("receivableId") Long receivableId,
                                       @Param("customerId") Long customerId,
                                       @Param("customerName") String customerName,
                                       @Param("status") Integer status,
                                       @Param("planDate") String planDate,
                                       @Param("phaseNo") Integer phaseNo);

    @Select("SELECT COUNT(*) FROM sal_ar_receipt_plan WHERE plan_no = #{no} AND (#{id} IS NULL OR plan_id <> #{id})")
    long countDuplicate(@Param("no") String no, @Param("id") Long id);
}
