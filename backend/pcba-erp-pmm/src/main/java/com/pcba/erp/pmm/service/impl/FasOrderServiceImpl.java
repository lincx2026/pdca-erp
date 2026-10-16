package com.pcba.erp.pmm.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.common.event.NoticeEvent;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pmm.entity.FasOrder;
import com.pcba.erp.pmm.entity.FasProductStock;
import com.pcba.erp.pmm.mapper.FasOrderMapper;
import com.pcba.erp.pmm.mapper.FasProductStockMapper;
import com.pcba.erp.pmm.service.IPmmFasOrderService;
import com.pcba.erp.pmm.service.IPmmFasStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FasOrderServiceImpl implements IPmmFasOrderService {

    private final FasOrderMapper mapper;
    private final FasProductStockMapper stockMapper;
    private final IPmmFasStockService stockService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public IPage<FasOrder> selectPage(int pageNum, int pageSize, String orderNo, String productName,
                                      Integer status, String lineName) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), orderNo, productName, status, lineName);
    }

    @Override
    public FasOrder getById(Long id) {
        if (id == null) throw new ServiceException("工单ID不能为空");
        FasOrder order = mapper.selectById(id);
        if (order == null) throw new ServiceException("工单不存在");
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FasOrder entity) {
        if (entity.getOrderNo() != null && mapper.countDuplicateNo(entity.getOrderNo(), null) > 0) {
            throw new ServiceException("工单号【" + entity.getOrderNo() + "】已存在");
        }
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getQtyComplete() == null) entity.setQtyComplete(0);
        if (entity.getQtyNg() == null) entity.setQtyNg(0);
        mapper.insert(entity);
        // 自动通知：后道组装工单创建
        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(1)
                .title("后道组装工单 " + entity.getOrderNo() + " 已创建")
                .summary((entity.getProductName() != null ? entity.getProductName() : "") + " (" + entity.getOrderNo() + ")")
                .content("<p>后道组装工单 <b>" + entity.getOrderNo() + "</b> 已创建，请相关人员关注。</p>")
                .relatedType("FAS_ORDER")
                .relatedId(String.valueOf(entity.getOrderId()))
                .actionRoute("/pm/fas-order")
                .build());
        return entity.getOrderId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FasOrder entity) {
        if (entity.getOrderId() == null) throw new ServiceException("工单ID不能为空");
        FasOrder old = mapper.selectById(entity.getOrderId());
        if (old == null) throw new ServiceException("工单不存在");
        if (entity.getOrderNo() != null && mapper.countDuplicateNo(entity.getOrderNo(), entity.getOrderId()) > 0) {
            throw new ServiceException("工单号已存在");
        }
        mapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        FasOrder order = mapper.selectById(id);
        if (order == null) throw new ServiceException("工单不存在");
        Integer s = order.getStatus();
        if (s != null && s >= 2) {
            throw new ServiceException("进行中及以后状态的工单禁止删除");
        }
        mapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long id) {
        FasOrder order = requireOrder(id);
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new ServiceException("只有新建状态的工单才能下达");
        }
        order.setStatus(1);
        mapper.updateById(order);
        // 自动通知：工单下达
        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(2)
                .title("后道组装工单 " + order.getOrderNo() + " 已下达")
                .summary("产品：" + (order.getProductName() != null ? order.getProductName() : "-"))
                .content("<p>后道组装工单 <b>" + order.getOrderNo() + "</b> 已下达，请安排生产。</p>")
                .relatedType("FAS_ORDER").relatedId(String.valueOf(id)).actionRoute("/pm/fas-order").build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startWork(Long id) {
        FasOrder order = requireOrder(id);
        if (order.getStatus() == null || order.getStatus() != 1) {
            throw new ServiceException("只有已下达状态的工单才能开工");
        }
        order.setStatus(2);
        order.setActualStart(LocalDateTime.now());
        mapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        FasOrder order = requireOrder(id);
        if (order.getStatus() == null || order.getStatus() != 2) {
            throw new ServiceException("只有进行中状态的工单才能完工");
        }
        order.setStatus(3);
        order.setActualEnd(LocalDateTime.now());
        mapper.updateById(order);
        // 自动通知：工单完工
        eventPublisher.publishEvent(NoticeEvent.builder()
                .noticeType(2).noticeLevel(2)
                .title("后道组装工单 " + order.getOrderNo() + " 已完工")
                .summary("产品：" + (order.getProductName() != null ? order.getProductName() : "-"))
                .content("<p>后道组装工单 <b>" + order.getOrderNo() + "</b> 已完工，完成数量 " + order.getQtyComplete() + "，不良 " + order.getQtyNg() + "。</p>")
                .relatedType("FAS_ORDER").relatedId(String.valueOf(id)).actionRoute("/pm/fas-order").build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long id, String warehouse, String batchNo, Integer qty, String remark) {
        FasOrder order = requireOrder(id);
        if (order.getStatus() == null || order.getStatus() != 3) {
            throw new ServiceException("只有已完工状态的工单才能生成入库单");
        }
        // qty == 0 时默认取完工数量
        int qtyIn = (qty == null || qty <= 0)
                ? (order.getQtyComplete() == null ? 0 : order.getQtyComplete())
                : qty;
        // 委托给 StockService 生成草稿 + 通知
        stockService.createInboundDraftFromOrder(id, warehouse, batchNo, qtyIn, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        FasOrder order = requireOrder(id);
        if (order.getStatus() == null || order.getStatus() < 3) {
            throw new ServiceException("未完工的工单不能关闭");
        }
        order.setStatus(5);
        mapper.updateById(order);
    }

    private FasOrder requireOrder(Long id) {
        if (id == null) throw new ServiceException("工单ID不能为空");
        FasOrder order = mapper.selectById(id);
        if (order == null) throw new ServiceException("工单不存在");
        return order;
    }
}
