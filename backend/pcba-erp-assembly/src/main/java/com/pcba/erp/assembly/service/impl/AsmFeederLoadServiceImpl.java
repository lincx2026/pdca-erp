package com.pcba.erp.assembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmFeederLoad;
import com.pcba.erp.assembly.mapper.AsmFeederLoadMapper;
import com.pcba.erp.assembly.service.IAsmFeederLoadService;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Feeder 上料 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsmFeederLoadServiceImpl implements IAsmFeederLoadService {

    private final AsmFeederLoadMapper feederLoadMapper;

    @Override
    public IPage<AsmFeederLoad> selectPage(int pageNum, int pageSize,
                                           String equipmentCode, String feederNo,
                                           Long materialId, Integer status) {
        Page<AsmFeederLoad> page = new Page<>(pageNum, pageSize);
        return feederLoadMapper.selectPage(page, equipmentCode, feederNo, materialId, status);
    }

    @Override
    public AsmFeederLoad getDetail(Long id) {
        AsmFeederLoad f = feederLoadMapper.selectById(id);
        if (f == null) throw new ServiceException("Feeder上料记录不存在");
        return f;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AsmFeederLoad entity) {
        if (entity.getStatus() == null) entity.setStatus(0); // 在料
        if (entity.getLoadTime() == null) entity.setLoadTime(LocalDateTime.now());
        if (entity.getOperatorName() == null) {
            entity.setOperatorName(SecurityUtils.getUsernameSafe());
        }
        if (feederLoadMapper.insert(entity) <= 0) throw new ServiceException("新增Feeder上料记录失败");
        return entity.getLoadId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(AsmFeederLoad entity) {
        if (entity.getLoadId() == null) throw new ServiceException("Feeder记录ID不能为空");
        AsmFeederLoad old = feederLoadMapper.selectById(entity.getLoadId());
        if (old == null) throw new ServiceException("Feeder上料记录不存在");
        return feederLoadMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        AsmFeederLoad f = feederLoadMapper.selectById(id);
        if (f == null) throw new ServiceException("Feeder上料记录不存在");
        Integer s = f.getStatus();
        if (Integer.valueOf(0).equals(s)) throw new ServiceException("在料状态的Feeder不允许删除, 请先下料");
        return feederLoadMapper.deleteById(id) > 0;
    }

    @Override
    public List<AsmFeederLoad> options() {
        return feederLoadMapper.selectList(
                new LambdaQueryWrapper<AsmFeederLoad>()
                        .orderByDesc(AsmFeederLoad::getLoadId)
                        .last("LIMIT 200"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean loadFeeder(AsmFeederLoad entity) {
        entity.setStatus(0);
        entity.setLoadTime(LocalDateTime.now());
        if (entity.getOperatorName() == null) {
            entity.setOperatorName(SecurityUtils.getUsernameSafe());
        }
        if (entity.getLoadId() != null) {
            return feederLoadMapper.updateById(entity) > 0;
        }
        if (feederLoadMapper.insert(entity) <= 0) throw new ServiceException("上料失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unloadFeeder(Long id) {
        AsmFeederLoad f = feederLoadMapper.selectById(id);
        if (f == null) throw new ServiceException("Feeder上料记录不存在");
        if (!Integer.valueOf(0).equals(f.getStatus())) {
            throw new ServiceException("只有在料状态可以下料");
        }
        f.setStatus(2); // 已下料
        f.setUnloadTime(LocalDateTime.now());
        return feederLoadMapper.updateById(f) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean scrapFeeder(Long id) {
        AsmFeederLoad f = feederLoadMapper.selectById(id);
        if (f == null) throw new ServiceException("Feeder上料记录不存在");
        f.setStatus(3); // 报废
        f.setUnloadTime(LocalDateTime.now());
        return feederLoadMapper.updateById(f) > 0;
    }
}
