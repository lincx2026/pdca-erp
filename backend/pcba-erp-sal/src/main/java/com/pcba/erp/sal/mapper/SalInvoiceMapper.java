package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.sal.entity.SalInvoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface SalInvoiceMapper extends BaseMapper<SalInvoice> {

    /** 分页查询开票单(支持发票号/客户/票种/状态/日期筛选) */
    @Select("<script>" +
            "SELECT i.* FROM sal_invoice i " +
            "<where>" +
            "  <if test=\"invoiceNo != null and invoiceNo != ''\">" +
            "    AND i.invoice_no LIKE CONCAT('%', CAST(#{invoiceNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"customerId != null\">" +
            "    AND i.customer_id = #{customerId}" +
            "  </if>" +
            "  <if test=\"invoiceType != null and invoiceType != ''\">" +
            "    AND i.invoice_type = #{invoiceType}" +
            "  </if>" +
            "  <if test=\"status != null\">" +
            "    AND i.status = #{status}" +
            "  </if>" +
            "  <if test=\"dateFrom != null\">" +
            "    AND i.invoice_date &gt;= #{dateFrom}" +
            "  </if>" +
            "  <if test=\"dateTo != null\">" +
            "    AND i.invoice_date &lt;= #{dateTo}" +
            "  </if>" +
            "</where>" +
            " ORDER BY i.invoice_id DESC" +
            "</script>")
    IPage<SalInvoice> selectPage(IPage<SalInvoice> page,
                                 @Param("invoiceNo") String invoiceNo,
                                 @Param("customerId") Long customerId,
                                 @Param("invoiceType") String invoiceType,
                                 @Param("status") Integer status,
                                 @Param("dateFrom") LocalDate dateFrom,
                                 @Param("dateTo") LocalDate dateTo);

    /** 查询客户可开票应收单(status<>9且未开票余额>0) */
    @Select("SELECT r.* FROM sal_ar_receivable r " +
            "WHERE r.customer_id=#{customerId} AND r.status <> 9 " +
            "AND COALESCE(r.total_amount,0) - COALESCE(r.invoiced_amount,0) > 0.0001 " +
            "ORDER BY r.biz_date ASC")
    @org.apache.ibatis.annotations.Results(value = {
            @org.apache.ibatis.annotations.Result(column = "receivable_id", property = "receivableId"),
            @org.apache.ibatis.annotations.Result(column = "invoiced_amount", property = "invoicedAmount")
    })
    java.util.List<com.pcba.erp.sal.entity.SalArReceivable> selectInvoiceableReceivables(@Param("customerId") Long customerId);

    /** 当日发票号序号(用于生成 INV+yyyyMMdd+3位) */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(invoice_no, 13, 3) AS INTEGER)), 0) FROM sal_invoice " +
            "WHERE invoice_no LIKE CONCAT('INV', CAST(#{ymd} AS TEXT), '%')")
    int maxInvoiceSeqOfDay(@Param("ymd") String ymd);

    /** 原子累加应收单已开票金额(作废回退时负向, GREATEST兜底非负) */
    @Update("UPDATE sal_ar_receivable SET invoiced_amount = GREATEST(0, COALESCE(invoiced_amount,0) + #{delta}) " +
            "WHERE receivable_id = #{receivableId} AND status <> 9")
    int addInvoicedAmount(@Param("receivableId") Long receivableId, @Param("delta") java.math.BigDecimal delta);
}
