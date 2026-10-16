package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pmm.entity.PmmJigLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface PmmJigLogMapper extends BaseMapper<PmmJigLog> {

    @Select("<script>" +
            "SELECT * FROM pmm_jig_log " +
            "<where>" +
            "  <if test='jigId != null'>" +
            "    AND jig_id = #{jigId}" +
            "  </if>" +
            "  <if test='jigNo != null and jigNo != \"\"'>" +
            "    AND jig_no LIKE CONCAT('%', CAST(#{jigNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='eventType != null and eventType != \"\"'>" +
            "    AND event_type = #{eventType}" +
            "  </if>" +
            "  <if test='eventTitle != null and eventTitle != \"\"'>" +
            "    AND event_title LIKE CONCAT('%', CAST(#{eventTitle} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='eventResult != null and eventResult != \"\"'>" +
            "    AND event_result = #{eventResult}" +
            "  </if>" +
            "  <if test='borrowerName != null and borrowerName != \"\"'>" +
            "    AND borrower_name LIKE CONCAT('%', CAST(#{borrowerName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='startTime != null'>" +
            "    AND event_time &gt;= #{startTime}" +
            "  </if>" +
            "  <if test='endTime != null'>" +
            "    AND event_time &lt;= #{endTime}" +
            "  </if>" +
            "</where>" +
            " ORDER BY event_time DESC, log_id DESC" +
            "</script>")
    IPage<PmmJigLog> selectPage(Page<PmmJigLog> page,
                                 @Param("jigId") Long jigId,
                                 @Param("jigNo") String jigNo,
                                 @Param("eventType") String eventType,
                                 @Param("eventTitle") String eventTitle,
                                 @Param("eventResult") String eventResult,
                                 @Param("borrowerName") String borrowerName,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);
}
