package com.pcba.erp.assembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmKitting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 配料单 Mapper
 */
@Mapper
public interface AsmKittingMapper extends BaseMapper<AsmKitting> {

    @Select("<script>" +
            "SELECT * FROM asm_kitting " +
            "<where>" +
            "  <if test='kittingNo != null and kittingNo != \"\"'>" +
            "    AND kitting_no LIKE CONCAT('%', CAST(#{kittingNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='workOrderId != null'>" +
            "    AND work_order_id = #{workOrderId}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "</where>" +
            " ORDER BY kitting_id DESC" +
            "</script>")
    IPage<AsmKitting> selectPage(Page<AsmKitting> page,
                                  @Param("kittingNo") String kittingNo,
                                  @Param("workOrderId") Long workOrderId,
                                  @Param("status") Integer status);

    /** 配料单号唯一校验 */
    @Select("SELECT COUNT(*) FROM asm_kitting WHERE kitting_no = #{kittingNo} " +
            "AND (#{kittingId} IS NULL OR kitting_id != #{kittingId})")
    int countDuplicate(@Param("kittingNo") String kittingNo, @Param("kittingId") Long kittingId);
}
