package com.pcba.erp.pdm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工艺路线主档
 * - route_code 唯一
 * - 一个产品可有多个工艺路线, 但只能有1个 is_default=1
 * - 状态 1启用 0停用
 */
@Data
@TableName("pdm_process_route")
public class PdmProcessRoute implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long routeId;

    private Long productId;

    /** 关联产品物料编码(来自 pdm_material.material_code, 非表字段) */
    @TableField(exist = false)
    private String productCode;
    /** 关联产品物料名称(来自 pdm_material.material_name, 非表字段) */
    @TableField(exist = false)
    private String productName;

    /** 绑定BOM版本ID(空=所有版本共用) */
    private Long bomVersionId;
    /** 关联BOM编码(来自 pdm_bom.bom_code, 非表字段) */
    @TableField(exist = false)
    private String bomVersionCode;

    private String routeName;
    private String routeCode;

    /** 0否 1是 */
    private Integer isDefault;

    /** 1启用 0停用 */
    private Integer status;

    /** 总标准工时(分钟/PCS, 由各工序累加, 存冗余方便列表展示) */
    @TableField(exist = false)
    private BigDecimal totalCycleMin;

    /** 总标准人工数(冗余) */
    @TableField(exist = false)
    private Integer totalLaborQty;

    /** 目标总良率(各工序良率相乘得到) */
    @TableField(exist = false)
    private BigDecimal overallYield;

    private String remark;

    /* ============ 公共字段 ============ */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /* ============ 非DB字段 ============ */
    /** 工艺步骤清单（保存/详情时传递） */
    @TableField(exist = false)
    private java.util.List<PdmProcessStep> steps;
}
