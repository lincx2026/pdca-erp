package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;
import com.pcba.erp.scm.entity.ScmPurchaseOrderLine;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.mapper.ScmPurchaseOrderLineMapper;
import com.pcba.erp.scm.mapper.ScmPurchaseOrderMapper;
import com.pcba.erp.scm.mapper.ScmPurchaseRequisitionLineMapper;
import com.pcba.erp.scm.mapper.ScmPurchaseRequisitionMapper;
import com.pcba.erp.scm.service.IScmPurchaseOrderService;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionLineService;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmPurchaseRequisitionServiceImpl implements IScmPurchaseRequisitionService {

    private final ScmPurchaseRequisitionMapper mapper;
    private final ScmPurchaseRequisitionLineMapper lineMapper;
    private final ScmPurchaseOrderMapper poMapper;
    private final ScmPurchaseOrderLineMapper poLineMapper;
    @Lazy private final IScmPurchaseRequisitionLineService requisitionLineService;
    @Lazy private final IScmPurchaseOrderService purchaseOrderService;
    private final JdbcTemplate jdbcTemplate;
    private final org.mybatis.spring.SqlSessionTemplate sqlSession;

    @Override
    public IPage<ScmPurchaseRequisition> selectPage(int pageNum, int pageSize, String requisitionNo, String title, String applicantName, Long deptId, Integer status, String requiredDate) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), requisitionNo, title, applicantName, deptId, status, requiredDate);
    }

    @Override
    public ScmPurchaseRequisition getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseRequisition r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmPurchaseRequisition entity) {
        entity.setStatus(0);
        entity.setAuditBy(null); entity.setAuditTime(null); entity.setRejectReason(null);
        // 单号生成 (后端兜底)
        try {
            Field f = ScmPurchaseRequisition.class.getDeclaredField("requisitionNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) {
                String ts = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                Long n = jdbcTemplate.queryForObject("SELECT nextval('scm_requisition_number_seq')", Long.class);
                f.set(entity, "PR" + ts + "-" + n);
            }
        } catch (Exception ignore) {}
        // 申请日期
        try {
            Field f = ScmPurchaseRequisition.class.getDeclaredField("requisitionDate");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        // 申请人 (默认 admin=1)
        try {
            Field f = ScmPurchaseRequisition.class.getDeclaredField("applicantId");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 1L);
        } catch (Exception ignore) {}
        // 币种默认 CNY
        try {
            Field f = ScmPurchaseRequisition.class.getDeclaredField("currency");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) f.set(entity, "CNY");
        } catch (Exception ignore) {}
        // 单号查重 (若实体有"单号"字段且非空)
        try {
            Field noField = ScmPurchaseRequisition.class.getDeclaredField("requisitionNo");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                Field idField = ScmPurchaseRequisition.class.getDeclaredField("requisitionId");
                idField.setAccessible(true);
                Object idVal = idField.get(entity);
                if (mapper.countDuplicate(noVal.toString(), (Long) idVal) > 0) {
                    throw new ServiceException("编号已存在: " + noVal);
                }
            }
        } catch (NoSuchFieldException ignore) { /* 无对应字段跳过查重 */ }
          catch (IllegalAccessException ex) { throw new ServiceException("保存失败: " + ex.getMessage()); }

        // 默认值: status=0 / 数字 0
        try {
            Field s = ScmPurchaseRequisition.class.getDeclaredField("status");
            s.setAccessible(true);
            if (s.get(entity) == null) s.set(entity, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        for (String f : new String[]{"totalAmount","taxAmount","paidAmount","totalQty","refundAmount","qty","estimatePrice","estimateAmount","orderQty","receivedQty","returnedQty","unitPrice","lineAmount","taxRate","minQty","discountRate"}) {
            try {
                Field ff = ScmPurchaseRequisition.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        mapper.insert(entity);
        try {
            Field idF = ScmPurchaseRequisition.class.getDeclaredField("requisitionId");
            idF.setAccessible(true);
            return (Long) idF.get(entity);
        } catch (Exception ex) { return null; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmPurchaseRequisition entity) {
        Long id = null;
        try {
            Field idF = ScmPurchaseRequisition.class.getDeclaredField("requisitionId");
            idF.setAccessible(true);
            id = (Long) idF.get(entity);
        } catch (Exception ignore) {}
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseRequisition old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        if (!Integer.valueOf(0).equals(old.getStatus()) && !Integer.valueOf(4).equals(old.getStatus())) throw new ServiceException("仅草稿或已驳回的请购单可以修改");
        entity.setStatus(null);
        // 单号查重
        try {
            Field noField = ScmPurchaseRequisition.class.getDeclaredField("requisitionNo");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                if (mapper.countDuplicate(noVal.toString(), id) > 0)
                    throw new ServiceException("编号已存在");
            }
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmPurchaseRequisition r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        if (!Integer.valueOf(0).equals(r.getStatus()) && !Integer.valueOf(4).equals(r.getStatus())) throw new ServiceException("仅草稿或已驳回的请购单可以删除");
        lineMapper.delete(new LambdaQueryWrapper<ScmPurchaseRequisitionLine>().eq(ScmPurchaseRequisitionLine::getRequisitionId, id));
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        ScmPurchaseRequisition r = mapper.selectForUpdate(id);
        if (r == null) throw new ServiceException("记录不存在");
        int current = r.getStatus() == null ? 0 : r.getStatus();
        boolean allowed = (status == 1 && (current == 0 || current == 4)) ||
                ((status == 2 || status == 4) && current == 1) ||
                (status == 9 && (current == 0 || current == 1 || current == 4));
        if (!allowed) throw new ServiceException("当前请购单状态不允许该操作");
        if (status == 1 && lineMapper.selectCount(new LambdaQueryWrapper<ScmPurchaseRequisitionLine>()
                .eq(ScmPurchaseRequisitionLine::getRequisitionId, id)) == 0)
            throw new ServiceException("请先添加请购明细");
        int updated = mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ScmPurchaseRequisition>()
                .eq(ScmPurchaseRequisition::getRequisitionId, id).eq(ScmPurchaseRequisition::getStatus, current)
                .set(ScmPurchaseRequisition::getStatus, status));
        if (updated != 1) throw new ServiceException("单据状态已变化，请刷新后重试");
        if (status == 1 || status == 2 || status == 4) {
            lineMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ScmPurchaseRequisitionLine>()
                    .eq(ScmPurchaseRequisitionLine::getRequisitionId, id)
                    .set(ScmPurchaseRequisitionLine::getStatus, status == 4 ? 0 : status));
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id) {
        ScmPurchaseRequisition source = getDetail(id);
        List<ScmPurchaseRequisitionLine> lines = lineMapper.selectList(new LambdaQueryWrapper<ScmPurchaseRequisitionLine>()
                .eq(ScmPurchaseRequisitionLine::getRequisitionId, id));
        source.setRequisitionId(null); source.setRequisitionNo(null); source.setStatus(0);
        source.setAuditBy(null); source.setAuditTime(null); source.setRejectReason(null);
        source.setCreateBy(null); source.setCreateTime(null); source.setUpdateBy(null); source.setUpdateTime(null);
        source.setRequisitionDate(LocalDate.now());
        Long newId = save(source);
        for (ScmPurchaseRequisitionLine line : lines) {
            line.setLineId(null); line.setRequisitionId(newId); line.setStatus(0);
            line.setConvertedPoId(null); line.setConvertedPoLineId(null); line.setConvertedQty(BigDecimal.ZERO);
            line.setSupplierPriceId(null); line.setErrorMsg(null);
            line.setCreateBy(null); line.setCreateTime(null); line.setUpdateBy(null); line.setUpdateTime(null);
            lineMapper.insert(line);
        }
        return newId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reject(Long id, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new ServiceException("驳回原因不能为空且不能超过500字");
        changeStatus(id, 4);
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ScmPurchaseRequisition>()
                .eq(ScmPurchaseRequisition::getRequisitionId, id).set(ScmPurchaseRequisition::getRejectReason, reason));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawConversion(Long id) {
        jdbcTemplate.queryForList("SELECT requisition_id FROM scm_purchase_requisition WHERE requisition_id=? FOR UPDATE", id);
        ScmPurchaseRequisition req = getDetail(id);
        if (!java.util.Arrays.asList(1, 2, 3).contains(req.getStatus())) throw new ServiceException("当前请购单状态不能撤回");
        jdbcTemplate.queryForList("SELECT line_id FROM scm_purchase_requisition_line WHERE requisition_id=? ORDER BY line_id FOR UPDATE", id);
        Long orphan = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scm_purchase_order_line l LEFT JOIN scm_purchase_order p ON p.po_id=l.po_id WHERE l.requisition_id=? AND p.po_id IS NULL", Long.class, id);
        if (orphan != null && orphan > 0) throw new ServiceException("存在已删除采购订单的孤立明细，请联系管理员核对后处理");
        List<Long> poIds = jdbcTemplate.queryForList("SELECT DISTINCT po_id FROM scm_purchase_order_line WHERE requisition_id=? ORDER BY po_id", Long.class, id);
        for (Long poId : poIds) {
            Long other = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scm_purchase_order_line WHERE po_id=? AND (requisition_id IS NULL OR requisition_id<>?)", Long.class, poId, id);
            if (other != null && other > 0) throw new ServiceException("采购订单包含其他请购单或手工行，请先在采购订单中处理");
            purchaseOrderService.remove(poId);
        }
        Long remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scm_purchase_requisition_line WHERE requisition_id=? AND converted_qty>0", Long.class, id);
        if (remaining != null && remaining > 0) throw new ServiceException("转单记录不完整，请联系管理员核对后处理");
        jdbcTemplate.update("UPDATE scm_purchase_requisition SET status=0,audit_by='',audit_time=NULL,reject_reason=NULL WHERE requisition_id=?", id);
        jdbcTemplate.update("UPDATE scm_purchase_requisition_line SET status=0 WHERE requisition_id=?", id);
        sqlSession.clearCache();
    }

    @Override
    public List<ScmPurchaseRequisition> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScmPurchaseRequisition>()
                .orderByDesc("requisition_id"));
    }

    // ======================================================================
    // T5-2: 安全库存短缺 → 请购行
    // ======================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateFromSafetyStock(Map<String, Object> options) {
        log.debug("[generateFromSafetyStock] begin options={}", options);
        if (options == null) options = new HashMap<>();

        Long warehouseId = null;
        Long applicantId = 1L;
        int requiredDateOffsetDays = 14;
        try {
            Object w = options.get("warehouseId");
            if (w != null && !String.valueOf(w).isBlank()) warehouseId = Long.valueOf(String.valueOf(w));
        } catch (Exception ignore) {}
        try {
            Object a = options.get("applicantId");
            if (a != null && !String.valueOf(a).isBlank()) applicantId = Long.valueOf(String.valueOf(a));
        } catch (Exception ignore) {}
        try {
            Object rd = options.get("requiredDateOffsetDays");
            if (rd != null && !String.valueOf(rd).isBlank()) requiredDateOffsetDays = Integer.parseInt(String.valueOf(rd));
        } catch (Exception ignore) {}
        final LocalDate requiredDate = LocalDate.now().plusDays(requiredDateOffsetDays);

        // 如果 warehouseId 没传，取第一个非虚拟仓
        if (warehouseId == null) {
            try {
                List<Long> ids = jdbcTemplate.queryForList(
                        "SELECT warehouse_id FROM wms_warehouse WHERE status=1 AND (warehouse_type IS NULL OR warehouse_type <> 'IQC') LIMIT 1",
                        Long.class);
                if (ids != null && !ids.isEmpty()) warehouseId = ids.get(0);
            } catch (Exception ignore) {}
        }

        // 扫描安全库存短缺
        String sql = "SELECT m.material_id, m.material_code, m.material_name, m.specification, m.unit, " +
                " COALESCE(m.safety_stock_min,0) reorder_point, " +
                " COALESCE(m.safety_stock_max,0) safety_stock, " +
                " COALESCE(m.min_order_qty,0) moq, " +
                " COALESCE(SUM(i.quantity - i.locked_qty),0) available " +
                "FROM pdm_material m " +
                "LEFT JOIN wms_inventory i ON m.material_id=i.material_id " +
                "  AND NOT EXISTS (SELECT 1 FROM wms_warehouse w WHERE w.warehouse_id=i.warehouse_id AND w.warehouse_type='IQC') " +
                "WHERE m.status=2 " +
                "GROUP BY m.material_id, m.material_code, m.material_name, m.specification, m.unit, " +
                "         m.safety_stock_min, m.safety_stock_max, m.min_order_qty " +
                "HAVING COALESCE(SUM(i.quantity - i.locked_qty),0) < (COALESCE(m.safety_stock_min,0) + COALESCE(m.safety_stock_max,0)) " +
                "   AND (COALESCE(m.safety_stock_min,0) + COALESCE(m.safety_stock_max,0)) > 0";

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.warn("[generateFromSafetyStock] 扫描安全库存异常，回退到使用safety_stock_min字段单独判断: {}", e.getMessage());
            rows = jdbcTemplate.queryForList(
                    "SELECT m.material_id, m.material_code, m.material_name, m.specification, m.unit, " +
                            " COALESCE(m.safety_stock_min,0) reorder_point, 0 safety_stock, " +
                            " COALESCE(m.min_order_qty,0) moq, " +
                            " COALESCE(SUM(i.quantity - i.locked_qty),0) available " +
                            "FROM pdm_material m " +
                            "LEFT JOIN wms_inventory i ON m.material_id=i.material_id " +
                            "  AND NOT EXISTS (SELECT 1 FROM wms_warehouse w WHERE w.warehouse_id=i.warehouse_id AND w.warehouse_type='IQC') " +
                            "WHERE m.status=2 " +
                            "GROUP BY m.material_id, m.material_code, m.material_name, m.specification, m.unit, " +
                            "         m.safety_stock_min, m.safety_stock_max, m.min_order_qty " +
                            "HAVING COALESCE(SUM(i.quantity - i.locked_qty),0) < COALESCE(m.safety_stock_min,0) " +
                            "   AND COALESCE(m.safety_stock_min,0) > 0");
        }

        if (rows == null || rows.isEmpty()) {
            throw new ServiceException("未扫描到任何安全库存短缺物料");
        }

        // 计算 deficit + MOQ 向上取整
        Map<Long, BigDecimal> deficitMap = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> matInfoMap = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Long materialId = ((Number) r.get("material_id")).longValue();
            BigDecimal reorderPoint = toBD(r.get("reorder_point"));
            BigDecimal safetyStock = toBD(r.get("safety_stock"));
            BigDecimal moq = toBD(r.get("moq"));
            BigDecimal available = toBD(r.get("available"));
            BigDecimal deficit = reorderPoint.add(safetyStock).subtract(available);
            if (deficit.compareTo(BigDecimal.ZERO) < 0) deficit = BigDecimal.ZERO;
            if (deficit.compareTo(BigDecimal.ZERO) == 0) continue;
            // MOQ 向上取整
            if (moq != null && moq.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal[] div = deficit.divideAndRemainder(moq);
                if (div[1].compareTo(BigDecimal.ZERO) > 0) {
                    deficit = moq.multiply(div[0].add(BigDecimal.ONE));
                } else if (deficit.compareTo(moq) < 0) {
                    deficit = moq;
                }
            }
            deficitMap.put(materialId, deficit);
            matInfoMap.put(materialId, r);
        }
        if (deficitMap.isEmpty()) {
            throw new ServiceException("未扫描到任何安全库存短缺物料（MOQ校正后为空）");
        }

        // 复用/新建 当日草稿
        LocalDate today = LocalDate.now();
        ScmPurchaseRequisition requisition = options().stream()
                .filter(r -> today.equals(r.getRequisitionDate()))
                .filter(r -> {
                    Integer s = r.getStatus();
                    return Integer.valueOf(0).equals(s) || Integer.valueOf(1).equals(s);
                })
                .findFirst()
                .orElse(null);
        Long requisitionId;
        if (requisition != null) {
            requisitionId = requisition.getRequisitionId();
            log.info("[generateFromSafetyStock] 追加到当日请购草稿 requisitionId={}", requisitionId);
        } else {
            ScmPurchaseRequisition pr = new ScmPurchaseRequisition();
            pr.setTitle("安全库存短缺补料");
            pr.setReason("系统扫描安全库存短缺补料");
            pr.setWarehouseId(warehouseId);
            pr.setRequisitionDate(today);
            pr.setRequiredDate(requiredDate);
            pr.setApplicantId(applicantId);
            pr.setCurrency("CNY");
            pr.setTaxRate(new BigDecimal("13"));
            pr.setStatus(0);
            requisitionId = save(pr);
            log.info("[generateFromSafetyStock] 新建请购单 requisitionId={}", requisitionId);
        }

        // 已有行 按 materialId 聚合
        Map<Long, ScmPurchaseRequisitionLine> existingByMaterial =
                requisitionLineService.options().stream()
                        .filter(l -> requisitionId.equals(l.getRequisitionId()))
                        .collect(Collectors.toMap(
                                ScmPurchaseRequisitionLine::getMaterialId,
                                l -> l,
                                (a, b) -> a,
                                LinkedHashMap::new));

        int nextLineNo = existingByMaterial.size() + 1;
        for (Map.Entry<Long, BigDecimal> e : deficitMap.entrySet()) {
            Long mid = e.getKey();
            BigDecimal deficit = e.getValue();
            ScmPurchaseRequisitionLine existing = existingByMaterial.get(mid);
            if (existing != null) {
                BigDecimal newQty = existing.getQty() == null ? BigDecimal.ZERO : existing.getQty();
                newQty = newQty.add(deficit);
                existing.setQty(newQty);
                requisitionLineService.update(existing);
            } else {
                Map<String, Object> info = matInfoMap.get(mid);
                ScmPurchaseRequisitionLine nl = new ScmPurchaseRequisitionLine();
                nl.setRequisitionId(requisitionId);
                nl.setLineNo(nextLineNo++);
                nl.setMaterialId(mid);
                if (info != null) {
                    nl.setMaterialCode(info.get("material_code") == null ? null : info.get("material_code").toString());
                    nl.setMaterialName(info.get("material_name") == null ? null : info.get("material_name").toString());
                    nl.setSpecification(info.get("specification") == null ? null : info.get("specification").toString());
                    nl.setUnit(info.get("unit") == null ? null : info.get("unit").toString());
                }
                nl.setQty(deficit);
                nl.setRequiredDate(requiredDate);
                nl.setStatus(0);
                nl.setRemark("安全库存补料");
                requisitionLineService.save(nl);
            }
        }
        log.debug("[generateFromSafetyStock] done requisitionId={} count={}", requisitionId, deficitMap.size());
        return requisitionId;
    }

    // ======================================================================
    // T5-3: 请购已审核 → 批量勾选行 → 按默认供方+阶梯价 → 合并PO
    // ======================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> convertToPurchaseOrder(List<Long> lineIds, Map<String, Object> commonHeader) {
        log.debug("[convertToPurchaseOrder] begin lineIds={} header={}", lineIds, commonHeader);
        if (lineIds == null || lineIds.isEmpty()) {
            throw new ServiceException("请选择要转采购的请购行");
        }
        Map<String, Object> hdr = commonHeader == null ? new HashMap<>() : commonHeader;
        LocalDate orderDate = hdr.get("orderDate") != null ? parseDate(hdr.get("orderDate")) : LocalDate.now();
        int deliveryDateOffsetDays = 20;
        try {
            Object d = hdr.get("deliveryDateOffsetDays");
            if (d != null && !String.valueOf(d).isBlank()) deliveryDateOffsetDays = Integer.parseInt(String.valueOf(d));
        } catch (Exception ignore) {}
        final LocalDate deliveryDateDefault = orderDate.plusDays(deliveryDateOffsetDays);
        String paymentTerm = hdr.get("paymentTerm") != null ? String.valueOf(hdr.get("paymentTerm")) : "月结60天";
        String currencyDefault = hdr.get("currency") != null ? String.valueOf(hdr.get("currency")) : "CNY";
        BigDecimal taxRateDefault = toBD(hdr.get("taxRate"));
        if (taxRateDefault == null || taxRateDefault.compareTo(BigDecimal.ZERO) <= 0) taxRateDefault = new BigDecimal("13");
        Long buyerId = null;
        try {
            Object b = hdr.get("buyerId");
            if (b != null && !String.valueOf(b).isBlank()) buyerId = Long.valueOf(String.valueOf(b));
        } catch (Exception ignore) {}
        String bizCategory = hdr.get("bizCategory") != null ? String.valueOf(hdr.get("bizCategory")) : "NORMAL";
        // 可选: qtyOverrides
        Map<Long, BigDecimal> qtyOverrides = new HashMap<>();
        try {
            Object qo = hdr.get("qtyOverrides");
            if (qo instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) qo).entrySet()) {
                    Long k = Long.valueOf(String.valueOf(e.getKey()));
                    BigDecimal v = toBD(e.getValue());
                    if (v != null) qtyOverrides.put(k, v);
                }
            }
        } catch (Exception ignore) {}

        List<Long> missingLineIds = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Long> poIds = new ArrayList<>();
        int mergedCount = 0;

        // Serialize conversions of the same lines before checking remaining quantity.
        List<Long> uniqueLineIds = lineIds.stream().distinct().sorted().toList();
        String placeholders = String.join(",", Collections.nCopies(uniqueLineIds.size(), "?"));
        jdbcTemplate.queryForList("SELECT requisition_id FROM scm_purchase_requisition WHERE requisition_id IN (SELECT requisition_id FROM scm_purchase_requisition_line WHERE line_id IN (" + placeholders + ")) ORDER BY requisition_id FOR UPDATE", Long.class, uniqueLineIds.toArray());
        jdbcTemplate.queryForList("SELECT line_id FROM scm_purchase_requisition_line WHERE line_id IN (" + placeholders + ") ORDER BY line_id FOR UPDATE", Long.class, uniqueLineIds.toArray());
        lineIds = uniqueLineIds;
        // 取所有行
        List<ScmPurchaseRequisitionLine> allLines = lineMapper.selectBatchIds(lineIds);
        if (allLines.size() != lineIds.size()) {
            List<Long> foundIds = allLines.stream().map(ScmPurchaseRequisitionLine::getLineId).collect(Collectors.toList());
            for (Long lid : lineIds) {
                if (!foundIds.contains(lid)) {
                    missingLineIds.add(lid);
                    warnings.add("请购行不存在: lineId=" + lid);
                    log.warn("[convertToPurchaseOrder] 行不存在 lineId={}", lid);
                }
            }
        }
        // 1. 校验: 对应 requisition.status >=2 已审 & line.status=2
        Map<Long, ScmPurchaseRequisition> requisitionMap = new HashMap<>();
        for (ScmPurchaseRequisitionLine line : allLines) {
            ScmPurchaseRequisition req = requisitionMap.computeIfAbsent(line.getRequisitionId(),
                    id -> mapper.selectById(id));
            if (req == null) {
                throw new ServiceException("请购单不存在: requisitionId=" + line.getRequisitionId());
            }
            if (!Integer.valueOf(2).equals(req.getStatus())) {
                throw new ServiceException("请购单未审核 status=" + req.getStatus() + ", 请先审核后再转采购 requisitionId=" + req.getRequisitionId());
            }
            if (line.getStatus() == null || line.getStatus() != 2) {
                throw new ServiceException("lineId=" + line.getLineId() + " status=" + line.getStatus() + " 不能转采购 (要求 status=2)");
            }
        }

        // 2. 对每 line：找 defaultSupplier + 匹配阶梯价
        // 按请购单、供应商、币种和交货日分组，避免跨请购单合并
        Map<String, ScmPurchaseOrder> poBucket = new LinkedHashMap<>();
        Map<String, List<ScmPurchaseOrderLine>> poLinesBucket = new LinkedHashMap<>();
        // 记录 lineId -> 已转化的 PO 行 & qty （稍后回写）
        Map<Long, PoLineBackRef> backRefs = new HashMap<>();

        for (ScmPurchaseRequisitionLine line : allLines) {
            Long lineId = line.getLineId();
            Long materialId = line.getMaterialId();

            // 默认供方
            BigDecimal alreadyConverted = line.getConvertedQty() == null ? BigDecimal.ZERO : line.getConvertedQty();
            BigDecimal remaining = (line.getQty() == null ? BigDecimal.ZERO : line.getQty()).subtract(alreadyConverted);
            BigDecimal qty = qtyOverrides.getOrDefault(lineId, remaining);
            if (qty.signum() <= 0 || qty.compareTo(remaining) > 0) throw new ServiceException("请购行 " + lineId + " 转单数量超过未转数量");
            ScmPurchaseRequisitionLine estimate = requisitionLineService.estimate(materialId, line.getSupplierId(), qty, currencyDefault, true);
            if (!"QUOTE".equals(estimate.getEstimateSource())) throw new ServiceException("物料 " + line.getMaterialCode() + "：" + estimate.getErrorMsg());
            Long supplierId = estimate.getSupplierId();
            String supplierCode = null;
            String supplierName = null;
            if (supplierId != null) {
                List<Map<String, Object>> suppliers = jdbcTemplate.queryForList(
                        "SELECT supplier_code, supplier_name FROM scm_supplier WHERE supplier_id = ?", supplierId);
                if (suppliers.isEmpty()) throw new ServiceException("请购行供应商不存在: " + supplierId);
                supplierCode = (String) suppliers.get(0).get("supplier_code");
                supplierName = (String) suppliers.get(0).get("supplier_name");
            }

            if (supplierId == null) {
                line.setErrorMsg("无默认供方，先维护默认供");
                lineMapper.updateById(line);
                missingLineIds.add(lineId);
                warnings.add("物料 " + (line.getMaterialCode() == null ? materialId : line.getMaterialCode()) + " 无默认供方");
                log.warn("[convertToPurchaseOrder] 行 {} 无默认供方 物料={}", lineId, materialId);
                continue;
            }
            log.info("[convertToPurchaseOrder] 命中默认供方 lineId={} materialId={} supplierId={} supplier={}",
                    lineId, materialId, supplierId, supplierName);

            // 阶梯价匹配
            BigDecimal unitPrice = estimate.getEstimatePrice();
            Long priceId = estimate.getSupplierPriceId();
            if (hdr.get("expectedPrices") instanceof Map<?, ?> expected) {
                Object value = expected.get(String.valueOf(lineId));
                if (!(value instanceof Map<?, ?> item) || item.get("priceId") == null || item.get("unitPrice") == null
                        || !String.valueOf(priceId).equals(String.valueOf(item.get("priceId")))
                        || unitPrice.compareTo(new BigDecimal(String.valueOf(item.get("unitPrice")))) != 0
                        || (item.get("supplierId") != null && !String.valueOf(supplierId).equals(String.valueOf(item.get("supplierId"))))
                        || (item.get("currency") != null && !String.valueOf(estimate.getEstimateCurrency()).equalsIgnoreCase(String.valueOf(item.get("currency"))))
                        || (item.get("qty") != null && qty.compareTo(new BigDecimal(String.valueOf(item.get("qty")))) != 0))
                    throw new ServiceException("物料 " + line.getMaterialCode() + " 的报价已变化，请重新预览后转单");
            }

            BigDecimal lineTaxRate = taxRateDefault;
            // TODO: requisitionLine 上 tax_rate 字段目前不存在，所以用 commonHeader 默认
            String lineCurrency = estimate.getEstimateCurrency() != null ? estimate.getEstimateCurrency() : currencyDefault;

            // 交货日：行 requiredDate > default deliveryDateDefault
            LocalDate dd = line.getRequiredDate() != null ? line.getRequiredDate() : deliveryDateDefault;

            String bucketKey = line.getRequisitionId() + "_" + supplierId + "_" + lineCurrency + "_" + dd.toString();
            ScmPurchaseOrder po = poBucket.get(bucketKey);
            if (po == null) {
                po = new ScmPurchaseOrder();
                po.setPoType("PURCHASE");
                po.setRequisitionId(line.getRequisitionId());
                po.setBizCategory(bizCategory);
                po.setSupplierId(supplierId);
                po.setSupplierCode(supplierCode);
                po.setSupplierName(supplierName);
                po.setBuyerId(buyerId);
                po.setPoDate(orderDate);
                po.setDeliveryDate(dd);
                po.setCurrency(lineCurrency);
                po.setTaxRate(lineTaxRate);
                po.setPaymentTerm(paymentTerm);
                po.setStatus(0);
                po.setRemark("来自请购批量转PO");
                poBucket.put(bucketKey, po);
                poLinesBucket.put(bucketKey, new ArrayList<>());
                mergedCount++;
            }

            ScmPurchaseOrderLine poLine = new ScmPurchaseOrderLine();
            poLine.setRequisitionLineId(lineId);
            poLine.setRequisitionId(line.getRequisitionId());
            poLine.setMaterialId(line.getMaterialId());
            poLine.setMaterialCode(line.getMaterialCode());
            poLine.setMaterialName(line.getMaterialName());
            poLine.setSpecification(line.getSpecification());
            poLine.setUnit(line.getUnit());
            poLine.setOrderQty(qty);
            poLine.setReceivedQty(BigDecimal.ZERO);
            poLine.setReturnedQty(BigDecimal.ZERO);
            poLine.setUnitPrice(unitPrice);
            poLine.setTaxRate(lineTaxRate);
            BigDecimal lineAmount = qty.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
            poLine.setLineAmount(lineAmount);
            BigDecimal taxAmt = lineAmount.multiply(lineTaxRate)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            poLine.setTaxAmount(taxAmt);
            poLine.setRequiredDate(line.getRequiredDate());
            // QC 默认：从物料 qc_required 字段取（若存在）
            poLine.setQcRequired(findMaterialQcRequired(materialId));
            poLine.setQcStatus(0);
            poLine.setRemark(line.getRemark());
            // warehouseId 取 requisition 上的
            ScmPurchaseRequisition r = requisitionMap.get(line.getRequisitionId());
            if (r != null) poLine.setWarehouseId(r.getWarehouseId());
            // 先不写 lineNo, poId（等插入po后回填）
            poLinesBucket.get(bucketKey).add(poLine);
            backRefs.put(lineId, new PoLineBackRef(null, null, qty, supplierId, supplierCode, supplierName, priceId));
        }

        // 3. 保存 PO + 行
        for (Map.Entry<String, ScmPurchaseOrder> e : poBucket.entrySet()) {
            ScmPurchaseOrder po = e.getValue();
            List<ScmPurchaseOrderLine> lines = poLinesBucket.get(e.getKey());
            Long poId = purchaseOrderService.save(po);
            poIds.add(poId);

            int poLineNo = 1;
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;
            for (ScmPurchaseOrderLine pol : lines) {
                pol.setPoId(poId);
                pol.setLineNo(poLineNo++);
                poLineMapper.insert(pol);
                totalAmount = totalAmount.add(pol.getLineAmount() == null ? BigDecimal.ZERO : pol.getLineAmount());
                totalTax = totalTax.add(pol.getTaxAmount() == null ? BigDecimal.ZERO : pol.getTaxAmount());

                // 回写 backRef
                if (pol.getRequisitionLineId() != null) {
                    PoLineBackRef ref = backRefs.get(pol.getRequisitionLineId());
                    if (ref != null) {
                        ref.poId = poId;
                        ref.poLineId = pol.getLineId();
                    }
                }
            }
            // 更新 PO 合计
            po.setPoId(poId);
            po.setTotalAmount(totalAmount);
            po.setTaxAmount(totalTax);
            try {
                purchaseOrderService.update(po);
            } catch (Exception ignore) {}
        }

        // 5 + 6: 回写 requisitionLine & requisition 头 status
        for (ScmPurchaseRequisitionLine line : allLines) {
            PoLineBackRef ref = backRefs.get(line.getLineId());
            if (ref == null) continue; // 无供方已处理 missingLineIds
            line.setConvertedPoId(ref.poId);
            line.setConvertedPoLineId(ref.poLineId);
            BigDecimal converted = (line.getConvertedQty() == null ? BigDecimal.ZERO : line.getConvertedQty()).add(ref.qty);
            line.setConvertedQty(converted);
            line.setSupplierId(ref.supplierId);
            line.setSupplierCode(ref.supplierCode);
            line.setSupplierName(ref.supplierName);
            line.setSupplierPriceId(ref.priceId);
            line.setErrorMsg("");
            BigDecimal originalQty = line.getQty() == null ? BigDecimal.ZERO : line.getQty();
            if (converted.compareTo(originalQty) >= 0) {
                line.setStatus(3); // 已转完
            } else {
                line.setStatus(2); // 部分转
            }
            lineMapper.updateById(line);
        }

        // 汇总更新 requisition 头状态
        Map<Long, List<ScmPurchaseRequisitionLine>> linesByReq = allLines.stream()
                .collect(Collectors.groupingBy(ScmPurchaseRequisitionLine::getRequisitionId));
        for (Map.Entry<Long, List<ScmPurchaseRequisitionLine>> re : linesByReq.entrySet()) {
            Long reqId = re.getKey();
            // 取 requisition 所有相关 line 最新
            List<ScmPurchaseRequisitionLine> allReqLines = requisitionLineService.options().stream()
                    .filter(l -> reqId.equals(l.getRequisitionId()))
                    .collect(Collectors.toList());
            boolean allDone = allReqLines.stream().allMatch(l -> {
                Integer s = l.getStatus();
                return Integer.valueOf(3).equals(s);
            });
            boolean anyConverted = allReqLines.stream().anyMatch(l -> Integer.valueOf(3).equals(l.getStatus()));
            boolean anyPending = allReqLines.stream().anyMatch(l -> Integer.valueOf(2).equals(l.getStatus()));
            ScmPurchaseRequisition req = mapper.selectById(reqId);
            if (req != null) {
                if (allDone) {
                    req.setStatus(3);
                } else if (anyConverted && anyPending) {
                    req.setStatus(2); // 部分转（仍保持原已审）
                }
                // 否则原状态不变
                mapper.updateById(req);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("poIds", poIds);
        result.put("missingLineIds", missingLineIds);
        result.put("warnings", warnings);
        result.put("mergedCount", mergedCount);
        log.info("[convertToPurchaseOrder] done poIds={} missingLineIds={} mergedCount={}", poIds, missingLineIds, mergedCount);
        log.debug("[convertToPurchaseOrder] done warnings={}", warnings);
        return result;
    }

    // ============ 内部工具 ============

    private static class PoLineBackRef {
        Long poId;
        Long poLineId;
        final BigDecimal qty;
        final Long supplierId;
        final String supplierCode;
        final String supplierName;
        final Long priceId;
        PoLineBackRef(Long poId, Long poLineId, BigDecimal qty, Long supplierId,
                      String supplierCode, String supplierName, Long priceId) {
            this.poId = poId; this.poLineId = poLineId; this.qty = qty;
            this.supplierId = supplierId; this.supplierCode = supplierCode;
            this.supplierName = supplierName; this.priceId = priceId;
        }
    }

    private static BigDecimal toBD(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static LocalDate parseDate(Object v) {
        if (v == null) return LocalDate.now();
        try {
            if (v instanceof LocalDate) return (LocalDate) v;
            if (v instanceof java.util.Date) return ((java.util.Date) v).toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            return LocalDate.parse(String.valueOf(v));
        } catch (Exception e) { return LocalDate.now(); }
    }

    private Integer findMaterialQcRequired(Long materialId) {
        // Material master has no QC flag; leave the purchase line's default unchanged.
        return 0;
    }
}
