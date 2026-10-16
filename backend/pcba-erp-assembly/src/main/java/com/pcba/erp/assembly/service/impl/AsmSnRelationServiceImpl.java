package com.pcba.erp.assembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmSnRelation;
import com.pcba.erp.assembly.mapper.AsmSnRelationMapper;
import com.pcba.erp.assembly.service.IAsmSnRelationService;
import com.pcba.erp.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SN 父子关系 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsmSnRelationServiceImpl implements IAsmSnRelationService {

    private final AsmSnRelationMapper relationMapper;

    @Override
    public IPage<AsmSnRelation> selectPage(int pageNum, int pageSize,
                                           Long parentSnId, Long childSnId) {
        Page<AsmSnRelation> page = new Page<>(pageNum, pageSize);
        return relationMapper.selectPage(page, parentSnId, childSnId);
    }

    @Override
    public AsmSnRelation getDetail(Long id) {
        AsmSnRelation r = relationMapper.selectById(id);
        if (r == null) throw new ServiceException("SN关系不存在");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(AsmSnRelation entity) {
        if (relationMapper.insert(entity) <= 0) throw new ServiceException("新增SN关系失败");
        return entity.getRelationId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(AsmSnRelation entity) {
        if (entity.getRelationId() == null) throw new ServiceException("关系ID不能为空");
        AsmSnRelation old = relationMapper.selectById(entity.getRelationId());
        if (old == null) throw new ServiceException("SN关系不存在");
        return relationMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        AsmSnRelation r = relationMapper.selectById(id);
        if (r == null) throw new ServiceException("SN关系不存在");
        return relationMapper.deleteById(id) > 0;
    }

    @Override
    public List<AsmSnRelation> options() {
        return relationMapper.selectList(
                new LambdaQueryWrapper<AsmSnRelation>()
                        .orderByDesc(AsmSnRelation::getRelationId)
                        .last("LIMIT 200"));
    }
}
