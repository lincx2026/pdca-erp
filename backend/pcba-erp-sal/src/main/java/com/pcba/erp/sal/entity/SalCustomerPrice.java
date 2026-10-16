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
 * 客户专属价格表(销售报价, 阶梯价, 审批流)
 * 对称 scm_supplier_price, supplier_id→customer_id, 加冗余名称字段 + 审批流字段
 * 状态机: 0草稿→1待审→2已审(生效)→3停用; 4驳回(可改后重提)
 */
@Data
@TableName("sal_customer_price")
public class SalCustomerPrice implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long priceId;
    @NotNull private Long customerId;
    @Size(max=30)  private String customerCode;
    @Size(max=200) private String customerName;
    @NotNull private Long materialId;
    @Size(max=50)  private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=200) private String specification;
    @Size(max=20)  private String unit;
    @Size(max=10)  private String currency;
    @NotNull private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal minQty;
    @NotNull private LocalDate validFrom;
    @NotNull private LocalDate validTo;
    @Size(max=50)  private String quoteSource;
    private Integer isDefault;
    private Integer status;
    /** 转单后生成的销售订单ID (status=5时非空, 追踪已被哪张订单使用) */
    private Long orderId;
    /** 转单后生成的销售订单行ID */
    private Long orderLineId;
    /** 含税单价 → 未税单价(瞬态, 供订单行使用) */
    @TableField(exist=false)
    private BigDecimal untaxedUnitPrice;
    @Size(max=64)  private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=500) private String rejectReason;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
