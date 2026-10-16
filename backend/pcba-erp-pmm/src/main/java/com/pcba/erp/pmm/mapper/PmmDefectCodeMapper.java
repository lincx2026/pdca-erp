package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmDefectCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmmDefectCodeMapper extends BaseMapper<PmmDefectCode> {

    @Select("<script>" +
            "SELECT * FROM pmm_defect_code " +
            "<where>" +
            "  <if test='defectCode != null and defectCode != \"\"'>" +
            "    AND defect_code LIKE CONCAT('%', CAST(#{defectCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='defectName != null and defectName != \"\"'>" +
            "    AND defect_name LIKE CONCAT('%', CAST(#{defectName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='defectCategory != null and defectCategory != \"\"'>" +
            "    AND defect_category = #{defectCategory}" +
            "  </if>" +
            "  <if test='status != null'> AND status = #{status} </if>" +
            "</where>" +
            " ORDER BY code_id ASC" +
            "</script>")
    IPage<PmmDefectCode> selectPage(Page<PmmDefectCode> page,
                                    @Param("defectCode") String defectCode,
                                    @Param("defectName") String defectName,
                                    @Param("defectCategory") String defectCategory,
                                    @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM pmm_defect_code WHERE defect_code = #{defectCode} " +
            "AND (#{codeId} IS NULL OR code_id != #{codeId})")
    int countDuplicate(@Param("defectCode") String defectCode, @Param("codeId") Long codeId);
}
