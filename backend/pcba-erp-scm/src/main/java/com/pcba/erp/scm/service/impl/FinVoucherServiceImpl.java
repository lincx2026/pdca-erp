package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.*;
import com.pcba.erp.scm.mapper.FinVoucherInterfaceMapper;
import com.pcba.erp.scm.mapper.ScmApPayableMapper;
import com.pcba.erp.scm.mapper.ScmApPaymentMapper;
import com.pcba.erp.scm.service.IFinVoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinVoucherServiceImpl implements IFinVoucherService {

    private final FinVoucherInterfaceMapper mapper;
    private final ScmApPayableMapper payableMapper;
    private final ScmApPaymentMapper paymentMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public IPage<FinVoucherInterface> selectPage(int pageNum, int pageSize, String voucherNo, String voucherType, String bizSource, Long bizId, Integer pushStatus, String voucherDate) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), voucherNo, voucherType, bizSource, bizId, pushStatus, voucherDate);
    }

    @Override
    public FinVoucherInterface getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        FinVoucherInterface r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(FinVoucherInterface entity) {
        try {
            Field f = FinVoucherInterface.class.getDeclaredField("voucherNo"); f.setAccessible(true);
            Object v = f.get(entity);
            if (v == null || v.toString().isBlank())
                f.set(entity, "FV" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        } catch (Exception ignore){}
        try {
            Field f = FinVoucherInterface.class.getDeclaredField("voucherDate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, LocalDate.now());
        } catch (Exception ignore){}
        try {
            Field f = FinVoucherInterface.class.getDeclaredField("pushStatus"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, 0);
        } catch (Exception ignore){}
        try {
            Field f = FinVoucherInterface.class.getDeclaredField("currency"); f.setAccessible(true);
            Object v = f.get(entity); if (v == null || v.toString().isBlank()) f.set(entity, "CNY");
        } catch (Exception ignore){}
        try {
            Field f = FinVoucherInterface.class.getDeclaredField("exchangeRate"); f.setAccessible(true);
            if (f.get(entity) == null) f.set(entity, BigDecimal.ONE);
        } catch (Exception ignore){}
        for (String fn : new String[]{"totalDebit","totalCredit","totalDebitCny","totalCreditCny"}) {
            try {
                Field f = FinVoucherInterface.class.getDeclaredField(fn); f.setAccessible(true);
                if (f.get(entity) == null && f.getType() == BigDecimal.class) f.set(entity, BigDecimal.ZERO);
            } catch (Exception ignore){}
        }
        if (mapper.countDuplicate(entity.getVoucherNo(), entity.getVoucherId()) > 0)
            throw new ServiceException("凭证编号已存在: " + entity.getVoucherNo());
        mapper.insert(entity);
        return entity.getVoucherId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(FinVoucherInterface entity) {
        if (entity.getVoucherId() == null) throw new ServiceException("ID不能为空");
        FinVoucherInterface old = mapper.selectById(entity.getVoucherId());
        if (old == null) throw new ServiceException("记录不存在");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        FinVoucherInterface r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<FinVoucherInterface> options() {
        return mapper.selectList(new LambdaQueryWrapper<FinVoucherInterface>().orderByDesc(FinVoucherInterface::getVoucherId));
    }

    // ========== 应付凭证 ==========
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long pushPayableVoucher(Long payableId) {
        ScmApPayable p = payableMapper.selectById(payableId);
        if (p == null) throw new ServiceException("应付单不存在: " + payableId);
        // 幂等：若已生成则复用
        if (p.getVoucherId() != null) {
            FinVoucherInterface old = mapper.selectById(p.getVoucherId());
            if (old != null) {
                if (old.getPushStatus() == null || old.getPushStatus() != 2) mockPush(old.getVoucherId());
                return old.getVoucherId();
            }
        }
        FinVoucherInterface v = new FinVoucherInterface();
        v.setVoucherType("AP");   // 应付
        v.setBizSource("SCM_AP_PAYABLE");
        v.setBizId(p.getPayableId());
        v.setBizNo(p.getPayableNo());
        v.setVoucherDate(p.getBizDate() != null ? p.getBizDate() : LocalDate.now());
        v.setCompanyCode("PCBA");
        v.setCurrency(p.getCurrency() == null ? "CNY" : p.getCurrency());
        v.setExchangeRate(p.getExchangeRate() == null ? BigDecimal.ONE : p.getExchangeRate());

        // 汇总物料行 → 借：原材料 (STOCK_IN 正 / RETURN_OUT 负)
        //            借：应交税费-增值税进项
        //            贷：应付账款-供方
        // 通过 payable_line 汇总
        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
                "SELECT 'MAT' AS kind, SUM(undisc_amount) AS amt FROM scm_ap_payable_line WHERE payable_id = ? " +
                "UNION ALL SELECT 'TAX', SUM(tax_amount) FROM scm_ap_payable_line WHERE payable_id = ?",
                payableId, payableId);
        BigDecimal matAmt = BigDecimal.ZERO, taxAmt = BigDecimal.ZERO;
        for (Map<String,Object> m : lines) {
            BigDecimal a = toDecimal(m.get("amt"));
            if ("MAT".equals(m.get("kind"))) matAmt = a;
            else if ("TAX".equals(m.get("kind"))) taxAmt = a;
        }
        BigDecimal credit = p.getCnyTotalAmount() == null ? BigDecimal.ZERO : p.getCnyTotalAmount();
        BigDecimal matCny = matAmt;
        BigDecimal taxCny = taxAmt;
        BigDecimal credCny = credit;

        List<Map<String,Object>> vLines = new ArrayList<>();
        int ln = 0;
        if (matAmt.compareTo(BigDecimal.ZERO) != 0) {
            ln++;
            Map<String,Object> l = new LinkedHashMap<>();
            l.put("lineNo", ln);
            l.put("subjectCode", "1403"); l.put("subjectName", "原材料");
            l.put("debit", matAmt); l.put("credit", BigDecimal.ZERO);
            l.put("debitCny", matCny); l.put("creditCny", BigDecimal.ZERO);
            l.put("auxType", "MATERIAL"); l.put("auxCode", p.getSupplierId()); l.put("auxName", p.getSupplierName());
            vLines.add(l);
        }
        if (taxAmt.compareTo(BigDecimal.ZERO) != 0) {
            ln++;
            Map<String,Object> l = new LinkedHashMap<>();
            l.put("lineNo", ln);
            l.put("subjectCode", "22210101"); l.put("subjectName", "应交税费-进项税额");
            l.put("debit", taxAmt); l.put("credit", BigDecimal.ZERO);
            l.put("debitCny", taxCny); l.put("creditCny", BigDecimal.ZERO);
            vLines.add(l);
        }
        if (credit.compareTo(BigDecimal.ZERO) != 0) {
            ln++;
            Map<String,Object> l = new LinkedHashMap<>();
            l.put("lineNo", ln);
            l.put("subjectCode", "2202"); l.put("subjectName", "应付账款");
            l.put("debit", BigDecimal.ZERO); l.put("credit", credit);
            l.put("debitCny", BigDecimal.ZERO); l.put("creditCny", credCny);
            l.put("auxType", "SUPPLIER"); l.put("auxCode", p.getSupplierId()); l.put("auxName", p.getSupplierName());
            l.put("remark", p.getBizType());
            vLines.add(l);
        }
        try { v.setVoucherLines(objectMapper.writeValueAsString(vLines)); }
        catch (JsonProcessingException e) { v.setVoucherLines("[]"); }
        v.setTotalDebit(matAmt.add(taxAmt));
        v.setTotalCredit(credit);
        v.setTotalDebitCny(matCny.add(taxCny));
        v.setTotalCreditCny(credCny);
        Long vid = this.save(v);
        p.setVoucherId(vid);
        payableMapper.updateById(p);
        // 模拟推送
        mockPush(vid);
        return vid;
    }

    // ========== 付款凭证 ==========
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long pushPaymentVoucher(Long paymentId) {
        ScmApPayment p = paymentMapper.selectById(paymentId);
        if (p == null) throw new ServiceException("付款单不存在: " + paymentId);
        if (p.getVoucherId() != null) {
            FinVoucherInterface old = mapper.selectById(p.getVoucherId());
            if (old != null) {
                if (old.getPushStatus() == null || old.getPushStatus() != 2) mockPush(old.getVoucherId());
                return old.getVoucherId();
            }
        }
        FinVoucherInterface v = new FinVoucherInterface();
        v.setVoucherType("PAY");
        v.setBizSource("SCM_AP_PAYMENT");
        v.setBizId(p.getPaymentId());
        v.setBizNo(p.getPaymentNo());
        v.setVoucherDate(p.getPayDate() != null ? p.getPayDate() : LocalDate.now());
        v.setCompanyCode("PCBA");
        v.setCurrency(p.getPayCurrency() == null ? "CNY" : p.getPayCurrency());
        v.setExchangeRate(p.getPayExchangeRate() == null ? BigDecimal.ONE : p.getPayExchangeRate());
        BigDecimal amt = p.getTotalAmount() == null ? BigDecimal.ZERO : p.getTotalAmount();
        BigDecimal cnyAmt = p.getCnyTotalAmount() == null ? amt : p.getCnyTotalAmount();
        List<Map<String,Object>> vLines = new ArrayList<>();
        // 借：应付账款-供方
        Map<String,Object> d = new LinkedHashMap<>();
        d.put("lineNo", 1);
        d.put("subjectCode","2202"); d.put("subjectName","应付账款");
        d.put("debit", amt); d.put("credit", BigDecimal.ZERO);
        d.put("debitCny", cnyAmt); d.put("creditCny", BigDecimal.ZERO);
        d.put("auxType","SUPPLIER"); d.put("auxCode", p.getSupplierId()); d.put("auxName", p.getSupplierName());
        vLines.add(d);
        // 贷：银行存款
        Map<String,Object> c = new LinkedHashMap<>();
        c.put("lineNo", 2);
        String bankName = p.getPayBankName() == null ? "" : p.getPayBankName();
        c.put("subjectCode","1002"); c.put("subjectName","银行存款" + (bankName.isBlank() ? "" : "-"+bankName));
        c.put("debit", BigDecimal.ZERO); c.put("credit", amt);
        c.put("debitCny", BigDecimal.ZERO); c.put("creditCny", cnyAmt);
        c.put("auxType","BANK"); c.put("auxCode", p.getPayBankAccount()); c.put("auxName", p.getWaterNo());
        vLines.add(c);
        try { v.setVoucherLines(objectMapper.writeValueAsString(vLines)); }
        catch (JsonProcessingException e) { v.setVoucherLines("[]"); }
        v.setTotalDebit(amt); v.setTotalCredit(amt);
        v.setTotalDebitCny(cnyAmt); v.setTotalCreditCny(cnyAmt);
        Long vid = this.save(v);
        p.setVoucherId(vid);
        paymentMapper.updateById(p);
        mockPush(vid);
        return vid;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean mockPush(Long voucherId) {
        FinVoucherInterface v = mapper.selectById(voucherId);
        if (v == null) throw new ServiceException("凭证不存在: " + voucherId);
        if (v.getPushStatus() != null && v.getPushStatus() == 9)
            throw new ServiceException("凭证已作废");
        v.setPushStatus(2);
        v.setPushTime(LocalDateTime.now());
        v.setPushMsg("OK: pushed@mock");
        v.setExternalVoucherNo("EXT-" + v.getVoucherNo());
        return mapper.updateById(v) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelVoucher(Long voucherId, String reason) {
        FinVoucherInterface v = mapper.selectById(voucherId);
        if (v == null) { log.warn("cancelVoucher不存在: {}", voucherId); return false; }
        v.setPushStatus(9);
        v.setRemark(reason == null ? "作废" : reason);
        return mapper.updateById(v) > 0;
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal)o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
