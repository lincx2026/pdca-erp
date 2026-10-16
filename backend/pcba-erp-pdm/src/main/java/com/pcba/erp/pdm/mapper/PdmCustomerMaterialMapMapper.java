package com.pcba.erp.pdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pdm.entity.PdmCustomerMaterialMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客户料号映射 Mapper
 */
@Mapper
public interface PdmCustomerMaterialMapMapper extends BaseMapper<PdmCustomerMaterialMap> {

    /**
     * 分页查询 + 关联物料主档和客户信息
     */
    @Select("<script>" +
            "SELECT m.*, " +
            "  p.material_code, p.material_name, p.specification, " +
            "  c.customer_code " +
            "FROM pdm_customer_material_map m " +
            "LEFT JOIN pdm_material p ON m.material_id = p.material_id " +
            "LEFT JOIN scm_customer c ON m.customer_id = c.customer_id " +
            "<where>" +
            "  <if test='customerId != null'>" +
            "    AND m.customer_id = #{customerId}" +
            "  </if>" +
            "  <if test='customerName != null and customerName != \"\"'>" +
            "    AND m.customer_name LIKE CONCAT('%', CAST(#{customerName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='materialId != null'>" +
            "    AND m.material_id = #{materialId}" +
            "  </if>" +
            "  <if test='customerMaterialNo != null and customerMaterialNo != \"\"'>" +
            "    AND m.customer_material_no LIKE CONCAT('%', CAST(#{customerMaterialNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='materialCode != null and materialCode != \"\"'>" +
            "    AND p.material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND m.status = #{status}" +
            "  </if>" +
            "</where>" +
            " ORDER BY m.map_id DESC" +
            "</script>")
    IPage<PdmCustomerMaterialMap> selectMapPage(Page<PdmCustomerMaterialMap> page,
                                                 @Param("customerId") Long customerId,
                                                 @Param("customerName") String customerName,
                                                 @Param("materialId") Long materialId,
                                                 @Param("customerMaterialNo") String customerMaterialNo,
                                                 @Param("materialCode") String materialCode,
                                                 @Param("status") Integer status);

    /**
     * 唯一性校验：同一客户下料号不重复
     */
    @Select("SELECT COUNT(*) FROM pdm_customer_material_map " +
            "WHERE customer_id = #{customerId} AND customer_material_no = #{customerMaterialNo} " +
            "AND (#{mapId} IS NULL OR map_id != #{mapId})")
    int checkUnique(@Param("customerId") Long customerId,
                    @Param("customerMaterialNo") String customerMaterialNo,
                    @Param("mapId") Long mapId);

    /**
     * 按客户料号反查工厂物料
     */
    @Select("SELECT m.*, p.material_code, p.material_name, p.specification, c.customer_code " +
            "FROM pdm_customer_material_map m " +
            "LEFT JOIN pdm_material p ON m.material_id = p.material_id " +
            "LEFT JOIN scm_customer c ON m.customer_id = c.customer_id " +
            "WHERE m.customer_material_no = #{customerMaterialNo} AND m.status = 1 " +
            "LIMIT 1")
    PdmCustomerMaterialMap selectByCustomerMaterialNo(@Param("customerMaterialNo") String customerMaterialNo);
}
