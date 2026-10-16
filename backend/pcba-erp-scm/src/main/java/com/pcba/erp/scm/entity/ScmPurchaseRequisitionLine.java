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
@TableName("scm_purchase_requisition_line")
public class ScmPurchaseRequisitionLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long lineId;
    @NotNull private Long requisitionId;
    @NotNull private Integer lineNo;
    @NotNull private Long materialId;
    @Size(max=50) private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=200) private String specification;
    @Size(max=20) private String unit;
    @NotNull private BigDecimal qty;
    private BigDecimal estimatePrice;
    private BigDecimal estimateAmount;
    private LocalDate requiredDate;
    private Long convertedPoId;
    private Long convertedPoLineId;
    private BigDecimal convertedQty = BigDecimal.ZERO;
    private Long supplierId;
    @Size(max=50) private String supplierCode;
    @Size(max=200) private String supplierName;
    private Long supplierPriceId;
    /** 0草稿1提交2已审3已转完7供方缺失 */
    private Integer status = 0;
    @Size(max=1000) private String errorMsg;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
