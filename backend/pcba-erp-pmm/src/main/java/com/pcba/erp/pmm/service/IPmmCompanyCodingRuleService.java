package com.pcba.erp.pmm.service;

import com.pcba.erp.pmm.entity.CompanyCodingRule;

import java.util.List;

/**
 * 编码规则 Service
 */
public interface IPmmCompanyCodingRuleService {

    /** 查询全部 */
    List<CompanyCodingRule> listAll();

    /** 新增或更新 */
    void save(CompanyCodingRule entity);

    /** 删除 */
    void remove(Long id);

    /** 根据模块名生成编码(prefix + separator + 日期(如有) + 零填充流水号, 生成后 currentSeq+1) */
    String generateCode(String moduleName);
}
