package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmSchedulePlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface PmmSchedulePlanMapper extends BaseMapper<PmmSchedulePlan> {

    @Select("<script>" +
            "SELECT * FROM pmm_schedule_plan " +
            "<where>" +
            "  <if test='scheduleDate != null'> AND schedule_date = #{scheduleDate} </if>" +
            "  <if test='shift != null and shift != \"\"'> AND shift = #{shift} </if>" +
            "  <if test='workOrderId != null'> AND work_order_id = #{workOrderId} </if>" +
            "  <if test='workCenter != null and workCenter != \"\"'>" +
            "    AND work_center LIKE CONCAT('%', CAST(#{workCenter} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='machineCode != null and machineCode != \"\"'>" +
            "    AND machine_code LIKE CONCAT('%', CAST(#{machineCode} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY schedule_date DESC, shift, work_center, machine_code, sequence, plan_id DESC" +
            "</script>")
    IPage<PmmSchedulePlan> selectPage(Page<PmmSchedulePlan> page,
                                      @Param("scheduleDate") LocalDate scheduleDate,
                                      @Param("shift") String shift,
                                      @Param("workOrderId") Long workOrderId,
                                      @Param("workCenter") String workCenter,
                                      @Param("machineCode") String machineCode);
}
