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
@TableName("sal_sales_return")
public class SalSalesReturn implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long returnId;
    @Size(max=50) private String returnNo;
    private Long soId;
    @Size(max=50) private String soNo;
    @NotNull private Long customerId;
    @Size(max=50) private String customerCode;
    @Size(max=200) private String customerName;
    private LocalDate returnDate;
    private Long warehouseId;
    @Size(max=200) private String reason;
    @Size(max=10) private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
    private Long stockOutId;
    /** 关联WMS入库单ID（销售退货入库生成后回填，非空表示已生成，防重复生成） */
    private Long stockInId;
    /** 0=草稿 1=提交 2=已审核 3=入库中 4=已完成 9=取消 */
    private Integer status;
    @Size(max=64) private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=500) private String rejectReason;
    /** 质检状态 NULL未启用质检/0待检/1合格/2部分不良/3全不良 */
    private Integer qcStatus;
    @Size(max=64) private String qcBy;
    private LocalDateTime qcTime;
    /** 整体处置 ACCEPT入库/RETURN_SUPPLIER退供方/CONCESSION折价/SCRAP报废 */
    @Size(max=20) private String disposition;
    @Size(max=500) private String qcRemark;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
