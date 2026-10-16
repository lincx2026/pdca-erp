package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.SalCustomerPrice;
import com.pcba.erp.sal.entity.SalCustomer;
import com.pcba.erp.sal.entity.SalSalesOrder;
import com.pcba.erp.sal.entity.SalSalesOrderLine;
import com.pcba.erp.sal.mapper.SalCustomerPriceMapper;
import com.pcba.erp.sal.mapper.SalSalesOrderLineMapper;
import com.pcba.erp.sal.service.ISalCustomerPriceService;
import com.pcba.erp.sal.service.ISalSalesOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 客户专属价格表服务 - 状态机: 0草稿→1待审→2已审(生效)→3停用; 4驳回(可改后重提)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalCustomerPriceServiceImpl implements ISalCustomerPriceService {

    private final SalCustomerPriceMapper mapper;
    @Lazy private final ISalSalesOrderService salesOrderService;
    private final SalSalesOrderLineMapper orderLineMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    /** 报价单号 Lua 脚本: 原子 INCR + 设置过期(2天) */
    private static final String LUA_QUOTE_INCR =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return v";

    /** 生成报价单号: Q + yyyyMMdd + 4位序号, 如 Q202609030001 */
    private String generateQuoteNo() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String seqKey = "sal:quote:seq:" + today;
        long expireSec = 2L * 24 * 3600; // 2天过期
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_QUOTE_INCR, Long.class);
        Long seq = redisTemplate.execute(script, Collections.singletonList(seqKey), String.valueOf(expireSec));
        if (seq == null) throw new ServiceException("报价单号生成失败");
        return "Q" + today + String.format("%04d", seq);
    }

    @Override
    public IPage<SalCustomerPrice> selectPage(int pageNum, int pageSize, Long customerId, Long materialId,
                                              String currency, Integer status, String customerName, Integer onlyValid) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), customerId, materialId, currency, status, customerName, onlyValid);
    }

    @Override
    public SalCustomerPrice getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalCustomerPrice r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalCustomerPrice e) {
        if (e.getCustomerId() == null) throw new ServiceException("客户不能为空");
        if (e.getMaterialId() == null) throw new ServiceException("物料不能为空");
        if (e.getUnitPrice() == null) throw new ServiceException("单价不能为空");
        if (e.getValidFrom() == null || e.getValidTo() == null) throw new ServiceException("有效期不能为空");
        if (e.getValidFrom().isAfter(e.getValidTo())) throw new ServiceException("生效日期不能晚于失效日期");
        if (e.getCurrency() == null || e.getCurrency().isBlank()) e.setCurrency("CNY");
        if (e.getTaxRate() == null) e.setTaxRate(new BigDecimal("13"));
        if (e.getMinQty() == null) e.setMinQty(BigDecimal.ZERO);
        // 新建恒为草稿, 清审批字段
        e.setStatus(0);
        e.setIsDefault(0);
        e.setAuditBy("");
        e.setAuditTime(null);
        e.setRejectReason("");
        // 自动生成报价单号: Q + yyyyMMdd + 4位序号
        if (e.getQuoteSource() == null || e.getQuoteSource().isBlank()) {
            e.setQuoteSource(generateQuoteNo());
        }
        mapper.insert(e);
        return e.getPriceId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchSave(List<SalCustomerPrice> items) {
        if (items == null || items.isEmpty()) throw new ServiceException("批量报价不能为空");
        // 共享字段校验: 第一条带 customerId/currency/validFrom/validTo
        SalCustomerPrice head = items.get(0);
        if (head.getCustomerId() == null) throw new ServiceException("客户不能为空");
        if (head.getValidFrom() == null || head.getValidTo() == null) throw new ServiceException("有效期不能为空");
        if (head.getValidFrom().isAfter(head.getValidTo())) throw new ServiceException("生效日期不能晚于失效日期");
        // 查客户名称
        Map<String, Object> cust = jdbcTemplate.queryForMap(
                "SELECT customer_code, customer_name FROM scm_customer WHERE customer_id = ?",
                head.getCustomerId());
        List<Long> ids = new ArrayList<>();
        for (SalCustomerPrice e : items) {
            if (e.getMaterialId() == null) throw new ServiceException("物料不能为空");
            if (e.getUnitPrice() == null) throw new ServiceException("单价不能为空");
            // 共享字段回填
            e.setCustomerId(head.getCustomerId());
            e.setCustomerCode((String) cust.get("customer_code"));
            e.setCustomerName((String) cust.get("customer_name"));
            e.setCurrency(head.getCurrency() == null || head.getCurrency().isBlank() ? "CNY" : head.getCurrency());
            e.setValidFrom(head.getValidFrom());
            e.setValidTo(head.getValidTo());
            if (e.getTaxRate() == null) e.setTaxRate(new BigDecimal("13"));
            if (e.getMinQty() == null) e.setMinQty(BigDecimal.ZERO);
            if (e.getRemark() == null) e.setRemark(head.getRemark());
            // 查物料名称
            Map<String, Object> mat = jdbcTemplate.queryForMap(
                    "SELECT material_code, material_name, specification, unit FROM pdm_material WHERE material_id = ?",
                    e.getMaterialId());
            e.setMaterialCode((String) mat.get("material_code"));
            e.setMaterialName((String) mat.get("material_name"));
            e.setSpecification((String) mat.get("specification"));
            e.setUnit((String) mat.get("unit"));
            // 草稿状态 + 清审批字段 + 报价单号
            e.setStatus(0);
            e.setIsDefault(0);
            e.setAuditBy("");
            e.setAuditTime(null);
            e.setRejectReason("");
            e.setQuoteSource(generateQuoteNo());
            mapper.insert(e);
            ids.add(e.getPriceId());
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalCustomerPrice e) {
        if (e.getPriceId() == null) throw new ServiceException("ID不能为空");
        SalCustomerPrice old = mapper.selectById(e.getPriceId());
        if (old == null) throw new ServiceException("记录不存在");
        if (old.getStatus() != null && old.getStatus() != 0 && old.getStatus() != 4)
            throw new ServiceException("仅草稿/已驳回状态可修改");
        if (e.getValidFrom() != null && e.getValidTo() != null && e.getValidFrom().isAfter(e.getValidTo()))
            throw new ServiceException("生效日期不能晚于失效日期");
        // 禁止通过update改状态/审批字段
        e.setStatus(old.getStatus());
        e.setAuditBy(old.getAuditBy());
        e.setAuditTime(old.getAuditTime());
        e.setRejectReason(old.getRejectReason());
        return mapper.updateById(e) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        SalCustomerPrice r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        if (r.getStatus() != null && r.getStatus() != 0 && r.getStatus() != 4)
            throw new ServiceException("仅草稿/已驳回状态可删除");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submit(Long id) {
        SalCustomerPrice r = mustGet(id);
        if (r.getStatus() == null || (r.getStatus() != 0 && r.getStatus() != 4))
            throw new ServiceException("仅草稿/已驳回可提交审核");
        r.setStatus(1);
        r.setRejectReason("");
        return mapper.updateById(r) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean audit(Long id, String auditBy) {
        SalCustomerPrice r = mustGet(id);
        if (r.getStatus() == null || r.getStatus() != 1)
            throw new ServiceException("仅待审核状态可审核通过");
        r.setStatus(2);
        r.setAuditBy(auditBy == null ? "" : auditBy);
        r.setAuditTime(LocalDateTime.now());
        r.setRejectReason("");
        mapper.updateById(r);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reject(Long id, String reason, String auditBy) {
        SalCustomerPrice r = mustGet(id);
        if (r.getStatus() == null || r.getStatus() != 1)
            throw new ServiceException("仅待审核状态可驳回");
        if (reason == null || reason.isBlank()) throw new ServiceException("驳回理由不能为空");
        r.setStatus(4);
        r.setAuditBy(auditBy == null ? "" : auditBy);
        r.setAuditTime(LocalDateTime.now());
        r.setRejectReason(reason);
        return mapper.updateById(r) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long id) {
        SalCustomerPrice r = mustGet(id);
        if (r.getStatus() == null || r.getStatus() != 2)
            throw new ServiceException("仅已审核(生效)状态可停用");
        r.setStatus(3);
        return mapper.updateById(r) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enable(Long id) {
        SalCustomerPrice r = mustGet(id);
        if (r.getStatus() == null || r.getStatus() != 3)
            throw new ServiceException("仅已停用状态可重新启用");
        r.setStatus(2);
        return mapper.updateById(r) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefault(Long id) {
        SalCustomerPrice r = mustGet(id);
        if (r.getStatus() == null || r.getStatus() != 2)
            throw new ServiceException("仅已审核(生效)状态可设为默认");
        // 同客户+物料生效价(status=2)中其他清0, 本条设1
        LambdaUpdateWrapper<SalCustomerPrice> uw = new LambdaUpdateWrapper<>();
        uw.eq(SalCustomerPrice::getCustomerId, r.getCustomerId())
          .eq(SalCustomerPrice::getMaterialId, r.getMaterialId())
          .eq(SalCustomerPrice::getStatus, 2)
          .set(SalCustomerPrice::getIsDefault, 0);
        mapper.update(null, uw);
        r.setIsDefault(1);
        return mapper.updateById(r) > 0;
    }

    @Override
    public SalCustomerPrice matchPrice(Long customerId, Long materialId, BigDecimal qty, LocalDate date) {
        if (customerId == null || materialId == null) return null;
        BigDecimal q = (qty == null) ? BigDecimal.ZERO : qty;
        LocalDate d = (date == null) ? LocalDate.now() : date;
        List<SalCustomerPrice> list = mapper.selectEffectivePrices(customerId, materialId, q, d);
        // mapper 已按 is_default DESC, min_qty DESC, unit_price ASC 排序, 取首条为最优
        if (list.isEmpty()) return null;
        SalCustomerPrice p = list.get(0);
        // 含税单价 → 未税单价(供订单行使用): untaxed = unitPrice / (1 + taxRate/100)
        if (p.getUnitPrice() != null && p.getTaxRate() != null && p.getTaxRate().compareTo(BigDecimal.ZERO) >= 0) {
            BigDecimal taxDivisor = BigDecimal.ONE.add(
                p.getTaxRate().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            p.setUntaxedUnitPrice(p.getUnitPrice().divide(taxDivisor, 4, RoundingMode.HALF_UP));
        }
        return p;
    }

    @Override
    public List<SalCustomerPrice> options() {
        return mapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireOverduePrices() {
        // 过期价自动流转: status=2 且 valid_to < 今天 → status=3(停用), 同时清掉 is_default
        LambdaUpdateWrapper<SalCustomerPrice> uw = new LambdaUpdateWrapper<>();
        uw.eq(SalCustomerPrice::getStatus, 2)
          .lt(SalCustomerPrice::getValidTo, LocalDate.now())
          .set(SalCustomerPrice::getStatus, 3)
          .set(SalCustomerPrice::getIsDefault, 0);
        int n = mapper.update(null, uw);
        if (n > 0) log.info("sal_customer_price 过期自动停用: {} 条", n);
        return n;
    }

    // ============ P1: 报价(价目表条目) 批量 转销售订单 ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToSalesOrder(Long customerId,
                                    List<Map<String, Object>> itemList,
                                    Map<String, Object> extra) {
        if (customerId == null) throw new ServiceException("客户ID不能为空");
        if (itemList == null || itemList.isEmpty()) throw new ServiceException("至少选择一条报价条目");

        // 1. 客户信息
        List<Map<String, Object>> cust = jdbcTemplate.queryForList(
                "SELECT customer_id, customer_code, customer_name FROM scm_customer WHERE customer_id=?", customerId);
        if (cust.isEmpty()) throw new ServiceException("客户不存在: customerId=" + customerId);
        Long cId = ((Number) cust.get(0).get("customer_id")).longValue();
        String cCode = (String) cust.get(0).get("customer_code");
        String cName = (String) cust.get(0).get("customer_name");

        // 2. 取所有priceId
        List<Long> priceIds = new ArrayList<>(itemList.size());
        for (Map<String, Object> item : itemList) {
            Object pid = item.get("priceId");
            if (pid == null) throw new ServiceException("每条必须有 priceId");
            priceIds.add(Long.valueOf(String.valueOf(pid)));
        }
        String ids = priceIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<SalCustomerPrice> prices = jdbcTemplate.query(
                "SELECT * FROM sal_customer_price WHERE price_id IN (" + ids + ")",
                (rs, i) -> {
                    SalCustomerPrice p = new SalCustomerPrice();
                    p.setPriceId(rs.getLong("price_id"));
                    p.setCustomerId(rs.getLong("customer_id"));
                    p.setCustomerCode(rs.getString("customer_code"));
                    p.setCustomerName(rs.getString("customer_name"));
                    p.setMaterialId(rs.getLong("material_id"));
                    p.setMaterialCode(rs.getString("material_code"));
                    p.setMaterialName(rs.getString("material_name"));
                    p.setSpecification(rs.getString("specification"));
                    p.setUnit(rs.getString("unit"));
                    p.setCurrency(rs.getString("currency"));
                    p.setUnitPrice(rs.getBigDecimal("unit_price"));
                    p.setTaxRate(rs.getBigDecimal("tax_rate"));
                    java.sql.Date vf = rs.getDate("valid_from");
                    p.setValidFrom(vf == null ? null : vf.toLocalDate());
                    java.sql.Date vt = rs.getDate("valid_to");
                    p.setValidTo(vt == null ? null : vt.toLocalDate());
                    p.setStatus(rs.getInt("status"));
                    p.setOrderId(rs.getObject("order_id") == null ? null : ((Number) rs.getObject("order_id")).longValue());
                    return p;
                });
        if (prices.size() != itemList.size()) throw new ServiceException("存在无效的priceId");

        // 3. 校验: 客户一致 + status=2 + orderId空
        for (SalCustomerPrice p : prices) {
            if (!cId.equals(p.getCustomerId())) {
                throw new ServiceException("报价条目 priceId=" + p.getPriceId() + " 的客户与所选不一致");
            }
            if (!Integer.valueOf(2).equals(p.getStatus())) {
                throw new ServiceException("报价条目 priceId=" + p.getPriceId() + " 状态不是【已审=生效】(status=2)，不可转单");
            }
            if (p.getOrderId() != null) {
                throw new ServiceException("报价条目 priceId=" + p.getPriceId() + " 已被其他订单(orderId=" + p.getOrderId() + ")引用，不可重复转单");
            }
        }
        // 取qty映射
        Map<Long, BigDecimal> qtyMap = new java.util.HashMap<>();
        Map<Long, LocalDate> reqDateMap = new java.util.HashMap<>();
        for (Map<String, Object> item : itemList) {
            Long pid = Long.valueOf(String.valueOf(item.get("priceId")));
            Object q = item.get("qty");
            if (q == null) throw new ServiceException("priceId=" + pid + " 缺少 qty");
            qtyMap.put(pid, new BigDecimal(String.valueOf(q)));
            Object rd = item.get("deliveryDate");
            if (rd != null && !String.valueOf(rd).isBlank()) reqDateMap.put(pid, LocalDate.parse(String.valueOf(rd)));
        }

        // 4. extra默认值
        extra = extra == null ? new java.util.HashMap<>() : extra;
        Object delv = extra.get("deliveryDate");
        LocalDate commonDelivery = delv == null || String.valueOf(delv).isBlank()
                ? LocalDate.now().plusDays(15) : LocalDate.parse(String.valueOf(delv));
        Object soDateO = extra.get("soDate");
        LocalDate soDate = soDateO == null || String.valueOf(soDateO).isBlank()
                ? LocalDate.now() : LocalDate.parse(String.valueOf(soDateO));
        Object wh = extra.get("warehouseId");
        Long warehouseId = (wh == null || String.valueOf(wh).isBlank()) ? null : Long.valueOf(String.valueOf(wh));
        Object sales = extra.get("salesRepId");
        Long salesRepId = (sales == null || String.valueOf(sales).isBlank()) ? null : Long.valueOf(String.valueOf(sales));
        String paymentTerm = extra.get("paymentTerm") == null ? null : String.valueOf(extra.get("paymentTerm"));
        String remark = extra.get("remark") == null ? null : String.valueOf(extra.get("remark"));

        // 5. 订单头金额汇总
        BigDecimal undisc = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        String currency = prices.get(0).getCurrency();

        List<SalSalesOrderLine> newLines = new ArrayList<>(prices.size());
        int lineNo = 1;
        for (SalCustomerPrice p : prices) {
            BigDecimal qty = qtyMap.getOrDefault(p.getPriceId(), BigDecimal.ONE);
            BigDecimal taxInclPrice = p.getUnitPrice() == null ? BigDecimal.ZERO : p.getUnitPrice();
            BigDecimal taxRate = (p.getTaxRate() == null) ? new BigDecimal("13") : p.getTaxRate();
            // 含税单价 → 未税单价: untaxed = taxInclPrice / (1 + taxRate/100)
            BigDecimal taxDivisor = BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            BigDecimal untaxedUnitPrice = taxInclPrice.divide(taxDivisor, 4, RoundingMode.HALF_UP);
            BigDecimal lineAmt = qty.multiply(untaxedUnitPrice).setScale(4, RoundingMode.HALF_UP);
            BigDecimal taxAmt = qty.multiply(taxInclPrice).subtract(lineAmt).setScale(4, RoundingMode.HALF_UP);
            undisc = undisc.add(lineAmt);
            totalTax = totalTax.add(taxAmt);
            total = total.add(lineAmt).add(taxAmt);

            SalSalesOrderLine l = new SalSalesOrderLine();
            l.setLineNo(lineNo++);
            l.setMaterialId(p.getMaterialId());
            l.setMaterialCode(p.getMaterialCode());
            l.setMaterialName(p.getMaterialName());
            l.setSpecification(p.getSpecification());
            l.setUnit(p.getUnit());
            l.setOrderQty(qty);
            l.setShippedQty(BigDecimal.ZERO);
            l.setReturnedQty(BigDecimal.ZERO);
            l.setUnitPrice(untaxedUnitPrice);
            l.setTaxRate(taxRate);
            l.setTaxAmount(taxAmt);
            l.setLineAmount(lineAmt);
            l.setRequiredDate(reqDateMap.getOrDefault(p.getPriceId(), commonDelivery));
            l.setWarehouseId(warehouseId);
            newLines.add(l);
        }

        // 6. 构建并保存订单头
        SalSalesOrder so = new SalSalesOrder();
        so.setSoType("NORMAL");
        so.setSourceType("FROM_PRICE");
        so.setSourceRef(priceIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        so.setCustomerId(cId);
        so.setCustomerCode(cCode);
        so.setCustomerName(cName);
        so.setSalesRepId(salesRepId);
        so.setSoDate(soDate);
        so.setDeliveryDate(commonDelivery);
        so.setWarehouseId(warehouseId);
        so.setCurrency(currency == null || currency.isBlank() ? "CNY" : currency);
        so.setTaxRate(new BigDecimal("13"));
        so.setUndiscAmount(undisc);
        so.setDiscountRate(BigDecimal.ZERO);
        so.setTaxAmount(totalTax);
        so.setTotalAmount(total);
        so.setReceivedAmount(BigDecimal.ZERO);
        so.setPaymentTerm(paymentTerm);
        so.setStatus(0);
        so.setRemark(remark);
        Long soId = salesOrderService.save(so);
        if (soId == null) throw new ServiceException("创建销售订单失败");

        // 7. 保存订单行 + 回写 报价条目
        for (int i = 0; i < prices.size(); i++) {
            SalCustomerPrice p = prices.get(i);
            SalSalesOrderLine l = newLines.get(i);
            l.setSoId(soId);
            orderLineMapper.insert(l);
            jdbcTemplate.update(
                    "UPDATE sal_customer_price SET status=5, order_id=?, order_line_id=? WHERE price_id=?",
                    soId, l.getLineId(), p.getPriceId());
        }
        log.info("[convertToSalesOrder] customerId={} prices={} → soId={}", customerId, priceIds, soId);
        return soId;
    }

    /** 每日凌晨1点执行一次过期价自动停用 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void scheduledExpireOverduePrices() {
        try { expireOverduePrices(); }
        catch (Exception e) { log.error("客户价过期自动停用任务失败", e); }
    }

    private SalCustomerPrice mustGet(Long id) {
        SalCustomerPrice r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }
}
