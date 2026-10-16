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
@TableName("scm_ap_payable_line")
public class ScmApPayableLine implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long lineId;
    @NotNull private Long payableId;
    private Integer lineNo;
    @Size(max=20) private String lineSrc;
    private Long stockInId; private Long stockInLineId;
    private Long stockOutId; private Long stockOutLineId;
    private Long deliveryId; private Long deliveryLineId;
    private Long returnId; private Long returnLineId;
    private Long poId; private Long poLineId;
    private Long materialId;
    @Size(max=50) private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=200) private String specification;
    @Size(max=20) private String unit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal undiscAmount;
    private BigDecimal taxAmount;
    private BigDecimal lineAmount;
    @Size(max=50) private String batchNo;
    private LocalDate producedDate;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
