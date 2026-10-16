package com.pcba.erp.pmm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.pmm.entity.PmmKanbanCard;

import java.util.List;

public interface IPmmKanbanCardService {
    IPage<PmmKanbanCard> selectPage(int pageNum, int pageSize, Long workOrderId, Integer lineNo,
                                    String workCenter, Integer isFlagged, String title);
    PmmKanbanCard getDetail(Long id);
    Long save(PmmKanbanCard entity);
    boolean update(PmmKanbanCard entity);
    boolean remove(Long id);
    boolean moveLine(Long id, Integer lineNo, Integer columnNo);
    List<PmmKanbanCard> allCards();
}
