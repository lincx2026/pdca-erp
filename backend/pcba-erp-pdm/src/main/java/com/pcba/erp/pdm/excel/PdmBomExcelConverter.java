package com.pcba.erp.pdm.excel;

import com.pcba.erp.pdm.entity.PdmBomLine;
import com.pcba.erp.pdm.entity.PdmMaterial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * BOM Excel 行解析与校验
 * - 物料编码查 PDM 主库，未命中标记预创建
 * - 用量/单位必填，损耗率 0-50%
 * - 位号表达式展开计数校验
 */
public class PdmBomExcelConverter {

    /** 供应类型 标签 → code */
    private static final Map<String, Integer> SUPPLY_MAP = new LinkedHashMap<>();
    static {
        SUPPLY_MAP.put("采购", 1); SUPPLY_MAP.put("自制", 2); SUPPLY_MAP.put("委外", 3);
        SUPPLY_MAP.put("虚拟", 4); SUPPLY_MAP.put("领用", 5);
    }
    private static final String[] SUPPLY_OPTIONS = {"采购","自制","委外","虚拟","领用"};

    public static String[] supplyTypeOptions() { return SUPPLY_OPTIONS; }
    public static Integer supplyCode(String label) {
        if (label == null) return 1;
        Integer c = SUPPLY_MAP.get(label.trim());
        return c == null ? 1 : c;
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    /**
     * 校验单行，填充 rowError / rowWarning / resolvedMaterialId / needPrecreate
     * @param vo Excel 行
     * @param materialByCode 已加载的 PDM 物料缓存 code→entity
     */
    public static void validateRow(PdmBomExcelVO vo, Map<String, PdmMaterial> materialByCode) {
        StringBuilder err = new StringBuilder();
        StringBuilder warn = new StringBuilder();

        // 1. 物料编码必填
        if (blank(vo.getComponentMaterialCode())) {
            err.append("物料编码必填;");
        }
        // 2. 用量必填且 > 0（String → BigDecimal，转换失败报行级错误）
        if (blank(vo.getQuantity())) {
            err.append("用量必填且>0;");
        } else {
            try {
                BigDecimal qty = new BigDecimal(vo.getQuantity().trim());
                vo.setQuantityValue(qty);
                if (qty.compareTo(BigDecimal.ZERO) <= 0) err.append("用量需>0;");
            } catch (NumberFormatException e) {
                err.append("用量须为数字;");
            }
        }
        // 3. 损耗率 0~50%
        if (!blank(vo.getLossRatePercent())) {
            try {
                BigDecimal loss = new BigDecimal(vo.getLossRatePercent().trim());
                vo.setLossRateValue(loss);
                if (loss.compareTo(BigDecimal.ZERO) < 0
                    || loss.compareTo(new BigDecimal("50")) > 0) {
                    err.append("损耗率需0~50%;");
                }
            } catch (NumberFormatException e) {
                err.append("损耗率须为数字;");
            }
        }
        // 4. 物料编码查 PDM
        if (!blank(vo.getComponentMaterialCode())) {
            String code = vo.getComponentMaterialCode().trim();
            PdmMaterial mat = materialByCode.get(code);
            if (mat != null) {
                vo.setResolvedMaterialId(mat.getMaterialId());
                // 自动回填名称/规格
                if (blank(vo.getComponentMaterialName())) vo.setComponentMaterialName(mat.getMaterialName());
                if (blank(vo.getComponentSpec())) vo.setComponentSpec(mat.getSpecification());
                if (blank(vo.getUnit())) vo.setUnit(mat.getUnit() == null ? "PCS" : mat.getUnit());
                // 检查状态：草稿 → 警告
                if (mat.getStatus() != null && mat.getStatus() == 0) {
                    warn.append("物料[" + code + "]为草稿态，建议先在PDM补全并启用;");
                }
            } else {
                vo.setNeedPrecreate(true);
                warn.append("物料[" + code + "]不在PDM主库，将自动创建草稿;");
            }
        }
        // 5. 替代料编码查 PDM
        if (!blank(vo.getAltMaterialCode())) {
            String altCode = vo.getAltMaterialCode().trim();
            PdmMaterial altMat = materialByCode.get(altCode);
            if (altMat != null) {
                vo.setResolvedAltMaterialId(altMat.getMaterialId());
            } else {
                vo.setAltNeedPrecreate(true);
                warn.append("替代料[" + altCode + "]不在PDM主库，将自动创建草稿;");
            }
        }
        // 6. 位号表达式展开校验（R5-R8 → R5,R6,R7,R8）
        if (!blank(vo.getReferenceDesignator())) {
            int count = expandDesignatorCount(vo.getReferenceDesignator());
            if (count < 0) {
                err.append("位号表达式格式错误;");
            }
        }

        vo.setRowError(err.length() == 0 ? null : err.toString());
        vo.setRowWarning(warn.length() == 0 ? null : warn.toString());
    }

    /**
     * VO → PdmBomLine（已校验通过且物料ID已解析的行才调）
     */
    public static PdmBomLine toLine(PdmBomExcelVO vo, Long bomId, Long parentMaterialId) {
        PdmBomLine line = new PdmBomLine();
        line.setBomId(bomId);
        line.setParentMaterialId(parentMaterialId);
        line.setComponentMaterialId(vo.getResolvedMaterialId());
        line.setComponentMaterialCode(vo.getComponentMaterialCode().trim());
        line.setComponentMaterialName(vo.getComponentMaterialName());
        line.setComponentSpec(vo.getComponentSpec());
        line.setQuantity(vo.getQuantityValue());
        line.setUnit(blank(vo.getUnit()) ? "PCS" : vo.getUnit().trim());

        // 损耗率: % → decimal
        BigDecimal lossRate = BigDecimal.ZERO;
        if (vo.getLossRateValue() != null) {
            lossRate = vo.getLossRateValue().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        }
        line.setLossRate(lossRate);
        // gross = qty / (1 - loss)
        BigDecimal denom = BigDecimal.ONE.subtract(lossRate);
        BigDecimal gross = (denom.compareTo(BigDecimal.ZERO) <= 0)
                ? line.getQuantity()
                : line.getQuantity().divide(denom, 6, RoundingMode.HALF_UP);
        line.setGrossQty(gross);

        line.setReferenceDesignator(vo.getReferenceDesignator());
        line.setSupplyType(supplyCode(vo.getSupplyTypeLabel()));
        line.setStatus(1);
        line.setRemark(vo.getRemark());

        // 替代料：设置 altGroupId（如果替代料存在则用主料编码+替代料编码组合）
        if (vo.getResolvedAltMaterialId() != null) {
            line.setAltGroupId(vo.getComponentMaterialCode().trim() + "+ALT");
        }

        return line;
    }

    /**
     * 从 VO 构造一个草稿 PdmMaterial（预创建用）
     */
    public static PdmMaterial toDraftMaterial(PdmBomExcelVO vo) {
        PdmMaterial m = new PdmMaterial();
        m.setMaterialCode(vo.getComponentMaterialCode().trim());
        m.setMaterialName(blank(vo.getComponentMaterialName()) ? vo.getComponentMaterialCode().trim() : vo.getComponentMaterialName().trim());
        m.setSpecification(blank(vo.getComponentSpec()) ? "(待补全)" : vo.getComponentSpec().trim());
        m.setUnit(blank(vo.getUnit()) ? "PCS" : vo.getUnit().trim());
        m.setMaterialType("RES"); // 默认电阻，后续 PDM 补全
        m.setBrand("(待补全)");
        m.setMpn(vo.getComponentMaterialCode().trim());
        m.setStatus(0); // 草稿
        m.setAbcClass("C");
        m.setSerialTracked(0);
        m.setRohsCompliant(1);
        m.setProcureScope("ELEC");
        return m;
    }

    /**
     * 位号表达式展开计数（R1,R2,R5-R8 → 5）
     * 支持: R1,R2,C5 / R5-R8 / R10,R11-R14
     * 返回 -1 表示格式错误
     */
    public static int expandDesignatorCount(String expr) {
        if (expr == null || expr.trim().isEmpty()) return 0;
        int count = 0;
        String[] parts = expr.trim().split("[,;\\s]+");
        for (String p : parts) {
            p = p.trim().toUpperCase();
            if (p.isEmpty()) continue;
            // 范围: R5-R8
            int dashIdx = p.indexOf('-');
            if (dashIdx > 0) {
                // 分离前缀字母和数字
                String prefix = p.replaceAll("[0-9].*$", "");
                String numPart = p.substring(prefix.length());
                String[] range = numPart.split("-");
                if (range.length == 2) {
                    try {
                        int from = Integer.parseInt(range[0].trim());
                        int to = Integer.parseInt(range[1].trim());
                        if (from > to) return -1;
                        count += (to - from + 1);
                    } catch (NumberFormatException e) { return -1; }
                } else { return -1; }
            } else {
                count++;
            }
        }
        return count;
    }
}
