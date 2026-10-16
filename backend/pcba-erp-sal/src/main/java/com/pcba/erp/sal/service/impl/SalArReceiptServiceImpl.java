package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.*;
import com.pcba.erp.sal.mapper.SalArReceiptMapper;
import com.pcba.erp.sal.mapper.SalCustomerMapper;
import com.pcba.erp.sal.service.ISalArReceivableService;
import com.pcba.erp.sal.service.ISalArReceiptPlanService;
import com.pcba.erp.sal.service.ISalArReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalArReceiptServiceImpl implements ISalArReceiptService {

    private final SalArReceiptMapper mapper;
    private final SalCustomerMapper customerMapper;
    private final ISalArReceivableService receivableService;
    private final ISalArReceiptPlanService planService;

    @Override
    public IPage<SalArReceipt> selectPage(int pageNum, int pageSize, String receiptNo, Long customerId, String customerName, Integer status, String receiptDate, Long planId, Long receivableId) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), receiptNo, customerId, customerName, status, receiptDate, planId, receivableId);
    }

    @Override
    public SalArReceipt getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalArReceipt r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalArReceipt entity) {
        // 单号
        try {
            Field f = SalArReceipt.class.getDeclaredField("receiptNo"); f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank())
                f.set(entity, "RC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        } catch (Exception ignore){}
        try {
            Field f = SalArReceipt.class.getDeclaredField("receiptDate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore){}
        try {
            Field f = SalArReceipt.class.getDeclaredField("receiptCurrency"); f.setAccessible(true);
            Object v = f.get(entity); if (v == null || v.toString().isBlank()) f.set(entity, "CNY");
        } catch (Exception ignore){}
        try {
            Field f = SalArReceipt.class.getDeclaredField("exchangeRate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, BigDecimal.ONE);
        } catch (Exception ignore){}
        try {
            Field f = SalArReceipt.class.getDeclaredField("status"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 0);
        } catch (Exception ignore){}
        for (String fn : new String[]{"totalAmount","cnyTotalAmount","feeAmount","invoiceAmount"}) {
            try {
                Field f = SalArReceipt.class.getDeclaredField(fn); f.setAccessible(true);
                if (f.get(entity) == null && f.getType() == BigDecimal.class) f.set(entity, BigDecimal.ZERO);
            } catch (Exception ignore){}
        }
        // 客户信息/付款方信息兜底
        if (entity.getCustomerId() != null) {
            SalCustomer c = customerMapper.selectById(entity.getCustomerId());
            if (c != null) {
                if (entity.getCustomerCode() == null) entity.setCustomerCode(c.getCustomerCode());
                if (entity.getCustomerName() == null) entity.setCustomerName(c.getCustomerName());
                if (entity.getPayerName() == null) entity.setPayerName(c.getPayerName() != null ? c.getPayerName() : c.getCustomerName());
                if (entity.getPayerBank() == null) entity.setPayerBank(c.getPayerBank());
                if (entity.getPayerAccount() == null) entity.setPayerAccount(c.getPayerAccount());
            }
        }
        // cnyTotalAmount 兜底 = totalAmount * exchangeRate
        BigDecimal total = entity.getTotalAmount() == null ? BigDecimal.ZERO : entity.getTotalAmount();
        BigDecimal rate = entity.getExchangeRate() == null ? BigDecimal.ONE : entity.getExchangeRate();
        if (entity.getCnyTotalAmount() == null || BigDecimal.ZERO.compareTo(entity.getCnyTotalAmount()) == 0)
            entity.setCnyTotalAmount(total.multiply(rate));
        if (mapper.countDuplicate(entity.getReceiptNo(), entity.getReceiptId()) > 0)
            throw new ServiceException("编号已存在: " + entity.getReceiptNo());
        mapper.insert(entity);
        return entity.getReceiptId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalArReceipt entity) {
        if (entity.getReceiptId() == null) throw new ServiceException("ID不能为空");
        SalArReceipt old = mapper.selectById(entity.getReceiptId());
        if (old == null) throw new ServiceException("记录不存在");
        if (old.getStatus() != null && old.getStatus() >= 2)
            throw new ServiceException("已收款成功/失败的单据不允许直接修改");
        if (entity.getReceiptNo() != null && !entity.getReceiptNo().isBlank()
                && mapper.countDuplicate(entity.getReceiptNo(), entity.getReceiptId()) > 0)
            throw new ServiceException("编号已存在");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        SalArReceipt r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        if (r.getStatus() != null && r.getStatus() == 2)
            throw new ServiceException("收款成功单据不允许删除，请使用作废");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<SalArReceipt> options() {
        return mapper.selectList(new LambdaQueryWrapper<SalArReceipt>().orderByDesc(SalArReceipt::getReceiptId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean audit(Long id, String auditBy) {
        SalArReceipt r = require(id);
        Integer st = r.getStatus() == null ? -1 : r.getStatus();
        if (st != 0) throw new ServiceException("仅草稿状态可审批");
        r.setStatus(1);
        r.setAuditBy(auditBy);
        r.setAuditTime(LocalDateTime.now());
        return mapper.updateById(r) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(Long id, String receiptBy) {
        SalArReceipt r = require(id);
        Integer st = r.getStatus() == null ? -1 : r.getStatus();
        if (st != 1) throw new ServiceException("仅已审批状态可确认收款成功");
        r.setStatus(2);
        r.setReceiptBy(receiptBy);
        r.setReceiptTime(LocalDateTime.now());
        boolean ok = mapper.updateById(r) > 0;
        if (!ok) return false;
        // 回写应收+计划
        BigDecimal cny = r.getCnyTotalAmount() == null ? BigDecimal.ZERO : r.getCnyTotalAmount();
        if (r.getReceivableId() != null) receivableService.applyReceipt(r.getReceivableId(), cny, r.getReceiptId());
        if (r.getPlanId() != null) planService.applyReceipt(r.getPlanId(), cny, r.getReceiptId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean fail(Long id, String failReason) {
        SalArReceipt r = require(id);
        Integer st = r.getStatus() == null ? -1 : r.getStatus();
        if (st != 1) throw new ServiceException("仅已审批状态可标记失败");
        r.setStatus(3);
        r.setFailReason(failReason);
        return mapper.updateById(r) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long id, String reason) {
        SalArReceipt r = require(id);
        Integer st = r.getStatus() == null ? -1 : r.getStatus();
        if (st == 9) return true; // 幂等
        BigDecimal cny = BigDecimal.ZERO;
        if (st == 2) {
            // 只有收款成功才需要回滚金额
            cny = r.getCnyTotalAmount() == null ? BigDecimal.ZERO : r.getCnyTotalAmount();
        }
        // 回滚(幂等：不存在则warn不抛异常)
        try {
            if (r.getReceivableId() != null) receivableService.rollbackReceipt(r.getReceivableId(), cny, r.getReceiptId());
        } catch (Exception ex) { log.warn("cancel 回滚应收异常 receivableId={}: {}", r.getReceivableId(), ex.getMessage()); }
        try {
            if (r.getPlanId() != null) planService.rollbackReceipt(r.getPlanId(), cny, r.getReceiptId());
        } catch (Exception ex) { log.warn("cancel 回滚计划异常 planId={}: {}", r.getPlanId(), ex.getMessage()); }
        r.setStatus(9);
        if (r.getRemark() == null || r.getRemark().isBlank()) r.setRemark(reason);
        return mapper.updateById(r) > 0;
    }

    private SalArReceipt require(Long id) {
        SalArReceipt r = mapper.selectById(id);
        if (r == null) throw new ServiceException("收款单不存在: " + id);
        return r;
    }
}
