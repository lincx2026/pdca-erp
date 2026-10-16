package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmWorkReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmmWorkReportMapper extends BaseMapper<PmmWorkReport> {

    @Select("<script>" +
            "SELECT * FROM pmm_work_report " +
            "<where>" +
            "  <if test='reportNo != null and reportNo != \"\"'>" +
            "    AND report_no LIKE CONCAT('%', CAST(#{reportNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='workOrderId != null'> AND work_order_id = #{workOrderId} </if>" +
            "  <if test='taskId != null'> AND task_id = #{taskId} </if>" +
            "  <if test='operatorId != null'> AND operator_id = #{operatorId} </if>" +
            "  <if test='reportType != null and reportType != \"\"'> AND report_type = #{reportType} </if>" +
            "  <if test='shift != null and shift != \"\"'> AND shift = #{shift} </if>" +
            "</where>" +
            " ORDER BY report_id DESC" +
            "</script>")
    IPage<PmmWorkReport> selectPage(Page<PmmWorkReport> page,
                                    @Param("reportNo") String reportNo,
                                    @Param("workOrderId") Long workOrderId,
                                    @Param("taskId") Long taskId,
                                    @Param("operatorId") Long operatorId,
                                    @Param("reportType") String reportType,
                                    @Param("shift") String shift);

    @Select("SELECT COUNT(*) FROM pmm_work_report WHERE report_no = #{reportNo} " +
            "AND (#{reportId} IS NULL OR report_id != #{reportId})")
    int countDuplicateNo(@Param("reportNo") String reportNo, @Param("reportId") Long reportId);
}
