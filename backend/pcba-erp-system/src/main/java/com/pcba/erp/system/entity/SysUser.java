package com.pcba.erp.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 用户表 sys_user */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long userId;

    /** 部门ID */
    private Long deptId;

    @NotBlank(message = "用户账号不能为空")
    @Size(min = 2, max = 30, message = "账号长度2-30")
    private String userName;

    @NotBlank(message = "用户昵称不能为空")
    @Size(max = 30, message = "昵称不超过30")
    private String nickName;

    /** 用户类型 00=系统 */
    private String userType;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱不超过50")
    private String email;

    @Size(max = 11, message = "手机号不超过11")
    private String phonenumber;

    /** 性别 0男 1女 2未知 */
    private String sex;

    private String avatar;

    /** 密码 (序列化时忽略) */
    @JsonIgnore
    private String password;

    /** 1正常 0停用，与认证状态和数据库默认值一致 */
    private Integer status;

    @TableLogic
    private String delFlag;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginDate;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private String remark;

    // ========== 非数据库字段 ==========
    /** 部门 */
    @TableField(exist = false)
    private SysDept dept;

    /** 角色ID列表(新增/修改时使用) */
    @TableField(exist = false)
    private List<Long> roleIds;

    /** 岗位ID列表(暂不用, 留空) */
    @TableField(exist = false)
    private List<Long> postIds;
}
