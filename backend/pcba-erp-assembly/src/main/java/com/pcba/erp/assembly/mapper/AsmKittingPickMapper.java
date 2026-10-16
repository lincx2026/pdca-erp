package com.pcba.erp.assembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmKittingPick;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 拣货记录 Mapper
 */
@Mapper
public interface AsmKittingPickMapper extends BaseMapper<AsmKittingPick> {

    @Select("<script>" +
            "SELECT * FROM asm_kitting_pick " +
            "<where>" +
            "  <if test='kittingId != null'>" +
            "    AND kitting_id = #{kittingId}" +
            "  </if>" +
            "  <if test='materialId != null'>" +
            "    AND material_id = #{materialId}" +
            "  </if>" +
            "  <if test='pickStatus != null'>" +
            "    AND pick_status = #{pickStatus}" +
            "  </if>" +
            "</where>" +
            " ORDER BY pick_id DESC" +
            "</script>")
    IPage<AsmKittingPick> selectPage(Page<AsmKittingPick> page,
                                     @Param("kittingId") Long kittingId,
                                     @Param("materialId") Long materialId,
                                     @Param("pickStatus") Integer pickStatus);

    /** 拣货单号唯一校验 */
    @Select("SELECT COUNT(*) FROM asm_kitting_pick WHERE pick_no = #{pickNo} " +
            "AND (#{pickId} IS NULL OR pick_id != #{pickId})")
    int countDuplicate(@Param("pickNo") String pickNo, @Param("pickId") Long pickId);
}
