package com.pcba.erp.pmm.service;

import com.pcba.erp.pmm.entity.CompanyParam;

import java.util.List;

/**
 * 系统参数 Service
 */
public interface IPmmCompanyParamService {

    /** 查询全部 */
    List<CompanyParam> listAll();

    /** 新增或更新 */
    void save(CompanyParam entity);

    /** 删除 */
    void remove(Long id);

    /** 按 param_key 查询 */
    CompanyParam getByKey(String key);
}
