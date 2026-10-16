package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmProjectMapper extends BaseMapper<PmProject> {

    @Select("<script>" +
            "SELECT * FROM pm_project " +
            "<where>" +
            "  <if test='projectCode != null and projectCode != \"\"'>" +
            "    AND project_code LIKE CONCAT('%', CAST(#{projectCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='projectName != null and projectName != \"\"'>" +
            "    AND project_name LIKE CONCAT('%', CAST(#{projectName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='customerName != null and customerName != \"\"'>" +
            "    AND customer_name LIKE CONCAT('%', CAST(#{customerName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='manager != null and manager != \"\"'>" +
            "    AND manager LIKE CONCAT('%', CAST(#{manager} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='categoryId != null'> AND category_id = #{categoryId} </if>" +
            "  <if test='status != null'> AND status = #{status} </if>" +
            "  <if test='priority != null and priority != \"\"'> AND priority = #{priority} </if>" +
            "</where>" +
            " ORDER BY status ASC, priority ASC, create_time DESC" +
            "</script>")
    IPage<PmProject> selectPage(Page<PmProject> page,
                                @Param("projectCode") String projectCode,
                                @Param("projectName") String projectName,
                                @Param("customerName") String customerName,
                                @Param("manager") String manager,
                                @Param("categoryId") Long categoryId,
                                @Param("status") Integer status,
                                @Param("priority") String priority);
}
