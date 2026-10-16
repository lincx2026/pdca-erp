package com.pcba.erp.pdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pdm.entity.PdmMaterialCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 物料分类 Mapper
 * - 数据范围 @DataScope 在 Service 层方法上触发 (AOP 只对 Spring Bean 生效), 这里接收拼接好的 dataScope SQL
 */
@Mapper
public interface PdmMaterialCategoryMapper extends BaseMapper<PdmMaterialCategory> {

    /**
     * 查询分类列表 (不做树化, 交给 Service buildTree)
     * ${dataScope} 占位: Service 层从 DataScopeThreadLocal 取出数据范围SQL传入
     */
    @Select("<script>" +
            "SELECT * FROM pdm_material_category " +
            "<where>" +
            "  <if test='categoryName != null and categoryName != \"\"'>" +
            "    AND category_name LIKE CONCAT('%', CAST(#{categoryName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  ${dataScope}" +
            "</where>" +
            " ORDER BY parent_id, order_num, category_id" +
            "</script>")
    List<PdmMaterialCategory> selectCategoryList(
            @Param("categoryName") String categoryName,
            @Param("status") Integer status,
            @Param("dataScope") String dataScope);

    /** 校验同级分类名称是否唯一 */
    @Select("SELECT COUNT(*) FROM pdm_material_category " +
            "WHERE parent_id = #{parentId} AND category_name = #{categoryName} " +
            "AND (#{categoryId} IS NULL OR category_id != #{categoryId})")
    int countDuplicateName(@Param("parentId") Long parentId,
                           @Param("categoryName") String categoryName,
                           @Param("categoryId") Long categoryId);

    /** 校验分类编码前缀是否唯一 (全局唯一) */
    @Select("SELECT COUNT(*) FROM pdm_material_category " +
            "WHERE category_code = #{categoryCode} " +
            "AND (#{categoryId} IS NULL OR category_id != #{categoryId})")
    int countDuplicateCode(@Param("categoryCode") String categoryCode,
                           @Param("categoryId") Long categoryId);

    /** 统计某分类下物料数量 (给分类树右侧计数列) */
    @Select("SELECT COUNT(*) FROM pdm_material WHERE category_id = #{categoryId}")
    long countMaterialByCategoryId(@Param("categoryId") Long categoryId);

    /** 查某分类下是否有子分类 */
    @Select("SELECT COUNT(*) FROM pdm_material_category WHERE parent_id = #{categoryId}")
    int countChildren(@Param("categoryId") Long categoryId);
}
