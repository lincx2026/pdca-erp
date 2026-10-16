package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmWorkOrderBom;
import com.pcba.erp.pmm.mapper.PmmWorkOrderBomMapper;
import com.pcba.erp.pmm.service.IPmmWorkOrderBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PmmWorkOrderBomServiceImpl implements IPmmWorkOrderBomService {

    private final PmmWorkOrderBomMapper mapper;

    @Override
    public IPage<PmmWorkOrderBom> selectPage(int pageNum, int pageSize, Long workOrderId, String materialCode, String materialName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), workOrderId, materialCode, materialName);
    }

    @Override
    public PmmWorkOrderBom getDetail(Long id) {
        if (id == null) throw new ServiceException("BOM行ID不能为空");
        PmmWorkOrderBom w = mapper.selectById(id);
        if (w == null) throw new ServiceException("BOM行不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmWorkOrderBom entity) {
        if (entity.getWorkOrderId() == null) throw new ServiceException("工单ID不能为空");
        if (entity.getLineNo() == null) entity.setLineNo(10);
        if (mapper.countDuplicateLine(entity.getWorkOrderId(), entity.getLineNo(), null) > 0)
            throw new ServiceException("该工单下已存在相同行号");
        if (entity.getQtyPerUnit() == null) entity.setQtyPerUnit(BigDecimal.ZERO);
        if (entity.getRequiredQty() == null) entity.setRequiredQty(BigDecimal.ZERO);
        if (entity.getIssuedQty() == null) entity.setIssuedQty(BigDecimal.ZERO);
        if (entity.getReturnedQty() == null) entity.setReturnedQty(BigDecimal.ZERO);
        if (entity.getScrapRate() == null) entity.setScrapRate(BigDecimal.ZERO);
        mapper.insert(entity);
        return entity.getBomLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmWorkOrderBom entity) {
        if (entity.getBomLineId() == null) throw new ServiceException("BOM行ID不能为空");
        PmmWorkOrderBom old = mapper.selectById(entity.getBomLineId());
        if (old == null) throw new ServiceException("BOM行不存在");
        if (entity.getWorkOrderId() != null && entity.getLineNo() != null) {
            if (mapper.countDuplicateLine(entity.getWorkOrderId(), entity.getLineNo(), entity.getBomLineId()) > 0)
                throw new ServiceException("该工单下已存在相同行号");
        }
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("BOM行ID不能为空");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<PmmWorkOrderBom> listByWorkOrder(Long workOrderId) {
        return mapper.selectList(new LambdaQueryWrapper<PmmWorkOrderBom>()
                .eq(workOrderId != null, PmmWorkOrderBom::getWorkOrderId, workOrderId)
                .orderByAsc(PmmWorkOrderBom::getLineNo));
    }
}
