package com.pcba.erp.assembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmFeederLoad;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Feeder 上料 Mapper
 */
@Mapper
public interface AsmFeederLoadMapper extends BaseMapper<AsmFeederLoad> {

    @Select("<script>" +
            "SELECT * FROM asm_feeder_load " +
            "<where>" +
            "  <if test='equipmentCode != null and equipmentCode != \"\"'>" +
            "    AND equipment_code LIKE CONCAT('%', CAST(#{equipmentCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='feederNo != null and feederNo != \"\"'>" +
            "    AND feeder_no = #{feederNo}" +
            "  </if>" +
            "  <if test='materialId != null'>" +
            "    AND material_id = #{materialId}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "</where>" +
            " ORDER BY load_id DESC" +
            "</script>")
    IPage<AsmFeederLoad> selectPage(Page<AsmFeederLoad> page,
                                    @Param("equipmentCode") String equipmentCode,
                                    @Param("feederNo") String feederNo,
                                    @Param("materialId") Long materialId,
                                    @Param("status") Integer status);
}
