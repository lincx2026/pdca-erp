package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.FasRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FasRecordMapper extends BaseMapper<FasRecord> {

    @Select("<script>" +
            "SELECT * FROM fas_record " +
            "<where>" +
            "  <if test='orderId != null'>" +
            "    AND order_id = #{orderId}" +
            "  </if>" +
            "  <if test='recordType != null and recordType != \"\"'>" +
            "    AND record_type = #{recordType}" +
            "  </if>" +
            "  <if test='operator != null and operator != \"\"'>" +
            "    AND operator LIKE CONCAT('%', CAST(#{operator} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY create_time DESC" +
            "</script>")
    IPage<FasRecord> selectPage(Page<FasRecord> page,
                                @Param("orderId") Long orderId,
                                @Param("recordType") String recordType,
                                @Param("operator") String operator);

    @Select("SELECT * FROM fas_record WHERE order_id = #{orderId} ORDER BY event_time DESC, record_id DESC")
    List<FasRecord> listByOrder(@Param("orderId") Long orderId);
}
