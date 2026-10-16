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

/** 生产看板卡片 pmm_kanban_card */
@Data
@TableName("pmm_kanban_card")
public class PmmKanbanCard implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long cardId;

    @NotNull(message = "工单ID不能为空")
    private Long workOrderId;

    private Long taskId;

    @NotBlank(message = "卡片标题不能为空")
    @Size(max = 200)
    private String title;

    /** 看板列 1=待开 2=生产中 3=待QC 4=已完成 5=异常 */
    private Integer lineNo;

    private Integer columnNo;

    @Size(max = 100)
    private String workCenter;

    private BigDecimal planQty;
    private BigDecimal passQty;
    private BigDecimal failQty;
    private BigDecimal progressPct;

    private LocalDateTime eta;

    @Size(max = 500)
    private String tags;

    private LocalDate dueDate;

    @Size(max = 20)
    private String backgroundColor;

    private Integer isFlagged;

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
