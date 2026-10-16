package com.pcba.erp.pdm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户料号映射 pdm_customer_material_map
 * - 将客户料号 ↔ 工厂内部物料关联，支持一对多（同一客户料号只能映射一条工厂物料）
 * - 状态: 1=启用 0=停用
 */
@Data
@TableName("pdm_customer_material_map")
public class PdmCustomerMaterialMap implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long mapId;

    /** 客户ID（关联 scm_customer） */
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 客户名称（冗余，便于查询展示） */
    private String customerName;

    /** 工厂物料ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 客户料号（客户侧编号） */
    @NotBlank(message = "客户料号不能为空")
    @Size(max = 100, message = "客户料号不超过100字符")
    private String customerMaterialNo;

    /** 客户物料名称 */
    @Size(max = 200)
    private String customerMaterialName;

    /** 1=启用 0=停用 */
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

    // ============== 非数据库字段（联表回填） ==============

    /** 工厂物料编码（联表） */
    @TableField(exist = false)
    private String materialCode;

    /** 工厂物料名称（联表） */
    @TableField(exist = false)
    private String materialName;

    /** 物料规格型号（联表） */
    @TableField(exist = false)
    private String specification;

    /** 客户编码（联表） */
    @TableField(exist = false)
    private String customerCode;
}
