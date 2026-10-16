package com.pcba.erp.sal.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 开票单明细(关联应收单,部分金额)
 */
@Data
@TableName("sal_invoice_line")
public class SalInvoiceLine implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO) private Long lineId;
    @NotNull private Long invoiceId;
    @NotNull private Long receivableId;
    @Size(max=50) private String receivableNo;
    private BigDecimal amount;    // 本次开票金额(价税合计)
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
}
