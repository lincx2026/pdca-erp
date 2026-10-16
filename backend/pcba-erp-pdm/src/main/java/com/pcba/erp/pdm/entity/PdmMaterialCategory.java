package com.pcba.erp.pdm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物料分类表 pdm_material_category
 * - 树形结构: parent_id + ancestors
 * - category_code 是物料编码前缀 (如 PCB / RES / CAP / IC)
 */
@Data
@TableName("pdm_material_category")
public class PdmMaterialCategory implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long categoryId;

    /** 父分类ID, 0=顶级 */
    private Long parentId;

    /** 祖级列表 逗号分隔, 根节点=0 */
    private String ancestors;

    /**
     * 分类编码前缀 (唯一)
     * 物料编码生成规则: {category_code}-{YY}-{4位流水}
     */
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 30, message = "分类编码不超过30字符")
    private String categoryCode;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100, message = "分类名称不超过100字符")
    private String categoryName;

    /** 显示顺序, 越小越靠前 */
    private Integer orderNum;

    /** 1=正常 0=停用 */
    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 子分类 (非数据库字段) */
    @TableField(exist = false)
    private List<PdmMaterialCategory> children;

    /** 分类下物料数量, 前端展示用 */
    @TableField(exist = false)
    private Long materialCount;
}
