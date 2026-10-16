package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.event.NoticeEvent;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.PmmWorkOrder;
import com.pcba.erp.pmm.mapper.PmmWorkOrderMapper;
import com.pcba.erp.pmm.service.IPmmWorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmmWorkOrderServiceImpl implements IPmmWorkOrderService {

    private final PmmWorkOrderMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public IPage<PmmWorkOrder> selectPage(int pageNum, int pageSize, String orderNo, String materialCode, String materialName,
                                           String sourceType, Integer status, String workCenter, String saleOrderNo) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), orderNo, materialCode, materialName, sourceType, status, workCenter, saleOrderNo);
    }

    @Override
    public PmmWorkOrder getDetail(Long id) {
        if (id == null) throw new ServiceException("工单ID不能为空");
        PmmWorkOrder w = mapper.selectById(id);
        if (w == null) throw new ServiceException("工单不存在");
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PmmWorkOrder entity) {
        if (entity.getOrderNo() != null) {
            if (mapper.countDuplicate(entity.getOrderNo(), null) > 0)
                throw new ServiceException("工单号【" + entity.getOrderNo() + "】已存在");
        }
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getFinishedQty() == null) entity.setFinishedQty(BigDecimal.ZERO);
        if (entity.getScrappedQty() == null) entity.setScrappedQty(BigDecimal.ZERO);
        if (entity.getPriority() == null) entity.setPriority(50);
        mapper.insert(entity);
        // 自动通知：工单已创建
        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(1)
                .title("生产工单 " + entity.getOrderNo() + " 已创建")
                .summary("工单号: " + entity.getOrderNo())
                .content("<p>生产工单 <b>" + entity.getOrderNo() + "</b> 已创建，请相关人员关注。</p>")
                .relatedType("WORK_ORDER")
                .relatedId(String.valueOf(entity.getWorkOrderId()))
                .actionRoute("/pm/work-order")
                .build());
        return entity.getWorkOrderId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PmmWorkOrder entity) {
        if (entity.getWorkOrderId() == null) throw new ServiceException("工单ID不能为空");
        PmmWorkOrder old = mapper.selectById(entity.getWorkOrderId());
        if (old == null) throw new ServiceException("工单不存在");
        if (entity.getOrderNo() != null) {
            if (mapper.countDuplicate(entity.getOrderNo(), entity.getWorkOrderId()) > 0)
                throw new ServiceException("工单号已存在");
        }
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        PmmWorkOrder w = mapper.selectById(id);
        if (w == null) throw new ServiceException("工单不存在");
        Integer s = w.getStatus();
        if (s != null && (s == 20 || s == 40 || s == 50))
            throw new ServiceException("生产中/完工/关闭的工单禁止直接删除, 请先取消");
        return mapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long id, Integer status) {
        if (id == null) throw new ServiceException("工单ID不能为空");
        if (status == null) throw new ServiceException("状态值非法");
        PmmWorkOrder w = mapper.selectById(id);
        if (w == null) throw new ServiceException("工单不存在");
        w.setStatus(status);
        boolean ok = mapper.updateById(w) > 0;
        if (ok) {
            // 自动通知：工单状态变更
            String[] statusTexts = {"新建", "已下达", "生产中", "暂停", "完工", "关闭", "", "", "", "取消"};
            String st = (status >= 0 && status < statusTexts.length) ? statusTexts[status] : ("状态" + status);
            int level = (status == 20 || status == 40) ? 2 : 1; // 生产中/完工 → 重要
            eventPublisher.publishEvent(NoticeEvent.builder()
                    .noticeType(2).noticeLevel(level)
                    .title("工单 " + w.getOrderNo() + " 状态变更为「" + st + "」")
                    .summary("工单 " + w.getOrderNo() + " → " + st)
                    .content("<p>工单 <b>" + w.getOrderNo() + "</b> 状态已变更为「" + st + "」。</p>")
                    .relatedType("WORK_ORDER")
                    .relatedId(String.valueOf(id))
                    .actionRoute("/pm/work-order")
                    .build());
        }
        return ok;
    }

    @Override
    public List<PmmWorkOrder> options() {
        return mapper.selectList(new LambdaQueryWrapper<PmmWorkOrder>()
                .ne(PmmWorkOrder::getStatus, 90)
                .orderByDesc(PmmWorkOrder::getWorkOrderId));
    }
}
