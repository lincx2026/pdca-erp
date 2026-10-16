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
@TableName("sal_ar_receipt")
public class SalArReceipt implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long receiptId;
    @Size(max=50) private String receiptNo;
    @NotNull private Long customerId;
    @Size(max=50) private String customerCode;
    @Size(max=200) private String customerName;
    @Size(max=200) private String payerName;
    @Size(max=200) private String payerBank;
    @Size(max=50) private String payerAccount;
    @Size(max=30) private String payerSwift;
    @Size(max=200) private String recvBankName;
    @Size(max=50) private String recvBankAccount;
    @Size(max=20) private String recvChannel;
    @NotNull private LocalDate receiptDate;
    @Size(max=10) private String receiptCurrency;
    private BigDecimal exchangeRate;
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
    private Long receivableId;
    /** 0=草稿 1=已审批 2=收款成功 3=收款失败 9=作废 */
    private Integer status;
    @Size(max=64) private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=64) private String receiptBy;
    private LocalDateTime receiptTime;
    @Size(max=500) private String failReason;
    @Size(max=500) private String attachment;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
