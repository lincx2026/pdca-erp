package com.pcba.erp.pmm.service.impl;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.CompanyProfile;
import com.pcba.erp.pmm.mapper.CompanyProfileMapper;
import com.pcba.erp.pmm.service.IPmmCompanyProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公司档案 Service 实现 (单行表, profile_id 固定为 1)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyProfileServiceImpl implements IPmmCompanyProfileService {

    private static final long PROFILE_ID = 1L;

    private final CompanyProfileMapper profileMapper;

    @Override
    public CompanyProfile get() {
        CompanyProfile profile = profileMapper.selectOne();
        if (profile == null) {
            // 单行表未初始化, 返回一个带默认主键的空对象, 避免前端报空指针
            profile = new CompanyProfile();
            profile.setProfileId(PROFILE_ID);
            profile.setCurrency("CNY");
            profile.setDecimalPlaces(2);
        }
        return profile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(CompanyProfile profile) {
        if (profile == null) throw new ServiceException("公司档案不能为空");
        profile.setProfileId(PROFILE_ID);
        CompanyProfile existing = profileMapper.selectOne();
        if (existing == null) {
            profileMapper.insert(profile);
        } else {
            profileMapper.updateById(profile);
        }
    }
}
