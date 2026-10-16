package com.pcba.erp.pdm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工艺步骤
 * - step_no 同路线内唯一 (10/20/30...便于插入)
 */
@Data
@TableName("pdm_process_step")
public class PdmProcessStep implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long stepId;

    private Long routeId;

    /** 10/20/30... 便于在两个工序间插入 */
    private Integer stepNo;

    /** SMT-PRINT / SMT-PLACE / SMT-REFLOW / AOI / DIP-WAVE / ASSEMBLE / ICT / FCT / AGING / PACKAGE ... */
    private String stepCode;

    private String stepName;

    /** 工作中心/车间, 例: SMT1线 / DIP2线 / 组装车间 */
    private String workCenter;

    /** 标准工时(分钟/PCS) */
    private BigDecimal standardCycleMin;

    /** 标准人工数 */
    private Integer standardLaborQty;

    /** 目标良率 (0~1) */
    private BigDecimal targetYieldRate;

    /** 1是 0否 */
    private Integer isRequiredInspection;

    /** 1是 0否 */
    private Integer isKeyProcess;

    private String sopFileUrl;

    private String remark;

    /* ============ 公共字段 ============ */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
