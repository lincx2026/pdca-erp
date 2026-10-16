package com.pcba.erp.pdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pdm.entity.PdmMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 物料主档 Mapper
 * - 数据范围 @DataScope 在 Service 层方法上触发, 这里接收 dataScope 参数
 */
@Mapper
public interface PdmMaterialMapper extends BaseMapper<PdmMaterial> {

    /**
     * 分页查询 + 关联分类名称 + 数据范围
     */
    @Select("<script>" +
            "SELECT m.*, c.category_name, c.category_code " +
            "FROM pdm_material m " +
            "LEFT JOIN pdm_material_category c ON m.category_id = c.category_id " +
            "<where>" +
            "  <if test='categoryId != null'>" +
            "    AND (m.category_id = #{categoryId} OR c.ancestors LIKE CONCAT('%', CAST(#{categoryId} AS TEXT), '%'))" +
            "  </if>" +
            "  <if test='materialCode != null and materialCode != \"\"'>" +
            "    AND m.material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='materialName != null and materialName != \"\"'>" +
            "    AND m.material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='materialType != null and materialType != \"\"'>" +
            "    AND m.material_type = #{materialType}" +
            "  </if>" +
            "  <if test='mpn != null and mpn != \"\"'>" +
            "    AND m.mpn LIKE CONCAT('%', CAST(#{mpn} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND m.status = #{status}" +
            "  </if>" +
            "  ${dataScope}" +
            "</where>" +
            " ORDER BY m.material_id DESC" +
            "</script>")
    IPage<PdmMaterial> selectMaterialPage(Page<PdmMaterial> page,
                                          @Param("categoryId") Long categoryId,
                                          @Param("materialCode") String materialCode,
                                          @Param("materialName") String materialName,
                                          @Param("materialType") String materialType,
                                          @Param("mpn") String mpn,
                                          @Param("status") Integer status,
                                          @Param("dataScope") String dataScope);

    /** 物料编码全局唯一校验 */
    @Select("SELECT COUNT(*) FROM pdm_material WHERE material_code = #{code} " +
            "AND (#{materialId} IS NULL OR material_id != #{materialId})")
    int countDuplicateCode(@Param("code") String code, @Param("materialId") Long materialId);
}
