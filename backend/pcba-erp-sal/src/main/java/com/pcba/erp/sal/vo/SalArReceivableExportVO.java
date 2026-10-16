package com.pcba.erp.sal.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 应收单导出 VO（EasyExcel）
 */
@Data
@ColumnWidth(16)
public class SalArReceivableExportVO {

    @ExcelProperty("应收编号") @ColumnWidth(22)
    private String receivableNo;

    @ExcelProperty("业务类型") @ColumnWidth(14)
    private String bizType;

    @ExcelProperty("客户编码") @ColumnWidth(18)
    private String customerCode;

    @ExcelProperty("客户名称") @ColumnWidth(28)
    private String customerName;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("未税金额") @ColumnWidth(14)
    private BigDecimal undiscAmount;

    @ExcelProperty("税额") @ColumnWidth(14)
    private BigDecimal taxAmount;

    @ExcelProperty("单据金额") @ColumnWidth(14)
    private BigDecimal totalAmount;

    @ExcelProperty("本币总额") @ColumnWidth(14)
    private BigDecimal cnyTotalAmount;

    @ExcelProperty("已收金额") @ColumnWidth(14)
    private BigDecimal receivedAmount;

    @ExcelProperty("未收金额") @ColumnWidth(14)
    private BigDecimal unreceivedAmount;

    @ExcelProperty("业务日期") @ColumnWidth(14)
    private LocalDate bizDate;

    @ExcelProperty("到期日期") @ColumnWidth(14)
    private LocalDate dueDate;

    @ExcelProperty("是否逾期") @ColumnWidth(10)
    private String isOverdue;

    @ExcelProperty("逾期天数") @ColumnWidth(10)
    private Integer overdueDays;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("对账人")
    private String reconcileBy;

    @ExcelProperty("创建时间") @ColumnWidth(20)
    private LocalDateTime createTime;

    @ExcelProperty("备注") @ColumnWidth(30)
    private String remark;
}
