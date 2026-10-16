package com.pcba.erp.sal.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售齐套主表 sal_kitting
 * - 关联销售订单 + 销售BOM, 按BOM展开×套数检查库存齐套性
 * - 状态机: 0待检查 → 1部分齐套 / 2齐套完成 → 3已生成出库 / 4已生成请购 / 5已取消
 */
@Data
@TableName("sal_kitting")
public class SalKitting implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long kittingId;

    /** 齐套单号: SK-yyyyMMdd-4位序号 */
    @Size(max = 50)
    private String kittingNo;

    @NotNull(message = "销售订单ID不能为空")
    private Long soId;
    @Size(max = 50)
    private String soNo;
    private Long soLineId;
    /** 来源报价价目表条目ID (P1: 由报价转单后追踪) */
    private Long priceId;

    private Long customerId;
    @Size(max = 200)
    private String customerName;

    @NotNull(message = "BOM ID不能为空")
    private Long bomId;
    @Size(max = 100)
    private String kitModel;

    private Long productMaterialId;
    @Size(max = 50)
    private String productMaterialCode;
    @Size(max = 200)
    private String productMaterialName;

    @NotNull(message = "齐套数量不能为空")
    private BigDecimal kitQty;

    private Long warehouseId;

    private Integer totalLines;
    private BigDecimal totalRequiredQty;
    private BigDecimal totalAvailableQty;
    private BigDecimal totalShortageQty;

    private Long stockOutId;
    private Long requisitionId;

    private LocalDateTime checkTime;
    private LocalDateTime completeTime;

    private Long operatorId;
    @Size(max = 64)
    private String operatorName;

    /**
     * 0=待检查 1=部分齐套 2=齐套完成 3=已生成出库 4=已生成请购 5=已取消
     */
    private Integer status;

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

    /* ============ 非DB字段 ============ */
    @TableField(exist = false)
    private List<SalKittingLine> lines;
}
