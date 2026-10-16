package com.pcba.erp.scm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scm_supplier")
public class ScmSupplier implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long supplierId;
    @NotBlank @Size(max=30) private String supplierCode;
    @NotBlank @Size(max=200) private String supplierName;
    @Size(max=50) private String shortName;
    @Size(max=10) private String level;
    @Size(max=50) private String contact;
    @Size(max=20) private String phone;
    @Size(max=100) private String email;
    @Size(max=500) private String address;
    /** 1=启用 0=停用 */
    private Integer status;
    @Size(max=1000) private String remark;
    /* ================= 放账/财务字段 (应付对账模块新增) ================= */
    /** 信用账期(天)：0=现款现货 >0=放账天数 */
    private Integer creditDays;
    /** 信用额度(CNY)：0=不控额度 >0=应付未付+新单>额度即拦截下单 */
    private BigDecimal creditLimit;
    /** 付款条款描述(月结30天/货到7天/TT60天等) */
    @Size(max=100) private String paymentTerms;
    /** 纳税人识别号(开发票用) */
    @Size(max=50) private String taxNo;
    /** 收款单位名称(付款带出) */
    @Size(max=200) private String payeeName;
    /** 开户行 */
    @Size(max=200) private String payeeBank;
    /** 银行账号 */
    @Size(max=50) private String payeeAccount;
    /** SWIFT(外币收汇) */
    @Size(max=30) private String payeeSwift;
    /* =============================== */
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
