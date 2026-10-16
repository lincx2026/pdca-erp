package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工档案 hrm_employee
 */
@Data
@TableName("hrm_employee")
public class HrmEmployee implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long employeeId;

    /** 工号 */
    private String employeeNo;

    private String name;

    /** 性别 0男 1女 */
    private Integer gender;

    private String phone;
    private String email;
    private String idCard;

    /** 部门ID */
    private Long deptId;

    /** 岗位 */
    private String position;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate leaveDate;

    /** 状态 1在职 2试用期 3离职 */
    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 关联部门名称(非数据库字段) */
    @TableField(exist = false)
    private String deptName;
}
