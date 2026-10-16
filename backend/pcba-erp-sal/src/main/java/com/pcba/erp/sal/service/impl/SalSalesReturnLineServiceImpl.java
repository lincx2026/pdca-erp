package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.service.IPdmMaterialService;
import com.pcba.erp.sal.entity.SalSalesReturn;
import com.pcba.erp.sal.entity.SalSalesReturnLine;
import com.pcba.erp.sal.mapper.SalSalesReturnLineMapper;
import com.pcba.erp.sal.service.ISalSalesReturnLineService;
import com.pcba.erp.sal.service.ISalSalesReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalSalesReturnLineServiceImpl implements ISalSalesReturnLineService {

    private final SalSalesReturnLineMapper mapper;
    private final IPdmMaterialService materialService;
    private final ISalSalesReturnService returnService;

    @Override
    public IPage<SalSalesReturnLine> selectPage(int pageNum, int pageSize, Long returnId, String materialCode, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), returnId, materialCode, materialName);
    }

    @Override
    public SalSalesReturnLine getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalSalesReturnLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalSalesReturnLine entity) {
        // 物料回填
        fillMaterial(entity);
        // lineNo 按主单递增(max+10)
        if (entity.getLineNo() == null || entity.getLineNo() <= 0) {
            Integer max = 0;
            try {
                max = mapper.selectObjs(new LambdaQueryWrapper<SalSalesReturnLine>()
                                .select(SalSalesReturnLine::getLineNo)
                                .eq(SalSalesReturnLine::getReturnId, entity.getReturnId())
                                .orderByDesc(SalSalesReturnLine::getLineNo)
                                .last("LIMIT 1"))
                        .stream().findFirst().map(o -> ((Number) o).intValue()).orElse(0);
            } catch (Exception ignore) {}
            entity.setLineNo(max + 10);
        }
        // 默认值
        if (entity.getReturnQty() == null) entity.setReturnQty(BigDecimal.ZERO);
        if (entity.getUnitPrice() == null) entity.setUnitPrice(BigDecimal.ZERO);
        // 金额自动计算：returnAmount = returnQty * unitPrice
        BigDecimal q = entity.getReturnQty();
        BigDecimal p = entity.getUnitPrice();
        if (entity.getReturnAmount() == null || BigDecimal.ZERO.compareTo(entity.getReturnAmount()) == 0) {
            entity.setReturnAmount(q.multiply(p));
        }

        mapper.insert(entity);
        recomputeHeader(entity.getReturnId());
        return entity.getLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalSalesReturnLine entity) {
        Long id = entity.getLineId();
        if (id == null) throw new ServiceException("ID不能为空");
        SalSalesReturnLine old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        fillMaterial(entity);
        entity.setReturnId(old.getReturnId());
        if (entity.getLineNo() == null) entity.setLineNo(old.getLineNo());
        // 金额重算(若用户未显式传值)
        BigDecimal q = entity.getReturnQty() != null ? entity.getReturnQty() : old.getReturnQty();
        BigDecimal p = entity.getUnitPrice() != null ? entity.getUnitPrice() : old.getUnitPrice();
        if (entity.getReturnAmount() == null) {
            entity.setReturnAmount(q.multiply(p));
        }
        boolean ok = mapper.updateById(entity) > 0;
        if (ok) recomputeHeader(old.getReturnId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        SalSalesReturnLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        boolean ok = mapper.deleteById(id) > 0;
        if (ok) recomputeHeader(r.getReturnId());
        return ok;
    }

    /* ========== 私有工具：物料回填 + 主单合计重算 ========== */
    @Override
    public void fillMaterial(SalSalesReturnLine entity) {
        if (entity == null || entity.getMaterialId() == null) return;
        PdmMaterial m;
        try { m = materialService.getById(entity.getMaterialId()); } catch (Exception ignore) { m = null; }
        if (m == null) return;
        if ((entity.getMaterialCode() == null || entity.getMaterialCode().isBlank()) && m.getMaterialCode() != null) entity.setMaterialCode(m.getMaterialCode());
        if ((entity.getMaterialName() == null || entity.getMaterialName().isBlank()) && m.getMaterialName() != null) entity.setMaterialName(m.getMaterialName());
        if ((entity.getSpecification() == null || entity.getSpecification().isBlank()) && m.getSpecification() != null) entity.setSpecification(m.getSpecification());
        if ((entity.getUnit() == null || entity.getUnit().isBlank()) && m.getUnit() != null) entity.setUnit(m.getUnit());
    }

    /** 重算销售退货主单：totalAmount = Σ(returnAmount) */
    @Override
    public void recomputeHeader(Long returnId) {
        if (returnId == null) return;
        SalSalesReturn header;
        try { header = returnService.getDetail(returnId); } catch (Exception ignore) { return; }
        if (header == null) return;
        List<SalSalesReturnLine> rows = mapper.selectList(
                new LambdaQueryWrapper<SalSalesReturnLine>().eq(SalSalesReturnLine::getReturnId, returnId));
        BigDecimal total = BigDecimal.ZERO;
        for (SalSalesReturnLine line : rows) {
            BigDecimal la = line.getReturnAmount() == null ? BigDecimal.ZERO : line.getReturnAmount();
            total = total.add(la);
        }
        boolean changed = false;
        if (header.getTotalAmount() == null || header.getTotalAmount().compareTo(total) != 0) {
            header.setTotalAmount(total); changed = true;
        }
        if (changed) {
            try { returnService.update(header); } catch (Exception ignore) { log.warn("重算销售退货主单失败: {}", returnId); }
        }
    }
}
