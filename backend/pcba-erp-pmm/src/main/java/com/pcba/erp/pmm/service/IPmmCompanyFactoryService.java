package com.pcba.erp.pmm.service;

import com.pcba.erp.pmm.entity.CompanyFactory;

import java.util.List;

/**
 * 厂区/产线 Service (树形结构)
 */
public interface IPmmCompanyFactoryService {

    /** 查询全部并递归构建树 */
    List<CompanyFactory> listTree();

    /** 详情 */
    CompanyFactory getDetail(Long id);

    /** 新增 */
    Long create(CompanyFactory entity);

    /** 编辑 */
    void update(CompanyFactory entity);

    /** 删除(检查是否有子节点) */
    void remove(Long id);
}
