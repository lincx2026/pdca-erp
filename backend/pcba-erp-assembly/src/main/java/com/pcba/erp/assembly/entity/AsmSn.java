package com.pcba.erp.assembly.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SN 主表 asm_sn
 * - 覆盖 原材料批次 / WIP 在制 / 成品 / 料盘 REEL 四类
 * - 作为 MES 采集、上下料追溯、成品追溯的 中枢主档
 */
@Data
@TableName("asm_sn")
public class AsmSn implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long snId;

    /** SN 编码, 唯一 (SKU-yyyyMMdd-4位流水 或 料盘码) */
    @Size(max = 100)
    private String snCode;

    /** RAW=原材料批次 / WIP=在制板 / FG=成品 / REEL=料盘 */
    @Size(max = 20)
    private String snType;

    /** SN 层级: 1原材料批次 2料盘 3单板 4成品 */
    private Integer snLevel;

    /** 关联物料 */
    private Long materialId;
    @Size(max = 50)
    private String materialCode;
    @Size(max = 200)
    private String materialName;
    @Size(max = 200)
    private String specification;

    /** 批次 */
    @Size(max = 50)
    private String batchNo;

    /** 数量 + 单位 (料盘/批次用量场景) */
    private BigDecimal qty;
    @Size(max = 10)
    private String unit;

    /** 供应商 */
    private Long supplierId;
    @Size(max = 200)
    private String supplierName;

    /** 合格证号 / 来料证书 */
    @Size(max = 100)
    private String certificateNo;

    /** 生产日期 / 有效期 */
    private LocalDate productionDate;
    private LocalDate expiryDate;

    /** 关联工单 (生产类 SN) */
    private Long workOrderId;
    @Size(max = 50)
    private String workOrderNo;

    /** 当前工序编码 (processStepCode) */
    @Size(max = 50)
    private String currentProcess;

    /**
     * 当前状态
     * 0=待生产 1=生产中 2=在制 3=已完工 4=不良 5=报废 6=已出货
     */
    private Integer currentStatus;

    /** 首次/最近出现时间 (MES 扫码自动刷新) */
    private LocalDateTime firstSeenTime;
    private LocalDateTime lastSeenTime;

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
