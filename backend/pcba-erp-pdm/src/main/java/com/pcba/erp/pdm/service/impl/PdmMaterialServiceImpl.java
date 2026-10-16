package com.pcba.erp.pdm.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.aspectj.DataScopeThreadLocal;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.entity.PdmMaterialCategory;
import com.pcba.erp.pdm.excel.DropdownWriteHandler;
import com.pcba.erp.pdm.excel.MaterialExcelConverter;
import com.pcba.erp.pdm.excel.MaterialExcelVO;
import com.pcba.erp.pdm.excel.MaterialImportResult;
import com.pcba.erp.pdm.mapper.PdmMaterialMapper;
import com.pcba.erp.pdm.service.IPdmMaterialCategoryService;
import com.pcba.erp.pdm.service.IPdmMaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 物料主档 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmMaterialServiceImpl implements IPdmMaterialService {

    private final PdmMaterialMapper materialMapper;
    private final IPdmMaterialCategoryService categoryService;
    /** 导入分批 size */
    private static final int BATCH_SIZE = 200;
    /** 单次导出/导入上限 */
    private static final int EXPORT_MAX_ROWS = 10000;
    private static final int IMPORT_MAX_ROWS = 20000;
    /** MPN 合法字符正则（与前端 rules 保持一致：字母数字 空格 和 _.+-/#()）*/
    private static final java.util.regex.Pattern MPN_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_.\\-+\\s/#()]+$");

    /**
     * 新增/修改物料前置校验（表单提交走 save/updateById；Excel 导入在 Converter 单独校验）。
     * 必填：编码/名称/分类/类型/单位 + 规格型号 / 品牌 / MPN
     */
    private void validateBeforePersist(PdmMaterial m, boolean isInsert) {
        List<String> errs = new ArrayList<>();
        // 基础必填（保留原来 Controller/DB 默认兜底）
        if (blank(m.getMaterialCode())) errs.add("物料编码必填");
        if (blank(m.getMaterialName())) errs.add("物料名称必填");
        if (m.getCategoryId() == null) errs.add("物料分类必填");
        if (blank(m.getMaterialType())) errs.add("物料类型必填");
        if (blank(m.getUnit())) errs.add("计量单位必填");
        // 本需求新增三列必填 + 长度/格式
        if (blank(m.getSpecification())) errs.add("规格型号必填");
        else if (m.getSpecification().trim().length() < 2 || m.getSpecification().trim().length() > 500)
            errs.add("规格型号长度需 2-500 字符");
        if (blank(m.getBrand())) errs.add("品牌/制造商必填");
        else if (m.getBrand().trim().length() < 2 || m.getBrand().trim().length() > 100)
            errs.add("品牌/制造商长度需 2-100 字符");
        if (blank(m.getMpn())) errs.add("原厂料号 MPN 必填");
        else {
            String mpn = m.getMpn().trim();
            if (mpn.length() < 2 || mpn.length() > 100) errs.add("MPN 长度需 2-100 字符");
            // MPN 允许任意 Unicode 字符（含中文、日文、特殊符号等），不再限制仅英文数字
        }
        // 分类采购归属兜底（若表单仍为空，按分类推断）
        if (blank(m.getProcureScope()) && m.getCategoryId() != null) {
            try {
                PdmMaterialCategory c = categoryService.getById(m.getCategoryId());
                if (c != null) {
                    String s = MaterialExcelConverter.inferProcureScopeFromCategoryName(c.getCategoryName());
                    if (s != null) m.setProcureScope(s);
                }
            } catch (Exception ignore) {}
        }
        if (blank(m.getProcureScope())) errs.add("采购归属必填");
        // 名称超长
        if (!blank(m.getMaterialName()) && m.getMaterialName().trim().length() > 200)
            errs.add("物料名称长度不能超过 200 字符");
        if (!errs.isEmpty()) {
            throw new ServiceException(String.join("; ", errs));
        }
        // 规范化：trim
        m.setMaterialCode(m.getMaterialCode() == null ? null : m.getMaterialCode().trim());
        m.setMaterialName(m.getMaterialName() == null ? null : m.getMaterialName().trim());
        m.setSpecification(m.getSpecification() == null ? null : m.getSpecification().trim());
        m.setBrand(m.getBrand() == null ? null : m.getBrand().trim());
        m.setMpn(m.getMpn() == null ? null : m.getMpn().trim());
    }
    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    @Override
    @DataScope(deptField = "m.category_id", userField = "m.create_by")
    public IPage<PdmMaterial> selectMaterialPage(int pageNum, int pageSize,
                                                 Long categoryId, String materialCode,
                                                 String materialName, String materialType,
                                                 String mpn, Integer status) {
        String dataScope = DataScopeThreadLocal.getAndClear();
        Page<PdmMaterial> page = new Page<>(pageNum, pageSize);
        return materialMapper.selectMaterialPage(page, categoryId, materialCode,
                materialName, materialType, mpn, status, dataScope);
    }

    @Override
    public PdmMaterial getById(Long materialId) {
        return materialMapper.selectById(materialId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(PdmMaterial material) {
        validateBeforePersist(material, true);
        // 物料编码全局唯一校验
        if (material.getMaterialCode() != null) {
            int dup = materialMapper.countDuplicateCode(material.getMaterialCode(), null);
            if (dup > 0) throw new ServiceException("物料编码【" + material.getMaterialCode() + "】已存在");
        }
        // 默认值
        if (material.getStatus() == null) material.setStatus(0); // 草稿
        if (material.getUnit() == null) material.setUnit("PCS");
        if (material.getAbcClass() == null) material.setAbcClass("C");
        if (material.getAbcManualFlag() == null) material.setAbcManualFlag(0);
        if (material.getSerialTracked() == null) material.setSerialTracked(0);
        if (material.getRohsCompliant() == null) material.setRohsCompliant(1);
        return materialMapper.insert(material) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(PdmMaterial material) {
        if (material.getMaterialId() == null) throw new ServiceException("物料ID不能为空");
        PdmMaterial old = materialMapper.selectById(material.getMaterialId());
        if (old == null) throw new ServiceException("物料不存在");
        // 已启用 2 / 已停用 3 的物料不允许直接修改 (需走变更, 这里简化版只拦 已启用)
        if (Integer.valueOf(2).equals(old.getStatus())) {
            throw new ServiceException("已启用状态的物料请先停用或创建新版本再修改");
        }
        validateBeforePersist(material, false);
        if (material.getMaterialCode() != null) {
            int dup = materialMapper.countDuplicateCode(material.getMaterialCode(), material.getMaterialId());
            if (dup > 0) throw new ServiceException("物料编码已存在");
        }
        return materialMapper.updateById(material) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Long materialId) {
        PdmMaterial m = materialMapper.selectById(materialId);
        if (m == null) throw new ServiceException("物料不存在");
        if (Integer.valueOf(2).equals(m.getStatus())) {
            throw new ServiceException("已启用状态的物料不能删除, 请先停用");
        }
        // TODO: W3+ 校验是否被 BOM / 采购单 / 工单引用, 引用则禁止删除
        return materialMapper.deleteById(materialId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submitAudit(Long materialId) {
        PdmMaterial m = requireStatus(materialId, 0, 4); // 草稿 / 已驳回 可提交
        m.setStatus(1);
        m.setRejectReason(null);
        return materialMapper.updateById(m) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditPass(Long materialId, String auditBy) {
        PdmMaterial m = requireStatus(materialId, 1); // 审核中
        m.setStatus(2);
        m.setAuditBy(auditBy);
        m.setAuditTime(LocalDateTime.now());
        return materialMapper.updateById(m) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditReject(Long materialId, String auditBy, String rejectReason) {
        PdmMaterial m = requireStatus(materialId, 1);
        m.setStatus(4);
        m.setAuditBy(auditBy);
        m.setAuditTime(LocalDateTime.now());
        m.setRejectReason(rejectReason == null ? "驳回原因未填写" : rejectReason);
        return materialMapper.updateById(m) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long materialId) {
        PdmMaterial m = requireStatus(materialId, 2); // 已启用
        m.setStatus(3);
        return materialMapper.updateById(m) > 0;
    }

    // =============== 私有 ===============
    private PdmMaterial requireStatus(Long materialId, Integer... allowed) {
        if (materialId == null) throw new ServiceException("物料ID不能为空");
        PdmMaterial m = materialMapper.selectById(materialId);
        if (m == null) throw new ServiceException("物料不存在");
        if (allowed != null) {
            boolean ok = false;
            for (Integer a : allowed) if (a.equals(m.getStatus())) { ok = true; break; }
            if (!ok) throw new ServiceException("当前物料状态不允许此操作");
        }
        return m;
    }

    /* ============================================================== */
    /*  W3.2 导入导出                                                  */
    /* ============================================================== */

    @Override
    public List<PdmMaterial> listForExport(Long categoryId, String materialCode,
                                           String materialName, String materialType,
                                           String mpn, Integer status) {
        Page<PdmMaterial> page = new Page<>(1, EXPORT_MAX_ROWS);
        IPage<PdmMaterial> ip = materialMapper.selectMaterialPage(page,
                categoryId, materialCode, materialName, materialType, mpn, status, null);
        return ip.getRecords();
    }

    @Override
    public void exportTemplate(OutputStream out) throws IOException {
        List<PdmMaterialCategory> categories = categoryService.selectCategoryList(null, 1);
        String[] categoryOptions = categories.stream()
                .map(PdmMaterialCategory::getCategoryName).filter(Objects::nonNull).toArray(String[]::new);

        Map<Integer, String[]> dropdown = new LinkedHashMap<>();
        dropdown.put(3, categoryOptions);                         // 分类
        dropdown.put(4, MaterialExcelConverter.materialTypeOptions()); // 物料类型
        dropdown.put(5, new String[]{"PCS","KG","M","L","BOX","SET","PACK","ROLL","REEL","EA"});
        dropdown.put(8, MaterialExcelConverter.storageOptions()); // 存储条件
        dropdown.put(10, MaterialExcelConverter.rohsOptions());   // RoHS
        dropdown.put(11, MaterialExcelConverter.snOptions());     // 序列号
        dropdown.put(12, MaterialExcelConverter.abcOptions());    // ABC
        dropdown.put(23, new String[]{"免检","抽检","全检","AQL"}); // 质检等级
        dropdown.put(24, MaterialExcelConverter.statusOptions()); // 物料状态
        dropdown.put(29, MaterialExcelConverter.procureScopeOptions()); // 采购归属

        // 示例行（表头占 1 行，紧接着示例引导行）
        MaterialExcelVO demo = new MaterialExcelVO();
        demo.setMaterialCode("RES-26-0001(示例,仅展示格式)");
        demo.setMaterialName("贴片电阻(示例)");
        demo.setSpecification("0402 1KΩ ±1% 1/16W");
        demo.setCategoryName(categories.isEmpty() ? "" : categories.get(0).getCategoryName());
        demo.setMaterialTypeLabel("电阻");
        demo.setUnit("PCS");
        demo.setBrand("YAGEO");
        demo.setMpn("RC0402FR-071KL");
        demo.setStorageConditionLabel("常温");
        demo.setShelfLifeDays(365);
        demo.setRohsLabel("是");
        demo.setSnControlLabel("否");
        demo.setAbcClass("B");
        demo.setSafeStockMin(new java.math.BigDecimal("1000"));
        demo.setSafeStockMax(new java.math.BigDecimal("50000"));
        demo.setLossRate(new java.math.BigDecimal("0.5"));
        demo.setLeadTimeDays(21);
        demo.setMoq(new java.math.BigDecimal("10000"));
        demo.setMpq(new java.math.BigDecimal("10000"));
        demo.setRefPrice(new java.math.BigDecimal("0.0012"));
        demo.setDefaultSupplierName("示例供应商");
        demo.setQcLevel("抽检");
        demo.setStatusLabel("草稿");
        demo.setMaterialVersion("V1.0");
        demo.setProcureScopeLabel("电子物料采购");

        EasyExcel.write(out, MaterialExcelVO.class)
                .registerWriteHandler(new DropdownWriteHandler(dropdown))
                .sheet("物料主档导入模板")
                .doWrite(Collections.singletonList(demo));
    }

    @Override
    public void exportToStream(OutputStream out, Long categoryId, String materialCode,
                               String materialName, String materialType,
                               String mpn, Integer status) throws IOException {
        List<PdmMaterial> list = listForExport(categoryId, materialCode,
                materialName, materialType, mpn, status);
        List<PdmMaterialCategory> categories = categoryService.selectCategoryList(null, null);
        Map<Long, String> cmap = MaterialExcelConverter.buildCategoryMap(categories);
        List<MaterialExcelVO> rows = list.stream()
                .map(m -> MaterialExcelConverter.toVO(m, cmap)).collect(Collectors.toList());
        EasyExcel.write(out, MaterialExcelVO.class)
                .sheet("物料主档")
                .doWrite(rows);
    }

    @Override
    public MaterialImportResult importMaterials(MultipartFile file, boolean updateSupport) throws IOException {
        // 加载分类映射 name → id（一次查，全量，分类数少≤1K）
        List<PdmMaterialCategory> categories = categoryService.selectCategoryList(null, 1);
        Map<String, Long> name2CategoryId = new HashMap<>();
        for (PdmMaterialCategory c : categories) {
            if (c.getCategoryName() != null) {
                name2CategoryId.put(c.getCategoryName().trim(), c.getCategoryId());
            }
        }
        // 编码 → 已存在实体 DB（updateSupport=true 时用），懒加载，按需查
        Map<String, PdmMaterial> dbByCode = new HashMap<>();

        MaterialImportListener listener = new MaterialImportListener(
                this, name2CategoryId, dbByCode, updateSupport, materialMapper);
        MaterialImportResult result;
        try {
            EasyExcel.read(file.getInputStream(), MaterialExcelVO.class, listener).sheet().doRead();
            // doRead 后再 flush 尾批
            listener.flushRemaining();
            result = listener.getResult();
        } catch (Exception e) {
            log.error("物料导入发生系统级异常: {}", e.getMessage(), e);
            result = listener.getResult();
            // 系统级异常，追加一条失败行(rowNum=-1 表示非数据行问题)
            MaterialExcelVO v = new MaterialExcelVO();
            v.setMaterialCode("【系统错误】");
            v.setMaterialName(e.getClass().getSimpleName());
            result.addFail(-1,
                    "导入过程发生异常: " + safeErrMsg(e) + "（已保留已成功行数，详见日志）",
                    v);
        }

        // 写一条失败汇总日志，便于后端追溯
        if (result.getFailCount() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("物料导入结果: total=").append(result.getTotalRows())
                    .append(", success=").append(result.getSuccessCount())
                    .append(", fail=").append(result.getFailCount())
                    .append("。失败明细:\n");
            for (MaterialImportResult.FailRow fr : result.getFailRows()) {
                sb.append("  [行").append(fr.getRowNum()).append("] ")
                        .append("编码=").append(fr.getMaterialCode() == null ? "" : fr.getMaterialCode())
                        .append(" | 名称=").append(fr.getMaterialName() == null ? "" : fr.getMaterialName())
                        .append(" | 原因=").append(fr.getErrorMsg())
                        .append("\n");
            }
            log.warn(sb.toString());
        } else {
            log.info("物料导入全部成功: total={}, success={}", result.getTotalRows(), result.getSuccessCount());
        }
        return result;
    }

    /** 安全异常消息：保留 cause 链但避免暴露过长堆栈细节 */
    private static String safeErrMsg(Exception e) {
        Throwable t = e;
        // 最多取 cause 3 层
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (t != null && depth < 3) {
            String msg = t.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (depth > 0) sb.append(" → ");
                // 取前 240 字符，避免超长
                sb.append(msg.length() > 240 ? msg.substring(0, 240) + "..." : msg);
            }
            t = t.getCause();
            depth++;
        }
        return sb.length() == 0 ? e.getClass().getSimpleName() : sb.toString();
    }

    /* ==================== private inner listener ===================== */
    private static class MaterialImportListener extends AnalysisEventListener<MaterialExcelVO> {

        private final PdmMaterialServiceImpl owner;
        private final Map<String, Long> name2CategoryId;
        private final Map<String, PdmMaterial> dbByCodeCache;
        private final boolean updateSupport;
        private final PdmMaterialMapper mapper;

        private final List<PdmMaterial> validBuffer = new ArrayList<>(BATCH_SIZE);
        private final Map<String, Integer> dupCodeInBatch = new HashMap<>();
        private int totalDataRows = 0;
        private MaterialImportResult result;

        MaterialImportListener(PdmMaterialServiceImpl owner,
                               Map<String, Long> name2CategoryId,
                               Map<String, PdmMaterial> dbByCodeCache,
                               boolean updateSupport,
                               PdmMaterialMapper mapper) {
            this.owner = owner;
            this.name2CategoryId = name2CategoryId;
            this.dbByCodeCache = dbByCodeCache;
            this.updateSupport = updateSupport;
            this.mapper = mapper;
        }

        MaterialImportResult getResult() {
            if (result == null) result = new MaterialImportResult(totalDataRows);
            return result;
        }

        @Override
        public void invoke(MaterialExcelVO v, AnalysisContext ctx) {
            int excelRowNum = 0;
            try {
                totalDataRows++;
                if (totalDataRows > IMPORT_MAX_ROWS) return; // 超限行跳过，由 doAfterAll 提示
                if (v == null) return;
                // 行号 = 数据行+2 （表头占 1 + 示例占 1，easyexcel invoke 从数据第 1 行开始）
                excelRowNum = ctx.readRowHolder().getRowIndex() + 1; // 1-based，已跳过表头
                v.setRowNum(excelRowNum);
                // 跳过示例引导行（匹配示例开头标记）
                if (v.getMaterialCode() != null && v.getMaterialCode().contains("(示例")) return;
                // 空行：编码/名称同时空 跳过
                if ((v.getMaterialCode() == null || v.getMaterialCode().trim().isEmpty())
                        && (v.getMaterialName() == null || v.getMaterialName().trim().isEmpty())) return;

                String err = MaterialExcelConverter.validateRow(v, name2CategoryId, dupCodeInBatch, 0);
                if (err != null) {
                    getResult().addFail(v.getRowNum(), err, v);
                    return;
                }
                // DB 唯一性：编码是否已存在
                String code = v.getMaterialCode().trim().toUpperCase();
                PdmMaterial existing = dbByCodeCache.computeIfAbsent(code, k -> {
                    // 查库
                    PdmMaterial m = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PdmMaterial>()
                            .eq(PdmMaterial::getMaterialCode, v.getMaterialCode().trim()).last("LIMIT 1"));
                    return m;
                });

                if (existing != null && !updateSupport) {
                    getResult().addFail(v.getRowNum(), "物料编码已存在且未开启更新支持", v);
                    return;
                }

                PdmMaterial entity = MaterialExcelConverter.toEntity(v);
                if (existing != null) {
                    // 更新模式：保留主键、创建者、状态机锁
                    entity.setMaterialId(existing.getMaterialId());
                    entity.setCreateBy(existing.getCreateBy());
                    entity.setCreateTime(existing.getCreateTime());
                    // 状态冲突保护：已启用 =2 不允许导入覆盖（避免越权绕过 updateById 校验）
                    if (Integer.valueOf(2).equals(existing.getStatus())) {
                        getResult().addFail(v.getRowNum(), "已启用物料禁止通过导入覆盖", v);
                        return;
                    }
                    entity.setUpdateBy(SecurityUtils.getUsernameSafe());
                    entity.setUpdateTime(LocalDateTime.now());
                }
                validBuffer.add(entity);

                if (validBuffer.size() >= BATCH_SIZE) {
                    flushBatch();
                }
            } catch (Exception e) {
                log.warn("物料导入单行行处理异常 rowNum={}, code={}: {}",
                        excelRowNum, v == null ? null : v.getMaterialCode(), e.getMessage());
                MaterialImportResult r = getResult();
                MaterialExcelVO safeV = (v == null) ? new MaterialExcelVO() : v;
                if (safeV.getRowNum() == 0) safeV.setRowNum(excelRowNum);
                r.addFail(safeV.getRowNum() == 0 ? excelRowNum : safeV.getRowNum(),
                        "行处理异常: " + safeErrMsgWrap(e), safeV);
            }
        }

        /** 包装异常消息（static inner class 用，独立方法避免与外层 private 方法同名调用） */
        private static String safeErrMsgWrap(Exception e) {
            Throwable t = e;
            StringBuilder sb = new StringBuilder();
            int depth = 0;
            while (t != null && depth < 3) {
                String msg = t.getMessage();
                if (msg != null && !msg.isBlank()) {
                    if (depth > 0) sb.append(" → ");
                    sb.append(msg.length() > 220 ? msg.substring(0, 220) + "..." : msg);
                }
                t = t.getCause();
                depth++;
            }
            return sb.length() == 0 ? e.getClass().getSimpleName() : sb.toString();
        }

        void flushRemaining() {
            if (!validBuffer.isEmpty()) {
                flushBatch();
            }
            // 超限提示
            if (result == null) result = new MaterialImportResult(totalDataRows);
            if (totalDataRows > IMPORT_MAX_ROWS) {
                // 无 material 数据追加一个失败信息（以 rowNum = MAX+1 表示系统级）
                result.addFail(IMPORT_MAX_ROWS + 1,
                        "数据行数超过单次上限" + IMPORT_MAX_ROWS + "，已丢弃多余行。请分批导入。",
                        new MaterialExcelVO());
            }
        }

        private void flushBatch() {
            if (result == null) result = new MaterialImportResult(totalDataRows);
            // 按 id 是否存在 split
            List<PdmMaterial> inserts = new ArrayList<>();
            List<PdmMaterial> updates = new ArrayList<>();
            for (PdmMaterial m : validBuffer) {
                if (m.getMaterialId() == null) inserts.add(m);
                else updates.add(m);
            }
            try {
                owner.runInserts(inserts, result);
                owner.runUpdates(updates, result);
            } finally {
                validBuffer.clear();
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) { /* no-op, 由 flushRemaining 触发 */ }
    }

    /** 批量插入：失败按行重试以保留成功行 + 记录失败原因 */
    @Transactional(rollbackFor = Exception.class)
    public void runInserts(List<PdmMaterial> inserts, MaterialImportResult r) {
        for (PdmMaterial m : inserts) {
            try {
                // 再次唯一兜底（并发/批内重复残留），save() 内部会抛 ServiceException
                if (save(m)) r.incSuccess(1);
            } catch (Exception e) {
                r.addFail(firstFailRow(m), e.getMessage(), buildFailVO(m));
            }
        }
    }

    /** 批量更新：失败按行重试以保留成功行 */
    @Transactional(rollbackFor = Exception.class)
    public void runUpdates(List<PdmMaterial> updates, MaterialImportResult r) {
        for (PdmMaterial m : updates) {
            try {
                if (updateById(m)) r.incSuccess(1);
                else r.addFail(firstFailRow(m), "更新失败(未找到记录)", buildFailVO(m));
            } catch (Exception e) {
                r.addFail(firstFailRow(m), e.getMessage(), buildFailVO(m));
            }
        }
    }

    private int firstFailRow(PdmMaterial m) {
        // 无法精准还原，以 0 替代（Listener 内按行 addFail 更准，这里只在 batch 拆分后兜底）
        return 0;
    }

    private MaterialExcelVO buildFailVO(PdmMaterial m) {
        MaterialExcelVO v = new MaterialExcelVO();
        v.setMaterialCode(m.getMaterialCode());
        v.setMaterialName(m.getMaterialName());
        v.setSpecification(m.getSpecification());
        return v;
    }
}
