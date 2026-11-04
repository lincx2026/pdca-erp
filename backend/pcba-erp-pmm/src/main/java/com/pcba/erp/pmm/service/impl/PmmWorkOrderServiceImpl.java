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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmmWorkOrderServiceImpl implements IPmmWorkOrderService {

    private final PmmWorkOrderMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;

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
        entity.setWorkOrderId(null); entity.setStatus(0);
        if (entity.getOrderQty() == null || entity.getOrderQty().signum() <= 0) throw new ServiceException("生产数量必须大于零");
        resolveReferences(entity);
        if (entity.getOrderNo() == null || entity.getOrderNo().isBlank()) {
            Long n = jdbcTemplate.queryForObject("SELECT nextval('pmm_work_order_number_seq')", Long.class);
            entity.setOrderNo("WO-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd")) + "-" + n);
        }
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
        if (entity.getOrderQty() != null && entity.getOrderQty().signum() <= 0) throw new ServiceException("生产数量必须大于零");
        resolveReferences(entity);
        if (entity.getOrderNo() != null && entity.getOrderNo().isBlank()) throw new ServiceException("工单号不能为空");
        if (entity.getOrderNo() != null) {
            if (mapper.countDuplicate(entity.getOrderNo(), entity.getWorkOrderId()) > 0)
                throw new ServiceException("工单号已存在");
        }
        return mapper.updateById(entity) > 0;
    }

    private void resolveReferences(PmmWorkOrder entity) {
        if (entity.getSaleOrderId() != null) {
            if (entity.getSaleOrderLineId() == null) throw new ServiceException("请选择销售订单明细");
            var rows = jdbcTemplate.queryForList("SELECT s.so_no, s.customer_id, s.customer_name, s.customer_order_no, l.material_id FROM sal_sales_order s JOIN sal_sales_order_line l ON l.so_id=s.so_id WHERE s.so_id=? AND l.line_id=?", entity.getSaleOrderId(), entity.getSaleOrderLineId());
            if (rows.isEmpty()) throw new ServiceException("销售订单明细不存在或不属于该订单");
            var row = rows.get(0);
            if (!java.util.Objects.equals(((Number) row.get("material_id")).longValue(), entity.getMaterialId())) throw new ServiceException("工单物料必须与销售订单明细一致");
            entity.setSaleOrderNo((String) row.get("so_no"));
            entity.setCustomerId(row.get("customer_id") == null ? null : ((Number) row.get("customer_id")).longValue());
            entity.setCustomerName((String) row.get("customer_name"));
            entity.setCustomerOrderNo((String) row.get("customer_order_no"));
        } else if (entity.getSaleOrderLineId() != null) throw new ServiceException("请先选择销售订单");
        if (entity.getFactoryId() != null) {
            var factories = jdbcTemplate.queryForList("SELECT factory_name FROM company_factory WHERE factory_id=? AND node_type='FACTORY' AND status=0", String.class, entity.getFactoryId());
            if (factories.isEmpty()) throw new ServiceException("加工厂不存在或已停用");
            entity.setFactoryName(factories.get(0));
        }
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
