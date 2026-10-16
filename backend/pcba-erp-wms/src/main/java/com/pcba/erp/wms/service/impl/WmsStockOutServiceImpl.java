package com.pcba.erp.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.event.StockOutConfirmedEvent;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.wms.entity.WmsInventory;
import com.pcba.erp.wms.entity.WmsStockOut;
import com.pcba.erp.wms.entity.WmsStockOutLine;
import com.pcba.erp.wms.mapper.WmsInventoryMapper;
import com.pcba.erp.wms.mapper.WmsStockOutLineMapper;
import com.pcba.erp.wms.mapper.WmsStockOutMapper;
import com.pcba.erp.wms.service.IWmsStockOutService;
import com.pcba.erp.wms.service.IWmsInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 出库单 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsStockOutServiceImpl extends ServiceImpl<WmsStockOutMapper, WmsStockOut>
        implements IWmsStockOutService {

    private final WmsStockOutLineMapper lineMapper;
    private final WmsInventoryMapper inventoryMapper;
    private final IWmsInventoryService inventoryService;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /* ========== 分页查询 ========== */
    @Override
    public IPage<WmsStockOut> selectStockOutPage(int pageNum, int pageSize,
                                                  String orderNo, Long warehouseId,
                                                  String outType, Integer status) {
        Page<WmsStockOut> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectStockOutPage(page, orderNo, warehouseId, outType, status);
    }

    /* ========== 详情 ========== */
    @Override
    public WmsStockOut getDetail(Long stockOutId) {
        WmsStockOut so = baseMapper.selectById(stockOutId);
        if (so == null) return null;
        so.setLines(lineMapper.selectByStockOutId(stockOutId));
        return so;
    }

    /* ========== 单号生成 ========== */
    @Override
    public synchronized String nextOrderNo() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "OUT" + today;
        List<WmsStockOut> list = baseMapper.selectList(new LambdaQueryWrapper<WmsStockOut>()
                .likeRight(WmsStockOut::getOrderNo, prefix)
                .orderByDesc(WmsStockOut::getOrderNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (list != null && !list.isEmpty()) {
            String maxNo = list.get(0).getOrderNo();
            if (maxNo != null && maxNo.length() > prefix.length()) {
                try {
                    seq = Integer.parseInt(maxNo.substring(prefix.length())) + 1;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return prefix + String.format("%03d", seq);
    }

    /* ========== 保存 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveStockOut(WmsStockOut stockOut) {
        if (stockOut.getOrderNo() == null || stockOut.getOrderNo().isBlank()) {
            stockOut.setOrderNo(nextOrderNo());
        }
        WmsStockOut exist = baseMapper.selectOne(new LambdaQueryWrapper<WmsStockOut>()
                .eq(WmsStockOut::getOrderNo, stockOut.getOrderNo()));
        if (exist != null) throw new ServiceException("出库单号已存在: " + stockOut.getOrderNo());
        if (stockOut.getWarehouseId() == null) throw new ServiceException("出库仓库不能为空");
        if (stockOut.getOutType() == null || stockOut.getOutType().isBlank()) {
            stockOut.setOutType("PRODUCTION");
        }
        if (stockOut.getStatus() == null) stockOut.setStatus(0);

        stockOut.setTotalQty(calcTotalQty(stockOut.getLines()));
        baseMapper.insert(stockOut);
        Long stockOutId = stockOut.getStockOutId();
        saveLines(stockOutId, stockOut.getLines());
        return stockOutId;
    }

    /* ========== 修改（主表 + 明细先删后插） ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStockOut(WmsStockOut stockOut) {
        if (stockOut.getStockOutId() == null) throw new ServiceException("出库单ID不能为空");
        WmsStockOut old = requireStatus(stockOut.getStockOutId(), 0, 4);
        if (stockOut.getOrderNo() != null && !stockOut.getOrderNo().isBlank()) {
            WmsStockOut dup = baseMapper.selectOne(new LambdaQueryWrapper<WmsStockOut>()
                    .eq(WmsStockOut::getOrderNo, stockOut.getOrderNo())
                    .ne(WmsStockOut::getStockOutId, stockOut.getStockOutId()));
            if (dup != null) throw new ServiceException("出库单号已存在: " + stockOut.getOrderNo());
        } else {
            stockOut.setOrderNo(old.getOrderNo());
        }

        List<WmsStockOutLine> lines = stockOut.getLines();
        if (lines != null) {
            lineMapper.delete(new LambdaQueryWrapper<WmsStockOutLine>()
                    .eq(WmsStockOutLine::getStockOutId, stockOut.getStockOutId()));
            stockOut.setTotalQty(calcTotalQty(lines));
            saveLines(stockOut.getStockOutId(), lines);
        }
        return baseMapper.updateById(stockOut) > 0;
    }

    /* ========== 删除 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeStockOut(Long stockOutId) {
        requireStatus(stockOutId, 0, 4);
        lineMapper.delete(new LambdaQueryWrapper<WmsStockOutLine>()
                .eq(WmsStockOutLine::getStockOutId, stockOutId));
        return baseMapper.deleteById(stockOutId) > 0;
    }

    /* ========== 状态机 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submitAudit(Long stockOutId) {
        WmsStockOut old = requireStatus(stockOutId, 0);
        old.setStatus(1);
        return baseMapper.updateById(old) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditPass(Long stockOutId) {
        WmsStockOut old = requireStatus(stockOutId, 1);
        old.setStatus(2);
        old.setAuditBy(SecurityUtils.getUsernameSafe());
        old.setAuditTime(LocalDateTime.now());
        return baseMapper.updateById(old) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reject(Long stockOutId, String reason) {
        if (reason == null || reason.isBlank()) throw new ServiceException("驳回原因不能为空");
        WmsStockOut old = requireStatus(stockOutId, 1);
        old.setStatus(4);
        old.setAuditBy(SecurityUtils.getUsernameSafe());
        old.setAuditTime(LocalDateTime.now());
        old.setRejectReason(reason);
        return baseMapper.updateById(old) > 0;
    }

    /* ========== 确认出库（扣减库存 + 采购退货反写 PO 行 returnedQty） ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmStockOut(Long stockOutId) {
        WmsStockOut so = requireStatus(stockOutId, 2);
        List<WmsStockOutLine> lines = lineMapper.selectByStockOutId(stockOutId);
        if (lines == null || lines.isEmpty()) throw new ServiceException("出库单明细为空，无法确认出库");
        Long warehouseId = so.getWarehouseId();

        // 1. 预校验库存充足
        for (WmsStockOutLine line : lines) {
            WmsInventory inv = findInventory(warehouseId, line);
            BigDecimal available = (inv == null || inv.getAvailableQty() == null)
                    ? BigDecimal.ZERO : inv.getAvailableQty();
            if (available.compareTo(nullToZero(line.getQuantity())) < 0) {
                throw new ServiceException("库存不足");
            }
        }
        // 2. 扣减库存（与入库对称: quantity -= qty; availableQty = quantity - lockedQty）
        for (WmsStockOutLine line : lines) {
            decreaseInventory(warehouseId, line);
        }
        // 3. 采购退货类反写 PO 行 returnedQty（sign=+1 累加）
        applyPoReturnedDelta(lines, +1);
        // 3b. 销售出库类反写 SO 行 shippedQty（sign=+1 累加）+ 推进 SO status 4/5
        applySoShippedDelta(lines, +1);
        // Write the confirmed state before synchronous listeners query it; all writes share this transaction.
        so.setStatus(3);
        if (baseMapper.updateById(so) != 1) throw new ServiceException("出库状态更新失败");
        // 4. 发布出库确认事件 → SCM监听生成红字应付(退货)；SAL监听生成应收(销售出库)
        try { eventPublisher.publishEvent(new StockOutConfirmedEvent(this, stockOutId)); }
        catch (Exception ex) { log.error("confirmStockOut 发布出库确认事件失败 stockOutId={}", stockOutId, ex); throw ex; }
        return true;
    }

    /* ========== 取消出库：状态 3→2，回滚库存台账 + 回滚 PO returnedQty ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelStockOut(Long stockOutId) {
        WmsStockOut so = requireStatus(stockOutId, 3);
        List<WmsStockOutLine> lines = lineMapper.selectByStockOutId(stockOutId);
        Long warehouseId = so.getWarehouseId();
        // 1) 库存回滚：按明细 quantity 加回 inventory（负/缺记录收敛 warn）
        if (lines != null) {
            for (WmsStockOutLine line : lines) increaseInventory(warehouseId, line);
        }
        // 2) PO returnedQty 回滚 sign=-1
        applyPoReturnedDelta(lines, -1);
        // 2b) SO shippedQty 回滚 sign=-1 + 回退 SO status（全部 shipped=0 则回 2 并清空 stock_out_id 允许重新生成）
        applySoShippedDelta(lines, -1);
        so.setStatus(2);
        return baseMapper.updateById(so) > 0;
    }

    /* ============== 私有工具 ============== */

    /** 库存增加（confirmStockOut 回滚时使用）——找不到记录或负值收敛至 0，幂等不抛异常 */
    private void increaseInventory(Long warehouseId, WmsStockOutLine line) {
        inventoryService.increaseInventory(warehouseId, line.getMaterialId(), line.getBatchNo(),
                line.getQuantity(), line.getUnit());
    }

    /** 库存扣减：库存不足抛异常；存在则 quantity -= qty 并重算 availableQty */
    private void decreaseInventory(Long warehouseId, WmsStockOutLine line) {
        inventoryService.decreaseInventory(warehouseId, line.getMaterialId(), line.getBatchNo(), line.getQuantity());
    }

    private WmsInventory findInventory(Long warehouseId, WmsStockOutLine line) {
        if (line.getMaterialId() == null) throw new ServiceException("明细缺少物料ID");
        return inventoryMapper.selectByWhMatBatch(warehouseId, line.getMaterialId(), line.getBatchNo());
    }

    private BigDecimal calcTotalQty(List<WmsStockOutLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        if (lines == null || lines.isEmpty()) return total;
        for (WmsStockOutLine l : lines) {
            total = total.add(nullToZero(l.getQuantity()));
        }
        return total;
    }

    private void saveLines(Long stockOutId, List<WmsStockOutLine> lines) {
        if (lines == null || lines.isEmpty()) return;
        for (WmsStockOutLine l : lines) {
            l.setLineId(null);
            l.setStockOutId(stockOutId);
            if (l.getQuantity() == null || l.getQuantity().signum() <= 0) throw new ServiceException("出库数量必须大于0");
            lineMapper.insert(l);
        }
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private WmsStockOut requireStatus(Long stockOutId, Integer... expected) {
        WmsStockOut old = baseMapper.selectForUpdate(stockOutId);
        if (old == null) throw new ServiceException("出库单不存在");
        Set<Integer> exp = Arrays.stream(expected).collect(Collectors.toSet());
        if (!exp.contains(old.getStatus())) {
            throw new ServiceException("当前出库单状态不允许该操作");
        }
        return old;
    }

    /* ============== PO 行 returnedQty 反写（采购退货类出库） ============== */

    /**
     * sign=+1 confirmStockOut：累加 PO行 returnedQty
     * sign=-1 cancelStockOut：扣减 PO行 returnedQty（使用 GREATEST(0,…) 防负数）
     * 分配优先级（命中即停，参考 WmsStockInServiceImpl 实现对齐）：
     *   1) wmsStockOutLine.poLineId 非空 —— 精准
     *   2) (poId, materialId) 非空 —— 按 line_no 升序贪心
     *      · sign>0：先落到 returnedQty<orderQty 的行；超额落到首行
     *      · sign<0：按 receivedQty/returnedQty 均无限制，只要 returnedQty>0 就扣
     *   3) returnLineId/deliveryLineId 反查 return/po → 回到 1/2
     *   4) 都空 → debug 跳过（领料/销售/调拨等出库）
     */
    private void applyPoReturnedDelta(List<WmsStockOutLine> lines, int sign) {
        if (lines == null || lines.isEmpty()) return;
        Set<Long> touchedPoIds = new HashSet<>();

        for (WmsStockOutLine l : lines) {
            BigDecimal qty = nullToZero(l.getQuantity());
            if (qty.signum() == 0) continue;
            BigDecimal delta = qty.multiply(BigDecimal.valueOf(sign));
            Long poLineId = l.getPoLineId();
            Long poId = l.getPoId();
            Long materialId = l.getMaterialId();

            // 路径3：returnLineId/deliveryLineId 兜底反查
            if (poLineId == null && poId == null) {
                Map<String, Object> info = queryReturnPoLink(l.getReturnLineId(), l.getDeliveryLineId());
                if (info != null) {
                    poLineId = (Long) info.get("poLineId");
                    if (poLineId == null) {
                        poId = (Long) info.get("poId");
                    }
                }
            }
            if (poLineId != null) {
                adjustPoLineReturned(poLineId, delta);
                Long resolved = selectPoIdByLineId(poLineId);
                if (resolved != null) touchedPoIds.add(resolved);
            } else if (poId != null && materialId != null) {
                greedyAssignReturnedToPoLines(poId, materialId, delta, touchedPoIds);
            } else {
                log.debug("[applyPoReturnedDelta] 跳过非PO出库明细 lineId={}", l.getLineId());
            }
        }
        // 注意：采购退货仅反写 PO行 returnedQty，PO 主单 status 仍按 receivedQty 推导（2/4/5）。
        // 若 receivedQty = returnedQty（即全部退光），此时 received 仍>=0 不会触发状态回到 2；
        // 仅在 receivedQty 合计为 0 时 status=2 （cancel 采购入库 confirm 的时候能触发）
        for (Long poId : touchedPoIds) recomputePoStatus(poId);
    }

    private void adjustPoLineReturned(Long poLineId, BigDecimal delta) {
        if (poLineId == null || delta == null || delta.signum() == 0) return;
        int rows = jdbcTemplate.update(
                "UPDATE scm_purchase_order_line SET returned_qty = GREATEST(0, COALESCE(returned_qty,0) + ?) WHERE line_id = ?",
                delta, poLineId);
        if (rows == 0) log.warn("[adjustPoLineReturned] 未找到PO行 lineId={}", poLineId);
    }

    private void greedyAssignReturnedToPoLines(Long poId, Long materialId, BigDecimal delta, Set<Long> touched) {
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                "SELECT line_id, order_qty o, COALESCE(returned_qty,0) r FROM scm_purchase_order_line " +
                        "WHERE po_id=? AND material_id=? ORDER BY line_no ASC",
                poId, materialId);
        if (candidates.isEmpty()) {
            log.warn("[greedyAssignReturnedToPoLines] PO下未找到物料行 poId={} materialId={}", poId, materialId);
            return;
        }
        BigDecimal remain = delta;
        if (delta.signum() > 0) {
            // +delta：优先往 returnedQty<orderQty 的行填；剩余溢出落到首行（允许 returned>order 的真实记录）
            for (Map<String, Object> c : candidates) {
                if (remain.signum() <= 0) break;
                BigDecimal order = toDecimal(c.get("o"));
                BigDecimal returned = toDecimal(c.get("r"));
                BigDecimal cap = order.subtract(returned);
                BigDecimal use = cap.signum() > 0 ? remain.min(cap) : BigDecimal.ZERO;
                if (use.signum() > 0) {
                    adjustPoLineReturned((Long) c.get("line_id"), use);
                    remain = remain.subtract(use);
                }
            }
            if (remain.signum() > 0) {
                adjustPoLineReturned((Long) candidates.get(0).get("line_id"), remain);
            }
        } else {
            // -delta：按 line_no 顺序扣 returnedQty>0 的行
            for (Map<String, Object> c : candidates) {
                if (remain.signum() >= 0) break;
                BigDecimal returned = toDecimal(c.get("r"));
                if (returned.signum() <= 0) continue;
                BigDecimal use = returned.min(remain.abs());
                if (use.signum() <= 0) continue;
                adjustPoLineReturned((Long) c.get("line_id"), use.negate());
                remain = remain.add(use);
            }
        }
        touched.add(poId);
    }

    private Long selectPoIdByLineId(Long lineId) {
        try {
            return jdbcTemplate.queryForObject("SELECT po_id FROM scm_purchase_order_line WHERE line_id=?", Long.class, lineId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 回退链路：按 return_line_id / delivery_line_id 找对应 PO 关联 */
    private Map<String, Object> queryReturnPoLink(Long returnLineId, Long deliveryLineId) {
        if (returnLineId != null) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT r.po_id poId, r.delivery_id deliveryId FROM scm_purchase_return r " +
                                "JOIN scm_purchase_return_line l ON l.return_id = r.return_id WHERE l.line_id=?", returnLineId);
                if (!rows.isEmpty()) {
                    Map<String, Object> r = rows.get(0);
                    // 如 return_line.delivery_line_id 也存在（上层实体已存 deliveryLineId），尝试再精确定位
                    if (deliveryLineId != null) {
                        Long poLineId = queryPoLineIdByDeliveryLine(deliveryLineId);
                        if (poLineId != null) { r.put("poLineId", poLineId); return r; }
                    }
                    return r;
                }
            } catch (Exception e) {
                log.warn("[queryReturnPoLink] returnLineId={} 查询失败 msg={}", returnLineId, e.getMessage());
            }
        }
        if (deliveryLineId != null) {
            Long lineId = queryPoLineIdByDeliveryLine(deliveryLineId);
            if (lineId != null) {
                Map<String, Object> m = new HashMap<>(2);
                m.put("poLineId", lineId);
                return m;
            }
        }
        return null;
    }

    private Long queryPoLineIdByDeliveryLine(Long deliveryLineId) {
        try {
            return jdbcTemplate.queryForObject("SELECT po_line_id FROM scm_delivery_note_line WHERE line_id=?", Long.class, deliveryLineId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 按 receivedQty 重算 PO 主单状态（returnedQty 不影响状态，仅 receivedQty 影响） */
    private void recomputePoStatus(Long poId) {
        if (poId == null) return;
        Map<String, Object> agg;
        try {
            agg = jdbcTemplate.queryForMap(
                    "SELECT COALESCE(SUM(order_qty),0) so, COALESCE(SUM(received_qty),0) sr, " +
                            "   (SELECT status FROM scm_purchase_order WHERE po_id=?) cur " +
                            "FROM scm_purchase_order_line WHERE po_id=?",
                    poId, poId);
        } catch (Exception e) {
            log.warn("[recomputePoStatus] 汇总失败 poId={} msg={}", poId, e.getMessage());
            return;
        }
        BigDecimal sumOrder = toDecimal(agg.get("so"));
        BigDecimal sumReceived = toDecimal(agg.get("sr"));
        Integer cur = agg.get("cur") == null ? null : ((Number) agg.get("cur")).intValue();
        if (cur == null) return;
        if (!Arrays.asList(2, 4, 5).contains(cur)) return;
        int next;
        if (sumReceived.signum() <= 0) next = 2;
        else if (sumOrder.signum() > 0 && sumReceived.compareTo(sumOrder) >= 0) next = 5;
        else next = 4;
        if (next != cur) {
            jdbcTemplate.update("UPDATE scm_purchase_order SET status=? WHERE po_id=?", next, poId);
            log.info("[recomputePoStatus@StockOut] PO={} {} → {}  (order={} received={})", poId, cur, next, sumOrder, sumReceived);
        }
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(o.toString()); } catch (Exception ignore) { return BigDecimal.ZERO; }
    }

    /* ============== SO 行 shippedQty 反写（销售出库类出库） ============== */

    /**
     * sign=+1 confirmStockOut：累加 SO行 shippedQty + 推进 SO status 4(部分)/5(全部)
     * sign=-1 cancelStockOut：扣减 SO行 shippedQty（GREATEST(0,…) 防负）+ 回退 SO status
     *   · 全部 shipped=0 → status 回退 2(已审核) + 清空 stock_out_id 允许重新生成
     *   · 仍有 0<shipped<order → status 4(部分出库)
     * 仅处理 soLineId 非空的明细（销售出库类）；其他类型跳过
     * 通过 JdbcTemplate 直写 sal_sales_order_line / sal_sales_order，WMS 不依赖 SAL 模块
     */
    private void applySoShippedDelta(List<WmsStockOutLine> lines, int sign) {
        if (lines == null || lines.isEmpty()) return;
        Set<Long> touchedSoIds = new HashSet<>();

        for (WmsStockOutLine l : lines) {
            BigDecimal qty = nullToZero(l.getQuantity());
            if (qty.signum() == 0) continue;
            Long soLineId = l.getSoLineId();
            if (soLineId == null) {
                log.debug("[applySoShippedDelta] 跳过非SO出库明细 lineId={}", l.getLineId());
                continue;
            }
            BigDecimal delta = qty.multiply(BigDecimal.valueOf(sign));
            adjustSoLineShipped(soLineId, delta);
            Long soId = l.getSoId();
            if (soId == null) soId = selectSoIdByLineId(soLineId);
            if (soId != null) touchedSoIds.add(soId);
        }
        for (Long soId : touchedSoIds) recomputeSoStatus(soId);
    }

    private void adjustSoLineShipped(Long soLineId, BigDecimal delta) {
        if (soLineId == null || delta == null || delta.signum() == 0) return;
        int rows = jdbcTemplate.update(
                "UPDATE sal_sales_order_line SET shipped_qty = GREATEST(0, COALESCE(shipped_qty,0) + ?) WHERE line_id = ?",
                delta, soLineId);
        if (rows == 0) log.warn("[adjustSoLineShipped] 未找到SO行 lineId={}", soLineId);
    }

    private Long selectSoIdByLineId(Long lineId) {
        try {
            return jdbcTemplate.queryForObject("SELECT so_id FROM sal_sales_order_line WHERE line_id=?", Long.class, lineId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按 shippedQty/orderQty 重算 SO 主单状态：
     *   shipped<=0           → 2(已审核) + 清空 stock_out_id 允许重新生成
     *   shipped>=order        → 5(全部出库)
     *   0<shipped<order       → 4(部分出库)
     * 仅在 3/4/5 状态下重算，避免干预 6/7/9 等终态。
     */
    private void recomputeSoStatus(Long soId) {
        if (soId == null) return;
        Map<String, Object> agg;
        try {
            agg = jdbcTemplate.queryForMap(
                    "SELECT COALESCE(SUM(order_qty),0) so, COALESCE(SUM(shipped_qty),0) ss, " +
                            "  (SELECT status FROM sal_sales_order WHERE so_id=?) cur " +
                            "FROM sal_sales_order_line WHERE so_id=?",
                    soId, soId);
        } catch (Exception e) {
            log.warn("[recomputeSoStatus] 汇总失败 soId={} msg={}", soId, e.getMessage());
            return;
        }
        BigDecimal sumOrder = toDecimal(agg.get("so"));
        BigDecimal sumShipped = toDecimal(agg.get("ss"));
        Integer cur = agg.get("cur") == null ? null : ((Number) agg.get("cur")).intValue();
        if (cur == null) return;
        if (!Arrays.asList(3, 4, 5).contains(cur)) return;

        int next;
        boolean clearStockOut = false;
        if (sumShipped.signum() <= 0) {
            next = 2; clearStockOut = true;
        } else if (sumOrder.signum() > 0 && sumShipped.compareTo(sumOrder) >= 0) {
            next = 5;
        } else {
            next = 4;
        }
        if (next != cur) {
            jdbcTemplate.update("UPDATE sal_sales_order SET status=? WHERE so_id=?", next, soId);
            log.info("[recomputeSoStatus@StockOut] SO={} {} → {} (order={} shipped={})", soId, cur, next, sumOrder, sumShipped);
        }
        if (clearStockOut) {
            jdbcTemplate.update("UPDATE sal_sales_order SET stock_out_id=NULL WHERE so_id=?", soId);
            log.info("[recomputeSoStatus@StockOut] SO={} shipped 全部回退为0，清空 stock_out_id 允许重新生成", soId);
        }
    }
}
