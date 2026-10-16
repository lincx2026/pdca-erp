package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pmm.entity.CompanyParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统参数 Mapper
 */
@Mapper
public interface CompanyParamMapper extends BaseMapper<CompanyParam> {

    /** 查询全部, 按 category、param_key 升序 */
    @Select("SELECT * FROM company_param ORDER BY category ASC, param_key ASC")
    List<CompanyParam> selectAll();
}
