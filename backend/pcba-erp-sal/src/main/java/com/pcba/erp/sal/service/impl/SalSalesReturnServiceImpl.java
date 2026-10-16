package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.SalSalesReturn;
import com.pcba.erp.sal.entity.SalSalesReturnLine;
import com.pcba.erp.sal.mapper.SalSalesReturnLineMapper;
import com.pcba.erp.sal.mapper.SalSalesReturnMapper;
import com.pcba.erp.sal.service.ISalSalesReturnService;
import com.pcba.erp.wms.entity.WmsStockIn;
import com.pcba.erp.wms.entity.WmsStockInLine;
import com.pcba.erp.wms.service.IWmsStockInService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalSalesReturnServiceImpl implements ISalSalesReturnService {

    private final SalSalesReturnMapper mapper;
    private final SalSalesReturnLineMapper returnLineMapper;
    private final IWmsStockInService wmsStockInService;

    @Override
    public IPage<SalSalesReturn> selectPage(int pageNum, int pageSize, String returnNo, Long customerId, String customerName, Long soId, Integer status, String returnDate) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), returnNo, customerId, customerName, soId, status, returnDate);
    }

    @Override
    public SalSalesReturn getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalSalesReturn r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalSalesReturn entity) {
        // 单号兜底
        try {
            Field f = SalSalesReturn.class.getDeclaredField("returnNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) {
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                f.set(entity, "SR" + ts);
            }
        } catch (Exception ignore) {}
        // 退货日期
        try {
            Field f = SalSalesReturn.class.getDeclaredField("returnDate");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore) {}
        // 币种默认
        try {
            Field f = SalSalesReturn.class.getDeclaredField("currency");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) f.set(entity, "CNY");
        } catch (Exception ignore) {}
        // 单号查重
        try {
            Field noField = SalSalesReturn.class.getDeclaredField("returnNo");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                Field idField = SalSalesReturn.class.getDeclaredField("returnId");
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
            Field s = SalSalesReturn.class.getDeclaredField("status");
            s.setAccessible(true);
            if (s.get(entity) == null) s.set(entity, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        for (String f : new String[]{"totalAmount","refundAmount","exchangeRate"}) {
            try {
                Field ff = SalSalesReturn.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        mapper.insert(entity);
        try {
            Field idF = SalSalesReturn.class.getDeclaredField("returnId");
            idF.setAccessible(true);
            return (Long) idF.get(entity);
        } catch (Exception ex) { return null; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalSalesReturn entity) {
        Long id = null;
        try {
            Field idF = SalSalesReturn.class.getDeclaredField("returnId");
            idF.setAccessible(true);
            id = (Long) idF.get(entity);
        } catch (Exception ignore) {}
        if (id == null) throw new ServiceException("ID不能为空");
        SalSalesReturn old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        // 单号查重
        try {
            Field noField = SalSalesReturn.class.getDeclaredField("returnNo");
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
        SalSalesReturn r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        SalSalesReturn r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        try {
            Field s = SalSalesReturn.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override
    public List<SalSalesReturn> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SalSalesReturn>()
                .orderByDesc("return_id"));
    }

    /* ========== 销售退货 → WMS 入库单生成（对称采购退货→出库单 generateStockOut 模式） ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateStockIn(Long returnId) {
        if (returnId == null) throw new ServiceException("销售退货单ID不能为空");
        SalSalesReturn ret = mapper.selectById(returnId);
        if (ret == null) throw new ServiceException("销售退货单不存在");
        if (ret.getStockInId() != null)
            throw new ServiceException("该退货单已生成入库单(stockInId=" + ret.getStockInId() + ")，不可重复生成");
        if (ret.getStatus() == null || ret.getStatus() != 2)
            throw new ServiceException("仅已审核(状态2)的销售退货单可生成入库单");

        List<SalSalesReturnLine> lines = returnLineMapper.selectList(
                new LambdaQueryWrapper<SalSalesReturnLine>()
                        .eq(SalSalesReturnLine::getReturnId, returnId)
                        .orderByAsc(SalSalesReturnLine::getLineNo));
        if (lines == null || lines.isEmpty()) throw new ServiceException("销售退货明细为空，无法生成入库单");

        // 入库仓库：退货主单 warehouseId → 兜底 1
        Long warehouseId = ret.getWarehouseId() == null ? 1L : ret.getWarehouseId();

        // 构造 WMS 退货入库主单（inType=RETURN）
        WmsStockIn stockIn = new WmsStockIn();
        stockIn.setWarehouseId(warehouseId);
        stockIn.setInType("RETURN");
        stockIn.setRemark("来自销售退货: " + (ret.getReturnNo() == null ? returnId : ret.getReturnNo()));

        // 构造入库明细：回填 returnId/returnLineId/soId/soLineId 链路字段，供 WMS confirmStockIn 精准回写 SO 行 returnedQty
        List<WmsStockInLine> inLines = new ArrayList<>(lines.size());
        for (SalSalesReturnLine rl : lines) {
            BigDecimal qty = rl.getReturnQty() == null ? BigDecimal.ZERO : rl.getReturnQty();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            WmsStockInLine il = new WmsStockInLine();
            il.setReturnId(returnId);
            il.setReturnLineId(rl.getLineId());
            il.setSoId(ret.getSoId());
            il.setSoLineId(rl.getSoLineId());
            il.setMaterialId(rl.getMaterialId());
            il.setMaterialCode(rl.getMaterialCode());
            il.setMaterialName(rl.getMaterialName());
            il.setSpecification(rl.getSpecification());
            il.setUnit(rl.getUnit());
            il.setQuantity(qty);
            il.setUnitPrice(rl.getUnitPrice());
            il.setRemark(rl.getRemark());
            inLines.add(il);
        }
        if (inLines.isEmpty()) throw new ServiceException("销售退货明细数量均为0，无可生成入库的明细");
        stockIn.setLines(inLines);

        Long stockInId = wmsStockInService.saveStockIn(stockIn);
        if (stockInId == null) throw new ServiceException("WMS 生成入库单失败");
        // 回填 stockInId + 状态推进 2(已审核) → 3(入库中)；入库确认后由 WMS 回写 returnedQty 并推进 3→4
        ret.setStockInId(stockInId);
        ret.setStatus(3);
        mapper.updateById(ret);
        log.info("[generateStockIn] Return={} → WMS入库单 stockInId={} status 2→3", returnId, stockInId);
        return stockInId;
    }

    /* ========== 退货质检录入（WMS入库确认后 qc_status=0待检 → 质检 → status 3→4） ========== */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submitQc(Long returnId, String disposition, String qcRemark, List<SalSalesReturnLine> lines, String operator) {
        if (returnId == null) throw new ServiceException("退货单ID不能为空");
        SalSalesReturn ret = mapper.selectById(returnId);
        if (ret == null) throw new ServiceException("退货单不存在");
        if (ret.getStatus() == null || ret.getStatus() != 3)
            throw new ServiceException("仅入库中(状态3)的退货单可录入质检");
        Integer qcStatus = ret.getQcStatus();
        if (qcStatus != null && qcStatus != 0)
            throw new ServiceException("该退货单已录入质检结论(qc_status=" + qcStatus + ")，不可重复录入");

        List<SalSalesReturnLine> existLines = returnLineMapper.selectList(
                new LambdaQueryWrapper<SalSalesReturnLine>()
                        .eq(SalSalesReturnLine::getReturnId, returnId)
                        .orderByAsc(SalSalesReturnLine::getLineNo));
        if (existLines == null || existLines.isEmpty())
            throw new ServiceException("退货明细为空，无法质检");

        // 按lineId映射录入结论
        Map<Long, SalSalesReturnLine> inputMap = new HashMap<>();
        if (lines != null) for (SalSalesReturnLine l : lines) {
            if (l.getLineId() != null) inputMap.put(l.getLineId(), l);
        }

        int hasBad = 0;   // 是否存在不良行
        int allBad = 1;    // 是否全不良(所有行qcQty=0)
        BigDecimal refund = BigDecimal.ZERO;

        for (SalSalesReturnLine ex : existLines) {
            SalSalesReturnLine in = inputMap.get(ex.getLineId());
            BigDecimal retQty = ex.getReturnQty() == null ? BigDecimal.ZERO : ex.getReturnQty();
            // qcQty未填默认全合格(=returnQty)
            BigDecimal qcQty = (in != null && in.getQcQty() != null) ? in.getQcQty() : retQty;
            if (qcQty.compareTo(BigDecimal.ZERO) < 0) qcQty = BigDecimal.ZERO;
            if (qcQty.compareTo(retQty) > 0) qcQty = retQty;
            ex.setQcQty(qcQty);
            if (in != null) {
                ex.setQcDisposition(in.getQcDisposition());
                ex.setQcRemark(in.getQcRemark());
            }
            BigDecimal price = ex.getUnitPrice() == null ? BigDecimal.ZERO : ex.getUnitPrice();
            refund = refund.add(qcQty.multiply(price));
            if (qcQty.compareTo(retQty) < 0) hasBad = 1;
            if (qcQty.compareTo(BigDecimal.ZERO) > 0) allBad = 0;
            returnLineMapper.updateById(ex);
        }

        int finalQc = allBad == 1 ? 3 : (hasBad == 1 ? 2 : 1);

        ret.setQcStatus(finalQc);
        ret.setQcBy(operator == null ? "" : operator);
        ret.setQcTime(LocalDateTime.now());
        ret.setDisposition(disposition);
        ret.setQcRemark(qcRemark);
        ret.setRefundAmount(refund.setScale(2, RoundingMode.HALF_UP));
        ret.setStatus(4); // 质检完成 → 推进已完成
        boolean ok = mapper.updateById(ret) > 0;
        log.info("[submitQc] Return={} qc_status={} disposition={} refund={} status 3→4 operator={}",
                returnId, finalQc, disposition, refund, operator);
        return ok;
    }
}
