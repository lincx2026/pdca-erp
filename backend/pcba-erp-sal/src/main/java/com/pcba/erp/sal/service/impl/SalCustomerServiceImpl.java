package com.pcba.erp.sal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.sal.entity.SalCustomer;
import com.pcba.erp.sal.mapper.SalCustomerMapper;
import com.pcba.erp.sal.service.ISalArReceivableService;
import com.pcba.erp.sal.service.ISalCustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalCustomerServiceImpl implements ISalCustomerService {

    private final SalCustomerMapper mapper;
    @Lazy private final ISalArReceivableService receivableService;
    private final StringRedisTemplate redisTemplate;

    private static final String LUA_CODE_INCR =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return v";

    @Override
    public String nextCustomerCode() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String seqKey = "sal:customer:code:seq:" + today;
        long expireSec = 2L * 24 * 3600;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_CODE_INCR, Long.class);
        Long seq = redisTemplate.execute(script, Collections.singletonList(seqKey), String.valueOf(expireSec));
        if (seq == null) throw new ServiceException("客户编码生成失败");
        return "CUS" + today + String.format("%04d", seq);
    }

    @Override
    public IPage<SalCustomer> selectPage(int pageNum, int pageSize, String customerCode, String customerName, String contact, String phone, String industry, String level, Integer status) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), customerCode, customerName, contact, phone, industry, level, status);
    }

    @Override
    public SalCustomer getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        SalCustomer r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SalCustomer entity) {
        // 编码为空时自动生成
        try {
            Field noField = SalCustomer.class.getDeclaredField("customerCode");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal == null || noVal.toString().isBlank()) {
                String newCode = nextCustomerCode();
                noField.set(entity, newCode);
                noVal = newCode;
            }
            if (noVal != null && !noVal.toString().isBlank()) {
                Field idField = SalCustomer.class.getDeclaredField("customerId");
                idField.setAccessible(true);
                Object idVal = idField.get(entity);
                if (mapper.countDuplicate(noVal.toString(), (Long) idVal) > 0) {
                    throw new ServiceException("编号已存在: " + noVal);
                }
            }
        } catch (NoSuchFieldException ignore) { /* 无对应字段跳过查重 */ }
          catch (IllegalAccessException ex) { throw new ServiceException("保存失败: " + ex.getMessage()); }

        // 默认值: status=0 / BigDecimal 字段 0
        try {
            Field s = SalCustomer.class.getDeclaredField("status");
            s.setAccessible(true);
            if (s.get(entity) == null) s.set(entity, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        for (String f : new String[]{"creditLimit","creditBalance"}) {
            try {
                Field ff = SalCustomer.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        mapper.insert(entity);
        try {
            Field idF = SalCustomer.class.getDeclaredField("customerId");
            idF.setAccessible(true);
            return (Long) idF.get(entity);
        } catch (Exception ex) { return null; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalCustomer entity) {
        Long id = null;
        try {
            Field idF = SalCustomer.class.getDeclaredField("customerId");
            idF.setAccessible(true);
            id = (Long) idF.get(entity);
        } catch (Exception ignore) {}
        if (id == null) throw new ServiceException("ID不能为空");
        SalCustomer old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        // 编码查重
        try {
            Field noField = SalCustomer.class.getDeclaredField("customerCode");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal != null && !noVal.toString().isBlank()) {
                if (mapper.countDuplicate(noVal.toString(), id) > 0)
                    throw new ServiceException("编号已存在");
            }
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        SalCustomer r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        SalCustomer r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        try {
            Field s = SalCustomer.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override
    public List<SalCustomer> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SalCustomer>()
                .orderByDesc("customer_id"));
    }

    /** 客户信用额度校验：委托应收单服务执行(逾期/额度拦截) */
    @Override
    public void checkCreditLimit(Long customerId, BigDecimal newOrderCnyAmount) {
        receivableService.checkCreditLimit(customerId, newOrderCnyAmount);
    }

    /** 客户信用占用查询：creditLimit/creditDays/unreceived/overdue/used/available/hasOverdue/controlled */
    @Override
    public Map<String, Object> getCredit(Long customerId) {
        if (customerId == null) throw new ServiceException("客户ID不能为空");
        SalCustomer c = mapper.selectById(customerId);
        if (c == null) throw new ServiceException("客户不存在");
        BigDecimal limit = c.getCreditLimit() == null ? BigDecimal.ZERO : c.getCreditLimit();
        BigDecimal unreceived = receivableService.sumUnreceived(customerId);
        if (unreceived == null) unreceived = BigDecimal.ZERO;
        BigDecimal overdue = receivableService.sumOverdue(customerId);
        if (overdue == null) overdue = BigDecimal.ZERO;
        BigDecimal available = limit.subtract(unreceived);
        Map<String, Object> r = new HashMap<>();
        r.put("customerId", customerId);
        r.put("customerCode", c.getCustomerCode());
        r.put("customerName", c.getCustomerName());
        r.put("creditLimit", limit);
        r.put("creditDays", c.getCreditDays() == null ? 0 : c.getCreditDays());
        r.put("paymentTerms", c.getPaymentTerms());
        r.put("unreceived", unreceived);              // 未收应收(占用)
        r.put("overdue", overdue);                    // 逾期未收
        r.put("used", unreceived);                    // 已用额度=未收
        r.put("available", available);                // 可用额度=额度-未收
        r.put("hasOverdue", overdue.compareTo(BigDecimal.ZERO) > 0);
        r.put("controlled", limit.compareTo(BigDecimal.ZERO) > 0); // 是否启用额度控制
        return r;
    }
}
