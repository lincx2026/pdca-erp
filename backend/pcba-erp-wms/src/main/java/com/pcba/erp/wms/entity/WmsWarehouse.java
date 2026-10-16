package com.pcba.erp.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仓库主档 wms_warehouse
 * - 仓库类型: RAW(原料) / SEMI(半成品) / FINISHED(成品) / DEFECTIVE(不良品)
 * - 状态: 1=启用 0=停用
 */
@Data
@TableName("wms_warehouse")
public class WmsWarehouse implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long warehouseId;

    /** 仓库编码, 唯一 */
    @NotBlank(message = "仓库编码不能为空")
    @Size(max = 50, message = "仓库编码不超过50字符")
    private String warehouseCode;

    @NotBlank(message = "仓库名称不能为空")
    @Size(max = 200, message = "仓库名称不超过200字符")
    private String warehouseName;

    /** 仓库类型: RAW/SEMI/FINISHED/DEFECTIVE */
    @Size(max = 20, message = "仓库类型不超过20字符")
    private String warehouseType;

    @Size(max = 500, message = "地址不超过500字符")
    private String address;

    /** 仓库管理员 */
    @Size(max = 50)
    private String manager;

    @Size(max = 30)
    private String phone;

    /** 1=启用 0=停用 */
    private Integer status;

    @Size(max = 1000)
    private String remark;

    /** 部门ID (数据归属) */
    private Long deptId;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
