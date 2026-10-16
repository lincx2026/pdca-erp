package com.pcba.erp.pdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pdm.entity.PdmBom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PdmBomMapper extends BaseMapper<PdmBom> {

    /** 带数据权限的分页查询 (SELECT 主档 + 成品编码/名称) */
    @Select("<script>" +
            "SELECT b.* FROM pdm_bom b " +
            "LEFT JOIN pdm_material m ON b.product_material_id = m.material_id " +
            "<where> " +
            "  <if test='bomCode!=null and bomCode!=\"\"'> AND b.bom_code LIKE CONCAT('%',CAST(#{bomCode} AS TEXT),'%') </if>" +
            "  <if test='bomName!=null and bomName!=\"\"'> AND b.bom_name LIKE CONCAT('%',CAST(#{bomName} AS TEXT),'%') </if>" +
            "  <if test='productMaterialCode!=null and productMaterialCode!=\"\"'> AND b.product_material_code LIKE CONCAT('%',CAST(#{productMaterialCode} AS TEXT),'%') </if>" +
            "  <if test='productMaterialId!=null'> AND b.product_material_id = #{productMaterialId} </if>" +
            "  <if test='categoryId!=null'> AND m.category_id = #{categoryId} </if>" +
            "  <if test='bomType!=null'> AND b.bom_type = #{bomType} </if>" +
            "  <if test='status!=null'> AND b.status = #{status} </if>" +
            "  <if test='version!=null and version!=\"\"'> AND b.version LIKE CONCAT('%',CAST(#{version} AS TEXT),'%') </if>" +
            "  ${dataScope} " +
            "</where> " +
            "ORDER BY b.bom_id DESC" +
            "</script>")
    List<PdmBom> selectBomPage(@Param("bomCode") String bomCode,
                                @Param("bomName") String bomName,
                                @Param("productMaterialCode") String productMaterialCode,
                                @Param("productMaterialId") Long productMaterialId,
                                @Param("categoryId") Long categoryId,
                                @Param("bomType") Integer bomType,
                                @Param("status") Integer status,
                                @Param("version") String version,
                                @Param("dataScope") String dataScope);

    /** 同成品版本唯一性校验 */
    @Select("SELECT COUNT(*) FROM pdm_bom WHERE product_material_id = #{productId} AND version = #{version} " +
            "AND (#{excludeId} IS NULL OR bom_id != #{excludeId})")
    int countDuplicateVersion(@Param("productId") Long productId,
                              @Param("version") String version,
                              @Param("excludeId") Long excludeId);
}
