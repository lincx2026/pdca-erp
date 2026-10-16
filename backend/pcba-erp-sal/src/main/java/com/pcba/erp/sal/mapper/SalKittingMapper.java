package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.sal.entity.SalKitting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 销售齐套 Mapper
 */
@Mapper
public interface SalKittingMapper extends BaseMapper<SalKitting> {

    @Select("<script>" +
            "SELECT * FROM sal_kitting " +
            "<where>" +
            "  <if test='kittingNo != null and kittingNo != \"\"'>" +
            "    AND kitting_no LIKE CONCAT('%', CAST(#{kittingNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='soNo != null and soNo != \"\"'>" +
            "    AND so_no LIKE CONCAT('%', CAST(#{soNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='customerId != null'>" +
            "    AND customer_id = #{customerId}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  <if test='bomId != null'>" +
            "    AND bom_id = #{bomId}" +
            "  </if>" +
            "</where>" +
            " ORDER BY kitting_id DESC" +
            "</script>")
    IPage<SalKitting> selectPage(Page<SalKitting> page,
                                  @Param("kittingNo") String kittingNo,
                                  @Param("soNo") String soNo,
                                  @Param("customerId") Long customerId,
                                  @Param("status") Integer status,
                                  @Param("bomId") Long bomId);

    @Select("SELECT COUNT(*) FROM sal_kitting WHERE kitting_no = #{kittingNo} " +
            "AND (#{kittingId} IS NULL OR kitting_id != #{kittingId})")
    int countDuplicate(@Param("kittingNo") String kittingNo, @Param("kittingId") Long kittingId);

    /** 跨仓库/批次汇总某物料的可用库存总量 */
    @Select("SELECT COALESCE(SUM(available_qty),0) FROM wms_inventory WHERE material_id = #{materialId} AND status = 1")
    BigDecimal sumAvailableByMaterial(@Param("materialId") Long materialId);
}
