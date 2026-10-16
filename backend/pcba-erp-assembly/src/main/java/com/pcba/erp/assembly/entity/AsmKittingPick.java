package com.pcba.erp.assembly.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拣货记录 asm_kitting_pick
 * - 对应配料行的一次实际拣货动作
 * - 每扫一次物料条码/料盘生成一条
 */
@Data
@TableName("asm_kitting_pick")
public class AsmKittingPick implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long pickId;

    /** 关联配料单/行 */
    private Long kittingId;
    private Long kittingLineId;

    /** 拣货单号: PK-yyyyMMdd-4位序号 */
    @Size(max = 50)
    private String pickNo;

    /** 发料仓/库位 */
    private Long warehouseId;
    @Size(max = 50)
    private String locationCode;

    /** 物料 */
    private Long materialId;
    @Size(max = 50)
    private String materialCode;

    /** 计划/实际拣货数量 */
    private BigDecimal planPickQty;
    private BigDecimal actualPickQty;

    /** 批次/料盘号 */
    @Size(max = 50)
    private String batchNo;

    /**
     * 拣货状态
     * 0=待拣 1=拣货中 2=已拣 3=异常
     */
    private Integer pickStatus;

    /** 拣货人/时间 */
    private Long pickerId;
    @Size(max = 64)
    private String pickerName;
    private LocalDateTime pickTime;

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
