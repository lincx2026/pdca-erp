package com.pcba.erp.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存台账 wms_inventory
 * - 记录某仓库某物料某批次的库存数量
 * - availableQty = quantity - lockedQty (可用库存)
 */
@Data
@TableName("wms_inventory")
public class WmsInventory implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long inventoryId;

    @NotNull(message = "仓库ID不能为空")
    private Long warehouseId;

    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /** 批次号 */
    @Size(max = 100)
    private String batchNo;

    /** 库存数量 */
    private BigDecimal quantity;

    /** 锁定量 (已分配未出库) */
    private BigDecimal lockedQty;

    /** 可用数量 = quantity - lockedQty */
    private BigDecimal availableQty;

    @Size(max = 10)
    private String unit;

    /** 状态: 1=正常 0=冻结 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ============== 非数据库字段 (联表回填) ==============
    @TableField(exist = false)
    private String warehouseName;

    @TableField(exist = false)
    private String warehouseCode;

    @TableField(exist = false)
    private String materialCode;

    @TableField(exist = false)
    private String materialName;

    @TableField(exist = false)
    private String specification;
}
