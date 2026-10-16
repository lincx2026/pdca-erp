package com.pcba.erp.pdm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.aspectj.DataScopeThreadLocal;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.excel.PdmBomExcelConverter;
import com.pcba.erp.pdm.excel.PdmBomExcelVO;
import com.pcba.erp.pdm.excel.DropdownWriteHandler;
import com.pcba.erp.pdm.entity.PdmBom;
import com.pcba.erp.pdm.entity.PdmBomLine;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.entity.PdmMaterialCategory;
import com.pcba.erp.pdm.mapper.PdmBomLineMapper;
import com.pcba.erp.pdm.mapper.PdmBomMapper;
import com.pcba.erp.pdm.mapper.PdmMaterialCategoryMapper;
import com.pcba.erp.pdm.mapper.PdmMaterialMapper;
import com.pcba.erp.pdm.service.IPdmBomService;
import com.alibaba.excel.EasyExcel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdmBomServiceImpl implements IPdmBomService {

    private final PdmBomMapper bomMapper;
    private final PdmBomLineMapper lineMapper;
    private final PdmMaterialMapper materialMapper;
    private final PdmMaterialCategoryMapper categoryMapper;
    private final StringRedisTemplate redisTemplate;

    /** Lua 脚本：原子 INCR + 首次设置过期 */
    private static final String LUA_CODE_INCR =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return v";

    /* ========== 分页查询 ========== */
    @Override
    @DataScope(deptField = "b.dept_id", userField = "b.create_by")
    public IPage<PdmBom> selectBomPage(int pageNum, int pageSize,
                                       String bomCode, String bomName, String productMaterialCode,
                                       Long productMaterialId, Long categoryId,
                                       Integer bomType, Integer status, String version) {
        String ds = DataScopeThreadLocal.getAndClear();
        Page<PdmBom> page = new Page<>(pageNum, pageSize);
        // MyBatis-Plus 自定义select 走 selectPage 会先COUNT再分页；这里手动COUNT兼容
        long total = bomMapper.selectBomPage(bomCode, bomName, productMaterialCode,
                productMaterialId, categoryId, bomType, status, version, ds).size();
        List<PdmBom> list = bomMapper.selectBomPage(bomCode, bomName, productMaterialCode,
                productMaterialId, categoryId, bomType, status, version, ds);
        // 手动分页: 按偏移量截取
        int from = Math.min((pageNum - 1) * pageSize, list.size());
        int to = Math.min(from + pageSize, list.size());
        page.setRecords(list.subList(from, to));
        page.setTotal(total);
        return page;
    }

    @Override
    public PdmBom getById(Long bomId) {
        return bomMapper.selectById(bomId);
    }

    @Override
    public PdmBom getDetail(Long bomId) {
        PdmBom b = bomMapper.selectById(bomId);
        if (b == null) return null;
        b.setLines(lineMapper.listByBomId(bomId));
        return b;
    }

    /* ========== 编码生成：BOM + yyyyMMdd + 4位序号（Redis Lua 原子 INCR，按天滚动，2天过期） ========== */
    @Override
    public String nextBomCode() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String seqKey = "pdm:bom:code:seq:" + today;
        long expireSec = 2L * 24 * 3600;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_CODE_INCR, Long.class);
        Long seq = redisTemplate.execute(script, Collections.singletonList(seqKey), String.valueOf(expireSec));
        if (seq == null) throw new ServiceException("BOM编码生成失败");
        return "BOM" + today + String.format("%04d", seq);
    }

    /* ========== 保存 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveWithLines(PdmBom bom) {
        // 1. 编码: 为空则系统生成
        if (bom.getBomCode() == null || bom.getBomCode().isBlank()) {
            bom.setBomCode(nextBomCode());
        }
        // 2. 成品物料必须存在
        PdmMaterial prod = materialMapper.selectById(bom.getProductMaterialId());
        if (prod == null) throw new ServiceException("成品物料不存在");
        if (bom.getProductMaterialCode() == null || bom.getProductMaterialCode().isBlank())
            bom.setProductMaterialCode(prod.getMaterialCode());
        if (bom.getProductMaterialName() == null || bom.getProductMaterialName().isBlank())
            bom.setProductMaterialName(prod.getMaterialName());
        if (bom.getUnit() == null || bom.getUnit().isBlank()) bom.setUnit(prod.getUnit() == null ? "PCS" : prod.getUnit());

        // 3. 版本号唯一性
        int dup = bomMapper.countDuplicateVersion(bom.getProductMaterialId(), bom.getVersion(), null);
        if (dup > 0) throw new ServiceException("该成品已存在版本号【" + bom.getVersion() + "】");

        // 4. 默认版本: 若 isDefault=1 则把同成品其他版本置为0
        if (Integer.valueOf(1).equals(bom.getIsDefault())) {
            clearDefaultForOtherVersions(bom.getProductMaterialId(), null);
        }
        if (bom.getQuantity() == null) bom.setQuantity(BigDecimal.ONE);
        if (bom.getIsDefault() == null) bom.setIsDefault(0);
        if (bom.getBomType() == null) bom.setBomType(1);
        if (bom.getStatus() == null) bom.setStatus(0);

        bomMapper.insert(bom);
        Long bomId = bom.getBomId();

        // 5. 行项
        if (bom.getLines() != null && !bom.getLines().isEmpty()) {
            saveLines(bomId, bom.getProductMaterialId(), bom.getLines());
        }
        return bomId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PdmBom bom) {
        if (bom.getBomId() == null) throw new ServiceException("BOM ID不能为空");
        PdmBom old = bomMapper.selectById(bom.getBomId());
        if (old == null) throw new ServiceException("BOM不存在");
        // 已启用(2)/已归档(5) 不允许直接修改主档（行项同理）
        if (Integer.valueOf(2).equals(old.getStatus()) || Integer.valueOf(5).equals(old.getStatus())) {
            throw new ServiceException("已启用/已归档BOM请先停用或通过ECO变更");
        }
        // 版本号唯一性
        if (bom.getVersion() != null && !bom.getVersion().isBlank()) {
            int dup = bomMapper.countDuplicateVersion(old.getProductMaterialId(), bom.getVersion(), bom.getBomId());
            if (dup > 0) throw new ServiceException("版本号【" + bom.getVersion() + "】已存在");
        }
        // 默认版本切换
        if (Integer.valueOf(1).equals(bom.getIsDefault())) {
            clearDefaultForOtherVersions(old.getProductMaterialId(), bom.getBomId());
        }
        // product_material_id 不允许修改
        bom.setProductMaterialId(old.getProductMaterialId());
        return bomMapper.updateById(bom) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceLines(Long bomId, List<PdmBomLine> lines) {
        PdmBom old = bomMapper.selectById(bomId);
        if (old == null) throw new ServiceException("BOM不存在");
        if (Integer.valueOf(2).equals(old.getStatus()) || Integer.valueOf(5).equals(old.getStatus())) {
            throw new ServiceException("已启用/已归档BOM请先停用再修改行项");
        }
        // 清空旧行
        lineMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId));
        if (lines == null || lines.isEmpty()) return true;
        saveLines(bomId, old.getProductMaterialId(), lines);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long bomId) {
        PdmBom old = bomMapper.selectById(bomId);
        if (old == null) return true;
        // 仅草稿/驳回可删
        if (!(Integer.valueOf(0).equals(old.getStatus()) || Integer.valueOf(4).equals(old.getStatus()))) {
            throw new ServiceException("仅草稿/驳回状态的BOM可删除");
        }
        // 删行
        lineMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PdmBomLine>()
                .eq(PdmBomLine::getBomId, bomId));
        return bomMapper.deleteById(bomId) > 0;
    }

    /* ========== 状态机 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submitAudit(Long bomId) {
        PdmBom old = requireStatus(bomId, 0, 4);
        old.setStatus(1);
        return bomMapper.updateById(old) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditPass(Long bomId, String auditBy) {
        PdmBom old = requireStatus(bomId, 1);
        old.setStatus(2);
        old.setAuditBy(auditBy);
        old.setAuditTime(LocalDateTime.now());
        if (old.getEffectiveDate() == null) old.setEffectiveDate(LocalDate.now());
        return bomMapper.updateById(old) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditReject(Long bomId, String auditBy, String rejectReason) {
        PdmBom old = requireStatus(bomId, 1);
        old.setStatus(4);
        old.setAuditBy(auditBy);
        old.setAuditTime(LocalDateTime.now());
        old.setRejectReason(rejectReason);
        return bomMapper.updateById(old) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long bomId) {
        PdmBom old = requireStatus(bomId, 2);
        old.setStatus(3);
        return bomMapper.updateById(old) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean archive(Long bomId) {
        PdmBom old = requireStatus(bomId, 3);
        old.setStatus(5);
        old.setExpiryDate(LocalDate.now());
        return bomMapper.updateById(old) > 0;
    }

    /* ========== BOM 展开 ========== */
    @Override
    public List<PdmBomLine> listSingleLevel(Long bomId) {
        return lineMapper.listByBomId(bomId);
    }

    @Override
    public List<PdmBomLine> explodeTree(Long bomId, int levelMax, boolean phantomExpand) {
        PdmBom bom = bomMapper.selectById(bomId);
        if (bom == null) return Collections.emptyList();
        if (levelMax <= 0) levelMax = 10;
        List<PdmBomLine> result = new ArrayList<>();
        // 成品 → 直接子件 (递归)
        explodeRecursive(bom.getProductMaterialId(), bomId, null, BigDecimal.ONE, 0,
                levelMax, phantomExpand, result, new HashSet<>());
        return result;
    }

    private void explodeRecursive(Long parentId, Long rootBomId, PdmBomLine directLineTemplate,
                                  BigDecimal parentQtyFactor, int level, int levelMax,
                                  boolean phantomExpand, List<PdmBomLine> result, Set<String> pathVisited) {
        if (level >= levelMax) return;
        // 实际取子件来源: root bomId 的单层，或当进入半成品层时，去找该半成品作为 product_material 的 默认BOM
        List<PdmBomLine> directChildren = fetchDirectChildren(parentId, rootBomId, level);
        for (PdmBomLine line : directChildren) {
            // 累计用量 = 父级因子 × 本层毛用量
            BigDecimal factor = line.getGrossQty() == null ? line.getQuantity() : line.getGrossQty();
            if (factor == null) factor = BigDecimal.ONE;
            BigDecimal accum = parentQtyFactor.multiply(factor);

            line.setLevel(level);
            line.setPathQty(accum);
            String key = line.getBomId() + "-" + line.getParentMaterialId() + "-" + line.getComponentMaterialId() + "-" + level;
            if (pathVisited.contains(key)) continue;
            pathVisited.add(key);

            // 判断: 是否虚拟件 → 继续展开子件且把本层不加入(phantom sink)，还是先加入
            boolean phantom = phantomExpand && Integer.valueOf(1).equals(line.getIsPhantom());
            if (!phantom) {
                result.add(line);
            }

            // 半成品: component_material_id 若本身有默认BOM且层深允许，继续递归
            boolean compHasBom = (null != findDefaultBomByProduct(line.getComponentMaterialId()));
            if (compHasBom) {
                explodeRecursive(line.getComponentMaterialId(), rootBomId, null,
                        phantom ? accum : parentQtyFactor.multiply(line.getQuantity() == null ? BigDecimal.ONE : line.getQuantity()),
                        level + 1, levelMax, phantomExpand, result, pathVisited);
            }
        }
    }

    /** 直接子件：level=0 用 rootBomId，level≥1 用 componentId=半成品 的默认BOM下的直接子件 */
    private List<PdmBomLine> fetchDirectChildren(Long parentMaterialId, Long rootBomId, int level) {
        if (level == 0) {
            return lineMapper.listDirectChildren(rootBomId, parentMaterialId);
        }
        PdmBom subBom = findDefaultBomByProduct(parentMaterialId);
        if (subBom == null) return Collections.emptyList();
        return lineMapper.listDirectChildren(subBom.getBomId(), parentMaterialId);
    }

    private PdmBom findDefaultBomByProduct(Long productId) {
        List<PdmBom> list = bomMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PdmBom>()
                        .eq(PdmBom::getProductMaterialId, productId)
                        .in(PdmBom::getStatus, Arrays.asList(1, 2))
                        .orderByAsc(PdmBom::getIsDefault).orderByDesc(PdmBom::getCreateTime)
        );
        // 先找默认版本
        return list.stream().filter(b -> Integer.valueOf(1).equals(b.getIsDefault())).findFirst()
                .orElse(list.isEmpty() ? null : list.get(0));
    }

    @Override
    public Map<Long, PdmBomLine> explodeFlatSum(Long bomId, int levelMax, boolean phantomExpand) {
        List<PdmBomLine> tree = explodeTree(bomId, levelMax, phantomExpand);
        Map<Long, PdmBomLine> agg = new LinkedHashMap<>();
        for (PdmBomLine l : tree) {
            Long mid = l.getComponentMaterialId();
            if (agg.containsKey(mid)) {
                PdmBomLine exist = agg.get(mid);
                exist.setPathQty((exist.getPathQty() == null ? BigDecimal.ZERO : exist.getPathQty())
                        .add(l.getPathQty() == null ? BigDecimal.ZERO : l.getPathQty()));
            } else {
                PdmBomLine cp = new PdmBomLine();
                cp.setComponentMaterialId(mid);
                cp.setComponentMaterialCode(l.getComponentMaterialCode());
                cp.setComponentMaterialName(l.getComponentMaterialName());
                cp.setComponentSpec(l.getComponentSpec());
                cp.setUnit(l.getUnit());
                cp.setPathQty(l.getPathQty());
                agg.put(mid, cp);
            }
        }
        return agg;
    }

    /* ========== Where-Used ========== */
    @Override
    public List<PdmBomLine> whereUsedSingle(Long componentId, Integer lineStatus, Integer bomStatus) {
        return lineMapper.whereUsed(componentId, lineStatus, bomStatus);
    }

    @Override
    public List<Map<String, Object>> whereUsedRecursive(Long componentId, Integer lineStatus, Integer bomStatus, int levelMax) {
        if (levelMax <= 0) levelMax = 10;
        List<Map<String, Object>> result = new ArrayList<>();
        LinkedList<Object[]> queue = new LinkedList<>(); // [currentMatId, level, path, rootBomId]
        queue.add(new Object[]{componentId, 0, String.valueOf(componentId), null});
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Object[] cur = queue.poll();
            Long curMat = (Long) cur[0];
            int lvl = (int) cur[1];
            if (lvl >= levelMax) continue;
            List<PdmBomLine> parents = lineMapper.whereUsed(curMat, lineStatus, bomStatus);
            for (PdmBomLine line : parents) {
                String pk = line.getBomId() + "|" + line.getParentMaterialId() + "|" + curMat;
                if (visited.contains(pk)) continue;
                visited.add(pk);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("componentId", curMat);
                row.put("parentId", line.getParentMaterialId());
                row.put("parentCode", line.getParentMaterialCode());
                row.put("parentName", line.getParentMaterialName());
                row.put("bomId", line.getBomId());
                // 通过 line.bom_code, line.bom_name 由 whereUsed 联表字段注入 → 这里通过 getter 拿不到（实体无字段）改用反射式Map:
                // 简单方案: 再按bomId查一次
                try {
                    PdmBom b = bomMapper.selectById(line.getBomId());
                    if (b != null) {
                        row.put("bomCode", b.getBomCode());
                        row.put("bomName", b.getBomName());
                        row.put("version", b.getVersion());
                        row.put("bomStatus", b.getStatus());
                    }
                } catch (Exception ignored) { /* ignore */ }
                row.put("lineQty", line.getQuantity());
                row.put("level", lvl + 1);
                row.put("path", cur[2] + " <- " + line.getParentMaterialId());
                result.add(row);
                // 继续查父父项
                queue.add(new Object[]{line.getParentMaterialId(), lvl + 1,
                        cur[2] + " <- " + line.getParentMaterialId(), line.getBomId()});
            }
        }
        return result;
    }

    /* ========== 批量替换子件 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int replaceComponent(Long bomId, Long oldComponentId, Long newComponentId,
                                boolean keepAltGroup, String remark) {
        PdmBom old = bomMapper.selectById(bomId);
        if (old == null) throw new ServiceException("BOM不存在");
        if (Integer.valueOf(2).equals(old.getStatus()) || Integer.valueOf(5).equals(old.getStatus())) {
            throw new ServiceException("已启用/已归档BOM请先停用再替换");
        }
        if (oldComponentId == null || newComponentId == null) throw new ServiceException("新旧子件ID不能为空");
        if (oldComponentId.equals(newComponentId)) return 0;
        PdmMaterial newMat = materialMapper.selectById(newComponentId);
        if (newMat == null) throw new ServiceException("新子件物料不存在");

        List<PdmBomLine> affected = lineMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PdmBomLine>()
                        .eq(PdmBomLine::getBomId, bomId)
                        .eq(PdmBomLine::getComponentMaterialId, oldComponentId));
        int count = 0;
        for (PdmBomLine l : affected) {
            // 若替换后同父项+同子件重复则跳过(保留已有)
            int dup = lineMapper.countDuplicateLine(bomId, l.getParentMaterialId(), newComponentId, l.getLineId());
            if (dup > 0) {
                log.warn("替换子件跳过重复: bomId={}, parent={}, comp={}", bomId, l.getParentMaterialId(), newComponentId);
                continue;
            }
            l.setComponentMaterialId(newComponentId);
            l.setComponentMaterialCode(newMat.getMaterialCode());
            l.setComponentMaterialName(newMat.getMaterialName());
            l.setComponentSpec(newMat.getSpecification());
            l.setUnit(newMat.getUnit() == null ? l.getUnit() : newMat.getUnit());
            if (!keepAltGroup) l.setAltGroupId("");
            String appended = remark == null ? "" : ("【替换子件】old=" + oldComponentId + "→new=" + newComponentId + " " + remark);
            l.setRemark((l.getRemark() == null ? "" : l.getRemark()) + appended);
            lineMapper.updateById(l);
            count++;
        }
        return count;
    }

    /* ============== 私有工具 ============== */
    private void clearDefaultForOtherVersions(Long productId, Long excludeBomId) {
        List<PdmBom> list = bomMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PdmBom>()
                        .eq(PdmBom::getProductMaterialId, productId)
                        .eq(PdmBom::getIsDefault, 1)
                        .ne(excludeBomId != null, PdmBom::getBomId, excludeBomId)
        );
        for (PdmBom b : list) {
            b.setIsDefault(0);
            bomMapper.updateById(b);
        }
    }

    private PdmBom requireStatus(Long bomId, Integer... expected) {
        PdmBom old = bomMapper.selectById(bomId);
        if (old == null) throw new ServiceException("BOM不存在");
        Set<Integer> exp = Arrays.stream(expected).collect(Collectors.toSet());
        if (!exp.contains(old.getStatus())) {
            throw new ServiceException("当前BOM状态不允许该操作");
        }
        return old;
    }

    /** 批量插入行 + 字段校验与冗余填充 */
    @Transactional(rollbackFor = Exception.class)
    public void saveLines(Long bomId, Long productMaterialId, List<PdmBomLine> lines) {
        int idx = 0;
        for (PdmBomLine line : lines) {
            line.setBomId(bomId);
            // 父项物料: 未填则按顶层成品
            if (line.getParentMaterialId() == null) line.setParentMaterialId(productMaterialId);
            if (line.getLineNo() == null || line.getLineNo() <= 0) line.setLineNo(++idx * 10);
            if (line.getComponentMaterialId() == null) {
                throw new ServiceException("行项缺少子件物料ID(行号 " + line.getLineNo() + ")");
            }
            // 同父+同子重复
            int dup = lineMapper.countDuplicateLine(bomId, line.getParentMaterialId(),
                    line.getComponentMaterialId(), null);
            if (dup > 0) {
                throw new ServiceException("同父项下子件重复(父=" + line.getParentMaterialId()
                        + ",子=" + line.getComponentMaterialId() + ")");
            }
            PdmMaterial comp = materialMapper.selectById(line.getComponentMaterialId());
            if (comp == null) throw new ServiceException("子件物料不存在: " + line.getComponentMaterialId());
            if (line.getComponentMaterialCode() == null || line.getComponentMaterialCode().isBlank())
                line.setComponentMaterialCode(comp.getMaterialCode());
            if (line.getComponentMaterialName() == null || line.getComponentMaterialName().isBlank())
                line.setComponentMaterialName(comp.getMaterialName());
            if (line.getComponentSpec() == null || line.getComponentSpec().isBlank())
                line.setComponentSpec(comp.getSpecification());
            if (line.getUnit() == null || line.getUnit().isBlank())
                line.setUnit(comp.getUnit() == null ? "PCS" : comp.getUnit());
            if (line.getQuantity() == null) line.setQuantity(BigDecimal.ONE);
            if (line.getLossRate() == null) line.setLossRate(BigDecimal.ZERO);
            // 损耗率限 0~0.5
            if (line.getLossRate().compareTo(BigDecimal.ZERO) < 0) line.setLossRate(BigDecimal.ZERO);
            if (line.getLossRate().compareTo(new BigDecimal("0.5")) > 0) line.setLossRate(new BigDecimal("0.5"));
            // gross = qty / (1 - loss)
            BigDecimal denom = BigDecimal.ONE.subtract(line.getLossRate());
            BigDecimal gross = (denom.compareTo(BigDecimal.ZERO) <= 0)
                    ? line.getQuantity()
                    : line.getQuantity().divide(denom, 6, RoundingMode.HALF_UP);
            line.setGrossQty(gross);
            if (line.getIsPhantom() == null) line.setIsPhantom(0);
            if (line.getIsOptional() == null) line.setIsOptional(0);
            if (line.getSupplyType() == null) line.setSupplyType(1);
            if (line.getStatus() == null) line.setStatus(1);

            lineMapper.insert(line);
        }
    }

    /* ==================== Excel 导入 ==================== */

    @Override
    public void exportBomTemplate(OutputStream out) throws IOException {
        // 下拉列
        Map<Integer, String[]> dropdown = new LinkedHashMap<>();
        dropdown.put(6, new String[]{"PCS","KG","M","L","BOX","SET","PACK","ROLL","REEL","EA"}); // 单位
        dropdown.put(9, PdmBomExcelConverter.supplyTypeOptions()); // 供应类型

        // 示例行
        PdmBomExcelVO demo = new PdmBomExcelVO();
        demo.setLineNo(10);
        demo.setReferenceDesignator("R1,R2,R5-R8");
        demo.setComponentMaterialCode("RES-0402-1K-1%");
        demo.setComponentMaterialName("贴片电阻 1KΩ 1%");
        demo.setComponentSpec("0402 1KΩ ±1% 1/16W");
        demo.setQuantity("4");
        demo.setUnit("PCS");
        demo.setLossRatePercent("1");
        demo.setPackageType("0402");
        demo.setSupplyTypeLabel("采购");
        demo.setRemark("顶层贴片");

        EasyExcel.write(out, PdmBomExcelVO.class)
                .registerWriteHandler(new DropdownWriteHandler(dropdown))
                .sheet("BOM行项导入模板")
                .doWrite(Collections.singletonList(demo));
    }

    @Override
    public Map<String, Object> previewBomImport(MultipartFile file,
                                                  Long productMaterialId,
                                                  String bomName,
                                                  String version) throws IOException {
        // 1. 成品物料校验
        PdmMaterial product = materialMapper.selectById(productMaterialId);
        if (product == null) throw new ServiceException("成品物料不存在, productMaterialId=" + productMaterialId);

        // 2. 解析 Excel
        List<PdmBomExcelVO> rows = new ArrayList<>();
        EasyExcel.read(file.getInputStream(), PdmBomExcelVO.class, new com.alibaba.excel.event.AnalysisEventListener<PdmBomExcelVO>() {
            @Override
            public void invoke(PdmBomExcelVO vo, com.alibaba.excel.context.AnalysisContext ctx) { rows.add(vo); }
            @Override
            public void doAfterAllAnalysed(com.alibaba.excel.context.AnalysisContext ctx) { }
        }).sheet().doRead();

        if (rows.isEmpty()) throw new ServiceException("Excel 无数据行");

        // 3. 收集所有物料编码，批量查 PDM
        Set<String> codes = new LinkedHashSet<>();
        for (PdmBomExcelVO r : rows) {
            if (r.getComponentMaterialCode() != null && !r.getComponentMaterialCode().trim().isEmpty())
                codes.add(r.getComponentMaterialCode().trim());
            if (r.getAltMaterialCode() != null && !r.getAltMaterialCode().trim().isEmpty())
                codes.add(r.getAltMaterialCode().trim());
        }
        Map<String, PdmMaterial> materialByCode = new HashMap<>();
        if (!codes.isEmpty()) {
            List<PdmMaterial> found = materialMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PdmMaterial>()
                            .in(PdmMaterial::getMaterialCode, codes));
            for (PdmMaterial m : found) materialByCode.put(m.getMaterialCode(), m);
        }

        // 4. 逐行校验
        int errorCount = 0, warnCount = 0;
        List<Map<String, Object>> errorRows = new ArrayList<>();
        List<Map<String, Object>> warningRows = new ArrayList<>();
        List<Map<String, Object>> itemRows = new ArrayList<>();
        List<Map<String, Object>> precreateMaterials = new ArrayList<>();

        int idx = 0;
        for (PdmBomExcelVO vo : rows) {
            idx++;
            if (vo.getLineNo() == null) vo.setLineNo(idx * 10);
            PdmBomExcelConverter.validateRow(vo, materialByCode);

            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("lineNo", vo.getLineNo());
            rowMap.put("referenceDesignator", vo.getReferenceDesignator());
            rowMap.put("componentMaterialCode", vo.getComponentMaterialCode());
            rowMap.put("componentMaterialName", vo.getComponentMaterialName());
            rowMap.put("componentSpec", vo.getComponentSpec());
            rowMap.put("quantity", vo.getQuantity());
            rowMap.put("unit", vo.getUnit());
            rowMap.put("lossRatePercent", vo.getLossRatePercent());
            rowMap.put("packageType", vo.getPackageType());
            rowMap.put("supplyTypeLabel", vo.getSupplyTypeLabel());
            rowMap.put("altMaterialCode", vo.getAltMaterialCode());
            rowMap.put("altMaterialName", vo.getAltMaterialName());
            rowMap.put("remark", vo.getRemark());
            rowMap.put("resolvedMaterialId", vo.getResolvedMaterialId());
            rowMap.put("resolvedAltMaterialId", vo.getResolvedAltMaterialId());
            rowMap.put("needPrecreate", vo.isNeedPrecreate());
            rowMap.put("altNeedPrecreate", vo.isAltNeedPrecreate());
            rowMap.put("rowError", vo.getRowError());
            rowMap.put("rowWarning", vo.getRowWarning());

            itemRows.add(rowMap);

            if (vo.getRowError() != null) {
                errorCount++;
                errorRows.add(rowMap);
            }
            if (vo.getRowWarning() != null) {
                warnCount++;
                warningRows.add(rowMap);
            }

            // 收集预创建物料
            if (vo.isNeedPrecreate()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("code", vo.getComponentMaterialCode().trim());
                p.put("name", vo.getComponentMaterialName());
                p.put("spec", vo.getComponentSpec());
                p.put("unit", vo.getUnit());
                precreateMaterials.add(p);
            }
            if (vo.isAltNeedPrecreate()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("code", vo.getAltMaterialCode().trim());
                p.put("name", vo.getAltMaterialName());
                p.put("spec", "");
                p.put("unit", vo.getUnit());
                precreateMaterials.add(p);
            }
        }

        // 5. 构造返回
        Map<String, Object> result = new LinkedHashMap<>();
        // BOM 头预填
        Map<String, Object> bomHeader = new LinkedHashMap<>();
        bomHeader.put("bomCode", nextBomCode());
        bomHeader.put("bomName", (bomName == null || bomName.isBlank()) ? (product.getMaterialName() + " BOM") : bomName);
        bomHeader.put("productMaterialId", productMaterialId);
        bomHeader.put("productMaterialCode", product.getMaterialCode());
        bomHeader.put("productMaterialName", product.getMaterialName());
        bomHeader.put("version", (version == null || version.isBlank()) ? "V1.0" : version);
        bomHeader.put("unit", product.getUnit() == null ? "PCS" : product.getUnit());
        bomHeader.put("bomType", 1);
        bomHeader.put("status", 0);
        result.put("bomHeader", bomHeader);
        result.put("items", itemRows);
        result.put("errors", errorRows);
        result.put("warnings", warningRows);
        result.put("precreateMaterials", precreateMaterials);
        result.put("totalLines", rows.size());
        result.put("errorCount", errorCount);
        result.put("warningCount", warnCount);
        result.put("canConfirm", errorCount == 0);
        return result;
    }

    /** 根据物料编码/名称推断分类ID */
    private Long inferCategoryId(String code, Object nameObj) {
        String catCode = inferCategoryCode(code, nameObj);
        return categoryCodeToId().getOrDefault(catCode, 26L);
    }

    /** 分类编码 -> ID 映射（懒加载） */
    private volatile Map<String, Long> categoryCodeToId;
    private Map<String, Long> categoryCodeToId() {
        if (categoryCodeToId == null) {
            synchronized (this) {
                if (categoryCodeToId == null) {
                    categoryCodeToId = new HashMap<>();
                    List<PdmMaterialCategory> cats = categoryMapper.selectList(null);
                    for (PdmMaterialCategory c : cats) {
                        categoryCodeToId.put(c.getCategoryCode(), c.getCategoryId());
                    }
                }
            }
        }
        return categoryCodeToId;
    }

    /** 根据物料编码/名称推断分类编码 */
    private String inferCategoryCode(String code, Object nameObj) {
        String key = ((code == null ? "" : code) + " " + (nameObj == null ? "" : nameObj)).toUpperCase();
        if (key.contains("PCB")) return "PCB-RAW";
        if (key.contains("电容") || key.contains("CAP")) return "CAP";
        if (key.contains("电感") || key.contains("磁珠") || key.contains("IND")) return "IND";
        if (key.contains("晶振") || key.contains("OSC") || key.contains("频率")) return "OSC";
        if (key.contains("IC") || key.contains("芯片") || key.contains("SOC") || key.contains("集成电路") || key.contains("MCU")) return "IC";
        if (key.contains("电阻") || key.contains("RES")) return "RES";
        if (key.contains("二极管") || key.contains("三极管") || key.contains("SEM")) return "SEM";
        if (key.contains("连接器") || key.contains("接插件") || key.contains("CON")) return "CON";
        if (key.contains("结构") || key.contains("外壳") || key.contains("STR")) return "STR";
        if (key.contains("螺丝") || key.contains("紧固") || key.contains("FASTENER")) return "FASTENER";
        if (key.contains("继电器") || key.contains("RELAY")) return "RELAY";
        if (key.contains("保险丝") || key.contains("FUSE")) return "FUSE";
        if (key.contains("显示") || key.contains("DISPLAY") || key.contains("LED")) return "DISPLAY";
        if (key.contains("变压器") || key.contains("TRANS")) return "TRANS";
        if (key.contains("线缆") || key.contains("排线") || key.contains("CABLE")) return "CABLE";
        if (key.contains("焊锡") || key.contains("锡膏") || key.contains("锡线") || key.contains("SLD")) return "SLD";
        if (key.contains("胶") || key.contains("GLU")) return "GLU";
        if (key.contains("包装") || key.contains("PKG")) return "PKG";
        if (key.contains("标签") || key.contains("条码") || key.contains("LBL")) return "LBL";
        if (key.contains("治具") || key.contains("测试架") || key.contains("FIX")) return "FIX";
        if (key.contains("辅料") || key.contains("AST")) return "AST";
        if (key.contains("软件") || key.contains("SP")) return "SP";
        return "DAS";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Long confirmBomImport(Map<String, Object> previewData) {
        // 1. 解析 BOM 头
        Map<String, Object> header = (Map<String, Object>) previewData.get("bomHeader");
        if (header == null) throw new ServiceException("缺少BOM头信息");
        Long productMaterialId = toLong(header.get("productMaterialId"));
        PdmMaterial product = materialMapper.selectById(productMaterialId);
        if (product == null) throw new ServiceException("成品物料不存在");

        // 2. 预创建草稿物料
        List<Map<String, Object>> precreate = (List<Map<String, Object>>) previewData.get("precreateMaterials");
        Map<String, Long> precreatedIds = new HashMap<>();
        if (precreate != null) {
            for (Map<String, Object> p : precreate) {
                String code = String.valueOf(p.get("code"));
                // 检查是否已存在（可能之前预览后被手动创建）
                PdmMaterial existing = materialMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PdmMaterial>()
                                .eq(PdmMaterial::getMaterialCode, code));
                if (existing != null) {
                    precreatedIds.put(code, existing.getMaterialId());
                    continue;
                }
                PdmMaterial draft = new PdmMaterial();
                draft.setMaterialCode(code);
                draft.setMaterialName(p.get("name") != null ? String.valueOf(p.get("name")) : code);
                draft.setSpecification(p.get("spec") != null ? String.valueOf(p.get("spec")) : "(待补全)");
                draft.setUnit(p.get("unit") != null ? String.valueOf(p.get("unit")) : "PCS");
                draft.setCategoryId(inferCategoryId(code, p.get("name")));
                draft.setCategoryCode(inferCategoryCode(code, p.get("name")));
                draft.setMaterialType("RES");
                draft.setBrand("(待补全)");
                draft.setMpn(code);
                draft.setStatus(0);
                draft.setAbcClass("C");
                draft.setSerialTracked(0);
                draft.setRohsCompliant(1);
                draft.setProcureScope("ELEC");
                materialMapper.insert(draft);
                precreatedIds.put(code, draft.getMaterialId());
                log.info("BOM导入预创建草稿物料: code={}, id={}", code, draft.getMaterialId());
            }
        }

        // 3. 构造 PdmBom + Lines
        PdmBom bom = new PdmBom();
        bom.setBomCode(header.get("bomCode") != null ? String.valueOf(header.get("bomCode")) : nextBomCode());
        bom.setBomName(String.valueOf(header.get("bomName")));
        bom.setProductMaterialId(productMaterialId);
        bom.setProductMaterialCode(product.getMaterialCode());
        bom.setProductMaterialName(product.getMaterialName());
        bom.setVersion(header.get("version") != null ? String.valueOf(header.get("version")) : "V1.0");
        bom.setUnit(product.getUnit() == null ? "PCS" : product.getUnit());
        bom.setBomType(header.get("bomType") != null ? toInt(header.get("bomType")) : 1);
        bom.setStatus(0);
        bom.setIsDefault(0);
        bom.setQuantity(BigDecimal.ONE);

        // 4. 构造行项
        List<Map<String, Object>> items = (List<Map<String, Object>>) previewData.get("items");
        if (items == null || items.isEmpty()) throw new ServiceException("无行项数据");
        List<PdmBomLine> lines = new ArrayList<>();
        for (Map<String, Object> row : items) {
            // 跳过有错误的行
            if (row.get("rowError") != null && !String.valueOf(row.get("rowError")).isEmpty()) continue;

            PdmBomLine line = new PdmBomLine();
            line.setLineNo(toInt(row.get("lineNo")));
            line.setReferenceDesignator(str(row.get("referenceDesignator")));
            line.setComponentMaterialCode(str(row.get("componentMaterialCode")));

            // 解析物料ID：优先用已解析的，否则查预创建
            Long matId = toLong(row.get("resolvedMaterialId"));
            if (matId == null) {
                matId = precreatedIds.get(str(row.get("componentMaterialCode")));
            }
            if (matId == null) throw new ServiceException("行 " + line.getLineNo() + " 物料ID解析失败");
            line.setComponentMaterialId(matId);
            line.setComponentMaterialName(str(row.get("componentMaterialName")));
            line.setComponentSpec(str(row.get("componentSpec")));
            line.setQuantity(toBigDecimal(row.get("quantity")));
            line.setUnit(str(row.get("unit")) != null ? str(row.get("unit")) : "PCS");

            // 损耗率
            BigDecimal lossPct = toBigDecimal(row.get("lossRatePercent"));
            BigDecimal lossRate = (lossPct == null) ? BigDecimal.ZERO : lossPct.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            line.setLossRate(lossRate);
            BigDecimal denom = BigDecimal.ONE.subtract(lossRate);
            line.setGrossQty(denom.compareTo(BigDecimal.ZERO) <= 0
                    ? line.getQuantity()
                    : line.getQuantity().divide(denom, 6, RoundingMode.HALF_UP));

            line.setSupplyType(PdmBomExcelConverter.supplyCode(str(row.get("supplyTypeLabel"))));
            line.setStatus(1);
            line.setRemark(str(row.get("remark")));

            // 替代料
            String altCode = str(row.get("altMaterialCode"));
            if (altCode != null && !altCode.isEmpty()) {
                Long altId = toLong(row.get("resolvedAltMaterialId"));
                if (altId == null) altId = precreatedIds.get(altCode);
                if (altId != null) line.setAltGroupId(line.getComponentMaterialCode() + "+ALT");
            }

            lines.add(line);
        }

        bom.setLines(lines);

        // 5. 调用已有的 saveWithLines 创建 BOM
        return saveWithLines(bom);
    }

    // ============ 类型转换工具 ============
    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }
    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }
    private static String str(Object o) { return o == null ? null : String.valueOf(o).trim().isEmpty() ? null : String.valueOf(o).trim(); }
    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return new BigDecimal(((Number) o).toString());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
