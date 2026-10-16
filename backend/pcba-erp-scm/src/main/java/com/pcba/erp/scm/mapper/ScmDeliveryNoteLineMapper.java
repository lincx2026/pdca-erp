package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.scm.entity.ScmDeliveryNoteLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScmDeliveryNoteLineMapper extends BaseMapper<ScmDeliveryNoteLine> {

    @Select("<script>" +
            "SELECT * FROM scm_delivery_note_line " +
            "<where>" +
            "  <if test='deliveryId != null'>" +
            "    AND delivery_id = #{deliveryId}" +
            "  </if>" +
            "  <if test=\"materialCode != null and materialCode != ''\">" +
            "    AND material_code LIKE CONCAT('%', CAST(#{materialCode} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"materialName != null and materialName != ''\">" +
            "    AND material_name LIKE CONCAT('%', CAST(#{materialName} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY line_no ASC" +
            "</script>")
    IPage<ScmDeliveryNoteLine> selectPage(Page<ScmDeliveryNoteLine> page, @Param("deliveryId") Long deliveryId, @Param("materialCode") String materialCode, @Param("materialName") String materialName);

    @Select("SELECT COUNT(*) FROM scm_delivery_note_line WHERE CAST(line_id AS TEXT) = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR line_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);
}
