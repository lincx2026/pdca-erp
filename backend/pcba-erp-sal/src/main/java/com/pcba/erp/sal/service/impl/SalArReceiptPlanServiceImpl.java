package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.SalArReceipt;
import com.pcba.erp.sal.entity.SalArReceiptPlan;
import com.pcba.erp.sal.mapper.SalArReceiptMapper;
import com.pcba.erp.sal.mapper.SalArReceiptPlanMapper;
import com.pcba.erp.sal.service.ISalArReceiptPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalArReceiptPlanServiceImpl implements ISalArReceiptPlanService {

    private final SalArReceiptPlanMapper mapper;
    private final SalArReceiptMapper receiptMapper;

    @Override
    public IPage<SalArReceiptPlan> selectPage(int pageNum, int pageSize, String planNo, Long receivableId, Long customerId, String customerName, Integer status, String planDate, Integer phaseNo) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), planNo, receivableId, customerId, customerName, status, planDate, phaseNo);
    }

    @Override
    public SalArReceiptPlan getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalArReceiptPlan r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalArReceiptPlan entity) {
        try {
            Field f = SalArReceiptPlan.class.getDeclaredField("planNo");
            f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank()) {
                f.set(entity, "RP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
            }
        } catch (Exception ignore){}
        try {
            Field f = SalArReceiptPlan.class.getDeclaredField("status");
            f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 0);
        } catch (Exception ignore){}
        for (String fn : new String[]{"ratio","planAmount","actualAmount","unpaidAmount"}) {
            try {
                Field f = SalArReceiptPlan.class.getDeclaredField(fn);
                f.setAccessible(true);
                if (f.get(entity) == null && f.getType() == BigDecimal.class) f.set(entity, BigDecimal.ZERO);
            } catch (Exception ignore){}
        }
        // unpaid 兜底 = planAmount - actualAmount
        BigDecimal plan = entity.getPlanAmount() == null ? BigDecimal.ZERO : entity.getPlanAmount();
        BigDecimal act  = entity.getActualAmount() == null ? BigDecimal.ZERO : entity.getActualAmount();
        if (entity.getUnpaidAmount() == null || BigDecimal.ZERO.compareTo(entity.getUnpaidAmount()) == 0) {
            entity.setUnpaidAmount(plan.subtract(act).max(BigDecimal.ZERO));
        }
        if (mapper.countDuplicate(entity.getPlanNo(), entity.getPlanId()) > 0) throw new ServiceException("编号已存在: " + entity.getPlanNo());
        mapper.insert(entity);
        return entity.getPlanId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalArReceiptPlan entity) {
        if (entity.getPlanId() == null) throw new ServiceException("ID不能为空");
        SalArReceiptPlan old = mapper.selectById(entity.getPlanId());
        if (old == null) throw new ServiceException("记录不存在");
        if (entity.getPlanNo() != null && !entity.getPlanNo().isBlank()
                && mapper.countDuplicate(entity.getPlanNo(), entity.getPlanId()) > 0) {
            throw new ServiceException("编号已存在");
        }
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        SalArReceiptPlan r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        // 仅当存在 收款成功(status=2) 且 实际收款金额>0 的收款单时，才阻止删除计划
        // 允许删除关联 作废(status=9) 或 零金额 收款单的计划
        Long cnt = receiptMapper.selectCount(new LambdaQueryWrapper<SalArReceipt>()
                .eq(SalArReceipt::getPlanId, id)
                .eq(SalArReceipt::getStatus, 2)
                .gt(SalArReceipt::getTotalAmount, BigDecimal.ZERO));
        if (cnt != null && cnt > 0) throw new ServiceException("已存在有效收款单，无法删除计划");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<SalArReceiptPlan> listByReceivable(Long receivableId) {
        return mapper.selectList(new LambdaQueryWrapper<SalArReceiptPlan>()
                .eq(SalArReceiptPlan::getReceivableId, receivableId)
                .orderByAsc(SalArReceiptPlan::getPhaseNo, SalArReceiptPlan::getPlanDate));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean applyReceipt(Long planId, BigDecimal cnyReceivedAmount, Long receiptId) {
        if (planId == null) return true;
        SalArReceiptPlan p = mapper.selectById(planId);
        if (p == null) { log.warn("applyReceipt plan不存在: {}", planId); return false; }
        BigDecimal act = p.getActualAmount() == null ? BigDecimal.ZERO : p.getActualAmount();
        BigDecimal planAmt = p.getPlanAmount() == null ? BigDecimal.ZERO : p.getPlanAmount();
        BigDecimal newAct = act.add(cnyReceivedAmount == null ? BigDecimal.ZERO : cnyReceivedAmount);
        p.setActualAmount(newAct);
        BigDecimal unpaid = planAmt.subtract(newAct);
        p.setUnpaidAmount(unpaid.max(BigDecimal.ZERO));
        int cmp = unpaid.compareTo(BigDecimal.ZERO);
        if (cmp <= 0) p.setStatus(2);
        else if (newAct.compareTo(BigDecimal.ZERO) > 0) p.setStatus(1);
        return mapper.updateById(p) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackReceipt(Long planId, BigDecimal cnyReceivedAmount, Long receiptId) {
        if (planId == null) return true;
        SalArReceiptPlan p = mapper.selectById(planId);
        if (p == null) { log.warn("rollbackReceipt plan不存在: {}", planId); return false; }
        BigDecimal act = p.getActualAmount() == null ? BigDecimal.ZERO : p.getActualAmount();
        BigDecimal planAmt = p.getPlanAmount() == null ? BigDecimal.ZERO : p.getPlanAmount();
        BigDecimal pay = cnyReceivedAmount == null ? BigDecimal.ZERO : cnyReceivedAmount;
        BigDecimal newAct = act.subtract(pay);
        if (newAct.compareTo(BigDecimal.ZERO) < 0) newAct = BigDecimal.ZERO;
        p.setActualAmount(newAct);
        BigDecimal unpaid = planAmt.subtract(newAct).max(BigDecimal.ZERO);
        p.setUnpaidAmount(unpaid);
        int cmp = newAct.compareTo(BigDecimal.ZERO);
        if (cmp <= 0) p.setStatus(0);
        else if (unpaid.compareTo(BigDecimal.ZERO) <= 0) p.setStatus(2);
        else p.setStatus(1);
        return mapper.updateById(p) > 0;
    }
}
