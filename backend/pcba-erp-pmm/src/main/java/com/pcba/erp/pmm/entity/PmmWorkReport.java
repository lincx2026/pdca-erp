package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 报工记录 pmm_work_report */
@Data
@TableName("pmm_work_report")
public class PmmWorkReport implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long reportId;

    @NotBlank(message = "报工单号不能为空")
    @Size(max = 50)
    private String reportNo;

    @NotNull(message = "工单ID不能为空")
    private Long workOrderId;

    @NotNull(message = "工序任务ID不能为空")
    private Long taskId;

    @Size(max = 200)
    private String processName;

    @Size(max = 100)
    private String workCenter;

    private Long operatorId;

    @Size(max = 64)
    private String operatorName;

    /** DAY / NIGHT / OT */
    @Size(max = 20)
    private String shift;

    /** START / END / PAUSE / PROGRESS */
    @Size(max = 20)
    private String reportType;

    private BigDecimal inputQty;
    private BigDecimal passQty;
    private BigDecimal failQty;
    private BigDecimal usedMinutes;

    @Size(max = 50)
    private String machineCode;

    @Size(max = 50)
    private String moldCode;

    private Integer isQcPass;

    @Size(max = 64)
    private String qcBy;
    private LocalDateTime qcTime;

    @Size(max = 500)
    private String qcRemark;

    @Size(max = 200)
    private String defectCodes;

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
