package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmKanbanCard;
import com.pcba.erp.pmm.mapper.PmmKanbanCardMapper;
import com.pcba.erp.pmm.service.IPmmKanbanCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PmmKanbanCardServiceImpl implements IPmmKanbanCardService {

    private final PmmKanbanCardMapper mapper;

    @Override
    public IPage<PmmKanbanCard> selectPage(int pageNum, int pageSize, Long workOrderId, Integer lineNo,
                                           String workCenter, Integer isFlagged, String title) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), workOrderId, lineNo, workCenter, isFlagged, title);
    }

    @Override
    public PmmKanbanCard getDetail(Long id) {
        if (id == null) throw new ServiceException("看板卡片ID不能为空");
        PmmKanbanCard w = mapper.selectById(id);
        if (w == null) throw new ServiceException("看板卡片不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmKanbanCard entity) {
        if (entity.getWorkOrderId() == null) throw new ServiceException("工单ID不能为空");
        if (entity.getLineNo() == null) entity.setLineNo(1);
        if (entity.getColumnNo() == null) entity.setColumnNo(1);
        if (entity.getPlanQty() == null) entity.setPlanQty(BigDecimal.ZERO);
        if (entity.getPassQty() == null) entity.setPassQty(BigDecimal.ZERO);
        if (entity.getFailQty() == null) entity.setFailQty(BigDecimal.ZERO);
        if (entity.getProgressPct() == null) entity.setProgressPct(BigDecimal.ZERO);
        if (entity.getIsFlagged() == null) entity.setIsFlagged(0);
        mapper.insert(entity);
        return entity.getCardId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmKanbanCard entity) {
        if (entity.getCardId() == null) throw new ServiceException("看板卡片ID不能为空");
        PmmKanbanCard old = mapper.selectById(entity.getCardId());
        if (old == null) throw new ServiceException("看板卡片不存在");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        if (id == null) throw new ServiceException("看板卡片ID不能为空");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean moveLine(Long id, Integer lineNo, Integer columnNo) {
        PmmKanbanCard w = getDetail(id);
        if (lineNo != null) w.setLineNo(lineNo);
        if (columnNo != null) w.setColumnNo(columnNo);
        return mapper.updateById(w) > 0;
    }

    @Override
    public List<PmmKanbanCard> allCards() {
        return mapper.selectList(new LambdaQueryWrapper<PmmKanbanCard>()
                .orderByAsc(PmmKanbanCard::getLineNo, PmmKanbanCard::getColumnNo)
                .orderByDesc(PmmKanbanCard::getIsFlagged, PmmKanbanCard::getCardId));
    }
}
