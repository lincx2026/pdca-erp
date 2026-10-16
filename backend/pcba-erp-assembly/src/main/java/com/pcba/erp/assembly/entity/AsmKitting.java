package com.pcba.erp.assembly.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配料单 asm_kitting
 * - 关联工单, BOM, 仓库, 按 BOM 行拆分解配料明细
 * - 状态机: 0待配料 → 1配料中 → 2已配料待发 → 3已发料 / 4部分发料 / 5已退料 / 6已取消
 */
@Data
@TableName("asm_kitting")
public class AsmKitting implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long kittingId;

    /** 配料单号: KT-yyyyMMdd-4位序号 */
    @Size(max = 50)
    private String kittingNo;

    /** 关联工单 */
    private Long workOrderId;
    @Size(max = 50)
    private String workOrderNo;

    /** 主物料 (产品) */
    private Long materialId;
    @Size(max = 50)
    private String materialCode;
    @Size(max = 200)
    private String materialName;

    /** 关联 BOM */
    private Long bomId;

    /** 默认发料仓 */
    private Long warehouseId;

    /** FULL=全配料 / PARTIAL=补配料 */
    @Size(max = 20)
    private String kittingType;

    /** BOM 明细行数 */
    private Integer totalLines;

    /** 总需求数量 */
    private BigDecimal totalRequiredQty;

    /** 累计已发数量 */
    private BigDecimal totalIssuedQty;

    /** 计划/实际时间 */
    private LocalDateTime planStartTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;

    /** 操作人 */
    private Long operatorId;
    @Size(max = 64)
    private String operatorName;

    /**
     * 状态机
     * 0=待配料 1=配料中 2=已配料待发 3=已发料 4=部分发料 5=已退料 6=已取消
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
