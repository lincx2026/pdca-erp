package com.pcba.erp.assembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmKittingLine;
import com.pcba.erp.assembly.mapper.AsmKittingLineMapper;
import com.pcba.erp.assembly.service.IAsmKittingLineService;
import com.pcba.erp.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 配料明细行 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsmKittingLineServiceImpl implements IAsmKittingLineService {

    private final AsmKittingLineMapper lineMapper;

    @Override
    public List<AsmKittingLine> listByKittingId(Long kittingId) {
        if (kittingId == null) throw new ServiceException("配料单ID不能为空");
        return lineMapper.selectList(new LambdaQueryWrapper<AsmKittingLine>()
                .eq(AsmKittingLine::getKittingId, kittingId).orderByAsc(AsmKittingLine::getKittingLineId));
    }

    @Override
    public IPage<AsmKittingLine> selectPage(int pageNum, int pageSize,
                                            Long kittingId, Long materialId, Integer lineStatus) {
        Page<AsmKittingLine> page = new Page<>(pageNum, pageSize);
        return lineMapper.selectPage(page, kittingId, materialId, lineStatus);
    }

    @Override
    public AsmKittingLine getDetail(Long id) {
        AsmKittingLine l = lineMapper.selectById(id);
        if (l == null) throw new ServiceException("配料明细行不存在");
        return l;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AsmKittingLine entity) {
        if (entity.getLineStatus() == null) entity.setLineStatus(0);
        if (entity.getLockStatus() == null) entity.setLockStatus(0);
        if (lineMapper.insert(entity) <= 0) throw new ServiceException("新增配料明细行失败");
        return entity.getKittingLineId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(AsmKittingLine entity) {
        if (entity.getKittingLineId() == null) throw new ServiceException("明细行ID不能为空");
        AsmKittingLine old = lineMapper.selectById(entity.getKittingLineId());
        if (old == null) throw new ServiceException("配料明细行不存在");
        return lineMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        AsmKittingLine l = lineMapper.selectById(id);
        if (l == null) throw new ServiceException("配料明细行不存在");
        return lineMapper.deleteById(id) > 0;
    }

    @Override
    public List<AsmKittingLine> options() {
        return lineMapper.selectList(
                new LambdaQueryWrapper<AsmKittingLine>()
                        .orderByDesc(AsmKittingLine::getKittingLineId)
                        .last("LIMIT 200"));
    }
}
