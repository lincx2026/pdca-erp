package com.pcba.erp.assembly.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配料明细行 asm_kitting_line
 * - 每行对应 BOM 的一个子料需求
 * - 贯穿 需求 → 预留 → 拣货 → 发料 → 退料 → 报废 全流程
 */
@Data
@TableName("asm_kitting_line")
public class AsmKittingLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long kittingLineId;

    /** 主单 ID */
    private Long kittingId;

    /** 行号 */
    private Integer lineNo;

    /** 物料信息 */
    private Long materialId;
    @Size(max = 50)
    private String materialCode;
    @Size(max = 200)
    private String materialName;
    @Size(max = 200)
    private String specification;
    @Size(max = 10)
    private String unit;

    /** BOM 行 */
    private Long bomLineId;

    /** 需求/预留/拣货/发料/退料/报废 数量 */
    private BigDecimal reqQty;
    private BigDecimal reservedQty;
    private BigDecimal pickedQty;
    private BigDecimal issuedQty;
    private BigDecimal returnedQty;
    private BigDecimal scrapQty;

    /** 实际发料仓/库位 */
    private Long warehouseId;
    @Size(max = 50)
    private String locationCode;

    /** 批次号 */
    @Size(max = 50)
    private String batchNo;

    /** 库存锁定状态 0=未锁 1=已锁 */
    private Integer lockStatus;

    /**
     * 行状态
     * 0=待拣 1=拣货中 2=已拣 3=已发 4=已退 5=缺料
     */
    private Integer lineStatus;

    @Size(max = 300)
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
