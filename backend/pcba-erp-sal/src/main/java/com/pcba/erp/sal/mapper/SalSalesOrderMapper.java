package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.sal.entity.SalSalesOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalSalesOrderMapper extends BaseMapper<SalSalesOrder> {

    @Select("<script>" +
            "SELECT * FROM sal_sales_order " +
            "<where>" +
            "  <if test=\"soNo != null and soNo != ''\">" +
            "    AND so_no LIKE CONCAT('%', CAST(#{soNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"soType != null and soType != ''\">" +
            "    AND so_type LIKE CONCAT('%', CAST(#{soType} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='customerId != null'>" +
            "    AND customer_id = #{customerId}" +
            "  </if>" +
            "  <if test=\"customerName != null and customerName != ''\">" +
            "    AND customer_name LIKE CONCAT('%', CAST(#{customerName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='salesRepId != null'>" +
            "    AND sales_rep_id = #{salesRepId}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  <if test=\"deliveryDate != null and deliveryDate != ''\">" +
            "    AND CAST(delivery_date AS TEXT) LIKE CONCAT('%', CAST(#{deliveryDate} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY so_id DESC" +
            "</script>")
    IPage<SalSalesOrder> selectPage(Page<SalSalesOrder> page, @Param("soNo") String soNo, @Param("soType") String soType, @Param("customerId") Long customerId, @Param("customerName") String customerName, @Param("salesRepId") Long salesRepId, @Param("status") Integer status, @Param("deliveryDate") String deliveryDate);

    @Select("SELECT COUNT(*) FROM sal_sales_order WHERE so_no = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR so_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);
}
