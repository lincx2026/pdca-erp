package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.service.IPdmMaterialService;
import com.pcba.erp.scm.entity.ScmPurchaseReturn;
import com.pcba.erp.scm.entity.ScmPurchaseReturnLine;
import com.pcba.erp.scm.mapper.ScmPurchaseReturnLineMapper;
import com.pcba.erp.scm.service.IScmPurchaseReturnLineService;
import com.pcba.erp.scm.service.IScmPurchaseReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmPurchaseReturnLineServiceImpl implements IScmPurchaseReturnLineService {

    private final ScmPurchaseReturnLineMapper mapper;
    private final IPdmMaterialService materialService;
    private final IScmPurchaseReturnService returnService;

    @Override
    public IPage<ScmPurchaseReturnLine> selectPage(int pageNum, int pageSize, Long returnId, String materialCode, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), returnId, materialCode, materialName);
    }

    @Override
    public ScmPurchaseReturnLine getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmPurchaseReturnLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    private void applyDefaults(ScmPurchaseReturnLine e) {
        // 物料回填
        fillMaterial(e);
        // lineNo 按主单递增
        if (e.getLineNo() == null || e.getLineNo() <= 0) {
            Integer max = 0;
            try {
                max = mapper.selectObjs(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScmPurchaseReturnLine>()
                                .select(ScmPurchaseReturnLine::getLineNo)
                                .eq(ScmPurchaseReturnLine::getReturnId, e.getReturnId())
                                .orderByDesc(ScmPurchaseReturnLine::getLineNo)
                                .last("LIMIT 1"))
                        .stream().findFirst().map(o -> ((Number) o).intValue()).orElse(0);
            } catch (Exception ignore) {}
            e.setLineNo(max + 10);
        }
        for (String f : new String[]{"returnQty","unitPrice","returnAmount"}) {
            try {
                Field ff = ScmPurchaseReturnLine.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(e) == null && ff.getType() == BigDecimal.class) ff.set(e, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        // returnAmount = returnQty * unitPrice
        BigDecimal q = e.getReturnQty() == null ? BigDecimal.ZERO : e.getReturnQty();
        BigDecimal p = e.getUnitPrice() == null ? BigDecimal.ZERO : e.getUnitPrice();
        if (e.getReturnAmount() == null || BigDecimal.ZERO.compareTo(e.getReturnAmount()) == 0) {
            e.setReturnAmount(q.multiply(p));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmPurchaseReturnLine entity) {
        applyDefaults(entity);
        mapper.insert(entity);
        recomputeHeader(entity.getReturnId());
        return entity.getLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmPurchaseReturnLine entity) {
        if (entity.getLineId() == null) throw new ServiceException("ID不能为空");
        ScmPurchaseReturnLine old = mapper.selectById(entity.getLineId());
        if (old == null) throw new ServiceException("记录不存在");
        applyDefaults(entity);
        entity.setReturnId(old.getReturnId());
        boolean ok = mapper.updateById(entity) > 0;
        if (ok) recomputeHeader(old.getReturnId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmPurchaseReturnLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        boolean ok = mapper.deleteById(id) > 0;
        if (ok) recomputeHeader(r.getReturnId());
        return ok;
    }

    @Override
    public boolean changeStatus(Long id, Integer status) { return true; }

    @Override
    public List<ScmPurchaseReturnLine> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScmPurchaseReturnLine>().orderByDesc("line_id"));
    }

    /* ========== 私有工具：物料回填 + 主单合计重算 ========== */
    private void fillMaterial(ScmPurchaseReturnLine entity) {
        if (entity == null || entity.getMaterialId() == null) return;
        PdmMaterial m;
        try { m = materialService.getById(entity.getMaterialId()); } catch (Exception ignore) { m = null; }
        if (m == null) return;
        if ((entity.getMaterialCode() == null || entity.getMaterialCode().isBlank()) && m.getMaterialCode() != null) entity.setMaterialCode(m.getMaterialCode());
        if ((entity.getMaterialName() == null || entity.getMaterialName().isBlank()) && m.getMaterialName() != null) entity.setMaterialName(m.getMaterialName());
        if ((entity.getSpecification() == null || entity.getSpecification().isBlank()) && m.getSpecification() != null) entity.setSpecification(m.getSpecification());
        if ((entity.getUnit() == null || entity.getUnit().isBlank()) && m.getUnit() != null) entity.setUnit(m.getUnit());
    }

    /** 重算采购退货 totalQty=SUM(returnQty); totalAmount=SUM(returnAmount) */
    private void recomputeHeader(Long returnId) {
        if (returnId == null) return;
        ScmPurchaseReturn header;
        try { header = returnService.getDetail(returnId); } catch (Exception ignore) { return; }
        if (header == null) return;
        List<ScmPurchaseReturnLine> rows = mapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScmPurchaseReturnLine>().eq(ScmPurchaseReturnLine::getReturnId, returnId));
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ScmPurchaseReturnLine line : rows) {
            BigDecimal q = line.getReturnQty() == null ? BigDecimal.ZERO : line.getReturnQty();
            BigDecimal a = line.getReturnAmount() == null ? BigDecimal.ZERO : line.getReturnAmount();
            totalQty = totalQty.add(q);
            totalAmount = totalAmount.add(a);
        }
        boolean changed = false;
        if (header.getTotalQty() == null || header.getTotalQty().compareTo(totalQty) != 0) {
            header.setTotalQty(totalQty); changed = true;
        }
        if (header.getTotalAmount() == null || header.getTotalAmount().compareTo(totalAmount) != 0) {
            header.setTotalAmount(totalAmount); changed = true;
        }
        if (changed) {
            try { returnService.update(header); } catch (Exception ignore) { log.warn("重算采购退货主单失败: {}", returnId); }
        }
    }
}
