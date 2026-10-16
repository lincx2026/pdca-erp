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
@TableName("scm_purchase_order_line")
public class ScmPurchaseOrderLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long lineId;
    @NotNull private Long poId;
    @NotNull private Integer lineNo;
    private Long requisitionLineId;
    @NotNull private Long materialId;
    @Size(max=50) private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=200) private String specification;
    @Size(max=20) private String unit;
    @NotNull private BigDecimal orderQty;
    private BigDecimal receivedQty;
    private BigDecimal returnedQty;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal lineAmount;
    private BigDecimal discountRate;
    @NotNull private Integer qcRequired = 0;
    /** 0无需1待检2合格3让步4拒收5已完成 */
    @NotNull private Integer qcStatus = 0;
    private Long requisitionId;
    private LocalDate requiredDate;
    private Long warehouseId;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
