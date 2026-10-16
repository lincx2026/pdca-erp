package com.pcba.erp.sal.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售开票单(蓝字)
 * 状态机: 0草稿 → 1已开(回写应收invoiced_amount) → 2作废(回退)
 * 一单多票: 一个应收单可分多次开票
 */
@Data
@TableName("sal_invoice")
public class SalInvoice implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long invoiceId;
    @Size(max=32) private String invoiceNo;
    @Size(max=2) private String invoiceType;        // 1专票 2普票 3电子专票 4电子普票
    @NotNull private Long customerId;
    @Size(max=30) private String customerCode;
    @Size(max=200) private String customerName;
    @NotNull private LocalDate invoiceDate;
    private BigDecimal undiscAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    @Size(max=10) private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal cnyTotalAmount;
    @Size(max=200) private String buyerName;          // 购方名称
    @Size(max=30) private String buyerTaxNo;          // 购方税号
    @Size(max=200) private String buyerAddrPhone;     // 购方地址电话
    @Size(max=200) private String buyerBankAccount;  // 购方开户行及账号
    private Integer redFlag;                          // 0蓝字 1红字(预留)
    private Long sourceInvoiceId;                     // 红冲原发票ID(预留)
    /** 0草稿 1已开 2作废 */
    private Integer status;
    @Size(max=64) private String issueBy;
    private LocalDateTime issueTime;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
