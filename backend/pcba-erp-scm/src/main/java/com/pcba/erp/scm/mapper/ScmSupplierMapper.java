package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.scm.entity.ScmSupplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScmSupplierMapper extends BaseMapper<ScmSupplier> {

    @Select("<script>" +
            "SELECT * FROM scm_supplier " +
            "<where>" +
            "  <if test=\"supplierCode != null and supplierCode != ''\">" +
            "    AND supplier_code LIKE CONCAT('%', CAST(#{supplierCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"supplierName != null and supplierName != ''\">" +
            "    AND supplier_name LIKE CONCAT('%', CAST(#{supplierName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"contact != null and contact != ''\">" +
            "    AND contact LIKE CONCAT('%', CAST(#{contact} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"phone != null and phone != ''\">" +
            "    AND phone LIKE CONCAT('%', CAST(#{phone} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"level != null and level != ''\">" +
            "    AND level LIKE CONCAT('%', CAST(#{level} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "</where>" +
            " ORDER BY supplier_id DESC" +
            "</script>")
    IPage<ScmSupplier> selectPage(Page<ScmSupplier> page, @Param("supplierCode") String supplierCode, @Param("supplierName") String supplierName, @Param("contact") String contact, @Param("phone") String phone, @Param("level") String level, @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM scm_supplier WHERE supplier_code = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR supplier_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);
}
