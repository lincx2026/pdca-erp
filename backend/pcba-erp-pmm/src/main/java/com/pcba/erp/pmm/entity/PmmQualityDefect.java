package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 质量不良记录 pmm_quality_defect */
@Data
@TableName("pmm_quality_defect")
public class PmmQualityDefect implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long defectId;

    private Long workOrderId;
    private Long taskId;
    private Long reportId;
    private Long materialId;

    @Size(max = 50)
    private String materialCode;

    @Size(max = 200)
    private String processName;

    @NotBlank(message = "不良代码不能为空")
    @Size(max = 20)
    private String defectCode;

    @Size(max = 100)
    private String defectName;

    /** CRITICAL / MAJOR / MINOR / SUGGESTION */
    @NotBlank(message = "不良等级不能为空")
    @Size(max = 20)
    private String defectLevel;

    @NotNull(message = "不良数量不能为空")
    private BigDecimal defectQty;

    private BigDecimal sampleQty;

    /** IQC / IPQC / FQC / OQC / SPC */
    @NotBlank(message = "检查类型不能为空")
    @Size(max = 20)
    private String checkType;

    private Long inspectorId;

    @Size(max = 64)
    private String inspectorName;

    private LocalDateTime checkTime;

    @Size(max = 1000)
    private String rootCause;

    @Size(max = 1000)
    private String correctiveAction;

    @Size(max = 1000)
    private String preventiveAction;

    /** PENDING / REWORK / SCRAP / CONCESSION / RETURN */
    @Size(max = 20)
    private String disposition;

    @Size(max = 64)
    private String handledBy;
    private LocalDateTime handleTime;

    @Size(max = 1000)
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
