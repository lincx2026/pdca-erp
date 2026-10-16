package com.pcba.erp.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单
 * - order_no 唯一；状态机: 0草稿 → 1待审核 → (2已审核 → 3已入库 / 4已驳回)
 * - in_type: PURCHASE 采购入库 / PRODUCTION 生产入库 / RETURN 退货入库 / OTHER 其他
 */
@Data
@TableName("wms_stock_in")
public class WmsStockIn implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long stockInId;

    @NotBlank(message = "入库单号不能为空")
    @Size(max = 50, message = "入库单号不超过50字符")
    private String orderNo;

    @NotNull(message = "入库仓库不能为空")
    private Long warehouseId;

    /** PURCHASE/PRODUCTION/RETURN/OTHER */
    @Size(max = 20)
    private String inType;

    private Long supplierId;
    @Size(max = 200)
    private String supplierName;

    private BigDecimal totalQty;
    private BigDecimal totalAmount;

    /** 0=草稿 1=待审核 2=已审核 3=已入库 4=已驳回 */
    private Integer status;

    /** 发票状态 0=未开票 1=部分开票 2=已开票 */
    private Integer invoiceStatus = 0;

    private String auditBy;
    private LocalDateTime auditTime;
    @Size(max = 500)
    private String rejectReason;

    @Size(max = 500)
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
    /** 入库明细（保存/详情时传递） */
    @TableField(exist = false)
    private List<WmsStockInLine> lines;
}
