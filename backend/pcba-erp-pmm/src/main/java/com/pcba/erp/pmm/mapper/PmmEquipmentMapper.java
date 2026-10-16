package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmEquipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmmEquipmentMapper extends BaseMapper<PmmEquipment> {

    @Select("<script>" +
            "SELECT * FROM pmm_equipment " +
            "<where>" +
            "  <if test='equipmentNo != null and equipmentNo != \"\"'>" +
            "    AND equipment_no LIKE CONCAT('%', CAST(#{equipmentNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='equipmentName != null and equipmentName != \"\"'>" +
            "    AND equipment_name LIKE CONCAT('%', CAST(#{equipmentName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='equipmentType != null and equipmentType != \"\"'>" +
            "    AND equipment_type = #{equipmentType}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  <if test='equipLevel != null'>" +
            "    AND equip_level = #{equipLevel}" +
            "  </if>" +
            "  <if test='workshop != null and workshop != \"\"'>" +
            "    AND workshop LIKE CONCAT('%', CAST(#{workshop} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='workCenter != null and workCenter != \"\"'>" +
            "    AND work_center = #{workCenter}" +
            "  </if>" +
            "</where>" +
            " ORDER BY equip_level ASC, equipment_no ASC" +
            "</script>")
    IPage<PmmEquipment> selectPage(Page<PmmEquipment> page,
                                   @Param("equipmentNo")   String equipmentNo,
                                   @Param("equipmentName") String equipmentName,
                                   @Param("equipmentType") String equipmentType,
                                   @Param("status")        Integer status,
                                   @Param("equipLevel")    Integer equipLevel,
                                   @Param("workshop")      String workshop,
                                   @Param("workCenter")    String workCenter);

    @Select("SELECT COUNT(*) FROM pmm_equipment WHERE equipment_no = #{equipmentNo} " +
            "AND (#{equipmentId} IS NULL OR equipment_id != #{equipmentId})")
    int countDuplicateNo(@Param("equipmentNo") String equipmentNo, @Param("equipmentId") Long equipmentId);
}
