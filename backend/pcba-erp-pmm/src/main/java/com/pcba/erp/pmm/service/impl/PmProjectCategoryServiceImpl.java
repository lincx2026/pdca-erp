package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmProjectCategory;
import com.pcba.erp.pmm.mapper.PmProjectCategoryMapper;
import com.pcba.erp.pmm.service.IPmProjectCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PmProjectCategoryServiceImpl implements IPmProjectCategoryService {

    private final PmProjectCategoryMapper mapper;

    @Override
    public List<PmProjectCategory> list() {
        return mapper.selectList(new LambdaQueryWrapper<PmProjectCategory>()
                .orderByAsc(PmProjectCategory::getOrderNum)
                .orderByAsc(PmProjectCategory::getCategoryId));
    }

    @Override
    public List<PmProjectCategory> tree() {
        List<PmProjectCategory> all = list();
        return buildTree(all);
    }

    private List<PmProjectCategory> buildTree(List<PmProjectCategory> all) {
        Map<Long, List<PmProjectCategory>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != 0L)
                .collect(Collectors.groupingBy(PmProjectCategory::getParentId));
        List<PmProjectCategory> roots = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0L)
                .collect(Collectors.toList());
        fillChildren(roots, childrenMap);
        return roots;
    }

    private void fillChildren(List<PmProjectCategory> nodes, Map<Long, List<PmProjectCategory>> childrenMap) {
        for (PmProjectCategory node : nodes) {
            List<PmProjectCategory> children = childrenMap.get(node.getCategoryId());
            if (children != null && !children.isEmpty()) {
                node.setChildren(children);
                fillChildren(children, childrenMap);
            } else {
                node.setChildren(new ArrayList<>());
            }
        }
    }

    @Override
    public PmProjectCategory getDetail(Long id) {
        if (id == null) throw new ServiceException("分类ID不能为空");
        PmProjectCategory c = mapper.selectById(id);
        if (c == null) throw new ServiceException("分类不存在");
        return c;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmProjectCategory entity) {
        if (entity.getCategoryName() == null || entity.getCategoryName().trim().isEmpty())
            throw new ServiceException("分类名称不能为空");
        if (entity.getParentId() == null) entity.setParentId(0L);
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getOrderNum() == null) entity.setOrderNum(0);
        mapper.insert(entity);
        return entity.getCategoryId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmProjectCategory entity) {
        if (entity.getCategoryId() == null) throw new ServiceException("分类ID不能为空");
        if (mapper.selectById(entity.getCategoryId()) == null) throw new ServiceException("分类不存在");
        // 不允许把自己设为自己的父节点
        if (entity.getParentId() != null && entity.getParentId().equals(entity.getCategoryId()))
            throw new ServiceException("不能将自身设为父分类");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("分类ID不能为空");
        // 检查是否有子分类
        Long childCount = mapper.selectCount(new LambdaQueryWrapper<PmProjectCategory>()
                .eq(PmProjectCategory::getParentId, id));
        if (childCount != null && childCount > 0)
            throw new ServiceException("存在子分类，不允许删除");
        return mapper.deleteById(id) > 0;
    }
}
