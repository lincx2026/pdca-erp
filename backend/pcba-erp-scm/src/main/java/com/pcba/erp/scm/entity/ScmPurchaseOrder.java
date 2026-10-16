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
@TableName("scm_purchase_order")
public class ScmPurchaseOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long poId;
    @Size(max=50) private String poNo;
    /** 采购模式: NORMAL正常采购 / CONSIGN寄售 / RUSH紧急采购 / FRAME框架订单 (原来就有, 不要动值域) */
    @Size(max=20) private String poType;
    /**
     * 采购大类（采购分工维度：电子物料采购 vs 配件采购 vs 备品采购）
     *   ELECTRONIC 电子物料&辅料&包材 → 入库草稿自动进 WMS 电子仓
     *   ACCESSORY  外购配件(线材/电池/适配器) → 入库草稿自动进 配件仓 1853
     *   SPARE      备品备件 → 暂未接入独立仓
     *  与 po_type 两个维度正交: 一笔 RUSH 紧急采购 仍然可以是 ACCESSORY(配件) 或 ELECTRONIC(电子)
     */
    @Size(max=20) private String bizCategory;
    private Long requisitionId;
    @NotNull private Long supplierId;
    @Size(max=50) private String supplierCode;
    @Size(max=200) private String supplierName;
    @Size(max=64) private String contactName;
    @Size(max=32) private String contactPhone;
    private Long buyerId;
    @Size(max=64) private String buyerName;
    private Long deptId;
    private LocalDate poDate;
    private LocalDate deliveryDate;
    private Long warehouseId;
    @Size(max=10) private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal taxRate;
    private BigDecimal undiscAmount;
    private BigDecimal discountRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    @Size(max=100) private String paymentTerm;
    @Size(max=100) private String deliveryTerm;
    @Size(max=50) private String transportType;
    @Size(max=500) private String incAttach;
    /** 0=草稿 1=提交 2=已审核 3=已下单 4=部分到货 5=全部到货 6=已完成 7=已驳回 9=取消 */
    private Integer status;
    private Integer signStatus;
    private LocalDateTime signTime;
    @Size(max=64) private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=500) private String rejectReason;
    @Size(max=500) private String closeReason;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
