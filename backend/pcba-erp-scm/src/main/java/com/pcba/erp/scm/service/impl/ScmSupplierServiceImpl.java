package com.pcba.erp.scm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.scm.entity.ScmSupplier;
import com.pcba.erp.scm.mapper.ScmSupplierMapper;
import com.pcba.erp.scm.service.IScmSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmSupplierServiceImpl implements IScmSupplierService {

    private final ScmSupplierMapper mapper;
    private final StringRedisTemplate redisTemplate;

    private static final String LUA_CODE_INCR =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return v";

    @Override
    public String nextSupplierCode() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String seqKey = "scm:supplier:code:seq:" + today;
        long expireSec = 2L * 24 * 3600;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_CODE_INCR, Long.class);
        Long seq = redisTemplate.execute(script, Collections.singletonList(seqKey), String.valueOf(expireSec));
        if (seq == null) throw new ServiceException("供应商编码生成失败");
        return "SUP" + today + String.format("%04d", seq);
    }

    @Override
    public IPage<ScmSupplier> selectPage(int pageNum, int pageSize, String supplierCode, String supplierName, String contact, String phone, String level, Integer status) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), supplierCode, supplierName, contact, phone, level, status);
    }

    @Override
    public ScmSupplier getDetail(Long id) {
        if (id == null) throw new ServiceException("ID不能为空");
        ScmSupplier r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScmSupplier entity) {
        // 编码为空时自动生成
        try {
            Field noField = ScmSupplier.class.getDeclaredField("supplierCode");
            noField.setAccessible(true);
            Object noVal = noField.get(entity);
            if (noVal == null || noVal.toString().isBlank()) {
                String newCode = nextSupplierCode();
                noField.set(entity, newCode);
                noVal = newCode;
            }
            if (noVal != null && !noVal.toString().isBlank()) {
                Field idField = ScmSupplier.class.getDeclaredField("supplierId");
                idField.setAccessible(true);
                Object idVal = idField.get(entity);
                if (mapper.countDuplicate(noVal.toString(), (Long) idVal) > 0) {
                    throw new ServiceException("编号已存在: " + noVal);
                }
            }
        } catch (NoSuchFieldException ignore) { /* 无对应字段跳过查重 */ }
          catch (IllegalAccessException ex) { throw new ServiceException("保存失败: " + ex.getMessage()); }

        // 默认值: status=0 / 数字 0
        try {
            Field s = ScmSupplier.class.getDeclaredField("status");
            s.setAccessible(true);
            if (s.get(entity) == null) s.set(entity, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        for (String f : new String[]{"totalAmount","taxAmount","paidAmount","totalQty","refundAmount","qty","estimatePrice","estimateAmount","orderQty","receivedQty","returnedQty","unitPrice","lineAmount","taxRate","minQty","discountRate"}) {
            try {
                Field ff = ScmSupplier.class.getDeclaredField(f);
                ff.setAccessible(true);
                if (ff.get(entity) == null && ff.getType() == BigDecimal.class) ff.set(entity, BigDecimal.ZERO);
            } catch (NoSuchFieldException | IllegalAccessException ignore) {}
        }
        mapper.insert(entity);
        try {
            Field idF = ScmSupplier.class.getDeclaredField("supplierId");
            idF.setAccessible(true);
            return (Long) idF.get(entity);
        } catch (Exception ex) { return null; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScmSupplier entity) {
        Long id = null;
        try {
            Field idF = ScmSupplier.class.getDeclaredField("supplierId");
            idF.setAccessible(true);
            id = (Long) idF.get(entity);
        } catch (Exception ignore) {}
        if (id == null) throw new ServiceException("ID不能为空");
        ScmSupplier old = mapper.selectById(id);
        if (old == null) throw new ServiceException("记录不存在");
        // 单号查重
        try {
            Field noField = ScmSupplier.class.getDeclaredField("supplierCode");
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
        ScmSupplier r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        ScmSupplier r = mapper.selectById(id);
        if (r == null) throw new ServiceException("记录不存在");
        try {
            Field s = ScmSupplier.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(r, status);
        } catch (Exception ignore) { throw new ServiceException("状态字段缺失"); }
        return mapper.updateById(r) > 0;
    }

    @Override
    public List<ScmSupplier> options() {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScmSupplier>()
                .orderByDesc("supplier_id"));
    }
}
