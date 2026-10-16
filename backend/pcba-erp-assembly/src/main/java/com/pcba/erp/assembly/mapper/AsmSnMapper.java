package com.pcba.erp.assembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmSn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * SN 主表 Mapper
 */
@Mapper
public interface AsmSnMapper extends BaseMapper<AsmSn> {

    @Select("<script>" +
            "SELECT * FROM asm_sn " +
            "<where>" +
            "  <if test='snCode != null and snCode != \"\"'>" +
            "    AND sn_code LIKE CONCAT('%', CAST(#{snCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='snType != null and snType != \"\"'>" +
            "    AND sn_type = #{snType}" +
            "  </if>" +
            "  <if test='snLevel != null'>" +
            "    AND sn_level = #{snLevel}" +
            "  </if>" +
            "  <if test='currentStatus != null'>" +
            "    AND current_status = #{currentStatus}" +
            "  </if>" +
            "  <if test='workOrderId != null'>" +
            "    AND work_order_id = #{workOrderId}" +
            "  </if>" +
            "  <if test='materialId != null'>" +
            "    AND material_id = #{materialId}" +
            "  </if>" +
            "</where>" +
            " ORDER BY sn_id DESC" +
            "</script>")
    IPage<AsmSn> selectPage(Page<AsmSn> page,
                            @Param("snCode") String snCode,
                            @Param("snType") String snType,
                            @Param("snLevel") Integer snLevel,
                            @Param("currentStatus") Integer currentStatus,
                            @Param("workOrderId") Long workOrderId,
                            @Param("materialId") Long materialId);

    /** SN 编码唯一校验 */
    @Select("SELECT COUNT(*) FROM asm_sn WHERE sn_code = #{snCode} " +
            "AND (#{snId} IS NULL OR sn_id != #{snId})")
    int countDuplicate(@Param("snCode") String snCode, @Param("snId") Long snId);
}
