package com.pcba.erp.scm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scm_ap_payable")
public class ScmApPayable implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long payableId;
    @Size(max=50) private String payableNo;
    @Size(max=20) private String bizType;
    @NotNull private Long supplierId;
    @Size(max=50) private String supplierCode;
    @Size(max=200) private String supplierName;
    @Size(max=10) private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal undiscAmount;
    private BigDecimal discountAmount;
    private BigDecimal roundingAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal cnyTotalAmount;
    private BigDecimal paidAmount;
    private BigDecimal unpaidAmount;
    @NotNull private LocalDate bizDate;
    private Integer creditDays;
    @NotNull private LocalDate dueDate;
    private Integer isOverdue;
    private Integer overdueDays;
    /** 0=待对账 1=对账中 2=对账完成 3=部分付 4=付款完成 9=作废 */
    private Integer status;
    @Size(max=64) private String reconcileBy;
    private LocalDateTime reconcileTime;
    @Size(max=64) private String signBy;
    private LocalDateTime signTime;
    private Long voucherId;
    @Size(max=500) private String attachment;
    @Size(max=80) private String invoiceNo;
    private LocalDate invoiceDate;
    private BigDecimal invoiceTaxAmount = BigDecimal.ZERO;
    private BigDecimal invoiceExclusiveAmount = BigDecimal.ZERO;
    private LocalDate invoiceReceivedDate;
    private Integer invoiceStatus = 0;
    @Size(max=500) private String invoiceImg;
    /** 逗号分隔stock_in_id列表 */
    @Size(max=1000) private String originStockInIds;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
