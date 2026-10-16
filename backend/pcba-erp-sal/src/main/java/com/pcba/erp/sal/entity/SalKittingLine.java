package com.pcba.erp.sal.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售齐套明细行 sal_kitting_line
 * - 每行对应 BOM 展开后的一个叶子物料需求
 * - 贯穿 需求 → 库存检查 → 缺料标记 → 出库/请购 全流程
 */
@Data
@TableName("sal_kitting_line")
public class SalKittingLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long kittingLineId;

    @NotNull
    private Long kittingId;

    private Integer lineNo;

    @NotNull
    private Long materialId;
    @Size(max = 50)
    private String materialCode;
    @Size(max = 200)
    private String materialName;
    @Size(max = 200)
    private String specification;
    @Size(max = 10)
    private String unit;

    private Long bomLineId;

    /** 需求量(BOM展开累计pathQty × kitQty) */
    private BigDecimal reqQty;
    /** 可用库存(跨批次汇总) */
    private BigDecimal availableQty;
    /** 缺口量(req-available, >=0) */
    private BigDecimal shortageQty;

    private Long warehouseId;
    @Size(max = 50)
    private String batchNo;

    /** 0=齐套 1=缺料 */
    private Integer isShortage;

    /**
     * 0=待检 1=齐套 2=缺料 3=已出库
     */
    private Integer lineStatus;

    @Size(max = 300)
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
