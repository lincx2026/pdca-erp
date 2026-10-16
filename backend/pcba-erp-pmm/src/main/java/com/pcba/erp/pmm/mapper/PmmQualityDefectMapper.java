package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmQualityDefect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmmQualityDefectMapper extends BaseMapper<PmmQualityDefect> {

    @Select("<script>" +
            "SELECT * FROM pmm_quality_defect " +
            "<where>" +
            "  <if test='workOrderId != null'> AND work_order_id = #{workOrderId} </if>" +
            "  <if test='defectCode != null and defectCode != \"\"'> AND defect_code = #{defectCode} </if>" +
            "  <if test='defectLevel != null and defectLevel != \"\"'> AND defect_level = #{defectLevel} </if>" +
            "  <if test='checkType != null and checkType != \"\"'> AND check_type = #{checkType} </if>" +
            "  <if test='disposition != null and disposition != \"\"'> AND disposition = #{disposition} </if>" +
            "  <if test='processName != null and processName != \"\"'>" +
            "    AND process_name LIKE CONCAT('%', CAST(#{processName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='inspectorId != null'> AND inspector_id = #{inspectorId} </if>" +
            "</where>" +
            " ORDER BY defect_id DESC" +
            "</script>")
    IPage<PmmQualityDefect> selectPage(Page<PmmQualityDefect> page,
                                       @Param("workOrderId") Long workOrderId,
                                       @Param("defectCode") String defectCode,
                                       @Param("defectLevel") String defectLevel,
                                       @Param("checkType") String checkType,
                                       @Param("disposition") String disposition,
                                       @Param("processName") String processName,
                                       @Param("inspectorId") Long inspectorId);
}
