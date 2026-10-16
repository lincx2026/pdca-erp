package com.pcba.erp.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 入库单明细
 * - 数量 quantity × 单价 unitPrice = 金额 amount（保存时自动计算）
 */
@Data
@TableName("wms_stock_in_line")
public class WmsStockInLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long lineId;

    @NotNull(message = "入库单ID不能为空")
    private Long stockInId;

    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    @Size(max = 50)
    private String materialCode;
    @Size(max = 200)
    private String materialName;
    @Size(max = 200)
    private String specification;
    @Size(max = 80)
    private String batchNo;

    @NotNull(message = "入库数量不能为空")
    private BigDecimal quantity;

    @Size(max = 20)
    private String unit;

    private BigDecimal unitPrice;
    private BigDecimal amount;

    @Size(max = 500)
    private String remark;

    /* ============== 跨模块链路（来源单据） ============== */

    /** 来源到货单ID（scm_delivery_note.delivery_id）；采购入库生成时回填 */
    private Long deliveryId;

    /** 来源到货单行ID（scm_delivery_note_line.line_id）；反写PO行receivedQty时匹配用 */
    private Long deliveryLineId;

    /** 来源采购订单ID（scm_purchase_order.po_id） */
    private Long poId;

    /** 来源采购订单行ID（scm_purchase_order_line.line_id）；优先级最高用于精准反写 */
    private Long poLineId;

    /* ============== 销售退货链路（来源单据） ============== */

    /** 来源销售退货单ID（sal_sales_return.return_id）；销售退货入库生成时回填 */
    private Long returnId;

    /** 来源销售退货单行ID（sal_sales_return_line.line_id）；精准反写SO行returnedQty优先级最高 */
    private Long returnLineId;

    /** 来源销售订单ID（sal_sales_order.so_id） */
    private Long soId;

    /** 来源销售订单行ID（sal_sales_order_line.line_id） */
    private Long soLineId;

    /* ============== 应付链路字段 ============== */

    /** 关联应付单ID（scm_ap_payable.payable_id） */
    private Long payableId;

    /** 关联应付单行ID（scm_ap_payable_line.line_id），预留字段 */
    private Long payableLineId;
}
