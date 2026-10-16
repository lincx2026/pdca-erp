package com.pcba.erp.assembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmKitting;
import com.pcba.erp.assembly.mapper.AsmKittingMapper;
import com.pcba.erp.assembly.service.IAsmKittingService;
import com.pcba.erp.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 配料单 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsmKittingServiceImpl implements IAsmKittingService {

    private final AsmKittingMapper kittingMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public IPage<AsmKitting> selectPage(int pageNum, int pageSize,
                                        String kittingNo, Long workOrderId, Integer status) {
        Page<AsmKitting> page = new Page<>(pageNum, pageSize);
        return kittingMapper.selectPage(page, kittingNo, workOrderId, status);
    }

    @Override
    public AsmKitting getDetail(Long id) {
        AsmKitting k = kittingMapper.selectById(id);
        if (k == null) throw new ServiceException("配料单不存在");
        return k;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AsmKitting entity) {
        if (entity.getStatus() == null) entity.setStatus(0); // 待配料
        if (entity.getKittingType() == null) entity.setKittingType("FULL");
        if (entity.getKittingNo() == null || entity.getKittingNo().isEmpty()) {
            entity.setKittingNo(nextKittingNo());
        } else {
            int dup = kittingMapper.countDuplicate(entity.getKittingNo(), null);
            if (dup > 0) throw new ServiceException("配料单号【" + entity.getKittingNo() + "】已存在");
        }
        if (kittingMapper.insert(entity) <= 0) throw new ServiceException("新增配料单失败");
        return entity.getKittingId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(AsmKitting entity) {
        if (entity.getKittingId() == null) throw new ServiceException("配料单ID不能为空");
        AsmKitting old = kittingMapper.selectById(entity.getKittingId());
        if (old == null) throw new ServiceException("配料单不存在");
        if (entity.getKittingNo() != null) {
            int dup = kittingMapper.countDuplicate(entity.getKittingNo(), entity.getKittingId());
            if (dup > 0) throw new ServiceException("配料单号已存在");
        }
        return kittingMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        AsmKitting k = kittingMapper.selectById(id);
        if (k == null) throw new ServiceException("配料单不存在");
        // 只有待配料/已取消可以删除
        Integer s = k.getStatus();
        if (!Integer.valueOf(0).equals(s) && !Integer.valueOf(6).equals(s)) {
            throw new ServiceException("当前状态不允许删除");
        }
        return kittingMapper.deleteById(id) > 0;
    }

    @Override
    public List<AsmKitting> options() {
        return kittingMapper.selectList(
                new LambdaQueryWrapper<AsmKitting>()
                        .orderByDesc(AsmKitting::getKittingId)
                        .last("LIMIT 200"));
    }

    @Override
    public String nextKittingNo() {
        String date = LocalDateTime.now().format(FMT);
        String prefix = "KT-" + date + "-";
        AsmKitting last = kittingMapper.selectOne(
                new LambdaQueryWrapper<AsmKitting>()
                        .likeRight(AsmKitting::getKittingNo, prefix)
                        .orderByDesc(AsmKitting::getKittingId)
                        .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getKittingNo() != null) {
            try {
                String tail = last.getKittingNo().substring(prefix.length());
                seq = Integer.parseInt(tail) + 1;
            } catch (Exception ignored) { /* 使用默认 1 */ }
        }
        return prefix + String.format("%04d", seq);
    }

    // ============ 状态流转 ============

    private AsmKitting requireStatus(Long id, Integer... allowed) {
        AsmKitting k = getDetail(id);
        if (allowed != null) {
            boolean ok = false;
            for (Integer a : allowed) if (a.equals(k.getStatus())) { ok = true; break; }
            if (!ok) throw new ServiceException("当前配料单状态不允许此操作");
        }
        return k;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startKitting(Long id) {
        AsmKitting k = requireStatus(id, 0); // 待配料
        k.setStatus(1);
        k.setActualStartTime(LocalDateTime.now());
        return kittingMapper.updateById(k) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean finishKitting(Long id) {
        AsmKitting k = requireStatus(id, 1); // 配料中
        k.setStatus(2);
        k.setActualEndTime(LocalDateTime.now());
        return kittingMapper.updateById(k) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean issueMaterial(Long id) {
        AsmKitting k = requireStatus(id, 2); // 已配料待发
        k.setStatus(3); // 已发料
        return kittingMapper.updateById(k) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelKitting(Long id) {
        AsmKitting k = requireStatus(id, 0, 1); // 待配料/配料中
        k.setStatus(6);
        return kittingMapper.updateById(k) > 0;
    }
}
