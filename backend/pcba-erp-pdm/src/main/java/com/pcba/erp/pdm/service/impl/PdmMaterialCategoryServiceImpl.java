package com.pcba.erp.pdm.service.impl;

import com.pcba.erp.common.annotation.DataScope;
import com.pcba.erp.common.aspectj.DataScopeThreadLocal;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.entity.PdmMaterialCategory;
import com.pcba.erp.pdm.mapper.PdmMaterialCategoryMapper;
import com.pcba.erp.pdm.service.IPdmMaterialCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 物料分类 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmMaterialCategoryServiceImpl implements IPdmMaterialCategoryService {

    private final PdmMaterialCategoryMapper categoryMapper;

    @Override
    @DataScope(deptField = "dept_id", userField = "create_by")
    public List<PdmMaterialCategory> selectCategoryTree(String categoryName, Integer status) {
        String dataScope = DataScopeThreadLocal.getAndClear();
        List<PdmMaterialCategory> flat = categoryMapper.selectCategoryList(categoryName, status, dataScope);
        return buildTree(flat);
    }

    @Override
    @DataScope(deptField = "dept_id", userField = "create_by")
    public List<PdmMaterialCategory> selectCategoryList(String categoryName, Integer status) {
        String dataScope = DataScopeThreadLocal.getAndClear();
        return categoryMapper.selectCategoryList(categoryName, status, dataScope);
    }

    @Override
    public PdmMaterialCategory getById(Long categoryId) {
        return categoryMapper.selectById(categoryId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(PdmMaterialCategory category) {
        // 1. 名称唯一性校验 (同级)
        int dupName = categoryMapper.countDuplicateName(
                category.getParentId() == null ? 0L : category.getParentId(),
                category.getCategoryName(), null);
        if (dupName > 0) throw new ServiceException("同级分类名称重复");

        // 2. 编码前缀全局唯一
        int dupCode = categoryMapper.countDuplicateCode(category.getCategoryCode(), null);
        if (dupCode > 0) throw new ServiceException("分类编码前缀【" + category.getCategoryCode() + "】已存在");

        // 3. 自动维护 ancestors
        Long pid = category.getParentId() == null ? 0L : category.getParentId();
        category.setParentId(pid);
        if (pid.equals(0L)) {
            category.setAncestors("0");
        } else {
            PdmMaterialCategory parent = categoryMapper.selectById(pid);
            if (parent == null) throw new ServiceException("父分类不存在");
            category.setAncestors(parent.getAncestors() + "," + pid);
        }
        if (category.getOrderNum() == null) category.setOrderNum(0);
        if (category.getStatus() == null) category.setStatus(1);
        return categoryMapper.insert(category) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(PdmMaterialCategory category) {
        if (category.getCategoryId() == null) throw new ServiceException("分类ID不能为空");
        PdmMaterialCategory old = categoryMapper.selectById(category.getCategoryId());
        if (old == null) throw new ServiceException("分类不存在");

        // 名称唯一性校验
        Long pid = category.getParentId() == null ? old.getParentId() : category.getParentId();
        if (category.getCategoryName() != null) {
            int dup = categoryMapper.countDuplicateName(pid, category.getCategoryName(), category.getCategoryId());
            if (dup > 0) throw new ServiceException("同级分类名称重复");
        }
        // 编码前缀唯一
        if (category.getCategoryCode() != null) {
            int dup = categoryMapper.countDuplicateCode(category.getCategoryCode(), category.getCategoryId());
            if (dup > 0) throw new ServiceException("分类编码前缀已存在");
        }
        return categoryMapper.updateById(category) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Long categoryId) {
        // 1. 是否有子分类
        if (categoryMapper.countChildren(categoryId) > 0) {
            throw new ServiceException("该分类下仍有子分类, 不能删除");
        }
        // 2. 是否有物料
        if (categoryMapper.countMaterialByCategoryId(categoryId) > 0) {
            throw new ServiceException("该分类下仍有物料, 请先转移或删除物料");
        }
        return categoryMapper.deleteById(categoryId) > 0;
    }

    @Override
    public List<PdmMaterialCategory> excludeTree(Long excludeId) {
        List<PdmMaterialCategory> root = selectCategoryTree(null, null);
        if (excludeId == null) return root;
        Set<Long> excludeIds = new HashSet<>();
        collectExclude(root, excludeId, excludeIds);
        filterTree(root, excludeIds);
        return root;
    }

    @Override
    public void fillMaterialCount(List<PdmMaterialCategory> tree) {
        if (tree == null) return;
        for (PdmMaterialCategory c : tree) {
            c.setMaterialCount(categoryMapper.countMaterialByCategoryId(c.getCategoryId()));
            fillMaterialCount(c.getChildren());
        }
    }

    // =============== 私有工具 ===============
    /** flat 列表 → 树 (按 parentId 组合) */
    private List<PdmMaterialCategory> buildTree(List<PdmMaterialCategory> flat) {
        if (flat == null || flat.isEmpty()) return Collections.emptyList();
        Map<Long, PdmMaterialCategory> map = flat.stream()
                .collect(Collectors.toMap(PdmMaterialCategory::getCategoryId, c -> c));
        List<PdmMaterialCategory> roots = new ArrayList<>();
        for (PdmMaterialCategory c : flat) {
            Long pid = c.getParentId() == null ? 0L : c.getParentId();
            if (pid.equals(0L) || !map.containsKey(pid)) {
                roots.add(c);
            } else {
                PdmMaterialCategory parent = map.get(pid);
                if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                parent.getChildren().add(c);
            }
        }
        // 每层按 order_num 排序
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<PdmMaterialCategory> nodes) {
        if (nodes == null) return;
        nodes.sort(Comparator.comparingInt(n -> n.getOrderNum() == null ? 0 : n.getOrderNum()));
        for (PdmMaterialCategory n : nodes) sortTree(n.getChildren());
    }

    private void collectExclude(List<PdmMaterialCategory> nodes, Long target, Set<Long> result) {
        if (nodes == null) return;
        for (PdmMaterialCategory n : nodes) {
            if (target.equals(n.getCategoryId())) {
                result.add(n.getCategoryId());
                addAllDescendants(n, result);
            }
            collectExclude(n.getChildren(), target, result);
        }
    }

    private void addAllDescendants(PdmMaterialCategory n, Set<Long> result) {
        if (n.getChildren() == null) return;
        for (PdmMaterialCategory c : n.getChildren()) {
            result.add(c.getCategoryId());
            addAllDescendants(c, result);
        }
    }

    private void filterTree(List<PdmMaterialCategory> nodes, Set<Long> excludeIds) {
        if (nodes == null) return;
        nodes.removeIf(n -> excludeIds.contains(n.getCategoryId()));
        for (PdmMaterialCategory n : nodes) filterTree(n.getChildren(), excludeIds);
    }
}
