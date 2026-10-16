package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.sal.entity.SalSalesReturn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalSalesReturnMapper extends BaseMapper<SalSalesReturn> {

    @Select("<script>" +
            "SELECT * FROM sal_sales_return " +
            "<where>" +
            "  <if test=\"returnNo != null and returnNo != ''\">" +
            "    AND return_no LIKE CONCAT('%', CAST(#{returnNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='customerId != null'>" +
            "    AND customer_id = #{customerId}" +
            "  </if>" +
            "  <if test=\"customerName != null and customerName != ''\">" +
            "    AND customer_name LIKE CONCAT('%', CAST(#{customerName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='soId != null'>" +
            "    AND so_id = #{soId}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  <if test=\"returnDate != null and returnDate != ''\">" +
            "    AND CAST(return_date AS TEXT) LIKE CONCAT('%', CAST(#{returnDate} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY return_id DESC" +
            "</script>")
    IPage<SalSalesReturn> selectPage(Page<SalSalesReturn> page, @Param("returnNo") String returnNo, @Param("customerId") Long customerId, @Param("customerName") String customerName, @Param("soId") Long soId, @Param("status") Integer status, @Param("returnDate") String returnDate);

    @Select("SELECT COUNT(*) FROM sal_sales_return WHERE return_no = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR return_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);
}
