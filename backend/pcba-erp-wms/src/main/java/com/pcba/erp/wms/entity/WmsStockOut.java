package com.pcba.erp.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 出库单
 * - order_no 唯一
 * - 状态机: 0草稿 → 1待审核 → (2已审核 → 3已出库) / 4已驳回
 */
@Data
@TableName("wms_stock_out")
public class WmsStockOut implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long stockOutId;

    /** 出库单号 */
    private String orderNo;

    /** 出库仓库ID */
    private Long warehouseId;

    /** 出库类型 PRODUCTION=生产领料 SALES=销售出库 TRANSFER=调拨出库 OTHER=其他 */
    private String outType;

    /** 客户ID（销售出库时） */
    private Long customerId;

    /** 客户名称（冗余） */
    private String customerName;

    /** 总数量 */
    private BigDecimal totalQty;

    /** 0=草稿 1=待审核 2=已审核 3=已出库 4=已驳回 */
    private Integer status;

    /** 审核人 */
    private String auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 驳回原因 */
    private String rejectReason;

    private String remark;

    /* ============ 公共字段 ============ */
    private Long deptId;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /* ============ 非DB字段 ============ */
    /** 仓库名称（联表回填） */
    @TableField(exist = false)
    private String warehouseName;
    /** 出库明细（保存/详情时传递） */
    @TableField(exist = false)
    private List<WmsStockOutLine> lines;
}