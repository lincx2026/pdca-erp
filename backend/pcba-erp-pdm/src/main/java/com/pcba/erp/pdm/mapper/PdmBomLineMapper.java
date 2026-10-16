package com.pcba.erp.pdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pdm.entity.PdmBomLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PdmBomLineMapper extends BaseMapper<PdmBomLine> {

    /** 查询某BOM下全部行（单层） */
    @Select("SELECT bl.*, m1.material_code AS parent_material_code, m1.material_name AS parent_material_name " +
            "FROM pdm_bom_line bl " +
            "LEFT JOIN pdm_material m1 ON bl.parent_material_id = m1.material_id " +
            "WHERE bl.bom_id = #{bomId} " +
            "ORDER BY bl.line_no ASC, bl.line_id ASC")
    List<PdmBomLine> listByBomId(@Param("bomId") Long bomId);

    /**
     * 多层展开: 以 parentId 为根，递归查找 (单层版，外部做循环递归或使用CTE)
     * 这里实现"一次取直接子项"，Service 层根据 needPhantomExpand 决定是否展开虚拟件子层
     */
    @Select("SELECT bl.*, m1.material_code AS parent_material_code, m1.material_name AS parent_material_name " +
            "FROM pdm_bom_line bl " +
            "LEFT JOIN pdm_material m1 ON bl.parent_material_id = m1.material_id " +
            "WHERE bl.bom_id = #{bomId} AND bl.parent_material_id = #{parentId} " +
            "  AND bl.status = 1 " +
            "ORDER BY bl.line_no ASC")
    List<PdmBomLine> listDirectChildren(@Param("bomId") Long bomId,
                                         @Param("parentId") Long parentId);

    /**
     * Where-Used 反查: 子件X出现在哪些BOM的父项中（单层反查，外部递归取上层）
     */
    @Select("SELECT bl.*, m1.material_code AS parent_material_code, m1.material_name AS parent_material_name, " +
            "       b.bom_code, b.bom_name, b.version " +
            "FROM pdm_bom_line bl " +
            "JOIN pdm_bom b ON bl.bom_id = b.bom_id " +
            "LEFT JOIN pdm_material m1 ON bl.parent_material_id = m1.material_id " +
            "WHERE bl.component_material_id = #{componentId} " +
            "  AND (#{status} IS NULL OR bl.status = #{status}) " +
            "  AND (#{bomStatus} IS NULL OR b.status = #{bomStatus}) " +
            "ORDER BY b.bom_id DESC, bl.line_no ASC")
    List<PdmBomLine> whereUsed(@Param("componentId") Long componentId,
                               @Param("status") Integer status,
                               @Param("bomStatus") Integer bomStatus);

    /** 某BOM下重复子件(同父项)计数 */
    @Select("SELECT COUNT(*) FROM pdm_bom_line WHERE bom_id = #{bomId} " +
            "AND parent_material_id = #{parentId} AND component_material_id = #{componentId} " +
            "AND (#{excludeLineId} IS NULL OR line_id != #{excludeLineId})")
    int countDuplicateLine(@Param("bomId") Long bomId,
                           @Param("parentId") Long parentId,
                           @Param("componentId") Long componentId,
                           @Param("excludeLineId") Long excludeLineId);
}
