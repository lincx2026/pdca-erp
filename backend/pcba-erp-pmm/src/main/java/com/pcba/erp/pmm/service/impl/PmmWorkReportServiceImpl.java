package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmWorkReport;
import com.pcba.erp.pmm.mapper.PmmWorkReportMapper;
import com.pcba.erp.pmm.service.IPmmWorkReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PmmWorkReportServiceImpl implements IPmmWorkReportService {

    private final PmmWorkReportMapper mapper;

    @Override
    public IPage<PmmWorkReport> selectPage(int pageNum, int pageSize, String reportNo, Long workOrderId, Long taskId,
                                           Long operatorId, String reportType, String shift) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), reportNo, workOrderId, taskId, operatorId, reportType, shift);
    }

    @Override
    public PmmWorkReport getDetail(Long id) {
        if (id == null) throw new ServiceException("报工ID不能为空");
        PmmWorkReport w = mapper.selectById(id);
        if (w == null) throw new ServiceException("报工记录不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmWorkReport entity) {
        if (entity.getWorkOrderId() == null) throw new ServiceException("工单ID不能为空");
        if (entity.getTaskId() == null) throw new ServiceException("工序任务ID不能为空");
        if (entity.getReportNo() != null) {
            if (mapper.countDuplicateNo(entity.getReportNo(), null) > 0)
                throw new ServiceException("报工单号已存在");
        }
        if (entity.getReportType() == null) entity.setReportType("PROGRESS");
        if (entity.getInputQty() == null) entity.setInputQty(BigDecimal.ZERO);
        if (entity.getPassQty() == null) entity.setPassQty(BigDecimal.ZERO);
        if (entity.getFailQty() == null) entity.setFailQty(BigDecimal.ZERO);
        if (entity.getUsedMinutes() == null) entity.setUsedMinutes(BigDecimal.ZERO);
        mapper.insert(entity);
        return entity.getReportId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmWorkReport entity) {
        if (entity.getReportId() == null) throw new ServiceException("报工ID不能为空");
        PmmWorkReport old = mapper.selectById(entity.getReportId());
        if (old == null) throw new ServiceException("报工记录不存在");
        if (entity.getReportNo() != null) {
            if (mapper.countDuplicateNo(entity.getReportNo(), entity.getReportId()) > 0)
                throw new ServiceException("报工单号已存在");
        }
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("报工ID不能为空");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<PmmWorkReport> listByWorkOrder(Long workOrderId) {
        return mapper.selectList(new LambdaQueryWrapper<PmmWorkReport>()
                .eq(workOrderId != null, PmmWorkReport::getWorkOrderId, workOrderId)
                .orderByDesc(PmmWorkReport::getReportId));
    }
}
