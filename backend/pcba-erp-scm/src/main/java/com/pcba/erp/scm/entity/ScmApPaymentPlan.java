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
@TableName("scm_ap_payment_plan")
public class ScmApPaymentPlan implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long planId;
    @Size(max=50) private String planNo;
    @NotNull private Long payableId;
    @Size(max=50) private String payableNo;
    @NotNull private Long supplierId;
    @Size(max=200) private String supplierName;
    private Integer phaseNo;
    @Size(max=50) private String phaseName;
    private BigDecimal ratio;
    private BigDecimal planAmount;
    @NotNull private LocalDate planDate;
    private BigDecimal actualAmount;
    private BigDecimal unpaidAmount;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private Integer writeOffStatus = 0;
    /** 0=待付 1=部分付 2=已付清 3=付款中 9=作废 */
    private Integer status;
    @Size(max=200) private String payBankName;
    @Size(max=50) private String payBankAccount;
    @Size(max=200) private String payeeName;
    @Size(max=200) private String payeeBank;
    @Size(max=50) private String payeeAccount;
    @Size(max=30) private String payeeSwift;
    @Size(max=50) private String flowNo;
    @Size(max=64) private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
