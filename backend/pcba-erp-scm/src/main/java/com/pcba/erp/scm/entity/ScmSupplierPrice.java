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
@TableName("scm_supplier_price")
public class ScmSupplierPrice implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long priceId;
    @NotNull private Long supplierId;
    @NotNull private Long materialId;
    @Size(max=10) private String currency;
    @NotNull private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal minQty;
    @NotNull private LocalDate validFrom;
    @NotNull private LocalDate validTo;
    @Size(max=50) private String quoteSource;
    private Integer isDefault;
    /** status: 0草稿 / 1提交 / 2生效(已审) / 3停用 / 5已转采购订单 */
    private Integer status;
    @Size(max=500) private String remark;
    /** 冗余字段（保存在库表，加速列表/转单时少JOIN） */
    @Size(max=50)  private String supplierCode;
    @Size(max=200) private String supplierName;
    @Size(max=50)  private String materialCode;
    @Size(max=200) private String materialName;
    @Size(max=200) private String specification;
    @Size(max=20)  private String unit;
    /** P0回填：报价转成哪张采购订单 & 哪一行 */
    private Long poId;
    private Long poLineId;

    @TableField(fill=FieldFill.INSERT) private String createBy;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill=FieldFill.INSERT_UPDATE) private String updateBy;
    @TableField(fill=FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
