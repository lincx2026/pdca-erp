package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmApPaymentPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScmApPaymentPlanMapper extends BaseMapper<ScmApPaymentPlan> {

    @Select("<script>" +
            "SELECT * FROM scm_ap_payment_plan <where>" +
            "  <if test='planNo != null and planNo != \"\"'>AND plan_no LIKE CONCAT('%',CAST(#{planNo} AS TEXT),'%')</if>" +
            "  <if test='payableId != null'>AND payable_id = #{payableId}</if>" +
            "  <if test='supplierId != null'>AND supplier_id = #{supplierId}</if>" +
            "  <if test='supplierName != null and supplierName != \"\"'>AND supplier_name LIKE CONCAT('%',CAST(#{supplierName} AS TEXT),'%')</if>" +
            "  <if test='status != null'>AND status = #{status}</if>" +
            "  <if test='planDate != null and planDate != \"\"'>AND plan_date = #{planDate}</if>" +
            "  <if test='phaseNo != null'>AND phase_no = #{phaseNo}</if>" +
            "</where> ORDER BY plan_date ASC, phase_no ASC, plan_id DESC</script>")
    IPage<ScmApPaymentPlan> selectPage(IPage<ScmApPaymentPlan> page,
                                       @Param("planNo") String planNo,
                                       @Param("payableId") Long payableId,
                                       @Param("supplierId") Long supplierId,
                                       @Param("supplierName") String supplierName,
                                       @Param("status") Integer status,
                                       @Param("planDate") String planDate,
                                       @Param("phaseNo") Integer phaseNo);

    @Select("SELECT COUNT(*) FROM scm_ap_payment_plan WHERE plan_no = #{no} AND plan_id <> #{id}")
    long countDuplicate(@Param("no") String no, @Param("id") Long id);
}
