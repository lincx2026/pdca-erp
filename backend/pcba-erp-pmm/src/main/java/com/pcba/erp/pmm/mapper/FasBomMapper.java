package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.FasBom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FasBomMapper extends BaseMapper<FasBom> {

    @Select("<script>" +
            "SELECT * FROM fas_bom " +
            "<where>" +
            "  <if test='productId != null'>" +
            "    AND product_id = #{productId}" +
            "  </if>" +
            "  <if test='componentName != null and componentName != \"\"'>" +
            "    AND component_name LIKE CONCAT('%', CAST(#{componentName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='componentType != null and componentType != \"\"'>" +
            "    AND component_type = #{componentType}" +
            "  </if>" +
            "</where>" +
            " ORDER BY feed_sequence ASC, bom_id DESC" +
            "</script>")
    IPage<FasBom> selectPage(Page<FasBom> page,
                             @Param("productId") Long productId,
                             @Param("componentName") String componentName,
                             @Param("componentType") String componentType);

    @Select("SELECT * FROM fas_bom WHERE product_id = #{productId} ORDER BY feed_sequence ASC, bom_id ASC")
    List<FasBom> listByProduct(@Param("productId") Long productId);
}
