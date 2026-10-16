package com.pcba.erp.assembly.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Feeder 上料记录 asm_feeder_load
 * - 绑定 SMT 设备上某 Feeder/Slot 当前装的料盘 SN
 * - 换料/下料/报废 都会产生状态变更记录
 */
@Data
@TableName("asm_feeder_load")
public class AsmFeederLoad implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long loadId;

    /** 设备信息 */
    private Long equipmentId;
    @Size(max = 50)
    private String equipmentCode;

    /** Feeder 编号 + Slot 位 (一台设备多 Feeder, 一 Feeder 多 Slot) */
    @Size(max = 20)
    private String feederNo;
    private Integer slotNo;

    /** 物料 */
    private Long materialId;
    @Size(max = 50)
    private String materialCode;
    @Size(max = 200)
    private String materialName;

    /** 料盘 SN + 上料时料盘剩余数量 */
    @Size(max = 100)
    private String reelSn;
    private BigDecimal reelQty;

    /** 批次 / 程序 / 工单 */
    @Size(max = 50)
    private String batchNo;
    @Size(max = 100)
    private String programName;
    private Long workOrderId;
    @Size(max = 50)
    private String workOrderNo;

    /** 上料/下料时间 */
    private LocalDateTime loadTime;
    private LocalDateTime unloadTime;

    /** 操作人 */
    private Long operatorId;
    @Size(max = 64)
    private String operatorName;

    /**
     * 状态
     * 0=在料 1=已换料 2=已下料 3=报废
     */
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
