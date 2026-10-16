package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.service.IPdmMaterialService;
import com.pcba.erp.sal.entity.SalSalesOrder;
import com.pcba.erp.sal.entity.SalSalesOrderLine;
import com.pcba.erp.sal.mapper.SalSalesOrderLineMapper;
import com.pcba.erp.sal.service.ISalSalesOrderLineService;
import com.pcba.erp.sal.service.ISalSalesOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalSalesOrderLineServiceImpl implements ISalSalesOrderLineService {

    private final SalSalesOrderLineMapper mapper;
    private final IPdmMaterialService materialService;
    private final ISalSalesOrderService orderService;

    @Override
    public IPage<SalSalesOrderLine> selectPage(int pageNum, int pageSize, Long soId, String materialCode, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), soId, materialCode, materialName);
    }

    @Override
    public SalSalesOrderLine getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalSalesOrderLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalSalesOrderLine entity) {
        // 物料回填
        fillMaterial(entity);
        // lineNo 按主单递增(max+10)
        if (entity.getLineNo() == null || entity.getLineNo() <= 0) {
            Integer max = 0;
            try {
                max = mapper.selectObjs(new LambdaQueryWrapper<SalSalesOrderLine>()
                                .select(SalSalesOrderLine::getLineNo)
                                .eq(SalSalesOrderLine::getSoId, entity.getSoId())
                                .orderByDesc(SalSalesOrderLine::getLineNo)
                                .last("LIMIT 1"))
                        .stream().findFirst().map(o -> ((Number) o).intValue()).orElse(0);
            } catch (Exception ignore) {}
            entity.setLineNo(max + 10);
        }
        // 默认值
        if (entity.getOrderQty() == null) entity.setOrderQty(BigDecimal.ZERO);
        if (entity.getShippedQty() == null) entity.setShippedQty(BigDecimal.ZERO);
        if (entity.getReturnedQty() == null) entity.setReturnedQty(BigDecimal.ZERO);
        if (entity.getUnitPrice() == null) entity.setUnitPrice(BigDecimal.ZERO);
        if (entity.getDiscountRate() == null) entity.setDiscountRate(BigDecimal.ZERO);
        // 金额自动计算：lineAmount = orderQty * unitPrice * (1 - discountRate) + taxAmount
        BigDecimal q = entity.getOrderQty();
        BigDecimal p = entity.getUnitPrice();
        BigDecimal d = entity.getDiscountRate();
        BigDecimal undisc = q.multiply(p).multiply(BigDecimal.ONE.subtract(d == null ? BigDecimal.ZERO : d));
        if (entity.getTaxRate() == null) entity.setTaxRate(BigDecimal.ZERO);
        if (entity.getTaxAmount() == null || BigDecimal.ZERO.compareTo(entity.getTaxAmount()) == 0) {
            entity.setTaxAmount(undisc.multiply(entity.getTaxRate()));
        }
        if (entity.getLineAmount() == null || BigDecimal.ZERO.compareTo(entity.getLineAmount()) == 0) {
            entity.setLineAmount(undisc.add(entity.getTaxAmount()));
        }

        mapper.insert(entity);
        recomputeHeader(entity.getSoId());
        return entity.getLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalSalesOrderLine entity) {
        Long id = entity.getLineId();
        if (id == null) throw new ServiceException("ID不能为空");
        SalSalesOrderLine old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        fillMaterial(entity);
        entity.setSoId(old.getSoId());
        if (entity.getLineNo() == null) entity.setLineNo(old.getLineNo());
        // 金额重算(若用户未显式传值)
        BigDecimal q = entity.getOrderQty() != null ? entity.getOrderQty() : old.getOrderQty();
        BigDecimal p = entity.getUnitPrice() != null ? entity.getUnitPrice() : old.getUnitPrice();
        BigDecimal d = entity.getDiscountRate() != null ? entity.getDiscountRate() : (old.getDiscountRate() == null ? BigDecimal.ZERO : old.getDiscountRate());
        BigDecimal r = entity.getTaxRate() != null ? entity.getTaxRate() : (old.getTaxRate() == null ? BigDecimal.ZERO : old.getTaxRate());
        if (entity.getLineAmount() == null) {
            BigDecimal undisc = q.multiply(p).multiply(BigDecimal.ONE.subtract(d));
            BigDecimal tax = entity.getTaxAmount() != null ? entity.getTaxAmount() : undisc.multiply(r);
            entity.setLineAmount(undisc.add(tax));
        }
        boolean ok = mapper.updateById(entity) > 0;
        if (ok) recomputeHeader(old.getSoId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        SalSalesOrderLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        boolean ok = mapper.deleteById(id) > 0;
        if (ok) recomputeHeader(r.getSoId());
        return ok;
    }

    /* ========== 私有工具：物料回填 + 主单合计重算 ========== */
    @Override
    public void fillMaterial(SalSalesOrderLine entity) {
        if (entity == null || entity.getMaterialId() == null) return;
        PdmMaterial m;
        try { m = materialService.getById(entity.getMaterialId()); } catch (Exception ignore) { m = null; }
        if (m == null) return;
        if ((entity.getMaterialCode() == null || entity.getMaterialCode().isBlank()) && m.getMaterialCode() != null) entity.setMaterialCode(m.getMaterialCode());
        if ((entity.getMaterialName() == null || entity.getMaterialName().isBlank()) && m.getMaterialName() != null) entity.setMaterialName(m.getMaterialName());
        if ((entity.getSpecification() == null || entity.getSpecification().isBlank()) && m.getSpecification() != null) entity.setSpecification(m.getSpecification());
        if ((entity.getUnit() == null || entity.getUnit().isBlank()) && m.getUnit() != null) entity.setUnit(m.getUnit());
    }

    /** 重算销售订单主单：undiscAmount = Σ(orderQty*unitPrice); totalAmount = Σ(lineAmount); taxAmount = Σ(taxAmount) */
    @Override
    public void recomputeHeader(Long soId) {
        if (soId == null) return;
        SalSalesOrder header;
        try { header = orderService.getDetail(soId); } catch (Exception ignore) { return; }
        if (header == null) return;
        List<SalSalesOrderLine> rows = mapper.selectList(
                new LambdaQueryWrapper<SalSalesOrderLine>().eq(SalSalesOrderLine::getSoId, soId));
        BigDecimal undisc = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (SalSalesOrderLine line : rows) {
            BigDecimal q = line.getOrderQty() == null ? BigDecimal.ZERO : line.getOrderQty();
            BigDecimal p = line.getUnitPrice() == null ? BigDecimal.ZERO : line.getUnitPrice();
            undisc = undisc.add(q.multiply(p));
            BigDecimal la = line.getLineAmount() == null ? BigDecimal.ZERO : line.getLineAmount();
            total = total.add(la);
            BigDecimal ta = line.getTaxAmount() == null ? BigDecimal.ZERO : line.getTaxAmount();
            tax = tax.add(ta);
        }
        boolean changed = false;
        if (header.getUndiscAmount() == null || header.getUndiscAmount().compareTo(undisc) != 0) {
            header.setUndiscAmount(undisc); changed = true;
        }
        if (header.getTotalAmount() == null || header.getTotalAmount().compareTo(total) != 0) {
            header.setTotalAmount(total); changed = true;
        }
        if (header.getTaxAmount() == null || header.getTaxAmount().compareTo(tax) != 0) {
            header.setTaxAmount(tax); changed = true;
        }
        if (changed) {
            try { orderService.update(header); } catch (Exception ignore) { log.warn("重算销售订单主单失败: {}", soId); }
        }
    }
}
