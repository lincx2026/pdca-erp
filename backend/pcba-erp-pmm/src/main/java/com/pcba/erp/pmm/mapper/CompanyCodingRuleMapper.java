package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pmm.entity.CompanyCodingRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 编码规则 Mapper
 */
@Mapper
public interface CompanyCodingRuleMapper extends BaseMapper<CompanyCodingRule> {

    /** 查询全部, 按 module_name 升序 */
    @Select("SELECT * FROM company_coding_rule ORDER BY module_name ASC")
    List<CompanyCodingRule> selectAll();
}
