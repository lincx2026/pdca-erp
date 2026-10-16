package com.pcba.erp.sal.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sal_sales_order_line")
public class SalSalesOrderLine implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long lineId;
    @NotNull private Long soId;
    @NotNull private Integer lineNo;
    @NotNull private Long materialId;
    @Size(max=50) private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=200) private String specification;
    @Size(max=20) private String unit;
    @NotNull private BigDecimal orderQty;
    private BigDecimal shippedQty;
    private BigDecimal returnedQty;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal lineAmount;
    private BigDecimal discountRate;
    private LocalDate requiredDate;
    private Long warehouseId;
    @Size(max=500) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
