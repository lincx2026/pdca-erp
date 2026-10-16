package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 工单BOM行 pmm_work_order_bom */
@Data
@TableName("pmm_work_order_bom")
public class PmmWorkOrderBom implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long bomLineId;

    @NotNull(message = "工单ID不能为空")
    private Long workOrderId;

    private Integer lineNo;
    private Long parentMaterialId;

    @NotNull(message = "子件物料ID不能为空")
    private Long materialId;

    @Size(max = 50)
    private String materialCode;
    @Size(max = 200)
    private String materialName;
    @Size(max = 200)
    private String specification;
    @Size(max = 20)
    private String unit;

    private BigDecimal qtyPerUnit;
    private BigDecimal requiredQty;
    private BigDecimal issuedQty;
    private BigDecimal returnedQty;
    private BigDecimal scrapRate;

    private Long warehouseId;

    @Size(max = 500)
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
