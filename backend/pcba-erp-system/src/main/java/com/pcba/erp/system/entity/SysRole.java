package com.pcba.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 角色表 sys_role */
@Data
@TableName("sys_role")
public class SysRole implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long roleId;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 30, message = "角色名称不超过30")
    private String roleName;

    @NotBlank(message = "角色权限字符串不能为空")
    @Size(max = 100, message = "角色权限字符串不超过100")
    private String roleKey;

    /** 显示顺序 */
    private Integer roleSort;

    /** 数据范围 1=全部 2=本部门 3=本部门及以下 4=本人 */
    private Integer dataScope;

    /** 菜单树选择子项是否关联显示 (0=父子联动 1=不联动) */
    private Integer menuCheckStrictly;
    private Integer deptCheckStrictly;

    /** 1=正常 0=停用 */
    private Integer status;

    @TableLogic
    private String delFlag;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private String remark;

    // ========= 扩展 =========
    @TableField(exist = false) private Boolean flag;
    @TableField(exist = false) private List<Long> menuIds;
    @TableField(exist = false) private List<Long> deptIds;
    @TableField(exist = false) private List<String> permissions;
}
