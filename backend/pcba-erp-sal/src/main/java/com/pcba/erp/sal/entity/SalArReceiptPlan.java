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
@TableName("sal_ar_receipt_plan")
public class SalArReceiptPlan implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long planId;
    @Size(max=50) private String planNo;
    @NotNull private Long receivableId;
    @Size(max=50) private String receivableNo;
    @NotNull private Long customerId;
    @Size(max=200) private String customerName;
    private Integer phaseNo;
    @Size(max=50) private String phaseName;
    private BigDecimal ratio;
    private BigDecimal planAmount;
    @NotNull private LocalDate planDate;
    private BigDecimal actualAmount;
    private BigDecimal unpaidAmount;
    /** 0=待收 1=部分收 2=已收清 3=收款中 9=作废 */
    private Integer status;
    @Size(max=200) private String recvBankName;
    @Size(max=50) private String recvBankAccount;
    @Size(max=200) private String payerName;
    @Size(max=200) private String payerBank;
    @Size(max=50) private String payerAccount;
    @Size(max=30) private String payerSwift;
    @Size(max=50) private String flowNo;
    @Size(max=64) private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
