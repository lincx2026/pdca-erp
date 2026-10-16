package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;
import com.pcba.erp.scm.entity.ScmPurchaseOrderLine;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.entity.ScmSupplierPrice;
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
        // 单号生成 (后端兜底)
        try {
            Field f = ScmPurchaseRequisition.class.getDeclaredField("requisitionNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) {
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                f.set(entity, "PR" + ts);
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
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        ScmPurchaseRequisition r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        try {
            Field s = ScmPurchaseRequisition.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
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
            if (req.getStatus() == null || req.getStatus() < 2) {
                throw new ServiceException("请购单未审核 status=" + req.getStatus() + ", 请先审核后再转采购 requisitionId=" + req.getRequisitionId());
            }
            if (line.getStatus() == null || line.getStatus() != 2) {
                throw new ServiceException("lineId=" + line.getLineId() + " status=" + line.getStatus() + " 不能转采购 (要求 status=2)");
            }
        }

        // 2. 对每 line：找 defaultSupplier + 匹配阶梯价
        // PO 组合 key: supplierId + currency + deliveryDate
        Map<String, ScmPurchaseOrder> poBucket = new LinkedHashMap<>();
        Map<String, List<ScmPurchaseOrderLine>> poLinesBucket = new LinkedHashMap<>();
        // 记录 lineId -> 已转化的 PO 行 & qty （稍后回写）
        Map<Long, PoLineBackRef> backRefs = new HashMap<>();

        for (ScmPurchaseRequisitionLine line : allLines) {
            Long lineId = line.getLineId();
            Long materialId = line.getMaterialId();

            // 默认供方
            Map<String, Object> defaultSupplier = findDefaultSupplier(materialId);
            Long supplierId = defaultSupplier == null ? null : (Long) defaultSupplier.get("supplierId");
            String supplierCode = defaultSupplier == null ? null : (String) defaultSupplier.get("supplierCode");
            String supplierName = defaultSupplier == null ? null : (String) defaultSupplier.get("supplierName");

            if (supplierId == null) {
                line.setStatus(7);
                line.setErrorMsg("无默认供方，先维护默认供");
                requisitionLineService.update(line);
                missingLineIds.add(lineId);
                warnings.add("物料 " + (line.getMaterialCode() == null ? materialId : line.getMaterialCode()) + " 无默认供方");
                log.warn("[convertToPurchaseOrder] 行 {} 无默认供方 物料={}", lineId, materialId);
                continue;
            }
            log.info("[convertToPurchaseOrder] 命中默认供方 lineId={} materialId={} supplierId={} supplier={}",
                    lineId, materialId, supplierId, supplierName);

            // 阶梯价匹配
            BigDecimal qty = qtyOverrides.getOrDefault(lineId,
                    line.getQty() == null ? BigDecimal.ZERO : line.getQty());

            ScmSupplierPrice matchedPrice = matchSupplierPrice(supplierId, materialId, qty);
            BigDecimal unitPrice;
            Long priceId;
            if (matchedPrice == null) {
                warnings.add("供方 " + supplierName + " 物料 " + line.getMaterialCode() + " 未找到生效阶梯价，采用 estimatePrice 或 0");
                log.warn("[convertToPurchaseOrder] lineId={} supplierId={} materialId={} 未命中阶梯价", lineId, supplierId, materialId);
                unitPrice = line.getEstimatePrice() == null ? BigDecimal.ZERO : line.getEstimatePrice();
                priceId = null;
            } else {
                unitPrice = matchedPrice.getUnitPrice() == null ? BigDecimal.ZERO : matchedPrice.getUnitPrice();
                priceId = matchedPrice.getPriceId();
                log.info("[convertToPurchaseOrder] 命中阶梯价 lineId={} priceId={} unitPrice={}", lineId, priceId, unitPrice);
            }

            BigDecimal lineTaxRate = taxRateDefault;
            // TODO: requisitionLine 上 tax_rate 字段目前不存在，所以用 commonHeader 默认
            String lineCurrency = matchedPrice != null && matchedPrice.getCurrency() != null ? matchedPrice.getCurrency() : currencyDefault;

            // 交货日：行 requiredDate > default deliveryDateDefault
            LocalDate dd = line.getRequiredDate() != null ? line.getRequiredDate() : deliveryDateDefault;

            String bucketKey = supplierId + "_" + lineCurrency + "_" + dd.toString();
            ScmPurchaseOrder po = poBucket.get(bucketKey);
            if (po == null) {
                po = new ScmPurchaseOrder();
                po.setPoType("PURCHASE");
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
            line.setConvertedQty(ref.qty);
            line.setSupplierId(ref.supplierId);
            line.setSupplierCode(ref.supplierCode);
            line.setSupplierName(ref.supplierName);
            line.setSupplierPriceId(ref.priceId);
            BigDecimal originalQty = line.getQty() == null ? BigDecimal.ZERO : line.getQty();
            if (ref.qty.compareTo(originalQty) >= 0) {
                line.setStatus(3); // 已转完
            } else {
                line.setStatus(2); // 部分转
            }
            requisitionLineService.update(line);
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
                return Integer.valueOf(3).equals(s) || Integer.valueOf(7).equals(s);
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

    /** 取默认供方：优先 PdmMaterialSupplier (isDefault=1 & status=1)，否则 fallback ScmSupplierPrice.isDefault=1 */
    private Map<String, Object> findDefaultSupplier(Long materialId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT supplier_id, supplier_code, supplier_name FROM pdm_material_supplier " +
                            "WHERE material_id=? AND COALESCE(is_default,0)=1 AND status=1 ORDER BY priority LIMIT 1",
                    materialId);
            if (!rows.isEmpty()) {
                Map<String, Object> r = rows.get(0);
                Map<String, Object> res = new HashMap<>();
                res.put("supplierId", ((Number) r.get("supplier_id")).longValue());
                res.put("supplierCode", r.get("supplier_code") == null ? null : r.get("supplier_code").toString());
                res.put("supplierName", r.get("supplier_name") == null ? null : r.get("supplier_name").toString());
                return res;
            }
        } catch (Exception e) {
            log.debug("[findDefaultSupplier] PdmMaterialSupplier查询失败 matId={}: {}", materialId, e.getMessage());
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT supplier_id, supplier_code, supplier_name FROM scm_supplier_price " +
                            "WHERE material_id=? AND COALESCE(is_default,0)=1 AND status=2 " +
                            "ORDER BY valid_from DESC LIMIT 1",
                    materialId);
            if (!rows.isEmpty()) {
                Map<String, Object> r = rows.get(0);
                Map<String, Object> res = new HashMap<>();
                res.put("supplierId", ((Number) r.get("supplier_id")).longValue());
                res.put("supplierCode", r.get("supplier_code") == null ? null : r.get("supplier_code").toString());
                res.put("supplierName", r.get("supplier_name") == null ? null : r.get("supplier_name").toString());
                return res;
            }
        } catch (Exception e) {
            log.debug("[findDefaultSupplier] ScmSupplierPrice fallback失败 matId={}: {}", materialId, e.getMessage());
        }
        return null;
    }

    /** ScmSupplierPrice 阶梯价匹配：精确层优先，否则 fallback 最小 minQty 层 */
    private ScmSupplierPrice matchSupplierPrice(Long supplierId, Long materialId, BigDecimal qty) {
        List<ScmSupplierPrice> candidates;
        try {
            LocalDate today = LocalDate.now();
            candidates = jdbcTemplate.query(
                    "SELECT * FROM scm_supplier_price WHERE supplier_id=? AND material_id=? AND status=2 " +
                            " AND (valid_from IS NULL OR valid_from <= ?) " +
                            " AND (valid_to IS NULL OR valid_to >= ?) " +
                            "ORDER BY COALESCE(min_qty,0) ASC",
                    (rs, rn) -> {
                        ScmSupplierPrice p = new ScmSupplierPrice();
                        p.setPriceId(rs.getLong("price_id"));
                        p.setSupplierId(rs.getLong("supplier_id"));
                        p.setMaterialId(rs.getLong("material_id"));
                        p.setCurrency(rs.getString("currency"));
                        p.setUnitPrice((BigDecimal) rs.getObject("unit_price"));
                        p.setTaxRate((BigDecimal) rs.getObject("tax_rate"));
                        p.setMinQty((BigDecimal) rs.getObject("min_qty"));
                        try { p.setValidFrom(rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toLocalDate()); } catch (Exception ignore) {}
                        try { p.setValidTo(rs.getDate("valid_to") == null ? null : rs.getDate("valid_to").toLocalDate()); } catch (Exception ignore) {}
                        p.setStatus(rs.getInt("status"));
                        return p;
                    }, supplierId, materialId, today, today);
        } catch (Exception ex) {
            log.warn("[matchSupplierPrice] 查询阶梯价异常 supplierId={} materialId={}: {}", supplierId, materialId, ex.getMessage());
            return null;
        }
        if (candidates == null || candidates.isEmpty()) return null;

        ScmSupplierPrice exact = null;
        for (ScmSupplierPrice p : candidates) {
            BigDecimal minQty = p.getMinQty() == null ? BigDecimal.ZERO : p.getMinQty();
            if (minQty.compareTo(qty) <= 0) {
                exact = p; // ASC 扫描最后=最大minQty且<=qty
            }
        }
        if (exact != null) return exact;
        // fallback 最小 minQty 层（第1条 ASC）
        ScmSupplierPrice fb = candidates.get(0);
        log.info("[matchSupplierPrice] fallback 最小阶梯 supplierId={} materialId={} qty={} priceId={}",
                supplierId, materialId, qty, fb.getPriceId());
        return fb;
    }

    private Integer findMaterialQcRequired(Long materialId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT qc_required FROM pdm_material WHERE material_id=? LIMIT 1", materialId);
            if (!rows.isEmpty() && rows.get(0).get("qc_required") != null) {
                int v = ((Number) rows.get(0).get("qc_required")).intValue();
                return v == 1 || v == 2 ? v : 0;
            }
        } catch (Exception ignore) {}
        return 0;
    }
}
