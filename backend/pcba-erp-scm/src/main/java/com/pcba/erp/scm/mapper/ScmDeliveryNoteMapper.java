package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.scm.entity.ScmDeliveryNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScmDeliveryNoteMapper extends BaseMapper<ScmDeliveryNote> {

    @Select("<script>" +
            "SELECT * FROM scm_delivery_note " +
            "<where>" +
            "  <if test=\"deliveryNo != null and deliveryNo != ''\">" +
            "    AND delivery_no LIKE CONCAT('%', CAST(#{deliveryNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"poNo != null and poNo != ''\">" +
            "    AND po_no LIKE CONCAT('%', CAST(#{poNo} AS TEXT), '%')" +
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
            "  <if test=\"deliveryDate != null and deliveryDate != ''\">" +
            "    AND CAST(delivery_date AS TEXT) LIKE CONCAT('%', CAST(#{deliveryDate} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY delivery_id DESC" +
            "</script>")
    IPage<ScmDeliveryNote> selectPage(Page<ScmDeliveryNote> page, @Param("deliveryNo") String deliveryNo, @Param("poNo") String poNo, @Param("supplierId") Long supplierId, @Param("supplierName") String supplierName, @Param("warehouseId") Long warehouseId, @Param("status") Integer status, @Param("deliveryDate") String deliveryDate);

    @Select("SELECT COUNT(*) FROM scm_delivery_note WHERE delivery_no = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR delivery_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);
}
