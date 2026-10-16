package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.PmmKanbanCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmmKanbanCardMapper extends BaseMapper<PmmKanbanCard> {

    @Select("<script>" +
            "SELECT * FROM pmm_kanban_card " +
            "<where>" +
            "  <if test='workOrderId != null'> AND work_order_id = #{workOrderId} </if>" +
            "  <if test='lineNo != null'> AND line_no = #{lineNo} </if>" +
            "  <if test='workCenter != null and workCenter != \"\"'>" +
            "    AND work_center LIKE CONCAT('%', CAST(#{workCenter} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='isFlagged != null'> AND is_flagged = #{isFlagged} </if>" +
            "  <if test='title != null and title != \"\"'>" +
            "    AND title LIKE CONCAT('%', CAST(#{title} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY line_no ASC, column_no ASC, is_flagged DESC, card_id DESC" +
            "</script>")
    IPage<PmmKanbanCard> selectPage(Page<PmmKanbanCard> page,
                                    @Param("workOrderId") Long workOrderId,
                                    @Param("lineNo") Integer lineNo,
                                    @Param("workCenter") String workCenter,
                                    @Param("isFlagged") Integer isFlagged,
                                    @Param("title") String title);
}
