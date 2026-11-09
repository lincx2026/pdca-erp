package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.SalSalesOrder;
import com.pcba.erp.sal.entity.SalSalesOrderLine;
import com.pcba.erp.sal.mapper.SalSalesOrderLineMapper;
import com.pcba.erp.sal.mapper.SalSalesOrderMapper;
import com.pcba.erp.sal.service.ISalArReceivableService;
import com.pcba.erp.sal.service.ISalSalesOrderService;
import com.pcba.erp.wms.entity.WmsStockOut;
import com.pcba.erp.wms.entity.WmsStockOutLine;
import com.pcba.erp.wms.service.IWmsStockOutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalSalesOrderServiceImpl implements ISalSalesOrderService {

    private final SalSalesOrderMapper mapper;
    private final SalSalesOrderLineMapper lineMapper;
    private final IWmsStockOutService wmsStockOutService;
    private final JdbcTemplate jdbcTemplate;
    @Lazy private final ISalArReceivableService receivableService;
    @Lazy private final com.pcba.erp.sal.service.ISalCustomerPriceService customerPrices;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id, boolean reprice, String customerOrderNo, LocalDate deliveryDate) {
        SalSalesOrder original = getDetail(id);
        SalSalesOrder copy = new SalSalesOrder();
        org.springframework.beans.BeanUtils.copyProperties(original, copy);
        copy.setSoId(null); copy.setSoNo(null); copy.setCustomerOrderNo(customerOrderNo == null ? null : customerOrderNo.trim());
        copy.setSoDate(LocalDate.now()); copy.setDeliveryDate(deliveryDate); copy.setSourceType("MANUAL"); copy.setSourceRef(null);
        copy.setStatus(0); copy.setStockOutId(null); copy.setReceivedAmount(BigDecimal.ZERO); copy.setAuditBy(null); copy.setAuditTime(null);
        copy.setRejectReason(null); copy.setCloseReason(null); copy.setCreateBy(null); copy.setCreateTime(null); copy.setUpdateBy(null); copy.setUpdateTime(null);
        copy.setTotalAmount(BigDecimal.ZERO); copy.setUndiscAmount(BigDecimal.ZERO); copy.setTaxAmount(BigDecimal.ZERO);
        Long newId = save(copy);
        List<SalSalesOrderLine> lines = lineMapper.selectList(new LambdaQueryWrapper<SalSalesOrderLine>().eq(SalSalesOrderLine::getSoId, id).orderByAsc(SalSalesOrderLine::getLineNo));
        BigDecimal total = BigDecimal.ZERO, untaxed = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (SalSalesOrderLine originalLine : lines) {
            SalSalesOrderLine line = new SalSalesOrderLine(); org.springframework.beans.BeanUtils.copyProperties(originalLine, line);
            line.setLineId(null); line.setSoId(newId); line.setShippedQty(BigDecimal.ZERO); line.setReturnedQty(BigDecimal.ZERO);
            line.setCreateBy(null); line.setCreateTime(null); line.setUpdateBy(null); line.setUpdateTime(null); line.setRequiredDate(deliveryDate);
            line.setPriceSource("COPY"); line.setCustomerPriceId(null);
            if (reprice) {
                var quote = customerPrices.matchPrice(copy.getCustomerId(), line.getMaterialId(), line.getOrderQty(), copy.getSoDate());
                if (quote == null || !Objects.equals(quote.getCurrency(), copy.getCurrency())) throw new ServiceException("物料 " + line.getMaterialCode() + " 没有适用的客户报价，无法重新匹配");
                line.setUnitPrice(quote.getUntaxedUnitPrice() == null ? quote.getUnitPrice() : quote.getUntaxedUnitPrice());
                line.setTaxRate(quote.getTaxRate() == null ? BigDecimal.ZERO : quote.getTaxRate().divide(new BigDecimal("100")));
                line.setPriceSource("QUOTE"); line.setCustomerPriceId(quote.getPriceId());
            }
            BigDecimal base = line.getOrderQty().multiply(line.getUnitPrice()).multiply(BigDecimal.ONE.subtract(line.getDiscountRate() == null ? BigDecimal.ZERO : line.getDiscountRate()));
            BigDecimal lineTax = base.multiply(line.getTaxRate() == null ? BigDecimal.ZERO : line.getTaxRate()).setScale(2, java.math.RoundingMode.HALF_UP);
            line.setTaxAmount(lineTax); line.setLineAmount(base.add(lineTax).setScale(2, java.math.RoundingMode.HALF_UP));
            untaxed = untaxed.add(line.getOrderQty().multiply(line.getUnitPrice())); tax = tax.add(lineTax); total = total.add(line.getLineAmount());
            lineMapper.insert(line);
        }
        copy.setSoId(newId); copy.setUndiscAmount(untaxed); copy.setTaxAmount(tax); copy.setTotalAmount(total); mapper.updateById(copy);
        return newId;
    }

    @Override
    public IPage<SalSalesOrder> selectPage(int pageNum, int pageSize, String soNo, String soType, Long customerId, String customerName, Long salesRepId, Integer status, String deliveryDate) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), soNo, soType, customerId, customerName, salesRepId, status, deliveryDate);
    }

    @Override
    public SalSalesOrder getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalSalesOrder r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalSalesOrder entity) {
        entity.setSoId(null); entity.setStatus(0); entity.setStockOutId(null);
        entity.setAuditBy(null); entity.setAuditTime(null); entity.setRejectReason(null);
        // 单号兜底
        try {
            Field f = SalSalesOrder.class.getDeclaredField("soNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) {
                LocalDate today = LocalDate.now();
                Long number = jdbcTemplate.queryForObject("INSERT INTO document_number_counter(document_type, number_date, last_number) VALUES ('SO', ?, 1) ON CONFLICT (document_type, number_date) DO UPDATE SET last_number = document_number_counter.last_number + 1 RETURNING last_number", Long.class, today);
                f.set(entity, "SO" + today.format(DateTimeFormatter.ofPattern("yyMMdd")) + String.format("%04d", number));
            }
        } catch (ReflectiveOperationException ex) { throw new ServiceException("生成销售订单号失败"); }
        // 订单日期
        try {
            Field f = SalSalesOrder.class.getDeclaredField("soDate");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        // 币种默认
        try {
            Field f = SalSalesOrder.class.getDeclaredField("currency");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) f.set(entity, "CNY");
        } catch (Exception ignore) {}
        // 单号查重
        try {
            Field noField = SalSalesOrder.class.getDeclaredField("soNo");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                Field idField = SalSalesOrder.class.getDeclaredField("soId");
                idField.setAccessible(true);
                Object idVal = idField.get(entity);
                if (mapper.countDuplicate(noVal.toString(), (Long) idVal) > 0) {
                    throw new ServiceException("编号已存在: " + noVal);
                }
            }
        } catch (NoSuchFieldException ignore) { /* 无对应字段跳过查重 */ }
          catch (IllegalAccessException ex) { throw new ServiceException("保存失败: " + ex.getMessage()); }

        // 默认值: status=0 / BigDecimal 字段 0
        try {
            Field s = SalSalesOrder.class.getDeclaredField("status");
            s.setAccessible(true);
            if (s.get(entity) == null) s.set(entity, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        for (String f : new String[]{"totalAmount","taxAmount","receivedAmount","undiscAmount","discountRate","taxRate","exchangeRate","orderQty","shippedQty","returnedQty","unitPrice","lineAmount"}) {
            try {
                Field ff = SalSalesOrder.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        // 注：客户信用额度/逾期拦截已移至审核(changeStatus→2)阶段执行，草稿(status=0)允许自由创建
        mapper.insert(entity);
        try {
            Field idF = SalSalesOrder.class.getDeclaredField("soId");
            idF.setAccessible(true);
            return (Long) idF.get(entity);
        } catch (Exception ex) { return null; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalSalesOrder entity) {
        Long id = null;
        try {
            Field idF = SalSalesOrder.class.getDeclaredField("soId");
            idF.setAccessible(true);
            id = (Long) idF.get(entity);
        } catch (Exception ignore) {}
        if (id == null) throw new ServiceException("ID不能为空");
        SalSalesOrder old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        // 单号查重
        try {
            Field noField = SalSalesOrder.class.getDeclaredField("soNo");
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
        SalSalesOrder r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        SalSalesOrder r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        // 审核推进(0/1 → 2)时执行客户信用额度/逾期拦截；草稿→提审不拦截
        if (status != null && status == 2) {
            try {
                Field cusF = SalSalesOrder.class.getDeclaredField("customerId"); cusF.setAccessible(true);
                Object cusId = cusF.get(r);
                if (cusId != null) {
                    // 本币金额 = totalAmount(原币) * exchangeRate(兜底1)；creditLimit 为 CNY，须按本币比对
                    Field amtF = SalSalesOrder.class.getDeclaredField("totalAmount"); amtF.setAccessible(true);
                    Field rateF = SalSalesOrder.class.getDeclaredField("exchangeRate"); rateF.setAccessible(true);
                    BigDecimal amt = (BigDecimal) amtF.get(r);
                    BigDecimal rate = (BigDecimal) rateF.get(r);
                    if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) rate = BigDecimal.ONE;
                    BigDecimal cny = (amt == null ? BigDecimal.ZERO : amt).multiply(rate);
                    receivableService.checkCreditLimit((Long) cusId, cny);
                }
            } catch (ServiceException se) { throw se; }
              catch (Exception ignore) {}
        }
        try {
            Field s = SalSalesOrder.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override
    public List<SalSalesOrder> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SalSalesOrder>()
                .orderByDesc("so_id"));
    }

    /* ========== 销售订单 → WMS 出库单生成（参考 SCM 到货→入库 generateStockIn 模式） ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateStockOut(Long soId) {
        if (soId == null) throw new ServiceException("销售订单ID不能为空");
        SalSalesOrder so = mapper.selectForUpdate(soId);
        if (so == null) throw new ServiceException("销售订单不存在");
        if (so.getStatus() == null || (so.getStatus() != 2 && so.getStatus() != 4)) throw new ServiceException("仅已审核或部分出库的销售订单可生成出库单");
        Integer pending = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wms_stock_out s WHERE s.status IN (0,1,2) AND EXISTS (SELECT 1 FROM wms_stock_out_line l WHERE l.stock_out_id=s.stock_out_id AND l.so_id=?)", Integer.class, soId);
        if (pending != null && pending > 0) throw new ServiceException("订单已有未完成的出库单，请先处理");

        List<SalSalesOrderLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<SalSalesOrderLine>()
                        .eq(SalSalesOrderLine::getSoId, soId)
                        .orderByAsc(SalSalesOrderLine::getLineNo));
        if (lines == null || lines.isEmpty()) throw new ServiceException("销售订单明细为空，无法生成出库单");

        // 出库仓库：SO 主单 warehouseId → 明细行 warehouseId → 兜底 1
        Long warehouseId = so.getWarehouseId();
        if (warehouseId == null) {
            warehouseId = lines.stream().map(SalSalesOrderLine::getWarehouseId).filter(Objects::nonNull).findFirst().orElse(1L);
        }

        // 构造出库主单
        WmsStockOut stockOut = new WmsStockOut();
        stockOut.setWarehouseId(warehouseId);
        stockOut.setOutType("SALES");
        stockOut.setCustomerId(so.getCustomerId());
        stockOut.setCustomerName(so.getCustomerName());
        stockOut.setRemark("来自销售订单: " + (so.getSoNo() == null ? soId : so.getSoNo()));

        // 构造出库明细行：回填 soId/soLineId 链路字段，供 WMS confirmStockOut 精准回写 SO 行 shippedQty
        // 出库数量 = 订单量 - 已发量（未发量）；已发完的行跳过
        List<WmsStockOutLine> outLines = new ArrayList<>(lines.size());
        for (SalSalesOrderLine ol : lines) {
            BigDecimal orderQty = ol.getOrderQty() == null ? BigDecimal.ZERO : ol.getOrderQty();
            BigDecimal shipped = ol.getShippedQty() == null ? BigDecimal.ZERO : ol.getShippedQty();
            BigDecimal remain = orderQty.subtract(shipped);
            if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
            WmsStockOutLine sl = new WmsStockOutLine();
            sl.setSoId(soId);
            sl.setSoLineId(ol.getLineId());
            sl.setMaterialId(ol.getMaterialId());
            sl.setMaterialCode(ol.getMaterialCode());
            sl.setMaterialName(ol.getMaterialName());
            sl.setSpecification(ol.getSpecification());
            sl.setUnit(ol.getUnit());
            sl.setQuantity(remain);
            sl.setBatchNo(pickAvailableBatch(warehouseId, ol.getMaterialId()));
            outLines.add(sl);
        }
        if (outLines.isEmpty()) throw new ServiceException("销售订单明细均已发完，无可生成出库的明细");
        stockOut.setLines(outLines);

        Long stockOutId = wmsStockOutService.saveStockOut(stockOut);
        // 回填 stockOutId 到 SO + 状态推进 2 → 3(已发货/在途)；出库确认后由 WMS 回写 shippedQty 推进至 4/5
        so.setStockOutId(stockOutId);
        so.setStatus(3);
        mapper.updateById(so);
        log.info("[generateStockOut] SO={} → WMS出库单 stockOutId={} status 2→3", soId, stockOutId);
        return stockOutId;
    }

    /**
     * FIFO 选取仓库+物料的第一个可用批次（available_qty>0，按 inventory_id 升序）。
     * 无可用批次返回空串（confirm 时将由 WMS 库存预校验抛"库存不足"，符合业务语义）。
     * 简化：单批次整行扣减；多批次分摊超出本次范围（后续如需可扩展为按批次拆行）。
     */
    private String pickAvailableBatch(Long warehouseId, Long materialId) {
        if (warehouseId == null || materialId == null) return "";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT batch_no FROM wms_inventory " +
                            "WHERE warehouse_id=? AND material_id=? AND available_qty > 0 " +
                            "ORDER BY inventory_id ASC LIMIT 1",
                    warehouseId, materialId);
            if (rows.isEmpty()) return "";
            Object bn = rows.get(0).get("batch_no");
            return bn == null ? "" : bn.toString();
        } catch (Exception e) {
            log.warn("[pickAvailableBatch] 查库存失败 wh={} mat={} msg={}", warehouseId, materialId, e.getMessage());
            return "";
        }
    }
}
