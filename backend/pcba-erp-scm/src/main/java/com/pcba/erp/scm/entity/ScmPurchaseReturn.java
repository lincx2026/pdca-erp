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
@TableName("scm_purchase_return")
public class ScmPurchaseReturn implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long returnId;
    @Size(max=50) private String returnNo;
    private Long poId;
    private Long deliveryId;
    @NotNull private Long supplierId;
    @Size(max=200) private String supplierName;
    private LocalDate returnDate;
    private Long warehouseId;
    @Size(max=200) private String returnReason;
    private BigDecimal totalQty;
    private BigDecimal totalAmount;
    private Integer hasRefund;
    private BigDecimal refundAmount;
    /** 1=退货后需补请购补货；0/NULL=不需要 */
    private Integer hasReplenishment;
    private Long stockOutId;
    /** 0=草稿 1=提交 2=审核通过 3=出库中 4=已完成 5=供方已签收 9=取消 */
    private Integer status;
    @Size(max=64) private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=64) private String signBy;
    private LocalDateTime signTime;
    @Size(max=500) private String rejectReason;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
