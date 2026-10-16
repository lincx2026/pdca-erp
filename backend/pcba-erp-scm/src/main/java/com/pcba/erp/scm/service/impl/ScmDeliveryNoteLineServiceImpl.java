package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterial;
import com.pcba.erp.pdm.service.IPdmMaterialService;
import com.pcba.erp.scm.entity.ScmDeliveryNote;
import com.pcba.erp.scm.entity.ScmDeliveryNoteLine;
import com.pcba.erp.scm.mapper.ScmDeliveryNoteLineMapper;
import com.pcba.erp.scm.service.IScmDeliveryNoteLineService;
import com.pcba.erp.scm.service.IScmDeliveryNoteService;
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
public class ScmDeliveryNoteLineServiceImpl implements IScmDeliveryNoteLineService {

    private final ScmDeliveryNoteLineMapper mapper;
    private final IPdmMaterialService materialService;
    private final IScmDeliveryNoteService deliveryService;

    @Override
    public IPage<ScmDeliveryNoteLine> selectPage(int pageNum, int pageSize, Long deliveryId, String materialCode, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), deliveryId, materialCode, materialName);
    }

    @Override
    public ScmDeliveryNoteLine getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmDeliveryNoteLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    private void applyDefaults(ScmDeliveryNoteLine entity) {
        // 物料回填
        fillMaterial(entity);
        // lineNo 按主单递增
        if (entity.getLineNo() == null || entity.getLineNo() <= 0) {
            Integer max = 0;
            try {
                max = mapper.selectObjs(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScmDeliveryNoteLine>()
                                .select(ScmDeliveryNoteLine::getLineNo)
                                .eq(ScmDeliveryNoteLine::getDeliveryId, entity.getDeliveryId())
                                .orderByDesc(ScmDeliveryNoteLine::getLineNo)
                                .last("LIMIT 1"))
                        .stream().findFirst().map(o -> ((Number) o).intValue()).orElse(0);
            } catch (Exception ignore) {}
            entity.setLineNo(max + 10);
        }
        for (String f : new String[]{"deliveryQty","acceptedQty","rejectedQty","concessionQty"}) {
            try {
                Field ff = ScmDeliveryNoteLine.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmDeliveryNoteLine entity) {
        applyDefaults(entity);
        mapper.insert(entity);
        recomputeHeader(entity.getDeliveryId());
        return entity.getLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmDeliveryNoteLine entity) {
        if (entity.getLineId() == null) throw new ServiceException("ID不能为空");
        ScmDeliveryNoteLine old = mapper.selectById(entity.getLineId());
        if (old == null) throw new ServiceException("记录不存在");
        applyDefaults(entity);
        entity.setDeliveryId(old.getDeliveryId());
        boolean ok = mapper.updateById(entity) > 0;
        if (ok) recomputeHeader(old.getDeliveryId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmDeliveryNoteLine r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        boolean ok = mapper.deleteById(id) > 0;
        if (ok) recomputeHeader(r.getDeliveryId());
        return ok;
    }

    @Override
    public boolean changeStatus(Long id, Integer status) { return true; }

    @Override
    public List<ScmDeliveryNoteLine> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScmDeliveryNoteLine>().orderByDesc("line_id"));
    }

    /* ========== 私有工具：物料回填 + 主单合计重算 ========== */
    private void fillMaterial(ScmDeliveryNoteLine entity) {
        if (entity == null || entity.getMaterialId() == null) return;
        PdmMaterial m;
        try { m = materialService.getById(entity.getMaterialId()); } catch (Exception ignore) { m = null; }
        if (m == null) return;
        if ((entity.getMaterialCode() == null || entity.getMaterialCode().isBlank()) && m.getMaterialCode() != null) entity.setMaterialCode(m.getMaterialCode());
        if ((entity.getMaterialName() == null || entity.getMaterialName().isBlank()) && m.getMaterialName() != null) entity.setMaterialName(m.getMaterialName());
        if ((entity.getSpecification() == null || entity.getSpecification().isBlank()) && m.getSpecification() != null) entity.setSpecification(m.getSpecification());
        if ((entity.getUnit() == null || entity.getUnit().isBlank()) && m.getUnit() != null) entity.setUnit(m.getUnit());
    }

    /** 重算到货单 totalQty=SUM(deliveryQty); totalAmount=SUM(acceptedQty*unitPrice)，无单价按 0 */
    private void recomputeHeader(Long deliveryId) {
        if (deliveryId == null) return;
        ScmDeliveryNote header;
        try { header = deliveryService.getDetail(deliveryId); } catch (Exception ignore) { return; }
        if (header == null) return;
        List<ScmDeliveryNoteLine> rows = mapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScmDeliveryNoteLine>().eq(ScmDeliveryNoteLine::getDeliveryId, deliveryId));
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ScmDeliveryNoteLine line : rows) {
            BigDecimal q = line.getDeliveryQty() == null ? BigDecimal.ZERO : line.getDeliveryQty();
            totalQty = totalQty.add(q);
        }
        boolean changed = false;
        if (header.getTotalQty() == null || header.getTotalQty().compareTo(totalQty) != 0) {
            header.setTotalQty(totalQty); changed = true;
        }
        if (header.getTotalAmount() == null || header.getTotalAmount().compareTo(totalAmount) != 0) {
            header.setTotalAmount(totalAmount); changed = true;
        }
        if (changed) {
            try { deliveryService.update(header); } catch (Exception ignore) { log.warn("重算到货单主单失败: {}", deliveryId); }
        }
    }
}
