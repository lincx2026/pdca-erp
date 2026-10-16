package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmWorkOrderBom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmmWorkOrderBomMapper extends BaseMapper<PmmWorkOrderBom> {

    @Select("<script>" +
            "SELECT * FROM pmm_work_order_bom " +
            "<where>" +
            "  <if test='workOrderId != null'> AND work_order_id = #{workOrderId} </if>" +
            "  <if test='materialCode != null and materialCode != \"\"'>" +
            "    AND material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='materialName != null and materialName != \"\"'>" +
            "    AND material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY work_order_id DESC, line_no ASC" +
            "</script>")
    IPage<PmmWorkOrderBom> selectPage(Page<PmmWorkOrderBom> page,
                                      @Param("workOrderId") Long workOrderId,
                                      @Param("materialCode") String materialCode,
                                      @Param("materialName") String materialName);

    @Select("SELECT COUNT(*) FROM pmm_work_order_bom WHERE work_order_id=#{workOrderId} AND line_no=#{lineNo} " +
            "AND (#{bomLineId} IS NULL OR bom_line_id != #{bomLineId})")
    int countDuplicateLine(@Param("workOrderId") Long workOrderId,
                           @Param("lineNo") Integer lineNo,
                           @Param("bomLineId") Long bomLineId);
}
