package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.sal.entity.SalInvoiceLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SalInvoiceLineMapper extends BaseMapper<SalInvoiceLine> {

    /** 按开票单ID查询明细 */
    @Select("SELECT * FROM sal_invoice_line WHERE invoice_id = #{invoiceId} ORDER BY line_id")
    List<SalInvoiceLine> selectByInvoiceId(@Param("invoiceId") Long invoiceId);

    /** 删除开票单明细 */
    int deleteByInvoiceId(@Param("invoiceId") Long invoiceId);
}
