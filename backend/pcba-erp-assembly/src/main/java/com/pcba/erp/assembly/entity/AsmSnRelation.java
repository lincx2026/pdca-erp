package com.pcba.erp.assembly.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SN 父子关系 asm_sn_relation
 * - 正向追溯: 成品 SN → 原材料批次 SN / 料盘 SN
 * - 反向追溯: 原材料 SN → 所有用了它的成品 SN
 * - BOM 展开后，每一条 父子关系 落一条
 */
@Data
@TableName("asm_sn_relation")
public class AsmSnRelation implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long relationId;

    /** 父 SN (成品 / 单板) */
    private Long parentSnId;
    @Size(max = 100)
    private String parentSnCode;

    /** 子 SN (料盘 / 原材料批次 / PCB 板) */
    private Long childSnId;
    @Size(max = 100)
    private String childSnCode;

    /** 关系类型: BOM / KIT / FEED / MOUNT */
    @Size(max = 20)
    private String relationType;

    /** 子件用量 (同一 BOM 行出现的数量) */
    private BigDecimal childQty;

    /** BOM 行 / 工单 */
    private Long bomLineId;
    private Long workOrderId;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
