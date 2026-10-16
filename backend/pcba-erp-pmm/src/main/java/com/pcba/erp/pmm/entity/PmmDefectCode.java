package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 不良代码字典 pmm_defect_code (辅助字典, 前端下拉/后端校验) */
@Data
@TableName("pmm_defect_code")
public class PmmDefectCode implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long codeId;

    @NotBlank(message = "不良代码不能为空")
    @Size(max = 20)
    private String defectCode;

    @NotBlank(message = "不良名称不能为空")
    @Size(max = 100)
    private String defectName;

    /** SMT / TH / ASSY / TEST / PKG / 其他 */
    @Size(max = 50)
    private String defectCategory;

    /** MINOR / MAJOR / CRITICAL / SUGGESTION */
    @Size(max = 20)
    private String defaultLevel;

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
}
