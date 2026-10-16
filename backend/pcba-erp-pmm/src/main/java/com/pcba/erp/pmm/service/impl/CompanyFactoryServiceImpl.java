package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.CompanyFactory;
import com.pcba.erp.pmm.mapper.CompanyFactoryMapper;
import com.pcba.erp.pmm.service.IPmmCompanyFactoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 厂区/产线 Service 实现 (树形结构)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyFactoryServiceImpl implements IPmmCompanyFactoryService {

    private final CompanyFactoryMapper factoryMapper;

    @Override
    public List<CompanyFactory> listTree() {
        List<CompanyFactory> all = factoryMapper.selectAll();
        return buildTree(all);
    }

    @Override
    public CompanyFactory getDetail(Long id) {
        if (id == null) throw new ServiceException("厂区/产线ID不能为空");
        CompanyFactory node = factoryMapper.selectById(id);
        if (node == null) throw new ServiceException("厂区/产线不存在");
        return node;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CompanyFactory entity) {
        if (entity == null) throw new ServiceException("厂区/产线不能为空");
        if (entity.getFactoryCode() == null || entity.getFactoryCode().isBlank())
            throw new ServiceException("厂区编码不能为空");
        if (factoryMapper.countDuplicateCode(entity.getFactoryCode(), null) > 0)
            throw new ServiceException("厂区编码【" + entity.getFactoryCode() + "】已存在");
        if (entity.getParentId() == null) entity.setParentId(0L);
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getOrderNum() == null) entity.setOrderNum(0);
        factoryMapper.insert(entity);
        return entity.getFactoryId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CompanyFactory entity) {
        if (entity == null || entity.getFactoryId() == null)
            throw new ServiceException("厂区/产线ID不能为空");
        CompanyFactory old = factoryMapper.selectById(entity.getFactoryId());
        if (old == null) throw new ServiceException("厂区/产线不存在");
        if (entity.getFactoryCode() != null && !entity.getFactoryCode().isBlank()) {
            if (factoryMapper.countDuplicateCode(entity.getFactoryCode(), entity.getFactoryId()) > 0)
                throw new ServiceException("厂区编码【" + entity.getFactoryCode() + "】已存在");
        }
        factoryMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (id == null) throw new ServiceException("厂区/产线ID不能为空");
        CompanyFactory node = factoryMapper.selectById(id);
        if (node == null) throw new ServiceException("厂区/产线不存在");
        // 检查是否有子节点
        Long childCount = factoryMapper.selectCount(
                new LambdaQueryWrapper<CompanyFactory>().eq(CompanyFactory::getParentId, id));
        if (childCount != null && childCount > 0)
            throw new ServiceException("该节点下存在子节点, 请先删除子节点后再删除");
        factoryMapper.deleteById(id);
    }

    // =========================================================
    //                      Helper: 递归构建树
    // =========================================================

    /**
     * 把扁平列表构建成树形结构(根节点 parentId=0 或 null)
     */
    private List<CompanyFactory> buildTree(List<CompanyFactory> all) {
        if (all == null || all.isEmpty()) return new ArrayList<>();
        Map<Long, List<CompanyFactory>> grouped = all.stream()
                .collect(Collectors.groupingBy(f -> f.getParentId() == null ? 0L : f.getParentId()));
        List<CompanyFactory> roots = grouped.getOrDefault(0L, new ArrayList<>());
        for (CompanyFactory root : roots) {
            setChildren(root, grouped);
        }
        return roots;
    }

    private void setChildren(CompanyFactory parent, Map<Long, List<CompanyFactory>> grouped) {
        List<CompanyFactory> children = grouped.get(parent.getFactoryId());
        if (children != null && !children.isEmpty()) {
            parent.setChildren(children);
            for (CompanyFactory child : children) {
                setChildren(child, grouped);
            }
        } else {
            parent.setChildren(new ArrayList<>());
        }
    }
}
