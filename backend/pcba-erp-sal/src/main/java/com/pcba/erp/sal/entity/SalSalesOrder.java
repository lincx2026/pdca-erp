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
@TableName("sal_sales_order")
public class SalSalesOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long soId;
    @Size(max=50) private String soNo;
    @Size(max=20) private String soType;
    /** 来源: MANUAL(手工建单) / FROM_PRICE(报价转单) / KITTING(由齐套生成的补充单) */
    @Size(max=20) private String sourceType;
    /** 来源引用(报价条目ID逗号列表等), 便于追溯 */
    @Size(max=200) private String sourceRef;
    @NotNull private Long customerId;
    @Size(max=50) private String customerCode;
    @Size(max=200) private String customerName;
    @Size(max=64) private String contactName;
    @Size(max=32) private String contactPhone;
    private Long salesRepId;
    @Size(max=64) private String salesRepName;
    private Long deptId;
    private LocalDate soDate;
    private LocalDate deliveryDate;
    private Long warehouseId;
    @Size(max=10) private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal taxRate;
    private BigDecimal undiscAmount;
    private BigDecimal discountRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal receivedAmount;
    @Size(max=100) private String paymentTerm;
    @Size(max=100) private String deliveryTerm;
    @Size(max=50) private String transportType;
    @Size(max=500) private String incAttach;
    /** 关联WMS出库单ID（生成出库单后回填，非空表示已生成，防重复生成） */
    private Long stockOutId;
    /** 0=草稿 1=提交 2=已审核 3=已发货 4=部分出库 5=全部出库 6=已完成 7=已驳回 9=取消 */
    private Integer status;
    @Size(max=64) private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=500) private String rejectReason;
    @Size(max=500) private String closeReason;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
