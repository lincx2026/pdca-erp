package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统参数 company_param
 * param_type: STRING / NUMBER / BOOLEAN
 */
@Data
@TableName("company_param")
public class CompanyParam implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long paramId;

    private String paramKey;
    private String paramValue;
    private String paramName;

    /** STRING / NUMBER / BOOLEAN */
    private String paramType;

    private String category;

    /** 0=启用 1=停用 */
    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
