package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmProcessTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmmProcessTaskMapper extends BaseMapper<PmmProcessTask> {

    @Select("<script>" +
            "SELECT * FROM pmm_process_task " +
            "<where>" +
            "  <if test='workOrderId != null'> AND work_order_id = #{workOrderId} </if>" +
            "  <if test='processName != null and processName != \"\"'>" +
            "    AND process_name LIKE CONCAT('%', CAST(#{processName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='workCenter != null and workCenter != \"\"'>" +
            "    AND work_center LIKE CONCAT('%', CAST(#{workCenter} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'> AND status = #{status} </if>" +
            "  <if test='assignedUserId != null'> AND assigned_user_id = #{assignedUserId} </if>" +
            "</where>" +
            " ORDER BY work_order_id DESC, sequence ASC, task_id DESC" +
            "</script>")
    IPage<PmmProcessTask> selectPage(Page<PmmProcessTask> page,
                                     @Param("workOrderId") Long workOrderId,
                                     @Param("processName") String processName,
                                     @Param("workCenter") String workCenter,
                                     @Param("status") Integer status,
                                     @Param("assignedUserId") Long assignedUserId);
}
