package com.pcba.erp.pdm.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料主档 pdm_material
 * - 编码规则: {分类前缀}-{YY}-{4位流水}
 * - 状态机: 0草稿 → 1审核中 → (2已启用 / 4已驳回) → 3已失效
 */
@Data
@TableName("pdm_material")
public class PdmMaterial implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long materialId;

    /** 系统自动生成的物料编码, 唯一 */
    @NotBlank(message = "物料编码不能为空")
    @Size(max = 50, message = "物料编码不超过50字符")
    private String materialCode;

    @NotBlank(message = "物料名称不能为空")
    @Size(max = 200, message = "物料名称不超过200字符")
    private String materialName;

    /** 规格型号/规格描述 */
    @Size(max = 500, message = "规格型号不超过500字符")
    private String specification;

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    /**
     * 采购归属（贯穿 SCM 采购分工 + 入库自动路由）
     *  ELEC  电子物料采购      → PCBA产品开发下 → 入电子仓 (WMS)
     *  PACK  包材/辅料采购     → PCBA产品开发下的辅料/包材分类 → 入电子仓子仓
     *  ACC   外购配件采购      → 成品产品开发下(线材/电池/适配器) → 入配件仓 1853
     *  SELF  成品自制,不采购   → 成品产品开发下(整机机型) → 完工入成品仓 1852
     */
    @Size(max = 10)
    private String procureScope;

    /** 计量单位, 默认 PCS */
    @Size(max = 10, message = "单位不超过10字符")
    private String unit;

    /**
     * 物料类型
     * RAW/PCB/RESISTOR/CAPACITOR/IC/CONNECTOR/STRUCT/PAD/ASSIST/
     * SOLDER/GLUE/LABEL/PACKAGE/FIXTURE/WIP/FINISHED
     */
    @Size(max = 20, message = "物料类型不超过20字符")
    private String materialType;

    /** 品牌/制造商 */
    @Size(max = 100)
    private String brand;

    /** 原厂料号 MPN */
    @Size(max = 100)
    private String mpn;

    /** 1=RoHS合规 0=不合规 */
    private Integer rohsCompliant;

    /** 存储条件: 常温/防潮/冷藏/MSD1/MSD2/MSD3 */
    @Size(max = 100)
    private String storageCondition;

    /** 有效期天数, null=无限期 */
    private Integer validDays;

    /** ABC分类 A/B/C */
    private String abcClass;

    /** 1=手工覆盖 0=自动 */
    private Integer abcManualFlag;

    /** 最小/最大安全库存 */
    private BigDecimal safetyStockMin;
    private BigDecimal safetyStockMax;

    /** 采购提前期(天) */
    private Integer leadTimeDays;

    /** 最小订货量 */
    private BigDecimal minOrderQty;

    /** 最小包装量 */
    private BigDecimal minPackageQty;

    /** 标准损耗率 0~0.5 */
    private BigDecimal lossRate;

    /** 是否按序列号管理 1=是 0=否 */
    private Integer serialTracked;

    /** 默认供应商1/2/3 (已废弃: 默认供应商改由 scm_supplier_price.is_default 管理, 非数据库字段) */
    @TableField(exist = false)
    private Long defaultSupplier1;
    @TableField(exist = false)
    private Long defaultSupplier2;
    @TableField(exist = false)
    private Long defaultSupplier3;

    /**
     * 状态机
     * 0=草稿 1=审核中 2=已启用 3=已失效 4=已驳回
     */
    private Integer status;

    /** 审核人/时间 */
    private String auditBy;
    private LocalDateTime auditTime;

    /** 驳回原因 */
    @Size(max = 500)
    private String rejectReason;

    @Size(max = 1000)
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ============== 非数据库字段 ==============
    /** 分类名称 (联表回填) */
    @TableField(exist = false)
    private String categoryName;

    /** 分类编码前缀 (用于编码生成器展示) */
    @TableField(exist = false)
    private String categoryCode;
}
