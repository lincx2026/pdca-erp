package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.*;
import com.pcba.erp.scm.mapper.*;
import com.pcba.erp.scm.service.IFinVoucherService;
import com.pcba.erp.scm.service.IScmApPayableLineService;
import com.pcba.erp.scm.service.IScmApPayableService;
import com.pcba.erp.scm.service.IScmApPaymentPlanService;
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
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmApPayableServiceImpl implements IScmApPayableService {

    private final ScmApPayableMapper mapper;
    private final ScmSupplierMapper supplierMapper;
    private final ScmApPayableLineMapper lineMapper;
    private final ScmPurchaseOrderLineMapper poLineMapper;
    private final ScmPurchaseOrderMapper purchaseOrderMapper;
    private final IScmApPayableLineService lineService;
    private final IScmApPaymentPlanService planService;
    private final JdbcTemplate jdbcTemplate;
    @Lazy private final IFinVoucherService voucherService;

    // ---------- CRUD 基础 ----------
    @Override
    public IPage<ScmApPayable> selectPage(int pageNum, int pageSize, String payableNo, Long supplierId, String supplierName, Integer status, String bizDate, String dueDate, Integer isOverdue) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), payableNo, supplierId, supplierName, status, bizDate, dueDate, isOverdue);
    }

    @Override
    public ScmApPayable getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmApPayable r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmApPayable entity) {
        try {
            Field f = ScmApPayable.class.getDeclaredField("payableNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank())
                f.set(entity, "AP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        } catch (Exception ignore) {}
        try {
            Field f = ScmApPayable.class.getDeclaredField("bizDate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        try {
            Field f = ScmApPayable.class.getDeclaredField("dueDate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        try {
            Field f = ScmApPayable.class.getDeclaredField("status"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 0);
        } catch (Exception ignore) {}
        try {
            Field f = ScmApPayable.class.getDeclaredField("currency"); f.setAccessible(true);
            Object v = f.get(entity); if (v == null || v.toString().isBlank()) f.set(entity, "CNY");
        } catch (Exception ignore) {}
        try {
            Field f = ScmApPayable.class.getDeclaredField("exchangeRate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, BigDecimal.ONE);
        } catch (Exception ignore) {}
        for (String fn : new String[]{"undiscAmount","discountAmount","roundingAmount","adjustmentAmount","taxAmount","totalAmount","cnyTotalAmount","paidAmount","unpaidAmount"}) {
            try {
                Field f = ScmApPayable.class.getDeclaredField(fn); f.setAccessible(true);
                if (f.get(entity) == null && f.getType() == BigDecimal.class) f.set(entity, BigDecimal.ZERO);
            } catch (Exception ignore) {}
        }
        try {
            Field f = ScmApPayable.class.getDeclaredField("creditDays"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 0);
        } catch (Exception ignore) {}
        // 供方信息兜底
        if (entity.getSupplierId() != null && (entity.getSupplierCode() == null || entity.getSupplierName() == null)) {
            ScmSupplier s = supplierMapper.selectById(entity.getSupplierId());
            if (s != null) {
                if (entity.getSupplierCode() == null) entity.setSupplierCode(s.getSupplierCode());
                if (entity.getSupplierName() == null) entity.setSupplierName(s.getSupplierName());
                if (entity.getCreditDays() == null || entity.getCreditDays() == 0) {
                    Integer cd = s.getCreditDays() == null ? 0 : s.getCreditDays();
                    entity.setCreditDays(cd);
                }
            }
        }
        if (mapper.countDuplicate(entity.getPayableNo(), entity.getPayableId()) > 0)
            throw new ServiceException("编号已存在: " + entity.getPayableNo());
        // overdue 标志初始化
        recomputeOverdueFlag(entity);
        mapper.insert(entity);
        return entity.getPayableId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmApPayable entity) {
        if (entity.getPayableId() == null) throw new ServiceException("ID不能为空");
        ScmApPayable old = mapper.selectById(entity.getPayableId());
        if (old == null) throw new ServiceException("记录不存在");
        if (entity.getPayableNo() != null && !entity.getPayableNo().isBlank()
                && mapper.countDuplicate(entity.getPayableNo(), entity.getPayableId()) > 0)
            throw new ServiceException("编号已存在");
        recomputeOverdueFlag(entity);
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmApPayable r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        if (r.getPaidAmount() != null && r.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
            throw new ServiceException("已存在付款记录，无法删除；请先作废");
        Long lc = lineMapper.selectCount(new LambdaQueryWrapper<ScmApPayableLine>().eq(ScmApPayableLine::getPayableId, id));
        if (lc != null && lc > 0) lineMapper.delete(new LambdaQueryWrapper<ScmApPayableLine>().eq(ScmApPayableLine::getPayableId, id));
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<ScmApPayable> options() {
        return mapper.selectList(new LambdaQueryWrapper<ScmApPayable>().orderByDesc(ScmApPayable::getPayableId));
    }

    // ====================================================================
    // T2B-1 生成入库应付 (强化)
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> generateFromStockIn(Long stockInId) {
        if (stockInId == null) throw new ServiceException("stockInId不能为空");

        // 1) 读取 WMS 入库单 + 明细 (包含 payable_line_id 已回写字段)
        List<Map<String, Object>> headers = jdbcTemplate.queryForList(
                "SELECT stock_in_id, order_no AS stock_in_no, create_time AS stock_in_date, " +
                        "warehouse_id, supplier_id, in_type, " +
                        "total_amount, status, create_time " +
                        "FROM wms_stock_in WHERE stock_in_id = ?", stockInId);
        if (headers.isEmpty()) throw new ServiceException("WMS入库单不存在: " + stockInId);
        Map<String, Object> h = headers.get(0);

        if (!"PURCHASE".equals(h.get("in_type"))) return Collections.emptyList();

        // 前置校验：必须status=3(已入库)
        Integer status = toInteger(h.get("status"));
        if (status == null || status != 3) {
            throw new ServiceException("入库单未确认无法生成应付");
        }

        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
                "SELECT l.line_id AS stock_in_line_id, l.stock_in_id, l.line_id AS line_no, " +
                        "l.material_id, l.material_code, l.material_name, l.specification, l.unit, " +
                        "l.quantity AS qty, l.unit_price, l.batch_no, l.po_id, l.po_line_id, " +
                        "l.delivery_id, l.delivery_line_id, l.payable_id, l.payable_line_id " +
                        "FROM wms_stock_in_line l WHERE l.stock_in_id = ? ORDER BY l.line_id", stockInId);
        if (lines.isEmpty()) {
            log.warn("入库单{}无明细，不生成应付", stockInId);
            return Collections.emptyList();
        }

        // 2) 幂等逐行过滤：payableLineId 非空直接跳过
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Map<String, Object> l : lines) {
            if (toLong(l.get("payable_line_id")) != null) {
                log.info("入库单{}行{}已关联应付行{},跳过", stockInId, l.get("stock_in_line_id"), l.get("payable_line_id"));
                continue;
            }
            pending.add(l);
        }
        if (pending.isEmpty()) {
            log.info("入库单{}所有明细已生成应付，幂等跳过", stockInId);
            return Collections.emptyList();
        }

        // 3) 供应商识别：优先 poId -> ScmPurchaseOrder.supplierId (跨供应商抛错)
        SupplierResolved sr = resolveSupplierFromStockIn(h, pending);
        Long supplierId = sr.supplierId;
        ScmSupplier supplier = sr.supplier;
        String supplierCode = supplier != null ? supplier.getSupplierCode() : null;
        String supplierName = supplier != null ? supplier.getSupplierName() : null;

        // 4) 计算 creditDays + dueDate (基于 Supplier + PO.paymentTerm fallback)
        int creditDays = resolveCreditDays(supplier, pending);
        LocalDate bizDate = toLocalDate(firstNonNull(h.get("stock_in_date"), h.get("create_time")));
        if (bizDate == null) bizDate = LocalDate.now();
        LocalDate dueDate = bizDate.plusDays(creditDays);

        // 5) 头合并：supplierId + dueDate + bizType=NORMAL + status<9
        final String bizType = "NORMAL";
        ScmApPayable header = findMergeablePayable(supplierId, dueDate, bizType);
        boolean isNew = header == null;
        if (isNew) {
            header = new ScmApPayable();
            header.setBizType(bizType);
            header.setSupplierId(supplierId);
            header.setSupplierCode(supplierCode);
            header.setSupplierName(supplierName);
            header.setBizDate(bizDate);
            header.setCreditDays(creditDays);
            header.setDueDate(dueDate);
            header.setCurrency("CNY");
            header.setExchangeRate(BigDecimal.ONE);
            header.setStatus(0);
            header.setRemark("入库确认自动生成");
            this.save(header);
        }

        // 6) 构建应付行 & 幂等回写 wms_stock_in_line.payable_id / payable_line_id
        BigDecimal headerUndisc = nvl(header.getUndiscAmount());
        BigDecimal headerTax = nvl(header.getTaxAmount());
        BigDecimal headerTotal = nvl(header.getTotalAmount());
        BigDecimal headerCny = nvl(header.getCnyTotalAmount());

        for (Map<String, Object> line : pending) {
            LineMeta m = buildLineMeta(line, BigDecimal.ONE);
            ScmApPayableLine ln = new ScmApPayableLine();
            ln.setPayableId(header.getPayableId());
            ln.setLineSrc(bizType);
            ln.setStockInId(stockInId);
            ln.setStockInLineId(toLong(line.get("stock_in_line_id")));
            ln.setDeliveryId(toLong(line.get("delivery_id")));
            ln.setDeliveryLineId(toLong(line.get("delivery_line_id")));
            ln.setPoId(toLong(line.get("po_id")));
            ln.setPoLineId(toLong(line.get("po_line_id")));
            ln.setMaterialId(toLong(line.get("material_id")));
            ln.setMaterialCode((String) line.get("material_code"));
            ln.setMaterialName((String) line.get("material_name"));
            ln.setSpecification((String) line.get("specification"));
            ln.setUnit((String) line.get("unit"));
            ln.setQty(m.qty);
            ln.setUnitPrice(m.unitPrice);
            ln.setTaxRate(m.taxRate);
            ln.setUndiscAmount(m.undiscAmount);
            ln.setTaxAmount(m.taxAmount);
            ln.setLineAmount(m.lineAmount);
            ln.setBatchNo((String) line.get("batch_no"));
            lineService.save(ln);

            // 幂等回写：只有 payable_line_id 为 NULL 的行才允许更新
            Long stockInLineId = toLong(line.get("stock_in_line_id"));
            int upd = jdbcTemplate.update(
                    "UPDATE wms_stock_in_line SET payable_id=?, payable_line_id=? " +
                            "WHERE line_id=? AND payable_line_id IS NULL",
                    header.getPayableId(), ln.getLineId(), stockInLineId);
            if (upd == 0) {
                log.warn("入库单行{}幂等冲突：并发/重复调用已先行回写payable_line_id，跳过", stockInLineId);
                // 已存在则不参与累加（防止重复生成）
                continue;
            }

            headerUndisc = headerUndisc.add(m.undiscAmount);
            headerTax = headerTax.add(m.taxAmount);
            headerTotal = headerTotal.add(m.lineAmount);
            headerCny = headerCny.add(m.lineAmount);
        }

        header.setUndiscAmount(headerUndisc);
        header.setTaxAmount(headerTax);
        header.setTotalAmount(headerTotal);
        header.setCnyTotalAmount(headerCny);
        BigDecimal unpaid = headerCny.subtract(nvl(header.getPaidAmount()));
        if (unpaid.compareTo(BigDecimal.ZERO) < 0) unpaid = BigDecimal.ZERO;
        header.setUnpaidAmount(unpaid);
        // originStockInIds 去重追加
        header.setOriginStockInIds(appendDistinctId(header.getOriginStockInIds(), stockInId));
        recomputeOverdueFlag(header);
        mapper.updateById(header);
        return Collections.singletonList(header.getPayableId());
    }

    // ====================================================================
    // T2B-2 红字应付 (出库-采购退货)
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> generateFromReturnOut(Long stockOutId) {
        if (stockOutId == null) throw new ServiceException("stockOutId不能为空");

        List<Map<String, Object>> headers = jdbcTemplate.queryForList(
                "SELECT stock_out_id, order_no AS stock_out_no, create_time AS stock_out_date, " +
                        "warehouse_id, status, create_time, out_type " +
                        "FROM wms_stock_out WHERE stock_out_id = ?", stockOutId);
        if (headers.isEmpty()) throw new ServiceException("WMS出库单不存在: " + stockOutId);
        Map<String, Object> h = headers.get(0);

        // 必须是 RETURN 类型 + status=3
        String outType = h.get("out_type") == null ? null : h.get("out_type").toString();
        if (!"RETURN".equalsIgnoreCase(outType == null ? "" : outType)
                && !"PURCHASE_RETURN".equalsIgnoreCase(outType)) {
            log.info("出库单{} out_type={} 非采购退货，跳过红字应付生成", stockOutId, outType);
            return Collections.emptyList();
        }
        Integer status = toInteger(h.get("status"));
        if (status == null || status != 3) {
            throw new ServiceException("退货出库单未确认无法生成红字应付");
        }

        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
                "SELECT l.line_id AS stock_out_line_id, l.stock_out_id, l.line_id AS line_no, " +
                        "l.material_id, l.material_code, l.material_name, l.specification, l.unit, " +
                        "l.quantity AS qty, NULL::numeric AS unit_price, l.batch_no, l.po_id, l.po_line_id, " +
                        "l.return_id, l.return_line_id, l.delivery_id, l.delivery_line_id, " +
                        "l.payable_id, l.payable_line_id " +
                        "FROM wms_stock_out_line l WHERE l.stock_out_id = ? ORDER BY l.line_id", stockOutId);
        if (lines.isEmpty()) {
            log.warn("出库单{}无明细，跳过", stockOutId);
            return Collections.emptyList();
        }

        // 幂等：已关联payable_line_id的行跳过
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Map<String, Object> l : lines) {
            if (toLong(l.get("payable_line_id")) != null) {
                log.info("出库单{}行{}已关联应付行，跳过", stockOutId, l.get("stock_out_line_id"));
                continue;
            }
            pending.add(l);
        }
        if (pending.isEmpty()) {
            log.info("出库单{}所有行均已生成红字应付，幂等跳过", stockOutId);
            return Collections.emptyList();
        }

        // 简单幂等：已存在 bizType=RETURN 且 origin 包含该出库id则跳过
        Long existed = lineMapper.selectCount(new LambdaQueryWrapper<ScmApPayableLine>()
                .eq(ScmApPayableLine::getStockOutId, stockOutId));
        if (existed != null && existed > 0) {
            log.info("出库单{}已生成过红字应付（通过stock_out_id查重），跳过", stockOutId);
            return Collections.emptyList();
        }

        // 供应商识别：优先 stock_out.poId → po.supplierId，逐行校验一致
        SupplierResolved sr = resolveSupplierFromStockOut(h, pending);
        Long supplierId = sr.supplierId;
        ScmSupplier supplier = sr.supplier;
        String supplierCode = supplier != null ? supplier.getSupplierCode() : null;
        String supplierName = supplier != null ? supplier.getSupplierName() : null;

        int creditDays = resolveCreditDays(supplier, pending);
        LocalDate bizDate = toLocalDate(firstNonNull(h.get("stock_out_date"), h.get("create_time")));
        if (bizDate == null) bizDate = LocalDate.now();
        LocalDate dueDate = bizDate.plusDays(creditDays);

        final String bizType = "RETURN";
        ScmApPayable header = findMergeablePayable(supplierId, dueDate, bizType);
        boolean isNew = header == null;
        if (isNew) {
            header = new ScmApPayable();
            header.setBizType(bizType);
            header.setSupplierId(supplierId);
            header.setSupplierCode(supplierCode);
            header.setSupplierName(supplierName);
            header.setBizDate(bizDate);
            header.setCreditDays(creditDays);
            header.setDueDate(dueDate);
            header.setCurrency("CNY");
            header.setExchangeRate(BigDecimal.ONE);
            header.setStatus(0);
            header.setRemark("采购退货确认自动生成红字应付");
            this.save(header);
        }

        BigDecimal headerUndisc = nvl(header.getUndiscAmount());
        BigDecimal headerTax = nvl(header.getTaxAmount());
        BigDecimal headerTotal = nvl(header.getTotalAmount());
        BigDecimal headerCny = nvl(header.getCnyTotalAmount());
        BigDecimal factor = new BigDecimal("-1");

        for (Map<String, Object> line : pending) {
            LineMeta m = buildLineMeta(line, factor);
            ScmApPayableLine ln = new ScmApPayableLine();
            ln.setPayableId(header.getPayableId());
            ln.setLineSrc(bizType);
            ln.setStockOutId(stockOutId);
            ln.setStockOutLineId(toLong(line.get("stock_out_line_id")));
            ln.setReturnId(toLong(line.get("return_id")));
            ln.setReturnLineId(toLong(line.get("return_line_id")));
            ln.setDeliveryId(toLong(line.get("delivery_id")));
            ln.setDeliveryLineId(toLong(line.get("delivery_line_id")));
            ln.setPoId(toLong(line.get("po_id")));
            ln.setPoLineId(toLong(line.get("po_line_id")));
            ln.setMaterialId(toLong(line.get("material_id")));
            ln.setMaterialCode((String) line.get("material_code"));
            ln.setMaterialName((String) line.get("material_name"));
            ln.setSpecification((String) line.get("specification"));
            ln.setUnit((String) line.get("unit"));
            ln.setQty(m.qty);
            ln.setUnitPrice(m.unitPrice);
            ln.setTaxRate(m.taxRate);
            ln.setUndiscAmount(m.undiscAmount);
            ln.setTaxAmount(m.taxAmount);
            ln.setLineAmount(m.lineAmount);
            ln.setBatchNo((String) line.get("batch_no"));
            lineService.save(ln);

            // 尝试回写 stock_out_line (字段不存在则跳过)
            try {
                int upd = jdbcTemplate.update(
                        "UPDATE wms_stock_out_line SET payable_id=?, payable_line_id=? " +
                                "WHERE line_id=? AND payable_line_id IS NULL",
                        header.getPayableId(), ln.getLineId(), toLong(line.get("stock_out_line_id")));
                if (upd == 0) {
                    log.warn("退货出库行{}幂等冲突(或无payable字段)，跳过回写", line.get("stock_out_line_id"));
                }
            } catch (Exception ex) {
                // 若列不存在则跳过不报错
                log.info("wms_stock_out_line 可能未添加 payable 回写字段，跳过回写: {}", ex.getMessage());
            }

            headerUndisc = headerUndisc.add(m.undiscAmount);
            headerTax = headerTax.add(m.taxAmount);
            headerTotal = headerTotal.add(m.lineAmount);
            headerCny = headerCny.add(m.lineAmount);
        }

        header.setUndiscAmount(headerUndisc);
        header.setTaxAmount(headerTax);
        header.setTotalAmount(headerTotal);
        header.setCnyTotalAmount(headerCny);
        BigDecimal unpaid = headerCny.subtract(nvl(header.getPaidAmount())); // 红字 unpaid 可为负
        header.setUnpaidAmount(unpaid);
        recomputeOverdueFlag(header);
        mapper.updateById(header);
        return Collections.singletonList(header.getPayableId());
    }

    // ====================================================================
    // T2B-3 对账 + 自动推送凭证 (若 voucherId==null)
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reconcileManual(Long payableId, String reconcileBy) {
        ScmApPayable p = require(payableId);
        if (p.getStatus() != null && (p.getStatus() == 4 || p.getStatus() == 9))
            throw new ServiceException("当前状态不允许对账");
        // 状态 0/1 → 2
        Integer st = p.getStatus() == null ? 0 : p.getStatus();
        if (st == 0 || st == 1) p.setStatus(2);
        p.setReconcileBy(reconcileBy);
        LocalDateTime now = LocalDateTime.now();
        p.setReconcileTime(now);
        boolean ok = mapper.updateById(p) > 0;
        if (ok) {
            // 幂等：已存在voucherId 就跳过
            if (p.getVoucherId() == null) {
                try {
                    Long vid = voucherService.pushPayableVoucher(payableId);
                    if (vid != null) {
                        p.setVoucherId(vid);
                        mapper.updateById(p);
                    }
                } catch (Exception ex) {
                    log.error("对账后推送应付凭证失败 payableId={}", payableId, ex);
                }
            } else {
                log.info("应付单{}已存在voucherId={}，幂等跳过推送凭证", payableId, p.getVoucherId());
            }
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelPayable(Long payableId) {
        ScmApPayable p = require(payableId);
        if (p.getPaidAmount() != null && p.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
            throw new ServiceException("已发生付款，先取消付款单再作废");
        p.setStatus(9);
        return mapper.updateById(p) > 0;
    }

    // ---------- 付款确认/回滚(供PaymentService调用) ----------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean applyPayment(Long payableId, BigDecimal cnyPaidAmount, Long paymentId) {
        if (payableId == null) return true;
        ScmApPayable p = mapper.selectById(payableId);
        if (p == null) { log.warn("applyPayment payable不存在: {}", payableId); return false; }
        BigDecimal pay = cnyPaidAmount == null ? BigDecimal.ZERO : cnyPaidAmount;
        BigDecimal paid = p.getPaidAmount() == null ? BigDecimal.ZERO : p.getPaidAmount();
        BigDecimal cnyTotal = p.getCnyTotalAmount() == null ? BigDecimal.ZERO : p.getCnyTotalAmount();
        BigDecimal newPaid = paid.add(pay);
        p.setPaidAmount(newPaid);
        BigDecimal unpaid = cnyTotal.subtract(newPaid);
        if (unpaid.compareTo(BigDecimal.ZERO) < 0) unpaid = BigDecimal.ZERO;
        p.setUnpaidAmount(unpaid);
        // 状态：0/1/2 → 3 部分付；unpaid=0 → 4 完成
        int st = p.getStatus() == null ? 0 : p.getStatus();
        if (unpaid.compareTo(BigDecimal.ZERO) <= 0) p.setStatus(4);
        else if (st == 0 || st == 1 || st == 2) p.setStatus(3);
        return mapper.updateById(p) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackPayment(Long payableId, BigDecimal cnyPaidAmount, Long paymentId) {
        if (payableId == null) return true;
        ScmApPayable p = mapper.selectById(payableId);
        if (p == null) { log.warn("rollbackPayment payable不存在: {}", payableId); return false; }
        BigDecimal pay = cnyPaidAmount == null ? BigDecimal.ZERO : cnyPaidAmount;
        BigDecimal paid = p.getPaidAmount() == null ? BigDecimal.ZERO : p.getPaidAmount();
        BigDecimal cnyTotal = p.getCnyTotalAmount() == null ? BigDecimal.ZERO : p.getCnyTotalAmount();
        BigDecimal newPaid = paid.subtract(pay);
        if (newPaid.compareTo(BigDecimal.ZERO) < 0) newPaid = BigDecimal.ZERO;
        p.setPaidAmount(newPaid);
        BigDecimal unpaid = cnyTotal.subtract(newPaid).max(BigDecimal.ZERO);
        p.setUnpaidAmount(unpaid);
        int st = p.getStatus() == null ? 0 : p.getStatus();
        if (newPaid.compareTo(BigDecimal.ZERO) <= 0) {
            if (st == 3 || st == 4) p.setStatus(2);
        } else {
            if (st == 4) p.setStatus(3);
        }
        return mapper.updateById(p) > 0;
    }

    // ====================================================================
    // T2B-4 分期计划 (强化)
    // ====================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean generatePlans(Long payableId, List<Map<String, Object>> phases) {
        ScmApPayable p = require(payableId);
        BigDecimal total = p.getCnyTotalAmount() == null ? BigDecimal.ZERO : p.getCnyTotalAmount();
        final BigDecimal total_ = total;

        // 默认 [100]
        if (phases == null || phases.isEmpty()) {
            phases = new ArrayList<>();
            Map<String, Object> once = new HashMap<>();
            once.put("phaseNo", 1);
            once.put("ratio", 100);
            once.put("dueDateOffset", 0);
            once.put("remark", "一次性");
            phases.add(once);
        }

        // 1) Σratio == 100.00 (允许 0.01 差异由最后一期补差)
        BigDecimal sumRatio = BigDecimal.ZERO;
        for (Map<String, Object> ph : phases) {
            BigDecimal r = toBigDecimal(ph.get("ratio"));
            sumRatio = sumRatio.add(r == null ? BigDecimal.ZERO : r);
        }
        BigDecimal diff100 = sumRatio.subtract(new BigDecimal("100"));
        if (diff100.abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new ServiceException("分期比例之和必须为100，当前为 " + sumRatio.toPlainString());
        }

        // 2) 已存在(phaseNo存在)就更新，否则 insert；按 phaseNo 排序
        List<ScmApPaymentPlan> olds = planService.listByPayable(payableId);
        Map<Integer, ScmApPaymentPlan> oldByPhase = new HashMap<>();
        if (olds != null) {
            for (ScmApPaymentPlan o : olds) {
                if (o.getPhaseNo() != null) oldByPhase.put(o.getPhaseNo(), o);
            }
        }

        // 按 phaseNo 升序处理
        List<Map<String, Object>> sorted = new ArrayList<>(phases);
        sorted.sort(Comparator.comparingInt(m -> {
            Integer n = toInteger(m.get("phaseNo"));
            return n == null ? Integer.MAX_VALUE : n;
        }));

        BigDecimal cumPlan = BigDecimal.ZERO;
        int n = sorted.size();
        for (int i = 0; i < n; i++) {
            Map<String, Object> ph = sorted.get(i);
            Integer phaseNo = toInteger(ph.get("phaseNo"));
            if (phaseNo == null) phaseNo = (i + 1);
            BigDecimal ratio = toBigDecimal(ph.get("ratio"));
            if (ratio == null) ratio = BigDecimal.ZERO;
            // dueDateOffset
            Integer offset = toInteger(ph.get("dueDateOffset"));
            if (offset == null) offset = 0;
            LocalDate planDate = p.getDueDate() == null ? LocalDate.now() : p.getDueDate().plusDays(offset);
            String remark = ph.get("remark") == null ? null : ph.get("remark").toString();
            String phaseName = ph.get("phaseName") == null ? ("第" + phaseNo + "期") : ph.get("phaseName").toString();

            // 金额：最后一期补差
            BigDecimal planAmount;
            if (i == n - 1) {
                planAmount = total_.subtract(cumPlan);
            } else {
                planAmount = total_.multiply(ratio).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                cumPlan = cumPlan.add(planAmount);
            }

            ScmApPaymentPlan plan = oldByPhase.get(phaseNo);
            if (plan == null) {
                plan = new ScmApPaymentPlan();
                plan.setPayableId(payableId);
                plan.setPayableNo(p.getPayableNo());
                plan.setSupplierId(p.getSupplierId());
                plan.setSupplierName(p.getSupplierName());
                plan.setPhaseNo(phaseNo);
                plan.setStatus(0);
                plan.setWriteOffStatus(0);
                plan.setPaidAmount(BigDecimal.ZERO);
                plan.setActualAmount(BigDecimal.ZERO);
                fillPayeeFromSupplier(plan, p.getSupplierId());
            }
            plan.setPhaseName(phaseName);
            plan.setRatio(ratio);
            plan.setPlanDate(planDate);
            plan.setPlanAmount(planAmount);
            plan.setUnpaidAmount(planAmount.max(BigDecimal.ZERO));
            if (remark != null) plan.setRemark(remark);
            if (plan.getWriteOffStatus() == null) plan.setWriteOffStatus(0);
            if (plan.getPaidAmount() == null) plan.setPaidAmount(BigDecimal.ZERO);

            if (plan.getPlanId() == null) planService.save(plan);
            else planService.update(plan);
        }

        // 3) 回写 payable.unpaidAmount & status
        List<ScmApPaymentPlan> plans = planService.listByPayable(payableId);
        BigDecimal sumPlanAmt = BigDecimal.ZERO;
        BigDecimal sumPaidAmt = BigDecimal.ZERO;
        if (plans != null) {
            for (ScmApPaymentPlan pl : plans) {
                sumPlanAmt = sumPlanAmt.add(nvl(pl.getPlanAmount()));
                sumPaidAmt = sumPaidAmt.add(nvl(pl.getPaidAmount()));
            }
        }
        BigDecimal unpaid = sumPlanAmt.subtract(sumPaidAmt).max(BigDecimal.ZERO);
        p.setUnpaidAmount(unpaid);
        int st = p.getStatus() == null ? 0 : p.getStatus();
        if (sumPaidAmt.compareTo(total) >= 0 && total.compareTo(BigDecimal.ZERO) >= 0) {
            p.setStatus(4);
        } else if (st == 2) {
            if (sumPaidAmt.compareTo(BigDecimal.ZERO) > 0) p.setStatus(3);
            // 否则保持 2 不变
        }
        mapper.updateById(p);
        return true;
    }

    private void fillPayeeFromSupplier(ScmApPaymentPlan plan, Long supplierId) {
        if (supplierId == null) return;
        ScmSupplier s = supplierMapper.selectById(supplierId);
        if (s == null) return;
        if (plan.getPayeeName() == null) plan.setPayeeName(s.getPayeeName() != null ? s.getPayeeName() : s.getSupplierName());
        if (plan.getPayeeBank() == null) plan.setPayeeBank(s.getPayeeBank());
        if (plan.getPayeeAccount() == null) plan.setPayeeAccount(s.getPayeeAccount());
        if (plan.getPayeeSwift() == null) plan.setPayeeSwift(s.getPayeeSwift());
    }

    // ---------- 信用额度管控 ----------
    @Override public BigDecimal sumUnpaid(Long supplierId) {
        BigDecimal v = mapper.sumUnpaid(supplierId);
        return v == null ? BigDecimal.ZERO : v;
    }
    @Override public BigDecimal sumOverdue(Long supplierId) {
        BigDecimal v = mapper.sumOverdue(supplierId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public void checkCreditLimit(Long supplierId, BigDecimal newOrderCnyAmount) {
        if (supplierId == null) return;
        ScmSupplier s = supplierMapper.selectById(supplierId);
        if (s == null) return;
        // 1) 逾期
        BigDecimal overdue = sumOverdue(supplierId);
        if (overdue.compareTo(BigDecimal.ZERO) > 0)
            throw new ServiceException("供方存在逾期未付金额 " + overdue + " CNY，请先处理逾期再下单");
        // 2) 额度
        BigDecimal limit = s.getCreditLimit() == null ? BigDecimal.ZERO : s.getCreditLimit();
        if (limit.compareTo(BigDecimal.ZERO) <= 0) return; // 0=不控额度
        BigDecimal unpaid = sumUnpaid(supplierId);
        BigDecimal add = newOrderCnyAmount == null ? BigDecimal.ZERO : newOrderCnyAmount;
        if (unpaid.add(add).compareTo(limit) > 0)
            throw new ServiceException(String.format("供方信用额度不足：额度%s，未付%s，新单%s，合计超限",
                    limit.toPlainString(), unpaid.toPlainString(), add.toPlainString()));
    }

    // ---------- 看板 ----------
    @Override
    public Map<String, Object> getKanbanSummary(Long supplierId) {
        Map<String,Object> r = new HashMap<>();
        r.put("unpaidTotal", sumUnpaid(supplierId));
        r.put("overdueTotal", sumOverdue(supplierId));
        r.put("dueThisMonth", mapper.sumDueThisMonth(supplierId));
        Map<String,Object> more = mapper.sumMore(supplierId);
        if (more != null) r.putAll(more);
        return r;
    }
    @Override public List<Map<String,Object>> getTopSuppliers(Long supplierId, int limit) { return mapper.topSuppliers(supplierId, limit); }
    @Override public List<Map<String,Object>> getAgingBuckets(Long supplierId) { return mapper.agingBuckets(supplierId); }

    // ====================================================================
    // 内部工具方法
    // ====================================================================
    private ScmApPayable require(Long id) {
        ScmApPayable p = mapper.selectById(id);
        if (p == null) throw new ServiceException("应付单不存在: " + id);
        return p;
    }
    private void recomputeOverdueFlag(ScmApPayable p) {
        if (p.getDueDate() == null) { p.setIsOverdue(0); p.setOverdueDays(0); return; }
        int days = (int) LocalDate.now().toEpochDay() - (int) p.getDueDate().toEpochDay();
        if (days > 0) { p.setIsOverdue(1); p.setOverdueDays(days); }
        else { p.setIsOverdue(0); p.setOverdueDays(0); }
    }

    /**
     * 供应商识别：入库单
     *  优先 poId -> ScmPurchaseOrder.supplierId，多行 supplier 必须一致
     *  否则 fallback: stockIn.returnId / soId / deliveryId -> 关联表识别
     */
    private SupplierResolved resolveSupplierFromStockIn(Map<String, Object> h, List<Map<String, Object>> pending) {
        Long headSupplier = toLong(h.get("supplier_id"));
        Long theSupplier = null;
        Set<Long> suppliers = new LinkedHashSet<>();
        boolean hasAnyPo = false;
        for (Map<String, Object> line : pending) {
            Long poId = toLong(line.get("po_id"));
            if (poId != null) {
                hasAnyPo = true;
                ScmPurchaseOrder po = purchaseOrderMapper.selectById(poId);
                if (po != null && po.getSupplierId() != null) {
                    suppliers.add(po.getSupplierId());
                }
            }
        }
        if (suppliers.size() > 1) {
            throw new ServiceException("无法跨供应商合并应付: " + suppliers);
        }
        if (!suppliers.isEmpty()) {
            theSupplier = suppliers.iterator().next();
        }
        // fallback 路径
        if (theSupplier == null) {
            Long deliveryId = toLong(h.get("delivery_id"));
            if (deliveryId != null) {
                List<Map<String,Object>> rows = jdbcTemplate.queryForList(
                        "SELECT supplier_id FROM scm_delivery_note WHERE delivery_id = ?", deliveryId);
                if (!rows.isEmpty()) theSupplier = toLong(rows.get(0).get("supplier_id"));
            }
        }
        if (theSupplier == null) {
            Long returnId = toLong(h.get("return_id"));
            if (returnId != null) {
                List<Map<String,Object>> rows = jdbcTemplate.queryForList(
                        "SELECT supplier_id FROM scm_purchase_return WHERE return_id = ?", returnId);
                if (!rows.isEmpty()) theSupplier = toLong(rows.get(0).get("supplier_id"));
            }
        }
        if (theSupplier == null) {
            Long soId = toLong(h.get("so_id"));
            if (soId != null) {
                // so → 从 sal_order (若存在) 或 scm_delivery_note / scm_purchase_return 间接识别
                try {
                    List<Map<String,Object>> rows = jdbcTemplate.queryForList(
                            "SELECT customer_id AS supplier_id FROM sal_order WHERE so_id = ?", soId);
                    if (!rows.isEmpty()) theSupplier = toLong(rows.get(0).get("supplier_id"));
                } catch (Exception ignore) {}
            }
        }
        if (theSupplier == null && headSupplier != null) {
            theSupplier = headSupplier;
        }
        if (theSupplier == null) {
            throw new ServiceException("入库缺少供应商上下文，无法生成应付");
        }
        ScmSupplier supplier = supplierMapper.selectById(theSupplier);
        return new SupplierResolved(theSupplier, supplier);
    }

    /** 供应商识别：出库单 (退货) */
    private SupplierResolved resolveSupplierFromStockOut(Map<String, Object> h, List<Map<String, Object>> pending) {
        Long headSupplier = toLong(h.get("supplier_id"));
        Long headPoId = toLong(h.get("po_id"));
        Set<Long> suppliers = new LinkedHashSet<>();
        // 优先 head.poId
        if (headPoId != null) {
            ScmPurchaseOrder po = purchaseOrderMapper.selectById(headPoId);
            if (po != null && po.getSupplierId() != null) suppliers.add(po.getSupplierId());
        }
        // 逐行 poId 校验一致
        for (Map<String, Object> line : pending) {
            Long poId = toLong(line.get("po_id"));
            if (poId != null) {
                ScmPurchaseOrder po = purchaseOrderMapper.selectById(poId);
                if (po != null && po.getSupplierId() != null) suppliers.add(po.getSupplierId());
            }
        }
        if (suppliers.size() > 1) throw new ServiceException("无法跨供应商合并红字应付: " + suppliers);
        Long theSupplier = suppliers.isEmpty() ? null : suppliers.iterator().next();
        if (theSupplier == null) {
            Long returnId = toLong(h.get("return_id"));
            if (returnId != null) {
                List<Map<String,Object>> rows = jdbcTemplate.queryForList(
                        "SELECT supplier_id FROM scm_purchase_return WHERE return_id = ?", returnId);
                if (!rows.isEmpty()) theSupplier = toLong(rows.get(0).get("supplier_id"));
            }
        }
        if (theSupplier == null && headSupplier != null) theSupplier = headSupplier;
        if (theSupplier == null) throw new ServiceException("退货出库缺少供应商上下文，无法生成红字应付");
        ScmSupplier supplier = supplierMapper.selectById(theSupplier);
        return new SupplierResolved(theSupplier, supplier);
    }

    /** 计算 creditDays：supplier.creditDays > PO.paymentTerm 映射 > fallback 30 */
    private int resolveCreditDays(ScmSupplier supplier, List<Map<String, Object>> pending) {
        if (supplier != null && supplier.getCreditDays() != null && supplier.getCreditDays() > 0) {
            return supplier.getCreditDays();
        }
        // 取第一行非空 poId 的 paymentTerm 作为代表
        String pt = null;
        if (supplier != null && supplier.getPaymentTerms() != null) pt = supplier.getPaymentTerms();
        for (Map<String, Object> line : pending) {
            Long poId = toLong(line.get("po_id"));
            if (poId != null) {
                ScmPurchaseOrder po = purchaseOrderMapper.selectById(poId);
                if (po != null && po.getPaymentTerm() != null && !po.getPaymentTerm().isBlank()) {
                    pt = po.getPaymentTerm();
                    break;
                }
            }
        }
        int d = mapPaymentTerm(pt);
        return d > 0 ? d : 30;
    }
    private int mapPaymentTerm(String pt) {
        if (pt == null) return 0;
        String s = pt.trim().toUpperCase();
        if (s.contains("月结30") || s.equals("NET30")) return 30;
        if (s.contains("月结45") || s.equals("NET45")) return 45;
        if (s.contains("月结60") || s.equals("NET60")) return 60;
        if (s.contains("月结90") || s.equals("NET90")) return 90;
        if (s.contains("货到付款") || s.equals("COD")) return 0;
        return 0;
    }

    /** 查找可合并的应付单：supplier+dueDate+bizType，status<9 */
    private ScmApPayable findMergeablePayable(Long supplierId, LocalDate dueDate, String bizType) {
        try {
            return mapper.selectOne(new LambdaQueryWrapper<ScmApPayable>()
                    .eq(ScmApPayable::getSupplierId, supplierId)
                    .eq(ScmApPayable::getDueDate, dueDate)
                    .eq(ScmApPayable::getBizType, bizType)
                    .lt(ScmApPayable::getStatus, 9)
                    .orderByDesc(ScmApPayable::getPayableId).last("LIMIT 1"));
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 构建行金额 (poLine.unitPrice / taxRate 优先)；factor 可为 -1 用于退货 */
    private LineMeta buildLineMeta(Map<String, Object> line, BigDecimal factor) {
        LineMeta m = new LineMeta();
        Long poLineId = toLong(line.get("po_line_id"));
        Long poId = toLong(line.get("po_id"));
        Long materialId = toLong(line.get("material_id"));
        BigDecimal qty = toBigDecimal(line.get("qty"));
        BigDecimal unitPrice = toBigDecimal(line.get("unit_price"));
        BigDecimal discountRate = BigDecimal.ZERO;
        BigDecimal taxRate = BigDecimal.ZERO;
        if (poLineId != null) {
            ScmPurchaseOrderLine poL = poLineMapper.selectById(poLineId);
            if (poL != null) {
                if (poL.getUnitPrice() != null && poL.getUnitPrice().compareTo(BigDecimal.ZERO) != 0) {
                    unitPrice = poL.getUnitPrice();
                }
                if (poL.getDiscountRate() != null) discountRate = poL.getDiscountRate();
                if (poL.getTaxRate() != null) taxRate = poL.getTaxRate();
            }
        } else if (poId != null && materialId != null) {
            try {
                ScmPurchaseOrderLine poL = poLineMapper.selectOne(new LambdaQueryWrapper<ScmPurchaseOrderLine>()
                        .eq(ScmPurchaseOrderLine::getPoId, poId)
                        .eq(ScmPurchaseOrderLine::getMaterialId, materialId)
                        .last("LIMIT 1"));
                if (poL != null) {
                    if (poL.getUnitPrice() != null && (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0)) {
                        unitPrice = poL.getUnitPrice();
                    }
                    if (poL.getDiscountRate() != null) discountRate = poL.getDiscountRate();
                    if (poL.getTaxRate() != null) taxRate = poL.getTaxRate();
                }
            } catch (Exception ignore) {}
        }
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        BigDecimal q = qty == null ? BigDecimal.ZERO : qty;
        BigDecimal undisc = q.multiply(unitPrice)
                .multiply(BigDecimal.ONE.subtract(discountRate))
                .multiply(factor);
        BigDecimal taxAmt = undisc.multiply(taxRate);
        m.qty = q.multiply(factor);
        m.unitPrice = unitPrice;
        m.discountRate = discountRate;
        m.taxRate = taxRate;
        m.undiscAmount = undisc;
        m.taxAmount = taxAmt;
        m.lineAmount = undisc.add(taxAmt);
        return m;
    }

    private String appendDistinctId(String origin, Long id) {
        if (id == null) return origin;
        String idStr = String.valueOf(id);
        Set<String> set = new LinkedHashSet<>();
        if (origin != null && !origin.isBlank()) {
            set.addAll(Arrays.asList(origin.split(",")));
        }
        set.add(idStr);
        return String.join(",", set);
    }

    private static class LineMeta {
        BigDecimal qty;
        BigDecimal unitPrice;
        BigDecimal discountRate;
        BigDecimal taxRate;
        BigDecimal undiscAmount;
        BigDecimal taxAmount;
        BigDecimal lineAmount;
    }
    private static class SupplierResolved {
        final Long supplierId;
        final ScmSupplier supplier;
        SupplierResolved(Long id, ScmSupplier s) { this.supplierId = id; this.supplier = s; }
    }

    private static Integer toInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception ignore) { return null; }
    }
    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number)o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception ignore) { return null; }
    }
    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal)o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception ignore) { return null; }
    }
    private static BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate) return (LocalDate)o;
        if (o instanceof LocalDateTime) return ((LocalDateTime)o).toLocalDate();
        if (o instanceof java.sql.Date) return ((java.sql.Date)o).toLocalDate();
        if (o instanceof java.util.Date) return ((java.util.Date)o).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        try { return LocalDate.parse(o.toString()); } catch (Exception ignore) { return null; }
    }
    @SafeVarargs
    private static <T> T firstNonNull(T... ts) {
        if (ts == null) return null;
        for (T t : ts) if (t != null) return t;
        return null;
    }
}
