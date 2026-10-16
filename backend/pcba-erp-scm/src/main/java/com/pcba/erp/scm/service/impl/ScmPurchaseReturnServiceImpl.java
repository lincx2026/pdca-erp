package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.event.StockOutConfirmedEvent;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.utils.SpringUtils;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;
import com.pcba.erp.scm.entity.ScmPurchaseOrderLine;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.entity.ScmPurchaseReturn;
import com.pcba.erp.scm.entity.ScmPurchaseReturnLine;
import com.pcba.erp.scm.mapper.ScmPurchaseOrderLineMapper;
import com.pcba.erp.scm.mapper.ScmPurchaseOrderMapper;
import com.pcba.erp.scm.mapper.ScmPurchaseReturnLineMapper;
import com.pcba.erp.scm.mapper.ScmPurchaseReturnMapper;
import com.pcba.erp.scm.service.IScmPurchaseOrderLineService;
import com.pcba.erp.scm.service.IScmPurchaseOrderService;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionLineService;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionService;
import com.pcba.erp.scm.service.IScmPurchaseReturnService;
import com.pcba.erp.wms.entity.WmsStockIn;
import com.pcba.erp.wms.entity.WmsStockInLine;
import com.pcba.erp.wms.entity.WmsStockOut;
import com.pcba.erp.wms.entity.WmsStockOutLine;
import com.pcba.erp.wms.mapper.WmsStockInLineMapper;
import com.pcba.erp.wms.mapper.WmsStockInMapper;
import com.pcba.erp.wms.service.IWmsStockOutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmPurchaseReturnServiceImpl implements IScmPurchaseReturnService {

    private final ScmPurchaseReturnMapper mapper;
    private final ScmPurchaseReturnLineMapper returnLineMapper;
    private final IWmsStockOutService stockOutService;
    private final WmsStockInLineMapper stockInLineMapper;
    private final WmsStockInMapper stockInMapper;
    private final ScmPurchaseOrderLineMapper poLineMapper;
    private final ScmPurchaseOrderMapper poMapper;
    private final IScmPurchaseRequisitionService requisitionService;
    private final IScmPurchaseRequisitionLineService requisitionLineService;
    private final JdbcTemplate jdbcTemplate;

    @Lazy
    @Autowired(required = false)
    private IScmPurchaseOrderLineService purchaseOrderLineService;

    @Override
    public IPage<ScmPurchaseReturn> selectPage(int pageNum, int pageSize, String returnNo, Long poId, Long supplierId, String supplierName, Long warehouseId, Integer status, String returnDate) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), returnNo, poId, supplierId, supplierName, warehouseId, status, returnDate);
    }

    @Override
    public ScmPurchaseReturn getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseReturn r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmPurchaseReturn entity) {
        // 单号兜底
        try {
            Field f = ScmPurchaseReturn.class.getDeclaredField("returnNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) {
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                f.set(entity, "RT" + ts);
            }
        } catch (Exception ignore) {}
        // 退货日期
        try {
            Field f = ScmPurchaseReturn.class.getDeclaredField("returnDate");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        // 仓库默认 1
        try {
            Field f = ScmPurchaseReturn.class.getDeclaredField("warehouseId");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 1L);
        } catch (Exception ignore) {}
        // 单号查重 (若实体有"单号"字段且非空)
        try {
            Field noField = ScmPurchaseReturn.class.getDeclaredField("returnNo");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                Field idField = ScmPurchaseReturn.class.getDeclaredField("returnId");
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
            Field s = ScmPurchaseReturn.class.getDeclaredField("status");
            s.setAccessible(true);
            if (s.get(entity) == null) s.set(entity, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        for (String f : new String[]{"totalAmount","taxAmount","paidAmount","totalQty","refundAmount","qty","estimatePrice","estimateAmount","orderQty","receivedQty","returnedQty","unitPrice","lineAmount","taxRate","minQty","discountRate"}) {
            try {
                Field ff = ScmPurchaseReturn.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        mapper.insert(entity);
        try {
            Field idF = ScmPurchaseReturn.class.getDeclaredField("returnId");
            idF.setAccessible(true);
            return (Long) idF.get(entity);
        } catch (Exception ex) { return null; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmPurchaseReturn entity) {
        Long id = null;
        try {
            Field idF = ScmPurchaseReturn.class.getDeclaredField("returnId");
            idF.setAccessible(true);
            id = (Long) idF.get(entity);
        } catch (Exception ignore) {}
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseReturn old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        // 单号查重
        try {
            Field noField = ScmPurchaseReturn.class.getDeclaredField("returnNo");
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
        ScmPurchaseReturn r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        ScmPurchaseReturn r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        try {
            Field s = ScmPurchaseReturn.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override
    public List<ScmPurchaseReturn> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScmPurchaseReturn>()
                .orderByDesc("return_id"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateStockOut(Long returnId) {
        if (returnId == null) throw new ServiceException("退货单ID不能为空");
        ScmPurchaseReturn ret = mapper.selectById(returnId);
        if (ret == null) throw new ServiceException("退货单不存在");
        if (ret.getStatus() == null || ret.getStatus() < 2) {
            throw new ServiceException("退货单需审核通过后才能生成出库单");
        }
        if (ret.getStockOutId() != null) {
            throw new ServiceException("退货单已生成出库单，请勿重复生成");
        }
        List<ScmPurchaseReturnLine> lines = returnLineMapper.selectList(new LambdaQueryWrapper<ScmPurchaseReturnLine>()
                .eq(ScmPurchaseReturnLine::getReturnId, returnId));
        if (lines == null || lines.isEmpty()) throw new ServiceException("退货单明细为空，无法生成出库单");

        // 构造 WMS 采购出库单（outType=RETURN，便于退货监听识别）
        WmsStockOut so = new WmsStockOut();
        so.setWarehouseId(ret.getWarehouseId() == null ? 1L : ret.getWarehouseId());
        so.setCustomerId(ret.getSupplierId());
        so.setCustomerName(ret.getSupplierName());
        so.setOutType("RETURN");
        so.setOrderNo(ret.getReturnNo());
        so.setRemark("采购退货生成 来源:" + (ret.getReturnNo() == null ? String.valueOf(returnId) : ret.getReturnNo()));
        List<WmsStockOutLine> sol = new ArrayList<>(lines.size());
        for (ScmPurchaseReturnLine l : lines) {
            WmsStockOutLine s = new WmsStockOutLine();
            s.setMaterialId(l.getMaterialId());
            s.setMaterialCode(l.getMaterialCode());
            s.setMaterialName(l.getMaterialName());
            s.setSpecification(l.getSpecification());
            s.setBatchNo(l.getBatchNo());
            s.setUnit(l.getUnit());
            s.setQuantity(l.getReturnQty() == null ? BigDecimal.ZERO : l.getReturnQty());
            s.setRemark(l.getRemark());
            /* 链路字段 —— 用于 WMS 确认出库后精准反写 PO 行 returnedQty */
            s.setReturnId(returnId);
            s.setReturnLineId(l.getLineId());
            s.setDeliveryId(ret.getDeliveryId());
            s.setDeliveryLineId(l.getDeliveryLineId());
            s.setPoId(ret.getPoId());
            // 优先从退货行.poLineId 精准（新增）；否则 deliveryLineId 反查；否则留空由WMS贪心按poId+materialId分配
            Long poLineId = l.getPoLineId();
            if (poLineId == null && l.getDeliveryLineId() != null) {
                poLineId = resolvePoLineIdByDeliveryLine(l.getDeliveryLineId());
            }
            s.setPoLineId(poLineId);
            sol.add(s);
        }
        so.setLines(sol);
        Long stockOutId = stockOutService.saveStockOut(so);
        if (stockOutId == null) throw new ServiceException("WMS 生成出库单失败");
        // 回填 SCM 退货单
        ret.setStockOutId(stockOutId);
        ret.setStatus(3); // 出库中
        mapper.updateById(ret);
        return stockOutId;
    }

    /** 通过 scm_delivery_note_line 反查 po_line_id（仅当退货行关联了到货明细时可精准定位） */
    private Long resolvePoLineIdByDeliveryLine(Long deliveryLineId) {
        if (deliveryLineId == null) return null;
        try {
            return mapper.resolvePoLineIdByDeliveryLine(deliveryLineId);
        } catch (Exception e) {
            log.warn("[resolvePoLineIdByDeliveryLine] 查询失败 deliveryLineId={} msg={}", deliveryLineId, e.getMessage());
            return null;
        }
    }

    /* ============================================================
     * T4-2 saveReturn：保存退货单(头+行) 校验剩余可退量，并回填单价/物料基础字段
     * ========================================================== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveReturn(ScmPurchaseReturn header, List<ScmPurchaseReturnLine> lines) {
        if (header == null) throw new ServiceException("退货单头不能为空");
        if (lines == null || lines.isEmpty()) throw new ServiceException("退货单明细不能为空");

        // 1. 先保存头（内部会生单默认单号/日期，状态会被兜底为0草稿）
        Long currentReturnId = header.getReturnId();
        Long returnId;
        if (currentReturnId == null) {
            returnId = save(header);
            if (returnId == null) throw new ServiceException("保存退货单头失败");
        } else {
            // 更新模式：允许对现有草稿重新saveReturn做全量覆盖；重校验
            ScmPurchaseReturn old = mapper.selectById(currentReturnId);
            if (old == null) throw new ServiceException("退货单不存在 returnId=" + currentReturnId);
            Integer st = old.getStatus() == null ? 0 : old.getStatus();
            if (st != 0) throw new ServiceException("退货单状态=" + st + " 非草稿(0)，不允许修改");
            update(header);
            returnId = currentReturnId;
            // 先删旧行
            returnLineMapper.delete(new LambdaQueryWrapper<ScmPurchaseReturnLine>()
                    .eq(ScmPurchaseReturnLine::getReturnId, returnId));
        }

        // 2. 准备辅助索引：stockInLineId -> WmsStockInLine；poLineId -> ScmPurchaseOrderLine
        Set<Long> silIds = lines.stream().map(ScmPurchaseReturnLine::getStockInLineId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, WmsStockInLine> stockInLineMap = new HashMap<>();
        if (!silIds.isEmpty()) {
            List<WmsStockInLine> sils = stockInLineMapper.selectBatchIds(silIds);
            if (sils != null) for (WmsStockInLine s : sils) stockInLineMap.put(s.getLineId(), s);
        }
        Set<Long> poLineIds = lines.stream().map(ScmPurchaseReturnLine::getPoLineId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ScmPurchaseOrderLine> poLineMap = new HashMap<>();
        if (!poLineIds.isEmpty()) {
            List<ScmPurchaseOrderLine> pols = poLineMapper.selectBatchIds(poLineIds);
            if (pols != null) for (ScmPurchaseOrderLine p : pols) poLineMap.put(p.getLineId(), p);
        }

        // 3. 逐行校验 + 赋值
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        int lineNo = 0;
        for (ScmPurchaseReturnLine rl : lines) {
            lineNo += 10;
            rl.setLineId(null);
            rl.setReturnId(returnId);
            if (rl.getLineNo() == null || rl.getLineNo() <= 0) rl.setLineNo(lineNo);

            BigDecimal returnQty = rl.getReturnQty() == null ? BigDecimal.ZERO : rl.getReturnQty();
            if (returnQty.signum() <= 0) {
                log.error("[saveReturn] returnId={} 第{}行 return_qty<=0 qty={}", returnId, lineNo, returnQty);
                throw new ServiceException("第" + lineNo/10 + "行退货数量必须大于0");
            }

            // 3a. 基于 stock_in_line 的剩余可退校验
            Long silId = rl.getStockInLineId();
            WmsStockInLine sil = silId == null ? null : stockInLineMap.get(silId);
            if (sil != null) {
                BigDecimal originalQty = sil.getQuantity() == null ? BigDecimal.ZERO : sil.getQuantity();
                BigDecimal alreadyReturned = sumAlreadyReturnedForStockInLine(silId, returnId);
                BigDecimal remaining = originalQty.subtract(alreadyReturned);
                if (remaining.signum() < 0) remaining = BigDecimal.ZERO;
                if (returnQty.compareTo(remaining) > 0) {
                    String msg = String.format("退货数量超过该行剩余可退量 remaining=%s 实际=%s (入库单行silId=%s 原量=%s 已退=%s)",
                            remaining, returnQty, silId, originalQty, alreadyReturned);
                    log.error("[saveReturn] {} returnId={}", msg, returnId);
                    throw new ServiceException(msg);
                }
                // 回填 stock_in_id / 批次 / unitPrice 等
                if (rl.getStockInId() == null) rl.setStockInId(sil.getStockInId());
                if (isBlank(rl.getBatchNo()) && !isBlank(sil.getBatchNo())) rl.setBatchNo(sil.getBatchNo());
                if (isBlank(rl.getMaterialCode()) && !isBlank(sil.getMaterialCode())) rl.setMaterialCode(sil.getMaterialCode());
                if (isBlank(rl.getMaterialName()) && !isBlank(sil.getMaterialName())) rl.setMaterialName(sil.getMaterialName());
                if (isBlank(rl.getSpecification()) && !isBlank(sil.getSpecification())) rl.setSpecification(sil.getSpecification());
                if (isBlank(rl.getUnit()) && !isBlank(sil.getUnit())) rl.setUnit(sil.getUnit());
                if (rl.getMaterialId() == null) rl.setMaterialId(sil.getMaterialId());
                // unitPrice 优先入库行.unit_price
                if (rl.getUnitPrice() == null && sil.getUnitPrice() != null) rl.setUnitPrice(sil.getUnitPrice());
                // 若 return 行.poLineId 为空，从入库行带入
                if (rl.getPoLineId() == null && sil.getPoLineId() != null) rl.setPoLineId(sil.getPoLineId());
                if (rl.getDeliveryLineId() == null && sil.getDeliveryLineId() != null) rl.setDeliveryLineId(sil.getDeliveryLineId());
                // 回填 header.poId (若未设置且入库行带)
                if (header.getPoId() == null && sil.getPoId() != null) header.setPoId(sil.getPoId());
                if (header.getWarehouseId() == null) {
                    // 按 stock_in_id 查 wms_stock_in.warehouseId
                    try {
                        WmsStockIn si = stockInMapper.selectById(sil.getStockInId());
                        if (si != null && si.getWarehouseId() != null) header.setWarehouseId(si.getWarehouseId());
                    } catch (Exception ignore) {}
                }
            } else if (rl.getPoLineId() != null) {
                // 无入库行来源，基于PO行做物料回填与单位价
                ScmPurchaseOrderLine pol = poLineMap.get(rl.getPoLineId());
                if (pol != null) {
                    if (rl.getUnitPrice() == null && pol.getUnitPrice() != null) rl.setUnitPrice(pol.getUnitPrice());
                    if (isBlank(rl.getMaterialCode()) && !isBlank(pol.getMaterialCode())) rl.setMaterialCode(pol.getMaterialCode());
                    if (isBlank(rl.getMaterialName()) && !isBlank(pol.getMaterialName())) rl.setMaterialName(pol.getMaterialName());
                    if (isBlank(rl.getSpecification()) && !isBlank(pol.getSpecification())) rl.setSpecification(pol.getSpecification());
                    if (isBlank(rl.getUnit()) && !isBlank(pol.getUnit())) rl.setUnit(pol.getUnit());
                    if (rl.getMaterialId() == null) rl.setMaterialId(pol.getMaterialId());
                    if (header.getPoId() == null) header.setPoId(pol.getPoId());
                }
            }

            if (rl.getMaterialId() == null) {
                throw new ServiceException("第" + (lineNo/10) + "行缺少物料ID(materialId)");
            }
            if (rl.getUnitPrice() == null) rl.setUnitPrice(BigDecimal.ZERO);
            BigDecimal lineAmount = returnQty.multiply(rl.getUnitPrice());
            rl.setReturnAmount(lineAmount);

            totalQty = totalQty.add(returnQty);
            totalAmount = totalAmount.add(lineAmount);
        }

        // 4. 批量插入行
        for (ScmPurchaseReturnLine rl : lines) returnLineMapper.insert(rl);

        // 5. 汇总头 totalQty/totalAmount 并 update
        ScmPurchaseReturn h = mapper.selectById(returnId);
        if (h != null) {
            h.setTotalQty(totalQty);
            h.setTotalAmount(totalAmount);
            if (header.getPoId() != null) h.setPoId(header.getPoId());
            if (header.getWarehouseId() != null) h.setWarehouseId(header.getWarehouseId());
            mapper.updateById(h);
        }

        log.info("[saveReturn] 保存成功 returnId={} totalQty={} totalAmount={} lines={}",
                returnId, totalQty, totalAmount, lines.size());
        return returnId;
    }

    /** 统计某入库单行除当前退货单外的已退数量 */
    private BigDecimal sumAlreadyReturnedForStockInLine(Long stockInLineId, Long excludeReturnId) {
        if (stockInLineId == null) return BigDecimal.ZERO;
        try {
            String sql = "SELECT COALESCE(SUM(r.return_qty),0) FROM scm_purchase_return_line r " +
                    "WHERE r.stock_in_line_id=? " +
                    "AND (? IS NULL OR r.return_id <> ?)";
            BigDecimal v = jdbcTemplate.queryForObject(sql, BigDecimal.class, stockInLineId, excludeReturnId, excludeReturnId);
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            log.warn("[sumAlreadyReturnedForStockInLine] 查询失败 stockInLineId={} msg={}", stockInLineId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /* ============================================================
     * T4-3 auditPass：审核 + 自动生成WMS出库+确认 + 事件+PO回写+可选补请购
     * ========================================================== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> auditPass(Long returnId) {
        if (returnId == null) throw new ServiceException("退货单ID不能为空");
        ScmPurchaseReturn ret = mapper.selectById(returnId);
        if (ret == null) throw new ServiceException("退货单不存在 returnId=" + returnId);
        Integer oldStatus = ret.getStatus() == null ? 0 : ret.getStatus();
        // 1. 状态校验：0或1→2；若已>=2 抛重复审核
        if (oldStatus >= 2) {
            throw new ServiceException("重复审核：退货单状态=" + oldStatus + " 已审核");
        }
        // 取行
        List<ScmPurchaseReturnLine> lines = returnLineMapper.selectList(new LambdaQueryWrapper<ScmPurchaseReturnLine>()
                .eq(ScmPurchaseReturnLine::getReturnId, returnId));
        if (lines == null || lines.isEmpty()) throw new ServiceException("退货单明细为空");
        // 合计退货量必须>0
        BigDecimal totalQty = BigDecimal.ZERO;
        for (ScmPurchaseReturnLine l : lines) {
            totalQty = totalQty.add(l.getReturnQty() == null ? BigDecimal.ZERO : l.getReturnQty());
        }
        if (totalQty.signum() <= 0) {
            log.error("[auditPass] returnId={} return_qty合计<=0 totalQty={}", returnId, totalQty);
            throw new ServiceException("无可退数量：退货行合计return_qty=" + totalQty);
        }

        String auditBy;
        try { auditBy = SecurityUtils.getUsername(); } catch (Exception ignore) { auditBy = "system"; }
        if (isBlank(auditBy)) auditBy = "system";

        // 2. 头状态推进 0/1 -> 2(已审核)；写入auditBy/time
        ret.setStatus(2);
        ret.setAuditBy(auditBy);
        ret.setAuditTime(LocalDateTime.now());
        ret.setTotalQty(totalQty);
        // 3. 生成WMS出库并直接推进到3(已确认)
        Long stockOutId;
        if (ret.getStockOutId() != null) {
            stockOutId = ret.getStockOutId();
            try { stockOutService.confirmStockOut(stockOutId); }
            catch (ServiceException ignored) {
                // 可能已是3状态；忽略，后续按状态判定
            }
        } else {
            stockOutId = createAndConfirmWmsStockOut(ret, lines);
        }
        ret.setStockOutId(stockOutId);

        // 4. 手动发布事件（双重确保 confirmStockOut 内部已经发过，这里兜底保证幂等即可）
        try {
            StockOutConfirmedEvent evt = new StockOutConfirmedEvent(this, stockOutId);
            SpringUtils.getBean(org.springframework.context.ApplicationContext.class).publishEvent(evt);
        } catch (Exception e) {
            log.warn("[auditPass] 兜底发布 StockOutConfirmedEvent 失败 stockOutId={} msg={}", stockOutId, e.getMessage());
        }

        // 5. 反写PO行returnedQty（按returnLine.poLineId分组；WMS侧confirmStockOut也会反写，但这里再精准确认一次避免poLineId链路未传递导致漏）
        Set<Long> touchedPoIds = new HashSet<>();
        Map<Long, BigDecimal> poLineRet = new LinkedHashMap<>();
        for (ScmPurchaseReturnLine l : lines) {
            Long plId = resolvePoLineId(l);
            if (plId == null) continue;
            poLineRet.merge(plId, l.getReturnQty() == null ? BigDecimal.ZERO : l.getReturnQty(), BigDecimal::add);
            Long poId = selectPoIdByLineId(plId);
            if (poId != null) touchedPoIds.add(poId);
        }
        for (Map.Entry<Long, BigDecimal> e : poLineRet.entrySet()) {
            incrementReturnedQty(e.getKey(), e.getValue());
        }
        for (Long poId : touchedPoIds) refreshPoStatus(poId);

        // 6. 头状态再推进到 4=已完成 (出库已确认)
        ret.setStatus(4);
        mapper.updateById(ret);

        // 7. 若 hasReplenishment == 1 → 生成补请购
        Long requisitionId = null;
        List<Long> requisitionLineIds = null;
        boolean needReplenish = ret.getHasReplenishment() != null && ret.getHasReplenishment() == 1;
        if (needReplenish) {
            Map<String, Object> replenish = createReplenishmentRequisition(ret, lines, auditBy);
            requisitionId = (Long) replenish.get("requisitionId");
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) replenish.get("lineIds");
            requisitionLineIds = ids;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("returnId", returnId);
        result.put("oldStatus", oldStatus);
        result.put("newStatus", ret.getStatus());
        result.put("stockOutId", stockOutId);
        result.put("totalQty", totalQty);
        result.put("auditBy", auditBy);
        if (requisitionId != null) {
            result.put("requisitionId", requisitionId);
            result.put("requisitionLineIds", requisitionLineIds);
        }
        log.info("[auditPass] 完成 returnId={} old={} new={} stockOutId={} touchedPoIds={} reqId={}",
                returnId, oldStatus, ret.getStatus(), stockOutId, touchedPoIds, requisitionId);
        return result;
    }

    /** 创建 WMS 出库单并一口气推进到 status=3 已确认 (RETURN采购退货) */
    private Long createAndConfirmWmsStockOut(ScmPurchaseReturn ret, List<ScmPurchaseReturnLine> lines) {
        WmsStockOut so = new WmsStockOut();
        so.setWarehouseId(ret.getWarehouseId() == null ? 1L : ret.getWarehouseId());
        so.setCustomerId(ret.getSupplierId());
        so.setCustomerName(ret.getSupplierName());
        so.setOutType("RETURN");
        so.setOrderNo(ret.getReturnNo());
        so.setRemark("采购退货出库 来源returnNo=" + (ret.getReturnNo() == null ? String.valueOf(ret.getReturnId()) : ret.getReturnNo())
                + (ret.getRemark() == null ? "" : " | " + ret.getRemark()));

        List<WmsStockOutLine> sol = new ArrayList<>(lines.size());
        for (ScmPurchaseReturnLine l : lines) {
            WmsStockOutLine s = new WmsStockOutLine();
            s.setMaterialId(l.getMaterialId());
            s.setMaterialCode(l.getMaterialCode());
            s.setMaterialName(l.getMaterialName());
            s.setSpecification(l.getSpecification());
            s.setBatchNo(l.getBatchNo());
            s.setUnit(l.getUnit());
            s.setQuantity(l.getReturnQty() == null ? BigDecimal.ZERO : l.getReturnQty());
            s.setRemark(l.getRemark());
            s.setReturnId(ret.getReturnId());
            s.setReturnLineId(l.getLineId());
            s.setDeliveryId(ret.getDeliveryId());
            s.setDeliveryLineId(l.getDeliveryLineId());
            s.setPoId(ret.getPoId());
            Long poLineId = resolvePoLineId(l);
            s.setPoLineId(poLineId);
            sol.add(s);
        }
        so.setLines(sol);
        Long stockOutId = stockOutService.saveStockOut(so);
        if (stockOutId == null) throw new ServiceException("保存WMS出库单失败");
        try {
            stockOutService.submitAudit(stockOutId);
            stockOutService.auditPass(stockOutId);
            stockOutService.confirmStockOut(stockOutId);
        } catch (Exception ex) {
            log.error("[auditPass] WMS 出库状态推进失败 returnId={} stockOutId={}", ret.getReturnId(), stockOutId, ex);
            throw new ServiceException("WMS 出库状态推进失败: " + ex.getMessage());
        }
        return stockOutId;
    }

    /** 优先取 returnLine.poLineId；否则从 deliveryLineId 反查；否则返回null */
    private Long resolvePoLineId(ScmPurchaseReturnLine l) {
        if (l.getPoLineId() != null) return l.getPoLineId();
        return resolvePoLineIdByDeliveryLine(l.getDeliveryLineId());
    }

    /** UPDATE scm_purchase_order_line SET returned_qty = returned_qty + ? WHERE line_id = ? */
    private void incrementReturnedQty(Long poLineId, BigDecimal delta) {
        if (poLineId == null || delta == null || delta.signum() == 0) return;
        int rows = jdbcTemplate.update(
                "UPDATE scm_purchase_order_line SET returned_qty = COALESCE(returned_qty,0) + ? WHERE line_id = ?",
                delta, poLineId);
        if (rows == 0) log.warn("[incrementReturnedQty] 未更新PO行 lineId={} delta={}", poLineId, delta);
    }

    private Long selectPoIdByLineId(Long lineId) {
        try {
            return jdbcTemplate.queryForObject("SELECT po_id FROM scm_purchase_order_line WHERE line_id=?", Long.class, lineId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 重算PO头状态：按收到/退回推导 (与ScmPurchaseOrderServiceImpl.generateStockIn状态逻辑对齐) */
    private void refreshPoStatus(Long poId) {
        if (poId == null) return;
        ScmPurchaseOrder po = poMapper.selectById(poId);
        if (po == null) return;
        Integer cur = po.getStatus() == null ? 0 : po.getStatus();
        if (!Arrays.asList(2, 3, 4, 5).contains(cur)) return;

        List<ScmPurchaseOrderLine> pols = poLineMapper.selectList(new QueryWrapper<ScmPurchaseOrderLine>().eq("po_id", poId));
        BigDecimal sumOrder = BigDecimal.ZERO;
        BigDecimal sumReceived = BigDecimal.ZERO;
        for (ScmPurchaseOrderLine l : pols) {
            sumOrder = sumOrder.add(l.getOrderQty() == null ? BigDecimal.ZERO : l.getOrderQty());
            sumReceived = sumReceived.add(l.getReceivedQty() == null ? BigDecimal.ZERO : l.getReceivedQty());
        }
        int next;
        if (sumReceived.signum() <= 0) next = 2;
        else if (sumOrder.signum() > 0 && sumReceived.compareTo(sumOrder) >= 0) next = 5;
        else next = 4;
        if (next != cur) {
            po.setStatus(next);
            poMapper.updateById(po);
            log.info("[refreshPoStatus@Return] PO={} {}→{} order={} received={}", poId, cur, next, sumOrder, sumReceived);
        }
    }

    /** 生成补请购单：创建草稿请购单头 + 提交状态(1)的请购明细行 */
    private Map<String, Object> createReplenishmentRequisition(ScmPurchaseReturn ret, List<ScmPurchaseReturnLine> lines, String applicant) {
        ScmPurchaseRequisition pr = new ScmPurchaseRequisition();
        pr.setTitle("采购退货补单 #" + (ret.getReturnNo() == null ? String.valueOf(ret.getReturnId()) : ret.getReturnNo()));
        pr.setRequisitionDate(LocalDate.now());
        pr.setApplicantName(applicant);
        pr.setWarehouseId(ret.getWarehouseId());
        String reason = "退货补单 returnNo=" + (ret.getReturnNo() == null ? String.valueOf(ret.getReturnId()) : ret.getReturnNo())
                + " reason=" + (ret.getReturnReason() == null ? "" : ret.getReturnReason());
        pr.setReason(reason);
        pr.setStatus(0); // 草稿(按约定当天)
        pr.setRemark(reason);
        Long requisitionId = requisitionService.save(pr);
        if (requisitionId == null) throw new ServiceException("生成补请购单头失败");

        LocalDate requiredDate = LocalDate.now().plusDays(14);
        List<Long> lineIds = new ArrayList<>(lines.size());
        int seq = 0;
        for (ScmPurchaseReturnLine l : lines) {
            ScmPurchaseRequisitionLine rl = new ScmPurchaseRequisitionLine();
            seq += 10;
            rl.setRequisitionId(requisitionId);
            rl.setLineNo(seq);
            rl.setMaterialId(l.getMaterialId());
            rl.setMaterialCode(l.getMaterialCode());
            rl.setMaterialName(l.getMaterialName());
            rl.setSpecification(l.getSpecification());
            rl.setUnit(l.getUnit());
            rl.setQty(l.getReturnQty() == null ? BigDecimal.ZERO : l.getReturnQty());
            rl.setEstimatePrice(l.getUnitPrice() == null ? BigDecimal.ZERO : l.getUnitPrice());
            rl.setRequiredDate(requiredDate);
            rl.setSupplierId(ret.getSupplierId());
            rl.setSupplierName(ret.getSupplierName());
            rl.setStatus(1); // 提交待审
            rl.setRemark(reason);
            Long lid = requisitionLineService.save(rl);
            if (lid != null) lineIds.add(lid);
        }
        log.info("[createReplenishmentRequisition] 生成补请购 requisitionId={} lines={}", requisitionId, lineIds.size());
        Map<String, Object> r = new HashMap<>(4);
        r.put("requisitionId", requisitionId);
        r.put("lineIds", lineIds);
        return r;
    }

    /* ============================================================
     * T4-4 fromStockInLines：前端选入库单行 → 生成草稿退货单(按stockIn分组)
     * ========================================================== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> fromStockInLines(Map<String, Object> body) {
        if (body == null || body.isEmpty()) throw new ServiceException("请求体不能为空");
        @SuppressWarnings("unchecked")
        Collection<Object> rawIds = (Collection<Object>) body.get("stockInLineIds");
        if (rawIds == null || rawIds.isEmpty()) throw new ServiceException("缺少 stockInLineIds 列表");
        List<Long> stockInLineIds = new ArrayList<>(rawIds.size());
        for (Object o : rawIds) {
            if (o == null) continue;
            try { stockInLineIds.add(Long.valueOf(o.toString())); }
            catch (Exception ignore) {}
        }
        if (stockInLineIds.isEmpty()) throw new ServiceException("stockInLineIds 为空");
        Object rawWh = body.get("warehouseId");
        Long warehouseId = null;
        if (rawWh != null) { try { warehouseId = Long.valueOf(rawWh.toString()); } catch (Exception ignore) {} }
        String returnReason = body.get("returnReason") == null ? "" : body.get("returnReason").toString();
        Object rawRepl = body.get("hasReplenishment");
        Integer hasReplenishment = 0;
        if (rawRepl != null) {
            try { hasReplenishment = Integer.valueOf(rawRepl.toString()); }
            catch (Exception ignore) { hasReplenishment = "1".equals(rawRepl.toString()) || Boolean.TRUE.toString().equalsIgnoreCase(rawRepl.toString()) ? 1 : 0; }
        }

        List<WmsStockInLine> silList = stockInLineMapper.selectBatchIds(stockInLineIds);
        if (silList == null || silList.isEmpty()) throw new ServiceException("入库明细不存在");

        // 按 stock_in_id 分组
        Map<Long, List<WmsStockInLine>> groups = new LinkedHashMap<>();
        for (WmsStockInLine l : silList) {
            Long sid = l.getStockInId();
            if (sid == null) continue;
            groups.computeIfAbsent(sid, k -> new ArrayList<>()).add(l);
        }
        // 批量取 wms_stock_in (warehouseId/supplierId 等)
        Set<Long> stockInIds = groups.keySet();
        Map<Long, WmsStockIn> stockInMap = new HashMap<>();
        List<WmsStockIn> sis = stockInMapper.selectBatchIds(stockInIds);
        if (sis != null) for (WmsStockIn s : sis) stockInMap.put(s.getStockInId(), s);

        List<Long> returnIds = new ArrayList<>(groups.size());
        for (Map.Entry<Long, List<WmsStockInLine>> e : groups.entrySet()) {
            Long stockInId = e.getKey();
            WmsStockIn si = stockInMap.get(stockInId);
            ScmPurchaseReturn header = new ScmPurchaseReturn();
            header.setStatus(0);
            header.setWarehouseId(warehouseId != null ? warehouseId : (si == null ? 1L : si.getWarehouseId()));
            header.setSupplierId(si == null ? null : si.getSupplierId());
            header.setSupplierName(si == null ? null : si.getSupplierName());
            header.setReturnDate(LocalDate.now());
            header.setReturnReason(returnReason);
            header.setHasReplenishment(hasReplenishment);
            if (si != null) {
                header.setRemark("由入库单生成 stockInId=" + stockInId + (si.getOrderNo() == null ? "" : " orderNo=" + si.getOrderNo()));
            } else {
                header.setRemark("由入库单行生成 stockInId=" + stockInId);
            }
            // 若该组所有行 poId 一致，回填poId
            Set<Long> poIds = e.getValue().stream().map(WmsStockInLine::getPoId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (poIds.size() == 1) header.setPoId(poIds.iterator().next());

            List<ScmPurchaseReturnLine> rls = new ArrayList<>(e.getValue().size());
            for (WmsStockInLine sil : e.getValue()) {
                ScmPurchaseReturnLine rl = new ScmPurchaseReturnLine();
                // 默认可退=入库原量-已退
                BigDecimal alreadyRet = sumAlreadyReturnedForStockInLine(sil.getLineId(), null);
                BigDecimal orig = sil.getQuantity() == null ? BigDecimal.ZERO : sil.getQuantity();
                BigDecimal remain = orig.subtract(alreadyRet);
                if (remain.signum() < 0) remain = BigDecimal.ZERO;
                rl.setStockInLineId(sil.getLineId());
                rl.setStockInId(sil.getStockInId());
                rl.setMaterialId(sil.getMaterialId());
                rl.setMaterialCode(sil.getMaterialCode());
                rl.setMaterialName(sil.getMaterialName());
                rl.setSpecification(sil.getSpecification());
                rl.setBatchNo(sil.getBatchNo());
                rl.setUnit(sil.getUnit());
                rl.setReturnQty(remain); // 预填剩余可退，前端可改
                rl.setUnitPrice(sil.getUnitPrice());
                rl.setPoLineId(sil.getPoLineId());
                rl.setDeliveryLineId(sil.getDeliveryLineId());
                rls.add(rl);
            }
            // 过滤掉剩余可退=0的行（避免生成空退货单）
            rls.removeIf(l -> l.getReturnQty() == null || l.getReturnQty().signum() <= 0);
            if (rls.isEmpty()) {
                log.warn("[fromStockInLines] stockInId={} 所有行剩余可退为0，跳过生成", stockInId);
                continue;
            }
            Long rid = saveReturn(header, rls);
            if (rid != null) returnIds.add(rid);
        }
        log.info("[fromStockInLines] 生成退货单数量={} returnIds={}", returnIds.size(), returnIds);
        return returnIds;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
