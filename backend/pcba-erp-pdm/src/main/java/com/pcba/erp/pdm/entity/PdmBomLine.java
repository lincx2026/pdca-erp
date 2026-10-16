package com.pcba.erp.pdm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BOM 行项（单层；多层通过 parent_material_id 链接）
 * - 父项成品产出1单位，所需 component 数量 quantity × gross(=1/(1-lossRate))
 */
@Data
@TableName("pdm_bom_line")
public class PdmBomLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long lineId;

    @NotNull(message = "BOM ID不能为空")
    private Long bomId;

    private Integer lineNo;

    /** 父项物料ID（顶层=成品；多层BOM时中间层=半成品） */
    @NotNull(message = "父项物料ID不能为空")
    private Long parentMaterialId;

    /** 子件物料ID */
    @NotNull(message = "子件物料ID不能为空")
    private Long componentMaterialId;

    private String componentMaterialCode;
    private String componentMaterialName;
    @Size(max = 500)
    private String componentSpec;

    /** 净用量 */
    @NotNull(message = "用量不能为空")
    private BigDecimal quantity;

    @Size(max = 20)
    private String unit;

    /** 损耗率 0~0.5 */
    private BigDecimal lossRate;

    /** 毛需求 = qty / (1 - lossRate), 保存时自动计算 */
    private BigDecimal grossQty;

    /** 引用替代组号 */
    @Size(max = 30)
    private String altGroupId;

    /** 虚拟件 0否 1是 */
    private Integer isPhantom;
    /** 可选件 0否 1是 */
    private Integer isOptional;

    /** 供应类型 1采购 2自制 3委外 4虚拟 5领用 */
    private Integer supplyType;

    /** 位号, 逗号分隔（R1,R2,C5） */
    @Size(max = 500)
    private String referenceDesignator;

    @Size(max = 200)
    private String mfrPartNo;

    /** 0停用 1生效 */
    private Integer status;

    private LocalDate effectiveDate;
    private LocalDate expiryDate;

    @Size(max = 500)
    private String remark;

    /* ============ 公共字段 ============ */
    private Long deptId;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /* ============ 非DB字段 ============ */
    /** 父项编码(树展示用) */
    @TableField(exist = false)
    private String parentMaterialCode;
    @TableField(exist = false)
    private String parentMaterialName;
    /** 递归子项(BOM多层展开返回) */
    @TableField(exist = false)
    private List<PdmBomLine> children;
    /** 展开层级(0=顶层) */
    @TableField(exist = false)
    private Integer level;
    /** 累计路径用量(多层展开累加, 非DB) */
    @TableField(exist = false)
    private BigDecimal pathQty;
}
