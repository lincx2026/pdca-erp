package com.pcba.erp.pdm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物料-供应商 阶梯报价
 */
@Data
@TableName("pdm_material_supplier")
public class PdmMaterialSupplier implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long materialId;
    private Long supplierId;
    private String supplierName;
    private String supplierPn;
    private String mpn;
    private String brand;

    /** 优先序 1~9 (约定 ≤3) */
    private Integer priority;

    private BigDecimal moq;
    private BigDecimal mpq;
    private Integer leadTimeDays;

    private BigDecimal tier1Qty;
    private BigDecimal tier1Price;
    private BigDecimal tier2Qty;
    private BigDecimal tier2Price;
    private BigDecimal tier3Qty;
    private BigDecimal tier3Price;

    /** CNY / USD / EUR / HKD / JPY */
    private String currency;
    /** 税率 默认 0.13 */
    private BigDecimal taxRate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** 是否默认价格 (同物料只有一条=1) */
    private Integer isDefault;

    /** 0草稿 1生效 2停用/过期 */
    private Integer status;
    private String remark;

    /** ===== @DataScope 控制 ===== */
    @TableField(select = false)
    private Long deptId;

    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private String updateBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
