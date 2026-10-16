package com.pcba.erp.sal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.sal.entity.SalCustomer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalCustomerMapper extends BaseMapper<SalCustomer> {

    @Select("<script>" +
            "SELECT c.*, " +
            " (COALESCE(c.credit_limit,0) - COALESCE(u.unreceived,0)) AS credit_balance " +
            "FROM scm_customer c " +
            "LEFT JOIN ( " +
            "  SELECT customer_id, SUM(GREATEST(COALESCE(cny_total_amount,0)-COALESCE(received_amount,0),0)) AS unreceived " +
            "  FROM sal_ar_receivable WHERE status &lt;&gt; 9 GROUP BY customer_id " +
            ") u ON u.customer_id = c.customer_id " +
            "<where>" +
            "  <if test=\"customerCode != null and customerCode != ''\">" +
            "    AND c.customer_code LIKE CONCAT('%', CAST(#{customerCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"customerName != null and customerName != ''\">" +
            "    AND c.customer_name LIKE CONCAT('%', CAST(#{customerName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"contact != null and contact != ''\">" +
            "    AND c.contact LIKE CONCAT('%', CAST(#{contact} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"phone != null and phone != ''\">" +
            "    AND c.phone LIKE CONCAT('%', CAST(#{phone} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"industry != null and industry != ''\">" +
            "    AND c.industry LIKE CONCAT('%', CAST(#{industry} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"level != null and level != ''\">" +
            "    AND c.level LIKE CONCAT('%', CAST(#{level} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND c.status = #{status}" +
            "  </if>" +
            "</where>" +
            " ORDER BY c.customer_id DESC" +
            "</script>")
    IPage<SalCustomer> selectPage(Page<SalCustomer> page, @Param("customerCode") String customerCode, @Param("customerName") String customerName, @Param("contact") String contact, @Param("phone") String phone, @Param("industry") String industry, @Param("level") String level, @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM scm_customer WHERE customer_code = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR customer_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);
}
