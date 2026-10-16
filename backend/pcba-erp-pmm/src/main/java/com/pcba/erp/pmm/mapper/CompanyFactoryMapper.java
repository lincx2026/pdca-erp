package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pmm.entity.CompanyFactory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 厂区/产线 Mapper (树形结构)
 */
@Mapper
public interface CompanyFactoryMapper extends BaseMapper<CompanyFactory> {

    /** 查询全部, 按 order_num 升序 */
    @Select("SELECT * FROM company_factory ORDER BY order_num ASC")
    List<CompanyFactory> selectAll();

    /** 检查编码重复(排除指定 ID) */
    @Select("SELECT COUNT(*) FROM company_factory WHERE factory_code = #{factoryCode} " +
            "AND (#{excludeId} IS NULL OR factory_id != #{excludeId})")
    int countDuplicateCode(@Param("factoryCode") String factoryCode,
                           @Param("excludeId") Long excludeId);
}
