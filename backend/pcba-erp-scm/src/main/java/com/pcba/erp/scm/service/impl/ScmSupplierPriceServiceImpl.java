package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;
import com.pcba.erp.scm.entity.ScmPurchaseOrderLine;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.entity.ScmSupplierPrice;
import com.pcba.erp.scm.mapper.ScmPurchaseOrderLineMapper;
import com.pcba.erp.scm.mapper.ScmPurchaseOrderMapper;
import com.pcba.erp.scm.mapper.ScmSupplierPriceMapper;
import com.pcba.erp.scm.service.IScmPurchaseOrderLineService;
import com.pcba.erp.scm.service.IScmPurchaseOrderService;
import com.pcba.erp.scm.service.IScmSupplierPriceService;
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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmSupplierPriceServiceImpl implements IScmSupplierPriceService {

    private final ScmSupplierPriceMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    @Lazy private final IScmPurchaseOrderService poService;
    private final ScmPurchaseOrderMapper poMapper;
    private final ScmPurchaseOrderLineMapper poLineMapper;
    @Lazy private final IScmPurchaseOrderLineService poLineService;

    @Override
    public IPage<ScmSupplierPrice> selectPage(int pageNum, int pageSize, Long supplierId, Long materialId,
                                              String materialCode, String materialName, String supplierName,
                                              String currency, Integer status) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), supplierId, materialId,
                materialCode, materialName, supplierName, currency, status);
    }

    @Override
    public IPage<ScmSupplierPrice> selectGroupedPage(int pageNum, int pageSize, Long supplierId, Long materialId,
                                                     String materialCode, String materialName, String supplierName,
                                                     String currency, Integer status) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        int total = mapper.countGroupedMaterials(supplierId, materialId, materialCode, materialName, supplierName, currency, status);
        Page<ScmSupplierPrice> page = new Page<>(pageNum, pageSize);
        page.setTotal(total);
        if (total == 0) { page.setRecords(new ArrayList<>()); return page; }
        List<Long> materialIds = mapper.selectMaterialIdsPage(offset, pageSize, supplierId, materialId,
                materialCode, materialName, supplierName, currency, status);
        if (materialIds == null || materialIds.isEmpty()) { page.setRecords(new ArrayList<>()); return page; }
        page.setRecords(mapper.selectByMaterialIds(materialIds));
        return page;
    }

    private void clearOtherDefaults(Long materialId, Long excludeId) {
        if (materialId == null) return;
        LambdaUpdateWrapper<ScmSupplierPrice> uw = new LambdaUpdateWrapper<>();
        uw.eq(ScmSupplierPrice::getMaterialId, materialId)
          .eq(ScmSupplierPrice::getIsDefault, 1)
          .set(ScmSupplierPrice::getIsDefault, 0);
        if (excludeId != null) uw.ne(ScmSupplierPrice::getPriceId, excludeId);
        mapper.update(null, uw);
    }

    /** 填充冗余列 (supplier_code/name, material_code/name/spec/unit) */
    private void fillRedundant(ScmSupplierPrice e) {
        if (e.getMaterialId() != null && (e.getMaterialCode() == null || e.getSupplierCode() == null)) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT m.material_code mc, m.material_name mn, m.specification ms, m.unit mu," +
                        " s.supplier_code sc, s.supplier_name sn " +
                        "FROM pdm_material m, scm_supplier s " +
                        "WHERE m.material_id=? AND s.supplier_id=?", e.getMaterialId(), e.getSupplierId());
                if (!rows.isEmpty()) {
                    Map<String, Object> r = rows.get(0);
                    if (e.getMaterialCode() == null)
                        e.setMaterialCode(r.get("mc") == null ? null : r.get("mc").toString());
                    if (e.getMaterialName() == null)
                        e.setMaterialName(r.get("mn") == null ? null : r.get("mn").toString());
                    if (e.getSpecification() == null)
                        e.setSpecification(r.get("ms") == null ? null : r.get("ms").toString());
                    if (e.getUnit() == null)
                        e.setUnit(r.get("mu") == null ? null : r.get("mu").toString());
                    if (e.getSupplierCode() == null)
                        e.setSupplierCode(r.get("sc") == null ? null : r.get("sc").toString());
                    if (e.getSupplierName() == null)
                        e.setSupplierName(r.get("sn") == null ? null : r.get("sn").toString());
                }
            } catch (Exception ignore) { /* 允许冗余失败 */ }
        }
    }

    @Override
    public ScmSupplierPrice getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmSupplierPrice r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmSupplierPrice entity) {
        try {
            Field noField = ScmSupplierPrice.class.getDeclaredField("priceId");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                Field idField = ScmSupplierPrice.class.getDeclaredField("priceId");
                idField.setAccessible(true);
                Object idVal = idField.get(entity);
                if (mapper.countDuplicate(noVal.toString(), (Long) idVal) > 0)
                    throw new ServiceException("编号已存在: " + noVal);
            }
        } catch (NoSuchFieldException ignore) {}
          catch (IllegalAccessException ex) { throw new ServiceException("保存失败: " + ex.getMessage()); }

        try {
            Field s = ScmSupplierPrice.class.getDeclaredField("status");
            s.setAccessible(true);
            if (s.get(entity) == null) s.set(entity, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        for (String f : new String[]{"unitPrice","taxRate","minQty"}) {
            try {
                Field ff = ScmSupplierPrice.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        if (entity.getIsDefault() != null && entity.getIsDefault() == 1)
            clearOtherDefaults(entity.getMaterialId(), null);
        else if (entity.getIsDefault() == null) entity.setIsDefault(0);

        fillRedundant(entity);
        mapper.insert(entity);
        try {
            Field idF = ScmSupplierPrice.class.getDeclaredField("priceId");
            idF.setAccessible(true);
            return (Long) idF.get(entity);
        } catch (Exception ex) { return null; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmSupplierPrice entity) {
        Long id = null;
        try {
            Field idF = ScmSupplierPrice.class.getDeclaredField("priceId");
            idF.setAccessible(true);
            id = (Long) idF.get(entity);
        } catch (Exception ignore) {}
        if (id == null) throw new ServiceException("ID不能为空");
        ScmSupplierPrice old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        try {
            Field noField = ScmSupplierPrice.class.getDeclaredField("priceId");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank())
                if (mapper.countDuplicate(noVal.toString(), id) > 0)
                    throw new ServiceException("编号已存在");
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        if (entity.getIsDefault() != null && entity.getIsDefault() == 1)
            clearOtherDefaults(entity.getMaterialId(), id);
        fillRedundant(entity);
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmSupplierPrice r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        ScmSupplierPrice r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        try {
            Field s = ScmSupplierPrice.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override public boolean submit(Long id)  { return changeStatus(id, 1); }
    @Override public boolean audit(Long id)   { return changeStatus(id, 2); }
    @Override public boolean disable(Long id) { return changeStatus(id, 3); }

    @Override
    public List<ScmSupplierPrice> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScmSupplierPrice>()
                .orderByDesc("price_id"));
    }

    /* ============================================================
     * P0: 供应商报价 → 采购订单 (镜像销售报价转销售订单)
     * ============================================================ */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToPurchaseOrder(Long supplierId,
                                       List<Map<String, Object>> items,
                                       Map<String, Object> extra) {
        if (supplierId == null) throw new ServiceException("供应商不能为空");
        if (items == null || items.isEmpty()) throw new ServiceException("报价条目不能为空");

        // 1. 供应商信息
        List<Map<String, Object>> cust = jdbcTemplate.queryForList(
                "SELECT supplier_id, supplier_code, supplier_name FROM scm_supplier WHERE supplier_id=?", supplierId);
        if (cust.isEmpty()) throw new ServiceException("供应商不存在");
        Map<String, Object> s = cust.get(0);
        String supCode = s.get("supplier_code") == null ? "" : s.get("supplier_code").toString();
        String supName = s.get("supplier_name") == null ? "" : s.get("supplier_name").toString();

        // 2. extra 取字段
        LocalDate poDate = extra != null && extra.get("poDate") != null
                ? LocalDate.parse(extra.get("poDate").toString()) : LocalDate.now();
        LocalDate defDelivery = extra != null && extra.get("deliveryDate") != null
                ? LocalDate.parse(extra.get("deliveryDate").toString()) : poDate.plusDays(14);
        Long warehouseId = extra != null && extra.get("warehouseId") != null
                ? Long.valueOf(extra.get("warehouseId").toString()) : null;
        Long buyerId = extra != null && extra.get("buyerId") != null
                ? Long.valueOf(extra.get("buyerId").toString()) : 1L;
        String paymentTerm = extra == null || extra.get("paymentTerm") == null ? "月结30天" : extra.get("paymentTerm").toString();
        String remark = extra == null || extra.get("remark") == null ? null : extra.get("remark").toString();
        String reqSource = extra == null || extra.get("reqSource") == null ? "由供应商报价转单生成" : extra.get("reqSource").toString();
        String currency = extra == null || extra.get("currency") == null ? "CNY" : extra.get("currency").toString();
        String poType = extra == null || extra.get("poType") == null ? "NORMAL" : extra.get("poType").toString();
        String bizCategory = extra == null || extra.get("bizCategory") == null ? "PURCHASE" : extra.get("bizCategory").toString();

        // 3. 校验 + 加载所有价格条目 (必须status=2 且 同供应商)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) { sb.append("?"); if (i < items.size() - 1) sb.append(","); }
        Long[] priceIds = items.stream()
                .map(m -> Long.valueOf(String.valueOf(m.get("priceId"))))
                .toArray(Long[]::new);
        List<ScmSupplierPrice> prices = jdbcTemplate.query(
                "SELECT * FROM scm_supplier_price WHERE price_id IN (" + sb + ")",
                (rs, idx) -> {
                    ScmSupplierPrice p = new ScmSupplierPrice();
                    p.setPriceId(rs.getLong("price_id"));
                    p.setSupplierId(rs.getLong("supplier_id"));
                    p.setMaterialId(rs.getLong("material_id"));
                    p.setCurrency(rs.getString("currency"));
                    p.setUnitPrice(rs.getBigDecimal("unit_price"));
                    p.setTaxRate(rs.getBigDecimal("tax_rate"));
                    p.setMinQty(rs.getBigDecimal("min_qty"));
                    java.sql.Date vf = rs.getDate("valid_from");
                    p.setValidFrom(vf == null ? null : vf.toLocalDate());
                    java.sql.Date vt = rs.getDate("valid_to");
                    p.setValidTo(vt == null ? null : vt.toLocalDate());
                    p.setStatus(rs.getInt("status"));
                    p.setSupplierCode(rs.getString("supplier_code"));
                    p.setSupplierName(rs.getString("supplier_name"));
                    p.setMaterialCode(rs.getString("material_code"));
                    p.setMaterialName(rs.getString("material_name"));
                    p.setSpecification(rs.getString("specification"));
                    p.setUnit(rs.getString("unit"));
                    return p;
                },
                (Object[]) priceIds);
        if (prices.size() != items.size()) throw new ServiceException("存在无效的报价ID");
        for (ScmSupplierPrice p : prices) {
            if (!p.getSupplierId().equals(supplierId))
                throw new ServiceException("报价不属于同一家供应商: " + p.getPriceId());
            if (p.getStatus() == null || p.getStatus() != 2)
                throw new ServiceException("报价非生效状态(status=2): " + p.getPriceId() + " 实际=" + p.getStatus());
            if (p.getValidTo() != null && poDate.isAfter(p.getValidTo()))
                throw new ServiceException("报价已过有效期: " + p.getPriceId() + " 有效期至" + p.getValidTo());
            if (p.getValidFrom() != null && poDate.isBefore(p.getValidFrom()))
                throw new ServiceException("报价未生效: " + p.getPriceId());
            if (p.getPoId() != null)
                throw new ServiceException("报价已被使用(已转单): " + p.getPriceId() + " poId=" + p.getPoId());
        }

        // 4. 构建 items qty map (priceId -> {qty, deliveryDate})
        Map<Long, Map<String, Object>> itemMap = new java.util.HashMap<>();
        for (Map<String, Object> it : items) {
            Long pid = Long.valueOf(String.valueOf(it.get("priceId")));
            itemMap.put(pid, it);
        }

        // 5. 给每行找同(物料+supplier)的请购行，回填 requisition_line_id (来源追踪)
        //    P0/P1阶段暂未强制要求关联请购；生产环境若启用请购单模块，需先在scm_purchase_requisition_line
        //    表新增 po_line_id + scm_purchase_requisition 表新增 supplier_id。
        Map<Long, Long> mid2ReqLine = new java.util.HashMap<>();
        /*
        List<Long> mids = prices.stream().map(ScmSupplierPrice::getMaterialId).toList();
        if (!mids.isEmpty()) {
            StringBuilder mq = new StringBuilder();
            for (int i = 0; i < mids.size(); i++) { mq.append("?"); if (i < mids.size()-1) mq.append(","); }
            List<ScmPurchaseRequisitionLine> rls = jdbcTemplate.query(
                    "SELECT r.line_id, r.material_id " +
                    "  FROM scm_purchase_requisition_line r " +
                    "  JOIN scm_purchase_requisition h ON h.requisition_id=r.requisition_id " +
                    " WHERE h.status IN (0,1,2) " +
                    "   AND r.material_id IN (" + mq + ")" +
                    " ORDER BY r.required_date ASC, r.line_id ASC",
                    (rs, i) -> {
                        ScmPurchaseRequisitionLine rl = new ScmPurchaseRequisitionLine();
                        rl.setLineId(rs.getLong(1));
                        rl.setMaterialId(rs.getLong(2));
                        return rl;
                    },
                    mids.toArray());
            for (ScmPurchaseRequisitionLine rl : rls) {
                mid2ReqLine.putIfAbsent(rl.getMaterialId(), rl.getLineId());
            }
        }
        */

        // 6. 构建采购订单主档
        ScmPurchaseOrder po = new ScmPurchaseOrder();
        po.setPoNo("PO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        po.setPoType(poType);
        po.setBizCategory(bizCategory);
        po.setPoDate(poDate);
        po.setDeliveryDate(defDelivery);
        po.setSupplierId(supplierId);
        po.setSupplierCode(supCode);
        po.setSupplierName(supName);
        po.setCurrency(currency);
        po.setBuyerId(buyerId);
        po.setWarehouseId(warehouseId);
        po.setPaymentTerm(paymentTerm);
        po.setTaxAmount(BigDecimal.ZERO);
        po.setTotalAmount(BigDecimal.ZERO);
        po.setRemark(remark);
        po.setStatus(0);
        Long poId = poService.save(po);
        if (poId == null) throw new ServiceException("创建采购订单失败");

        // 7. 行项
        int lineNo = 0;
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        List<Long> consumedPriceIds = new ArrayList<>();
        for (ScmSupplierPrice p : prices) {
            Map<String, Object> it = itemMap.get(p.getPriceId());
            BigDecimal qty = new BigDecimal(String.valueOf(it.getOrDefault("qty", "1")));
            if (qty.compareTo(BigDecimal.ZERO) <= 0)
                throw new ServiceException("数量非法 priceId=" + p.getPriceId());
            BigDecimal min = p.getMinQty() == null ? BigDecimal.ZERO : p.getMinQty();
            if (min.compareTo(BigDecimal.ZERO) > 0 && qty.compareTo(min) < 0)
                throw new ServiceException("数量低于最小起订(min=" + min + ") priceId=" + p.getPriceId());
            LocalDate d = it.get("deliveryDate") != null && !it.get("deliveryDate").toString().isBlank()
                    ? LocalDate.parse(it.get("deliveryDate").toString()) : defDelivery;
            lineNo++;

            BigDecimal unitPrice = p.getUnitPrice() == null ? BigDecimal.ZERO : p.getUnitPrice();
            BigDecimal taxRate   = p.getTaxRate()   == null ? BigDecimal.ZERO : p.getTaxRate();
            BigDecimal lineAmount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax    = lineAmount.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            ScmPurchaseOrderLine line = new ScmPurchaseOrderLine();
            line.setPoId(poId);
            line.setLineNo(lineNo);
            line.setMaterialId(p.getMaterialId());
            line.setMaterialCode(p.getMaterialCode());
            line.setMaterialName(p.getMaterialName());
            line.setSpecification(p.getSpecification());
            line.setUnit(p.getUnit());
            line.setOrderQty(qty);
            line.setReceivedQty(BigDecimal.ZERO);
            line.setReturnedQty(BigDecimal.ZERO);
            line.setRequiredDate(d);
            line.setWarehouseId(warehouseId);
            line.setUnitPrice(unitPrice);
            line.setTaxRate(taxRate);
            line.setTaxAmount(lineTax);
            line.setLineAmount(lineAmount);
            line.setRequisitionLineId(mid2ReqLine.get(p.getMaterialId()));
            line.setRemark("来自供应商报价 priceId=" + p.getPriceId());
            poLineService.save(line);
            Long lineId;
            try {
                Field idf = ScmPurchaseOrderLine.class.getDeclaredField("lineId");
                idf.setAccessible(true); lineId = (Long) idf.get(line);
            } catch (Exception e) { lineId = null; }

            if (lineId != null) {
                final Long flid = lineId;
                jdbcTemplate.update("UPDATE scm_supplier_price SET po_id=?, po_line_id=?, status=5 WHERE price_id=?",
                        poId, flid, p.getPriceId());
                consumedPriceIds.add(p.getPriceId());
            }
            if (line.getRequisitionLineId() != null) {
                jdbcTemplate.update("UPDATE scm_purchase_requisition_line SET po_id=?, po_line_id=? WHERE line_id=?",
                        poId, lineId, line.getRequisitionLineId());
            }

            subTotal = subTotal.add(lineAmount);
            taxTotal = taxTotal.add(lineTax);
        }

        // 8. 更新采购订单主档 (taxAmount + totalAmount = sub + tax)
        BigDecimal totalAmount = subTotal.add(taxTotal);
        po.setPoId(poId);
        po.setTaxAmount(taxTotal);
        po.setTotalAmount(totalAmount);
        poMapper.updateById(po);

        log.info("[convertToPurchaseOrder] supplierId={} poId={} items={} priceIds={}",
                supplierId, poId, prices.size(), consumedPriceIds);
        return poId;
    }

    private Object[] toArrayPrepend(List<Long> rest, Long first) {
        Object[] r = new Object[rest.size() + 1];
        r[0] = first;
        for (int i = 0; i < rest.size(); i++) r[i+1] = rest.get(i);
        return r;
    }
}
