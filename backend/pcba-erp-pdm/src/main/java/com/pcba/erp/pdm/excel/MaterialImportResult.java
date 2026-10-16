package com.pcba.erp.pdm.excel;

import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料导入结果 DTO
 */
@Getter
public class MaterialImportResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总处理行数（含标题行不计 → 只计数据行）*/
    private final int totalRows;
    /** 成功插入数量 */
    private int successCount;
    /** 失败数量 */
    private int failCount;
    /** 失败明细（每行一条，含 rowNum 和 errorMsg）*/
    private final List<FailRow> failRows;

    public MaterialImportResult(int totalRows) {
        this.totalRows = totalRows;
        this.failRows = new ArrayList<>();
    }

    public void incSuccess(int delta) { this.successCount += delta; }
    public void addFail(int rowNum, String errorMsg, MaterialExcelVO source) {
        this.failCount++;
        this.failRows.add(new FailRow(rowNum, errorMsg, source));
    }

    @Getter
    public static class FailRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int rowNum;         // Excel 行号 1-based，含表头
        private final String errorMsg;    // 错误原因，多条分号分隔
        /** 该行原值（前端可用来显示原内容方便用户修正）*/
        private final String materialCode;
        private final String materialName;
        private final String categoryName;
        private final String specification;
        private final String brand;
        private final String mpn;
        private final String procureScopeLabel;

        public FailRow(int rowNum, String errorMsg, MaterialExcelVO v) {
            this.rowNum = rowNum;
            this.errorMsg = errorMsg;
            this.materialCode = v == null ? null : v.getMaterialCode();
            this.materialName = v == null ? null : v.getMaterialName();
            this.categoryName = v == null ? null : v.getCategoryName();
            this.specification = v == null ? null : v.getSpecification();
            this.brand = v == null ? null : v.getBrand();
            this.mpn = v == null ? null : v.getMpn();
            this.procureScopeLabel = v == null ? null : v.getProcureScopeLabel();
        }
    }
}
