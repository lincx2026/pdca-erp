package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pmm.entity.CompanyProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 公司档案 Mapper (单行表, profile_id 固定为 1)
 */
@Mapper
public interface CompanyProfileMapper extends BaseMapper<CompanyProfile> {

    /** 查询 profile_id=1 的单行记录 */
    @Select("SELECT * FROM company_profile WHERE profile_id = 1 LIMIT 1")
    CompanyProfile selectOne();
}
