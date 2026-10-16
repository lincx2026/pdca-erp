package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmQualityDefect;
import com.pcba.erp.pmm.mapper.PmmQualityDefectMapper;
import com.pcba.erp.pmm.service.IPmmQualityDefectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PmmQualityDefectServiceImpl implements IPmmQualityDefectService {

    private final PmmQualityDefectMapper mapper;

    @Override
    public IPage<PmmQualityDefect> selectPage(int pageNum, int pageSize, Long workOrderId, String defectCode,
                                              String defectLevel, String checkType, String disposition,
                                              String processName, Long inspectorId) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), workOrderId, defectCode, defectLevel,
                checkType, disposition, processName, inspectorId);
    }

    @Override
    public PmmQualityDefect getDetail(Long id) {
        if (id == null) throw new ServiceException("不良ID不能为空");
        PmmQualityDefect w = mapper.selectById(id);
        if (w == null) throw new ServiceException("不良记录不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmQualityDefect entity) {
        if (entity.getDefectQty() == null) entity.setDefectQty(BigDecimal.ONE);
        if (entity.getSampleQty() == null) entity.setSampleQty(BigDecimal.ZERO);
        if (entity.getDisposition() == null) entity.setDisposition("PENDING");
        mapper.insert(entity);
        return entity.getDefectId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmQualityDefect entity) {
        if (entity.getDefectId() == null) throw new ServiceException("不良ID不能为空");
        PmmQualityDefect old = mapper.selectById(entity.getDefectId());
        if (old == null) throw new ServiceException("不良记录不存在");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("不良ID不能为空");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<PmmQualityDefect> listByWorkOrder(Long workOrderId) {
        return mapper.selectList(new LambdaQueryWrapper<PmmQualityDefect>()
                .eq(workOrderId != null, PmmQualityDefect::getWorkOrderId, workOrderId)
                .orderByDesc(PmmQualityDefect::getDefectId));
    }
}
