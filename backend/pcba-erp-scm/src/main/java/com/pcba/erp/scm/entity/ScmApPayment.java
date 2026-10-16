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
@TableName("scm_ap_payment")
public class ScmApPayment implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long paymentId;
    @Size(max=50) private String paymentNo;
    @NotNull private Long supplierId;
    @Size(max=50) private String supplierCode;
    @Size(max=200) private String supplierName;
    @Size(max=200) private String payeeName;
    @Size(max=200) private String payeeBank;
    @Size(max=50) private String payeeAccount;
    @Size(max=30) private String payeeSwift;
    @Size(max=200) private String payBankName;
    @Size(max=50) private String payBankAccount;
    @Size(max=20) private String payChannel;
    @NotNull private LocalDate payDate;
    @Size(max=10) private String payCurrency;
    private BigDecimal payExchangeRate;
    private BigDecimal totalAmount;
    private BigDecimal cnyTotalAmount;
    @Size(max=10) private String feeBearer;
    private BigDecimal feeAmount;
    @Size(max=100) private String waterNo;
    @Size(max=100) private String invoiceNo;
    private BigDecimal invoiceAmount;
    private Long voucherId;
    private Long planId;
    private Integer planPhaseNo;
    private Long payableId;
    /** 0=草稿 1=已审批 2=付款成功 3=付款失败 9=作废 */
    private Integer status;
    @Size(max=64) private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=64) private String payBy;
    private LocalDateTime payTime;
    @Size(max=500) private String failReason;
    @Size(max=500) private String attachment;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
