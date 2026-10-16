package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 员工附件 hrm_employee_attachment
 * 用于存储劳动合同、身份证、学历证书、资格证书等
 */
@Data
@TableName("hrm_employee_attachment")
public class HrmEmployeeAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long attachmentId;

    private Long employeeId;

    /** 附件类型: contract合同 id_card身份证 education学历 cert资格证书 other其他 */
    private String attachType;

    /** 原始文件名 */
    private String fileName;

    /** OSS 对象键 */
    private String objectKey;

    /** 文件大小(字节) */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

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
}
