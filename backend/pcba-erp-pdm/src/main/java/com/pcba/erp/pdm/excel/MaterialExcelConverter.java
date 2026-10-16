package com.pcba.erp.pdm.excel;

import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.entity.PdmMaterialCategory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 物料导入导出 字段翻译器
 * - 导出: entity → vo（数字编码 → 中文 label）
 * - 导入: vo → entity（中文 label → 数字编码 + 名称 → categoryId）
 */
public class MaterialExcelConverter {

    /* ============== 静态映射表：label → value ============== */
    private static final Map<String, Integer> MATERIAL_TYPE_MAP;         // canonical_label → code
    private static final Map<Integer, String> MATERIAL_TYPE_REV;         // code → canonical_label
    private static final Map<String, String> MATERIAL_TYPE_ALIAS;        // alias（含 canonical + 常见业务写法）→ canonical_label
    private static final Map<String, Integer> STATUS_MAP;
    private static final Map<Integer, String> STATUS_REV;
    private static final Map<String, Integer> STORAGE_MAP;
    private static final Map<Integer, String> STORAGE_REV;
    private static final Map<String, Integer> ROHS_MAP;
    private static final Map<String, Integer> SN_MAP;
    private static final String[] ABC_SET = {"A","B","C","N/A"};

    /**
     * 采购归属：ELEC/PACK/ACC/SELF。
     * 为导入友好，支持多种等价写法（短码/label/labelShort/常见中文别名），
     * 统一归一到 4 个短码。
     */
    private static final Map<String, String> PROCURE_LABEL_2_CODE; // label/alias → 短码
    private static final Map<String, String> PROCURE_CODE_2_LABEL; // 短码 → Excel 显示 label
    private static final String[] PROCURE_OPTIONS;                // 下拉用（中文label）

    static {
        // 16 种标准物料类型 + 常见业务别名归一
        // 格式: { code, canonical_label, alias1, alias2, ... }
        String[][] typeDefs = {
                {"0", "PCB空板",       "PCB板", "PCB", "线路板", "印刷电路板", "裸板", "PCBA板"},
                {"1", "电阻",          "贴片电阻", "碳膜电阻", "金属膜电阻", "功率电阻", "电位器", "热敏电阻", "压敏电阻", "可调电阻"},
                {"2", "电容",          "贴片电容", "电解电容", "陶瓷电容", "钽电容", "涤纶电容", "云母电容", "可变电容", "超级电容"},
                {"3", "电感/磁珠",     "电感", "磁珠", "功率电感", "高频电感", "共模电感", "差模电感", "扼流圈", "线圈"},
                {"4", "集成电路/芯片", "芯片", "IC", "集成电路", "蓝牙芯片", "BLE芯片", "MCU", "单片机", "SOC", "SoC", "门阵列",
                                          "FPGA", "CPLD", "DSP", "ARM", "传感器芯片", "霍尔传感器", "射频芯片", "电源芯片",
                                          "MOS管", "MOSFET", "N沟道MOS管", "P沟道MOS管", "IGBT", "IGBT管",
                                          "数码管", "LED数码管", "数码模块", "LED模块",
                                          "晶振", "晶振/频率元件", "频率元件", "振荡器", "谐振器", "MEMS芯片", "存储器",
                                          "Flash", "EEPROM", "DRAM", "SRAM"},
                {"5", "二极管/三极管", "二极管", "三极管", "LED", "LED灯", "贴片LED", "插件LED", "稳压管",
                                          "肖特基", "整流桥", "双向晶闸管", "变容二极管", "发光二极管",
                                          "NPN三极管", "PNP三极管",
                                          "显示器件", "光电器件", "光学器件", "显示元件", "LED显示屏", "RGB灯", "RGB彩灯", "RGB灯带"},
                {"6", "连接器",        "连接器", "端子", "插件端子", "贴片端子", "2pin", "3pin", "4pin", "5pin",
                                          "排针", "排母", "Pin针", "Pin座", "PIN针", "PIN座", "弹簧针", "pogo pin",
                                          "电池连接器", "电源连接器", "板对板连接器", "线对板连接器"},
                {"7", "结构件",        "结构件", "外壳", "上盖", "下盖", "底壳", "底座", "支架", "固定件",
                                          "螺丝", "自攻螺丝", "十字螺丝", "平头螺丝", "沉头螺丝", "盘头螺丝", "六角螺丝",
                                          "十字盘头螺丝", "十字盘头机牙螺丝", "十字圆头自攻螺丝", "紧固件",
                                          "螺母", "垫片", "垫圈", "弹片", "电池弹片", "电池座", "电池仓", "电池盖", "电池夹",
                                          "电池正负极片", "电池正负连片", "电池弹片单极片-正极", "电池弹片单极片-负极",
                                          "弹簧", "按键", "按键帽", "旋钮", "旋转盖", "透明盖", "内圈座", "防水圈",
                                          "密封圈", "O型圈", "减震垫", "脚垫", "平衡块", "配重块"},
                {"8", "焊锡膏/锡线",   "焊锡膏", "锡膏", "锡线", "焊锡丝", "助焊剂", "助焊膏", "焊锡条", "无铅锡膏"},
                {"9", "胶类",          "胶", "密封胶", "导热胶", "AB胶", "结构胶", "点胶", "双组份胶", "热熔胶", "UV胶"},
                {"10", "包装材料",     "包装材料", "包装盒", "保护膜", "静电膜", "气泡袋", "防静电袋", "胶带",
                                          "缠绕膜", "纸箱", "彩盒", "彩箱", "珍珠棉", "EPE", "吸塑盒", "托盘", "栈板", "卡板"},
                {"11", "标签/条码",    "标签", "条码", "二维码", "贴纸", "铭牌", "铭牌贴纸", "条码纸", "碳带"},
                {"12", "生产辅料",     "生产辅料", "清洁剂", "酒精", "擦拭布", "粘尘胶", "静电手环", "静电服",
                                          "防静电手腕带", "手套", "指套", "无尘布", "无尘纸"},
                {"13", "线缆/线材",    "线缆", "线材", "电线", "电缆", "端子线", "导线", "电源线", "USB线",
                                          "HDMI线", "音频线", "排线", "FFC排线", "FPC排线", "FFC线", "FPC线",
                                          "RGB线", "延长线", "转接线", "屏蔽线", "双绞线", "同轴电缆"},
                {"14", "模块/模组",    "模块", "模组", "显示模块", "显示屏", "OLED", "LCD", "TFT", "背光模块",
                                          "触摸屏", "触控屏", "RGB彩灯模块", "数码管模块", "传感器模块", "蓝牙模块",
                                          "WiFi模块", "电源模块", "降压模块", "升压模块", "升压稳压模块"},
                {"15", "其他",         "其他", "测试架", "测试治具", "治具", "工装", "配件", "零件", "辅料(其他)"},
        };
        MATERIAL_TYPE_MAP = new HashMap<>();
        MATERIAL_TYPE_REV = new HashMap<>();
        MATERIAL_TYPE_ALIAS = new HashMap<>();
        for (String[] def : typeDefs) {
            int v = Integer.parseInt(def[0]);
            String canonical = def[1];
            MATERIAL_TYPE_MAP.put(canonical, v);
            MATERIAL_TYPE_REV.put(v, canonical);
            // 所有 alias（从 canonical 开始）归一到 canonical
            for (int i = 1; i < def.length; i++) {
                MATERIAL_TYPE_ALIAS.put(def[i], canonical);
                // 额外再去空格版本 + 去空格并小写版本（宽松匹配）
                String trimmed = def[i].trim();
                if (!trimmed.equals(def[i])) MATERIAL_TYPE_ALIAS.put(trimmed, canonical);
                // 直接填数字 code 也支持
                if (i == 1) {
                    MATERIAL_TYPE_ALIAS.put(def[0], canonical);               // 数字"0" → "PCB空板"
                    MATERIAL_TYPE_ALIAS.put(String.valueOf(v), canonical);     // 同上（v 是 int）
                }
            }
        }

        STATUS_MAP = Map.of("草稿",0, "审核中",1, "已启用",2, "已停用",3, "已驳回",4);
        STATUS_REV = new HashMap<>();
        STATUS_MAP.forEach((k,v)-> STATUS_REV.put(v, k));

        STORAGE_MAP = Map.of("常温", 0, "阴凉", 1, "冷藏", 2, "冷冻", 3, "防潮", 4, "真空", 5);
        STORAGE_REV = new HashMap<>();
        STORAGE_MAP.forEach((k,v)-> STORAGE_REV.put(v, k));

        ROHS_MAP = Map.of("是", 1, "否", 0, "合规", 1, "不合规", 0, "未知", 2);
        SN_MAP = Map.of("是",1, "否",0, "管控",1, "不管控",0);

        // 采购归属：[code, canonical_label, alias1, alias2, ...]
        String[][] procure = {
                {"ELEC", "电子物料采购", "ELEC", "ELEC类", "电子", "电子物料", "电子采购", "电子类", "电子物料类", "电子类采购"},
                {"PACK", "包材/辅料采购", "PACK", "PACK类", "包辅", "包材", "辅料", "包材辅料", "包装辅料", "包材类", "辅料类", "包装类", "包装材料采购"},
                {"ACC",  "外购配件采购", "ACC",  "ACC类", "配件", "外购配件", "外购", "配件采购", "配件类", "外购类", "外购配件类"},
                {"SELF", "成品自制(不采购)", "SELF", "SELF类", "自制", "成品自制", "成品", "不采购", "自制(不采购)", "自制类", "成品类", "成品自制类"}
        };
        Map<String, String> l2c = new HashMap<>();
        Map<String, String> c2l = new HashMap<>();
        List<String> opts = new ArrayList<>(procure.length);
        for (String[] row : procure) {
            String code = row[0];
            String label = row[1];
            c2l.put(code, label);
            opts.add(label);
            for (int i = 1; i < row.length; i++) {
                l2c.put(row[i].toUpperCase(), code);
                l2c.put(row[i], code);               // 大小写不敏感 + 原文
            }
        }
        PROCURE_LABEL_2_CODE = l2c;
        PROCURE_CODE_2_LABEL = c2l;
        PROCURE_OPTIONS = opts.toArray(new String[0]);
    }

    /** 选项集合（给模板下拉使用）*/
    public static String[] materialTypeOptions()  { return MATERIAL_TYPE_MAP.keySet().toArray(new String[0]); }
    public static String[] statusOptions()        { return STATUS_MAP.keySet().toArray(new String[0]); }
    public static String[] storageOptions()       { return STORAGE_MAP.keySet().toArray(new String[0]); }
    public static String[] abcOptions()           { return ABC_SET; }
    public static String[] rohsOptions()          { return ROHS_MAP.keySet().toArray(new String[0]); }
    public static String[] snOptions()            { return SN_MAP.keySet().toArray(new String[0]); }
    public static String[] procureScopeOptions()  { return PROCURE_OPTIONS; }

    /* ======================================================================== */
    /* 导出：entity → VO                                                         */
    /* ======================================================================== */
    public static MaterialExcelVO toVO(PdmMaterial m, Map<Long, String> categoryId2Name) {
        MaterialExcelVO v = new MaterialExcelVO();
        v.setMaterialId(m.getMaterialId());
        v.setMaterialCode(m.getMaterialCode());
        v.setMaterialName(m.getMaterialName());
        v.setSpecification(m.getSpecification());
        v.setCategoryName(categoryId2Name.getOrDefault(m.getCategoryId(), ""));
        v.setMaterialTypeLabel(MATERIAL_TYPE_REV.getOrDefault(m.getMaterialType(), ""));
        v.setUnit(m.getUnit());
        v.setBrand(m.getBrand());
        v.setMpn(m.getMpn());
        v.setStorageConditionLabel(STORAGE_REV.getOrDefault(m.getStorageCondition(), ""));
        v.setShelfLifeDays(m.getValidDays());
        v.setRohsLabel(m.getRohsCompliant() == null ? "" : (m.getRohsCompliant() == 1 ? "是" : "否"));
        v.setSnControlLabel(m.getSerialTracked() == null ? "" : (m.getSerialTracked() == 1 ? "是" : "否"));
        v.setAbcClass(m.getAbcClass());
        v.setSafeStockMin(m.getSafetyStockMin());
        v.setSafeStockMax(m.getSafetyStockMax());
        v.setLossRate(m.getLossRate());
        v.setLeadTimeDays(m.getLeadTimeDays());
        v.setMoq(m.getMinOrderQty());
        v.setMpq(m.getMinPackageQty());
        v.setStatusLabel(STATUS_REV.getOrDefault(m.getStatus(), "草稿"));
        v.setRemark(m.getRemark());
        v.setProcureScopeLabel(PROCURE_CODE_2_LABEL.getOrDefault(m.getProcureScope(), ""));
        return v;
    }

    public static Map<Long, String> buildCategoryMap(List<PdmMaterialCategory> categories) {
        return categories.stream().collect(Collectors.toMap(
                PdmMaterialCategory::getCategoryId,
                c -> c.getCategoryName() == null ? "" : c.getCategoryName(),
                (a, b) -> a));
    }

    /* ======================================================================== */
    /* 导入：校验 + 翻译 VO → entity                                              */
    /* 先调用 validateRow，若无错误再 toEntity 入库                                */
    /* ======================================================================== */

    /**
     * 校验单行，返回 null 表示通过；否则返回错误信息(多条分号分隔)
     * 校验：
     *  1. 必填（编码/名称/分类/类型/单位）
     *  2. 编码格式 ^[A-Z0-9_-]{4,32}$
     *  3. 分类名称 → 在分类名 Map 中反解 id（唯一，不区分大小写）
     *  4. 类型/状态/存储/RoHS/SN → 选项合法
     *  5. ABC ∈ {A,B,C,N/A}
     *  6. 数值列非负 (shelfLifeDays/safeStock/leadTime/moq/mpq/refPrice)
     *  7. lossRate ∈ [0,100]
     *  8. 字符长度：名称≤64, 描述≤500
     */
    public static String validateRow(MaterialExcelVO v,
                                     Map<String, Long> name2CategoryId,
                                     Map<String, Integer> dupCodeInBatch,
                                     int dupLimit) {
        StringBuilder sb = new StringBuilder();
        // 1 必填
        if (blank(v.getMaterialCode())) sb.append("物料编码必填;");
        if (blank(v.getMaterialName())) sb.append("物料名称必填;");
        if (blank(v.getCategoryName())) sb.append("物料分类必填;");
        if (blank(v.getMaterialTypeLabel())) sb.append("物料类型必填;");
        if (blank(v.getUnit())) sb.append("计量单位必填;");
        // 1.2 规格型号/品牌/原厂MPN 三列新增强制必填（与前端表单规则一致）
        if (blank(v.getSpecification())) sb.append("规格型号必填;");
        else {
            String sp = v.getSpecification().trim();
            if (sp.length() < 2 || sp.length() > 500) sb.append("规格型号长度需2-500字符;");
        }
        if (blank(v.getBrand())) sb.append("品牌/制造商必填;");
        else {
            String br = v.getBrand().trim();
            if (br.length() < 2 || br.length() > 100) sb.append("品牌/制造商长度需2-100字符;");
        }
        if (blank(v.getMpn())) sb.append("原厂MPN必填;");
        else {
            String mpn = v.getMpn().trim();
            if (mpn.length() < 2 || mpn.length() > 100) sb.append("MPN长度需2-100字符;");
            // MPN 允许任意 Unicode 字符（含中文、日文、特殊符号等），不再限制仅英文数字
        }

        // 2 编码格式
        if (!blank(v.getMaterialCode())) {
            String code = v.getMaterialCode().trim();
            if (code.length() < 4 || code.length() > 32 || !code.matches("^[A-Za-z0-9_-]{4,32}$")) {
                sb.append("物料编码需4~32位且只含字母数字下划线短横;");
            } else {
                // 批内重复
                int cnt = dupCodeInBatch.merge(code.toUpperCase(), 1, Integer::sum);
                if (cnt > 1) sb.append("物料编码批内重复;");
                if (cnt > dupLimit + 1) { /* no-op, 占位 */ }
            }
        }

        // 3 分类反解
        if (!blank(v.getCategoryName())) {
            Long cid = name2CategoryId.get(v.getCategoryName().trim());
            if (cid == null) sb.append("物料分类[").append(v.getCategoryName().trim()).append("]不存在;");
            else v.setCategoryId(cid);
        }

        // 4 枚举 — 物料类型：先 alias 归一到 canonical_label 再校验
        if (!blank(v.getMaterialTypeLabel())) {
            String rawType = v.getMaterialTypeLabel().trim();
            Integer mt = null;
            // 先查 alias 归一表（覆盖绝大部分业务常见写法）
            String canonical = MATERIAL_TYPE_ALIAS.get(rawType);
            if (canonical != null) {
                mt = MATERIAL_TYPE_MAP.get(canonical);
            } else {
                // 兜底：宽松匹配 —— 去空格/括号/斜杠/连字符/点号，再查 alias（防 "晶振/频率元件"、"自攻螺丝(圆头)"、"RGB-001" 这种变体）
                String loose = rawType.replaceAll("[\\s/\\-·_.()（）\\[\\]【】,，、]", "");
                canonical = MATERIAL_TYPE_ALIAS.get(loose);
                if (canonical == null && loose.length() >= 2) {
                    // 取完整字符串 + 去末尾词试截断（如"贴片电阻(1206)"→"贴片电阻"）
                    for (int end = loose.length(); end >= 2 && canonical == null; end--) {
                        canonical = MATERIAL_TYPE_ALIAS.get(loose.substring(0, end));
                    }
                }
                if (canonical != null) {
                    mt = MATERIAL_TYPE_MAP.get(canonical);
                } else {
                    // 最后兜底：直接查 MATERIAL_TYPE_MAP（canonical_label 本身）
                    mt = MATERIAL_TYPE_MAP.get(rawType);
                }
            }
            if (mt == null) {
                // 找不到时把所有可用别名列表拼成一段提示，帮助用户快速定位
                StringBuilder hint = new StringBuilder("物料类型[").append(rawType).append("]不合法，");
                hint.append("可填: ");
                String[] opts = materialTypeOptions();
                for (int i = 0; i < opts.length; i++) {
                    if (i > 0) hint.append("/");
                    hint.append(opts[i]);
                }
                hint.append(" 或其常见别名(螺丝/电池座/端子/排线/MOS管等)");
                sb.append(hint).append(";");
            } else {
                v.setMaterialType(mt);
            }
        }
        if (!blank(v.getStatusLabel())) {
            Integer s = STATUS_MAP.get(v.getStatusLabel().trim());
            if (s == null) sb.append("物料状态不合法;"); else v.setStatus(s);
        }
        if (!blank(v.getStorageConditionLabel())) {
            Integer s = STORAGE_MAP.get(v.getStorageConditionLabel().trim());
            if (s == null) sb.append("存储条件不合法;");
        }
        if (!blank(v.getRohsLabel())) {
            Integer r = ROHS_MAP.get(v.getRohsLabel().trim());
            if (r == null) sb.append("RoHS合规请填 是/否;");
        }
        if (!blank(v.getSnControlLabel())) {
            Integer s = SN_MAP.get(v.getSnControlLabel().trim());
            if (s == null) sb.append("序列号管理请填 是/否;");
        }
        // 5 采购归属 (必填；若用户没填，尝试按分类名智能推断；推断失败再报错)
        boolean scopeOk = false;
        if (!blank(v.getProcureScopeLabel())) {
            String raw = v.getProcureScopeLabel().trim();
            String code = PROCURE_LABEL_2_CODE.get(raw);
            if (code == null) code = PROCURE_LABEL_2_CODE.get(raw.toUpperCase());
            if (code == null) {
                sb.append("采购归属不合法，请从下拉选择或填短码 ELEC/PACK/ACC/SELF;");
            } else {
                v.setProcureScope(code);
                scopeOk = true;
            }
        }
        if (!scopeOk && !blank(v.getCategoryName())) {
            String nm = v.getCategoryName().trim();
            String inferred = inferProcureScopeFromCategoryName(nm);
            if (inferred != null) { v.setProcureScope(inferred); scopeOk = true; }
        }
        if (!scopeOk) sb.append("采购归属必填(或可留空由系统按分类自动推断);");
        // 6 ABC
        if (!blank(v.getAbcClass())) {
            String a = v.getAbcClass().trim().toUpperCase();
            boolean ok = false;
            for (String x : ABC_SET) if (x.equals(a)) { ok = true; break; }
            if (!ok) sb.append("ABC分类请填 A/B/C/N/A;");
            else v.setAbcClass(a);
        }
        // 7 数值非负
        if (v.getShelfLifeDays() != null && v.getShelfLifeDays() < 0) sb.append("有效期需≥0;");
        if (v.getSafeStockMin() != null && v.getSafeStockMin().compareTo(BigDecimal.ZERO) < 0) sb.append("安全库存Min需≥0;");
        if (v.getSafeStockMax() != null && v.getSafeStockMax().compareTo(BigDecimal.ZERO) < 0) sb.append("安全库存Max需≥0;");
        if (v.getSafeStockMin() != null && v.getSafeStockMax() != null
                && v.getSafeStockMin().compareTo(v.getSafeStockMax()) > 0)
            sb.append("安全库存Min需≤Max;");
        if (v.getLeadTimeDays() != null && v.getLeadTimeDays() < 0) sb.append("采购提前期需≥0;");
        if (v.getMoq() != null && v.getMoq().compareTo(BigDecimal.ZERO) < 0) sb.append("MOQ需≥0;");
        if (v.getMpq() != null && v.getMpq().compareTo(BigDecimal.ZERO) < 0) sb.append("MPQ需≥0;");
        if (v.getRefPrice() != null && v.getRefPrice().compareTo(BigDecimal.ZERO) < 0) sb.append("参考单价需≥0;");
        // 7 lossRate
        if (v.getLossRate() != null && (v.getLossRate().compareTo(BigDecimal.ZERO) < 0
                || v.getLossRate().compareTo(new BigDecimal("100")) > 0))
            sb.append("损耗率需在0~100之间;");
        // 8 长度
        if (!blank(v.getMaterialName()) && v.getMaterialName().trim().length() > 64) sb.append("物料名称超长(64);");
        if (!blank(v.getDescription()) && v.getDescription().trim().length() > 500) sb.append("描述超长(500);");
        if (!blank(v.getRemark()) && v.getRemark().trim().length() > 500) sb.append("备注超长(500);");

        String msg = sb.toString();
        if (msg.isEmpty()) return null;
        return msg.endsWith(";") ? msg.substring(0, msg.length() - 1) : msg;
    }

    /**
     * VO → Entity (仅在 validateRow 成功后调用)
     * 默认 status=草稿，其余枚举按 label 反解
     */
    public static PdmMaterial toEntity(MaterialExcelVO v) {
        PdmMaterial m = new PdmMaterial();
        m.setMaterialCode(v.getMaterialCode() == null ? null : v.getMaterialCode().trim());
        m.setMaterialName(v.getMaterialName() == null ? null : v.getMaterialName().trim());
        m.setSpecification(v.getSpecification() == null ? null : v.getSpecification().trim());
        m.setCategoryId(v.getCategoryId());
        m.setMaterialType(v.getMaterialType() == null ? null : MATERIAL_TYPE_REV.getOrDefault(v.getMaterialType(), String.valueOf(v.getMaterialType())));
        m.setUnit(v.getUnit() == null ? "PCS" : v.getUnit().trim());
        m.setBrand(v.getBrand() == null ? null : v.getBrand().trim());
        m.setMpn(v.getMpn() == null ? null : v.getMpn().trim());
        m.setStorageCondition(blank(v.getStorageConditionLabel()) ? null : v.getStorageConditionLabel().trim());
        m.setValidDays(v.getShelfLifeDays());
        m.setRohsCompliant(blank(v.getRohsLabel()) ? 1
                : ROHS_MAP.getOrDefault(v.getRohsLabel().trim(), 1));
        m.setSerialTracked(blank(v.getSnControlLabel()) ? 0
                : SN_MAP.getOrDefault(v.getSnControlLabel().trim(), 0));
        m.setAbcClass(blank(v.getAbcClass()) ? "C" : v.getAbcClass().trim().toUpperCase());
        m.setSafetyStockMin(v.getSafeStockMin());
        m.setSafetyStockMax(v.getSafeStockMax());
        m.setLossRate(v.getLossRate());
        m.setLeadTimeDays(v.getLeadTimeDays());
        m.setMinOrderQty(v.getMoq());
        m.setMinPackageQty(v.getMpq());
        m.setStatus(v.getStatus() == null ? 0 : v.getStatus());
        m.setRemark(v.getRemark() == null ? null : v.getRemark().trim());
        // 采购归属：validate 阶段已把 v.procureScope 写入（用户选或按分类推断）
        m.setProcureScope(blank(v.getProcureScope()) ? "ELEC" : v.getProcureScope());
        return m;
    }

    private static final java.util.regex.Pattern MPN_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_.\\-+\\s/#()]+$");
    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    /**
     * 按分类名称智能推断采购归属（仅导入端作为兜底推断，与前端 inferProcureScopeByCategory 对齐）。
     * 规则：
     *  - 辅料/包材/包装/焊锡/胶/标签/治具 → PACK
     *  - 线材/电池/适配器/电源/充电器/外壳/五金/塑胶/铭牌/保护膜/配件前缀 → ACC
     *  - 整机/成品/套料/分类编码 FG 开头 → SELF
     *  - 其余默认 ELEC
     */
    public static String inferProcureScopeFromCategoryName(String catName) {
        if (catName == null) return null;
        String n = catName.trim();
        if (n.isEmpty()) return null;
        String u = n.toUpperCase();
        if (u.startsWith("FG")) return "SELF";
        if (n.contains("辅料")||n.contains("包材")||n.contains("彩盒")||n.contains("包装")||n.contains("说明书")||n.contains("外箱")||n.contains("泡沫")
                ||n.contains("焊锡")||n.contains("胶类")||n.contains("胶/")||n.contains("标签")||n.contains("条码")
                ||n.contains("治具")||n.contains("测试架")||n.contains("包装材料")||n.contains("结构件")
                ||n.contains("塑胶件")||n.contains("冲压件")||n.contains("铭牌")) return "PACK";
        if (n.contains("线材")||n.contains("电池")||n.contains("适配器")||n.contains("电源")||n.contains("充电器")
                ||n.contains("外壳")||n.contains("配件")||n.contains("挂绳")||n.contains("保护膜")
                ||n.contains("五金")||n.contains("塑胶")) return "ACC";
        if (n.contains("整机")||n.contains("机型")||n.contains("成品")||n.contains("套料")) return "SELF";
        // Default for PCB/PCBA/半导体/电子制造类/阻容/电感/IC/连接器/PCB空板/SMT辅助/半成品
        return "ELEC";
    }
}
