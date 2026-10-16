package com.pcba.erp.sal.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售退货导出 VO（EasyExcel）
 */
@Data
@ColumnWidth(16)
public class SalSalesReturnExportVO {

    @ExcelProperty("退货编号") @ColumnWidth(22)
    private String returnNo;

    @ExcelProperty("客户编码") @ColumnWidth(18)
    private String customerCode;

    @ExcelProperty("客户名称") @ColumnWidth(28)
    private String customerName;

    @ExcelProperty("关联销售订单") @ColumnWidth(22)
    private String soNo;

    @ExcelProperty("退货日期") @ColumnWidth(14)
    private LocalDate returnDate;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("退货金额") @ColumnWidth(14)
    private BigDecimal totalAmount;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("关联入库单") @ColumnWidth(16)
    private String stockInId;

    @ExcelProperty("创建时间") @ColumnWidth(20)
    private LocalDateTime createTime;

    @ExcelProperty("备注") @ColumnWidth(30)
    private String remark;
}
