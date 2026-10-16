package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.*;
import com.pcba.erp.sal.mapper.*;
import com.pcba.erp.sal.service.ISalArReceivableLineService;
import com.pcba.erp.sal.service.ISalArReceivableService;
import com.pcba.erp.sal.service.ISalArReceiptPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * SAL应收单服务
 * 简化版(模块独立)：手工生成应收单(不使用事件驱动)；对账推进状态→2；收款确认回写 received_amount。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalArReceivableServiceImpl implements ISalArReceivableService {

    private final SalArReceivableMapper mapper;
    private final SalCustomerMapper customerMapper;
    private final SalArReceivableLineMapper lineMapper;
    private final ISalArReceivableLineService lineService;
    private final ISalArReceiptPlanService planService;
    private final JdbcTemplate jdbcTemplate;

    // ---------- CRUD 基础 ----------
    @Override
    public IPage<SalArReceivable> selectPage(int pageNum, int pageSize, String receivableNo, Long customerId, String customerName, Integer status, String bizDate, String dueDate, Integer isOverdue) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), receivableNo, customerId, customerName, status, bizDate, dueDate, isOverdue);
    }

    @Override
    public SalArReceivable getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalArReceivable r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalArReceivable entity) {
        try {
            Field f = SalArReceivable.class.getDeclaredField("receivableNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank())
                f.set(entity, "AR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        } catch (Exception ignore) {}
        try {
            Field f = SalArReceivable.class.getDeclaredField("bizDate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        try {
            Field f = SalArReceivable.class.getDeclaredField("dueDate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        try {
            Field f = SalArReceivable.class.getDeclaredField("status"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 0);
        } catch (Exception ignore) {}
        try {
            Field f = SalArReceivable.class.getDeclaredField("currency"); f.setAccessible(true);
            Object v = f.get(entity); if (v == null || v.toString().isBlank()) f.set(entity, "CNY");
        } catch (Exception ignore) {}
        try {
            Field f = SalArReceivable.class.getDeclaredField("exchangeRate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, BigDecimal.ONE);
        } catch (Exception ignore) {}
        for (String fn : new String[]{"undiscAmount","discountAmount","roundingAmount","adjustmentAmount","taxAmount","totalAmount","cnyTotalAmount","receivedAmount","unreceivedAmount"}) {
            try {
                Field f = SalArReceivable.class.getDeclaredField(fn); f.setAccessible(true);
                if (f.get(entity) == null && f.getType() == BigDecimal.class) f.set(entity, BigDecimal.ZERO);
            } catch (Exception ignore) {}
        }
        try {
            Field f = SalArReceivable.class.getDeclaredField("creditDays"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 0);
        } catch (Exception ignore) {}
        // 客户信息兜底
        if (entity.getCustomerId() != null && (entity.getCustomerCode() == null || entity.getCustomerName() == null)) {
            SalCustomer c = customerMapper.selectById(entity.getCustomerId());
            if (c != null) {
                if (entity.getCustomerCode() == null) entity.setCustomerCode(c.getCustomerCode());
                if (entity.getCustomerName() == null) entity.setCustomerName(c.getCustomerName());
                if (entity.getCreditDays() == null || entity.getCreditDays() == 0) {
                    Integer cd = c.getCreditDays() == null ? 0 : c.getCreditDays();
                    entity.setCreditDays(cd);
                }
            }
        }
        if (mapper.countDuplicate(entity.getReceivableNo(), entity.getReceivableId()) > 0)
            throw new ServiceException("编号已存在: " + entity.getReceivableNo());
        // overdue 标志初始化
        recomputeOverdueFlag(entity);
        mapper.insert(entity);
        return entity.getReceivableId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalArReceivable entity) {
        if (entity.getReceivableId() == null) throw new ServiceException("ID不能为空");
        SalArReceivable old = mapper.selectById(entity.getReceivableId());
        if (old == null) throw new ServiceException("记录不存在");
        if (entity.getReceivableNo() != null && !entity.getReceivableNo().isBlank()
                && mapper.countDuplicate(entity.getReceivableNo(), entity.getReceivableId()) > 0)
            throw new ServiceException("编号已存在");
        recomputeOverdueFlag(entity);
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        SalArReceivable r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        if (r.getReceivedAmount() != null && r.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0)
            throw new ServiceException("已存在收款记录，无法删除；请先作废");
        Long lc = lineMapper.selectCount(new LambdaQueryWrapper<SalArReceivableLine>().eq(SalArReceivableLine::getReceivableId, id));
        if (lc != null && lc > 0) lineMapper.delete(new LambdaQueryWrapper<SalArReceivableLine>().eq(SalArReceivableLine::getReceivableId, id));
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<SalArReceivable> options() {
        return mapper.selectList(new LambdaQueryWrapper<SalArReceivable>().orderByDesc(SalArReceivable::getReceivableId));
    }

    // ---------- 事件驱动：WMS 销售出库确认 → 自动生成应收单 ----------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateFromSalesOut(Long stockOutId) {
        if (stockOutId == null) throw new ServiceException("stockOutId 不能为空");

        // 1. 查出库主单 + 校验类型
        Map<String, Object> out;
        try {
            out = jdbcTemplate.queryForMap(
                    "SELECT stock_out_id, order_no, out_type, customer_id, customer_name " +
                            "FROM wms_stock_out WHERE stock_out_id=?", stockOutId);
        } catch (Exception e) {
            log.warn("[generateFromSalesOut] 出库单不存在 stockOutId={} msg={}", stockOutId, e.getMessage());
            return null;
        }
        String outType = out.get("out_type") == null ? null : out.get("out_type").toString();
        if (!"SALES".equals(outType)) {
            log.debug("[generateFromSalesOut] 非销售出库(outType={})，跳过 stockOutId={}", outType, stockOutId);
            return null;
        }

        // 2. 幂等：已存在同 stockOutId 的应收行 → 直接返回既有 receivableId
        try {
            Long exist = jdbcTemplate.queryForObject(
                    "SELECT receivable_id FROM sal_ar_receivable_line WHERE stock_out_id=? LIMIT 1",
                    Long.class, stockOutId);
            if (exist != null) {
                log.info("[generateFromSalesOut] 已存在应收单 receivableId={}，跳过 stockOutId={}", exist, stockOutId);
                return exist;
            }
        } catch (Exception ignore) {
            // EmptyResult → 无既有记录，继续生成
        }

        // 3. 查出库明细
        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
                "SELECT line_id, so_id, so_line_id, material_id, material_code, material_name, " +
                        "specification, unit, quantity, batch_no " +
                        "FROM wms_stock_out_line WHERE stock_out_id=? ORDER BY line_id ASC", stockOutId);
        if (lines.isEmpty()) {
            log.warn("[generateFromSalesOut] 出库单明细为空 stockOutId={}", stockOutId);
            return null;
        }

        // 4. 取关联 SO（首行 so_id）→ 客户/币种/税率/信用期/币种汇率
        Long soId = null;
        for (Map<String, Object> l : lines) {
            if (l.get("so_id") != null) { soId = ((Number) l.get("so_id")).longValue(); break; }
        }
        Map<String, Object> so = null;
        if (soId != null) {
            try {
                so = jdbcTemplate.queryForMap(
                        "SELECT so_id, so_no, customer_id, customer_code, customer_name, currency, " +
                                "exchange_rate, tax_rate FROM sal_sales_order WHERE so_id=?", soId);
            } catch (Exception e) {
                log.warn("[generateFromSalesOut] SO 不存在 soId={} msg={}", soId, e.getMessage());
            }
        }
        Long customerId = so != null && so.get("customer_id") != null ? ((Number) so.get("customer_id")).longValue()
                : (out.get("customer_id") == null ? null : ((Number) out.get("customer_id")).longValue());
        String customerName = so != null && so.get("customer_name") != null ? so.get("customer_name").toString()
                : (out.get("customer_name") == null ? null : out.get("customer_name").toString());
        String currency = so != null && so.get("currency") != null ? so.get("currency").toString() : "CNY";
        BigDecimal exchangeRate = so != null && so.get("exchange_rate") != null
                ? toDec(so.get("exchange_rate")) : BigDecimal.ONE;
        BigDecimal soTaxRate = so != null && so.get("tax_rate") != null ? toDec(so.get("tax_rate")) : BigDecimal.ZERO;

        // 5. 构造应收主单（出库即对账完成 → status=2）
        SalArReceivable ar = new SalArReceivable();
        ar.setReceivableNo("AR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        ar.setBizType("SALES");
        ar.setCustomerId(customerId);
        ar.setCustomerCode(so != null && so.get("customer_code") != null ? so.get("customer_code").toString() : null);
        ar.setCustomerName(customerName);
        ar.setCurrency(currency);
        ar.setExchangeRate(exchangeRate);
        ar.setBizDate(LocalDate.now());
        ar.setDueDate(LocalDate.now()); // 兜底，下方按 creditDays 修正
        ar.setStatus(2); // 出库即对账完成，可直接进入收款流程
        ar.setReconcileBy("system");
        ar.setReconcileTime(LocalDateTime.now());
        ar.setRemark("来自销售出库: " + (so != null && so.get("so_no") != null ? so.get("so_no") : out.get("order_no")));

        // 6. 构造明细行 + 累加金额（价格取自 SO 行 unit_price/tax_rate）
        BigDecimal sumUndisc = BigDecimal.ZERO, sumTax = BigDecimal.ZERO;
        List<SalArReceivableLine> recvLines = new ArrayList<>(lines.size());
        int lineNo = 1;
        for (Map<String, Object> l : lines) {
            BigDecimal qty = toDec(l.get("quantity"));
            if (qty.signum() <= 0) continue;
            Long soLineId = l.get("so_line_id") == null ? null : ((Number) l.get("so_line_id")).longValue();
            BigDecimal unitPrice = BigDecimal.ZERO, taxRate = soTaxRate;
            if (soLineId != null) {
                try {
                    Map<String, Object> sol = jdbcTemplate.queryForMap(
                            "SELECT unit_price, tax_rate FROM sal_sales_order_line WHERE line_id=?", soLineId);
                    unitPrice = toDec(sol.get("unit_price"));
                    BigDecimal tr = toDec(sol.get("tax_rate"));
                    if (tr.signum() != 0) taxRate = tr;
                } catch (Exception ignore) { }
            }
            BigDecimal undisc = qty.multiply(unitPrice);
            BigDecimal tax = undisc.multiply(taxRate);
            BigDecimal lineAmt = undisc.add(tax);

            SalArReceivableLine rl = new SalArReceivableLine();
            rl.setLineNo(lineNo++);
            rl.setLineSrc("SALES_OUT");
            rl.setStockOutId(stockOutId);
            rl.setStockOutLineId(l.get("line_id") == null ? null : ((Number) l.get("line_id")).longValue());
            rl.setSoId(l.get("so_id") == null ? soId : ((Number) l.get("so_id")).longValue());
            rl.setSoLineId(soLineId);
            rl.setMaterialId(l.get("material_id") == null ? null : ((Number) l.get("material_id")).longValue());
            rl.setMaterialCode(l.get("material_code") == null ? null : l.get("material_code").toString());
            rl.setMaterialName(l.get("material_name") == null ? null : l.get("material_name").toString());
            rl.setSpecification(l.get("specification") == null ? null : l.get("specification").toString());
            rl.setUnit(l.get("unit") == null ? null : l.get("unit").toString());
            rl.setQty(qty);
            rl.setUnitPrice(unitPrice);
            rl.setTaxRate(taxRate);
            rl.setUndiscAmount(undisc);
            rl.setTaxAmount(tax);
            rl.setLineAmount(lineAmt);
            rl.setBatchNo(l.get("batch_no") == null ? null : l.get("batch_no").toString());
            recvLines.add(rl);

            sumUndisc = sumUndisc.add(undisc);
            sumTax = sumTax.add(tax);
        }
        if (recvLines.isEmpty()) {
            log.warn("[generateFromSalesOut] 无可用明细(全零数量) stockOutId={}", stockOutId);
            return null;
        }
        BigDecimal sumLine = sumUndisc.add(sumTax);
        ar.setUndiscAmount(sumUndisc);
        ar.setTaxAmount(sumTax);
        ar.setTotalAmount(sumLine);
        ar.setDiscountAmount(BigDecimal.ZERO);
        ar.setRoundingAmount(BigDecimal.ZERO);
        ar.setAdjustmentAmount(BigDecimal.ZERO);
        ar.setCnyTotalAmount(sumLine.multiply(exchangeRate));
        ar.setReceivedAmount(BigDecimal.ZERO);
        ar.setUnreceivedAmount(sumLine);

        // 7. 信用期 dueDate（优先客户表 credit_days）
        Integer creditDays = 0;
        if (customerId != null) {
            SalCustomer c = customerMapper.selectById(customerId);
            if (c != null && c.getCreditDays() != null) creditDays = c.getCreditDays();
        }
        ar.setCreditDays(creditDays);
        ar.setDueDate(LocalDate.now().plusDays(creditDays));
        recomputeOverdueFlag(ar);

        // 8. 保存主单 + 明细
        Long receivableId = save(ar);
        for (SalArReceivableLine rl : recvLines) {
            rl.setReceivableId(receivableId);
            lineMapper.insert(rl);
        }
        log.info("[generateFromSalesOut] SO={} stockOutId={} → 应收单 receivableId={} 金额={} 行数={}",
                soId, stockOutId, receivableId, sumLine, recvLines.size());
        return receivableId;
    }

    // ---------- 事件驱动：WMS 销售退货入库确认 → 自动生成红字应收单（冲销） ----------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateFromSalesReturnStockIn(Long stockInId) {
        if (stockInId == null) throw new ServiceException("stockInId 不能为空");

        // 1. 查入库主单 + 校验类型
        Map<String, Object> in;
        try {
            in = jdbcTemplate.queryForMap(
                    "SELECT stock_in_id, order_no, in_type, warehouse_id FROM wms_stock_in WHERE stock_in_id=?", stockInId);
        } catch (Exception e) {
            log.warn("[generateFromSalesReturnStockIn] 入库单不存在 stockInId={} msg={}", stockInId, e.getMessage());
            return null;
        }
        String inType = in.get("in_type") == null ? null : in.get("in_type").toString();
        if (!"RETURN".equals(inType)) {
            log.debug("[generateFromSalesReturnStockIn] 非退货入库(inType={})，跳过 stockInId={}", inType, stockInId);
            return null;
        }

        // 2. 查入库明细
        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
                "SELECT line_id, return_id, return_line_id, so_id, so_line_id, material_id, material_code, material_name, " +
                        "specification, unit, quantity, batch_no, unit_price " +
                        "FROM wms_stock_in_line WHERE stock_in_id=? ORDER BY line_id ASC", stockInId);
        if (lines.isEmpty()) {
            log.warn("[generateFromSalesReturnStockIn] 入库单明细为空 stockInId={}", stockInId);
            return null;
        }

        // 仅处理含 return_id 的明细（销售退货链路）；无 return_id 的非销售退货入库跳过
        Long returnId = null;
        for (Map<String, Object> l : lines) {
            if (l.get("return_id") != null) { returnId = ((Number) l.get("return_id")).longValue(); break; }
        }
        if (returnId == null) {
            log.debug("[generateFromSalesReturnStockIn] 入库明细无 return_id（非销售退货），跳过 stockInId={}", stockInId);
            return null;
        }

        // 3. 幂等：已存在同 returnId 的红字应收行 → 直接返回既有 receivableId
        try {
            Long exist = jdbcTemplate.queryForObject(
                    "SELECT receivable_id FROM sal_ar_receivable_line WHERE return_id=? LIMIT 1",
                    Long.class, returnId);
            if (exist != null) {
                log.info("[generateFromSalesReturnStockIn] 已存在红字应收单 receivableId={}，跳过 stockInId={}", exist, stockInId);
                return exist;
            }
        } catch (Exception ignore) {
            // EmptyResult → 无既有记录，继续生成
        }

        // 4. 取关联退货单 + SO（客户/币种/税率）
        Map<String, Object> ret = null;
        try {
            ret = jdbcTemplate.queryForMap(
                    "SELECT return_id, return_no, so_id, customer_id, customer_code, customer_name, currency " +
                            "FROM sal_sales_return WHERE return_id=?", returnId);
        } catch (Exception e) {
            log.warn("[generateFromSalesReturnStockIn] 退货单不存在 returnId={} msg={}", returnId, e.getMessage());
            return null;
        }
        Long soId = ret.get("so_id") == null ? null : ((Number) ret.get("so_id")).longValue();
        Map<String, Object> so = null;
        if (soId != null) {
            try {
                so = jdbcTemplate.queryForMap(
                        "SELECT so_id, so_no, customer_id, customer_code, customer_name, currency, " +
                                "exchange_rate, tax_rate FROM sal_sales_order WHERE so_id=?", soId);
            } catch (Exception e) {
                log.warn("[generateFromSalesReturnStockIn] SO 不存在 soId={} msg={}", soId, e.getMessage());
            }
        }
        Long customerId = so != null && so.get("customer_id") != null ? ((Number) so.get("customer_id")).longValue()
                : (ret.get("customer_id") == null ? null : ((Number) ret.get("customer_id")).longValue());
        String customerCode = so != null && so.get("customer_code") != null ? so.get("customer_code").toString()
                : (ret.get("customer_code") == null ? null : ret.get("customer_code").toString());
        String customerName = so != null && so.get("customer_name") != null ? so.get("customer_name").toString()
                : (ret.get("customer_name") == null ? null : ret.get("customer_name").toString());
        String currency = so != null && so.get("currency") != null ? so.get("currency").toString()
                : (ret.get("currency") == null ? "CNY" : ret.get("currency").toString());
        BigDecimal exchangeRate = so != null && so.get("exchange_rate") != null
                ? toDec(so.get("exchange_rate")) : BigDecimal.ONE;
        if (exchangeRate == null || exchangeRate.signum() <= 0) exchangeRate = BigDecimal.ONE; // 兜底：0/负汇率取1
        BigDecimal soTaxRate = so != null && so.get("tax_rate") != null ? toDec(so.get("tax_rate")) : BigDecimal.ZERO;

        // 5. 构造红字应收主单（金额取负，冲销原蓝字应收；入库即对账完成 → status=2）
        SalArReceivable ar = new SalArReceivable();
        ar.setReceivableNo("AR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        ar.setBizType("SALES_RETURN");
        ar.setCustomerId(customerId);
        ar.setCustomerCode(customerCode);
        ar.setCustomerName(customerName);
        ar.setCurrency(currency);
        ar.setExchangeRate(exchangeRate);
        ar.setBizDate(LocalDate.now());
        ar.setDueDate(LocalDate.now()); // 兜底，下方按 creditDays 修正
        ar.setStatus(2); // 红字亦对账完成，可直接参与冲抵/退款
        ar.setReconcileBy("system");
        ar.setReconcileTime(LocalDateTime.now());
        ar.setRemark("来自销售退货入库: " + (ret.get("return_no") != null ? ret.get("return_no") : in.get("order_no")));

        // 6. 构造红字明细行 + 累加金额（价格取自 SO 行 unit_price/tax_rate；金额取负）
        BigDecimal sumUndisc = BigDecimal.ZERO, sumTax = BigDecimal.ZERO;
        List<SalArReceivableLine> recvLines = new ArrayList<>(lines.size());
        int lineNo = 1;
        for (Map<String, Object> l : lines) {
            if (l.get("return_id") == null) continue; // 仅处理销售退货链路行
            BigDecimal qty = toDec(l.get("quantity"));
            if (qty.signum() <= 0) continue;
            Long soLineId = l.get("so_line_id") == null ? null : ((Number) l.get("so_line_id")).longValue();
            BigDecimal unitPrice = toDec(l.get("unit_price"));
            BigDecimal taxRate = soTaxRate;
            // 优先 SO 行取价（红字应与原蓝字同价）
            if (soLineId != null) {
                try {
                    Map<String, Object> sol = jdbcTemplate.queryForMap(
                            "SELECT unit_price, tax_rate FROM sal_sales_order_line WHERE line_id=?", soLineId);
                    BigDecimal up = toDec(sol.get("unit_price"));
                    if (up.signum() != 0) unitPrice = up;
                    BigDecimal tr = toDec(sol.get("tax_rate"));
                    if (tr.signum() != 0) taxRate = tr;
                } catch (Exception ignore) { }
            }
            BigDecimal undisc = qty.multiply(unitPrice);
            BigDecimal tax = undisc.multiply(taxRate);
            BigDecimal lineAmt = undisc.add(tax);

            SalArReceivableLine rl = new SalArReceivableLine();
            rl.setLineNo(lineNo++);
            rl.setLineSrc("SALES_RETURN_IN");
            rl.setReturnId(l.get("return_id") == null ? returnId : ((Number) l.get("return_id")).longValue());
            rl.setReturnLineId(l.get("return_line_id") == null ? null : ((Number) l.get("return_line_id")).longValue());
            rl.setSoId(l.get("so_id") == null ? soId : ((Number) l.get("so_id")).longValue());
            rl.setSoLineId(soLineId);
            rl.setMaterialId(l.get("material_id") == null ? null : ((Number) l.get("material_id")).longValue());
            rl.setMaterialCode(l.get("material_code") == null ? null : l.get("material_code").toString());
            rl.setMaterialName(l.get("material_name") == null ? null : l.get("material_name").toString());
            rl.setSpecification(l.get("specification") == null ? null : l.get("specification").toString());
            rl.setUnit(l.get("unit") == null ? null : l.get("unit").toString());
            rl.setQty(qty);
            rl.setUnitPrice(unitPrice);
            rl.setTaxRate(taxRate);
            // 红字：金额取负
            rl.setUndiscAmount(undisc.negate());
            rl.setTaxAmount(tax.negate());
            rl.setLineAmount(lineAmt.negate());
            rl.setBatchNo(l.get("batch_no") == null ? null : l.get("batch_no").toString());
            recvLines.add(rl);

            sumUndisc = sumUndisc.add(undisc);
            sumTax = sumTax.add(tax);
        }
        if (recvLines.isEmpty()) {
            log.warn("[generateFromSalesReturnStockIn] 无可用退货明细(全零数量/无return_id) stockInId={}", stockInId);
            return null;
        }
        BigDecimal sumLine = sumUndisc.add(sumTax);
        // 红字主单金额取负
        ar.setUndiscAmount(sumUndisc.negate());
        ar.setTaxAmount(sumTax.negate());
        ar.setTotalAmount(sumLine.negate());
        ar.setDiscountAmount(BigDecimal.ZERO);
        ar.setRoundingAmount(BigDecimal.ZERO);
        ar.setAdjustmentAmount(BigDecimal.ZERO);
        ar.setCnyTotalAmount(sumLine.multiply(exchangeRate).negate());
        ar.setReceivedAmount(BigDecimal.ZERO);
        ar.setUnreceivedAmount(sumLine.negate()); // 负值=客户应付的退款/冲抵额

        // 7. 信用期 dueDate（优先客户表 credit_days）
        Integer creditDays = 0;
        if (customerId != null) {
            SalCustomer c = customerMapper.selectById(customerId);
            if (c != null && c.getCreditDays() != null) creditDays = c.getCreditDays();
        }
        ar.setCreditDays(creditDays);
        ar.setDueDate(LocalDate.now().plusDays(creditDays));
        recomputeOverdueFlag(ar);

        // 8. 保存主单 + 明细
        Long receivableId = save(ar);
        for (SalArReceivableLine rl : recvLines) {
            rl.setReceivableId(receivableId);
            lineMapper.insert(rl);
        }
        log.info("[generateFromSalesReturnStockIn] Return={} stockInId={} → 红字应收单 receivableId={} 金额={} 行数={}",
                returnId, stockInId, receivableId, sumLine.negate(), recvLines.size());
        return receivableId;
    }

    private static BigDecimal toDec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(o.toString()); } catch (Exception ignore) { return BigDecimal.ZERO; }
    }

    // ---------- 对账流程(两级: 提交→对账中→确认→对账完成；批量直通；反审回退) ----------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submitReconcile(Long receivableId, String signBy, String attachment, String remark, String operator) {
        SalArReceivable p = require(receivableId);
        if (p.getStatus() == null || p.getStatus() != 0)
            throw new ServiceException("仅「待对账」状态可提交对账");
        p.setStatus(1);
        p.setReconcileBy(operator == null ? "" : operator);
        p.setReconcileTime(LocalDateTime.now());
        if (signBy != null && !signBy.isBlank()) p.setSignBy(signBy);
        if (attachment != null) p.setAttachment(attachment);
        if (remark != null && !remark.isBlank()) p.setRemark(remark);
        return mapper.updateById(p) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmReconcile(Long receivableId, String signBy, LocalDateTime signTime, Long voucherId, String operator) {
        SalArReceivable p = require(receivableId);
        if (p.getStatus() == null || p.getStatus() != 1)
            throw new ServiceException("仅「对账中」状态可确认对账完成");
        if (signBy == null || signBy.isBlank()) throw new ServiceException("客户签字人必填");
        p.setStatus(2);
        if (operator != null && !operator.isBlank()) p.setReconcileBy(operator);
        p.setReconcileTime(LocalDateTime.now());
        p.setSignBy(signBy);
        p.setSignTime(signTime == null ? LocalDateTime.now() : signTime);
        if (voucherId != null) p.setVoucherId(voucherId);
        return mapper.updateById(p) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean undoReconcile(Long receivableId) {
        SalArReceivable p = require(receivableId);
        if (p.getStatus() == null || p.getStatus() != 2)
            throw new ServiceException("仅「对账完成」状态可撤销对账");
        if (p.getReceivedAmount() != null && p.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0)
            throw new ServiceException("已发生收款，无法撤销对账；请先取消收款单");
        // 用 LambdaUpdateWrapper 显式置空 reconcile/sign/voucher 字段（updateById 不会更新 null）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SalArReceivable> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(SalArReceivable::getReceivableId, receivableId)
          .set(SalArReceivable::getStatus, 0)
          .set(SalArReceivable::getReconcileBy, "")
          .set(SalArReceivable::getReconcileTime, null)
          .set(SalArReceivable::getSignBy, "")
          .set(SalArReceivable::getSignTime, null)
          .set(SalArReceivable::getVoucherId, null);
        return mapper.update(null, uw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchReconcile(Long customerId, String dateFrom, String dateTo, String operator) {
        if (customerId == null) throw new ServiceException("客户必填");
        if (dateFrom == null || dateFrom.isBlank() || dateTo == null || dateTo.isBlank())
            throw new ServiceException("业务日期起止必填");
        LocalDate dFrom = LocalDate.parse(dateFrom);
        LocalDate dTo = LocalDate.parse(dateTo);
        if (dFrom.isAfter(dTo)) throw new ServiceException("起始日期不能晚于结束日期");
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SalArReceivable> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(SalArReceivable::getCustomerId, customerId)
          .eq(SalArReceivable::getStatus, 0)
          .ge(SalArReceivable::getBizDate, dFrom)
          .le(SalArReceivable::getBizDate, dTo)
          .set(SalArReceivable::getStatus, 2)
          .set(SalArReceivable::getReconcileBy, operator == null ? "" : operator)
          .set(SalArReceivable::getReconcileTime, LocalDateTime.now());
        int n = mapper.update(null, uw);
        log.info("batchReconcile customerId={} range={}~{} 批量推进 {} 条 0→2", customerId, dateFrom, dateTo, n);
        return n;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long receivableId) {
        SalArReceivable p = require(receivableId);
        if (p.getReceivedAmount() != null && p.getReceivedAmount().compareTo(BigDecimal.ZERO) > 0)
            throw new ServiceException("已发生收款，先取消收款单再作废");
        p.setStatus(9);
        return mapper.updateById(p) > 0;
    }

    // ---------- 收款确认/回滚(供ReceiptService调用) ----------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean applyReceipt(Long receivableId, BigDecimal cnyReceivedAmount, Long receiptId) {
        if (receivableId == null) return true;
        SalArReceivable p = mapper.selectById(receivableId);
        if (p == null) { log.warn("applyReceipt receivable不存在: {}", receivableId); return false; }
        BigDecimal recv = cnyReceivedAmount == null ? BigDecimal.ZERO : cnyReceivedAmount;
        BigDecimal received = p.getReceivedAmount() == null ? BigDecimal.ZERO : p.getReceivedAmount();
        BigDecimal cnyTotal = p.getCnyTotalAmount() == null ? BigDecimal.ZERO : p.getCnyTotalAmount();
        BigDecimal newReceived = received.add(recv);
        p.setReceivedAmount(newReceived);
        BigDecimal unreceived = cnyTotal.subtract(newReceived);
        if (unreceived.compareTo(BigDecimal.ZERO) < 0) unreceived = BigDecimal.ZERO;
        p.setUnreceivedAmount(unreceived);
        // 状态：0/1/2 → 3 部分收；unreceived=0 → 4 完成
        int st = p.getStatus() == null ? 0 : p.getStatus();
        if (unreceived.compareTo(BigDecimal.ZERO) <= 0) p.setStatus(4);
        else if (st == 0 || st == 1 || st == 2) p.setStatus(3);
        return mapper.updateById(p) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackReceipt(Long receivableId, BigDecimal cnyReceivedAmount, Long receiptId) {
        if (receivableId == null) return true;
        SalArReceivable p = mapper.selectById(receivableId);
        if (p == null) { log.warn("rollbackReceipt receivable不存在: {}", receivableId); return false; }
        BigDecimal recv = cnyReceivedAmount == null ? BigDecimal.ZERO : cnyReceivedAmount;
        BigDecimal received = p.getReceivedAmount() == null ? BigDecimal.ZERO : p.getReceivedAmount();
        BigDecimal cnyTotal = p.getCnyTotalAmount() == null ? BigDecimal.ZERO : p.getCnyTotalAmount();
        BigDecimal newReceived = received.subtract(recv);
        if (newReceived.compareTo(BigDecimal.ZERO) < 0) newReceived = BigDecimal.ZERO;
        p.setReceivedAmount(newReceived);
        BigDecimal unreceived = cnyTotal.subtract(newReceived).max(BigDecimal.ZERO);
        p.setUnreceivedAmount(unreceived);
        int st = p.getStatus() == null ? 0 : p.getStatus();
        if (newReceived.compareTo(BigDecimal.ZERO) <= 0) {
            // 若状态是收款完成/部分收，回滚到对账完成
            if (st == 3 || st == 4) p.setStatus(2);
        } else {
            if (st == 4) p.setStatus(3);
        }
        return mapper.updateById(p) > 0;
    }

    // ---------- 分期计划 ----------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean generatePlans(Long receivableId, List<Map<String, Object>> phases) {
        SalArReceivable p = require(receivableId);
        BigDecimal total = p.getCnyTotalAmount() == null ? BigDecimal.ZERO : p.getCnyTotalAmount();
        // 删除旧计划(未发生收款)；已存在实际收款则视为已有有效计划，幂等跳过不重建
        List<SalArReceiptPlan> olds = planService.listByReceivable(receivableId);
        boolean hasActualReceived = false;
        for (SalArReceiptPlan o : olds) {
            if (o.getActualAmount() != null && o.getActualAmount().compareTo(BigDecimal.ZERO) > 0) {
                hasActualReceived = true;
            } else {
                planService.remove(o.getPlanId());
            }
        }
        if (hasActualReceived) {
            log.info("应收单{}已存在已收款的分期计划，幂等跳过重建", receivableId);
            return true;
        }
        if (phases == null || phases.isEmpty()) {
            phases = new ArrayList<>();
            Map<String,Object> once = new HashMap<>();
            once.put("phaseNo", 1); once.put("phaseName", "一次性");
            once.put("ratio", 100); once.put("planDate", p.getDueDate());
            phases.add(once);
        }
        int i = 0;
        for (Map<String,Object> ph : phases) {
            i++;
            Integer phaseNo = ph.get("phaseNo") == null ? i : ((Number)ph.get("phaseNo")).intValue();
            String phaseName = ph.get("phaseName") == null ? ("第"+phaseNo+"期") : ph.get("phaseName").toString();
            BigDecimal ratio = toBigDecimal(ph.get("ratio"));
            if (ratio == null) ratio = BigDecimal.ZERO;
            Object pd = ph.get("planDate");
            LocalDate planDate = pd instanceof LocalDate ? (LocalDate)pd : toLocalDate(pd);
            if (planDate == null) planDate = p.getDueDate();
            BigDecimal planAmount = total.multiply(ratio).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            // 最后一期兜底差额
            if (i == phases.size()) {
                SalArReceiptPlan lastPlan = new SalArReceiptPlan();
                lastPlan.setReceivableId(receivableId); lastPlan.setReceivableNo(p.getReceivableNo());
                lastPlan.setCustomerId(p.getCustomerId()); lastPlan.setCustomerName(p.getCustomerName());
                lastPlan.setPhaseNo(phaseNo); lastPlan.setPhaseName(phaseName); lastPlan.setRatio(ratio);
                lastPlan.setPlanDate(planDate); lastPlan.setStatus(0);
                // 实际最后一期= total - sum(前N-1期)
                BigDecimal sumPrev = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(plan_amount),0) FROM sal_ar_receipt_plan WHERE receivable_id = ?", BigDecimal.class, receivableId);
                if (sumPrev == null) sumPrev = BigDecimal.ZERO;
                planAmount = total.subtract(sumPrev);
                lastPlan.setPlanAmount(planAmount);
                lastPlan.setActualAmount(BigDecimal.ZERO); lastPlan.setUnpaidAmount(planAmount.max(BigDecimal.ZERO));
                fillPayerFromCustomer(lastPlan, p.getCustomerId());
                planService.save(lastPlan);
            } else {
                SalArReceiptPlan pl = new SalArReceiptPlan();
                pl.setReceivableId(receivableId); pl.setReceivableNo(p.getReceivableNo());
                pl.setCustomerId(p.getCustomerId()); pl.setCustomerName(p.getCustomerName());
                pl.setPhaseNo(phaseNo); pl.setPhaseName(phaseName); pl.setRatio(ratio);
                pl.setPlanDate(planDate); pl.setStatus(0);
                pl.setPlanAmount(planAmount); pl.setActualAmount(BigDecimal.ZERO);
                pl.setUnpaidAmount(planAmount.max(BigDecimal.ZERO));
                fillPayerFromCustomer(pl, p.getCustomerId());
                planService.save(pl);
            }
        }
        return true;
    }

    private void fillPayerFromCustomer(SalArReceiptPlan plan, Long customerId) {
        if (customerId == null) return;
        SalCustomer c = customerMapper.selectById(customerId);
        if (c == null) return;
        if (plan.getPayerName() == null) plan.setPayerName(c.getPayerName() != null ? c.getPayerName() : c.getCustomerName());
        if (plan.getPayerBank() == null) plan.setPayerBank(c.getPayerBank());
        if (plan.getPayerAccount() == null) plan.setPayerAccount(c.getPayerAccount());
    }

    // ---------- 信用额度管控 ----------
    @Override public BigDecimal sumUnreceived(Long customerId) {
        BigDecimal v = mapper.sumUnreceived(customerId);
        return v == null ? BigDecimal.ZERO : v;
    }
    @Override public BigDecimal sumOverdue(Long customerId) {
        BigDecimal v = mapper.sumOverdue(customerId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public void checkCreditLimit(Long customerId, BigDecimal newOrderCnyAmount) {
        if (customerId == null) return;
        SalCustomer c = customerMapper.selectById(customerId);
        if (c == null) return;
        // 1) 逾期
        BigDecimal overdue = sumOverdue(customerId);
        if (overdue.compareTo(BigDecimal.ZERO) > 0)
            throw new ServiceException("客户存在逾期未收金额 " + overdue + " CNY，请先处理逾期再下单");
        // 2) 额度
        BigDecimal limit = c.getCreditLimit() == null ? BigDecimal.ZERO : c.getCreditLimit();
        if (limit.compareTo(BigDecimal.ZERO) <= 0) return; // 0=不控额度
        BigDecimal unreceived = sumUnreceived(customerId);
        BigDecimal add = newOrderCnyAmount == null ? BigDecimal.ZERO : newOrderCnyAmount;
        if (unreceived.add(add).compareTo(limit) > 0)
            throw new ServiceException(String.format("客户信用额度不足：额度%s，未收%s，新单%s，合计超限",
                    limit.toPlainString(), unreceived.toPlainString(), add.toPlainString()));
    }

    // ---------- 看板 ----------
    @Override
    public Map<String, Object> kanbanSummary(Long customerId) {
        Map<String,Object> r = new HashMap<>();
        r.put("receivedTotal", sumReceived(customerId));
        r.put("unreceivedTotal", sumUnreceived(customerId));
        r.put("overdueTotal", sumOverdue(customerId));
        r.put("dueThisMonth", mapper.sumDueThisMonth(customerId));
        Map<String,Object> more = mapper.sumMore(customerId);
        if (more != null) r.putAll(more);
        return r;
    }

    /** 已收总额 */
    private BigDecimal sumReceived(Long customerId) {
        BigDecimal v = mapper.sumReceived(customerId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override public List<Map<String,Object>> topCustomers(Long customerId, int limit) { return mapper.topCustomers(customerId, limit); }
    @Override public List<Map<String,Object>> aging(Long customerId) { return mapper.agingBuckets(customerId); }

    // ---------- 内部工具 ----------
    private SalArReceivable require(Long id) {
        SalArReceivable p = mapper.selectById(id);
        if (p == null) throw new ServiceException("应收单不存在: " + id);
        return p;
    }
    private void recomputeOverdueFlag(SalArReceivable p) {
        if (p.getDueDate() == null) { p.setIsOverdue(0); p.setOverdueDays(0); return; }
        int days = (int) LocalDate.now().toEpochDay() - (int) p.getDueDate().toEpochDay();
        if (days > 0) { p.setIsOverdue(1); p.setOverdueDays(days); }
        else { p.setIsOverdue(0); p.setOverdueDays(0); }
    }
    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal)o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception ignore) { return null; }
    }
    private static LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate) return (LocalDate)o;
        if (o instanceof LocalDateTime) return ((LocalDateTime)o).toLocalDate();
        if (o instanceof java.sql.Date) return ((java.sql.Date)o).toLocalDate();
        if (o instanceof java.util.Date) return ((java.util.Date)o).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        try { return LocalDate.parse(o.toString()); } catch (Exception ignore) { return null; }
    }
}
