package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产工单 pmm_work_order
 * 状态 0草稿 / 10已下发 / 20生产中 / 30暂停 / 40完工 / 50关闭 / 90取消
 */
@Data
@TableName("pmm_work_order")
public class PmmWorkOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long workOrderId;

    @NotBlank(message = "工单号不能为空")
    @Size(max = 50)
    private String orderNo;

    /** MTO / MTS / REWORK / SAMPLE */
    @Size(max = 20)
    private String sourceType;

    @NotNull(message = "成品物料ID不能为空")
    private Long materialId;

    @Size(max = 50)
    private String materialCode;

    @Size(max = 200)
    private String materialName;

    @Size(max = 200)
    private String specification;

    private Long bomId;
    private Long routeId;

    @NotNull(message = "生产数量不能为空")
    private BigDecimal orderQty;

    private BigDecimal finishedQty;
    private BigDecimal scrappedQty;

    @Size(max = 20)
    private String unit;

    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private LocalDateTime actualStartDate;
    private LocalDateTime actualEndDate;

    private Integer priority;

    @Size(max = 100)
    private String workCenter;

    private Long deptId;
    private Long salesmanId;
    private Long customerId;

    @Size(max = 200)
    private String customerName;

    @Size(max = 50)
    private String saleOrderNo;

    /** 0=草稿 10=已下发 20=生产中 30=暂停 40=完工 50=关闭 90=取消 */
    private Integer status;

    @Size(max = 64)
    private String auditBy;
    private LocalDateTime auditTime;

    @Size(max = 500)
    private String rejectReason;

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
