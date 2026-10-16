package com.pcba.erp.pmm.service;

import com.pcba.erp.pmm.entity.CompanyProfile;

/**
 * 公司档案 Service (单行表)
 */
public interface IPmmCompanyProfileService {

    /** 获取公司档案(profile_id=1) */
    CompanyProfile get();

    /** 保存公司档案(更新 profile_id=1 的单行记录) */
    void save(CompanyProfile profile);
}
