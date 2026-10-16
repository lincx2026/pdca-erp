package com.pcba.erp.pdm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * BOM 主档
 * - bom_code 唯一；product_material_id + version 联合唯一
 * - 状态机: 0草稿 → 1审批中 → (2已启用 / 4驳回) → 3停用 / 4归档
 */
@Data
@TableName("pdm_bom")
public class PdmBom implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long bomId;

    @NotBlank(message = "BOM编号不能为空")
    @Size(max = 64, message = "BOM编号不超过64字符")
    private String bomCode;

    @NotBlank(message = "BOM名称不能为空")
    @Size(max = 200, message = "BOM名称不超过200字符")
    private String bomName;

    /** 产成品/半成品 物料ID */
    @NotNull(message = "成品物料ID不能为空")
    private Long productMaterialId;

    private String productMaterialCode;
    private String productMaterialName;

    @Size(max = 500)
    private String specification;

    @NotBlank(message = "版本号不能为空")
    @Size(max = 30, message = "版本号不超过30字符")
    private String version;

    /** 同成品只能有1个默认版本 */
    private Integer isDefault;

    /** 产出基数(通常1) */
    private BigDecimal quantity;

    @Size(max = 20)
    private String unit;

    /** 1制造 2工程 3销售 4计划 5替代 */
    private Integer bomType;

    /** 客户ID(销售BOM专用:按客户区分齐套) */
    private Long customerId;
    /** 机型/齐套型号(如跑步机X1/椭圆机E5) */
    @Size(max = 100)
    private String kitModel;

    /** 0草稿 1审批中 2启用 3停用 4归档 */
    private Integer status;

    private LocalDate effectiveDate;
    private LocalDate expiryDate;

    /** 关联ECO变更单 */
    @Size(max = 64)
    private String ecoNo;

    private String auditBy;
    private LocalDateTime auditTime;
    @Size(max = 500)
    private String rejectReason;

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
    /** 子件清单（保存/详情/新增时传递） */
    @TableField(exist = false)
    private java.util.List<PdmBomLine> lines;
    /** 用于列表查询：成品分类ID筛选 */
    @TableField(exist = false)
    private Long categoryId;
}
