package com.pcba.erp.sal.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开票单导出 VO(EasyExcel)
 */
@Data
@ColumnWidth(16)
public class SalInvoiceExportVO {

    @ExcelProperty("发票号") @ColumnWidth(22)
    private String invoiceNo;

    @ExcelProperty("票种") @ColumnWidth(10)
    private String invoiceType;

    @ExcelProperty("客户编码") @ColumnWidth(18)
    private String customerCode;

    @ExcelProperty("客户名称") @ColumnWidth(28)
    private String customerName;

    @ExcelProperty("开票日期") @ColumnWidth(14)
    private LocalDate invoiceDate;

    @ExcelProperty("未税金额") @ColumnWidth(14)
    private BigDecimal undiscAmount;

    @ExcelProperty("税额") @ColumnWidth(14)
    private BigDecimal taxAmount;

    @ExcelProperty("价税合计") @ColumnWidth(14)
    private BigDecimal totalAmount;

    @ExcelProperty("本币合计") @ColumnWidth(14)
    private BigDecimal cnyTotalAmount;

    @ExcelProperty("购方名称") @ColumnWidth(28)
    private String buyerName;

    @ExcelProperty("购方税号") @ColumnWidth(20)
    private String buyerTaxNo;

    @ExcelProperty("状态") @ColumnWidth(10)
    private String status;

    @ExcelProperty("开票人") @ColumnWidth(14)
    private String issueBy;

    @ExcelProperty("开票时间") @ColumnWidth(20)
    private LocalDateTime issueTime;

    @ExcelProperty("备注") @ColumnWidth(30)
    private String remark;
}
