package com.pcba.erp.sal.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sal_sales_return_line")
public class SalSalesReturnLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long lineId;
    @NotNull private Long returnId;
    @NotNull private Integer lineNo;
    private Long soLineId;
    @NotNull private Long materialId;
    @Size(max=50) private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=200) private String specification;
    @Size(max=20) private String unit;
    @NotNull private BigDecimal returnQty;
    private BigDecimal unitPrice;
    private BigDecimal returnAmount;
    @Size(max=200) private String reason;
    @Size(max=500) private String remark;
    /** 本次合格数(质检录入, 未填默认=returnQty) */
    private BigDecimal qcQty;
    /** 行处置 ACCEPT/RETURN_SUPPLIER/CONCESSION/SCRAP */
    @Size(max=20) private String qcDisposition;
    @Size(max=500) private String qcRemark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
