package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 排产计划 pmm_schedule_plan */
@Data
@TableName("pmm_schedule_plan")
public class PmmSchedulePlan implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long planId;

    @NotNull(message = "排产日期不能为空")
    private LocalDate scheduleDate;

    /** DAY / NIGHT / OT */
    @Size(max = 20)
    private String shift;

    @NotNull(message = "工单ID不能为空")
    private Long workOrderId;

    private Long taskId;

    @Size(max = 100)
    private String workCenter;

    @Size(max = 50)
    private String machineCode;

    private Long operatorId;

    @Size(max = 64)
    private String operatorName;

    private BigDecimal planQty;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private Integer isFixed;
    private Integer sequence;

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
