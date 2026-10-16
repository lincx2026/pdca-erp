package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 工序任务 pmm_process_task */
@Data
@TableName("pmm_process_task")
public class PmmProcessTask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long taskId;

    @NotNull(message = "工单ID不能为空")
    private Long workOrderId;

    private Long stepId;

    @Size(max = 50)
    private String processCode;

    @NotBlank(message = "工序名称不能为空")
    @Size(max = 200)
    private String processName;

    private Integer sequence;

    @Size(max = 100)
    private String workCenter;

    private Long assignedUserId;

    @Size(max = 64)
    private String assignedUser;

    private BigDecimal planQty;
    private BigDecimal inputQty;
    private BigDecimal passQty;
    private BigDecimal failQty;
    private BigDecimal cycleTimeSec;

    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;

    /** 0=待派发 10=已派发 20=加工中 30=暂停 40=已完成 50=已流转 90=取消 */
    private Integer status;

    private Integer isKeyProcess;
    private Integer isQcRequired;

    private Long qcUserId;

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
