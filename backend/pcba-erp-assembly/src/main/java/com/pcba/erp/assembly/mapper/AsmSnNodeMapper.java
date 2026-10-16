package com.pcba.erp.assembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.assembly.entity.AsmSnNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * SN 工序节点 Mapper
 */
@Mapper
public interface AsmSnNodeMapper extends BaseMapper<AsmSnNode> {

    @Select("<script>" +
            "SELECT * FROM asm_sn_node " +
            "<where>" +
            "  <if test='snId != null'>" +
            "    AND sn_id = #{snId}" +
            "  </if>" +
            "  <if test='nodeType != null and nodeType != \"\"'>" +
            "    AND node_type = #{nodeType}" +
            "  </if>" +
            "  <if test='equipmentId != null'>" +
            "    AND equipment_id = #{equipmentId}" +
            "  </if>" +
            "  <if test='nodeTimeFrom != null'>" +
            "    AND node_time <![CDATA[ >= ]]> #{nodeTimeFrom}" +
            "  </if>" +
            "  <if test='nodeTimeTo != null'>" +
            "    AND node_time <![CDATA[ <= ]]> #{nodeTimeTo}" +
            "  </if>" +
            "</where>" +
            " ORDER BY node_id DESC" +
            "</script>")
    IPage<AsmSnNode> selectPage(Page<AsmSnNode> page,
                                @Param("snId") Long snId,
                                @Param("nodeType") String nodeType,
                                @Param("equipmentId") Long equipmentId,
                                @Param("nodeTimeFrom") LocalDateTime nodeTimeFrom,
                                @Param("nodeTimeTo") LocalDateTime nodeTimeTo);
}
