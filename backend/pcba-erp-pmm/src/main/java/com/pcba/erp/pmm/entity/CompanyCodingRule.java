package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 编码规则 company_coding_rule
 */
@Data
@TableName("company_coding_rule")
public class CompanyCodingRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long ruleId;

    private String moduleName;
    private String moduleDesc;

    private String prefix;

    /** 日期格式, 如 yyyyMMdd, 为空则不拼接日期 */
    private String dateFormat;

    private Integer seqLength;
    private Integer currentSeq;

    /** 分隔符(MySQL 保留字, 字段名加反引号) */
    @TableField(value = "`separator`")
    private String separator;

    /** 0=启用 1=停用 */
    private Integer status;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
