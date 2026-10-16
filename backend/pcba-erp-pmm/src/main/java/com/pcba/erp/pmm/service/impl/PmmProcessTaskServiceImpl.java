package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmProcessTask;
import com.pcba.erp.pmm.mapper.PmmProcessTaskMapper;
import com.pcba.erp.pmm.service.IPmmProcessTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PmmProcessTaskServiceImpl implements IPmmProcessTaskService {

    private final PmmProcessTaskMapper mapper;

    @Override
    public IPage<PmmProcessTask> selectPage(int pageNum, int pageSize, Long workOrderId, String processName,
                                            String workCenter, Integer status, Long assignedUserId) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), workOrderId, processName, workCenter, status, assignedUserId);
    }

    @Override
    public PmmProcessTask getDetail(Long id) {
        if (id == null) throw new ServiceException("工序任务ID不能为空");
        PmmProcessTask w = mapper.selectById(id);
        if (w == null) throw new ServiceException("工序任务不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmProcessTask entity) {
        if (entity.getWorkOrderId() == null) throw new ServiceException("工单ID不能为空");
        if (entity.getSequence() == null) entity.setSequence(10);
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getPlanQty() == null) entity.setPlanQty(BigDecimal.ZERO);
        if (entity.getInputQty() == null) entity.setInputQty(BigDecimal.ZERO);
        if (entity.getPassQty() == null) entity.setPassQty(BigDecimal.ZERO);
        if (entity.getFailQty() == null) entity.setFailQty(BigDecimal.ZERO);
        if (entity.getIsKeyProcess() == null) entity.setIsKeyProcess(0);
        if (entity.getIsQcRequired() == null) entity.setIsQcRequired(0);
        mapper.insert(entity);
        return entity.getTaskId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmProcessTask entity) {
        if (entity.getTaskId() == null) throw new ServiceException("工序任务ID不能为空");
        PmmProcessTask old = mapper.selectById(entity.getTaskId());
        if (old == null) throw new ServiceException("工序任务不存在");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        PmmProcessTask w = mapper.selectById(id);
        if (w == null) throw new ServiceException("工序任务不存在");
        if (w.getStatus() != null && (w.getStatus() == 20 || w.getStatus() == 40 || w.getStatus() == 50))
            throw new ServiceException("加工中/已完成/已流转的工序任务不能删除");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("工序任务ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        PmmProcessTask w = mapper.selectById(id);
        if (w == null) throw new ServiceException("工序任务不存在");
        w.setStatus(status);
        return mapper.updateById(w) > 0;
    }

    @Override
    public List<PmmProcessTask> listByWorkOrder(Long workOrderId) {
        return mapper.selectList(new LambdaQueryWrapper<PmmProcessTask>()
                .eq(workOrderId != null, PmmProcessTask::getWorkOrderId, workOrderId)
                .orderByAsc(PmmProcessTask::getSequence, PmmProcessTask::getTaskId));
    }
}
