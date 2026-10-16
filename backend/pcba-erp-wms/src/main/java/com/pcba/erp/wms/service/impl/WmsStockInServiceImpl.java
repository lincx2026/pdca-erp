package com.pcba.erp.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.aspectj.DataScopeThreadLocal;
import com.pcba.erp.common.event.StockInConfirmedEvent;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.wms.entity.WmsInventory;
import com.pcba.erp.wms.entity.WmsStockIn;
import com.pcba.erp.wms.entity.WmsStockInLine;
import com.pcba.erp.wms.mapper.WmsInventoryMapper;
import com.pcba.erp.wms.mapper.WmsStockInLineMapper;
import com.pcba.erp.wms.mapper.WmsStockInMapper;
import com.pcba.erp.wms.service.IWmsStockInService;
import com.pcba.erp.wms.service.IWmsInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class WmsStockInServiceImpl implements IWmsStockInService {

    private final WmsStockInMapper stockInMapper;
    private final WmsStockInLineMapper lineMapper;
    private final WmsInventoryMapper inventoryMapper;
    private final IWmsInventoryService inventoryService;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /* ========== 分页查询 ========== */
    @Override
    @DataScope(deptField = "s.dept_id", userField = "s.create_by")
    public IPage<WmsStockIn> selectStockInPage(int pageNum, int pageSize,
                                                String orderNo, Long warehouseId,
                                                String inType, Integer status) {
        String ds = DataScopeThreadLocal.getAndClear();
        List<WmsStockIn> list = stockInMapper.selectStockInPage(orderNo, warehouseId, inType, status, ds);
        Page<WmsStockIn> page = new Page<>(pageNum, pageSize);
        long total = list.size();
        int from = Math.min((pageNum - 1) * pageSize, list.size());
        int to = Math.min(from + pageSize, list.size());
        page.setRecords(list.subList(from, to));
        page.setTotal(total);
        return page;
    }

    @Override
    public WmsStockIn getDetail(Long stockInId) {
        WmsStockIn s = stockInMapper.selectById(stockInId);
        if (s == null) return null;
        s.setLines(lineMapper.selectByStockInId(stockInId));
        return s;
    }

    /* ========== 单号生成: IN + yyyyMMdd + 3位序号 ========== */
    @Override
    public synchronized String nextOrderNo() {
        String prefix = "IN" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String max = stockInMapper.selectMaxOrderNoByPrefix(prefix);
        int seq = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) { }
        }
        return prefix + String.format("%03d", seq);
    }

    /* ========== 保存 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveStockIn(WmsStockIn stockIn) {
        if (stockIn.getOrderNo() == null || stockIn.getOrderNo().isBlank()) {
            stockIn.setOrderNo(nextOrderNo());
        }
        if (stockIn.getWarehouseId() == null) throw new ServiceException("入库仓库不能为空");
        if (stockIn.getInType() == null || stockIn.getInType().isBlank()) stockIn.setInType("PURCHASE");
        if (stockIn.getStatus() == null) stockIn.setStatus(0);

        calcTotals(stockIn);
        stockInMapper.insert(stockIn);
        Long stockInId = stockIn.getStockInId();

        if (stockIn.getLines() != null && !stockIn.getLines().isEmpty()) {
            saveLines(stockInId, stockIn.getLines());
        }
        return stockInId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStockIn(WmsStockIn stockIn) {
        if (stockIn.getStockInId() == null) throw new ServiceException("入库单ID不能为空");
        WmsStockIn old = stockInMapper.selectById(stockIn.getStockInId());
        if (old == null) throw new ServiceException("入库单不存在");
        // 仅草稿(0)/已驳回(4)可修改
        if (!(Integer.valueOf(0).equals(old.getStatus()) || Integer.valueOf(4).equals(old.getStatus()))) {
            throw new ServiceException("仅草稿/驳回状态的入库单可修改");
        }
        // 仓库、单号不允许修改
        stockIn.setOrderNo(old.getOrderNo());
        stockIn.setStatus(old.getStatus());

        calcTotals(stockIn);
        boolean ok = stockInMapper.updateById(stockIn) > 0;

        // 明细全量覆盖
        if (stockIn.getLines() != null) {
            lineMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WmsStockInLine>()
                    .eq(WmsStockInLine::getStockInId, stockIn.getStockInId()));
            if (!stockIn.getLines().isEmpty()) {
                saveLines(stockIn.getStockInId(), stockIn.getLines());
            }
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeStockIn(Long stockInId) {
        WmsStockIn old = stockInMapper.selectById(stockInId);
        if (old == null) return true;
        if (!(Integer.valueOf(0).equals(old.getStatus()) || Integer.valueOf(4).equals(old.getStatus()))) {
            throw new ServiceException("仅草稿/驳回状态的入库单可删除");
        }
        lineMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WmsStockInLine>()
                .eq(WmsStockInLine::getStockInId, stockInId));
        return stockInMapper.deleteById(stockInId) > 0;
    }

    /* ========== 状态机 ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submitAudit(Long stockInId) {
        WmsStockIn old = requireStatus(stockInId, 0);
        old.setStatus(1);
        return stockInMapper.updateById(old) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditPass(Long stockInId, String auditBy) {
        WmsStockIn old = requireStatus(stockInId, 1);
        old.setStatus(2);
        old.setAuditBy(auditBy);
        old.setAuditTime(LocalDateTime.now());
        return stockInMapper.updateById(old) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reject(Long stockInId, String auditBy, String rejectReason) {
        WmsStockIn old = requireStatus(stockInId, 1);
        old.setStatus(4);
        old.setAuditBy(auditBy);
        old.setAuditTime(LocalDateTime.now());
        old.setRejectReason(rejectReason);
        return stockInMapper.updateById(old) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmStockIn(Long stockInId) {
        WmsStockIn old = requireStatus(stockInId, 2);
        // 状态 2 → 3
        old.setStatus(3);
        stockInMapper.updateById(old);

        List<WmsStockInLine> lines = lineMapper.selectByStockInId(stockInId);
        Long warehouseId = old.getWarehouseId();

        // 1) 同步库存台账：按 materialId + warehouseId + batchNo 增加库存
        for (WmsStockInLine line : lines) {
            increaseInventory(warehouseId, line);
        }

        // 2) 采购入库：反写 SCM 采购订单行 receivedQty，推进 PO 主单 status
        applyPoReceivedDelta(lines, +1);

        // 2b) 销售退货入库：回写 SO 行 returnedQty + 推进 sal_sales_return 状态 3→4
        applySoReturnedDelta(lines, +1);

        // 3) 追加应付单自动生成（通过ApplicationEvent解耦WMS↔SCM循环依赖，幂等由SCM侧保证）
        try { eventPublisher.publishEvent(new StockInConfirmedEvent(this, stockInId)); }
        catch (Exception ex) { log.error("confirmStockIn 发布入库确认事件失败 stockInId={}", stockInId, ex); throw ex; }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelStockIn(Long stockInId) {
        WmsStockIn old = requireStatus(stockInId, 3);
        // 状态 3 → 2（回退到已审核，可再次确认）
        old.setStatus(2);
        stockInMapper.updateById(old);

        List<WmsStockInLine> lines = lineMapper.selectByStockInId(stockInId);
        Long warehouseId = old.getWarehouseId();

        // 1) 库存台账回滚：按 materialId + warehouseId + batchNo 减少库存
        for (WmsStockInLine line : lines) {
            decreaseInventory(warehouseId, line);
        }

        // 2) 采购入库回滚：SCM 采购订单行 receivedQty 回滚，PO 主单状态回退
        applyPoReceivedDelta(lines, -1);

        // 2b) 销售退货入库回滚：SO 行 returnedQty 回退 + sal_sales_return 状态 4→3
        applySoReturnedDelta(lines, -1);
        return true;
    }

    /* ============== 私有工具 ============== */

    /** 库存增加：存在则累加 quantity，不存在则新增 */
    private void increaseInventory(Long warehouseId, WmsStockInLine line) {
        inventoryService.increaseInventory(warehouseId, line.getMaterialId(), line.getBatchNo(),
                line.getQuantity(), line.getUnit());
    }

    /** 库存扣减（cancelStockIn 使用）；找不到库存或可用量不足仅 warn，不阻塞，保持幂等 */
    private void decreaseInventory(Long warehouseId, WmsStockInLine line) {
        inventoryService.decreaseInventory(warehouseId, line.getMaterialId(), line.getBatchNo(), line.getQuantity());
    }

    /* ============== PO 行 receivedQty 反写 + PO 主单状态机 ============== */

    /**
     * 反写采购订单：
     *  - sign = +1：confirmStockIn 累加 receivedQty
     *  - sign = -1：cancelStockIn 扣减 receivedQty
     *  执行策略（按优先级命中即停）：
     *    1) 若 wmsStockInLine.poLineId 非空 —— 精准反写该行
     *    2) 否则若 wmsStockInLine.poId 非空 + materialId —— 在该 PO 下按 materialId 行按 lineNo 升序
     *       『贪心轮询』分配到 receivedQty 尚未到 orderQty 的行（收到相同物料多 PO 行场景）
     *    3) 否则若 deliveryLineId 非空 —— 走 scm_delivery_note_line → 取 po_line_id / delivery.po_id
     *       再回到 1/2
     *    4) 否则 log 后跳过（非采购类入库，例如盘点/调拨入库）
     *
     * 最后，对涉及到的每个 PO 汇总 ΣreceivedQty 对比 ΣorderQty：
     *    - Σreceived <= 0           → status = 2 (已审核 / 回到未到货)
     *    - Σreceived >= ΣorderQty   → status = 5 (全部到货)
     *    - 中间态                   → status = 4 (部分到货)
     * 仅当旧状态在 {2,4,5} 集合中才推进/回退（保留草稿/审核/取消等人工维护状态不被覆盖）。
     */
    private void applyPoReceivedDelta(List<WmsStockInLine> lines, int sign) {
        if (lines == null || lines.isEmpty()) return;
        Set<Long> touchedPoIds = new HashSet<>();

        for (WmsStockInLine l : lines) {
            if (l.getQuantity() == null || BigDecimal.ZERO.compareTo(l.getQuantity()) == 0) continue;
            BigDecimal delta = l.getQuantity().multiply(BigDecimal.valueOf(sign));

            Long poLineId = l.getPoLineId();
            Long poId = l.getPoId();
            Long materialId = l.getMaterialId();

            // 3) 路径：按 deliveryLineId 回填 poLineId / poId
            if (poLineId == null && poId == null && l.getDeliveryLineId() != null) {
                Map<String, Object> info = queryDeliverySource(l.getDeliveryLineId());
                if (info != null) {
                    poLineId = (Long) info.get("poLineId");
                    if (poLineId == null) {
                        poId = (Long) info.get("poId");
                    }
                }
            }

            if (poLineId != null) {
                // 1) 精准命中 PO 行
                adjustPoLineReceived(poLineId, delta);
                Long resolvedPoId = selectPoIdByLineId(poLineId);
                if (resolvedPoId != null) touchedPoIds.add(resolvedPoId);
            } else if (poId != null && materialId != null) {
                // 2) 按物料在 PO 下所有行贪心分配（按 lineNo 升序，避免超过 orderQty）
                greedyAssignToPoLines(poId, materialId, delta, touchedPoIds);
            } else {
                // 4) 不属于PO场景，跳过
                log.debug("[applyPoReceivedDelta] 跳过非PO入库明细 lineId={}", l.getLineId());
            }
        }

        // PO 主单 status 推进 / 回退
        for (Long poId : touchedPoIds) recomputePoStatus(poId);
    }

    /** 调整某个 PO Line 的 receivedQty（使用数据库原子 ±delta；收到值不可<0、>orderQty时允许超过 orderQty，仅日志） */
    private void adjustPoLineReceived(Long poLineId, BigDecimal delta) {
        if (poLineId == null || delta == null || BigDecimal.ZERO.compareTo(delta) == 0) return;
        int rows = jdbcTemplate.update(
                "UPDATE scm_purchase_order_line SET received_qty = GREATEST(0, COALESCE(received_qty, 0) + ?) WHERE line_id = ?",
                delta, poLineId);
        if (rows == 0) log.warn("[adjustPoLineReceived] 未找到PO行 lineId={}", poLineId);
    }

    /** 从 PO 下该物料的候选行集合（received<order 优先，升序 lineNo）贪心 ±delta */
    private void greedyAssignToPoLines(Long poId, Long materialId, BigDecimal delta, Set<Long> touched) {
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                "SELECT line_id, order_qty o, COALESCE(received_qty, 0) r FROM scm_purchase_order_line " +
                        "WHERE po_id=? AND material_id=? ORDER BY line_no ASC",
                poId, materialId);
        if (candidates.isEmpty()) {
            log.warn("[greedyAssignToPoLines] PO下未找到物料行 poId={} materialId={}", poId, materialId);
            return;
        }
        BigDecimal remain = delta;
        if (delta.signum() > 0) {
            // +：按 received<order 优先级分配；若全部已满，仍分摊到首行（避免丢失，保持与订单超额到货惯例一致，同时写日志）
            for (Map<String, Object> c : candidates) {
                if (remain.signum() <= 0) break;
                BigDecimal order = toDecimal(c.get("o"));
                BigDecimal recv = toDecimal(c.get("r"));
                BigDecimal cap = order.subtract(recv);
                if (cap.signum() <= 0) continue;
                BigDecimal use = remain.min(cap);
                adjustPoLineReceived((Long) c.get("line_id"), use);
                remain = remain.subtract(use);
            }
            // 仍剩余（超过总订货量）：落到首行
            if (remain.signum() > 0) {
                adjustPoLineReceived((Long) candidates.get(0).get("line_id"), remain);
            }
        } else {
            // -：按 received>0 反向扣减（顺序仍然是 lineNo 升序）
            for (Map<String, Object> c : candidates) {
                if (remain.signum() >= 0) break;
                BigDecimal recv = toDecimal(c.get("r"));
                if (recv.signum() <= 0) continue;
                // use = min(recv, abs(remain))，结果是负数：use.negate()
                BigDecimal use = recv.min(remain.abs());
                if (use.signum() <= 0) continue;
                adjustPoLineReceived((Long) c.get("line_id"), use.negate());
                remain = remain.add(use);
            }
        }
        touched.add(poId);
    }

    private Long selectPoIdByLineId(Long lineId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT po_id FROM scm_purchase_order_line WHERE line_id=?", Long.class, lineId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 查 scm_delivery_note_line + 主单 poId 作为链路回退 */
    private Map<String, Object> queryDeliverySource(Long deliveryLineId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT l.po_line_id poLineId, d.po_id poId " +
                            "FROM scm_delivery_note_line l " +
                            "JOIN scm_delivery_note d ON d.delivery_id = l.delivery_id " +
                            "WHERE l.line_id=?", deliveryLineId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("[queryDeliverySource] 查询到货链路失败 lineId={} msg={}", deliveryLineId, e.getMessage());
            return null;
        }
    }

    /** 按 receivedQty 汇总回写 PO 主单 status（仅在 2/4/5 之间切换） */
    private void recomputePoStatus(Long poId) {
        if (poId == null) return;
        Map<String, Object> agg;
        try {
            agg = jdbcTemplate.queryForMap(
                    "SELECT COALESCE(SUM(order_qty), 0) so, COALESCE(SUM(received_qty), 0) sr, " +
                            "       (SELECT status FROM scm_purchase_order WHERE po_id=?) cur " +
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
        // 仅在 2=已审核 / 4=部分到货 / 5=全部到货 范围内切换，其它状态（如草稿/已下单/已驳回/取消）人工维护不覆盖
        if (!Arrays.asList(2, 4, 5).contains(cur)) return;

        int next;
        if (sumReceived.signum() <= 0) next = 2;
        else if (sumReceived.compareTo(sumOrder) >= 0 && sumOrder.signum() > 0) next = 5;
        else next = 4;

        if (next != cur) {
            jdbcTemplate.update("UPDATE scm_purchase_order SET status=? WHERE po_id=?", next, poId);
            log.info("[recomputePoStatus] PO={}  {} → {}  (order={} received={})", poId, cur, next, sumOrder, sumReceived);
        }
    }

    /* ============== SO 行 returnedQty 反写 + sal_sales_return 状态推进 ============== */
    //
    //  说明：SO 主单 status 仅按 shippedQty 重算（与 PO 仅按 receivedQty 判定对称），
    //  returnedQty 只作为退货量跟踪，不影响 SO 状态流转；故本方法只调整行 returned_qty，
    //  不调用 recomputeSoStatus（recomputeSoStatus 由 WmsStockOutServiceImpl.applySoShippedDelta 维护）。
    /**
     * sign = +1：confirmStockIn 累加 SO 行 returned_qty，并将 sal_sales_return 置为"待质检"(status=3, qc_status=0)
     * sign = -1：cancelStockIn 扣减 SO 行 returned_qty，并回退 sal_sales_return 4→3 且清质检结论
     * 质检合格后由 SAL 模块 submitQc 推进 status 3→4（WMS 不直接到4，留质检拦截点）
     * 仅处理 returnLineId 非空的入库明细（销售退货类）；其他类型跳过。
     * 通过 JdbcTemplate 直写 sal_sales_order_line / sal_sales_return，WMS 不依赖 SAL 模块。
     */
    private void applySoReturnedDelta(List<WmsStockInLine> lines, int sign) {
        if (lines == null || lines.isEmpty()) return;
        Set<Long> touchedReturnIds = new HashSet<>();

        for (WmsStockInLine l : lines) {
            BigDecimal qty = toDecimal(l.getQuantity());
            if (qty.signum() == 0) continue;
            Long returnLineId = l.getReturnLineId();
            if (returnLineId == null) {
                log.debug("[applySoReturnedDelta] 跳过非销售退货入库明细 lineId={}", l.getLineId());
                continue;
            }
            BigDecimal delta = qty.multiply(BigDecimal.valueOf(sign));
            // 精准回写 SO 行 returned_qty（优先 soLineId；为空跳过——无 SO 关联的纯退货）
            if (l.getSoLineId() != null) {
                adjustSoLineReturned(l.getSoLineId(), delta);
            } else {
                log.debug("[applySoReturnedDelta] 入库明细缺 soLineId，跳过 returned_qty 回写 lineId={}", l.getLineId());
            }
            if (l.getReturnId() != null) touchedReturnIds.add(l.getReturnId());
        }
        // sal_sales_return 状态推进（幂等：仅 3↔4 之间切换）
        for (Long returnId : touchedReturnIds) advanceSalReturnStatus(returnId, sign);
    }

    private void adjustSoLineReturned(Long soLineId, BigDecimal delta) {
        if (soLineId == null || delta == null || delta.signum() == 0) return;
        int rows = jdbcTemplate.update(
                "UPDATE sal_sales_order_line SET returned_qty = GREATEST(0, COALESCE(returned_qty,0) + ?) WHERE line_id = ?",
                delta, soLineId);
        if (rows == 0) log.warn("[adjustSoLineReturned] 未找到SO行 lineId={}", soLineId);
    }

    /**
     * 退货状态机（含质检拦截点）：
     * sign=+1 → status=3(入库中) 时置 qc_status=0(待检)，不推进4；质检由 SAL 模块 submitQc 完成 3→4
     * sign=-1 → status=4(已完成) 回退到 3 并清质检结论(qc_status/qc_by/qc_time/disposition 置NULL)，需重新质检
     * 幂等：若 qc_status 已有结论(1/2/3) 则 sign=+1 不覆盖；status 不符则不动。
     */
    private void advanceSalReturnStatus(Long returnId, int sign) {
        if (returnId == null) return;
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap("SELECT status, qc_status FROM sal_sales_return WHERE return_id=?", returnId);
        } catch (Exception e) {
            log.warn("[advanceSalReturnStatus] 退货单不存在或查询失败 returnId={} msg={}", returnId, e.getMessage());
            return;
        }
        Integer cur = row.get("status") == null ? null : ((Number) row.get("status")).intValue();
        if (cur == null) return;
        Object qcObj = row.get("qc_status");
        Integer qc = qcObj == null ? null : ((Number) qcObj).intValue();
        if (sign > 0) {
            if (cur != 3) return; // 仅入库中停留
            if (qc == null) {
                jdbcTemplate.update("UPDATE sal_sales_return SET qc_status=0 WHERE return_id=?", returnId);
                log.info("[advanceSalReturnStatus] Return={} 入库确认置待检 qc_status=0 (停3,待SAL质检)", returnId);
            }
        } else {
            if (cur != 4) return; // 仅已完成→入库中
            jdbcTemplate.update("UPDATE sal_sales_return SET status=3, qc_status=NULL, qc_by=NULL, qc_time=NULL, disposition=NULL WHERE return_id=?", returnId);
            log.info("[advanceSalReturnStatus] Return={} 4→3 回退并清质检结论", returnId);
        }
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(o.toString()); } catch (Exception ignore) { return BigDecimal.ZERO; }
    }

    /** 计算总数量与总金额，回填到主表 */
    private void calcTotals(WmsStockIn stockIn) {
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (stockIn.getLines() != null) {
            for (WmsStockInLine line : stockIn.getLines()) {
                BigDecimal qty = line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity();
                BigDecimal price = line.getUnitPrice() == null ? BigDecimal.ZERO : line.getUnitPrice();
                BigDecimal amt = line.getAmount();
                if (amt == null) {
                    amt = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
                    line.setAmount(amt);
                }
                totalQty = totalQty.add(qty);
                totalAmount = totalAmount.add(amt);
            }
        }
        stockIn.setTotalQty(totalQty);
        stockIn.setTotalAmount(totalAmount);
    }

    /** 批量保存明细，回填 stockInId */
    @Transactional(rollbackFor = Exception.class)
    public void saveLines(Long stockInId, List<WmsStockInLine> lines) {
        for (WmsStockInLine line : lines) {
            line.setLineId(null);
            line.setStockInId(stockInId);
            if (line.getQuantity() == null || line.getQuantity().signum() <= 0) throw new ServiceException("入库数量必须大于0");
            BigDecimal qty = line.getQuantity();
            BigDecimal price = line.getUnitPrice() == null ? BigDecimal.ZERO : line.getUnitPrice();
            if (line.getAmount() == null) {
                line.setAmount(qty.multiply(price).setScale(2, RoundingMode.HALF_UP));
            }
            lineMapper.insert(line);
        }
    }

    /** 状态校验，返回当前实体 */
    private WmsStockIn requireStatus(Long stockInId, Integer... expected) {
        WmsStockIn old = stockInMapper.selectForUpdate(stockInId);
        if (old == null) throw new ServiceException("入库单不存在");
        Set<Integer> exp = java.util.Arrays.stream(expected).collect(Collectors.toSet());
        if (!exp.contains(old.getStatus())) {
            throw new ServiceException("当前入库单状态不允许该操作");
        }
        return old;
    }
}