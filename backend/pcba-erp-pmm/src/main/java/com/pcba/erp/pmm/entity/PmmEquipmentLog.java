package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备事件日志 pmm_equipment_log
 * 事件类型: STATUS_CHANGE状态变更 / MAINTAIN保养 / REPAIR维修 / CALIBRATION校准 / ACCIDENT事故 / OTHER其他
 */
@Data
@TableName("pmm_equipment_log")
public class PmmEquipmentLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long logId;

    @NotNull(message = "设备ID不能为空")
    private Long equipmentId;

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 50)
    private String equipmentNo;

    @NotBlank(message = "事件类型不能为空")
    @Size(max = 30)
    private String eventType;

    @NotBlank(message = "事件标题不能为空")
    @Size(max = 200)
    private String eventTitle;

    @NotNull(message = "事件发生时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;

    /** PASS成功 / FAIL失败 / PENDING处理中 */
    @Size(max = 20)
    private String eventResult;

    /** 状态变更事件: 变更前状态 */
    private Integer oldStatus;

    /** 状态变更事件: 变更后状态 */
    private Integer newStatus;

    private Long operatorId;

    @Size(max = 64)
    private String operatorName;

    @NotNull(message = "花费金额不能为空, 无花费填0")
    private BigDecimal costAmount;

    /** 附件 JSON: [{name, url}] */
    @Size(max = 4000)
    private String attachments;

    @Size(max = 1000)
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
