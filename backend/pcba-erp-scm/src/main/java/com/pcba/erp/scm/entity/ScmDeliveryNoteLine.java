package com.pcba.erp.scm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("scm_delivery_note_line")
public class ScmDeliveryNoteLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long lineId;
    @NotNull private Long deliveryId;
    @NotNull private Integer lineNo;
    private Long poLineId;
    @NotNull private Long materialId;
    @Size(max=50) private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=200) private String specification;
    @Size(max=20) private String unit;
    @NotNull private BigDecimal deliveryQty;
    private BigDecimal acceptedQty;
    private BigDecimal rejectedQty;
    private BigDecimal concessionQty;
    @Size(max=50) private String batchNo;
    private LocalDate producedDate;
    private LocalDate expireDate;
    @Size(max=500) private String iqcDefectDesc;
    @NotNull private Integer qcStatus = 0;
    @NotNull private BigDecimal qcInspectedQty = BigDecimal.ZERO;
    private BigDecimal qcAcceptedQty = BigDecimal.ZERO;
    private BigDecimal qcConcessionQty = BigDecimal.ZERO;
    private BigDecimal qcRejectedQty = BigDecimal.ZERO;
    private BigDecimal qcScrappedQty = BigDecimal.ZERO;
    /** ACCEPT/CONCESSION/REJECT/SCRAP/MIXED */
    @Size(max=20) private String qcDisposition;
    @Size(max=60) private String qcReasonCode;
    @Size(max=500) private String qcRemark;
    @Size(max=64) private String qcInspector;
    private LocalDateTime qcInspectTime;
    private BigDecimal stockedQty = BigDecimal.ZERO;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
