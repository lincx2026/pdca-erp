package com.pcba.erp.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 出库单明细
 */
@Data
@TableName("wms_stock_out_line")
public class WmsStockOutLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long lineId;

    /** 出库单ID */
    private Long stockOutId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 规格（冗余） */
    private String specification;

    /** 批次号 */
    private String batchNo;

    /** 出库数量 */
    private BigDecimal quantity;

    /** 单位 */
    private String unit;

    private String remark;

    /* ============== 跨模块链路字段（来源单据） ============== */

    /** 来源到货单ID（scm_delivery_note.delivery_id） */
    private Long deliveryId;
    /** 来源到货单行ID（scm_delivery_note_line.line_id） */
    private Long deliveryLineId;
    /** 来源退货单ID（scm_purchase_return.return_id） */
    private Long returnId;
    /** 来源退货单行ID（scm_purchase_return_line.line_id） */
    private Long returnLineId;
    /** 来源采购订单ID（scm_purchase_order.po_id） */
    private Long poId;
    /** 来源采购订单行ID（scm_purchase_order_line.line_id）；精准反写优先级最高 */
    private Long poLineId;
    /** 来源销售订单ID（sal_sales_order.so_id）；销售出库时回填，用于回写 SO 行 shippedQty */
    private Long soId;
    /** 来源销售订单行ID（sal_sales_order_line.line_id）；精准反写优先级最高 */
    private Long soLineId;
}