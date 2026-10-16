package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.scm.entity.ScmPurchaseReturn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScmPurchaseReturnMapper extends BaseMapper<ScmPurchaseReturn> {

    @Select("<script>" +
            "SELECT * FROM scm_purchase_return " +
            "<where>" +
            "  <if test=\"returnNo != null and returnNo != ''\">" +
            "    AND return_no LIKE CONCAT('%', CAST(#{returnNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='poId != null'>" +
            "    AND po_id = #{poId}" +
            "  </if>" +
            "  <if test='supplierId != null'>" +
            "    AND supplier_id = #{supplierId}" +
            "  </if>" +
            "  <if test=\"supplierName != null and supplierName != ''\">" +
            "    AND supplier_name LIKE CONCAT('%', CAST(#{supplierName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='warehouseId != null'>" +
            "    AND warehouse_id = #{warehouseId}" +
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
    IPage<ScmPurchaseReturn> selectPage(Page<ScmPurchaseReturn> page, @Param("returnNo") String returnNo, @Param("poId") Long poId, @Param("supplierId") Long supplierId, @Param("supplierName") String supplierName, @Param("warehouseId") Long warehouseId, @Param("status") Integer status, @Param("returnDate") String returnDate);

    @Select("SELECT COUNT(*) FROM scm_purchase_return WHERE return_no = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR return_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);

    /** 采购退货行关联的到货明细定位到 PO 行（scm_delivery_note_line 存 po_line_id 时可直接用） */
    @Select("SELECT po_line_id FROM scm_delivery_note_line WHERE line_id = #{deliveryLineId} LIMIT 1")
    Long resolvePoLineIdByDeliveryLine(@Param("deliveryLineId") Long deliveryLineId);
}
