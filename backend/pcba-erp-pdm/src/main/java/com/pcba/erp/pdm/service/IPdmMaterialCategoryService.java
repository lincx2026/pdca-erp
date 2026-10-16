package com.pcba.erp.pdm.service;

import com.pcba.erp.pdm.entity.PdmMaterialCategory;

import java.util.List;

/**
 * 物料分类 Service
 */
public interface IPdmMaterialCategoryService {

    /** 查询分类树 (含子节点), 可按名称/状态过滤 */
    List<PdmMaterialCategory> selectCategoryTree(String categoryName, Integer status);

    /** 查询扁平列表 (不含子节点) */
    List<PdmMaterialCategory> selectCategoryList(String categoryName, Integer status);

    /** 详情 */
    PdmMaterialCategory getById(Long categoryId);

    /** 新增分类 (自动维护 ancestors) */
    boolean save(PdmMaterialCategory category);

    /** 修改分类 */
    boolean updateById(PdmMaterialCategory category);

    /** 删除分类 (校验无下级+无物料) */
    boolean removeById(Long categoryId);

    /** 分类树下拉 (排除某节点及子级, 防止循环父级) */
    List<PdmMaterialCategory> excludeTree(Long excludeId);

    /** 加载分类下物料数量 */
    void fillMaterialCount(List<PdmMaterialCategory> tree);
}
