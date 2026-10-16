package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.FasOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FasOrderMapper extends BaseMapper<FasOrder> {

    @Select("<script>" +
            "SELECT * FROM fas_order " +
            "<where>" +
            "  <if test='orderNo != null and orderNo != \"\"'>" +
            "    AND order_no LIKE CONCAT('%', CAST(#{orderNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='productName != null and productName != \"\"'>" +
            "    AND product_name LIKE CONCAT('%', CAST(#{productName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  <if test='lineName != null and lineName != \"\"'>" +
            "    AND line_name LIKE CONCAT('%', CAST(#{lineName} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY status ASC, create_time DESC" +
            "</script>")
    IPage<FasOrder> selectPage(Page<FasOrder> page,
                               @Param("orderNo") String orderNo,
                               @Param("productName") String productName,
                               @Param("status") Integer status,
                               @Param("lineName") String lineName);

    @Select("SELECT COUNT(*) FROM fas_order WHERE order_no = #{orderNo} " +
            "AND (#{excludeId} IS NULL OR order_id != #{excludeId})")
    int countDuplicateNo(@Param("orderNo") String orderNo, @Param("excludeId") Long excludeId);
}
