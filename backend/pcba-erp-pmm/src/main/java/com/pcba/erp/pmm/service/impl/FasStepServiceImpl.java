package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.FasOrder;
import com.pcba.erp.pmm.entity.FasStep;
import com.pcba.erp.pmm.mapper.FasOrderMapper;
import com.pcba.erp.pmm.mapper.FasStepMapper;
import com.pcba.erp.pmm.service.IPmmFasStepService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FasStepServiceImpl implements IPmmFasStepService {

    private final FasStepMapper mapper;
    private final FasOrderMapper orderMapper;

    @Override
    public IPage<FasStep> selectPage(int pageNum, int pageSize, Long orderId, String stepName, Integer status) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), orderId, stepName, status);
    }

    @Override
    public List<FasStep> listByOrder(Long orderId) {
        if (orderId == null) throw new ServiceException("工单ID不能为空");
        return mapper.listByOrder(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FasStep entity) {
        if (entity.getOrderId() == null) throw new ServiceException("工单ID不能为空");
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getQtyPass() == null) entity.setQtyPass(0);
        if (entity.getQtyNg() == null) entity.setQtyNg(0);
        mapper.insert(entity);
        return entity.getStepId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FasStep entity) {
        if (entity.getStepId() == null) throw new ServiceException("工序ID不能为空");
        FasStep old = mapper.selectById(entity.getStepId());
        if (old == null) throw new ServiceException("工序不存在");
        mapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (id == null) throw new ServiceException("工序ID不能为空");
        FasStep step = mapper.selectById(id);
        if (step == null) throw new ServiceException("工序不存在");
        Integer s = step.getStatus();
        if (s != null && s == 1) {
            throw new ServiceException("进行中的工序禁止删除");
        }
        mapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startStep(Long stepId, String operator) {
        FasStep step = requireStep(stepId);
        if (step.getStatus() != null && step.getStatus() != 0) {
            throw new ServiceException("只有待开始的工序才能开始");
        }
        step.setStatus(1);
        step.setOperator(operator);
        step.setStartTime(LocalDateTime.now());
        mapper.updateById(step);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeStep(Long stepId, Integer qtyPass, Integer qtyNg, String operator) {
        FasStep step = requireStep(stepId);
        if (step.getStatus() == null || step.getStatus() != 1) {
            throw new ServiceException("只有进行中的工序才能完成");
        }
        step.setStatus(2);
        step.setQtyPass(qtyPass == null ? 0 : qtyPass);
        step.setQtyNg(qtyNg == null ? 0 : qtyNg);
        if (operator != null) step.setOperator(operator);
        step.setEndTime(LocalDateTime.now());
        mapper.updateById(step);

        // 同步更新工单的完工数量与不良数量
        if (step.getOrderId() != null) {
            FasOrder order = orderMapper.selectById(step.getOrderId());
            if (order != null) {
                int pass = qtyPass == null ? 0 : qtyPass;
                int ng = qtyNg == null ? 0 : qtyNg;
                int curComplete = order.getQtyComplete() == null ? 0 : order.getQtyComplete();
                int curNg = order.getQtyNg() == null ? 0 : order.getQtyNg();
                order.setQtyComplete(curComplete + pass);
                order.setQtyNg(curNg + ng);
                orderMapper.updateById(order);
            }
        }
    }

    private FasStep requireStep(Long stepId) {
        if (stepId == null) throw new ServiceException("工序ID不能为空");
        FasStep step = mapper.selectById(stepId);
        if (step == null) throw new ServiceException("工序不存在");
        return step;
    }
}
