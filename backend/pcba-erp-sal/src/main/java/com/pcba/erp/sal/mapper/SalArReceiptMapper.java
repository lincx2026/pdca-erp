package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalArReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalArReceiptMapper extends BaseMapper<SalArReceipt> {

    @Select("<script>" +
            "SELECT * FROM sal_ar_receipt <where>" +
            "  <if test='receiptNo != null and receiptNo != \"\"'>AND receipt_no LIKE CONCAT('%',CAST(#{receiptNo} AS TEXT),'%')</if>" +
            "  <if test='customerId != null'>AND customer_id = #{customerId}</if>" +
            "  <if test='customerName != null and customerName != \"\"'>AND customer_name LIKE CONCAT('%',CAST(#{customerName} AS TEXT),'%')</if>" +
            "  <if test='status != null'>AND status = #{status}</if>" +
            "  <if test='receiptDate != null and receiptDate != \"\"'>AND receipt_date = #{receiptDate}</if>" +
            "  <if test='planId != null'>AND plan_id = #{planId}</if>" +
            "  <if test='receivableId != null'>AND receivable_id = #{receivableId}</if>" +
            "</where> ORDER BY receipt_date DESC, receipt_id DESC</script>")
    IPage<SalArReceipt> selectPage(IPage<SalArReceipt> page,
                                   @Param("receiptNo") String receiptNo,
                                   @Param("customerId") Long customerId,
                                   @Param("customerName") String customerName,
                                   @Param("status") Integer status,
                                   @Param("receiptDate") String receiptDate,
                                   @Param("planId") Long planId,
                                   @Param("receivableId") Long receivableId);

    @Select("SELECT COUNT(*) FROM sal_ar_receipt WHERE receipt_no = #{no} AND (#{id} IS NULL OR receipt_id <> #{id})")
    long countDuplicate(@Param("no") String no, @Param("id") Long id);
}
