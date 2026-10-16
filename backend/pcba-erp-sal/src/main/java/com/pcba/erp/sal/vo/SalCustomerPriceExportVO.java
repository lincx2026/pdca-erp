package com.pcba.erp.sal.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/** 客户专属价格表导出VO */
@Data
@ColumnWidth(16)
public class SalCustomerPriceExportVO {
    @ExcelProperty("客户编码") private String customerCode;
    @ExcelProperty("客户名称") private String customerName;
    @ExcelProperty("物料编码") private String materialCode;
    @ExcelProperty("物料名称") private String materialName;
    @ExcelProperty("规格")     private String specification;
    @ExcelProperty("单位")     private String unit;
    @ExcelProperty("币种")     private String currency;
    @ExcelProperty("未税单价") private BigDecimal unitPrice;
    @ExcelProperty("税率(%)")  private BigDecimal taxRate;
    @ExcelProperty("起订量")   private BigDecimal minQty;
    @ExcelProperty("生效日期") private String validFrom;
    @ExcelProperty("失效日期") private String validTo;
    @ExcelProperty("报价来源") private String quoteSource;
    @ExcelProperty("是否默认") private String isDefault;
    @ExcelProperty("状态")     private String status;
    @ExcelProperty("审核人")   private String auditBy;
    @ExcelProperty("驳回理由") private String rejectReason;
    @ExcelProperty("备注")     private String remark;
}
