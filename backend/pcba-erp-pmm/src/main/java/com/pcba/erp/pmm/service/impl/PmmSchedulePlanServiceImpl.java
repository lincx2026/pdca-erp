package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmSchedulePlan;
import com.pcba.erp.pmm.mapper.PmmSchedulePlanMapper;
import com.pcba.erp.pmm.service.IPmmSchedulePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PmmSchedulePlanServiceImpl implements IPmmSchedulePlanService {

    private final PmmSchedulePlanMapper mapper;

    @Override
    public IPage<PmmSchedulePlan> selectPage(int pageNum, int pageSize, LocalDate scheduleDate, String shift,
                                             Long workOrderId, String workCenter, String machineCode) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), scheduleDate, shift, workOrderId, workCenter, machineCode);
    }

    @Override
    public PmmSchedulePlan getDetail(Long id) {
        if (id == null) throw new ServiceException("排产ID不能为空");
        PmmSchedulePlan w = mapper.selectById(id);
        if (w == null) throw new ServiceException("排产记录不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmSchedulePlan entity) {
        if (entity.getWorkOrderId() == null) throw new ServiceException("工单ID不能为空");
        if (entity.getScheduleDate() == null && entity.getStartTime() != null)
            entity.setScheduleDate(entity.getStartTime().toLocalDate());
        if (entity.getPlanQty() == null) entity.setPlanQty(BigDecimal.ZERO);
        if (entity.getIsFixed() == null) entity.setIsFixed(0);
        if (entity.getSequence() == null) entity.setSequence(0);
        mapper.insert(entity);
        return entity.getPlanId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmSchedulePlan entity) {
        if (entity.getPlanId() == null) throw new ServiceException("排产ID不能为空");
        PmmSchedulePlan old = mapper.selectById(entity.getPlanId());
        if (old == null) throw new ServiceException("排产记录不存在");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("排产ID不能为空");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<PmmSchedulePlan> listByDate(LocalDate date) {
        return mapper.selectList(new LambdaQueryWrapper<PmmSchedulePlan>()
                .eq(date != null, PmmSchedulePlan::getScheduleDate, date)
                .orderByAsc(PmmSchedulePlan::getWorkCenter, PmmSchedulePlan::getMachineCode,
                        PmmSchedulePlan::getSequence, PmmSchedulePlan::getStartTime));
    }
}
