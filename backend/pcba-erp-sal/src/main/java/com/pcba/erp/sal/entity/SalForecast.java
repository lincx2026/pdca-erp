package com.pcba.erp.sal.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售预测/滚动计划 - 一张表搞定，靠版本号+周期分桶实现滚动
 * 状态机: 0草稿 → 1生效 → 2已归档(不删,保留历史)
 * 生成: 每周一凌晨1:05 @Scheduled → 自动建新版本覆盖未来12周
 * 算法: 移动平均(MA-8) SQL AVG, 历史不足4周跳过避免噪音
 */
@Data
@TableName("sal_forecast")
public class SalForecast implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long forecastId;
    @NotNull @Size(max=20)  private String versionNo;        // 滚动版本(2026-W35/2026-M08)
    @NotNull                 private String versionType;     // W=周/M=月
    private Long customerId;                                 // NULL=汇总行
    @Size(max=200) private String customerName;
    private Long materialId;
    @Size(max=50)  private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=20)  private String unit;
    @NotNull                 private LocalDate periodStart;
    @NotNull                 private LocalDate periodEnd;
    @NotNull private BigDecimal forecastQty;
    @NotNull private BigDecimal forecastAmt;
    @NotNull private BigDecimal actualQty;
    @NotNull private BigDecimal actualAmt;
    private BigDecimal hitRate;
    @Size(max=20)  private String method;                   // MA/MANUAL/SEASON
    private Integer status;                                 // 0草稿 1生效 2已归档
    @Size(max=64)  private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT)       private String createBy;
    @TableField(fill=FieldFill.INSERT)       private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
