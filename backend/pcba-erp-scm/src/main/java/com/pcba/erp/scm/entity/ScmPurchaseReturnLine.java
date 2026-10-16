package com.pcba.erp.scm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scm_purchase_return_line")
public class ScmPurchaseReturnLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long lineId;
    @NotNull private Long returnId;
    @NotNull private Integer lineNo;
    private Long deliveryLineId;
    /** 来源采购订单行ID (精准反写PO行returnedQty时使用) */
    private Long poLineId;
    @NotNull private Long materialId;
    @Size(max=50) private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=200) private String specification;
    @Size(max=20) private String unit;
    @NotNull private BigDecimal returnQty;
    private BigDecimal unitPrice;
    private BigDecimal returnAmount;
    @Size(max=50) private String batchNo;
    private Long stockInId;
    private Long stockInLineId;
    private Integer qcFlag = 0;
    @Size(max=60) private String defectCode;
    @Size(max=60) private String qcReasonCode;
    @Size(max=500) private String defectDesc;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
