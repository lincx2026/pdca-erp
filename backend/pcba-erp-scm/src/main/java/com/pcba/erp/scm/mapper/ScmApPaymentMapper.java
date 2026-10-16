package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.ScmApPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScmApPaymentMapper extends BaseMapper<ScmApPayment> {

    @Select("<script>" +
            "SELECT * FROM scm_ap_payment <where>" +
            "  <if test='paymentNo != null and paymentNo != \"\"'>AND payment_no LIKE CONCAT('%',CAST(#{paymentNo} AS TEXT),'%')</if>" +
            "  <if test='supplierId != null'>AND supplier_id = #{supplierId}</if>" +
            "  <if test='supplierName != null and supplierName != \"\"'>AND supplier_name LIKE CONCAT('%',CAST(#{supplierName} AS TEXT),'%')</if>" +
            "  <if test='status != null'>AND status = #{status}</if>" +
            "  <if test='payDate != null and payDate != \"\"'>AND pay_date = #{payDate}</if>" +
            "  <if test='planId != null'>AND plan_id = #{planId}</if>" +
            "  <if test='payableId != null'>AND payable_id = #{payableId}</if>" +
            "</where> ORDER BY pay_date DESC, payment_id DESC</script>")
    IPage<ScmApPayment> selectPage(IPage<ScmApPayment> page,
                                   @Param("paymentNo") String paymentNo,
                                   @Param("supplierId") Long supplierId,
                                   @Param("supplierName") String supplierName,
                                   @Param("status") Integer status,
                                   @Param("payDate") String payDate,
                                   @Param("planId") Long planId,
                                   @Param("payableId") Long payableId);

    @Select("SELECT COUNT(*) FROM scm_ap_payment WHERE payment_no = #{no} AND payment_id <> #{id}")
    long countDuplicate(@Param("no") String no, @Param("id") Long id);
}
