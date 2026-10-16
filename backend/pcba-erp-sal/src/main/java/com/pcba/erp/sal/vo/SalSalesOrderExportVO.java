package com.pcba.erp.sal.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售订单导出 VO（EasyExcel）
 */
@Data
@ColumnWidth(16)
public class SalSalesOrderExportVO {

    @ExcelProperty("订单编号") @ColumnWidth(22)
    private String soNo;

    @ExcelProperty("订单类型")
    private String soType;

    @ExcelProperty("客户编码") @ColumnWidth(18)
    private String customerCode;

    @ExcelProperty("客户名称") @ColumnWidth(28)
    private String customerName;

    @ExcelProperty("联系人")
    private String contactName;

    @ExcelProperty("联系电话") @ColumnWidth(16)
    private String contactPhone;

    @ExcelProperty("销售代表") @ColumnWidth(14)
    private String salesRepName;

    @ExcelProperty("订单日期") @ColumnWidth(14)
    private LocalDate soDate;

    @ExcelProperty("交货日期") @ColumnWidth(14)
    private LocalDate deliveryDate;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("税率")
    private BigDecimal taxRate;

    @ExcelProperty("未税金额") @ColumnWidth(14)
    private BigDecimal undiscAmount;

    @ExcelProperty("税额") @ColumnWidth(14)
    private BigDecimal taxAmount;

    @ExcelProperty("总金额") @ColumnWidth(14)
    private BigDecimal totalAmount;

    @ExcelProperty("已收款") @ColumnWidth(14)
    private BigDecimal receivedAmount;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("审核人")
    private String auditBy;

    @ExcelProperty("创建时间") @ColumnWidth(20)
    private LocalDateTime createTime;

    @ExcelProperty("备注") @ColumnWidth(30)
    private String remark;
}
