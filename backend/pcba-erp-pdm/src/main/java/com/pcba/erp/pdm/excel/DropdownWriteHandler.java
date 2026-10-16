package com.pcba.erp.pdm.excel;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.util.Map;

/**
 * 模板下载用：在指定列区间(index 0-based) 写入 下拉框 约束
 * 避免 Excel 255 字符限制：下拉项较多的分类名放到 隐藏 Sheet2 的列，再用 INDIRECT 引用
 *
 * 使用方式：EasyExcel.write(..).registerWriteHandler(new DropdownWriteHandler(...)).sheet()
 */
public class DropdownWriteHandler implements SheetWriteHandler {

    /**
     * key: 列 index (0-based, 与 MaterialExcelVO 的 index 对齐)
     * value: 下拉选项数组
     */
    private final Map<Integer, String[]> dropdownMap;

    /** 从第几行开始应用下拉（默认 2=跳过表头，从第 2 行也就是数据第 1 行到 1000 行）*/
    private final int startRow;
    private final int endRow;

    public DropdownWriteHandler(Map<Integer, String[]> dropdownMap) {
        this(dropdownMap, 1, 999);
    }

    public DropdownWriteHandler(Map<Integer, String[]> dropdownMap, int startRow, int endRow) {
        this.dropdownMap = dropdownMap;
        this.startRow = startRow;
        this.endRow = endRow;
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder wbHolder, WriteSheetHolder sheetHolder) {
        Sheet sheet = sheetHolder.getSheet();
        DataValidationHelper helper = sheet.getDataValidationHelper();
        Workbook wb = wbHolder.getWorkbook();

        for (Map.Entry<Integer, String[]> e : dropdownMap.entrySet()) {
            int colIdx = e.getKey();
            String[] options = e.getValue();
            if (options == null || options.length == 0) continue;

            // 若下拉项字符总长度 <= 240，直接写公式；否则放隐藏 Sheet 引用
            int totalLen = 0;
            for (String s : options) totalLen += (s == null ? 0 : s.length()) + 1;
            DataValidationConstraint constraint;
            if (totalLen <= 240) {
                constraint = helper.createExplicitListConstraint(options);
            } else {
                // 创建隐藏 Sheet: 名称 DICT_{colIdx}，一列一行写 options
                String dictName = "DICT_C" + colIdx;
                Sheet dictSheet = wb.getSheet(dictName);
                if (dictSheet == null) {
                    dictSheet = wb.createSheet(dictName);
                    for (int i = 0; i < options.length; i++) {
                        Row row = dictSheet.getRow(i);
                        if (row == null) row = dictSheet.createRow(i);
                        Cell c = row.createCell(0);
                        c.setCellValue(options[i] == null ? "" : options[i]);
                    }
                    wb.setSheetHidden(wb.getSheetIndex(dictSheet), true);
                }
                String formula = String.format("'%s'!$A$1:$A$%d", dictName, options.length);
                constraint = helper.createFormulaListConstraint(formula);
            }

            CellRangeAddressList range = new CellRangeAddressList(startRow, endRow, colIdx, colIdx);
            DataValidation dv = helper.createValidation(constraint, range);
            dv.setEmptyCellAllowed(true);
            dv.setShowErrorBox(true);
            dv.setErrorStyle(DataValidation.ErrorStyle.STOP);
            dv.createErrorBox("输入不合法", "请从下拉列表中选择有效值");
            sheet.addValidationData(dv);
        }
    }
}
