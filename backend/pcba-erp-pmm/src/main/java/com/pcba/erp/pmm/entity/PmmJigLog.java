package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工装治具动作流水 pmm_jig_log
 */
@Data
@TableName("pmm_jig_log")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PmmJigLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long logId;

    @NotNull(message = "工装ID不能为空")
    private Long jigId;

    @NotBlank(message = "工装编号不能为空")
    @Size(max = 50)
    private String jigNo;

    /**
     * INBOUND/BORROW/RETURN/CALIBRATE/RESHARPEN/REPAIR/SCRAP/CHECK/ADJUST
     */
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 30)
    private String eventType;

    @NotBlank(message = "事件标题不能为空")
    @Size(max = 200)
    private String eventTitle;

    @NotNull(message = "事件发生时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;

    /** PASS/FAIL/PENDING */
    @Size(max = 10)
    private String eventResult;

    /** 本次使用次数 (归还事件累加) */
    private Integer usedCountAdd;

    private Long borrowerId;
    @Size(max = 50)
    private String borrowerName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate realReturnDate;

    private BigDecimal costAmount;

    @Size(max = 500)
    private String attachmentUrl;

    @Size(max = 1000)
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ==========================================================
    //                前端 Dialog 扩展字段 (不入库)
    // ==========================================================
    /** 领用Dialog: 计划归还日 → 入库前拷贝到 returnDate */
    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planReturnDate;

    /** Dialog: 执行人姓名 (没有独立列, 展示兼容用) */
    @TableField(exist = false)
    private String operatorName;

    /** 校准Dialog: 测量项 */
    @TableField(exist = false)
    private String measureItem;
    /** 校准Dialog: 单位 */
    @TableField(exist = false)
    private String measureUnit;
    /** 校准Dialog: 测量值 */
    @TableField(exist = false)
    private BigDecimal measureValue;
    /** 校准Dialog: 下限 */
    @TableField(exist = false)
    private BigDecimal measureMin;
    /** 校准Dialog: 上限 */
    @TableField(exist = false)
    private BigDecimal measureMax;
}
