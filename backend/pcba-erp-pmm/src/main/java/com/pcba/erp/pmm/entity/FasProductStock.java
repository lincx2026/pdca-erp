package com.pcba.erp.pmm.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("fas_product_stock")
public class FasProductStock implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long stockId;
    private Long productId;
    @Size(max = 200)
    private String productName;
    @Size(max = 50)
    private String batchNo;
    @Size(max = 50)
    private String warehouse;
    private Integer qtyIn;
    private Integer qtyOut;
    private Integer qtyAvailable;
    private Long orderId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @Size(max = 500)
    private String remark;

    /** 单据类型 INBOUND=入库单 OUTBOUND=出库单 */
    @Size(max = 20)
    private String docType;

    /** 关联单据号(WFI/WFO前缀) */
    @Size(max = 50)
    private String docNo;

    /** 单据状态 0草稿 1待审核 2已审核 3已完成 4已驳回 */
    private Integer docStatus;

    /**
     * 台账业务类型（成品 vs 外购配件，软区分复用同一张表）
     *  FG  = 成品整机 —— 由后道组装工单 → 完工入库单审核进入
     *  ACC = 外购配件（线材/电池/适配器等）—— 由SCM配件采购到货 → 配件采购入库单审核进入
     */
    @Size(max = 5)
    private String stockType;

    /** 单据动作数量(台, 入=正 出=负) */
    private Integer flowQty;

    /** 业务类型 ASSEMBLY_IN/SALES_OUT/SALES_RETURN/SAMPLE/TRANSFER/PURCHASE_IN/KIT_OUT/OTHER */
    @Size(max = 30)
    private String sourceType;

    /** 来源采购到货单号(仅配件采购入库单 scm_delivery_note.delivery_no 有值) */
    @Size(max = 64)
    private String purchaseNo;

    @Size(max = 64)
    private String auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    @Size(max = 500)
    private String rejectReason;
}
