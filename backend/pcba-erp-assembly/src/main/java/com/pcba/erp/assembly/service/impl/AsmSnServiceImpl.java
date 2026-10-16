package com.pcba.erp.assembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmSn;
import com.pcba.erp.assembly.mapper.AsmSnMapper;
import com.pcba.erp.assembly.service.IAsmSnService;
import com.pcba.erp.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SN 主表 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsmSnServiceImpl implements IAsmSnService {

    private final AsmSnMapper snMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public IPage<AsmSn> selectPage(int pageNum, int pageSize,
                                    String snCode, String snType, Integer snLevel,
                                    Integer currentStatus, Long workOrderId, Long materialId) {
        Page<AsmSn> page = new Page<>(pageNum, pageSize);
        return snMapper.selectPage(page, snCode, snType, snLevel, currentStatus, workOrderId, materialId);
    }

    @Override
    public AsmSn getDetail(Long id) {
        AsmSn s = snMapper.selectById(id);
        if (s == null) throw new ServiceException("SN 不存在");
        return s;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AsmSn entity) {
        if (entity.getCurrentStatus() == null) entity.setCurrentStatus(0); // 待生产
        if (entity.getSnLevel() == null) entity.setSnLevel(1);
        if (entity.getSnCode() == null || entity.getSnCode().isEmpty()) {
            entity.setSnCode(nextSnCode());
        } else {
            int dup = snMapper.countDuplicate(entity.getSnCode(), null);
            if (dup > 0) throw new ServiceException("SN 编码【" + entity.getSnCode() + "】已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getFirstSeenTime() == null) entity.setFirstSeenTime(now);
        entity.setLastSeenTime(now);
        if (snMapper.insert(entity) <= 0) throw new ServiceException("新增 SN 失败");
        return entity.getSnId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(AsmSn entity) {
        if (entity.getSnId() == null) throw new ServiceException("SN ID不能为空");
        AsmSn old = snMapper.selectById(entity.getSnId());
        if (old == null) throw new ServiceException("SN 不存在");
        if (entity.getSnCode() != null) {
            int dup = snMapper.countDuplicate(entity.getSnCode(), entity.getSnId());
            if (dup > 0) throw new ServiceException("SN 编码已存在");
        }
        entity.setLastSeenTime(LocalDateTime.now());
        return snMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        AsmSn s = snMapper.selectById(id);
        if (s == null) throw new ServiceException("SN 不存在");
        return snMapper.deleteById(id) > 0;
    }

    @Override
    public List<AsmSn> options() {
        return snMapper.selectList(
                new LambdaQueryWrapper<AsmSn>()
                        .orderByDesc(AsmSn::getSnId)
                        .last("LIMIT 200"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long snId, Integer currentStatus, String currentProcess) {
        AsmSn s = snMapper.selectById(snId);
        if (s == null) throw new ServiceException("SN 不存在");
        if (currentStatus != null) s.setCurrentStatus(currentStatus);
        if (currentProcess != null) s.setCurrentProcess(currentProcess);
        s.setLastSeenTime(LocalDateTime.now());
        return snMapper.updateById(s) > 0;
    }

    @Override
    public String nextSnCode() {
        String date = LocalDateTime.now().format(FMT);
        String prefix = "SN-" + date + "-";
        AsmSn last = snMapper.selectOne(
                new LambdaQueryWrapper<AsmSn>()
                        .likeRight(AsmSn::getSnCode, prefix)
                        .orderByDesc(AsmSn::getSnId)
                        .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getSnCode() != null) {
            try {
                String tail = last.getSnCode().substring(prefix.length());
                seq = Integer.parseInt(tail) + 1;
            } catch (Exception ignored) { /* 默认 1 */ }
        }
        return prefix + String.format("%04d", seq);
    }
}
