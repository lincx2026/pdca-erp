package com.pcba.erp.pdm.excel;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物料 Excel 导入导出 VO
 * 列顺序 = 表头顺序 = 模板顺序（通过 index 强制）
 * 导入时：分类名称 → 查库反解 category_id；状态/ABC等 → 中文映射枚举值
 * 导出时：category_id → category_name；状态/ABC → 显示中文
 */
@Data
@HeadRowHeight(22)
@ContentRowHeight(18)
@ColumnWidth(16)
public class MaterialExcelVO implements Serializable {

    @ExcelIgnore
    private static final long serialVersionUID = 1L;

    /* 必须写 @ExcelIgnore 否则默认也作为列 */
    @ExcelIgnore
    private Long materialId;

    /* =============== 必填列 (index 0~5) =============== */
    @ExcelProperty(value = "物料编码*", index = 0)
    @ColumnWidth(22)
    private String materialCode;

    @ExcelProperty(value = "物料名称*", index = 1)
    @ColumnWidth(24)
    private String materialName;

    @ExcelProperty(value = "规格型号*", index = 2)
    @ColumnWidth(26)
    private String specification;

    /** 分类名称，导入用 name 反解 categoryId */
    @ExcelProperty(value = "物料分类*", index = 3)
    @ColumnWidth(18)
    private String categoryName;

    @ExcelProperty(value = "物料类型*", index = 4)
    @ColumnWidth(16)
    private String materialTypeLabel;

    @ExcelProperty(value = "计量单位*", index = 5)
    @ColumnWidth(12)
    private String unit;

    /* =============== 基础信息 (6~13) =============== */
    @ExcelProperty(value = "品牌/制造商*", index = 6)
    @ColumnWidth(20)
    private String brand;

    @ExcelProperty(value = "原厂MPN*", index = 7)
    @ColumnWidth(24)
    private String mpn;

    @ExcelProperty(value = "存储条件", index = 8)
    @ColumnWidth(14)
    private String storageConditionLabel;

    @ExcelProperty(value = "有效期(天)", index = 9)
    private Integer shelfLifeDays;

    @ExcelProperty(value = "RoHS合规", index = 10)
    private String rohsLabel;

    @ExcelProperty(value = "序列号管理", index = 11)
    private String snControlLabel;

    @ExcelProperty(value = "ABC分类", index = 12)
    private String abcClass;

    @ExcelProperty(value = "物料描述", index = 13)
    @ColumnWidth(28)
    private String description;

    /* =============== 计划与采购 (14~22) =============== */
    @ExcelProperty(value = "安全库存(Min)", index = 14)
    private BigDecimal safeStockMin;

    @ExcelProperty(value = "安全库存(Max)", index = 15)
    private BigDecimal safeStockMax;

    @ExcelProperty(value = "损耗率(%)", index = 16)
    private BigDecimal lossRate;

    @ExcelProperty(value = "采购提前期(天)", index = 17)
    private Integer leadTimeDays;

    @ExcelProperty(value = "最小采购量(MOQ)", index = 18)
    private BigDecimal moq;

    @ExcelProperty(value = "标准包装量(MPQ)", index = 19)
    private BigDecimal mpq;

    @ExcelProperty(value = "参考单价(元)", index = 20)
    private BigDecimal refPrice;

    @ExcelProperty(value = "默认供应商", index = 21)
    @ColumnWidth(18)
    private String defaultSupplierName;

    @ExcelProperty(value = "默认供应商料号", index = 22)
    @ColumnWidth(20)
    private String defaultSupplierPn;

    /* =============== 质量与状态 (23~29) =============== */
    @ExcelProperty(value = "质检等级", index = 23)
    @ColumnWidth(12)
    private String qcLevel;

    @ExcelProperty(value = "物料状态", index = 24)
    private String statusLabel;

    @ExcelProperty(value = "版本号", index = 25)
    private String materialVersion;

    @ExcelProperty(value = "图号/档号", index = 26)
    @ColumnWidth(18)
    private String drawingNo;

    @ExcelProperty(value = "环保等级", index = 27)
    private String environmental;

    @ExcelProperty(value = "备注", index = 28)
    @ColumnWidth(30)
    private String remark;

    /**
     * 采购归属：决定归属哪条采购线、入库路由哪个仓。
     * 导入支持填写 中文label或短码(空格/逗号拼接 labelShort)：
     *   ELEC  电子物料采购      PACK  包材/辅料采购
     *   ACC   外购配件采购      SELF  成品自制(不采购)
     */
    @ExcelProperty(value = "采购归属*", index = 29)
    @ColumnWidth(22)
    private String procureScopeLabel;

    /* ================= 内部辅助（ExcelIgnore）================= */
    /** 导入：反解出的分类ID，成功则有值 */
    @ExcelIgnore
    private Long categoryId;
    /** 导入：反解出的物料类型枚举值 0~15 */
    @ExcelIgnore
    private Integer materialType;
    /** 导入：反解出的 status 0~4 */
    @ExcelIgnore
    private Integer status;
    /** 导入：反解出的 procure_scope 短码 (ELEC/PACK/ACC/SELF) */
    @ExcelIgnore
    private String procureScope;
    /** 导入：行号（1-based，含表头），用于失败定位 */
    @ExcelIgnore
    private Integer rowNum;
    /** 导入：失败原因（逗号分隔）；成功行为空 */
    @ExcelIgnore
    private String errorMsg;
}
