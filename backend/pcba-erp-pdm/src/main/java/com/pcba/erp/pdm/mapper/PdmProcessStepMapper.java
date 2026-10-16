package com.pcba.erp.pdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pdm.entity.PdmProcessStep;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PdmProcessStepMapper extends BaseMapper<PdmProcessStep> {

    @Select("SELECT * FROM pdm_process_step WHERE route_id = #{routeId} ORDER BY step_no ASC")
    List<PdmProcessStep> selectByRouteId(@Param("routeId") Long routeId);

    @Delete("DELETE FROM pdm_process_step WHERE route_id = #{routeId}")
    int deleteByRouteId(@Param("routeId") Long routeId);
}
