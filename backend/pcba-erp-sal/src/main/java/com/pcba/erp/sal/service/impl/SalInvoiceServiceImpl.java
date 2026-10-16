package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.SalArReceivable;
import com.pcba.erp.sal.entity.SalInvoice;
import com.pcba.erp.sal.entity.SalInvoiceLine;
import com.pcba.erp.sal.mapper.SalArReceivableMapper;
import com.pcba.erp.sal.mapper.SalInvoiceLineMapper;
import com.pcba.erp.sal.mapper.SalInvoiceMapper;
import com.pcba.erp.sal.service.ISalInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 销售开票管理
 * 状态机: 0草稿 → 1已开(回写应收invoiced_amount) → 2作废(回退)
 * 一单多票: 一应收单可分多次开票, line.amount ≤ receivable.totalAmount - invoicedAmount
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalInvoiceServiceImpl implements ISalInvoiceService {

    private final SalInvoiceMapper invoiceMapper;
    private final SalInvoiceLineMapper lineMapper;
    private final SalArReceivableMapper receivableMapper;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public IPage<SalInvoice> selectPage(int pageNum, int pageSize, String invoiceNo, Long customerId, String invoiceType, Integer status, LocalDate dateFrom, LocalDate dateTo) {
        return invoiceMapper.selectPage(new Page<>(pageNum, pageSize), invoiceNo, customerId, invoiceType, status, dateFrom, dateTo);
    }

    @Override
    public Map<String, Object> getDetail(Long id) {
        SalInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) throw new ServiceException("开票单不存在");
        List<SalInvoiceLine> lines = lineMapper.selectByInvoiceId(id);
        Map<String, Object> r = new HashMap<>();
        r.put("invoice", inv);
        r.put("lines", lines);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalInvoice entity, List<SalInvoiceLine> lines) {
        if (entity.getCustomerId() == null) throw new ServiceException("客户必填");
        if (entity.getInvoiceDate() == null) throw new ServiceException("开票日期必填");
        if (lines == null || lines.isEmpty()) throw new ServiceException("开票明细不能为空");
        // 生成发票号
        if (entity.getInvoiceNo() == null || entity.getInvoiceNo().isBlank()) {
            entity.setInvoiceNo(generateInvoiceNo());
        }
        // 默认值
        if (entity.getInvoiceType() == null || entity.getInvoiceType().isBlank()) entity.setInvoiceType("2");
        if (entity.getCurrency() == null || entity.getCurrency().isBlank()) entity.setCurrency("CNY");
        if (entity.getExchangeRate() == null || entity.getExchangeRate().compareTo(BigDecimal.ZERO) <= 0)
            entity.setExchangeRate(BigDecimal.ONE);
        if (entity.getRedFlag() == null) entity.setRedFlag(0);
        entity.setStatus(0);
        // 金额由明细汇总
        recalcAmounts(entity, lines);
        invoiceMapper.insert(entity);
        for (SalInvoiceLine l : lines) {
            l.setInvoiceId(entity.getInvoiceId());
            if (l.getAmount() == null) l.setAmount(BigDecimal.ZERO);
            lineMapper.insert(l);
        }
        log.info("save invoice {} customer={} lines={} total={}", entity.getInvoiceNo(), entity.getCustomerId(), lines.size(), entity.getTotalAmount());
        return entity.getInvoiceId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalInvoice entity, List<SalInvoiceLine> lines) {
        SalInvoice exists = invoiceMapper.selectById(entity.getInvoiceId());
        if (exists == null) throw new ServiceException("开票单不存在");
        if (exists.getStatus() == null || exists.getStatus() != 0)
            throw new ServiceException("仅草稿状态可编辑");
        if (lines == null || lines.isEmpty()) throw new ServiceException("开票明细不能为空");
        entity.setStatus(0);
        if (entity.getCurrency() == null || entity.getCurrency().isBlank()) entity.setCurrency("CNY");
        if (entity.getExchangeRate() == null || entity.getExchangeRate().compareTo(BigDecimal.ZERO) <= 0)
            entity.setExchangeRate(BigDecimal.ONE);
        recalcAmounts(entity, lines);
        invoiceMapper.updateById(entity);
        // 删旧明细+插新明细
        lineMapper.deleteByInvoiceId(entity.getInvoiceId());
        for (SalInvoiceLine l : lines) {
            l.setLineId(null);
            l.setInvoiceId(entity.getInvoiceId());
            if (l.getAmount() == null) l.setAmount(BigDecimal.ZERO);
            lineMapper.insert(l);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        SalInvoice exists = invoiceMapper.selectById(id);
        if (exists == null) throw new ServiceException("开票单不存在");
        if (exists.getStatus() != null && exists.getStatus() != 0)
            throw new ServiceException("仅草稿状态可删除");
        lineMapper.deleteByInvoiceId(id);
        return invoiceMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean issue(Long id, String operator) {
        SalInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) throw new ServiceException("开票单不存在");
        if (inv.getStatus() == null || inv.getStatus() != 0)
            throw new ServiceException("仅草稿状态可确认开票");
        List<SalInvoiceLine> lines = lineMapper.selectByInvoiceId(id);
        if (lines == null || lines.isEmpty()) throw new ServiceException("开票明细为空,无法开票");
        // 逐行校验部分金额 ≤ 未开票余额,并原子累加回写
        for (SalInvoiceLine l : lines) {
            if (l.getAmount() == null || l.getAmount().compareTo(BigDecimal.ZERO) <= 0)
                throw new ServiceException("开票金额必须大于0");
            SalArReceivable r = receivableMapper.selectById(l.getReceivableId());
            if (r == null) throw new ServiceException("应收单不存在: " + l.getReceivableId());
            if (r.getStatus() != null && r.getStatus() == 9)
                throw new ServiceException("应收单已作废: " + r.getReceivableNo());
            BigDecimal invoiced = r.getInvoicedAmount() == null ? BigDecimal.ZERO : r.getInvoicedAmount();
            BigDecimal total = r.getTotalAmount() == null ? BigDecimal.ZERO : r.getTotalAmount();
            BigDecimal balance = total.subtract(invoiced);
            if (l.getAmount().compareTo(balance) > 0)
                throw new ServiceException("开票金额 " + l.getAmount() + " 超过应收单 " + r.getReceivableNo()
                        + " 可开票余额 " + balance.setScale(2, java.math.RoundingMode.HALF_UP));
            // 原子累加回写
            invoiceMapper.addInvoicedAmount(r.getReceivableId(), l.getAmount());
        }
        // 推进状态
        inv.setStatus(1);
        inv.setIssueBy(operator == null ? "" : operator);
        inv.setIssueTime(LocalDateTime.now());
        int n = invoiceMapper.updateById(inv);
        log.info("issue invoice {} lines={} operator={}", inv.getInvoiceNo(), lines.size(), operator);
        return n > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long id) {
        SalInvoice inv = invoiceMapper.selectById(id);
        if (inv == null) throw new ServiceException("开票单不存在");
        if (inv.getStatus() == null || inv.getStatus() != 1)
            throw new ServiceException("仅已开状态可作废");
        List<SalInvoiceLine> lines = lineMapper.selectByInvoiceId(id);
        // 逐行回退 invoiced_amount(负向,确保非负)
        for (SalInvoiceLine l : lines) {
            invoiceMapper.addInvoicedAmount(l.getReceivableId(), l.getAmount().negate());
        }
        inv.setStatus(2);
        int n = invoiceMapper.updateById(inv);
        log.info("cancel invoice {} lines={}", inv.getInvoiceNo(), lines == null ? 0 : lines.size());
        return n > 0;
    }

    @Override
    public List<SalArReceivable> invoiceableReceivables(Long customerId) {
        if (customerId == null) throw new ServiceException("客户必填");
        return invoiceMapper.selectInvoiceableReceivables(customerId);
    }

    @Override
    public String generateInvoiceNo() {
        String ymd = LocalDate.now().format(YMD);
        int seq = invoiceMapper.maxInvoiceSeqOfDay(ymd);
        // 并发兜底: 若唯一索引冲突, 重试递增
        for (int i = 0; i < 5; i++) {
            String no = "INV" + ymd + String.format("%03d", seq + 1 + i);
            SalInvoice exists = invoiceMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SalInvoice>()
                    .eq(SalInvoice::getInvoiceNo, no));
            if (exists == null) return no;
        }
        return "INV" + ymd + String.format("%03d", seq + 6);
    }

    /** 汇总明细金额到主单: total=sum(amount), tax=total*taxRate/(1+taxRate), undisc=total-tax */
    private void recalcAmounts(SalInvoice inv, List<SalInvoiceLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SalInvoiceLine l : lines) {
            if (l.getAmount() == null) l.setAmount(BigDecimal.ZERO);
            total = total.add(l.getAmount());
        }
        inv.setTotalAmount(total);
        // 税额: 若主单有税额则用,否则按13%反算
        BigDecimal rate = inv.getExchangeRate() == null ? BigDecimal.ONE : inv.getExchangeRate();
        inv.setCnyTotalAmount(total.multiply(rate));
        // 税额/未税: 若调用方已填 taxAmount 则保留,否则按13%反算
        if (inv.getTaxAmount() == null || inv.getTaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal tax = total.multiply(new BigDecimal("0.13")).divide(new BigDecimal("1.13"), 2, java.math.RoundingMode.HALF_UP);
            inv.setTaxAmount(tax);
        }
        inv.setUndiscAmount(inv.getTotalAmount().subtract(inv.getTaxAmount()));
    }
}
