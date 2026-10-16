package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 厂区/产线 company_factory (树形结构, parent_id 自关联)
 * node_type: FACTORY / CATEGORY / LINE
 */
@Data
@TableName("company_factory")
public class CompanyFactory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long factoryId;

    /** 父节点 ID, 根节点为 0 */
    private Long parentId;

    private String factoryCode;
    private String factoryName;

    /** FACTORY / CATEGORY / LINE */
    private String nodeType;

    private String address;
    private String managerName;
    private String managerPhone;

    /** 0=启用 1=停用 */
    private Integer status;

    private Integer orderNum;

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

    /** 子节点(树形结构, 非数据库字段) */
    @TableField(exist = false)
    private List<CompanyFactory> children;
}
