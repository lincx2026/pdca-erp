package com.pcba.erp.pdm.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * BOM 行项 Excel 导入 VO（单层 BOM，一行 = 一个子件）
 * 列顺序与 PdmBomLine 实体字段对齐，物料编码引用 PDM 物料主库
 */
@Data
public class PdmBomExcelVO {

    @ExcelProperty(value = "序号", index = 0)
    @ColumnWidth(8)
    private Integer lineNo;

    @ExcelProperty(value = "位号", index = 1)
    @ColumnWidth(20)
    /** R1,R2,C5 或 R5-R8 范围表达式 */
    private String referenceDesignator;

    @ExcelProperty(value = "物料编码*", index = 2)
    @ColumnWidth(22)
    /** 子件物料编码，必须能在 PDM 物料主库匹配到；未匹配到则预创建草稿 */
    private String componentMaterialCode;

    @ExcelProperty(value = "物料名称", index = 3)
    @ColumnWidth(20)
    /** 物料名称（预创建草稿时用；已存在则自动回填） */
    private String componentMaterialName;

    @ExcelProperty(value = "规格型号", index = 4)
    @ColumnWidth(28)
    private String componentSpec;

    @ExcelProperty(value = "用量*", index = 5)
    @ColumnWidth(10)
    /** 净用量 per 1 成品（用 String 接收，避免非数字直接抛异常） */
    private String quantity;

    @ExcelProperty(value = "单位", index = 6)
    @ColumnWidth(8)
    private String unit;

    @ExcelProperty(value = "损耗率%", index = 7)
    @ColumnWidth(10)
    /** 0~50 (%)，存库时转 decimal 0~0.5（用 String 接收） */
    private String lossRatePercent;

    @ExcelProperty(value = "封装", index = 8)
    @ColumnWidth(14)
    /** 如 0402/0603/QFN-48 */
    private String packageType;

    @ExcelProperty(value = "供应类型", index = 9)
    @ColumnWidth(12)
    /** 采购/自制/委外/虚拟/领用 */
    private String supplyTypeLabel;

    @ExcelProperty(value = "替代料编码", index = 10)
    @ColumnWidth(22)
    private String altMaterialCode;

    @ExcelProperty(value = "替代料名称", index = 11)
    @ColumnWidth(20)
    private String altMaterialName;

    @ExcelProperty(value = "备注", index = 12)
    @ColumnWidth(30)
    private String remark;

    /* ============ 非导出字段（解析时填充） ============ */
    /** 解析后用量（BigDecimal） */
    private transient BigDecimal quantityValue;
    /** 解析后损耗率（BigDecimal） */
    private transient BigDecimal lossRateValue;
    /** 解析后物料ID（已匹配到 PDM） */
    private transient Long resolvedMaterialId;
    /** 解析后替代料ID */
    private transient Long resolvedAltMaterialId;
    /** 是否需要预创建此物料 */
    private transient boolean needPrecreate;
    /** 是否需要预创建替代料 */
    private transient boolean altNeedPrecreate;
    /** 行级错误 */
    private transient String rowError;
    /** 行级警告 */
    private transient String rowWarning;
}
