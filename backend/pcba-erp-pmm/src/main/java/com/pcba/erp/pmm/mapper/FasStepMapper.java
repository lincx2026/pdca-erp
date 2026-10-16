package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.FasStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FasStepMapper extends BaseMapper<FasStep> {

    @Select("<script>" +
            "SELECT * FROM fas_step " +
            "<where>" +
            "  <if test='orderId != null'>" +
            "    AND order_id = #{orderId}" +
            "  </if>" +
            "  <if test='stepName != null and stepName != \"\"'>" +
            "    AND step_name LIKE CONCAT('%', CAST(#{stepName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "</where>" +
            " ORDER BY step_no ASC" +
            "</script>")
    IPage<FasStep> selectPage(Page<FasStep> page,
                              @Param("orderId") Long orderId,
                              @Param("stepName") String stepName,
                              @Param("status") Integer status);

    @Select("SELECT * FROM fas_step WHERE order_id = #{orderId} ORDER BY step_no ASC")
    List<FasStep> listByOrder(@Param("orderId") Long orderId);
}
