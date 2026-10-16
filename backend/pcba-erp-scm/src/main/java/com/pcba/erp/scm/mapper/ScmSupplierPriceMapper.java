package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.scm.entity.ScmSupplierPrice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScmSupplierPriceMapper extends BaseMapper<ScmSupplierPrice> {

    /**
     * 分页查询 - 以物料为主视角
     * LEFT JOIN pdm_material / scm_supplier 回填名称
     * 排序: 物料ID 升序 → 默认 DESC → 单价 ASC → price_id DESC
     * (保证同物料的多个供应商报价相邻, 便于前端分组与比价)
     */
    @Select("<script>" +
            "SELECT p.*, " +
            "       m.material_code AS \"materialCode\", " +
            "       m.material_name AS \"materialName\", " +
            "       m.specification AS specification, " +
            "       s.supplier_code AS \"supplierCode\", " +
            "       s.supplier_name AS \"supplierName\" " +
            "FROM scm_supplier_price p " +
            "LEFT JOIN pdm_material m ON m.material_id = p.material_id " +
            "LEFT JOIN scm_supplier s ON s.supplier_id = p.supplier_id " +
            "<where>" +
            "  <if test='supplierId != null'>" +
            "    AND p.supplier_id = #{supplierId}" +
            "  </if>" +
            "  <if test='materialId != null'>" +
            "    AND p.material_id = #{materialId}" +
            "  </if>" +
            "  <if test=\"materialCode != null and materialCode != ''\">" +
            "    AND m.material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"materialName != null and materialName != ''\">" +
            "    AND m.material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"supplierName != null and supplierName != ''\">" +
            "    AND s.supplier_name LIKE CONCAT('%', CAST(#{supplierName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"currency != null and currency != ''\">" +
            "    AND p.currency LIKE CONCAT('%', CAST(#{currency} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND p.status = #{status}" +
            "  </if>" +
            "</where>" +
            " ORDER BY p.material_id ASC, p.is_default DESC, p.unit_price ASC, p.price_id DESC" +
            "</script>")
    IPage<ScmSupplierPrice> selectPage(Page<ScmSupplierPrice> page,
                                       @Param("supplierId") Long supplierId,
                                       @Param("materialId") Long materialId,
                                       @Param("materialCode") String materialCode,
                                       @Param("materialName") String materialName,
                                       @Param("supplierName") String supplierName,
                                       @Param("currency") String currency,
                                       @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM scm_supplier_price WHERE CAST(price_id AS TEXT) = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR price_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);

    // ============== 分组分页 (以物料为基础, 保证同物料完整出现在一页) ==============

    /** 统计满足筛选条件的 distinct material_id 数量 (= 物料组分页的 total) */
    @Select("<script>" +
            "SELECT COUNT(*) FROM ( " +
            "  SELECT DISTINCT p.material_id " +
            "  FROM scm_supplier_price p " +
            "  LEFT JOIN pdm_material m ON m.material_id = p.material_id " +
            "  LEFT JOIN scm_supplier s ON s.supplier_id = p.supplier_id " +
            "  <where>" +
            "    <if test='supplierId != null'> AND p.supplier_id = #{supplierId} </if>" +
            "    <if test='materialId != null'> AND p.material_id = #{materialId} </if>" +
            "    <if test=\"materialCode != null and materialCode != ''\"> AND m.material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%') </if>" +
            "    <if test=\"materialName != null and materialName != ''\"> AND m.material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%') </if>" +
            "    <if test=\"supplierName != null and supplierName != ''\"> AND s.supplier_name LIKE CONCAT('%', CAST(#{supplierName} AS TEXT), '%') </if>" +
            "    <if test=\"currency != null and currency != ''\"> AND p.currency LIKE CONCAT('%', CAST(#{currency} AS TEXT), '%') </if>" +
            "    <if test='status != null'> AND p.status = #{status} </if>" +
            "  </where>" +
            ") t" +
            "</script>")
    int countGroupedMaterials(@Param("supplierId") Long supplierId,
                              @Param("materialId") Long materialId,
                              @Param("materialCode") String materialCode,
                              @Param("materialName") String materialName,
                              @Param("supplierName") String supplierName,
                              @Param("currency") String currency,
                              @Param("status") Integer status);

    /** 取当前页的 distinct material_id 列表 (按 material_id 升序) */
    @Select("<script>" +
            "SELECT DISTINCT p.material_id " +
            "FROM scm_supplier_price p " +
            "LEFT JOIN pdm_material m ON m.material_id = p.material_id " +
            "LEFT JOIN scm_supplier s ON s.supplier_id = p.supplier_id " +
            "<where>" +
            "  <if test='supplierId != null'> AND p.supplier_id = #{supplierId} </if>" +
            "  <if test='materialId != null'> AND p.material_id = #{materialId} </if>" +
            "  <if test=\"materialCode != null and materialCode != ''\"> AND m.material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%') </if>" +
            "  <if test=\"materialName != null and materialName != ''\"> AND m.material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%') </if>" +
            "  <if test=\"supplierName != null and supplierName != ''\"> AND s.supplier_name LIKE CONCAT('%', CAST(#{supplierName} AS TEXT), '%') </if>" +
            "  <if test=\"currency != null and currency != ''\"> AND p.currency LIKE CONCAT('%', CAST(#{currency} AS TEXT), '%') </if>" +
            "  <if test='status != null'> AND p.status = #{status} </if>" +
            "</where>" +
            " ORDER BY p.material_id ASC" +
            " LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<Long> selectMaterialIdsPage(@Param("offset") int offset,
                                     @Param("size") int size,
                                     @Param("supplierId") Long supplierId,
                                     @Param("materialId") Long materialId,
                                     @Param("materialCode") String materialCode,
                                     @Param("materialName") String materialName,
                                     @Param("supplierName") String supplierName,
                                     @Param("currency") String currency,
                                     @Param("status") Integer status);

    /** 取指定 material_id 集合的全部报价 (联表回填, 按物料→默认→单价排序) */
    @Select("<script>" +
            "SELECT p.*, " +
            "       m.material_code AS \"materialCode\", " +
            "       m.material_name AS \"materialName\", " +
            "       m.specification AS specification, " +
            "       s.supplier_code AS \"supplierCode\", " +
            "       s.supplier_name AS \"supplierName\" " +
            "FROM scm_supplier_price p " +
            "LEFT JOIN pdm_material m ON m.material_id = p.material_id " +
            "LEFT JOIN scm_supplier s ON s.supplier_id = p.supplier_id " +
            "WHERE p.material_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'> #{id} </foreach>" +
            " ORDER BY p.material_id ASC, p.is_default DESC, p.unit_price ASC, p.price_id DESC" +
            "</script>")
    List<ScmSupplierPrice> selectByMaterialIds(@Param("ids") List<Long> ids);
}
