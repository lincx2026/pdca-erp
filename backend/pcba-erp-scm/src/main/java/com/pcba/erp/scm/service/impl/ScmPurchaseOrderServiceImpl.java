package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;
import com.pcba.erp.scm.entity.ScmPurchaseOrderLine;
import com.pcba.erp.scm.mapper.ScmPurchaseOrderLineMapper;
import com.pcba.erp.scm.mapper.ScmPurchaseOrderMapper;
import com.pcba.erp.scm.service.IScmApPayableService;
import com.pcba.erp.scm.service.IScmPurchaseOrderService;
import com.pcba.erp.wms.entity.WmsStockIn;
import com.pcba.erp.wms.entity.WmsStockInLine;
import com.pcba.erp.wms.service.IWmsStockInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
public class ScmPurchaseOrderServiceImpl implements IScmPurchaseOrderService {

    private final ScmPurchaseOrderMapper mapper;
    private final ScmPurchaseOrderLineMapper lineMapper;
    @Lazy private final IScmApPayableService payableService;
    @Lazy private final IWmsStockInService stockInService;

    @Override
    public IPage<ScmPurchaseOrder> selectPage(int pageNum, int pageSize, String poNo, String poType, String bizCategory, Long supplierId, String supplierName, Long buyerId, Integer status, String deliveryDate) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), poNo, poType, bizCategory, supplierId, supplierName, buyerId, status, deliveryDate);
    }

    @Override
    public ScmPurchaseOrder getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseOrder r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmPurchaseOrder entity) {
        try {
            Field f = ScmPurchaseOrder.class.getDeclaredField("poNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) {
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                f.set(entity, "PO" + ts);
            }
        } catch (Exception ignore) {}
        try {
            Field f = ScmPurchaseOrder.class.getDeclaredField("poDate");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        try {
            Field f = ScmPurchaseOrder.class.getDeclaredField("currency");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) f.set(entity, "CNY");
        } catch (Exception ignore) {}
        try {
            Field noField = ScmPurchaseOrder.class.getDeclaredField("poNo");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                Field idField = ScmPurchaseOrder.class.getDeclaredField("poId");
                idField.setAccessible(true);
                Object idVal = idField.get(entity);
                if (mapper.countDuplicate(noVal.toString(), (Long) idVal) > 0)
                    throw new ServiceException("编号已存在: " + noVal);
            }
        } catch (NoSuchFieldException ignore) {}
          catch (IllegalAccessException ex) { throw new ServiceException("保存失败: " + ex.getMessage()); }

        try {
            Field s = ScmPurchaseOrder.class.getDeclaredField("status");
            s.setAccessible(true);
            if (s.get(entity) == null) s.set(entity, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        for (String f : new String[]{"totalAmount","taxAmount","paidAmount","totalQty","refundAmount","qty","estimatePrice","estimateAmount","orderQty","receivedQty","returnedQty","unitPrice","lineAmount","taxRate","minQty","discountRate","subTotal","totalTax","prepayAmount"}) {
            try {
                Field ff = ScmPurchaseOrder.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        mapper.insert(entity);
        try {
            Field idF = ScmPurchaseOrder.class.getDeclaredField("poId");
            idF.setAccessible(true);
            return (Long) idF.get(entity);
        } catch (Exception ex) { return null; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmPurchaseOrder entity) {
        Long id = null;
        try {
            Field idF = ScmPurchaseOrder.class.getDeclaredField("poId");
            idF.setAccessible(true);
            id = (Long) idF.get(entity);
        } catch (Exception ignore) {}
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseOrder old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        try {
            Field noField = ScmPurchaseOrder.class.getDeclaredField("poNo");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank())
                if (mapper.countDuplicate(noVal.toString(), id) > 0)
                    throw new ServiceException("编号已存在");
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmPurchaseOrder r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        ScmPurchaseOrder r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        if (status != null && status == 2) {
            try {
                Field supF = ScmPurchaseOrder.class.getDeclaredField("supplierId"); supF.setAccessible(true);
                Object supId = supF.get(r);
                if (supId != null) {
                    Field cnyF = ScmPurchaseOrder.class.getDeclaredField("totalAmount"); cnyF.setAccessible(true);
                    BigDecimal cny = (BigDecimal) cnyF.get(r);
                    payableService.checkCreditLimit((Long) supId, cny == null ? BigDecimal.ZERO : cny);
                }
            } catch (ServiceException se) { throw se; }
              catch (Exception ignore) {}
        }
        try {
            Field s = ScmPurchaseOrder.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override
    public List<ScmPurchaseOrder> options() {
        return mapper.selectList(new QueryWrapper<ScmPurchaseOrder>().orderByDesc("po_id"));
    }

    /* ========================================================================
     * P1: 生成采购入库单 (多次/分批, 量 = min(入参qty, 剩余lastQty))
     * 走WMS状态机 submit -> audit -> confirmStockIn: 加库存 + 反写PO.receivedQty
     * ====================================================================== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateStockIn(Long poId, Map<String, Object> body) {
        if (poId == null) throw new ServiceException("采购订单ID不能为空");
        ScmPurchaseOrder po = mapper.selectById(poId);
        if (po == null) throw new ServiceException("采购订单不存在");
        // status 允许: 2已审核/3已下达/4部分到货(继续入)，5/6/9 禁止
        int st = po.getStatus() == null ? -1 : po.getStatus();
        if (st != 2 && st != 3 && st != 4)
            throw new ServiceException("采购订单状态=" + st + " 不允许生成入库单（要求：已审核=2 / 已下达=3 / 部分到货=4）");

        // 1. 取 PO 所有行明细 (通过 Mapper BaseMapper selectList)
        List<ScmPurchaseOrderLine> allLines = lineMapper.selectList(
                new QueryWrapper<ScmPurchaseOrderLine>().eq("po_id", poId).orderByAsc("line_id"));
        if (allLines.isEmpty()) throw new ServiceException("采购订单没有明细行");

        // 2. 按 lineId 建索引
        Map<Long, ScmPurchaseOrderLine> lineMap = new java.util.HashMap<>(allLines.size());
        for (ScmPurchaseOrderLine l : allLines) lineMap.put(l.getLineId(), l);

        // 3. 从 body 解析 lines / warehouseId / stockInNo / stockInDate / remark
        if (body == null || body.isEmpty()) throw new ServiceException("请求体不能为空");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null || lines.isEmpty()) throw new ServiceException("入库明细不能为空");

        Long whId = null;
        Object rawWh = body.get("warehouseId");
        if (rawWh != null) {
            try { whId = Long.valueOf(rawWh.toString()); } catch (Exception ignore) {}
        }
        if (whId == null) whId = po.getWarehouseId();
        if (whId == null) throw new ServiceException("入库仓库不能为空(warehouseId)");
        String stockInNo = body.get("stockInNo") == null ? null : body.get("stockInNo").toString();
        String remark   = body.get("remark") == null ? null : body.get("remark").toString();
        LocalDate stockInDate = null;
        Object rawDt = body.get("stockInDate");
        if (rawDt != null && !rawDt.toString().isBlank()) {
            try { stockInDate = LocalDate.parse(rawDt.toString()); } catch (Exception ignore) {}
        }
        String defBatch = "PO" + poId + "-" + (stockInDate != null ? stockInDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                                                      : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        String auditBy;
        try { auditBy = SecurityUtils.getUsername(); } catch (Exception ignore) { auditBy = "system"; }
        if (auditBy == null || auditBy.isBlank()) auditBy = "system";

        WmsStockIn stockIn = new WmsStockIn();
        stockIn.setWarehouseId(whId);
        stockIn.setInType("PURCHASE");
        stockIn.setSupplierId(po.getSupplierId());
        stockIn.setSupplierName(po.getSupplierName());
        stockIn.setStatus(0);
        String poNo = po.getPoNo();
        // 入参自定义单号优先（便于演示区分批次），否则Service自动生成
        if (stockInNo != null && !stockInNo.isBlank()) stockIn.setOrderNo(stockInNo);
        if (remark != null && !remark.isBlank()) stockIn.setRemark(remark);
        else stockIn.setRemark("由采购订单生成 poId=" + poId + (poNo != null && !poNo.isBlank() ? (" " + poNo) : ""));
        // 自定义入库日期(演示区分批次)，否则默认当天
        if (stockInDate != null) {
            stockIn.setCreateTime(stockInDate.atTime(9, 0, 0));
        }

        List<WmsStockInLine> inLines = new ArrayList<>(lines.size());
        int seq = 0;
        for (Map<String, Object> row : lines) {
            Long lineId = row.get("lineId") == null ? null : Long.valueOf(row.get("lineId").toString());
            BigDecimal qty = row.get("qty") == null ? BigDecimal.ZERO : new BigDecimal(row.get("qty").toString());
            String batchNo = row.get("batchNo") == null ? defBatch : row.get("batchNo").toString();
            BigDecimal unitPrice = null;
            if (row.get("unitPrice") != null && !row.get("unitPrice").toString().isBlank())
                unitPrice = new BigDecimal(row.get("unitPrice").toString());

            if (lineId == null) throw new ServiceException("入库明细缺少 lineId (采购订单行ID)");
            ScmPurchaseOrderLine pol = lineMap.get(lineId);
            if (pol == null) throw new ServiceException("无效的订单行 lineId=" + lineId);

            BigDecimal order = pol.getOrderQty() == null ? BigDecimal.ZERO : pol.getOrderQty();
            BigDecimal recv  = pol.getReceivedQty() == null ? BigDecimal.ZERO : pol.getReceivedQty();
            BigDecimal ret   = pol.getReturnedQty() == null ? BigDecimal.ZERO : pol.getReturnedQty();
            BigDecimal lastQty = order.subtract(recv).subtract(ret);
            if (lastQty.compareTo(BigDecimal.ZERO) < 0) lastQty = BigDecimal.ZERO;

            // 核心分配: actual = min(qty, lastQty)  (Exp1380344: 分配阶段截断, 不在后置校验报超)
            BigDecimal actual = qty.compareTo(lastQty) < 0 ? qty : lastQty;
            if (actual.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("[generateStockIn] 跳过lineId={} lastQty=0 qty={}", lineId, qty);
                continue;
            }

            BigDecimal up = unitPrice != null ? unitPrice
                    : (pol.getUnitPrice() == null ? BigDecimal.ZERO : pol.getUnitPrice());
            BigDecimal amt = actual.multiply(up).setScale(2, RoundingMode.HALF_UP);

            seq++;
            WmsStockInLine inl = new WmsStockInLine();
            inl.setMaterialId(pol.getMaterialId());
            inl.setMaterialCode(pol.getMaterialCode());
            inl.setMaterialName(pol.getMaterialName());
            inl.setSpecification(pol.getSpecification());
            inl.setBatchNo(batchNo);
            inl.setQuantity(actual);
            inl.setUnit(pol.getUnit());
            inl.setUnitPrice(up);
            inl.setAmount(amt);
            inl.setRemark("PO行 lineId=" + lineId + " 入参qty=" + qty + " 允许剩余=" + lastQty + " 实际截=" + actual);
            inl.setPoId(poId);
            inl.setPoLineId(lineId);
            inLines.add(inl);
        }

        if (inLines.isEmpty()) throw new ServiceException("所有明细剩余可入库量为0，取消生成");

        stockIn.setLines(inLines);
        // 4. 保存 + 推进状态机
        Long stockInId = stockInService.saveStockIn(stockIn);
        try {
            stockInService.submitAudit(stockInId);
            stockInService.auditPass(stockInId, auditBy);
            stockInService.confirmStockIn(stockInId);
        } catch (Exception ex) {
            log.error("[generateStockIn] poId={} stockInId={} 状态推进失败", poId, stockInId, ex);
            throw new ServiceException("生成入库单后确认失败: " + ex.getMessage());
        }

        // 5. 推进 PO 主单 status (若 applyPoReceivedDelta 已推状态，这里可做幂等保护)
        ScmPurchaseOrder latest = mapper.selectById(poId);
        if (latest != null) {
            int curSt = latest.getStatus() == null ? 0 : latest.getStatus();
            if (curSt == 2 || curSt == 3) {
                boolean allDone = true, anyDone = false;
                List<ScmPurchaseOrderLine> latestLines = lineMapper.selectList(
                        new QueryWrapper<ScmPurchaseOrderLine>().eq("po_id", poId));
                for (ScmPurchaseOrderLine l : latestLines) {
                    BigDecimal o = l.getOrderQty() == null ? BigDecimal.ZERO : l.getOrderQty();
                    BigDecimal rq = l.getReceivedQty() == null ? BigDecimal.ZERO : l.getReceivedQty();
                    BigDecimal rt = l.getReturnedQty() == null ? BigDecimal.ZERO : l.getReturnedQty();
                    BigDecimal remaining = o.subtract(rq).subtract(rt);
                    if (rq.compareTo(BigDecimal.ZERO) > 0) anyDone = true;
                    if (remaining.compareTo(BigDecimal.ZERO) > 0) allDone = false;
                }
                int target = curSt;
                if (allDone) target = 5;
                else if (anyDone) target = 4;
                if (target != curSt) {
                    changeStatus(poId, target);
                }
            }
        }

        log.info("[generateStockIn] poId={} stockInId={} inLines={}", poId, stockInId, inLines.size());
        return stockInId;
    }
}
