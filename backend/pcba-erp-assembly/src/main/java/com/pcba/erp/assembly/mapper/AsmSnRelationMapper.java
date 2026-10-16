package com.pcba.erp.assembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmSnRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * SN 父子关系 Mapper
 */
@Mapper
public interface AsmSnRelationMapper extends BaseMapper<AsmSnRelation> {

    @Select("<script>" +
            "SELECT * FROM asm_sn_relation " +
            "<where>" +
            "  <if test='parentSnId != null'>" +
            "    AND parent_sn_id = #{parentSnId}" +
            "  </if>" +
            "  <if test='childSnId != null'>" +
            "    AND child_sn_id = #{childSnId}" +
            "  </if>" +
            "</where>" +
            " ORDER BY relation_id DESC" +
            "</script>")
    IPage<AsmSnRelation> selectPage(Page<AsmSnRelation> page,
                                    @Param("parentSnId") Long parentSnId,
                                    @Param("childSnId") Long childSnId);
}
