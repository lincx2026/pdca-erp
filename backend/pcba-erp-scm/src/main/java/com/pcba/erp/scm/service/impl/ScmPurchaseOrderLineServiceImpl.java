package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.service.IPdmMaterialService;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;
import com.pcba.erp.scm.entity.ScmPurchaseOrderLine;
import com.pcba.erp.scm.mapper.ScmPurchaseOrderLineMapper;
import com.pcba.erp.scm.service.IScmPurchaseOrderLineService;
import com.pcba.erp.scm.service.IScmPurchaseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmPurchaseOrderLineServiceImpl implements IScmPurchaseOrderLineService {

    private final ScmPurchaseOrderLineMapper mapper;
    private final IPdmMaterialService materialService;

    // 使用 @Lazy 避免 ScmPurchaseOrderServiceImpl <-> ScmPurchaseOrderLineServiceImpl 循环依赖
    // (generateStockIn -> lineMapper/Mapper操作, recomputeHeader 非高频,延迟注入即可)
    @Autowired @Lazy
    private IScmPurchaseOrderService orderService;

    @Override
    public IPage<ScmPurchaseOrderLine> selectPage(int pageNum, int pageSize, Long poId, String materialCode, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), poId, materialCode, materialName);
    }

    @Override
    public ScmPurchaseOrderLine getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseOrderLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmPurchaseOrderLine entity) {
        // 物料回填
        fillMaterial(entity);
        // lineNo 按主单递增
        if (entity.getLineNo() == null || entity.getLineNo() <= 0) {
            Integer max = 0;
            try {
                max = mapper.selectObjs(new LambdaQueryWrapper<ScmPurchaseOrderLine>()
                                .select(ScmPurchaseOrderLine::getLineNo)
                                .eq(ScmPurchaseOrderLine::getPoId, entity.getPoId())
                                .orderByDesc(ScmPurchaseOrderLine::getLineNo)
                                .last("LIMIT 1"))
                        .stream().findFirst().map(o -> ((Number) o).intValue()).orElse(0);
            } catch (Exception ignore) {}
            entity.setLineNo(max + 10);
        }
        // 默认值
        if (entity.getOrderQty() == null) entity.setOrderQty(BigDecimal.ZERO);
        if (entity.getReceivedQty() == null) entity.setReceivedQty(BigDecimal.ZERO);
        if (entity.getReturnedQty() == null) entity.setReturnedQty(BigDecimal.ZERO);
        if (entity.getUnitPrice() == null) entity.setUnitPrice(BigDecimal.ZERO);
        if (entity.getDiscountRate() == null) entity.setDiscountRate(BigDecimal.ZERO);
        // 税额/金额自动计算：lineAmount = orderQty * unitPrice * (1 - discountRate); taxAmount = lineAmount * taxRate
        BigDecimal q = entity.getOrderQty();
        BigDecimal p = entity.getUnitPrice();
        BigDecimal d = entity.getDiscountRate();
        BigDecimal lineAmt = q.multiply(p).multiply(BigDecimal.ONE.subtract(d == null ? BigDecimal.ZERO : d));
        if (entity.getLineAmount() == null || BigDecimal.ZERO.compareTo(entity.getLineAmount()) == 0) {
            entity.setLineAmount(lineAmt);
        }
        if (entity.getTaxRate() != null && (entity.getTaxAmount() == null || BigDecimal.ZERO.compareTo(entity.getTaxAmount()) == 0)) {
            BigDecimal base = entity.getLineAmount() != null ? entity.getLineAmount() : lineAmt;
            entity.setTaxAmount(base.multiply(entity.getTaxRate()));
        }
        if (entity.getTaxRate() == null) entity.setTaxRate(BigDecimal.ZERO);
        if (entity.getTaxAmount() == null) entity.setTaxAmount(BigDecimal.ZERO);

        mapper.insert(entity);
        recomputeHeader(entity.getPoId());
        return entity.getLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmPurchaseOrderLine entity) {
        Long id = entity.getLineId();
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseOrderLine old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        fillMaterial(entity);
        entity.setPoId(old.getPoId());
        if (entity.getLineNo() == null) entity.setLineNo(old.getLineNo());
        // 金额重算（若用户未显式传 lineAmount）
        BigDecimal q = entity.getOrderQty() != null ? entity.getOrderQty() : old.getOrderQty();
        BigDecimal p = entity.getUnitPrice() != null ? entity.getUnitPrice() : old.getUnitPrice();
        BigDecimal d = entity.getDiscountRate() != null ? entity.getDiscountRate() : (old.getDiscountRate() == null ? BigDecimal.ZERO : old.getDiscountRate());
        BigDecimal r = entity.getTaxRate() != null ? entity.getTaxRate() : (old.getTaxRate() == null ? BigDecimal.ZERO : old.getTaxRate());
        if (entity.getLineAmount() == null) {
            entity.setLineAmount(q.multiply(p).multiply(BigDecimal.ONE.subtract(d)));
        }
        if (entity.getTaxAmount() == null) {
            entity.setTaxAmount((entity.getLineAmount() == null ? old.getLineAmount() : entity.getLineAmount()).multiply(r));
        }
        boolean ok = mapper.updateById(entity) > 0;
        if (ok) recomputeHeader(old.getPoId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmPurchaseOrderLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        boolean ok = mapper.deleteById(id) > 0;
        if (ok) recomputeHeader(r.getPoId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        ScmPurchaseOrderLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        try {
            Field s = ScmPurchaseOrderLine.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override
    public List<ScmPurchaseOrderLine> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScmPurchaseOrderLine>()
                .orderByDesc("line_id"));
    }

    /* ========== 私有工具：物料回填 + 主单合计重算 ========== */
    private void fillMaterial(ScmPurchaseOrderLine entity) {
        if (entity == null || entity.getMaterialId() == null) return;
        PdmMaterial m;
        try { m = materialService.getById(entity.getMaterialId()); } catch (Exception ignore) { m = null; }
        if (m == null) return;
        if ((entity.getMaterialCode() == null || entity.getMaterialCode().isBlank()) && m.getMaterialCode() != null) entity.setMaterialCode(m.getMaterialCode());
        if ((entity.getMaterialName() == null || entity.getMaterialName().isBlank()) && m.getMaterialName() != null) entity.setMaterialName(m.getMaterialName());
        if ((entity.getSpecification() == null || entity.getSpecification().isBlank()) && m.getSpecification() != null) entity.setSpecification(m.getSpecification());
        if ((entity.getUnit() == null || entity.getUnit().isBlank()) && m.getUnit() != null) entity.setUnit(m.getUnit());
    }

    /** 重算采购订单：undiscAmount = SUM(orderQty*unitPrice); totalAmount = SUM(lineAmount); taxAmount = SUM(taxAmount) */
    private void recomputeHeader(Long poId) {
        if (poId == null) return;
        ScmPurchaseOrder header;
        try { header = orderService.getDetail(poId); } catch (Exception ignore) { return; }
        if (header == null) return;
        List<ScmPurchaseOrderLine> rows = mapper.selectList(
                new LambdaQueryWrapper<ScmPurchaseOrderLine>().eq(ScmPurchaseOrderLine::getPoId, poId));
        BigDecimal undisc = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (ScmPurchaseOrderLine line : rows) {
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
            try { orderService.update(header); } catch (Exception ignore) { log.warn("重算采购订单主单失败: {}", poId); }
        }
    }
}
