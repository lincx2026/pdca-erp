package com.pcba.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 菜单表 sys_menu */
@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long menuId;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称不超过50")
    private String menuName;

    private Long parentId;
    private Integer orderNum;

    @Size(max = 200, message = "路由地址不超过200")
    private String path;

    @Size(max = 255, message = "组件路径不超过255")
    private String component;

    /** 是否外部链接 1=是 0=否 */
    private Integer isFrame;

    /** 是否缓存 1=是 0=否 */
    private Integer isCache;

    /** M=目录 C=菜单 F=按钮 */
    private String menuType;

    /** 0=显示 1=隐藏 */
    private Integer visible;

    /** 0=正常 1=停用 */
    private Integer status;

    /** 权限标识, 例: system:user:list */
    @Size(max = 100, message = "权限标识不超过100")
    private String perms;

    /** 菜单图标 */
    private String icon;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private String remark;

    // ===== 扩展 =====
    @TableField(exist = false) private List<SysMenu> children;
}
