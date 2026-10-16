package com.pcba.erp.pdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pdm.entity.PdmMaterialAlternate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PdmMaterialAlternateMapper extends BaseMapper<PdmMaterialAlternate> {

    /**
     * 按主料查询替代关系（JOIN 带出 主料/替代料 编码+名称）
     * 支持双向：mainId=查主料替代, altId=查哪些物料可以用 X 当替代料
     */
    @Select("<script>" +
            "SELECT a.*, " +
            " mm.material_code AS main_material_code, mm.material_name AS main_material_name, " +
            " ma.material_code AS alt_material_code,  ma.material_name AS alt_material_name " +
            "FROM pdm_material_alternate a " +
            "LEFT JOIN pdm_material mm ON mm.material_id = a.main_material_id " +
            "LEFT JOIN pdm_material ma ON ma.material_id = a.alt_material_id " +
            "<where>" +
            "<if test='mainMaterialId != null'> AND a.main_material_id = #{mainMaterialId} </if>" +
            "<if test='altMaterialId != null'>  AND a.alt_material_id  = #{altMaterialId}  </if>" +
            "<if test='groupId != null and groupId != \"\"'> AND a.group_id = #{groupId} </if>" +
            "<if test='status != null'> AND a.status = #{status} </if>" +
            "<if test='dataScope != null and dataScope != \"\"'> AND ${dataScope} </if>" +
            "</where> ORDER BY a.group_id ASC, a.priority ASC, a.id ASC" +
            "</script>")
    List<PdmMaterialAlternate> listAlternates(@Param("mainMaterialId") Long mainMaterialId,
                                              @Param("altMaterialId") Long altMaterialId,
                                              @Param("groupId") String groupId,
                                              @Param("status") Integer status,
                                              @Param("dataScope") String dataScope);

    /** groupId+main+alt 三键唯一冲突计数 */
    @Select("SELECT COUNT(*) FROM pdm_material_alternate " +
            "WHERE group_id=#{groupId} AND main_material_id=#{main} AND alt_material_id=#{alt} " +
            "AND (#{id} IS NULL OR id != #{id})")
    int countDuplicateGroup(@Param("groupId") String groupId,
                            @Param("main") Long main,
                            @Param("alt") Long alt,
                            @Param("id") Long id);

    /** 同组主料是否已经有相同替代料 */
    @Select("SELECT COUNT(*) FROM pdm_material_alternate " +
            "WHERE main_material_id=#{main} AND alt_material_id=#{alt} " +
            "AND (#{id} IS NULL OR id != #{id})")
    int countSameAlt(@Param("main") Long main, @Param("alt") Long alt, @Param("id") Long id);
}
