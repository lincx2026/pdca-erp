package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmEquipmentLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmmEquipmentLogMapper extends BaseMapper<PmmEquipmentLog> {

    @Select("<script>" +
            "SELECT * FROM pmm_equipment_log " +
            "<where>" +
            "  <if test='equipmentId != null'> AND equipment_id = #{equipmentId} </if>" +
            "  <if test='equipmentNo != null and equipmentNo != \"\"'> AND equipment_no = #{equipmentNo} </if>" +
            "  <if test='eventType != null and eventType != \"\"'> AND event_type = #{eventType} </if>" +
            "  <if test='eventResult != null and eventResult != \"\"'> AND event_result = #{eventResult} </if>" +
            "</where>" +
            " ORDER BY event_time DESC, log_id DESC" +
            "</script>")
    IPage<PmmEquipmentLog> selectPage(Page<PmmEquipmentLog> page,
                                      @Param("equipmentId") Long equipmentId,
                                      @Param("equipmentNo") String equipmentNo,
                                      @Param("eventType")   String eventType,
                                      @Param("eventResult") String eventResult);
}
