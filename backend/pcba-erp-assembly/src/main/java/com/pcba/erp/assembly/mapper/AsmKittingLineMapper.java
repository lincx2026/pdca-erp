package com.pcba.erp.assembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmKittingLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 配料明细行 Mapper
 */
@Mapper
public interface AsmKittingLineMapper extends BaseMapper<AsmKittingLine> {

    @Select("<script>" +
            "SELECT * FROM asm_kitting_line " +
            "<where>" +
            "  <if test='kittingId != null'>" +
            "    AND kitting_id = #{kittingId}" +
            "  </if>" +
            "  <if test='materialId != null'>" +
            "    AND material_id = #{materialId}" +
            "  </if>" +
            "  <if test='lineStatus != null'>" +
            "    AND line_status = #{lineStatus}" +
            "  </if>" +
            "</where>" +
            " ORDER BY kitting_line_id DESC" +
            "</script>")
    IPage<AsmKittingLine> selectPage(Page<AsmKittingLine> page,
                                     @Param("kittingId") Long kittingId,
                                     @Param("materialId") Long materialId,
                                     @Param("lineStatus") Integer lineStatus);
}
