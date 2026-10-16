package com.pcba.erp.sal.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sal_ar_receivable")
public class SalArReceivable implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long receivableId;
    @Size(max=50) private String receivableNo;
    @Size(max=20) private String bizType;
    @NotNull private Long customerId;
    @Size(max=50) private String customerCode;
    @Size(max=200) private String customerName;
    @Size(max=10) private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal undiscAmount;
    private BigDecimal discountAmount;
    private BigDecimal roundingAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal cnyTotalAmount;
    private BigDecimal receivedAmount;
    private BigDecimal invoicedAmount;   // 已开票金额(开票单回写)
    private BigDecimal unreceivedAmount;
    @NotNull private LocalDate bizDate;
    private Integer creditDays;
    @NotNull private LocalDate dueDate;
    private Integer isOverdue;
    private Integer overdueDays;
    /** 0=待对账 1=对账中 2=对账完成 3=部分收 4=收款完成 9=作废 */
    private Integer status;
    @Size(max=64) private String reconcileBy;
    private LocalDateTime reconcileTime;
    @Size(max=64) private String signBy;
    private LocalDateTime signTime;
    private Long voucherId;
    @Size(max=500) private String attachment;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
