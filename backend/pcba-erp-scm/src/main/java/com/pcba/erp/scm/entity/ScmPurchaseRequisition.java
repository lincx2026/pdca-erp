package com.pcba.erp.scm.entity;

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
@TableName("scm_purchase_requisition")
public class ScmPurchaseRequisition implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long requisitionId;
    @Size(max=50) private String requisitionNo;
    @NotBlank @Size(max=200) private String title;
    private Long applicantId;
    @Size(max=64) private String applicantName;
    private Long deptId;
    @Size(max=100) private String deptName;
    private LocalDate requisitionDate;
    private LocalDate requiredDate;
    private BigDecimal totalAmount;
    @Size(max=10) private String currency;
    private BigDecimal taxRate;
    @Size(max=500) private String reason;
    @Size(max=500) private String attachFile;
    private Long warehouseId;
    /** 0=草稿 1=提交 2=已审核 3=已转采购单 4=已驳回 9=取消 */
    private Integer status;
    @Size(max=64) private String auditBy;
    private LocalDateTime auditTime;
    @Size(max=500) private String rejectReason;
    @Size(max=1000) private String remark;
    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
