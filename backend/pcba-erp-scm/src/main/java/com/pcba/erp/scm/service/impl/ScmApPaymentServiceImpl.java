package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.*;
import com.pcba.erp.scm.mapper.ScmApPaymentMapper;
import com.pcba.erp.scm.mapper.ScmSupplierMapper;
import com.pcba.erp.scm.service.IFinVoucherService;
import com.pcba.erp.scm.service.IScmApPayableService;
import com.pcba.erp.scm.service.IScmApPaymentPlanService;
import com.pcba.erp.scm.service.IScmApPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
public class ScmApPaymentServiceImpl implements IScmApPaymentService {

    private final ScmApPaymentMapper mapper;
    private final ScmSupplierMapper supplierMapper;
    private final IScmApPayableService payableService;
    private final IScmApPaymentPlanService planService;
    @Lazy private final IFinVoucherService voucherService;

    @Override
    public IPage<ScmApPayment> selectPage(int pageNum, int pageSize, String paymentNo, Long supplierId, String supplierName, Integer status, String payDate, Long planId, Long payableId) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), paymentNo, supplierId, supplierName, status, payDate, planId, payableId);
    }

    @Override
    public ScmApPayment getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmApPayment r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmApPayment entity) {
        // 单号
        try {
            Field f = ScmApPayment.class.getDeclaredField("paymentNo"); f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank())
                f.set(entity, "PY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        } catch (Exception ignore){}
        try {
            Field f = ScmApPayment.class.getDeclaredField("payDate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore){}
        try {
            Field f = ScmApPayment.class.getDeclaredField("payCurrency"); f.setAccessible(true);
            Object v = f.get(entity); if (v == null || v.toString().isBlank()) f.set(entity, "CNY");
        } catch (Exception ignore){}
        try {
            Field f = ScmApPayment.class.getDeclaredField("payExchangeRate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, BigDecimal.ONE);
        } catch (Exception ignore){}
        try {
            Field f = ScmApPayment.class.getDeclaredField("status"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 0);
        } catch (Exception ignore){}
        for (String fn : new String[]{"totalAmount","cnyTotalAmount","feeAmount","invoiceAmount"}) {
            try {
                Field f = ScmApPayment.class.getDeclaredField(fn); f.setAccessible(true);
                if (f.get(entity) == null && f.getType() == BigDecimal.class) f.set(entity, BigDecimal.ZERO);
            } catch (Exception ignore){}
        }
        // 供方信息/收款方信息兜底
        if (entity.getSupplierId() != null) {
            ScmSupplier s = supplierMapper.selectById(entity.getSupplierId());
            if (s != null) {
                if (entity.getSupplierCode() == null) entity.setSupplierCode(s.getSupplierCode());
                if (entity.getSupplierName() == null) entity.setSupplierName(s.getSupplierName());
                if (entity.getPayeeName() == null) entity.setPayeeName(s.getPayeeName() != null ? s.getPayeeName() : s.getSupplierName());
                if (entity.getPayeeBank() == null) entity.setPayeeBank(s.getPayeeBank());
                if (entity.getPayeeAccount() == null) entity.setPayeeAccount(s.getPayeeAccount());
                if (entity.getPayeeSwift() == null) entity.setPayeeSwift(s.getPayeeSwift());
            }
        }
        // cnyTotalAmount 兜底 = totalAmount * payExchangeRate
        BigDecimal total = entity.getTotalAmount() == null ? BigDecimal.ZERO : entity.getTotalAmount();
        BigDecimal rate = entity.getPayExchangeRate() == null ? BigDecimal.ONE : entity.getPayExchangeRate();
        if (entity.getCnyTotalAmount() == null || BigDecimal.ZERO.compareTo(entity.getCnyTotalAmount()) == 0)
            entity.setCnyTotalAmount(total.multiply(rate));
        if (mapper.countDuplicate(entity.getPaymentNo(), entity.getPaymentId()) > 0)
            throw new ServiceException("编号已存在: " + entity.getPaymentNo());
        mapper.insert(entity);
        return entity.getPaymentId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmApPayment entity) {
        if (entity.getPaymentId() == null) throw new ServiceException("ID不能为空");
        ScmApPayment old = mapper.selectById(entity.getPaymentId());
        if (old == null) throw new ServiceException("记录不存在");
        if (old.getStatus() != null && old.getStatus() >= 2)
            throw new ServiceException("已付款成功/失败的单据不允许直接修改");
        if (entity.getPaymentNo() != null && !entity.getPaymentNo().isBlank()
                && mapper.countDuplicate(entity.getPaymentNo(), entity.getPaymentId()) > 0)
            throw new ServiceException("编号已存在");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        ScmApPayment r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        if (r.getStatus() != null && r.getStatus() == 2)
            throw new ServiceException("付款成功单据不允许删除，请使用作废");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<ScmApPayment> options() {
        return mapper.selectList(new LambdaQueryWrapper<ScmApPayment>().orderByDesc(ScmApPayment::getPaymentId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean audit(Long id, String auditBy) {
        ScmApPayment p = require(id);
        Integer st = p.getStatus() == null ? -1 : p.getStatus();
        if (st != 0) throw new ServiceException("仅草稿状态可审批");
        p.setStatus(1);
        p.setAuditBy(auditBy);
        p.setAuditTime(LocalDateTime.now());
        return mapper.updateById(p) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmPayment(Long id, String payBy) {
        ScmApPayment p = require(id);
        Integer st = p.getStatus() == null ? -1 : p.getStatus();
        if (st != 1) throw new ServiceException("仅已审批状态可确认付款成功");
        p.setStatus(2);
        p.setPayBy(payBy);
        p.setPayTime(LocalDateTime.now());
        boolean ok = mapper.updateById(p) > 0;
        if (!ok) return false;
        // 回写应付+计划
        BigDecimal cny = p.getCnyTotalAmount() == null ? BigDecimal.ZERO : p.getCnyTotalAmount();
        if (p.getPayableId() != null) payableService.applyPayment(p.getPayableId(), cny, p.getPaymentId());
        if (p.getPlanId() != null) planService.applyPayment(p.getPlanId(), cny, p.getPaymentId());
        // 推送付款凭证
        try { voucherService.pushPaymentVoucher(id); }
        catch (Exception ex) { log.error("推送付款凭证失败 paymentId={}", id, ex); }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markFailed(Long id, String failReason) {
        ScmApPayment p = require(id);
        Integer st = p.getStatus() == null ? -1 : p.getStatus();
        if (st != 1) throw new ServiceException("仅已审批状态可标记失败");
        p.setStatus(3);
        p.setFailReason(failReason);
        return mapper.updateById(p) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelPayment(Long id, String reason) {
        ScmApPayment p = require(id);
        Integer st = p.getStatus() == null ? -1 : p.getStatus();
        if (st == 9) return true; // 幂等
        BigDecimal cny = BigDecimal.ZERO;
        if (st == 2) {
            // 只有付款成功才需要回滚金额
            cny = p.getCnyTotalAmount() == null ? BigDecimal.ZERO : p.getCnyTotalAmount();
        }
        // 回滚(幂等：不存在则warn不抛异常)
        try {
            if (p.getPayableId() != null) payableService.rollbackPayment(p.getPayableId(), cny, p.getPaymentId());
        } catch (Exception ex) { log.warn("cancelPayment 回滚应付异常 payableId={}: {}", p.getPayableId(), ex.getMessage()); }
        try {
            if (p.getPlanId() != null) planService.rollbackPayment(p.getPlanId(), cny, p.getPaymentId());
        } catch (Exception ex) { log.warn("cancelPayment 回滚计划异常 planId={}: {}", p.getPlanId(), ex.getMessage()); }
        // 回滚凭证(若已生成)
        try {
            if (p.getVoucherId() != null) voucherService.cancelVoucher(p.getVoucherId(), reason == null ? "取消付款" : reason);
        } catch (Exception ex) { log.warn("cancelPayment 作废凭证异常 voucherId={}: {}", p.getVoucherId(), ex.getMessage()); }
        p.setStatus(9);
        if (p.getRemark() == null || p.getRemark().isBlank()) p.setRemark(reason);
        return mapper.updateById(p) > 0;
    }

    private ScmApPayment require(Long id) {
        ScmApPayment p = mapper.selectById(id);
        if (p == null) throw new ServiceException("付款单不存在: " + id);
        return p;
    }
}
