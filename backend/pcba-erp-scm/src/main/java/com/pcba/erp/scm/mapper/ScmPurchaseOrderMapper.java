package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.scm.entity.ScmPurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScmPurchaseOrderMapper extends BaseMapper<ScmPurchaseOrder> {

    @Select("<script>" +
            "SELECT * FROM scm_purchase_order " +
            "<where>" +
            "  <if test=\"poNo != null and poNo != ''\">" +
            "    AND po_no LIKE CONCAT('%', CAST(#{poNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"poType != null and poType != ''\">" +
            "    AND po_type LIKE CONCAT('%', CAST(#{poType} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"bizCategory != null and bizCategory != ''\">" +
            "    AND biz_category = #{bizCategory}" +
            "  </if>" +
            "  <if test='supplierId != null'>" +
            "    AND supplier_id = #{supplierId}" +
            "  </if>" +
            "  <if test=\"supplierName != null and supplierName != ''\">" +
            "    AND supplier_name LIKE CONCAT('%', CAST(#{supplierName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='buyerId != null'>" +
            "    AND buyer_id = #{buyerId}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  <if test=\"deliveryDate != null and deliveryDate != ''\">" +
            "    AND CAST(delivery_date AS TEXT) LIKE CONCAT('%', CAST(#{deliveryDate} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY po_id DESC" +
            "</script>")
    IPage<ScmPurchaseOrder> selectPage(Page<ScmPurchaseOrder> page, @Param("poNo") String poNo, @Param("poType") String poType, @Param("bizCategory") String bizCategory, @Param("supplierId") Long supplierId, @Param("supplierName") String supplierName, @Param("buyerId") Long buyerId, @Param("status") Integer status, @Param("deliveryDate") String deliveryDate);

    @Select("SELECT COUNT(*) FROM scm_purchase_order WHERE po_no = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR po_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);
}
