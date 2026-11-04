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

    @Select("<script>SELECT m.* FROM pdm_material m WHERE 1=1 " +
            "<if test='keyword != null and keyword != &quot;&quot;'> AND (" +
            "LOWER(m.material_code) LIKE CONCAT('%', LOWER(#{keyword}), '%') OR " +
            "LOWER(m.material_name) LIKE CONCAT('%', LOWER(#{keyword}), '%') OR " +
            "LOWER(m.specification) LIKE CONCAT('%', LOWER(#{keyword}), '%') OR " +
            "LOWER(m.search_keywords) LIKE CONCAT('%', LOWER(#{keyword}), '%')) </if> " +
            " ${dataScope} ORDER BY m.material_id DESC</script>")
    IPage<PdmMaterial> searchMaterials(Page<PdmMaterial> page, @Param("keyword") String keyword,
                                       @Param("dataScope") String dataScope);

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
    @Select("<script>SELECT COUNT(*) FROM pdm_material WHERE material_code = #{code} " +
            "<if test='materialId != null'>AND material_id != #{materialId}</if></script>")
    int countDuplicateCode(@Param("code") String code, @Param("materialId") Long materialId);
}
